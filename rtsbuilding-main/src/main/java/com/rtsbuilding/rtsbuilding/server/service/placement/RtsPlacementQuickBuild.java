package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsCarriedSyncPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.platform.Platform;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsDropAbsorber;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

/**
 * RTS 快速建造（预解析状态）放置逻辑，用于储存浏览器批处理作业。
 *
 * <p>快速建造为每个批处理作业预计算一个 {@link StatePlacementPlan}，
 * 使得作业内所有目标位置共享同一组解析后的方块状态、点击上下文模板
 * 和物品提取规则。这显著消除了大批量放置中重复的
 * {@link BlockPlaceContext} 创建和状态查找开销。
 *
 * <p><b>核心方法：</b>
 * <ul>
 *   <li>{@link #resolveStatePlacementPlan(ServerPlayer, RtsPlacementBatch.PlaceBatchJob)} —
 *       从批处理作业的第一个位置解析放置计划，缓存物品、模板堆叠、旋转状态和来源 ID</li>
 *   <li>{@link #placeStateBatchEntry(ServerPlayer, RtsStorageSession, BlockPos, StatePlacementPlan)} —
 *       使用预解析计划放置单个方块，提取物品、设置方块、触发动画/声音</li>
 *   <li>{@link #canPlaceStateAt(ServerLevel, ServerPlayer, BlockPos, BlockState)} —
 *       检查目标位置是否可以放置给定方块状态（空气/可替换检查 + 碰撞检测）</li>
 * </ul>
 *
 * <p><b>内部记录：</b>{@link StatePlacementPlan} 包含物品引用、单次计数模板堆叠、
 * 完全旋转后的方块状态、是否从储存提取的标志和物品 ID。
 *
 * <p><b>设计原则：</b>此类故意不处理交互式主手放置、批处理作业生命周期管理、
 * 声音播放或提取编排——这些职责位于 {@code RtsPlacementExecutor}、
 * {@code RtsPlacementBatch}、{@code RtsPlacementSound} 和 {@code RtsPlacementExtractor} 中。
 */
public final class RtsPlacementQuickBuild {

    private RtsPlacementQuickBuild() {
    }

