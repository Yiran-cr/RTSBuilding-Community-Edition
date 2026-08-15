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
 * Server confirmation that an RTS block break actually succeeded.
 *
 * <p>The client treats this as a visual cue plus the server-confirmed post-break
 * block state for local fake-air cleanup. It must not drive tool durability,
 * drops, or retry behaviour; those remain authoritative on the server-side
 * mining path.
 *
 * <p>{@code serverTick}/{@code seq} 由服务端填充：前者是破坏发生的服务端 tick，
 * 后者是同 tick 内该玩家的递增序号。客户端当前不据此错峰——动画启动时刻锚定到
 * 方块状态实际变化的瞬间（BlockUpdate 触发），两者保留供将来与服务端时间的精确对齐。</p>
 */
public record S2CRtsBreakAnimationPayload(BlockPos pos, BlockState state, BlockState resultState,
                                          long serverTick, int seq) implements CustomPacketPayload {
    public static final Type<S2CRtsBreakAnimationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_break_animation"));

    public S2CRtsBreakAnimationPayload {
        pos = pos == null ? BlockPos.ZERO : pos;
        state = state == null ? Blocks.AIR.defaultBlockState() : state;
        resultState = resultState == null ? Blocks.AIR.defaultBlockState() : resultState;
        serverTick = Math.max(0L, serverTick);
        seq = Math.max(0, seq);
    }

    public S2CRtsBreakAnimationPayload(BlockPos pos, BlockState state) {
        this(pos, state, Blocks.AIR.defaultBlockState(), 0L, 0);
    }

    public S2CRtsBreakAnimationPayload(BlockPos pos, BlockState state, BlockState resultState) {
        this(pos, state, resultState, 0L, 0);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsBreakAnimationPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeVarInt(Block.getId(payload.state()));
                buf.writeVarInt(Block.getId(payload.resultState()));
                buf.writeVarLong(payload.serverTick());
                buf.writeVarInt(payload.seq());
            },
            (buf) -> new S2CRtsBreakAnimationPayload(
                    buf.readBlockPos(),
                    Block.stateById(buf.readVarInt()),
                    Block.stateById(buf.readVarInt()),
                    buf.readVarLong(),
                    buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
