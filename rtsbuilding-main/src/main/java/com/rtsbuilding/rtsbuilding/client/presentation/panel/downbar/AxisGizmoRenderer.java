package com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar;

import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 轨道球渲染器 —— 负责圆形背景与随视角投影的六段彩轴及文字圆点的绘制。
 */
final class AxisGizmoRenderer {

    /** X 轴颜色（红） */
    private static final int COLOR_X = 0xFFE0514D;

    /** Y 轴颜色（绿） */
    private static final int COLOR_Y = 0xFF4DAB51;

    /** Z 轴颜色（蓝） */
    private static final int COLOR_Z = 0xFF4D7FE0;

    /** 背景圆未悬浮时的填充色 */
    private static final int BG_NORMAL = 0xB0000000;

    /** 背景圆悬浮时的填充色 */
    private static final int BG_HOVERED = 0xC0000000;

    /** 整体悬浮时轴/球的高亮系数 */
    private static final float HIGHLIGHT_GIZMO = 1.10f;

    /** 相机朝向对应轴时的高亮系数 */
    private static final float HIGHLIGHT_ACTIVE = 1.25f;

    private final TrackballProjection projection;

    AxisGizmoRenderer(TrackballProjection projection) {
        this.projection = projection;
    }

    /**
     * 渲染 3D 轨道球：随视角投影的正负六段彩轴与中心小球，并刷新悬停动画。
     * <p>三档高亮：</p>
     * <ul>
     *   <li>鼠标悬浮大圆形背景时，所有轴线段与文字背景球整体高亮；</li>
     *   <li>相机当前朝向/位置对应的世界轴方向更亮；</li>
     *   <li>鼠标悬浮的球高亮为白色。</li>
     * </ul>
     */
    void render(GuiGraphics g, AxisGizmoState state, int mouseX, int mouseY) {
        projection.updateViewBasis();

        // 拖拽中禁止悬浮判断（文字球不高亮、无整体高亮）
        int hoveredSegment = state.isDragging() ? -1 : hitAxis(projection, state, mouseX, mouseY);
        boolean gizmoHovered = !state.isDragging()
                && distance(mouseX, mouseY, state.getCx(), state.getCy())
                <= TrackballProjection.RADIUS;
        int activeSegmentMask = projection.computeActiveSegmentMask();

        // 圆形半透明背景（悬浮时略微提亮）
        SdfRenderer.drawCircle(g, state.getCx(), state.getCy(), TrackballProjection.RADIUS,
                gizmoHovered ? BG_HOVERED : BG_NORMAL);

        // 按深度排序绘制六段轴（画家算法：背向先画、朝向后画，防止穿透）
        renderAxisSegmentsSorted(g, state, hoveredSegment, gizmoHovered, activeSegmentMask);
    }

    /**
     * 按投影深度排序后绘制全部六段轴。
     */
    private void renderAxisSegmentsSorted(GuiGraphics g, AxisGizmoState state,
                                          int hoveredSegment, boolean gizmoHovered,
                                          int activeSegmentMask) {
        int cx = state.getCx();
        int cy = state.getCy();
        int[] axes = new int[6];
        int[] signs = new int[6];
        double[] depths = new double[6];
        TrackballProjection.SegmentScreen[] screens = new TrackballProjection.SegmentScreen[6];
        Integer[] order = new Integer[6];
        int n = 0;
        for (int i = 0; i < 3; i++) {
            for (int s = 0; s < 2; s++) {
                int sign = s == 0 ? 1 : -1;
                TrackballProjection.SegmentScreen sc = projection.projectScreen(cx, cy, i, sign);
                axes[n] = i;
                signs[n] = sign;
                depths[n] = sc.depth();
                screens[n] = sc;
                order[n] = n;
                n++;
            }
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(depths[a], depths[b]));
        for (int idx : order) {
            renderAxisSegment(g, state, axes[idx], signs[idx], screens[idx],
                    hoveredSegment, gizmoHovered, activeSegmentMask);
        }
    }