    /**
     * 从批处理作业的第一个位置解析 {@link StatePlacementPlan}。
     * 该计划缓存物品、单个计数的模板堆叠、最终的旋转方块状态、
     * 是否使用选中的储存物品以及来源物品 ID，
     * 以便同一作业中的每个位置重用相同的计划。
     *
     * <p>当玩家、作业或放置上下文无效时返回 {@code null}。
     */
    public static StatePlacementPlan resolveStatePlacementPlan(ServerPlayer player,
                                                               RtsPlacementBatch.PlaceBatchJob job) {
        if (player == null || job == null || !job.quickBuild()) {
            return null;
        }

        // 完全改为使用储存空间的方块进行放置。
        // 必须存在有效的 itemId，否则拒绝
        String jobItemId = job.itemId();
        if (jobItemId == null || jobItemId.isBlank()) {
            return null;
        }

        ResourceLocation id = ResourceLocation.tryParse(jobItemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        ItemStack templateStack = job.itemPrototype();
        if (templateStack.isEmpty()) {
            templateStack = new ItemStack(item);
        }

        if (!(item instanceof BlockItem blockItem)) {
            return null;
        }

        BlockPos templatePos = job.templatePosition();
        if (templatePos == null || job.face() == null || !player.serverLevel().hasChunkAt(templatePos)) {
            return null;
        }
        templateStack.setCount(1);
        BlockPlaceContext context = new BlockPlaceContext(
                player.serverLevel(),
                player,
                InteractionHand.MAIN_HAND,
                templateStack,
                job.templateHit(templatePos));
        BlockState state = blockItem.getBlock().getStateForPlacement(context);
        if (state == null) {
            return null;
        }

        ResourceLocation sourceId = BuiltInRegistries.ITEM.getKey(item);
        if (sourceId == null) {
            return null;
        }
        return new StatePlacementPlan(
                item,
                templateStack,
                RtsPlacementHelper.rotateState(state, job.rotateSteps()),
                sourceId.toString());
    }

    /**
     * 放置结果语义，供批处理作业按结果更新进度与计数。
     *
     * <ul>
     *   <li><b>CONTINUE</b>：位置已处理（交互式立即落位路径），调用方仍需用
     *       {@link RtsPlacementHelper#detectPlacedPos} 检测实际放置位置；</li>
     *   <li><b>PLACED</b>：已成功调度放置（快速建造为延迟落位，物品已扣，动画结束将真正 setBlock）——计为已放置；</li>
     *   <li><b>SKIPPED</b>：该位置被跳过（占用/无法放置等）——计为失败（failed）；</li>
     *   <li><b>STOP</b>：无法继续（无存储/物品不足/参数无效）——中止当前作业。</li>
     * </ul>
     */
    public enum PlaceOutcome {
        CONTINUE,
        PLACED,
        SKIPPED,
        STOP
    }

    /**
     * 使用预解析的 {@link StatePlacementPlan} 放置单个方块。
     * 这是快速建造批处理作业采用的快速路径：它提取物品一次
     * （或重用主手堆叠），延迟落位（动画结束才 setBlock），并触发成功效果。
     *
     * <p><b>进度语义</b>：快速建造走 {@link RtsBlockAnimationCommitter#schedulePlace} 延迟落位——
     * 调度成功（物品已扣）即返回 {@link PlaceOutcome#PLACED}，调用方应据此计入进度，
     * 而<b>不能</b>在调度后立即读取世界状态判定（方块要等动画周期结束后才 setBlock，立即检测必然落空）。</p>
     *
     * <p>替换模式（{@code replace=true}）：目标位置被非空气方块占用且确认物品可用时，
     * 先逐个破坏该方块（使用项目原生破坏逻辑 {@link RtsMiningStateMachine#destroyMinedBlock}），
     * 再放置新方块——形成"破坏一个、放置一个"的交替替换效果，避免
     * 一次性清空区域导致中间状态混乱。物品不可用时不会破坏目标。</p>
     *
     * @param replace              替换模式：允许覆盖已有方块（先破坏再放置）
     * @param onCommitFinished     延迟落位最终完成的回调（成功=true/失败或放弃=false，恰好调用一次）；
     *                             {@code null} 表示不关心落位结果（此时调用方按调度即计）。
     * @return 放置结果（{@link PlaceOutcome}）：PLACED=已调度放置 / SKIPPED=跳过该位置 / STOP=中止作业
     */
    public static PlaceOutcome placeStateBatchEntry(ServerPlayer player, RtsStorageSession session, BlockPos targetPos,
                                                    StatePlacementPlan plan, boolean replace,
                                                    @Nullable Consumer<Boolean> onCommitFinished) {
        if (session == null || targetPos == null || plan == null) {
            return PlaceOutcome.STOP;
        }
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, targetPos)) {
            return PlaceOutcome.STOP;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        ServerLevel level = player.serverLevel();
        boolean occupied = !canPlaceStateAt(level, player, targetPos, plan.state());
        if (occupied && !replace) {
            // 非替换模式：位置被占用，跳过（原行为）
            return PlaceOutcome.SKIPPED;
        }

        // 先提取物品（确认可用；替换模式避免“破坏后无物可放”）
        ItemStack placementStack = plan.templateStack();
        ItemStack extracted = ItemStack.EMPTY;
        boolean refundExtractedOnFailure = false;
        boolean fromCarried = false;
        List<IItemHandler> extractHandlers = List.of();
        List<IItemHandler> insertHandlers = List.of();
        // 完全改为使用储存空间的方块进行放置
        {
            List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
            boolean includePlayerMainInventory = RtsStoragePageBuilder.shouldIncludePlayerMainInventoryInStorageView(player, session);
            boolean creativeSource = player.isCreative();
            if (activeLinked.isEmpty() && !includePlayerMainInventory && !creativeSource) {
                return PlaceOutcome.STOP;
            }
            extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
            insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);
            // 方案1：优先从 carried 扣减（点击网格拿起的物品直接可用于快速建造）
            if (!creativeSource) {
                extracted = RtsPlacementExtractor.takeOneFromCarried(player, plan.item(), plan.templateStack());
                if (!extracted.isEmpty()) {
                    fromCarried = true;
                    player.containerMenu.broadcastChanges();
                }
            }
            if (extracted.isEmpty()) {
                extracted = creativeSource
                        ? RtsPlacementExtractor.creativeStack(plan.item(), plan.templateStack())
                        : includePlayerMainInventory
                                ? RtsPlacementExtractor.extractSelectedFromNetworkCached(player, extractHandlers, plan.item(), plan.templateStack())
                                : RtsPlacementExtractor.extractSelectedFromLinked(extractHandlers, plan.item(), plan.templateStack());
                if (extracted.isEmpty()) {
                    // 物品不可用：不破坏目标，直接跳过（替换模式也不破坏）
                    return PlaceOutcome.STOP;
                }
                refundExtractedOnFailure = !creativeSource;
            }
            placementStack = extracted.copy();
            placementStack.setCount(1);
        }

