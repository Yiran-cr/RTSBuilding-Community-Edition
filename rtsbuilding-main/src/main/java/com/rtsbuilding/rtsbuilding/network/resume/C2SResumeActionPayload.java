package com.rtsbuilding.rtsbuilding.network.resume;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：对暂停工作流执行恢复动作。
 *
 * @param workflowEntryId 目标工作流条目 ID
 * @param strategy        0=开始（无冲突直接恢复）；1=跳过（跳过冲突位置继续放置）；
 *                        2=覆盖（用原生破坏逻辑破坏冲突方块后再放置）
 */
public record C2SResumeActionPayload(int workflowEntryId, byte strategy) implements CustomPacketPayload {

    public static final Type<C2SResumeActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_resume_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SResumeActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.workflowEntryId());
                buf.writeByte(payload.strategy());
            },
            (buf) -> new C2SResumeActionPayload(buf.readVarInt(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
