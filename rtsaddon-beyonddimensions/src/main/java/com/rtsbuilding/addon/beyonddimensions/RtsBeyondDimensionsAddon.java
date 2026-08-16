package com.rtsbuilding.addon.beyonddimensions;

import com.rtsbuilding.rtsbuilding.api.compat.*;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod("rtsbuilding_addon_beyonddimensions")
public class RtsBeyondDimensionsAddon implements RtsIntegration {

    private static final Logger LOGGER = LoggerFactory.getLogger("RTSBuilding/BD");

    private final boolean hostLoaded;

    public RtsBeyondDimensionsAddon(IEventBus modEventBus, ModContainer modContainer) {
        if (!ModList.get().isLoaded("beyonddimensions")) {
            LOGGER.info("Beyond Dimensions not detected — addon will not register");
            this.hostLoaded = false;
            return;
        }
        this.hostLoaded = true;
        // 统一走 RtsIntegration 注册入口（阶段二：Addon 集成统一抽象）
        // BD 为编译期强耦合集成（见 architecture-optimization.md 阶段二 2.4），绑定由 javac 保证。
        RtsCompatRegistry.registerIntegration(this);
        LOGGER.info("Beyond Dimensions integration registered");
    }

    @Override public String integrationId() { return "beyonddimensions"; }

    @Override public boolean available() { return hostLoaded; }

    /** BD 为编译期绑定（非反射），宿主类加载即视为健康。 */
    @Override @Nullable
    public String selfCheck() { return hostLoaded ? null : "beyonddimensions not loaded"; }

    @Override
    public void register(RtsCompatRegistry registry) {
        if (!hostLoaded) return;
        registry.register(new BdStorageProvider());
        registry.register(new BdFluidProvider());
        LOGGER.info("Beyond Dimensions storage/fluid providers registered");
    }

    private static final class BdStorageProvider implements RtsStorageNetworkProvider {
        @Override public String getModId() { return "beyonddimensions"; }
        @Override public boolean isAvailable() { return true; }

        @Override @Nullable
        public IItemHandler createItemHandler(ServerPlayer player, BlockPos pos) {
            var storage = BdAdapter.primaryStorage(player);
            if (storage == null) return null;
            if (pos != null && !pos.equals(BlockPos.ZERO)) {
                // 精确判定：仅玩家主网络的成员方块才返回网络 handler；
                // 普通方块（箱子等）返回 null → 调用方回退到方块自身能力（避免重复接入同一网络）
                if (!BdAdapter.isNetMember(player, pos, storage)) {
                    return null;
                }
            }
            return new BdDirectItemHandler(storage);
        }

        @Override
        public void releaseItemHandler(IItemHandler handler) {
            if (handler instanceof BdDirectItemHandler bd) bd.release();
        }

        @Override
        public boolean isNetworkNode(ServerPlayer player, BlockPos pos) {
            return createItemHandler(player, pos) != null;
        }

        @Override @Nullable
        public String getNetworkDisplayName(ServerPlayer player) {
            return BdAdapter.displayName(player);
        }

        public static boolean hasPrimaryNetwork(ServerPlayer player) {
            return BdAdapter.primaryNet(player) != null;
        }
    }

    private static final class BdFluidProvider implements RtsFluidNetworkProvider {
        @Override public String getModId() { return "beyonddimensions"; }
        @Override public boolean isAvailable() { return true; }

        @Override @Nullable
        public IFluidHandler createFluidHandler(ServerPlayer player) {
            return BdAdapter.fluidHandler(player);
        }

        @Override
        public List<FluidStack> collectFluids(ServerPlayer player, @Nullable BlockPos pos, @Nullable BlockEntity blockEntity) {
            return List.of();
        }
    }

    private static final class BdDirectItemHandler
            implements IItemHandler, ReportedCountItemHandler, DirectExtractHandler, AnySlotInsertItemHandler {

        private final UnifiedStorage storage;
        private final Map<Item, ItemStackKey> itemToKey = new HashMap<>();
        private final List<ItemStackKey> keys = new ArrayList<>();
        private final List<ItemStack> displayStacks = new ArrayList<>();
        private final List<Long> counts = new ArrayList<>();

        BdDirectItemHandler(UnifiedStorage storage) {
            this.storage = storage;
            rebuildCache();
        }

        private void rebuildCache() {
            itemToKey.clear();
            keys.clear();
            displayStacks.clear();
            counts.clear();
            int size = BdAdapter.bucketSize(storage);
            for (int i = 0; i < size; i++) {
                if (!(BdAdapter.bucketGet(storage, i) instanceof ItemStackKey key)) continue;
                KeyAmount entry = BdAdapter.stackByKey(storage, key);
                long amount = entry.amount();
                if (amount <= 0L) continue;
                ItemStack itemStack = BdAdapter.outStackByKey(storage, key);
                if (itemStack == null || itemStack.isEmpty()) continue;
                itemToKey.put(itemStack.getItem(), key);
                keys.add(key);
                displayStacks.add(itemStack.copyWithCount(1));
                counts.add(amount);
            }
        }

        @Override public int getSlots() { return keys.size(); }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= keys.size()) return ItemStack.EMPTY;
            long amount = counts.get(slot);
            if (amount <= 0L) return ItemStack.EMPTY;
            ItemStack result = displayStacks.get(slot).copy();
            result.setCount((int) Math.min(Integer.MAX_VALUE, amount));
            return result;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
            KeyAmount remainder = BdAdapter.insert(storage, new ItemStackKey(stack), stack.getCount(), simulate);
            if (!simulate && remainder.isEmpty()) rebuildCache();
            return remainder.isEmpty() ? ItemStack.EMPTY :
                    remainder.toStack() instanceof ItemStack result ? result : stack.copy();
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= keys.size() || amount <= 0) return ItemStack.EMPTY;
            ItemStackKey key = keys.get(slot);
            if (key == null) return ItemStack.EMPTY;
            KeyAmount extracted = BdAdapter.extract(storage, key, amount, simulate);
            if (!simulate) rebuildCache();
            return extracted.isEmpty() ? ItemStack.EMPTY :
                    extracted.toStack() instanceof ItemStack result ? result : ItemStack.EMPTY;
        }

        @Override
        public ItemStack tryExtractItem(Item target, int amount, boolean simulate) {
            if (target == null || amount <= 0) return ItemStack.EMPTY;
            ItemStackKey key = itemToKey.get(target);
            if (key == null) return ItemStack.EMPTY;
            KeyAmount result = BdAdapter.extract(storage, key, amount, simulate);
            if (!simulate) rebuildCache();
            return result.isEmpty() ? ItemStack.EMPTY :
                    result.toStack() instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItemAnywhere(ItemStack stack, boolean simulate) {
            return insertItem(0, stack, simulate);
        }

        @Override
        public long getReportedCount(int slot) {
            return (slot >= 0 && slot < counts.size()) ? Math.max(0L, counts.get(slot)) : 0L;
        }

        void release() {
            itemToKey.clear();
            keys.clear();
            displayStacks.clear();
            counts.clear();
        }

        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
    }
}
