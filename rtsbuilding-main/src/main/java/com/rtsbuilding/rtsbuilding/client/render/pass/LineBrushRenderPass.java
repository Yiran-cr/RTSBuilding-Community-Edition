package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 线模式建造预览：以角支架线框样式（与 {@link InteractionTargetPass} 一致）高亮
 * 起点到当前悬停位置的线段方块。仅在 {@link LineBrushSelector} 处于拖拽状态时渲染，
 * 默认颜色为蓝色，可通过渲染设置面板调整。
 *
 * <p>当线段中的方块与世界中已存在的方块（非空气）重叠时，该方块的线框以紫色显示，
 * 用于提示建造位置存在冲突。</p>
 */
public final class LineBrushRenderPass implements RenderPass {

    private static final double LINE_OFFSET = 0.01D;

    /** 线模式预览线框颜色（ARGB，默认蓝色），可在渲染设置面板中调整。 */
    public static int lineBrushColor = 0xFF3388FF;

    /** 线模式中与世界中已有方块重叠时的线框颜色（ARGB，默认紫色），可在渲染设置面板中调整。 */
    public static int lineBrushOverlapColor = 0xFFAA00FF;

    private static final CornerBracketRenderer.Rgb color = new CornerBracketRenderer.Rgb();

    private final LineBrushSelector brush;

    public LineBrushRenderPass(LineBrushSelector brush) {
        this.brush = brush;
    }

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.screen instanceof com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen
                && brush.isActive()
                && brush.getStart() != null
                && brush.getHover() != null;
    }

    @Override
    public void render(Minecraft mc, RenderPass.BufferAllocator alloc, PoseStack poseStack,
                       float partialTick, int frameIndex) {
        if (mc.level == null || mc.getCameraEntity() == null) return;
        Vec3 cameraPos = mc.getCameraEntity().getEyePosition(partialTick);

        // 墙模式墙高调整（阶段二）渲染墙体（含高度扩展），其余阶段渲染走向线
        boolean showWall = brush.isWallActive() && brush.isHeightAdjusting();
        List<BlockPos> line = showWall ? brush.computeWallPositions() : brush.computeLinePositions();

        // 确认阶段（线微调 / 墙高调整）：线框闪烁提示玩家再次右键确认
        float flicker;
        if (brush.isAdjusting() || brush.isHeightAdjusting()) {
            double phase = Math.sin(System.currentTimeMillis() / 160.0D);
            flicker = 0.30F + 0.70F * (float) (0.5D + 0.5D * phase);
        } else {
            flicker = 1.0F;
        }
        float depthAlpha = 0.9F * flicker;
        float noDepthAlpha = CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA * flicker;

        for (BlockPos p : line) {
            // 与世界已有方块重叠时使用紫色，否则使用常规蓝色
            boolean overlap = !mc.level.getBlockState(p).isAir();
            color.update(overlap ? lineBrushOverlapColor : lineBrushColor);
            float r = color.r, g = color.g, b = color.b;
            double minX = p.getX() - LINE_OFFSET;
            double minY = p.getY() - LINE_OFFSET;
            double minZ = p.getZ() - LINE_OFFSET;
            double maxX = p.getX() + 1.0D + LINE_OFFSET;
            double maxY = p.getY() + 1.0D + LINE_OFFSET;
            double maxZ = p.getZ() + 1.0D + LINE_OFFSET;
            double distance = cameraPos.distanceTo(new Vec3(p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D));

            // 深度层：实心角支架线框（待确认阶段随闪烁因子变化 alpha）
            CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.brackets(),
                    minX, minY, minZ, maxX, maxY, maxZ, r, g, b, depthAlpha, distance);
            // 穿透层：深度测试开启时额外渲染半透明线框
            if (BoxSelectionPass.depthTestEnabled) {
                CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.noDepth(),
                        minX, minY, minZ, maxX, maxY, maxZ, r, g, b,
                        noDepthAlpha, distance);
            }
        }
    }

    @Override
    public int requiredBuffers() {
        return 4 | 8;
    }
}
