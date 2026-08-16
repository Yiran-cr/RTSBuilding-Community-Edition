package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;

/**
 * 服务端方块放置动画提交器 —— BuildingGadgets2「动画即落位」触发/结束语义的服务端适配，
 * 并支持其 {@code retryList} 支撑依赖重试机制。
 *
 * <p>BuildingGadgets2 的放置不会直接 {@code setBlock} 目标方块，而是先在目标位置设置幻影
 * {@code RenderBlock}，由服务端逐 tick 推进动画进度，动画结束瞬间才真正放置目标方块 ——
 * 动画结束时刻 = 方块落位时刻。本类以轻量方式实现同样的语义：</p>
 *
 * <ul>
 *   <li><b>触发</b>：{@link #schedulePlace} 立即发送放置动画包（客户端收到后马上播放
 *       「方块从 0 放大到 1」的生长动画），并把真实方块落位登记到延迟队列；</li>
 *   <li><b>结束</b>：{@link #tick()} 每服务端 tick 推进，动画周期 {@link #PLACE_ANIMATION_TICKS}
 *       结束后执行 {@code attempt}（真正 {@code setBlock} 目标方块 + 后置逻辑），方块「生长完成即出现」。</li>
 *   <li><b>支撑依赖重试</b>（对齐 BuildingGadgets2 {@code retryList}）：{@code attempt} 在落位前检查
 *       {@code canSurvive}，若因支撑方块（火把/门等）尚未落位而失败返回 {@code false}，本类会将任务
 *       延迟 {@link #RETRY_TICKS} 后重试（最多 {@link #MAX_RETRIES} 次）；重试超限后调用 {@code onGiveUp}
 *       执行退款清理。</li>
 * </ul>
 *
 * <p>由于动画期间目标位置保持原状态（空气/可替换方块）而未落位，客户端只有生长动画，
 * 动画结束瞬间 BlockUpdate 到达方块出现，与 BuildingGadgets2 的观感一致。
 * 重试阶段不再重复发送动画包（动画已播完），仅延迟真实落位。</p>
 *
 * <p>队列不跨服持久化：服务器重启或玩家掉线时，已安排但未到期的落位由 {@code attempt}
 * 内的空值/环境判断自行处理（默认仍落位，保证已扣物品的方块不丢失）。</p>
 */
public final class RtsBlockAnimationCommitter {

    /** 放置动画周期（服务端 tick，50ms/tick）。客户端 grow 动画总时长须与之对齐（见 PlaceAnimationPass）。 */
    public static final int PLACE_ANIMATION_TICKS = 12;

    /** 破坏动画周期（服务端 tick，50ms/tick）。客户端 shrink 动画总时长须与之对齐（见 BreakEffectPass）。 */
    public static final int BREAK_ANIMATION_TICKS = 12;

    /** 支撑依赖重试间隔（服务端 tick）：等待同批支撑方块先落位。 */
    public static final int RETRY_TICKS = 4;

    /** 单位置最大重试次数（对齐 BuildingGadgets2 {@code retryList} 的一次重试）。 */
    public static final int MAX_RETRIES = 1;

    /** 延迟落位任务队列（FIFO，按到期 tick 顺序执行）。 */
    private static final ArrayDeque<PlaceCommit> QUEUE = new ArrayDeque<>();

    /** 服务端 tick 计数器（在 {@link #tick()} 内推进）。 */
    private static long tickCounter = 0L;

    /**
     * 延迟落位任务。
     *
     * @param attempt     落位尝试：返回 {@code true} 表示完成（成功或已自行处理失败退款），
     *                    {@code false} 表示支撑依赖未就绪、请求延迟重试
     * @param onGiveUp    重试次数用尽时的放弃清理（如退回已提取的物品）
     * @param dueTick     到期 tick
     * @param attemptCount 已尝试次数（含本次之前的重试）
     */
    private record PlaceCommit(BooleanSupplier attempt, Runnable onGiveUp, long dueTick, int attemptCount) {
    }

    private RtsBlockAnimationCommitter() {
    }

    /**
     * 提交一次延迟放置（首次：发送动画包）。
     *
     * @param player   目标玩家（用于发送动画包）
     * @param pos      放置位置
     * @param state    放置的目标方块状态（动画包携带）
     * @param attempt  动画周期结束后执行的落位尝试（见 {@link PlaceCommit}）
     * @param onGiveUp 重试超限时的退款清理
     */
    public static void schedulePlace(ServerPlayer player, BlockPos pos, BlockState state,
                                     BooleanSupplier attempt, Runnable onGiveUp) {
        if (player == null || pos == null) {
            return;
        }
        // 触发：先发放置动画包（含服务端权威时长），落位登记到延迟队列
        RtsPlacementSound.playRemotePlacedBlockAnimation(player, pos, state, PLACE_ANIMATION_TICKS);
        QUEUE.addLast(new PlaceCommit(attempt, onGiveUp, tickCounter + PLACE_ANIMATION_TICKS, 0));
    }

    /** 每服务端 tick 调用（挂载于 ServerTickEvent.Post）：执行所有到期的落位任务。 */
    public static void tick() {
        tickCounter++;
        while (!QUEUE.isEmpty()) {
            PlaceCommit c = QUEUE.peekFirst();
            if (c.dueTick() > tickCounter) {
                break;
            }
            QUEUE.removeFirst();
            boolean done;
            try {
                done = c.attempt().getAsBoolean();
            } catch (RuntimeException ex) {
                RtsbuildingMod.LOGGER.error("[RtsBlockAnimationCommitter] 延迟落位任务执行异常", ex);
                safeGiveUp(c);
                continue;
            }
            if (!done) {
                if (c.attemptCount() < MAX_RETRIES) {
                    // 支撑依赖未就绪：延迟重试（不发动画包），等待同批支撑方块先落位
                    QUEUE.addLast(new PlaceCommit(c.attempt(), c.onGiveUp(),
                            tickCounter + RETRY_TICKS, c.attemptCount() + 1));
                } else {
                    // 重试次数用尽：放弃并执行退款清理
                    safeGiveUp(c);
                }
            }
        }
    }

    /** 执行放弃清理（退款），异常时仅记录日志不中断队列。 */
    private static void safeGiveUp(PlaceCommit c) {
        try {
            if (c.onGiveUp() != null) {
                c.onGiveUp().run();
            }
        } catch (RuntimeException ex) {
            RtsbuildingMod.LOGGER.error("[RtsBlockAnimationCommitter] 落位放弃清理执行异常", ex);
        }
    }

    /**
     * 判断玩家是否仍在线可交互（用于延迟回调中保护玩家相关后置逻辑）。
     * 落位本身（{@code setBlock}）不依赖玩家，应无条件执行以保证已扣物品的方块不丢失。
     */
    public static boolean isPlayerStillOnline(ServerPlayer player) {
        return player != null && !player.isRemoved()
                && player.connection != null
                && player.connection.getConnection() != null
                && player.connection.getConnection().isConnected();
    }
}
