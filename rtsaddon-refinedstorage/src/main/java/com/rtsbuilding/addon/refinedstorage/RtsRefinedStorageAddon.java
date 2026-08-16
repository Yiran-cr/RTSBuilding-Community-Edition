package com.rtsbuilding.addon.refinedstorage;

import com.rtsbuilding.rtsbuilding.api.compat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Refined Storage 2（RS 2.0.x，MC 1.21.1）存储网络集成。
 *
 * <p>通过反射绑定 <code>com.refinedmods.refinedstorage.api.*</code> 组件式架构（2.0.x 新 API）：
 * <pre>
 * BlockEntity.getContainerProvider() → getContainers() → NetworkNodeContainer.getNode()
 *   → NetworkNode.getNetwork() → Network.getComponent(StorageNetworkComponent.class)
 *     → StorageNetworkComponent.getResources(Actor) 快照
 *     → insert(ResourceKey, amount, Action, Actor) / extract(ResourceKey, amount, Action, Actor)
 * </pre>
 *
 * <p>2.0.x 的 insert/extract 使用 {@code Actor.EMPTY} 即可（无 SecurityManager 门控），
 * 旧版（2.0.0-milestone）的 SecurityManager 权限链路在新版已不存在。
 *
 * <p>注意：反射绑定按类名/方法签名硬编码，宿主大版本升级需人工同步（见 RtsIntegration.selfCheck）。
 */
@Mod("rtsbuilding_addon_refinedstorage")
public class RtsRefinedStorageAddon implements RtsIntegration {

    private static final Logger LOGGER = LoggerFactory.getLogger("RTSBuilding/RS");

    private final RsReflection reflection;
    private final String loadError;

    public RtsRefinedStorageAddon(IEventBus modEventBus, ModContainer modContainer) {
        if (!ModList.get().isLoaded("refinedstorage")) {
            LOGGER.info("Refined Storage not detected — addon will not register providers");
            this.reflection = null;
            this.loadError = null;
            return;
        }
        var reflection = new RsReflection();
        if (!reflection.loaded) {
            LOGGER.warn("Refined Storage found but reflection load failed: {}", reflection.lastError);
            this.reflection = null;
            this.loadError = "reflection load failed: " + reflection.lastError;
            // 仍注册 integration，让设置面板/日志能显示"宿主存在但绑定失败"
            RtsCompatRegistry.registerIntegration(this);
            return;
        }
        this.reflection = reflection;
        this.loadError = null;
        // 统一走 RtsIntegration 注册入口（阶段二：Addon 集成统一抽象）
        RtsCompatRegistry.registerIntegration(this);
        LOGGER.info("Refined Storage integration registered (RS2 {})", reflection.apiVersion());
    }

    @Override public String integrationId() { return "refinedstorage"; }

    @Override public boolean available() { return reflection != null && reflection.loaded; }

    @Override @Nullable
    public String selfCheck() {
        if (loadError != null) return loadError;
        return reflection == null ? "refinedstorage not loaded" : reflection.selfCheck();
    }

    @Override
    public void register(RtsCompatRegistry registry) {
        if (reflection == null) return;
        registry.register(new RsStorageProvider(reflection));
        LOGGER.info("Refined Storage provider registered");
    }

    private static final class RsStorageProvider implements RtsStorageNetworkProvider {
        private final RsReflection ref;

        RsStorageProvider(RsReflection ref) { this.ref = ref; }

        @Override public String getModId() { return "refinedstorage"; }
        @Override public boolean isAvailable() { return ref.loaded; }

        @Override @Nullable
        public IItemHandler createItemHandler(ServerPlayer player, BlockPos pos) {
            if (!ref.loaded) return null;
            try {
                var storageRef = ref.resolveStorage(player, pos);
                if (storageRef == null) return null;
                return new RsNetworkItemHandler(ref, storageRef);
            } catch (Throwable e) {
                return null;
            }
        }

        @Override
        public void releaseItemHandler(IItemHandler handler) {
            // RS handler 无外部资源需释放（快照与句柄随 handler 自身 GC）
        }

        @Override
        public boolean isNetworkNode(ServerPlayer player, BlockPos pos) {
            return createItemHandler(player, pos) != null;
        }

        @Override @Nullable
        public String getNetworkDisplayName(ServerPlayer player) {
            return "Refined Storage Network";
        }
    }

