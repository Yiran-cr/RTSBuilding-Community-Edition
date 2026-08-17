package com.rtsbuilding.uifw.component;

import com.rtsbuilding.uifw.animate.AnimFloat;
import com.rtsbuilding.uifw.animate.ColorAnimation;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.render.UiPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 矢量删除按钮：SDF 圆角背景（悬停渐变）+ 矢量垃圾桶图标，无贴图。
 * <p>支持确认态：{@code confirm} 为 true 时背景切换为警示红、图标切换为问号，
 * 提示需要再次点击才执行删除（防误删）。</p>
 */
public final class DeleteButton {

    /** 按钮边长（px）。 */
    public static final int SIZE = 14;
    private static final int RADIUS = 3;
    private static final int ICON_INSET = 2;

    private final AnimFloat hoverState = AnimFloat.hover();

    /**
     * 渲染按钮。
     *
     * @param x,y    按钮左上角
     * @param confirm 是否处于「二次确认」状态
     */
    public void render(GuiGraphics g, int mouseX, int mouseY, int x, int y, boolean confirm) {
        boolean hovering = hit(mouseX, mouseY, x, y);
        float t = this.hoverState.track(hovering);

        int base = confirm ? UiPalette.get("list_delete") : UiPalette.get("list_btn");
        int bg = ColorAnimation.lerpRGB(base, UiPalette.get("list_btn_hover"), t);
        SdfRenderer.drawRoundedRect(g, x, y, SIZE, SIZE, RADIUS, bg);

        int iconColor = UiPalette.get("tooltip_text");
        if (confirm) {
            TextRenderer.drawCentered(g, Minecraft.getInstance().font, "?",
                    x + SIZE / 2, y + (SIZE - Minecraft.getInstance().font.lineHeight) / 2 + 1, iconColor);
        } else {
            int iconSize = SIZE - ICON_INSET * 2;
            SdfRenderer.drawTrashIcon(g, x + ICON_INSET, y + ICON_INSET, iconSize, iconColor);
        }
    }

    /** 命中检测（与渲染坐标一致）。 */
    public boolean hit(double mx, double my, int x, int y) {
        return mx >= x && mx < x + SIZE && my >= y && my < y + SIZE;
    }
}