        // 替换模式：确认有物品后再逐个破坏目标方块（使用项目原生破坏逻辑）
        if (occupied) {
            RtsMiningStateMachine.destroyMinedBlock(player, session, targetPos, player.getInventory().selected);
            // 破坏掉落物直接存入 RTS 储存空间（与正常挖掘一致）
            RtsDropAbsorber.absorbNearbyMinedDrops(player, targetPos, session);
            if (!canPlaceStateAt(level, player, targetPos, plan.state())) {
                // 破坏后仍无法放置（碰撞/世界边界等）：退款已提取物品并跳过
                refundExtracted(player, extracted, insertHandlers, fromCarried, refundExtractedOnFailure);
                return PlaceOutcome.SKIPPED;
            }
        }

        // 延迟落位（BuildingGadgets2「动画即落位」语义）：客户端收到动画包立即播放生长动画，
        // 服务端在动画周期结束后才真正 setBlock —— 方块"生长完成即出现"。
        // 落位前该位置保持空气/可替换状态；支持支撑依赖重试（对齐 BG2 retryList）：
        // 落位时 canSurvive 失败（支撑方块尚未落位，如墙先于其上的火把）→ 延迟重试一次；
        // 重试仍失败或 setBlock 失败 → 退回已提取的物品。
        // 需要被延迟回调捕获的局部变量均转为 final 拷贝。
        ItemStack commitPlacementStack = placementStack;
        ItemStack commitExtracted = extracted;
        boolean commitRefundOnFailure = refundExtractedOnFailure;
        boolean commitFromCarried = fromCarried;
        List<IItemHandler> commitExtractHandlers = extractHandlers;
        List<IItemHandler> commitInsertHandlers = insertHandlers;
        RtsBlockAnimationCommitter.schedulePlace(player, targetPos, plan.state(),
                () -> {
                    // 支撑依赖未就绪（邻居尚未落位）→ 请求延迟重试
                    if (!plan.state().canSurvive(level, targetPos)) {
                        return false;
                    }
                    // 落位本身不依赖玩家：玩家下线时仍 setBlock，保证已扣物品的方块不丢失
                    boolean placed = BlockPlacer.setBlock(level, targetPos, plan.state());
                    if (!placed) {
                        refundExtracted(player, commitExtracted, commitInsertHandlers, commitFromCarried, commitRefundOnFailure);
                        if (onCommitFinished != null) onCommitFinished.accept(false);
                        return true;
                    }
                    BlockState placedState = level.getBlockState(targetPos);
                    if (placedState.is(plan.state().getBlock())) {
                        BlockPlacer.applyQuickBuildBlockEntity(level, targetPos, commitPlacementStack, placedState, player);
                    }
                    // 完全改为使用储存空间的方块进行放置，不再从主手扣除
                    BlockPlacer.trackPlaced(level, targetPos);
                    // 落位成功：通知调用方（无论玩家在线与否，方块已真实出现）
                    if (onCommitFinished != null) onCommitFinished.accept(true);
                    // 玩家相关的后置逻辑（声音/页面/续货）仅在玩家仍在线时执行
                    if (!RtsBlockAnimationCommitter.isPlayerStillOnline(player)) {
                        return true;
                    }
                    RtsPlacementSound.playRemotePlacedBlockSound(player, level, targetPos);
                    RtsServer.get().page().recordRecentItem(session, plan.itemId(), S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
                    if (commitFromCarried) {
                        // 方案2：自动续货——放置成功消耗后从网络补回差额，carried 始终保持满组
                        RtsPlacementExtractor.replenishCarried(player, commitExtractHandlers, plan.item(), plan.templateStack());
                        // 同步权威 carried 状态（已被续货补充）给客户端
                        Platform.sendPacket(player, new S2CRtsCarriedSyncPayload(player.containerMenu.getCarried()));
                    }
                    return true;
                },
                () -> {
                    // 重试次数用尽仍无法落位：退回已提取的物品，并通知调用方落位失败
                    refundExtracted(player, commitExtracted, commitInsertHandlers, commitFromCarried, commitRefundOnFailure);
                    if (onCommitFinished != null) onCommitFinished.accept(false);
                });
        return PlaceOutcome.PLACED;
    }