    private static final class RsNetworkItemHandler
            implements IItemHandler, ReportedCountItemHandler, AnySlotInsertItemHandler, RefreshableSnapshotHandler {

        private static final int REFRESH_THROTTLE = 10;

        private final RsReflection ref;
        private final Object storageComponent;
        private List<SlotView> slots = List.of();
        private int tickSinceRefresh = 0;

        RsNetworkItemHandler(RsReflection ref, RsStorageRef storageRef) {
            this.ref = ref;
            this.storageComponent = storageRef.storageComponent();
            ensureFreshSnapshot();
        }

        @Override
        public void ensureFreshSnapshot() {
            if (tickSinceRefresh < REFRESH_THROTTLE && !slots.isEmpty()) {
                tickSinceRefresh++;
                return;
            }
            slots = buildSlots();
            tickSinceRefresh = 0;
        }

        private List<SlotView> buildSlots() {
            try {
                return ref.snapshotAll(storageComponent);
            } catch (Throwable e) {
                return List.of();
            }
        }

        @Override public int getSlots() { ensureFreshSnapshot(); return slots.size(); }
        @Override public ItemStack getStackInSlot(int slot) { ensureFreshSnapshot(); return slot < slots.size() ? slots.get(slot).displayStack() : ItemStack.EMPTY; }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            try {
                long inserted = ref.insert(storageComponent, stack, stack.getCount(), simulate);
                if (inserted <= 0) return stack;
                if (!simulate) this.slots = List.of();
                ItemStack remaining = stack.copy();
                remaining.setCount((int) (stack.getCount() - inserted));
                return remaining;
            } catch (Throwable e) {
                return stack;
            }
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ensureFreshSnapshot();
            if (slot >= slots.size()) return ItemStack.EMPTY;
            var view = slots.get(slot);
            try {
                long extracted = ref.extract(storageComponent, view.resource(), amount, simulate);
                if (extracted <= 0) return ItemStack.EMPTY;
                if (!simulate) this.slots = List.of();
                ItemStack result = view.displayStack().copy();
                result.setCount((int) extracted);
                return result;
            } catch (Throwable e) {
                return ItemStack.EMPTY;
            }
        }

        @Override
        public ItemStack insertItemAnywhere(ItemStack stack, boolean simulate) {
            return insertItem(0, stack, simulate);
        }

        @Override
        public long getReportedCount(int slot) {
            ensureFreshSnapshot();
            return slot < slots.size() ? slots.get(slot).amount() : 0;
        }

