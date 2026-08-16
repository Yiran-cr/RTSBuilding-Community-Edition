package com.rtsbuilding.uifw.component;

import com.rtsbuilding.uifw.window.window.UiPanel;
import com.rtsbuilding.uifw.component.color.ColorPickerPanel;
import com.rtsbuilding.uifw.component.color.ColorPickerPanel.ColorGroup;
import com.rtsbuilding.uifw.component.color.ColorPickerPanel.ColorSource;
import com.rtsbuilding.uifw.animate.AnimFloat;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.render.SdfRenderer;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nullable;

public class ColorPickerButton {

    private static final int COLOR_WHEEL_FRAME = 12;

    public static final int BTN_SIZE = 16;
    private static final int BORDER_WIDTH = 1;

    private final AnimFloat hoverState = AnimFloat.hover();

    private int areaX, areaY;

    private ColorPickerPanel colorPickerPanel;

    @Nullable
    private ColorSource colorSource;

    @Nullable
    private ColorGroup colorGroup;

    private UiPanel parentPanel;

    public void setColorPickerPanel(ColorPickerPanel panel) {
        this.colorPickerPanel = panel;
    }

    public void setColorSource(@Nullable ColorSource source) {
        this.colorSource = source;
        this.colorGroup = null;
    }

    public void setColorGroup(@Nullable ColorGroup group) {
        this.colorGroup = group;
        this.colorSource = null;
    }

    public void setParentPanel(UiPanel parent) {
        this.parentPanel = parent;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, int btnX, int btnY) {
        this.areaX = btnX;
        this.areaY = btnY;

        boolean hovering = mouseX >= btnX && mouseX < btnX + BTN_SIZE
                && mouseY >= btnY && mouseY < btnY + BTN_SIZE;
        float t = this.hoverState.track(hovering);

        int fillColor = lerpColor(UiPalette.bg(), UiPalette.accent(), t);
        renderBackground(g, btnX, btnY, UiPalette.black(), fillColor);

        int iconX = btnX + (BTN_SIZE - COLOR_WHEEL_FRAME) / 2;
        int iconY = btnY + (BTN_SIZE - COLOR_WHEEL_FRAME) / 2;
        SdfRenderer.drawColorWheel(g, iconX, iconY, COLOR_WHEEL_FRAME);
    }

    private void renderBackground(GuiGraphics g, int btnX, int btnY, int borderColor, int fillColor) {
        SdfRenderer.drawBorderedRoundedRect(g, btnX, btnY, BTN_SIZE, BTN_SIZE, 4,
                borderColor, fillColor, BORDER_WIDTH);
    }

    private static int lerpColor(int from, int to, float t) {
        if (t <= 0.005f) return from;
        if (t >= 0.995f) return to;
        int a = lerpComp((from >> 24) & 0xFF, (to >> 24) & 0xFF, t);
        int r = lerpComp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerpComp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerpComp(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpComp(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }

    public boolean handleClick(double mouseX, double mouseY) {
        if (mouseX >= areaX && mouseX < areaX + BTN_SIZE
                && mouseY >= areaY && mouseY < areaY + BTN_SIZE) {
            if (colorPickerPanel != null) {
                if (!colorPickerPanel.isOpen()) {
                    applyColor();
                    if (parentPanel != null) {
                        parentPanel.openChild(colorPickerPanel);
                    } else {
                        colorPickerPanel.setOpen(true);
                    }
                } else {
                    applyColor();
                }
            }
            return true;
        }
        return false;
    }

    private void applyColor() {
        if (colorGroup != null) {
            colorPickerPanel.setColorGroup(colorGroup);
        } else if (colorSource != null) {
            colorPickerPanel.setColorSource(colorSource);
        }
    }
}
