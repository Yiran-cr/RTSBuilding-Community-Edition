package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.network.resume.S2CResumeScanPayload;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlockPlacementPlanner.PlacementPlan;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.service.destruction.RtsDestructionBatch;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch.PlaceBatchJob;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 工作流恢复服务：扫描暂停工作流的剩余方块/冲突/材料需求，并把恢复动作
 * （开始 / 跳过 / 覆盖）分发给蓝图、普通放置或破坏工作流的对应处理逻辑。
 *
 * <p>策略语义：0=开始（无冲突直接恢复），1=跳过（冲突位置按跳过处理继续放置），
 * 2=覆盖（先用原生破坏逻辑破坏冲突方块再放置）。对破坏工作流，三种策略均视为
 * 「继续恢复」（破坏不存在冲突概念，恢复动作交给 {@link RtsDestructionBatch} 处理）。</p>
 */
public final class ResumeWorkflowService {

    private ResumeWorkflowService() {
    }

    // ==================== 扫描 ====================

    /**
     * 扫描指定工作流的恢复数据；无法定位时返回 {@code null}。
     * 依次尝试蓝图工作流、普通放置作业、破坏作业。
     */
    @Nullable
    public static S2CResumeScanPayload scan(ServerPlayer player, int workflowEntryId) {
        if (player == null) return null;

        PipelineContext pipeCtx = TickablePipelineRegistry.findContextByWorkflowEntry(player, workflowEntryId);
        com.rtsbuilding.rtsbuilding.RtsbuildingMod.LOGGER.info("[Resume] scan entryId={} pipeCtx={}",
                workflowEntryId, pipeCtx == null ? "null" : pipeCtx.getClass().getSimpleName());
        if (pipeCtx instanceof BlueprintContext bctx) {
            return scanBlueprint(player, bctx, workflowEntryId);
        }
        S2CResumeScanPayload pending = scanPendingJob(player, workflowEntryId);
        if (pending != null) {
            return pending;
        }
        // 破坏作业挂起（工具耐久不足）不在放置作业队列中，单独扫描
        return scanDestroyJob(player, workflowEntryId);
    }

    /** 蓝图工作流：多材料清单 + 剩余/冲突位置。 */
    @Nullable
    private static S2CResumeScanPayload scanBlueprint(ServerPlayer player, BlueprintContext bctx, int entryId) {
        List<PlacementPlan> plans = bctx.getPlacementPlans();
        LinkedList<Integer> remaining = bctx.getRemainingQueue();
        if (plans == null || remaining == null || remaining.isEmpty()) return null;

        var level = player.serverLevel();
        List<Long> remainingPos = new ArrayList<>();
        List<Long> conflictPos = new ArrayList<>();
        int alreadyPlaced = 0;
        int conflictCount = 0;
        for (int idx : remaining) {
            PlacementPlan plan = plans.get(idx);
            if (plan == null) continue;
            if (!level.hasChunkAt(plan.target())) continue;
            BlockState current = level.getBlockState(plan.target());
            if (current.getBlock() == plan.state().getBlock()) {
                alreadyPlaced++;
            } else if (!current.isAir() && !current.canBeReplaced()) {
                conflictCount++;
                conflictPos.add(plan.target().asLong());
            } else {
                remainingPos.add(plan.target().asLong());
            }
        }

        int totalRemaining = remaining.size();
        int neededItems = totalRemaining - alreadyPlaced;
        long bottleneckAvailable = bottleneckAvailable(player, plans, remaining, neededItems);
        long missingItems = Math.max(0, neededItems - bottleneckAvailable);

        // 材料清单
        RtsBlueprintJobService.RtsBlueprintMaterialsScan mats = RtsBlueprintJobService.scanBlueprintMaterials(player, entryId);
        List<String> matIds = new ArrayList<>();
        List<String> matLabels = new ArrayList<>();
        List<Integer> matReq = new ArrayList<>();
        List<Long> matAvail = new ArrayList<>();
        if (mats != null) {
            matIds = mats.itemIds();
            matLabels = mats.itemLabels();
            matReq = mats.required();
            matAvail = mats.available();
        }

        return new S2CResumeScanPayload(
                entryId, true,
                totalRemaining, alreadyPlaced, conflictCount,
                neededItems, missingItems,
                "", "",
                matIds, matLabels, matReq, matAvail,
                remainingPos, conflictPos);
    }

