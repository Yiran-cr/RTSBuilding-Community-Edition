package com.rtsbuilding.rtsbuilding.network.camera;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 其他玩家：RTS 无人机建造/破坏光束同步包。
 *
 * <p>当 RTS 模式玩家远程放置（建造）或破坏方块时，服务端把"无人机摄像头 → 目标方块"
 * （建造，蓝光）或"目标方块 → 无人机摄像头"（破坏，红光）的光束广播给<b>除该玩家以外</b>
 * 的所有在线玩家；主控玩家不会收到此包，因此看不到光束。</p>
 *
 * <p>客户端收到后注册一条光束，渲染时通过 {@code droneEntityId} 实时追踪无人机实体位置
 * （摄像头位置 = 无人机位置 + 摄像头部件偏移），使光束两端持续跟随"方块位置"与"摄像头位置"；
 * 若无人机实体因距离过远未加载，则回退到包内记录的初始起点。</p>
 *
 * @param droneEntityId 无人机实体 ID（客户端据此查找无人机实体实时追踪）
 * @param targetPos     目标方块位置（光束的另一端，追踪方块中心）
 * @param place         是否放置（true = 建造蓝光；false = 破坏红光）
 * @param originX       备用起点 X（无人机摄像头在发包瞬间的世界坐标）
 * @param originY       备用起点 Y
 * @param originZ       备用起点 Z
 */
public record S2CRtsDroneBeamPayload(
        int droneEntityId,
        BlockPos targetPos,
        boolean place,
        double originX,
        double originY,
        double originZ) implements CustomPacketPayload {

    public static final Type<S2CRtsDroneBeamPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_drone_beam"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsDroneBeamPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.droneEntityId());
                buf.writeBlockPos(payload.targetPos());
                buf.writeBoolean(payload.place());
                buf.writeDouble(payload.originX());
                buf.writeDouble(payload.originY());
                buf.writeDouble(payload.originZ());
            },
            (buf) -> new S2CRtsDroneBeamPayload(
                    buf.readVarInt(),
                    buf.readBlockPos(),
                    buf.readBoolean(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
