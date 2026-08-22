package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.network.NetworkConstants;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.RtsService;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.PlaceContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementHelper;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link RtsPlacementServiceImpl} 的默认实现——处理 RTS 模式下的远程方块放置操作。
 *
 * <p>该实现类通过 {@link com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry}
 * 执行放置流程：
 * <ul>
 *   <li>单方块放置（{@code PLACE_SINGLE}）</li>
 *   <li>快速建造（{@code QUICK_BUILD}）</li>
 *   <li>批量放置（{@code PLACE_BATCH}）</li>
 * </ul>
 * 同时管理挂起放置作业的恢复、方块旋转和进度查询。
 * 当工作流不可用时回退到直接入队（{@link com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch}）。
 */
public final class RtsPlacementServiceImpl implements RtsService {

    private final RtsServer server = RtsServer.get();

    public void placeSelected(ServerPlayer player, BlockPos clickedPos, Direction face,
                              double hitX, double hitY, double hitZ, byte rotateSteps,
                              boolean forcePlace, boolean skipIfOccupied, String itemId,
                              ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ,
                              double rayDirX, double rayDirY, double rayDirZ,
                              boolean quickBuild, boolean forceEmptyHand) {
        double hitOffsetX = clickedPos == null ? 0.5D : hitX - clickedPos.getX();
        double hitOffsetY = clickedPos == null ? 0.5D : hitY - clickedPos.getY();
        double hitOffsetZ = clickedPos == null ? 0.5D : hitZ - clickedPos.getZ();
        RtsStorageSession session = player == null ? null : server.session().getIfPresent(player);

        if (player != null && session != null && !forceEmptyHand) {
            PipelineRegistry.execute(quickBuild ? RtsWorkflowType.QUICK_BUILD : RtsWorkflowType.PLACE_SINGLE,
                    PlaceContext.builder(player)
                            .clickedPositions(clickedPos == null ? List.of() : List.of(clickedPos))
                            .face(face)
                            .hitOffsetX(hitOffsetX)
                            .hitOffsetY(hitOffsetY)
                            .hitOffsetZ(hitOffsetZ)
                            .rotateSteps(rotateSteps)
                            .forcePlace(forcePlace)
                            .skipIfOccupied(skipIfOccupied)
                            .itemId(itemId)
                            .itemPrototype(itemPrototype)
                            .rayOriginX(rayOriginX)
                            .rayOriginY(rayOriginY)
                            .rayOriginZ(rayOriginZ)
                            .rayDirX(rayDirX)
                            .rayDirY(rayDirY)
                            .rayDirZ(rayDirZ)
                            .quickBuild(quickBuild)
                            .forceEmptyHand(false)
                            .totalBlocks(1)
                            .build());
            return;
        }

        // 回退：forceEmptyHand 或无会话 — 入队但不经过工作流
        RtsPlacementBatch.enqueuePlaceBatch(
                player,
                session,
                clickedPos == null ? List.of() : List.of(clickedPos),
                face,
                hitOffsetX,
                hitOffsetY,
                hitOffsetZ,
                rotateSteps,
                forcePlace,
                skipIfOccupied,
                itemId,
                itemPrototype,
                rayOriginX,
                rayOriginY,
                rayOriginZ,
                rayDirX,
                rayDirY,
                rayDirZ,
                quickBuild,
                forceEmptyHand,
                true,
                -1);
    }

    public void enqueuePlaceBatch(ServerPlayer player, List<BlockPos> clickedPositions, Direction face,
                                  double hitOffsetX, double hitOffsetY, double hitOffsetZ, byte rotateSteps,
                                  boolean forcePlace, boolean skipIfOccupied, String itemId,
                                  ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ,
                                  double rayDirX, double rayDirY, double rayDirZ, boolean quickBuild) {
        RtsStorageSession session = player == null ? null : server.session().getIfPresent(player);

        if (player == null || session == null || clickedPositions == null || clickedPositions.isEmpty()) {
            // 无会话或空位置：静默忽略，无需入队
            return;
        }
        List<BlockPos> sanitized = new ArrayList<>(Math.min(clickedPositions.size(), NetworkConstants.MAX_POSITIONS));
        for (BlockPos pos : clickedPositions) {
            if (pos != null && RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                sanitized.add(pos.immutable());
                if (sanitized.size() >= NetworkConstants.MAX_POSITIONS) {
                    break;
                }
            }
        }
        if (sanitized.isEmpty()) {
            return;
        }

        PipelineRegistry.execute(RtsWorkflowType.PLACE_BATCH,
                PlaceContext.builder(player)
                        .clickedPositions(sanitized)
                        .face(face)
                        .hitOffsetX(hitOffsetX)
                        .hitOffsetY(hitOffsetY)
                        .hitOffsetZ(hitOffsetZ)
                        .rotateSteps(rotateSteps)
                        .forcePlace(forcePlace)
                        .skipIfOccupied(skipIfOccupied)
                        .itemId(itemId == null ? "" : itemId)
                        .itemPrototype(itemPrototype)
                        .rayOriginX(rayOriginX)
                        .rayOriginY(rayOriginY)
                        .rayOriginZ(rayOriginZ)
                        .rayDirX(rayDirX)
                        .rayDirY(rayDirY)
                        .rayDirZ(rayDirZ)
                        .quickBuild(quickBuild)
                        .forceEmptyHand(false)
                        .sendRemoteHint(true)
                        .totalBlocks(sanitized.size())
                        .build());
    }

