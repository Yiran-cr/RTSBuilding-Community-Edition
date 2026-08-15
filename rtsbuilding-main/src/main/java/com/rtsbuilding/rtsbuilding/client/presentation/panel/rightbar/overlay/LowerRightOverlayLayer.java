package com.rtsbuilding.rtsbuilding.client.presentation.panel.rightbar.overlay;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.DownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.plugin.FeatureAdjusters;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 右面板下嵌层：功能调节器（滑块 + 数值输入框 + 形状模式）。
 *
 * <p>薄转发层：渲染与交互全部委托给 {@link FeatureAdjusters} 插件，
 * 与 {@code LeftDownOverlayLayer}/{@code RightDownOverlayLayer}/{@code UpperRightOverlayLayer}
 * 的「宿主嵌层 + 插件」风格保持一致。</p>
 */
public final class LowerRightOverlayLayer extends DownOverlayLayer {

    private final FeatureAdjusters adjusters;

    public LowerRightOverlayLayer() {
        this.adjusters = new FeatureAdjusters(this);
    }

    @Override
    protected void renderContent(GuiGraphics g) {
        adjusters.renderContent(g);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return adjusters.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return adjusters.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return adjusters.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return adjusters.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return adjusters.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return adjusters.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
}