    /** 计算瓶颈材料可支撑的方块数（创造模式恒足够）。 */
    private static long bottleneckAvailable(ServerPlayer player, List<PlacementPlan> plans,
                                            LinkedList<Integer> remaining, int neededItems) {
        if (player.isCreative()) return Integer.MAX_VALUE;
        java.util.Map<net.minecraft.resources.ResourceLocation, Integer> matReqs = new java.util.LinkedHashMap<>();
        for (int idx : remaining) {
            PlacementPlan plan = plans.get(idx);
            if (plan == null) continue;
            for (net.minecraft.world.item.Item item : plan.items()) {
                var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
                if (id != null) matReqs.merge(id, 1, Integer::sum);
            }
        }
        long minAvailable = Long.MAX_VALUE;
        for (var e : matReqs.entrySet()) {
            int req = e.getValue();
            if (req <= 0) continue;
            long avail = RtsBlueprintJobService.countMaterial(player, e.getKey());
            long perBlock = (req + neededItems - 1) / neededItems;
            long blocksPossible = perBlock > 0 ? avail / perBlock : Long.MAX_VALUE;
            minAvailable = Math.min(minAvailable, blocksPossible);
        }
        return minAvailable == Long.MAX_VALUE ? Integer.MAX_VALUE : minAvailable;
    }

    /** 普通放置工作流：单物品 + 剩余/冲突位置。 */
    @Nullable
    private static S2CResumeScanPayload scanPendingJob(ServerPlayer player, int entryId) {
        RtsStorageSession session = RtsServer.get().session().getIfPresent(player);
        if (session == null) return null;
        RtsResumeScanResult r = RtsPendingPlacementService.scanPendingJob(player, session, entryId);
        if (r == null) return null;

        PlaceBatchJob job = findPendingJob(session, entryId);
        List<Long> remainingPos = new ArrayList<>();
        List<Long> conflictPos = new ArrayList<>();
        int alreadyPlaced = 0;
        int conflictCount = 0;
        if (job != null) {
            net.minecraft.world.level.block.Block expectedBlock = expectedBlockOf(job);
            var level = player.serverLevel();
            for (BlockPos pos : job.remainingPositions()) {
                if (!level.hasChunkAt(pos)) continue;
                BlockState current = level.getBlockState(pos);
                boolean same = expectedBlock != null && current.getBlock() == expectedBlock;
                if (same) {
                    alreadyPlaced++;
                } else if (!current.isAir() && !current.canBeReplaced()) {
                    conflictCount++;
                    conflictPos.add(pos.asLong());
                } else {
                    remainingPos.add(pos.asLong());
                }
            }
        }

        return new S2CResumeScanPayload(
                entryId, false,
                r.totalRemaining(), Math.max(alreadyPlaced, r.alreadyPlacedCount()), Math.max(conflictCount, r.conflictCount()),
                r.neededItems(), r.missingItems(),
                r.itemId(), r.itemLabel(),
                List.of(r.itemId()), List.of(r.itemLabel()),
                List.of(r.neededItems()), List.of(r.availableItems()),
                remainingPos, conflictPos);
    }

    /**
     * 破坏工作流（区域破坏）扫描：作业挂起在 {@code pendingDestroyJobs}，不依赖材料，
     * 只统计剩余未破坏位置。工具仍即将损坏时置 {@code missingItems=1} 让客户端
     * 「继续」按钮灰化（需要先准备/修复工具再恢复）。
     */
    @Nullable
    private static S2CResumeScanPayload scanDestroyJob(ServerPlayer player, int entryId) {
        RtsStorageSession session = RtsServer.get().session().getIfPresent(player);
        if (session == null) return null;
        RtsDestructionBatch.DestructionJob job = findPendingDestroyJob(session, entryId);
        com.rtsbuilding.rtsbuilding.RtsbuildingMod.LOGGER.info("[Resume] scanDestroyJob entryId={} pendingDestroy={} found={}",
                entryId, session.destruction.pendingDestroyJobs.size(), job != null);
        if (job == null) return null;

        var level = player.serverLevel();
        List<Long> remainingPos = new ArrayList<>();
        int alreadyDestroyed = 0;
        for (BlockPos pos : job.remainingPositions()) {
            if (!level.hasChunkAt(pos)) continue;
            BlockState current = level.getBlockState(pos);
            if (current.isAir()) {
                alreadyDestroyed++;
            } else {
                remainingPos.add(pos.asLong());
            }
        }

        // 破坏不需要材料：工具可用则按钮可用（missingItems=0），工具近损坏则灰化
        boolean toolReady = !session.mining.miningToolProtectionEnabled
                || !RtsMiningValidator.isToolNearBreak(player, session);
        long missing = toolReady ? 0 : 1;
        int totalRemaining = job.remainingPositions().size();

        return new S2CResumeScanPayload(
                entryId, false,
                totalRemaining, alreadyDestroyed, 0,
                totalRemaining, missing,
                "", "",
                List.of(), List.of(), List.of(), List.of(),
                remainingPos, List.of());
    }

