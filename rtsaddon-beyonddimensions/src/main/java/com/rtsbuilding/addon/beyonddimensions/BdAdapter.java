package com.rtsbuilding.addon.beyonddimensions;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.List;

/**
 * Beyond Dimensions 宿主 API 的唯一访问入口（强耦合集成适配层）。
 *
 * <p>BD 集成是<b>编译期强耦合</b>（有意选择，见 architecture-optimization.md 阶段二 2.4）：
 * 直接引用 {@code com.wintercogs.beyonddimensions.*}，宿主大版本升级若改 API 会导致本 addon
 * 编译失败。所有宿主类引用被<b>收敛在本文件</b>——若未来改为反射/接口化，只需改这里。
 *
 * <p>注意：{@link BdDirectItemHandler} 因深度使用 {@link UnifiedStorage} 的缓存/键映射语义，
 * 仍保留强类型引用（避免过度抽象破坏正确性），但入口统一经本类获取。
 */
final class BdAdapter {

    private BdAdapter() {}

    /** 玩家主网络（无则返回 null）。 */
    @Nullable
    static DimensionsNet primaryNet(ServerPlayer player) {
        if (player == null || player.getServer() == null) return null;
        return DimensionsNet.getPrimaryNetFromPlayer(player);
    }

    /** 玩家主网络的统一存储（无网络返回 null）。 */
    @Nullable
    static UnifiedStorage primaryStorage(ServerPlayer player) {
        DimensionsNet net = primaryNet(player);
        return net == null ? null : net.getUnifiedStorage();
    }

    /** 指定方块是否属于玩家主网络（精确判定，避免重复接入同一网络）。 */
    static boolean isNetMember(ServerPlayer player, BlockPos pos, UnifiedStorage storage) {
        if (pos == null || player == null) return false;
        BlockEntity be = player.serverLevel().getBlockEntity(pos);
        return be instanceof com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity neted
                && neted.getNet() == DimensionsNet.getPrimaryNetFromPlayer(player);
    }

    /** 网络显示名（无自定义名时回退默认）。 */
    static String displayName(ServerPlayer player) {
        DimensionsNet net = primaryNet(player);
        if (net == null) return "Beyond Dimensions Network";
        try {
            String name = net.getCustomName();
            return (name != null && !name.isEmpty()) ? name : "Beyond Dimensions Network";
        } catch (NoSuchMethodError e) {
            return "Beyond Dimensions Network";
        }
    }

    /** 流体处理器（直接基于统一存储构建，供 FluidProvider 使用）。 */
    @Nullable
    static IFluidHandler fluidHandler(ServerPlayer player) {
        UnifiedStorage storage = primaryStorage(player);
        return storage == null ? null
                : new com.wintercogs.beyonddimensions.api.capability.helper.unordered.FluidUnifiedStorageHandler(storage);
    }

    /** 存储项条目数量（供 rebuildCache 使用）。 */
    static int bucketSize(UnifiedStorage storage) {
        var bucket = storage.<com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler.TypeBucket>getBucket(ItemStackKey.ID);
        return bucket.isEmpty() ? 0 : bucket.get().size();
    }

    static Object bucketGet(UnifiedStorage storage, int i) {
        var bucket = storage.<com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler.TypeBucket>getBucket(ItemStackKey.ID);
        return bucket.isEmpty() ? null : bucket.get().get(i);
    }

    static KeyAmount stackByKey(UnifiedStorage storage, ItemStackKey key) {
        return storage.getStackByKey(key);
    }

    static ItemStack outStackByKey(UnifiedStorage storage, ItemStackKey key) {
        Object out = storage.getOutStackByKey(key);
        return out instanceof ItemStack s ? s : ItemStack.EMPTY;
    }

    static KeyAmount insert(UnifiedStorage storage, ItemStackKey key, int count, boolean simulate) {
        return storage.insert(key, count, simulate);
    }

    static KeyAmount extract(UnifiedStorage storage, ItemStackKey key, int amount, boolean simulate) {
        return storage.extract(key, amount, simulate, false);
    }

    /** 供 itemToKey 缓存使用的键映射。 */
    static void mapItemToKey(Map<Item, ItemStackKey> map, Item item, ItemStackKey key) {
        map.put(item, key);
    }
}
