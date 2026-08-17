package com.rtsbuilding.uifw.component.color;

import com.rtsbuilding.uifw.animate.AnimFloat;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.UiPalette;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 色轮组件：纯矢量绘制（SDF shader 在 fragment 内计算 HSV 着色）+ 数学取色，
 * 无贴图、无运行时位图。取色/指示点定位全部用 HSV↔RGB 数学换算，与 shader 渲染一致。
 */
public class ColorWheelComponent {

    public static final int DRAW_SIZE = 95;
    public static final int PAD = 3;
    public static final int AREA_SIZE = DRAW_SIZE + PAD * 2;
    private static final int INDICATOR_RADIUS = 2;

    /** 色轮圆半径（与 shader 一致：min(halfW,halfH)-1）。 */
    private static final double WHEEL_RADIUS = DRAW_SIZE / 2.0 - 1.0;
    /** 半径占绘制尺寸的比例（指示点归一化用）。 */
    private static final float RADIUS_RATIO = (float) (WHEEL_RADIUS / DRAW_SIZE);

    public static class WheelPickResult {
        public final int texU;
        public final int texV;
        public final float relX;
        public final float relY;
        public final int color;

        public WheelPickResult(int texU, int texV, float relX, float relY, int color) {
            this.texU = texU;
            this.texV = texV;
            this.relX = relX;
            this.relY = relY;
            this.color = color;
        }
    }

    public static class IndicatorPos {
        public final int texU;
        public final int texV;
        public final float relX;
        public final float relY;

        public IndicatorPos(int texU, int texV, float relX, float relY) {
            this.texU = texU;
            this.texV = texV;
            this.relX = relX;
            this.relY = relY;
        }
    }

    /** 矢量绘制 HSV 色轮（SDF shader，无贴图）。 */
    public void renderWheel(GuiGraphics g, int wheelX, int wheelY) {
        SdfRenderer.drawColorWheel(g, wheelX, wheelY, DRAW_SIZE);
    }

    public void renderIndicator(GuiGraphics g, int wheelX, int wheelY,
                                 float relX, float relY,
                                 AnimFloat animator,
                                 int mouseX, int mouseY, boolean dragging) {
        // 浮点坐标定位（亚像素精度，避免整数取整造成运动锯齿）
        float dotCenterX = wheelX + relX * DRAW_SIZE;
        float dotCenterY = wheelY + relY * DRAW_SIZE;

        float minCenter = (float) (wheelX + DRAW_SIZE / 2.0 - WHEEL_RADIUS);
        float maxCenter = (float) (wheelX + DRAW_SIZE / 2.0 + WHEEL_RADIUS);
        dotCenterX = Math.max(minCenter, Math.min(maxCenter, dotCenterX));
        minCenter = (float) (wheelY + DRAW_SIZE / 2.0 - WHEEL_RADIUS);
        maxCenter = (float) (wheelY + DRAW_SIZE / 2.0 + WHEEL_RADIUS);
        dotCenterY = Math.max(minCenter, Math.min(maxCenter, dotCenterY));

        SdfRenderer.drawCircleF(g, dotCenterX, dotCenterY, INDICATOR_RADIUS, UiPalette.get("picker_indicator"));
    }

    /**
     * 数学取色：鼠标位置 → 相对圆心极坐标 → HSV → RGB。
     * 命中圆形色轮外（距离 &gt; 半径）时夹到圆边缘最近色。
     */
    public WheelPickResult pickColor(double mouseX, double mouseY, int wheelX, int wheelY) {
        double lx = mouseX - (wheelX + DRAW_SIZE / 2.0);
        double ly = mouseY - (wheelY + DRAW_SIZE / 2.0);
        double dist = Math.sqrt(lx * lx + ly * ly);

        if (dist > WHEEL_RADIUS) {
            double scale = WHEEL_RADIUS / dist;
            lx *= scale;
            ly *= scale;
            dist = WHEEL_RADIUS;
        }

        double hue = (Math.atan2(ly, lx) / (2 * Math.PI) + 1.0) % 1.0;
        double sat = WHEEL_RADIUS <= 0 ? 0.0 : Math.min(1.0, dist / WHEEL_RADIUS);
        int color = ColorPickerPanel.ColorMath.hsvToRgb((float) hue, (float) sat, 1.0f);

        float relX = 0.5f + (float) (lx / WHEEL_RADIUS) * RADIUS_RATIO;
        float relY = 0.5f + (float) (ly / WHEEL_RADIUS) * RADIUS_RATIO;
        return new WheelPickResult(0, 0, relX, relY, color);
    }

    /**
     * 将目标 ARGB 颜色映射到色轮指示点位置（取其色相/饱和度）。
     */
    public IndicatorPos syncIndicatorToColor(int targetColor) {
        float[] hsv = ColorPickerPanel.ColorMath.rgbToHsv(targetColor);
        return calcIndicatorUVFromHS(hsv[0], hsv[1]);
    }

    /**
     * 由 HS 值（色相 0-1 / 饱和度 0-1）换算指示点相对位置（0-1）。
     */
    public IndicatorPos calcIndicatorUVFromHS(float hue, float saturation) {
        double angle = hue * 2 * Math.PI;
        float rx = (float) (Math.cos(angle) * saturation) * RADIUS_RATIO;
        float ry = (float) (Math.sin(angle) * saturation) * RADIUS_RATIO;
        return new IndicatorPos(0, 0, 0.5f + rx, 0.5f + ry);
    }
}
