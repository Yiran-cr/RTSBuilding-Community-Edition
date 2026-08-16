package com.rtsbuilding.uifw.window.component;

import com.rtsbuilding.uifw.component.ScaleSliderComponent;
import com.rtsbuilding.uifw.component.ToggleSwitch;
import com.rtsbuilding.uifw.window.api.UiPanelHost;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;

/**
 * 设置分区（可折叠）：标题头 + 折叠动画 + 内容行（标签/开关/滑块）。
 * 行数与点击分发由子类实现。
 */
public abstract class SettingsSection {

    private static final int LEFT_PADDING = 8;
    private static final int CONTENT_TOP_GAP = 0;
    private static final int LINE_HEIGHT = 20;

    protected static final int LEFT_PAD = 6;
    protected static final int RIGHT_PAD = 6;
    protected static final int SLIDER_GAP = 8;
    protected static final int MID_DIVIDER_GAP = 4;

    /** 左右两半控件分界 X（居中 + 间隙）。 */
    protected static int midControlX(int x, int w) {
        return x + w / 2 + MID_DIVIDER_GAP;
    }

    private final CollapsibleSection section;

    protected int getLeftPadding() { return LEFT_PADDING; }
    protected int getLineHeight() { return LINE_HEIGHT; }
    protected int getTextColor() { return ThemeManager.getTextColor(); }
    protected int getHoverBgColor() { return UiPalette.get("section_hover_bg"); }
    protected int getHoverTextColor() { return ThemeManager.getHoverTextColor(); }

    private static int getSeparatorColor() {
        return UiPalette.get("settings_separator");
    }

    protected SettingsSection(String titleKey) {
        this.section = new CollapsibleSection(titleKey);
    }

    protected abstract int getContentRowCount();

    protected int getContentLineCount() {
        return getContentRowCount();
    }

    protected int getEffectiveContentHeight() {
        return getContentRowCount() * LINE_HEIGHT + 6;
    }

    /** 渲染分区：标题头 + 内容区（按折叠进度裁剪）。 */
    public void render(GuiGraphics g, int mouseX, int mouseY, int contentX, int contentY, int contentW) {
        int headerX = contentX + LEFT_PADDING;
        int headerY = contentY + 8;
        int headerW = contentW - LEFT_PADDING * 2;

        int lineCount = getContentLineCount();
        int contentFullH = getEffectiveContentHeight();
        section.drawHeader(g, mouseX, mouseY, headerX, headerY, headerW, contentFullH);

        int headerBottom = headerY + CollapsibleSection.headerHeight();

        int animH = (int) (contentFullH * section.getContentProgress());
        if (animH > 0) {
            SdfRenderer.drawPill(g, headerX + 5, headerBottom - 2, headerW - 10, 2, getSeparatorColor());

            int contentTop = headerBottom + CONTENT_TOP_GAP;
            g.flush();
            enableScissor(g, headerX, contentTop, headerX + headerW, contentTop + animH);
            renderContent(g, mouseX, mouseY, headerX, contentTop, headerW, lineCount);
            g.flush();
            g.disableScissor();
        }
    }

    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int lineCount) {
        for (int i = 0; i < lineCount; i++) {
            int lineY = y + 4 + i * LINE_HEIGHT;
        }
    }

    private static void enableScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof UiPanelHost host) {
            host.enableUiScissor(g, x1, y1, x2, y2);
        } else {
            g.enableScissor(x1, y1, x2, y2);
        }
    }

    /** 点击分发：标题头切换折叠，内容行分发给子类。 */
    public boolean handleClick(double mouseX, double mouseY, int contentX, int contentY, int contentW) {
        int headerX = contentX + LEFT_PADDING;
        int headerY = contentY + 8;
        int headerW = contentW - LEFT_PADDING * 2;
        if (section.isHeaderClicked(mouseX, mouseY, headerX, headerY, headerW)) {
            section.toggle();
            return true;
        }
        if (isExpanded()) {
            int lineCount = getContentLineCount();
            int contentFullH = getEffectiveContentHeight();
            int animH = (int) (contentFullH * section.getContentProgress());
            if (animH > 0) {
                int contentTop = headerY + CollapsibleSection.headerHeight() + CONTENT_TOP_GAP;
                if (mouseX >= headerX + 2 && mouseX < headerX + headerW - 2
                        && mouseY >= contentTop && mouseY < contentTop + animH) {
                    for (int i = 0; i < lineCount; i++) {
                        if (onContentLineClick(i, mouseX, mouseY, contentX, contentY, contentW)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    protected boolean onContentLineClick(int lineIndex, double mouseX, double mouseY,
                                         int contentX, int contentY, int contentW) {
        return false;
    }

    protected int rowY(int y, int row) {
        return y + 4 + LINE_HEIGHT * row;
    }

    protected int textY(int y, int row) {
        return rowY(y, row) + 2;
    }

    protected int toggleX(int w) {
        return w - 28 - RIGHT_PAD;
    }

    protected int toggleY(int y, int row) {
        int textCenter = textY(y, row) + Minecraft.getInstance().font.lineHeight / 2;
        return textCenter - 7;
    }

    protected void renderLabel(GuiGraphics g, String text, int x, int y, int row) {
        TextRenderer.draw(g, text, x + LEFT_PAD, textY(y, row), getTextColor());
    }

    protected void renderToggle(GuiGraphics g, int mx, int my,
                                 int x, int y, int w, int row,
                                 ToggleSwitch toggle, boolean state) {
        toggle.render(g, x + toggleX(w), toggleY(y, row), state);
    }

    protected void renderSlider(GuiGraphics g, int mx, int my,
                                 int x, int y, int w, int row, String label,
                                 ScaleSliderComponent slider, SliderTrack trackPos,
                                 double min, double max, double value) {
        TextRenderer.draw(g, label, x + LEFT_PAD, textY(y, row), getTextColor());
        int lineCenterY = textY(y, row) + Minecraft.getInstance().font.lineHeight / 2;
        int controlStart = midControlX(x, w);
        trackPos.trackX = controlStart;
        trackPos.trackY = lineCenterY - 2;
        trackPos.trackW = Mth.clamp(x + w - RIGHT_PAD - controlStart, 20, x + w - RIGHT_PAD - controlStart);
        trackPos.slider = slider;
        slider.render(g, mx, my, trackPos.trackX, trackPos.trackY, trackPos.trackW, min, max, value);
    }

    public static final class SliderTrack {
        public int trackX, trackY, trackW;
        public ScaleSliderComponent slider;
    }

    public boolean isExpanded() {
        return section.isExpanded();
    }

    public void setExpanded(boolean expanded) {
        section.setExpanded(expanded);
    }

    public int totalHeight(int contentW) {
        int contentFullH = getEffectiveContentHeight();
        return section.totalHeight(contentFullH);
    }
}