    /**
     * 渲染单个轴段（指定轴与方向）。
     */
    private void renderAxisSegment(GuiGraphics g, AxisGizmoState state,
                                   int axis, int sign, TrackballProjection.SegmentScreen screen,
                                   int hoveredSegment, boolean gizmoHovered, int activeSegmentMask) {
        int segmentIndex = sign > 0 ? axis : axis + 3;
        int cx = state.getCx();
        int cy = state.getCy();
        boolean hovered = segmentIndex == hoveredSegment;
        boolean isActive = (activeSegmentMask & (1 << segmentIndex)) != 0;
        state.getHoverAnims()[segmentIndex].track(hovered);

        double scale = screen.scale();
        int dotRadius = TrackballProjection.LABEL_DOT_RADIUS;

        // 基础色 → 整体悬浮高亮 → 相机朝向对应轴更高亮（叠乘后柔和，避免过曝）
        int dotColor = axisColor(axis);
        if (gizmoHovered) {
            dotColor = ColorAnimation.scale(dotColor, HIGHLIGHT_GIZMO);
        }
        if (isActive) {
            dotColor = ColorAnimation.scale(dotColor, HIGHLIGHT_ACTIVE);
        }

        // 线段：正方向轴始终渲染；负方向轴不绘制线段
        if (sign > 0) {
            drawLineVector(g, cx, cy, screen.x(), screen.y(), dotColor, 2);
        }
        // 背景圆：正负轴均绘制；鼠标悬浮的球高亮为白色，文字改深色
        int textColor = 0xFFFFFFFF;
        if (hovered) {
            dotColor = 0xFFFFFFFF;
            textColor = 0xFF1B2430;
        }
        // 用 pose 浮点缩放绘制球与文字，透视缩放连续平滑
        var pose = g.pose();
        pose.pushPose();
        pose.translate(screen.x(), screen.y(), 0);
        pose.scale((float) scale, (float) scale, 1.0f);
        SdfRenderer.drawCircle(g, 0, 0, dotRadius, dotColor);

        // 文字：仅正方向轴显示（负方向轴只绘制无文字的圆点）
        if (sign > 0) {
            TextRenderer.drawCentered(g, Minecraft.getInstance().font, axisLabel(segmentIndex),
                    0, -3, textColor);
        }
        pose.popPose();
    }

    /**
     * 计算命中的轴段（按轴端文字背景圆圆心距离判定）。
     * <p>多个按钮投影重叠时，取深度最大（最前方）的按钮，后方按钮被遮挡。
     * 渲染与输入共用同一判定。调用方需保证已先调用
     * {@code projection.updateViewBasis()} 更新视图基底。</p>
     *
     * @return 0..2 正轴，3..5 负轴；未命中返回 -1
     */
    static int hitAxis(TrackballProjection projection, AxisGizmoState state, int mx, int my) {
        if (!state.contains(mx, my)) return -1;
        int cx = state.getCx();
        int cy = state.getCy();
        int best = -1;
        double bestDepth = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 6; i++) {
            int axis = i % 3;
            int sign = i < 3 ? 1 : -1;
            TrackballProjection.SegmentScreen sc = projection.projectScreen(cx, cy, axis, sign);
            double hitRadius = Math.max(4.0D,
                    (TrackballProjection.LABEL_DOT_RADIUS + 2) * sc.scale());
            if (distance(mx, my, sc.x(), sc.y()) <= hitRadius && sc.depth() > bestDepth) {
                best = i;
                bestDepth = sc.depth();
            }
        }
        return best;
    }

    /**
     * 用矢量渲染（SDF 圆角胶囊线）绘制一条线段。
     * <p>坐标使用浮点：平移精度不取整，避免低分辨率下端点阶梯跳跃。</p>
     */
    private static void drawLineVector(GuiGraphics g, double x0, double y0, double x1, double y1,
                                       int color, int thickness) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5D) return;
        float angleDeg = (float) Math.toDegrees(Math.atan2(dy, dx));

        var pose = g.pose();
        pose.pushPose();
        pose.translate(x0, y0, 0);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angleDeg));
        int half = thickness / 2;
        SdfRenderer.drawRoundedRect(g, 0, -half, (int) Math.round(len) + 1, thickness, half, color);
        pose.popPose();
        g.flush();
    }

    private static double distance(double x0, double y0, double x1, double y1) {
        double dx = x0 - x1;
        double dy = y0 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static int axisColor(int index) {
        return switch (index) {
            case 0 -> COLOR_X;
            case 1 -> COLOR_Y;
            default -> COLOR_Z;
        };
    }

    private static String axisLabel(int segmentIndex) {
        int axis = segmentIndex % 3;
        String base = switch (axis) {
            case 0 -> "X";
            case 1 -> "Y";
            default -> "Z";
        };
        return segmentIndex < 3 ? base : "-" + base;
    }
}
