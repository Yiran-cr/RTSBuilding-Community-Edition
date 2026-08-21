package com.rtsbuilding.uifw.window.window;

import com.rtsbuilding.uifw.window.api.UiPanelApi;
import com.rtsbuilding.uifw.window.window.handler.PanelDragHandler;
import com.rtsbuilding.uifw.window.window.handler.PanelResizeHandler;
import com.rtsbuilding.uifw.window.window.model.PanelBounds;
import com.rtsbuilding.uifw.component.CloseButton;
import com.rtsbuilding.uifw.window.api.UiPanelHost;
import com.rtsbuilding.uifw.animate.AnimFloat;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.state.HoverSuppression;
import com.rtsbuilding.uifw.theme.ThemeManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class UiPanel implements UiPanelApi {

    private static final int DEFAULT_TITLE_BAR_H = 20;
    private static final int DEFAULT_MIN_W = 80;
    private static final int DEFAULT_MIN_H = 60;
    private static final int DEFAULT_RESIZE_BORDER = 5;
    public static final int SCREEN_MARGIN = 4;

    public enum ResizeCursor {
        DEFAULT, RESIZE_EW, RESIZE_NS, RESIZE_NWSE, RESIZE_NESW
    }

    protected final PanelBounds bounds = new PanelBounds(0, 0);
    protected UiPanelHost screen;
    protected boolean open;
    protected boolean draggable = true;
    protected boolean resizable = false;
    protected boolean closable = true;

    private long lastClickTime = System.nanoTime();
    protected boolean mouseHovering;
    CloseButton closeButton;
    final PanelResizeHandler resizeHandler = new PanelResizeHandler(this);
    final PanelDragHandler dragHandler = new PanelDragHandler(this);
    private boolean skipHoverDetection;
    private UiPanel parentPanel;
    private final List<UiPanel> children = new ArrayList<>();
    private final AnimFloat panelHoverState = AnimFloat.hover();
    private final PanelInputHandler inputHandler = new PanelInputHandler(this);

    public UiPanel getParentPanel() { return parentPanel; }
    public List<UiPanel> getChildren() { return Collections.unmodifiableList(children); }

    public void openChild(UiPanel child) {
        if (child == null || child == this) return;
        if (child.parentPanel == this && child.isOpen()) return;
        if (child.parentPanel != null) child.parentPanel.children.remove(child);
        child.parentPanel = this;
        this.children.add(child);
        child.setOpen(true);
    }

    void setSkipHoverDetection(boolean skip) { this.skipHoverDetection = skip; }

    protected abstract void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick);
    protected abstract void handleContentClick(double mouseX, double mouseY, int button);
    protected abstract Component getTitle();
    protected abstract int getDefaultWidth();
    protected abstract int getDefaultHeight();
    protected abstract void computeDefaultPosition();

    /**
     * 将窗口居中于宿主屏幕，并受顶部/底部内边距约束（所有 UiPanel 子类统一的开局位置基准）：
     * 水平居中；垂直居中，但顶部不低于 {@code topInset}、底部不超出屏幕底边 {@code bottomInset}。
     *
     * @param topInset    顶部最小内边距（用于避开顶栏/按钮区等）
     * @param bottomInset 底部最小内边距
     */
    protected void positionCentered(int topInset, int bottomInset) {
        if (this.screen == null) return;
        setWindowX(Math.max(8, (this.screen.getUiWidth() - getWindowWidth()) / 2));
        setWindowY(Mth.clamp((this.screen.getUiHeight() - getWindowHeight()) / 2,
                topInset,
                Math.max(topInset, this.screen.getUiHeight() - getWindowHeight() - bottomInset)));
    }

    @Override
    public void init(UiPanelHost screen) {
        this.screen = screen;
        bounds.setDefaults(
                Math.max(getMinWindowWidth(), getDefaultWidth()),
                Math.max(getMinWindowHeight(), getDefaultHeight()));
        this.closeButton = createCloseButton();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!this.open || !canShowWindow()) { this.mouseHovering = false; return; }
        initializePosition();
        if (!this.resizeHandler.isResizing()) clampWindowToScreen();
        updatePanelHoverState(mouseX, mouseY);

        if (this.skipHoverDetection) HoverSuppression.floatingWindow().setSuppressed(true);
        boolean needScissor = shouldClipContent();
        try {
            renderWindowFrame(g, mouseX, mouseY);
            onRenderBeforeContent(g, mouseX, mouseY);
            if (needScissor) { g.flush(); enableContentScissor(g); }
            renderContent(g, mouseX, mouseY, partialTick);
            g.flush();
        } finally {
            if (this.skipHoverDetection) HoverSuppression.floatingWindow().setSuppressed(false);
            if (needScissor) g.disableScissor();
        }
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) { render(g, mouseX, mouseY, 0.0F); }

    public boolean isOpen() { return this.open; }

    public void setOpen(boolean open) {
        boolean wasOpen = this.open;
        if (open && !wasOpen) {
            // 仅当面板从未初始化过 bounds 时才重置为默认位置/尺寸；
            // 若已有 bounds（持久化恢复或用户拖拽调整过），打开时保留当前状态，
            // 避免每次打开都回到默认居中位置导致持久化位置失效。
            initializePosition();
            markBroughtToFront();
            if (this.screen != null) this.screen.getFloatingWindowLayer().markSortDirty();
        }
        this.open = open;
        if (!open && wasOpen) {
            closeAllChildren();
            detachFromParent();
            onClose();
        }
    }

    private void closeAllChildren() { for (UiPanel child : List.copyOf(this.children)) child.setOpen(false); }
    private void detachFromParent() {
        if (this.parentPanel != null) { this.parentPanel.children.remove(this); this.parentPanel = null; }
    }
    public void toggleOpen() { setOpen(!this.open); }

    public int getWindowX() { return bounds.getX(); }
    public int getWindowY() { return bounds.getY(); }
    public int getWindowWidth() { return bounds.getWidth(); }
    public int getWindowHeight() { return bounds.getHeight(); }
    public void setWindowX(int x) { bounds.setX(x); }
    public void setWindowY(int y) { bounds.setY(y); }
    public void setWindowWidth(int w) { bounds.setWidth(w); }
    public void setWindowHeight(int h) { bounds.setHeight(h); }
    public UiPanelHost getScreen() { return this.screen; }
    public long getLastClickTime() { return lastClickTime; }
    public void markBroughtToFront() { this.lastClickTime = System.nanoTime(); }
    public boolean hasInitializedBounds() { return bounds.isInitialized(); }
    protected boolean isResizing() { return resizeHandler.isResizing(); }

    public void setPosition(int x, int y) {
        ensureSizeInitialized(); bounds.setX(x); bounds.setY(y);
        bounds.setInitialized(true); clampWindowToScreen();
    }

    public void setBounds(int x, int y, int width, int height) {
        bounds.setX(x); bounds.setY(y);
        bounds.setWidth(Math.max(getMinWindowWidth(), width));
        bounds.setHeight(Math.max(getMinWindowHeight(), height));
        clampWindowSize(); bounds.setInitialized(true); clampWindowToScreen();
    }

    public void setTransientBounds(int x, int y, int width, int height) {
        bounds.setX(x); bounds.setY(y);
        bounds.setWidth(Math.max(getMinWindowWidth(), width));
        bounds.setHeight(Math.max(getMinWindowHeight(), height));
        clampWindowSize(); bounds.setInitialized(true); clampWindowToScreen();
    }

    public void setSize(int width, int height) {
        ensureSizeInitialized(); bounds.setWidth(width); bounds.setHeight(height);
        clampWindowSize(); clampWindowToScreen();
    }

    public void resetToDefaultBounds() {
        bounds.resetToDefaults(); clampWindowSize(); computeDefaultPosition();
        clampWindowToScreen(); bounds.setInitialized(true);
    }

    public boolean isInsideWindow(double mouseX, double mouseY) {
        return mouseX >= bounds.getX() && mouseX < bounds.getX() + bounds.getWidth()
                && mouseY >= bounds.getY() && mouseY < bounds.getY() + bounds.getHeight();
    }

    public boolean isInsideWindowOrResizeBorder(double mouseX, double mouseY) {
        int border = getResizeBorderWidth();
        return mouseX >= bounds.getX() - border && mouseX < bounds.getX() + bounds.getWidth() + border
                && mouseY >= bounds.getY() - border && mouseY < bounds.getY() + bounds.getHeight() + border;
    }

    public boolean isInsideResizableBorder(double mouseX, double mouseY) {
        return inputHandler.currentResizeCursor(mouseX, mouseY) != ResizeCursor.DEFAULT;
    }

    public ResizeCursor currentResizeCursor(double mouseX, double mouseY) {
        return inputHandler.currentResizeCursor(mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) { return inputHandler.mouseClicked(mx, my, btn); }
    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) { return inputHandler.mouseDragged(mx, my, btn, dx, dy); }
    @Override public boolean mouseReleased(double mx, double my, int btn) { return inputHandler.mouseReleased(mx, my, btn); }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) { return inputHandler.mouseScrolled(mx, my, sx, sy); }
    @Override public boolean keyPressed(int kc, int sc, int mod) { return inputHandler.keyPressed(kc, sc, mod); }
    @Override public boolean charTyped(char cp, int mod) { return inputHandler.charTyped(cp, mod); }
    @Override public void close() { setOpen(false); }

    protected int getTitleBarHeight() { return DEFAULT_TITLE_BAR_H; }
    public int getMinWindowWidth() { return DEFAULT_MIN_W; }
    public int getMinWindowHeight() { return DEFAULT_MIN_H; }
    protected int getResizeBorderWidth() { return DEFAULT_RESIZE_BORDER; }

    protected int getMaxWindowWidth() {
        return this.screen == null ? bounds.getWidth()
                : Math.max(getMinWindowWidth(), this.screen.getUiWidth() - SCREEN_MARGIN * 2);
    }

    protected int getMaxWindowHeight() {
        return this.screen == null ? bounds.getHeight()
                : Math.max(getMinWindowHeight(), this.screen.getUiHeight() - SCREEN_MARGIN * 2);
    }

    protected int getPanelBgColor() { return UiPalette.bg(); }
    protected int getPanelHoverBgColor() { return UiPalette.hoverBorder(); }
    protected int getPanelBorderColor() { return UiPalette.accent(); }
    protected int getTitleBarBgColor() { return UiPalette.border(); }
    protected int getTitleTextColor() { return ThemeManager.getTextColor(); }
    protected boolean canShowWindow() { return true; }
    protected boolean shouldClipContent() { return true; }
    protected boolean shouldUseSdfBackground() { return true; }

    protected int contentX() { return bounds.getX() + 1; }
    protected int contentY() { return bounds.getY() + getTitleBarHeight() + 3; }
    protected int contentWidth() { return Math.max(0, bounds.getWidth() - 2); }
    protected int contentHeight() { return Math.max(0, bounds.getHeight() - getTitleBarHeight() - 7); }

    protected boolean handleContentScroll(double mx, double my, double sx, double sy) { return true; }
    protected boolean handleWindowKeyPressed(int kc, int sc, int mod) { return false; }
    protected boolean handleWindowCharTyped(char cp, int mod) { return false; }
    protected void onClose() {}
    protected void onBoundsChanged() {}

    private CloseButton createCloseButton() { return WindowFrameRenderer.createCloseButton(); }

    protected void positionBelow(UiPanel aboveWindow, int gap) {
        bounds.setX(aboveWindow.getWindowX());
        bounds.setY(aboveWindow.getWindowY() + aboveWindow.getWindowHeight() + gap);
        clampWindowToScreen();
    }

    /**
     * 面板背景绘制完成之后、内容区裁剪建立之前的钩子：
     * 用于绘制需要延伸出内容区（如贴紧标题栏或面板边缘）的背景元素。
     */
    protected void onRenderBeforeContent(GuiGraphics g, int mouseX, int mouseY) {
    }

    protected void renderWindowFrame(GuiGraphics g, int mx, int my) {
        WindowFrameRenderer.renderFrame(g, mx, my, new WindowFrameRenderer.Context(
                bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(),
                getTitleBarHeight(), getPanelBgColor(), getPanelHoverBgColor(),
                getPanelBorderColor(),
                getTitleBarBgColor(), getTitleTextColor(), getTitle(), this.closable, this.closeButton,
                this.panelHoverState.get(), shouldUseSdfBackground()));
    }

    private void enableContentScissor(GuiGraphics g) {
        int x1 = contentX(), y1 = contentY(), x2 = x1 + contentWidth(), y2 = y1 + contentHeight();
        if (this.screen != null) this.screen.enableUiScissor(g, x1, y1, x2, y2);
        else g.enableScissor(x1, y1, x2, y2);
    }

    private void updatePanelHoverState(int mx, int my) {
        this.mouseHovering = !this.skipHoverDetection && isInsideWindow(mx, my);
        this.panelHoverState.track(this.mouseHovering);
    }

    void initializePosition() {
        if (bounds.needsInit()) initializeDefaultBounds();
    }

    private void initializeDefaultBounds() {
        bounds.resetToDefaults(); clampWindowSize(); computeDefaultPosition();
        clampWindowToScreen(); bounds.setInitialized(true);
    }

    private void ensureSizeInitialized() {
        if (bounds.needsSizeInit()) { bounds.resetToDefaults(); clampWindowSize(); }
    }

    public void clampWindowSize() {
        bounds.setWidth(Mth.clamp(bounds.getWidth(), getMinWindowWidth(), getMaxWindowWidth()));
        bounds.setHeight(Mth.clamp(bounds.getHeight(), getMinWindowHeight(), getMaxWindowHeight()));
    }

    public void clampWindowToScreen() {
        if (this.screen == null) return;
        int maxX = Math.max(0, this.screen.getUiWidth() - bounds.getWidth());
        int maxY = Math.max(0, this.screen.getUiHeight() - getTitleBarHeight());
        bounds.setX(Mth.clamp(bounds.getX(), 0, maxX));
        bounds.setY(Mth.clamp(bounds.getY(), 0, maxY));
    }
}