    // ==================== 恢复动作 ====================

    /**
     * 对指定工作流执行恢复动作。
     *
     * @param strategy 0=开始，1=跳过，2=覆盖
     */
    public static boolean apply(ServerPlayer player, int workflowEntryId, byte strategy) {
        if (player == null) return false;

        PipelineContext pipeCtx = TickablePipelineRegistry.findContextByWorkflowEntry(player, workflowEntryId);
        if (pipeCtx instanceof BlueprintContext) {
            return applyBlueprint(player, workflowEntryId, strategy);
        }
        RtsStorageSession session = RtsServer.get().session().getIfPresent(player);
        if (session == null) return false;
        // 覆盖：先破坏冲突方块；跳过/开始：直接恢复（冲突按跳过处理）
        if (RtsPendingPlacementService.resumeWithStrategy(player, session, strategy == 2 ? 1 : 0, workflowEntryId)) {
            return true;
        }
        // 放置作业不存在 → 尝试恢复破坏作业（策略无意义，统一走继续恢复）
        return applyDestroyJob(player, workflowEntryId);
    }

    /**
     * 恢复挂起的破坏作业：确认作业存在后交给 {@link RtsDestructionBatch#tryResumePendingDestroyJobs}
     * 处理（工具借还 + 移回活跃队列 + {@code token.resume()}）。
     */
    private static boolean applyDestroyJob(ServerPlayer player, int workflowEntryId) {
        RtsStorageSession session = RtsServer.get().session().getIfPresent(player);
        if (session == null) return false;
        if (findPendingDestroyJob(session, workflowEntryId) == null) {
            return false;
        }
        RtsDestructionBatch.tryResumePendingDestroyJobs(player, session);
        return true;
    }

    /** 蓝图恢复：覆盖先破坏冲突方块；开始/跳过直接恢复（蓝图管线对冲突位置自然跳过）。 */
    private static boolean applyBlueprint(ServerPlayer player, int workflowEntryId, byte strategy) {
        if (strategy == 2) {
            RtsStorageSession session = RtsServer.get().session().getOrCreate(player);
            PipelineContext pipeCtx = TickablePipelineRegistry.findContextByWorkflowEntry(player, workflowEntryId);
            if (pipeCtx instanceof BlueprintContext bctx) {
                var plans = bctx.getPlacementPlans();
                var remaining = bctx.getRemainingQueue();
                if (plans != null && remaining != null) {
                    for (int idx : remaining) {
                        PlacementPlan plan = plans.get(idx);
                        if (plan == null) continue;
                        BlockPos target = plan.target();
                        BlockState current = player.serverLevel().getBlockState(target);
                        if (!current.isAir() && !current.canBeReplaced()
                                && current.getBlock() != plan.state().getBlock()) {
                            RtsMiningStateMachine.destroyMinedBlock(player, session, target, player.getInventory().selected);
                        }
                    }
                }
            }
        }
        return RtsBlueprintJobService.resumeBlueprintWorkflow(player, workflowEntryId);
    }

    // ==================== 工具 ====================

    /** 解析普通放置作业期望放置的方块（非方块物品返回 null）。 */
    @Nullable
    private static net.minecraft.world.level.block.Block expectedBlockOf(PlaceBatchJob job) {
        String itemId = job.itemId();
        if (itemId == null || itemId.isBlank()) return null;
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (id == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) return null;
        if (net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id) instanceof net.minecraft.world.item.BlockItem bi) {
            net.minecraft.world.level.block.Block b = bi.getBlock();
            return b == net.minecraft.world.level.block.Blocks.AIR ? null : b;
        }
        return null;
    }

    @Nullable
    private static PlaceBatchJob findPendingJob(RtsStorageSession session, int workflowEntryId) {
        for (PlaceBatchJob job : session.placement.pendingJobs) {
            if (job.workflowEntryId() == workflowEntryId) {
                return job;
            }
        }
        return null;
    }

    /** 在挂起的破坏作业队列中按工作流条目 ID 定位作业。 */
    @Nullable
    private static RtsDestructionBatch.DestructionJob findPendingDestroyJob(RtsStorageSession session, int workflowEntryId) {
        if (session == null || session.destruction.pendingDestroyJobs.isEmpty()) {
            return null;
        }
        for (RtsDestructionBatch.DestructionJob job : session.destruction.pendingDestroyJobs) {
            if (job.workflowEntryId() == workflowEntryId) {
                return job;
            }
        }
        return null;
    }
}
