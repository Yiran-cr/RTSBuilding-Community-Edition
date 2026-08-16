package com.rtsbuilding.uifw.component;

import com.mojang.math.Axis;
import com.rtsbuilding.uifw.animate.AnimFloat;
import com.rtsbuilding.uifw.animate.ColorAnimation;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.UiPalette;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 矢量关闭按钮：SDF 圆角背景（悬停渐变）+ 矢量 × 图标（两条旋转的 SDF 圆角矩形），无贴图。
 */
public final class CloseButton {

    public static final int SIZE = 14;
    private static final int ICON_INSET = 4;
    private static final int ICON_THICK = 2;
    private static final int RADIUS = 3;

    private int x;
    private int y;
    private final AnimFloat hoverState = AnimFloat.hover();

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        boolean hovering = hit(mouseX, mouseY);
        float t = this.hoverState.track(hovering);

        int bg = ColorAnimation.lerpRGB(UiPalette.get("button_bg"), UiPalette.get("button_hover_bg"), t);
        SdfRenderer.drawRoundedRect(g, x, y, SIZE, SIZE, RADIUS, bg);

        int color = UiPalette.get("icon_close");
        int halfLen = (SIZE - ICON_INSET * 2) / 2;
        int thickness = ICON_THICK;
        g.pose().pushPose();
        g.pose().translate(x + SIZE / 2f, y + SIZE / 2f, 0);
        // 两条交叉的细圆角矩形构成 ×
        g.pose().mulPose(Axis.ZP.rotationDegrees(45));
        SdfRenderer.drawRoundedRect(g, -halfLen, -thickness / 2, halfLen * 2, thickness, thickness / 2f, color);
        g.pose().mulPose(Axis.ZP.rotationDegrees(90));
        SdfRenderer.drawRoundedRect(g, -halfLen, -thickness / 2, halfLen * 2, thickness, thickness / 2f, color);
        g.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return hit(mouseX, mouseY);
    }

    private boolean hit(double mx, double my) {
        return mx >= x && mx < x + SIZE && my >= y && my < y + SIZE;
    }
}
