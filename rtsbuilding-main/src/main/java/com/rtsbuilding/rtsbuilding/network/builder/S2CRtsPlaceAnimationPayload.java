package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server confirmation that an RTS block placement actually succeeded.
 *
 * <p>The client treats this as a purely visual cue. It must not drive gameplay
 * state, inventory counts, undo history, or placement retries; those stay
 * authoritative on the server-side placement path.
 *
 * <p>{@code serverTick}/{@code seq} 由服务端填充：前者是放置发生的服务端 tick，
 * 后者是同 tick 内该玩家的递增序号。客户端当前不据此错峰——动画启动时刻锚定到
 * 方块状态实际变化的瞬间（BlockUpdate 触发），两者保留供将来与服务端时间的精确对齐。</p>
 *
 * <p>{@code durationTicks} 为服务端权威的放置动画时长（tick，50ms/tick）：服务端在该时长后
 * 真正落位方块，客户端据此播放生长动画，保证「动画结束 = 方块落位」的节奏由服务端控制。</p>
 */
public record S2CRtsPlaceAnimationPayload(BlockPos pos, BlockState state, long serverTick, int seq,
                                          int durationTicks) implements CustomPacketPayload {
    public static final Type<S2CRtsPlaceAnimationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_place_animation"));

    public S2CRtsPlaceAnimationPayload {
        pos = pos == null ? BlockPos.ZERO : pos;
        state = state == null ? Blocks.AIR.defaultBlockState() : state;
        serverTick = Math.max(0L, serverTick);
        seq = Math.max(0, seq);
        durationTicks = Math.max(1, durationTicks);
    }

    public S2CRtsPlaceAnimationPayload(BlockPos pos, BlockState state) {
        this(pos, state, 0L, 0, 12);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsPlaceAnimationPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeVarInt(Block.getId(payload.state()));
                buf.writeVarLong(payload.serverTick());
                buf.writeVarInt(payload.seq());
                buf.writeVarInt(payload.durationTicks());
            },
            (buf) -> new S2CRtsPlaceAnimationPayload(
                    buf.readBlockPos(),
                    Block.stateById(buf.readVarInt()),
                    buf.readVarLong(),
                    buf.readVarInt(),
                    buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
