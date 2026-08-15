package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;

/**
 * 服务端方块放置动画提交器 —— BuildingGadgets2「动画即落位」触发/结束语义的服务端适配。
 *
 * <p>BuildingGadgets2 的放置不会直接 {@code setBlock} 目标方块，而是先在目标位置设置幻影
 * {@code RenderBlock}，由服务端逐 tick 推进动画进度，动画结束瞬间才真正放置目标方块 ——
 * 动画结束时刻 = 方块落位时刻。本类以轻量方式实现同样的语义：</p>
 *
 * <ul>
 *   <li><b>触发</b>：{@link #schedulePlace} 立即发送放置动画包（客户端收到后马上播放
 *       「方块从 0 放大到 1」的生长动画），并把真实方块落位登记到延迟队列；</li>
 *   <li><b>结束</b>：{@link #tick()} 每服务端 tick 推进，动画周期 {@link #PLACE_ANIMATION_TICKS}
 *       结束后执行 {@code onCommit}（真正 {@code setBlock} 目标方块 + 后置逻辑），方块「生长完成即出现」。</li>
 * </ul>
 *
 * <p>由于动画期间目标位置保持原状态（空气/可替换方块）而未落位，客户端只有生长动画，
 * 动画结束瞬间 BlockUpdate 到达方块出现，与 BuildingGadgets2 的观感一致。</p>
 *
 * <p>队列不跨服持久化：服务器重启或玩家掉线时，已安排但未到期的落位由 {@code onCommit}
 * 内的空值/环境判断自行处理（默认仍落位，保证已扣物品的方块不丢失）。</p>
 */
public final class RtsBlockAnimationCommitter {

    /** 放置动画周期（服务端 tick，50ms/tick）。客户端 grow 动画总时长须与之对齐（见 PlaceAnimationPass）。 */
    public static final int PLACE_ANIMATION_TICKS = 12;

    /** 延迟落位任务队列（FIFO，按到期 tick 顺序执行）。 */
    private static final ArrayDeque<PlaceCommit> QUEUE = new ArrayDeque<>();

    /** 服务端 tick 计数器（在 {@link #tick()} 内推进）。 */
    private static long tickCounter = 0L;

    /** 延迟落位任务：到期后执行真实方块落位与后置逻辑。 */
    private record PlaceCommit(Runnable onCommit, long dueTick) {
    }

    private RtsBlockAnimationCommitter() {
    }

    /**
     * 提交一次延迟放置。
     *
     * @param player   目标玩家（用于发送动画包）
     * @param pos      放置位置
     * @param state    放置的目标方块状态（动画包携带）
     * @param onCommit 动画周期结束后执行：真正放置方块与后置逻辑（BE 应用、追踪、声音、进度等）
     */
    public static void schedulePlace(ServerPlayer player, BlockPos pos, BlockState state, Runnable onCommit) {
        if (player == null || pos == null) {
            return;
        }
        // 触发：先发放置动画包（客户端立即播放生长动画），落位登记到延迟队列
        RtsPlacementSound.playRemotePlacedBlockAnimation(player, pos, state);
        QUEUE.addLast(new PlaceCommit(onCommit, tickCounter + PLACE_ANIMATION_TICKS));
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
            try {
                c.onCommit().run();
            } catch (RuntimeException ex) {
                RtsbuildingMod.LOGGER.error("[RtsBlockAnimationCommitter] 延迟落位任务执行异常", ex);
            }
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
