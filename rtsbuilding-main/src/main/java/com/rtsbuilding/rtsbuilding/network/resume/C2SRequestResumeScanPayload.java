package com.rtsbuilding.rtsbuilding.network.resume;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：请求扫描某个暂停工作流的恢复数据。
 * 服务端据 {@code workflowEntryId} 扫描剩余方块/冲突/材料后回发 {@link S2CResumeScanPayload}。
 */
public record C2SRequestResumeScanPayload(int workflowEntryId) implements CustomPacketPayload {

    public static final Type<C2SRequestResumeScanPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_resume_scan"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestResumeScanPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarInt(payload.workflowEntryId()),
            (buf) -> new C2SRequestResumeScanPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
