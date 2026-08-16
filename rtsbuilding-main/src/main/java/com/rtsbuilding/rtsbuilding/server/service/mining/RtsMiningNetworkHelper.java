package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBreakAnimationPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsMineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsUltimineProgressPayload;
import com.rtsbuilding.rtsbuilding.platform.Platform;
import com.rtsbuilding.rtsbuilding.server.service.beam.RtsDroneBeamService;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsBlockAnimationCommitter;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 挖掘网络包发送辅助器，向客户端发送视觉反馈数据包。
 *
 * <p>提供对 {@link Platform#sendPacket} 的便捷包装，用于在远程挖掘过程中
 * 向客户端发送破坏阶段裂纹更新、破坏动画和连锁挖掘进度。
 *
 * <p>核心方法：
 * <ul>
 *   <li>{@link #sendMineProgress} — 发送指定位置的破坏阶段裂纹更新（0-9 阶段）</li>
 *   <li>{@link #sendBreakAnimation} — 发送方块破坏动画（从 state → resultState 的视觉变化）</li>
 *   <li>{@link #sendUltimineProgress} — 发送连锁挖掘进度（已处理数/总数）</li>
 *   <li>{@link #clearMineProgress} — 清除服务端和客户端的破坏阶段粒子</li>
 *   <li>{@link #sendUltimineBatchProgress} — 将连锁挖掘批次进度映射为破坏阶段发送</li>
 * </ul>
 */
public final class RtsMiningNetworkHelper {

    /** 破坏动画同 tick 序号：每 tick 每玩家递增，用于客户端错峰启动批量破坏动画。 */
    private static final Map<UUID, Integer> PER_PLAYER_BREAK_SEQ = new HashMap<>();
    private static long BREAK_SEQ_RESET_TICK = -1L;

    private RtsMiningNetworkHelper() {
    }

    /** 向指定位置发送破坏阶段裂纹更新。 */
    public static void sendMineProgress(ServerPlayer player, BlockPos pos, int stage) {
        Platform.sendPacket(player, new S2CRtsMineProgressPayload(pos, (byte) stage));
    }

    /**
     * 发送破坏动画数据包，显示方块从哪种状态变为哪种状态。
     * <p>数据包携带服务端 tick、同 tick 内序号（{@code seq}）与服务端权威动画时长。
     * 同时广播一条破坏红光（目标方块 → 无人机摄像头）给其他玩家。</p>
     */
    public static void sendBreakAnimation(ServerPlayer player, BlockPos pos, BlockState state, BlockState resultState) {
        sendBreakAnimation(player, pos, state, resultState, RtsBlockAnimationCommitter.BREAK_ANIMATION_TICKS);
    }

    /**
     * 发送破坏动画数据包（指定<b>服务端权威动画时长</b>，见 {@link RtsBlockAnimationCommitter#BREAK_ANIMATION_TICKS}）。
     */
    public static void sendBreakAnimation(ServerPlayer player, BlockPos pos, BlockState state, BlockState resultState, int durationTicks) {
        if (player == null || pos == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long tick = level.getGameTime();
        int seq = nextBreakSeq(player, tick);
        Platform.sendPacket(player,
                new S2CRtsBreakAnimationPayload(pos.immutable(), state, resultState, tick, seq, durationTicks));
        // 破坏光束：只对其他玩家可见（主控不接收），两端追踪方块位置与无人机摄像头位置
        RtsDroneBeamService.broadcastBreak(player, pos);
    }

    /**
     * 获取本玩家本 tick 的破坏动画序号并递增；tick 变化时序号从 0 重新计数。
     */
    private static int nextBreakSeq(ServerPlayer player, long tick) {
        if (tick != BREAK_SEQ_RESET_TICK) {
            BREAK_SEQ_RESET_TICK = tick;
            PER_PLAYER_BREAK_SEQ.clear();
        }
        int seq = PER_PLAYER_BREAK_SEQ.getOrDefault(player.getUUID(), 0);
        PER_PLAYER_BREAK_SEQ.put(player.getUUID(), seq + 1);
        return seq;
    }

    /** 发送连锁挖掘进度更新（已处理数/总数）。 */
    public static void sendUltimineProgress(ServerPlayer player, int processed, int total) {
        Platform.sendPacket(player, new S2CRtsUltimineProgressPayload(processed, total));
    }

    /**
     * 清除指定位置的服务端和客户端破坏阶段粒子。
     */
    public static void clearMineProgress(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return;
        }
        player.serverLevel().destroyBlockProgress(player.getId(), pos, -1);
        sendMineProgress(player, pos, -1);
    }

    /**
     * 向客户端发送当前连锁挖掘批次的进度，将 {@code processed / total}
     * 映射到破坏阶段（0-9）。
     * <p>带变化检测：仅当进度位置或阶段发生变化时才发包，
     * 避免连锁挖掘每 tick 都发送一次内容相同的裂纹包。</p>
     */
    public static void sendUltimineBatchProgress(ServerPlayer player, RtsStorageSession session) {
        if (session.mining.ultimineProgressPos == null) {
            return;
        }
        int total = Math.max(1, session.mining.ultimineTotalTargets);
        int broken = session.mining.ultimineBrokenTargets;
        int stage = Math.min(9, (int) (broken / (double) total * 10.0D));

        // 位置或阶段均未变化 → 跳过（避免每 tick 空转发包）
        boolean posChanged = !session.mining.ultimineProgressPos.equals(session.mining.ultimineLastProgressPos);
        boolean stageChanged = stage != session.mining.ultimineLastStage;
        if (!posChanged && !stageChanged) {
            return;
        }
        session.mining.ultimineLastProgressPos = session.mining.ultimineProgressPos;
        session.mining.ultimineLastStage = stage;
        sendMineProgress(player, session.mining.ultimineProgressPos, stage);
    }
}
