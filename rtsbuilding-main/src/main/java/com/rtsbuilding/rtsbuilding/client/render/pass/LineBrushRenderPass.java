package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 线模式建造预览：高亮起点到当前悬停位置的线段方块。
 * 仅在 {@link LineBrushSelector} 处于拖拽状态时渲染，颜色为半透明青色。
 */
public final class LineBrushRenderPass implements RenderPass {

    private static final double OVERLAY_OFFSET = 0.02D;
    private static final float ALPHA = 0.25f;
    private static final float NO_DEPTH_ALPHA = 0.08f;
    private static final float R = 0.30f;
    private static final float G = 0.80f;
    private static final float B = 1.0f;

    private final LineBrushSelector brush;

    public LineBrushRenderPass(LineBrushSelector brush) {
        this.brush = brush;
    }

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.screen instanceof com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen
                && brush.isDragging()
                && brush.getStart() != null
                && brush.getHover() != null;
    }

    @Override
    public void render(Minecraft mc, RenderPass.BufferAllocator alloc, PoseStack poseStack,
                       float partialTick, int frameIndex) {
        List<BlockPos> line = brush.computeLinePositions();
        for (BlockPos p : line) {
            double minX = p.getX() - OVERLAY_OFFSET;
            double minY = p.getY() - OVERLAY_OFFSET;
            double minZ = p.getZ() - OVERLAY_OFFSET;
            double maxX = p.getX() + 1.0D + OVERLAY_OFFSET;
            double maxY = p.getY() + 1.0D + OVERLAY_OFFSET;
            double maxZ = p.getZ() + 1.0D + OVERLAY_OFFSET;
            CornerBracketRenderer.renderFilledFaces(alloc.brackets(), poseStack,
                    minX, minY, minZ, maxX, maxY, maxZ, R, G, B, ALPHA);
            if (BoxSelectionPass.depthTestEnabled) {
                CornerBracketRenderer.renderFilledFaces(alloc.noDepth(), poseStack,
                        minX, minY, minZ, maxX, maxY, maxZ, R, G, B, NO_DEPTH_ALPHA);
            }
        }
    }
}
