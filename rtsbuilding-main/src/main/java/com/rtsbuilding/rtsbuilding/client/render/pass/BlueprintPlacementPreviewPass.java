package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.RenderPipeline;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.render.util.RtsAlphaVertexConsumer;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
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

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.screen instanceof BuilderScreen screen && screen.isBlueprintPlacementActive();
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex) {
        if (!(mc.screen instanceof BuilderScreen screen)) return;
        RtsBlueprint blueprint = screen.getActiveBlueprintPlacement();
        BlockPos anchor = screen.getPlacementAnchor();
        if (blueprint == null || anchor == null || mc.level == null) return;

        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        for (RtsBlueprintBlock block : blueprint.blocks()) {
            if (block.isMissingBlock()) continue;
            BlockState state = block.state();
            if (state == null || state.isAir()) continue;
            BlockPos worldPos = anchor.offset(block.relativePos());
            renderGhostBlock(mc.level, worldPos, state, GHOST_ALPHA, dispatcher, poseStack, alloc);
        }

        // 蓝图整体实际大小：锚点 + 声明尺寸的包围盒，蓝色线框标识
        renderBlueprintBounds(mc, alloc, poseStack, blueprint, anchor, partialTick);
    }

    /** 渲染蓝图整体包围盒的蓝色线框（深度测试层 + 穿透层），标识实际占地大小。 */
    private static void renderBlueprintBounds(Minecraft mc, BufferAllocator alloc, PoseStack poseStack,
                                              RtsBlueprint blueprint, BlockPos anchor, float partialTick) {
        var size = blueprint.size();
        if (size == null || size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) return;
        double minX = anchor.getX(), minY = anchor.getY(), minZ = anchor.getZ();
        double maxX = anchor.getX() + size.getX();
        double maxY = anchor.getY() + size.getY();
        double maxZ = anchor.getZ() + size.getZ();

        var camera = mc.getCameraEntity();
        double distance = camera != null
                ? camera.getEyePosition(partialTick).distanceTo(
                        new Vec3(anchor.getX() + size.getX() / 2.0,
                                anchor.getY() + size.getY() / 2.0,
                                anchor.getZ() + size.getZ() / 2.0))
                : 16;

        // 蓝色线框：深度测试层（实线角括号）+ 穿透层（较淡，便于被遮挡时仍可见）
        CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.brackets(),
                minX, minY, minZ, maxX, maxY, maxZ,
                0.35f, 0.6f, 1.0f, 0.9f, distance);
        CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.noDepth(),
                minX, minY, minZ, maxX, maxY, maxZ,
                0.35f, 0.6f, 1.0f, 0.35f, distance);
    }

    /** 渲染单个方块的真实模型半透明虚影；非完整模型方块回退为半透明色块。 */
    private static void renderGhostBlock(Level level, BlockPos pos, BlockState state, float alpha,
                                         BlockRenderDispatcher dispatcher, PoseStack poseStack,
                                         BufferAllocator alloc) {
        if (state.getRenderShape() != RenderShape.MODEL) {
            int color = 0xFFCCCCCC;
            var mapColor = state.getMapColor(level, pos);
            if (mapColor != null) color = 0xFF000000 | mapColor.col;
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            double x = pos.getX(), y = pos.getY(), z = pos.getZ();
            CornerBracketRenderer.renderFilledFaces(alloc.brackets(), poseStack,
                    x, y, z, x + 1, y + 1, z + 1, r, g, b, alpha);
            return;
        }
        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        int light = LevelRenderer.getLightColor(level, pos);
        MultiBufferSource src = rt -> new RtsAlphaVertexConsumer(alloc.blockSource().getBuffer(rt), alpha);
        dispatcher.renderSingleBlock(state, poseStack, src, light, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, RenderPipeline.BLOCK_ANIMATION);
        poseStack.popPose();
    }

    @Override
    public int requiredBuffers() {
        return 4 | 8;
    }
}
