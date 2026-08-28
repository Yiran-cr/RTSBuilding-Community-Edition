package com.rtsbuilding.rtsbuilding.client.presentation.plugin.grid;

import com.rtsbuilding.uifw.window.popup.BasePopup;
import com.rtsbuilding.uifw.component.ToggleSwitch;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class ContainerModePopup extends BasePopup {

    public record ContainerModeItem(Component label, Runnable action) {}

    private final ContainerModeItem[] items;
    private final boolean[] states;
    /** 行尾开关（视觉与设置面板 ToggleSwitch 一致，带滑动动画）。 */
    private final ToggleSwitch[] switches;

    private boolean showBidirectional;
    private boolean showExtractOnly;

    @FunctionalInterface
    public interface OnFilterChangeListener {
        void onFilterChanged(boolean showBidirectional, boolean showExtractOnly);
    }

    private final OnFilterChangeListener listener;

    private static final int BTN_TEXT_GAP = 4;

    public ContainerModePopup(boolean showBidirectional, boolean showExtractOnly, OnFilterChangeListener listener) {
        this.showBidirectional = showBidirectional;
        this.showExtractOnly = showExtractOnly;
        this.listener = listener;

        this.items = new ContainerModeItem[]{
            new ContainerModeItem(Component.translatable("tooltip.rtsbuilding.rightdown.container_bidirectional"), this::toggleBidirectional),
            new ContainerModeItem(Component.translatable("tooltip.rtsbuilding.rightdown.container_extract"), this::toggleExtractOnly)
        };

        this.states = new boolean[]{showBidirectional, showExtractOnly};

        var font = Minecraft.getInstance().font;
        int[] contentWidths = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            contentWidths[i] = ToggleSwitch.TRACK_W + BTN_TEXT_GAP + font.width(items[i].label().getString());
        }
        setItemContentWidths(contentWidths);

        initAnims(items.length);

        this.switches = new ToggleSwitch[items.length];
        for (int i = 0; i < items.length; i++) {
            this.switches[i] = new ToggleSwitch(states[i], null);
        }
    }

    public void setShowBidirectional(boolean show) {
        this.showBidirectional = show;
        this.states[0] = show;
    }

    public void setShowExtractOnly(boolean show) {
        this.showExtractOnly = show;
        this.states[1] = show;
    }

    public boolean isShowBidirectional() {
        return showBidirectional;
    }

    public boolean isShowExtractOnly() {
        return showExtractOnly;
    }

    private void toggleBidirectional() {
        showBidirectional = !showBidirectional;
        states[0] = showBidirectional;
        if (listener != null) {
            listener.onFilterChanged(showBidirectional, showExtractOnly);
        }
    }

    private void toggleExtractOnly() {
        showExtractOnly = !showExtractOnly;
        states[1] = showExtractOnly;
        if (listener != null) {
            listener.onFilterChanged(showBidirectional, showExtractOnly);
        }
    }

    @Override
    protected int getItemCount() {
        return items.length;
    }

    @Override
    protected void renderItem(GuiGraphics g, int index, int itemY, float hoverT) {
        var font = Minecraft.getInstance().font;
        int textColor = hoverT > 0.5f ? ThemeManager.getHoverTextColor() : ThemeManager.getTextColor();
        String label = items[index].label().getString();
        int textX = x + getPadH();
        int textY = itemY + (getItemHeight() - font.lineHeight) / 2 + 1;
        TextRenderer.draw(g, label, textX, textY, textColor);

        int btnX = x + getPopupWidth() - getPadH() - ToggleSwitch.TRACK_W;
        int btnY = itemY + (getItemHeight() - ToggleSwitch.TRACK_H) / 2;

        switches[index].render(g, btnX, btnY, states[index]);
    }

    @Override
    protected boolean onItemClick(int index) {
        if (items[index].action() != null) {
            items[index].action().run();
        }
        return true;
    }
}
