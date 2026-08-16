package com.rtsbuilding.uifw.component;

import com.rtsbuilding.uifw.render.SdfRenderer;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 圆形色块矢量显示组件：SDF 绘制圆（无贴图），用于调色盘/取色器/设置面板展示单个颜色。
 *
 * <p>支持纯色填充与带描边两种形态；颜色由调用方传入（可为业务色/主题色），
 * 本组件不读取主题。半透明颜色按原样绘制（会透出下层），如需垫底请自行先画底色。</p>
 */
public final class CircleColorSwatch {

    public static final int DEFAULT_SIZE = 14;

    public void render(GuiGraphics g, int x, int y, int size, int color) {
        if (size <= 0) return;
        SdfRenderer.drawCircle(g, x + size / 2, y + size / 2, size / 2, color);
    }

    /** 带描边的圆形色块：外圆为描边色，内圆为填充色。 */
    public void render(GuiGraphics g, int x, int y, int size, int fillColor, int borderColor, int borderWidth) {
        if (size <= 0) return;
        int centerX = x + size / 2;
        int centerY = y + size / 2;
        int outer = size / 2;
        int bw = Math.max(0, Math.min(borderWidth, outer));
        SdfRenderer.drawCircle(g, centerX, centerY, outer, borderColor);
        int inner = outer - bw;
        if (inner > 0) {
            SdfRenderer.drawCircle(g, centerX, centerY, inner, fillColor);
        }
    }
}