        @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
    }

    private record RsStorageRef(Object storageComponent) {}
    private record SlotView(Object resource, ItemStack displayStack, long amount) {}

    /**
     * RS 2.0.x 反射绑定（com.refinedmods.refinedstorage.api 包名）。
     *
     * <p>所有句柄在构造时一次性绑定；任一关键类/方法缺失都会导致 {@code loaded=false}，
     * 调用点统一走 {@code isAvailable()==false} 降级路径，绝不抛给调用方。
     */
    private static final class RsReflection {
        boolean loaded = false;
        String lastError = "";
        private String apiVersion = "2.0.x";

        private Class<?> clNetworkNodeContainerProvider;
        private Class<?> clInWorldNetworkNodeContainer;
        private Class<?> clNetworkNode;
        private Class<?> clNetwork;
        private Class<?> clStorageNetworkComponent;
        private Class<?> clTrackedResourceAmount;
        private Class<?> clItemResource;
        private Class<?> clActor;

        private MethodHandle mhGetContainers, mhGetNode, mhGetNetwork;
        private MethodHandle mhGetComponent, mhGetResources, mhToItemStack, mhOfItemStack;
        private MethodHandle mhInsert, mhExtract, mhGetResourceAmount, mhGetResource, mhGetAmount;
        private MethodHandle mhActorEmpty;

        RsReflection() {
            try {
                var lookup = MethodHandles.publicLookup();
                var cl = getClass().getClassLoader();

                // ── 核心 API 类 ──
                clNetworkNodeContainerProvider = Class.forName(
                        "com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider", false, cl);
                clInWorldNetworkNodeContainer = Class.forName(
                        "com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer", false, cl);
                clNetworkNode = Class.forName(
                        "com.refinedmods.refinedstorage.api.network.node.NetworkNode", false, cl);
                clNetwork = Class.forName(
                        "com.refinedmods.refinedstorage.api.network.Network", false, cl);
                clStorageNetworkComponent = Class.forName(
                        "com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent", false, cl);
                clTrackedResourceAmount = Class.forName(
                        "com.refinedmods.refinedstorage.api.storage.TrackedResourceAmount", false, cl);
                clItemResource = Class.forName(
                        "com.refinedmods.refinedstorage.common.support.resource.ItemResource", false, cl);

                var clActor = Class.forName(
                        "com.refinedmods.refinedstorage.api.storage.Actor", false, cl);
                this.clActor = clActor;
                var clAction = Class.forName(
                        "com.refinedmods.refinedstorage.api.core.Action", false, cl);
                var clResourceAmount = Class.forName(
                        "com.refinedmods.refinedstorage.api.resource.ResourceAmount", false, cl);
                var clResourceKey = Class.forName(
                        "com.refinedmods.refinedstorage.api.resource.ResourceKey", false, cl);

                // BlockEntity.getContainerProvider() 为具体类方法，在 resolveStorage 运行时解析（见下方）。
                mhGetContainers = lookup.findVirtual(clNetworkNodeContainerProvider, "getContainers",
                        MethodType.methodType(Set.class));
                mhGetNode = lookup.findVirtual(
                        Class.forName("com.refinedmods.refinedstorage.api.network.node.container.NetworkNodeContainer", false, cl),
                        "getNode", MethodType.methodType(clNetworkNode));
                mhGetNetwork = lookup.findVirtual(clNetworkNode, "getNetwork",
                        MethodType.methodType(clNetwork));

                // Network.getComponent(Class) — ComponentAccessor.getComponent
                mhGetComponent = lookup.findVirtual(
                        Class.forName("com.refinedmods.refinedstorage.api.core.component.ComponentAccessor", false, cl),
                        "getComponent",
                        MethodType.methodType(Object.class, Class.class));

                // StorageNetworkComponent.getResources(Class<? extends Actor>) → List<TrackedResourceAmount>
                mhGetResources = lookup.findVirtual(clStorageNetworkComponent, "getResources",
                        MethodType.methodType(List.class, Class.class));

                // TrackedResourceAmount.resourceAmount() → ResourceAmount; .resource()/.amount()
                mhGetResourceAmount = lookup.findVirtual(clTrackedResourceAmount, "resourceAmount",
                        MethodType.methodType(clResourceAmount));
                mhGetResource = lookup.findVirtual(clResourceAmount, "resource",
                        MethodType.methodType(clResourceKey));
                mhGetAmount = lookup.findVirtual(clResourceAmount, "amount",
                        MethodType.methodType(long.class));

                // ItemResource.toItemStack() / ItemResource.ofItemStack(ItemStack)
                mhToItemStack = lookup.findVirtual(clItemResource, "toItemStack",
                        MethodType.methodType(ItemStack.class));
                mhOfItemStack = lookup.findStatic(clItemResource, "ofItemStack",
                        MethodType.methodType(clItemResource, ItemStack.class));

                // StorageNetworkComponent.insert/extract — 继承自 InsertableStorage/ExtractableStorage
                mhInsert = lookup.findVirtual(
                        Class.forName("com.refinedmods.refinedstorage.api.storage.InsertableStorage", false, cl),
                        "insert",
                        MethodType.methodType(long.class, clResourceKey, long.class, clAction, clActor));
                mhExtract = lookup.findVirtual(
                        Class.forName("com.refinedmods.refinedstorage.api.storage.ExtractableStorage", false, cl),
                        "extract",
                        MethodType.methodType(long.class, clResourceKey, long.class, clAction, clActor));

                // Actor.EMPTY 常量（无权限限制的空执行者）
                mhActorEmpty = lookup.findStaticGetter(clActor, "EMPTY", clActor);

                loaded = true;
            } catch (Throwable e) {
                loaded = false;
                lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
        }

        String apiVersion() { return apiVersion; }

        /** 自检反射绑定健康度：缺失关键句柄时返回诊断串，健康返回 null。 */
        @Nullable
        String selfCheck() {
            if (!loaded) return "reflection load failed: " + lastError;
            StringBuilder missing = new StringBuilder();
            if (mhGetContainers == null) missing.append("mhGetContainers,");
            if (mhGetNode == null) missing.append("mhGetNode,");
            if (mhGetNetwork == null) missing.append("mhGetNetwork,");
            if (mhGetComponent == null) missing.append("mhGetComponent,");
            if (mhGetResources == null) missing.append("mhGetResources,");
            if (mhInsert == null) missing.append("mhInsert,");
            if (mhExtract == null) missing.append("mhExtract,");
            if (mhToItemStack == null) missing.append("mhToItemStack,");
            if (mhOfItemStack == null) missing.append("mhOfItemStack,");
            if (mhActorEmpty == null) missing.append("mhActorEmpty,");
            return missing.length() == 0 ? null : "missing: " + missing;
        }

        /**
         * 从玩家视角解析目标方块所属的 RS 存储网络组件。
         *
         * <p>链路：<code>BlockEntity.getContainerProvider()</code>（具体类方法，按类名反射解析）
         * → <code>getContainers()</code> → 首个容器 <code>getNode()</code>
         * → <code>getNetwork()</code> → <code>getComponent(StorageNetworkComponent.class)</code>。
         *
         * <p>任一步为空（未连接网络 / 非 RS 方块）都返回 null。
         */
        @Nullable
        RsStorageRef resolveStorage(ServerPlayer player, BlockPos pos) throws Throwable {
            var level = player.serverLevel();
            var be = level.getBlockEntity(pos);
            if (be == null) return null;

            // getContainerProvider() 是 public final 方法，定义在抽象基类
            // AbstractNetworkNodeContainerBlockEntity；getMethod 会沿继承链找到它。
            MethodHandle getter = null;
            try {
                var m = be.getClass().getMethod("getContainerProvider");
                getter = MethodHandles.lookup().unreflect(m);
            } catch (NoSuchMethodException ignored) {
                return null;
            }
            if (getter == null) return null;

            var provider = getter.invoke(be);
            if (provider == null) return null;
            var containers = (Collection<?>) mhGetContainers.invoke(provider);
            if (containers == null || containers.isEmpty()) return null;
            var container = containers.iterator().next();
            var node = mhGetNode.invoke(container);
            if (node == null) return null;
            var network = mhGetNetwork.invoke(node);
            if (network == null) return null; // 节点未连接网络

            var component = mhGetComponent.invoke(network, clStorageNetworkComponent);
            if (component == null) return null;
            return new RsStorageRef(component);
        }

        @SuppressWarnings("unchecked")
        List<SlotView> snapshotAll(Object storageComponent) throws Throwable {
            List<SlotView> result = new ArrayList<>();
            var resources = (List<?>) mhGetResources.invoke(storageComponent, clActor);
            if (resources == null) return result;
            for (var tracked : resources) {
                var resourceAmount = mhGetResourceAmount.invoke(tracked);
                var resource = mhGetResource.invoke(resourceAmount);
                long amount = (long) mhGetAmount.invoke(resourceAmount);
                ItemStack display = toItemStack(resource);
                if (display != null) {
                    result.add(new SlotView(resource, display, amount));
                }
            }
            return result;
        }

        @Nullable
        private ItemStack toItemStack(Object resource) throws Throwable {
            if (clItemResource.isInstance(resource)) {
                return (ItemStack) mhToItemStack.invoke(resource);
            }
            return null;
        }

        long insert(Object storageComponent, ItemStack stack, long amount, boolean simulate) throws Throwable {
            var resource = mhOfItemStack.invoke(stack);
            var action = simulate ? action("SIMULATE") : action("EXECUTE");
            return (long) mhInsert.invoke(storageComponent, resource, amount, action, mhActorEmpty.invoke());
        }

        long extract(Object storageComponent, Object resource, long amount, boolean simulate) throws Throwable {
            var action = simulate ? action("SIMULATE") : action("EXECUTE");
            return (long) mhExtract.invoke(storageComponent, resource, amount, action, mhActorEmpty.invoke());
        }

        /** 反射取 Action 枚举常量（每次调用，避免缓存枚举时类加载失败）。 */
        private static Object action(String name) throws Throwable {
            var cl = Class.forName("com.refinedmods.refinedstorage.api.core.Action",
                    false, RtsRefinedStorageAddon.class.getClassLoader());
            return MethodHandles.publicLookup().findStatic(cl, "valueOf",
                    MethodType.methodType(cl, String.class)).invoke(name);
        }
    }
}
