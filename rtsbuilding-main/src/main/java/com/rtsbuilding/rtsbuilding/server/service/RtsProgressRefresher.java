package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlockPlacementPlanner;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlueprintPersistence;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * 蓝图工作流进度刷新服务。
 *
 * <p>进度刷新职责从 {@link RtsPendingPlacementService} 抽取而来。历史上曾在此扫描世界实际方块状态
 * 刷新范围放置/形状建造的进度条（{@code refreshPlacementProgress}），该扫描存在重复计数、误算已有方块、
 * chunk 未加载回退等缺陷，导致放置进度与实际放置对不上，已随链路检查移除。
 * 放置/破坏进度统一由动作计数（{@code placedPositions.size()}/{@code destroyedPositions.size()}）驱动。</p>
 *
 * <p>本类当前仅负责<b>蓝图</b>工作流进度刷新：扫描已放置但被挖掉的蓝图方块，重新放回队列待重新放置。
 * 蓝图进度扫描使用每玩家节流（最多每 20 tick 一次），避免每 tick 做 O(n) 世界查询的性能开销。</p>
 */
public final class RtsProgressRefresher {

    /**
     * Blueprint progress refresh throttle: records the tick count of the last refresh per player.
     */
    private static final Map<UUID, Long> BLUEPRINT_REFRESH_TICK = new HashMap<>();

    /** Blueprint progress refresh throttle interval (ticks). */
    private static final long BLUEPRINT_REFRESH_INTERVAL = 20;

    private RtsProgressRefresher() {
    }

    /**
     * Clears blueprint refresh throttle cache, preventing memory leaks after player disconnect.
     */
    public static void clearPlayerCache(UUID playerUuid) {
        if (playerUuid != null) {
            BLUEPRINT_REFRESH_TICK.remove(playerUuid);
        }
    }

    /**
     * 刷新工作流进度。
     *
     * <p><b>放置进度不由本方法刷新</b>：范围放置/形状建造的进度由
     * {@link com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch} 中的
     * 动作计数（{@code placedPositions.size()}）驱动，与破坏流程保持一致，无需世界扫描覆盖。
     * 世界扫描覆盖（旧 {@code refreshPlacementProgress}）存在重复计数/误算已有方块/chunk 未加载回退等缺陷，
     * 会导致进度条与实际放置进度对不上，已于链路检查中移除。</p>
     *
     * <p>本方法仅负责<b>蓝图</b>工作流进度刷新（节流：每玩家每 20 tick 一次），
     * 处理「已放置的蓝图方块被挖掉后重新入队」的恢复检测。</p>
     */
    public static void refreshWorkflowProgress(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) return;
        refreshBlueprintProgress(player);
    }

    // ======================================================================
    //  Blueprint progress (throttled)
    // ======================================================================

    /**
     * 扫描已放置但被挖掉的蓝图方块，重新放回队列待重新放置。
     * 节流：每玩家最多每 20 tick 扫描一次。
     * <p>公开供 {@link com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch}
     * 在 tick 末尾调用（仅刷新蓝图，放置进度由动作计数驱动）。</p>
     */
    public static void refreshBlueprintProgress(ServerPlayer player) {
        UUID puid = player.getUUID();
        long currentTick = player.serverLevel().getGameTime();
        Long lastRefresh = BLUEPRINT_REFRESH_TICK.get(puid);
        boolean shouldScan = lastRefresh == null || (currentTick - lastRefresh) >= BLUEPRINT_REFRESH_INTERVAL;
        if (!shouldScan) return;
        BLUEPRINT_REFRESH_TICK.put(puid, currentTick);

        var engine = RtsWorkflowEngine.getInstance();
        for (var status : engine.getAllProgress(player)) {
            if (!status.isActive() || status.type() != RtsWorkflowType.BLUEPRINT_BUILD) continue;
            int entryId = status.entryId();
            PipelineContext pipeCtx = TickablePipelineRegistry.findContextByWorkflowEntry(player, entryId);
            if (!(pipeCtx instanceof BlueprintContext bctx)) continue;

            List<BlockPlacementPlanner.PlacementPlan> plans = bctx.getPlacementPlans();
            LinkedList<Integer> remaining = bctx.getRemainingQueue();
            if (plans == null || remaining == null || plans.isEmpty()) continue;

            ServerLevel level = player.serverLevel();
            int total = plans.size();
            Set<Integer> remainingSet = new HashSet<>(remaining);
            LinkedList<Integer> backToQueue = new LinkedList<>();
            int actualPlaced = 0;

            for (int idx = 0; idx < total; idx++) {
                BlockPlacementPlanner.PlacementPlan plan = plans.get(idx);
                if (plan == null) continue;
                if (remainingSet.contains(idx)) continue;
                if (!level.hasChunkAt(plan.target())) continue;

                BlockState current = level.getBlockState(plan.target());
                if (current.getBlock() == plan.state().getBlock()) {
                    actualPlaced++;
                } else {
                    backToQueue.add(idx);
                }
            }

            remaining.addAll(backToQueue);
            remaining.removeIf(idx -> {
                BlockPlacementPlanner.PlacementPlan plan = plans.get(idx);
                if (plan == null) return false;
                if (!level.hasChunkAt(plan.target())) return false;
                return level.getBlockState(plan.target()).getBlock() == plan.state().getBlock();
            });

            bctx.setPlacedCount(actualPlaced);
            bctx.setRemainingQueue(remaining);
            BlueprintPersistence.saveToEntry(player, entryId, bctx);
            int refreshPlacedCount = actualPlaced;
            engine.from(player, entryId).ifPresent(token -> token.setCompletedBlocks(refreshPlacedCount));
        }
    }

    // ======================================================================
    //  Shared helper methods
    // ======================================================================

    /**
     * Counts the total amount of template-matching items in the player's main inventory.
     */
    public static long countItemsInPlayerInventory(ServerPlayer player, ItemStack template) {
        if (player == null || template == null || template.isEmpty()) return 0;
        boolean includePlayerInventory = RtsStoragePageBuilder.shouldIncludePlayerMainInventoryInStorageView(player,
                RtsServer.get().session().getIfPresent(player));
        if (!includePlayerInventory) return 0;

        int start = RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
        int end = RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
        long count = 0;
        for (int slot = start; slot < end; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, template)) {
                count = RtsCountUtil.saturatedAdd(count, stack.getCount());
            }
        }
        return count;
    }
}