    public int submitPendingPlacement(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        RtsStorageSession session = server.session().getIfPresent(player);
        if (session == null || session.placement.pendingJobs.isEmpty()) {
            return 0;
        }
        int count = RtsPendingPlacementService.resumeAllPendingJobs(player, session);
        if (count > 0) {
            player.displayClientMessage(
                    Component.literal("Resumed " + count + " pending placement job(s)."), true);
        } else {
            player.displayClientMessage(
                    Component.literal("No pending placements can be resumed — insufficient items."), true);
        }
        return count;
    }

    public void rotateBlock(ServerPlayer player, BlockPos pos) {
        rotateBlock(player, pos, 90, false);
    }

    /**
     * 旋转单个方块（方向旋转模式）。
     *
     * @param player  执行者
     * @param pos     目标位置
     * @param degrees 每次旋转角度（90° 整数倍，内部归一化为步数）
     * @param pitch   {@code true} 上下翻转（绕水平轴，轴随相机朝向选择 X/Z），
     *                {@code false} 水平旋转（绕竖直 Y 轴）
     */
    public void rotateBlock(ServerPlayer player, BlockPos pos, int degrees, boolean pitch) {
        RtsStorageSession session = server.session().getIfPresent(player);
        if (session == null || !RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
            return;
        }
        int steps = stepsForDegrees(degrees);
        if (pitch) {
            boolean aboutZ = isPitchAboutZ(player);
            RtsPlacementHelper.rotatePlacedBlock(player.serverLevel(), pos,
                    0, aboutZ ? 0 : steps, aboutZ ? steps : 0);
        } else {
            RtsPlacementHelper.rotatePlacedBlock(player.serverLevel(), pos, steps, 0, 0);
        }
    }

    /**
     * 批量旋转框选区域 [min, max) 内的方块（方向旋转模式 + 框选模式）。
     * 每个位置都经过 {@link RtsLinkedStorageResolver#canAccessWorldTarget} 校验
     * （相机动作范围 / 领地保护 / 世界边界），框体积超过
     * {@link NetworkConstants#MAX_POSITIONS} 时拒绝处理。
     */
    public void rotateArea(ServerPlayer player, BlockPos min, BlockPos max,
                           int degrees, boolean pitch) {
        if (player == null || min == null || max == null) {
            return;
        }
        long volume = (long) (max.getX() - min.getX())
                * (max.getY() - min.getY())
                * (max.getZ() - min.getZ());
        if (volume <= 0 || volume > NetworkConstants.MAX_POSITIONS) {
            return;
        }
        int steps = stepsForDegrees(degrees);
        boolean aboutZ = pitch && isPitchAboutZ(player);
        int ySteps = pitch ? 0 : steps;
        int xSteps = pitch && !aboutZ ? steps : 0;
        int zSteps = pitch && aboutZ ? steps : 0;
        ServerLevel level = player.serverLevel();
        for (int y = min.getY(); y < max.getY(); y++) {
            for (int z = min.getZ(); z < max.getZ(); z++) {
                for (int x = min.getX(); x < max.getX(); x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                        continue;
                    }
                    RtsPlacementHelper.rotatePlacedBlock(level, pos, ySteps, xSteps, zSteps);
                }
            }
        }
    }

    /** 把旋转角度（90° 整数倍）归一化为 0~3 步数。 */
    private static int stepsForDegrees(int degrees) {
        return Math.floorMod(Math.round(degrees / 90.0f), 4);
    }

    /**
     * 上下翻转的旋转轴选择（相机相对）：绕与相机水平朝向垂直的水平轴旋转，
     * 使方块朝相机前后方向上下翻转，直觉上“面向视角翻转”。相机朝向由
     * {@link RtsCameraManager#getCameraYaw} 权威位置（客户端 10Hz 上报）决定。
     *
     * @return {@code true} 绕 Z 轴旋转（相机朝 X 方向时），{@code false} 绕 X 轴
     */
    private static boolean isPitchAboutZ(ServerPlayer player) {
        float yaw = RtsCameraManager.getCameraYaw(player);
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        return Math.abs(fx) >= Math.abs(fz);
    }

    public int getPlaceBatchTotalBlocks(ServerPlayer player) {
        var engine = RtsWorkflowEngine.getInstance();
        return engine.getAllProgress(player).stream()
                .filter(d -> d.type() == RtsWorkflowType.PLACE_BATCH || d.type() == RtsWorkflowType.QUICK_BUILD)
                .mapToInt(RtsWorkflowStatus::totalBlocks)
                .sum();
    }

    public int getPlaceBatchCompletedBlocks(ServerPlayer player) {
        var engine = RtsWorkflowEngine.getInstance();
        return engine.getAllProgress(player).stream()
                .filter(d -> d.type() == RtsWorkflowType.PLACE_BATCH || d.type() == RtsWorkflowType.QUICK_BUILD)
                .mapToInt(RtsWorkflowStatus::completedBlocks)
                .sum();
    }

    public int getPlaceBatchRemainingBlocks(ServerPlayer player) {
        var engine = RtsWorkflowEngine.getInstance();
        return engine.getAllProgress(player).stream()
                .filter(d -> d.type() == RtsWorkflowType.PLACE_BATCH || d.type() == RtsWorkflowType.QUICK_BUILD)
                .mapToInt(RtsWorkflowStatus::remainingBlocks)
                .sum();
    }

    public String getPlaceBatchItemId(ServerPlayer player) {
        if (player == null) return "";
        RtsStorageSession session = server.session().getIfPresent(player);
        if (session == null) return "";
        if (!session.placement.placeBatchJobs.isEmpty()) {
            return session.placement.placeBatchJobs.peekFirst().itemId();
        }
        if (!session.placement.pendingJobs.isEmpty()) {
            return session.placement.pendingJobs.peekFirst().itemId();
        }
        return "";
    }
}
