package com.rtsbuilding.rtsbuilding.network.resume;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 服务端 → 客户端：暂停工作流的恢复扫描结果。
 *
 * <p>统一承载蓝图（多材料）与普通放置（单物品）工作流的扫描数据，并携带
 * 剩余位置与冲突位置（供客户端渲染绿/橙线框预览）。</p>
 *
 * @param entryId           工作流条目 ID
 * @param blueprint         是否为蓝图工作流
 * @param totalRemaining    剩余总数（含已放置与冲突槽）
 * @param alreadyPlaced     已存在同种方块的位置数
 * @param conflictCount     冲突（被非空气不同方块占据）位置数
 * @param neededItems       实际需要放置的方块数（= totalRemaining - alreadyPlaced）
 * @param missingItems      缺口（≤0 表示材料足够）
 * @param itemId            单物品工作流的物品 ID
 * @param itemLabel         单物品工作流的物品显示名
 * @param materialIds       材料清单 ID（蓝图多材料）
 * @param materialLabels    材料显示名
 * @param materialRequired  材料需求量
 * @param materialAvailable 材料可用量
 * @param remainingPositions 剩余位置（asLong），供绿色线框
 * @param conflictPositions 冲突位置（asLong），供橙色线框
 */
public record S2CResumeScanPayload(
        int entryId,
        boolean blueprint,
        int totalRemaining,
        int alreadyPlaced,
        int conflictCount,
        int neededItems,
        long missingItems,
        String itemId,
        String itemLabel,
        List<String> materialIds,
        List<String> materialLabels,
        List<Integer> materialRequired,
        List<Long> materialAvailable,
        List<Long> remainingPositions,
        List<Long> conflictPositions) implements CustomPacketPayload {

    public static final Type<S2CResumeScanPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_resume_scan"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CResumeScanPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entryId());
                buf.writeBoolean(payload.blueprint());
                buf.writeVarInt(payload.totalRemaining());
                buf.writeVarInt(payload.alreadyPlaced());
                buf.writeVarInt(payload.conflictCount());
                buf.writeVarInt(payload.neededItems());
                buf.writeVarLong(payload.missingItems());
                buf.writeUtf(payload.itemId() == null ? "" : payload.itemId());
                buf.writeUtf(payload.itemLabel() == null ? "" : payload.itemLabel());
                buf.writeCollection(payload.materialIds(), (b, s) -> b.writeUtf(s));
                buf.writeCollection(payload.materialLabels(), (b, s) -> b.writeUtf(s));
                buf.writeCollection(payload.materialRequired(), (b, v) -> b.writeVarInt(v));
                buf.writeCollection(payload.materialAvailable(), (b, v) -> b.writeVarLong(v));
                buf.writeCollection(payload.remainingPositions(), (b, v) -> b.writeVarLong(v));
                buf.writeCollection(payload.conflictPositions(), (b, v) -> b.writeVarLong(v));
            },
            (buf) -> new S2CResumeScanPayload(
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarLong(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readList(b -> b.readUtf()),
                    buf.readList(b -> b.readUtf()),
                    buf.readList(b -> b.readVarInt()),
                    buf.readList(b -> b.readVarLong()),
                    buf.readList(b -> b.readVarLong()),
                    buf.readList(b -> b.readVarLong())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
