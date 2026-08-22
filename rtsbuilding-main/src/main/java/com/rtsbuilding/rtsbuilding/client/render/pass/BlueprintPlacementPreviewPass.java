package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.RenderPipeline;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.render.util.RtsAlphaVertexConsumer;
import com.rtsbuilding.rtsbuilding.client.render.util.RtsTintVertexConsumer;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.common.blueprint.transform.BlueprintTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * 蓝图放置幽灵预览 pass —— 蓝图列表「使用」按钮进入放置模式后，
 * 在世界内按<b>真实方块模型</b>渲染每个蓝图方块目标位置的半透明虚影
 * （参考 {@link BreakEffectPass} 的放置/破坏动画渲染：BlockRenderDispatcher +
 * {@link RtsAlphaVertexConsumer} 施加透明度）。
 * <p>非完整模型方块（流体等）回退为半透明色块；确认放置后由服务端
 * BLUEPRINT_BUILD 工作流逐格建造。</p>
 */
public final class BlueprintPlacementPreviewPass implements RenderPass {

    /** 虚影透明度。 */
    private static final float GHOST_ALPHA = 0.45F;
    /** 冲突染色：目标位置已被不可替换方块占用时，虚影染红色（R 保留、G/B 衰减）。 */
    private static final float CONFLICT_TINT_R = 1.0F;
    private static final float CONFLICT_TINT_G = 0.3F;
    private static final float CONFLICT_TINT_B = 0.3F;

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.screen instanceof BuilderScreen screen && screen.isBlueprintPlacementActive();
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex) {
        if (!(mc.screen instanceof BuilderScreen screen)) return;
        RtsBlueprint blueprint = screen.getActiveBlueprintPlacement();
        BlockPos anchor = screen.getPlacementAnchor();
        int ySteps = screen.getPlacementYSteps();
        if (blueprint == null || anchor == null || mc.level == null) return;

        // 与服务端 BlockPlacementPlanner 完全一致的旋转语义：绕中心旋转 + 方块状态旋转
        BlockPos centerOffset = BlueprintTransform.centerRotationOffset(blueprint.size(), ySteps, 0, 0);

        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (RtsBlueprintBlock block : blueprint.blocks()) {
            if (block.isMissingBlock()) continue;
            BlockState state = BlueprintTransform.rotateState(block.state(), ySteps, 0, 0);
            if (state == null || state.isAir()) continue;
            BlockPos worldPos = anchor.offset(
                    BlueprintTransform.rotateAroundCenter(block.relativePos(), ySteps, 0, 0, centerOffset));
            // 冲突检测：目标位置已存在不可替换方块（空气/水等可替换方块视为无冲突）
            boolean conflict = !mc.level.getBlockState(worldPos).canBeReplaced();
            renderGhostBlock(mc.level, worldPos, state, GHOST_ALPHA, conflict, dispatcher, poseStack, alloc);
            minX = Math.min(minX, worldPos.getX());
            minY = Math.min(minY, worldPos.getY());
            minZ = Math.min(minZ, worldPos.getZ());
            maxX = Math.max(maxX, worldPos.getX() + 1.0D);
            maxY = Math.max(maxY, worldPos.getY() + 1.0D);
            maxZ = Math.max(maxZ, worldPos.getZ() + 1.0D);
        }

        // 蓝图整体实际大小（旋转后）：蓝色线框标识，与幽灵块范围一致
        if (minX != Double.POSITIVE_INFINITY) {
            renderBlueprintBounds(mc, alloc, poseStack,
                    minX, minY, minZ, maxX, maxY, maxZ, partialTick);
        }
    }

    /** 渲染蓝图整体包围盒的蓝色线框（深度测试层 + 穿透层），标识实际占地大小。 */
    private static void renderBlueprintBounds(Minecraft mc, BufferAllocator alloc, PoseStack poseStack,
                                              double minX, double minY, double minZ,
                                              double maxX, double maxY, double maxZ, float partialTick) {
        var camera = mc.getCameraEntity();
        double distance = camera != null
                ? camera.getEyePosition(partialTick).distanceTo(
                        new Vec3((minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0))
                : 16;

        // 蓝色线框：深度测试层（实线角括号）+ 穿透层（较淡，便于被遮挡时仍可见）
        CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.brackets(),
                minX, minY, minZ, maxX, maxY, maxZ,
                0.35f, 0.6f, 1.0f, 0.9f, distance);
        CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.noDepth(),
                minX, minY, minZ, maxX, maxY, maxZ,
                0.35f, 0.6f, 1.0f, 0.35f, distance);
    }

    /**
     * 渲染单个方块的真实模型半透明虚影；非完整模型方块回退为半透明色块。
     * 目标位置与现存方块冲突（{@code conflict}=true）时红色染色提示。
     */
    private static void renderGhostBlock(Level level, BlockPos pos, BlockState state, float alpha, boolean conflict,
                                         BlockRenderDispatcher dispatcher, PoseStack poseStack,
                                         BufferAllocator alloc) {
        if (state.getRenderShape() != RenderShape.MODEL) {
            int color = 0xFFCCCCCC;
            var mapColor = state.getMapColor(level, pos);
            if (mapColor != null) color = 0xFF000000 | mapColor.col;
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            if (conflict) {
                // 冲突：红色调偏置（保留亮度但压掉 G/B 分量）
                float l = (r + g + b) / 3f;
                r = Math.min(1.0F, (r + l * 2f) / 3f + 0.2F);
                g = Math.min(1.0F, g * 0.3F);
                b = Math.min(1.0F, b * 0.3F);
            }
            double x = pos.getX(), y = pos.getY(), z = pos.getZ();
            CornerBracketRenderer.renderFilledFaces(alloc.brackets(), poseStack,
                    x, y, z, x + 1, y + 1, z + 1, r, g, b, alpha);
            return;
        }
        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        int light = LevelRenderer.getLightColor(level, pos);
        MultiBufferSource src = conflict
                ? rt -> new RtsTintVertexConsumer(alloc.blockSource().getBuffer(rt), alpha,
                        CONFLICT_TINT_R, CONFLICT_TINT_G, CONFLICT_TINT_B)
                : rt -> new RtsAlphaVertexConsumer(alloc.blockSource().getBuffer(rt), alpha);
        dispatcher.renderSingleBlock(state, poseStack, src, light, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, RenderPipeline.BLOCK_ANIMATION);
        poseStack.popPose();
    }

    @Override
    public int requiredBuffers() {
        return 4 | 8;
    }
}
