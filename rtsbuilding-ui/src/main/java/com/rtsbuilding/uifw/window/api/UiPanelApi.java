package com.rtsbuilding.uifw.window.api;

import net.minecraft.client.gui.GuiGraphics;

public interface UiPanelApi {

    /**
     * 面板宿主初始化回调（由宿主屏幕在准备阶段调用）。
     *
     * @param host 宿主屏幕（业务屏幕实现 {@link UiPanelHost}）
     */
    default void init(UiPanelHost host) {}

    
    default void tick() {}

    
    default void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    
    default void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {}

    

    default boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }

    default boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return false; }

    default boolean mouseMoved(double mouseX, double mouseY) { return false; }

    default boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) { return false; }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    default boolean keyReleased(int keyCode, int scanCode, int modifiers) { return false; }

    default boolean charTyped(char codePoint, int modifiers) { return false; }

    default void close() {}
}