    static boolean canPlaceStateAt(ServerLevel level, ServerPlayer player, BlockPos targetPos, BlockState state) {
        if (level == null || targetPos == null || state == null || !level.hasChunkAt(targetPos)) {
            return false;
        }
        BlockState current = level.getBlockState(targetPos);
        if (!current.isAir() && !current.canBeReplaced()) {
            return false;
        }
        CollisionContext collision = player == null ? CollisionContext.empty() : CollisionContext.of(player);
        return state.canSurvive(level, targetPos) && level.isUnobstructed(state, targetPos, collision);
    }

    /**
     * 放置失败/替换破坏后无法放置时，退回已提取的物品：
     * 从 carried 扣减的合并回 carried（剩余退回网络），否则按需退回链接存储。
     */
    private static void refundExtracted(ServerPlayer player, ItemStack extracted,
                                        List<IItemHandler> insertHandlers,
                                        boolean fromCarried, boolean refundExtractedOnFailure) {
        if (extracted.isEmpty()) {
            return;
        }
        if (fromCarried) {
            ItemStack remain = RtsPlacementExtractor.mergeIntoCarried(player, extracted);
            if (!remain.isEmpty()) {
                RtsTransferInserter.refundToLinked(insertHandlers, player, remain);
            }
            Platform.sendPacket(player, new S2CRtsCarriedSyncPayload(player.containerMenu.getCarried()));
        } else if (refundExtractedOnFailure) {
            RtsTransferInserter.refundToLinked(insertHandlers, player, extracted);
        }
    }

    /**
     * 快速建造路径的预计算放置计划。
     *
     * @param item                  要放置的方块物品
     * @param templateStack         单次计数模板堆叠（组件保留）
     * @param state                 完全旋转后的方块状态
     * @param itemId                用于最近物品追踪的字符串编码物品 ID
     */
    public record StatePlacementPlan(
            Item item,
            ItemStack templateStack,
            BlockState state,
            String itemId) {
        public StatePlacementPlan {
            templateStack = templateStack == null ? ItemStack.EMPTY : templateStack.copy();
            if (!templateStack.isEmpty()) {
                templateStack.setCount(1);
            }
        }
    }
}
