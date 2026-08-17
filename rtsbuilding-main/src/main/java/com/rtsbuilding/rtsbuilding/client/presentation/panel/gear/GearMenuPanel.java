package com.rtsbuilding.rtsbuilding.client.presentation.panel.gear;

import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.uifw.layout.FlexLayout;
import com.rtsbuilding.uifw.layout.UiBox;
import com.rtsbuilding.uifw.layout.UiRect;
import com.rtsbuilding.uifw.window.component.ScrollBar;
import com.rtsbuilding.uifw.window.window.UiPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.uifw.render.UiPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreenConstants.*;

public final class GearMenuPanel extends UiPanel {
    private static final int LEGACY_DEFAULT_WINDOW_W = 200;
    private static final int LEGACY_DEFAULT_WINDOW_H = 284;
    private static final int DEFAULT_WINDOW_W = 253;
    private static final int MIN_WINDOW_W = 187;
    
    private static final int SCROLLBAR_RIGHT_GAP = 11;
    
    private static final int CONTENT_WIDTH_REDUCTION = 6;
    
    /** 分区卡片顶部内边距（px）。内容渲染起点用它补偿，使首个分区贴内容区顶部，与滚动条轨道中心对齐。 */
    private static final int CONTENT_TOP_PAD = 8;
    private CameraModule cameraModule = null;
    private final RenderingSection renderingSection = new RenderingSection();
    private final PersonalizationSection personalizationSection = new PersonalizationSection();
    private final OperationSection operationSection = new OperationSection();
    private final KeybindSection keybindSection = new KeybindSection();
    private final IntegrationSection integrationSection = new IntegrationSection();

    
    private final ScrollBar scrollBar = new ScrollBar().withScrollBottomPad(0);

    @Override
    public void init(com.rtsbuilding.uifw.window.api.UiPanelHost screen) {
        super.init(screen);
        this.resizable = true;
        RtsClientKernel kernel = RtsClientKernel.get();
        this.cameraModule = kernel.module(CameraModule.class);
        this.operationSection.setCameraModule(this.cameraModule);
        this.renderingSection.setColorPickerPanel(
                ((BuilderScreen) screen).getColorPickerPanel());
        this.renderingSection.setColorPickerButtonParent(this);
    }

    public void open() {
        setOpen(true);
        markBroughtToFront();
    }

    

    
    private int totalSectionHeight(int cw) {
        return renderingSection.totalHeight(cw)
                + personalizationSection.totalHeight(cw)
                + operationSection.totalHeight(cw)
                + keybindSection.totalHeight(cw)
                + (integrationSection.hasIntegrations() ? integrationSection.totalHeight(cw) : 0);
    }

    /**
     * 五个分区垂直堆叠（ColumnLayout）。渲染与点击命中共用同一布局，保证坐标一致。
     * 无宿主集成时最后一个分区高度为 0。
     */
    private List<UiRect> sectionRects(int cx, int scrolledCy, int cw, int ch) {
        return FlexLayout.layout(FlexLayout.Direction.COLUMN, FlexLayout.Justify.START,
                FlexLayout.Align.STRETCH, 0, cx, scrolledCy, cw, ch,
                List.of(
                        UiBox.fixed(0, renderingSection.totalHeight(cw)),
                        UiBox.fixed(0, personalizationSection.totalHeight(cw)),
                        UiBox.fixed(0, operationSection.totalHeight(cw)),
                        UiBox.fixed(0, keybindSection.totalHeight(cw)),
                        UiBox.fixed(0, integrationSection.hasIntegrations() ? integrationSection.totalHeight(cw) : 0)));
    }

    

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        
        renderingSection.renderColorTooltips(g, mouseX, mouseY);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();
        int ch = contentHeight();

        
        int totalH = totalSectionHeight(cw);
        scrollBar.setContent(totalH, ch);

        int scroll = scrollBar.getScroll();

        
        int sectionRenderW = cw - CONTENT_WIDTH_REDUCTION;

        
        // 内容渲染起点：补偿分区卡片顶部内边距（CONTENT_TOP_PAD），使首个分区贴内容区顶部，
        // 内容块与滚动条轨道 [cy, cy+ch] 中心对齐。
        int scrolledCy = cy - CONTENT_TOP_PAD - scroll;
        List<UiRect> sectionRects = sectionRects(cx, scrolledCy, cw, ch);
        renderingSection.render(g, mouseX, mouseY, cx, sectionRects.get(0).y(), sectionRenderW);
        personalizationSection.render(g, mouseX, mouseY, cx, sectionRects.get(1).y(), sectionRenderW);
        operationSection.render(g, mouseX, mouseY, cx, sectionRects.get(2).y(), sectionRenderW);
        keybindSection.render(g, mouseX, mouseY, cx, sectionRects.get(3).y(), sectionRenderW);
        // 无宿主集成时不显示「宿主集成」分区
        if (integrationSection.hasIntegrations()) {
            integrationSection.render(g, mouseX, mouseY, cx, sectionRects.get(4).y(), sectionRenderW);
        }

        // 滚动条轨道覆盖整个内容窗口 [cy, cy+ch]，与内容可视区域对齐
        if (scrollBar.isVisible()) {
            int barX = cx + cw - SCROLLBAR_RIGHT_GAP;
            scrollBar.render(g, barX, cy, ch);
        }
    }

    

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int cx = contentX();
            int cy = contentY();
            int cw = contentWidth();
            int ch = contentHeight();

            
            if (scrollBar.isVisible()) {
                int barX = cx + cw - SCROLLBAR_RIGHT_GAP;
                if (scrollBar.handleClick(mouseX, mouseY, barX, cy, ch)) {
                    return;
                }
            }

            
            int sectionClickW = cw - CONTENT_WIDTH_REDUCTION;

            int scroll = scrollBar.getScroll();
            int scrolledCy = cy - CONTENT_TOP_PAD - scroll;
            List<UiRect> sectionRects = sectionRects(cx, scrolledCy, cw, ch);

            if (renderingSection.handleClick(mouseX, mouseY, cx, sectionRects.get(0).y(), sectionClickW)) return;
            if (personalizationSection.handleClick(mouseX, mouseY, cx, sectionRects.get(1).y(), sectionClickW)) return;
            if (operationSection.handleClick(mouseX, mouseY, cx, sectionRects.get(2).y(), sectionClickW)) return;
            if (keybindSection.handleClick(mouseX, mouseY, cx, sectionRects.get(3).y(), sectionClickW)) return;
            if (integrationSection.hasIntegrations()) {
                integrationSection.handleClick(mouseX, mouseY, cx, sectionRects.get(4).y(), sectionClickW);
            }
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            
            if (scrollBar.isDragging()) {
                int cy = contentY();
                int ch = contentHeight();
                scrollBar.handleDrag(mouseY, cy, ch);
                return true;
            }
            
            if (operationSection.isSliderDragging()) {
                operationSection.handleSliderDrag(mouseX);
                return true;
            }
            
            if (renderingSection.isSliderDragging()) {
                renderingSection.handleSliderDrag(mouseX);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollBar.endDrag();
        operationSection.endSliderDrag();
        renderingSection.endSliderDrag();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        
        if (operationSection.handleSliderScroll(mouseX, mouseY, scrollY)) {
            return true;
        }
        
        if (renderingSection.handleSliderScroll(mouseX, mouseY, scrollY)) {
            return true;
        }
        return scrollBar.handleScroll(scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (keybindSection.isCapturing() && isInsideWindow(mouseX, mouseY)) {
            keybindSection.captureMouse(button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keybindSection.isCapturing()) {
            keybindSection.captureKey(keyCode, scanCode, modifiers);
            return true;
        }
        return super.handleWindowKeyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void onClose() {
        keybindSection.cancelCapture();
        super.onClose();
    }

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.settings.title");
    }

    @Override
    protected int getDefaultWidth() {
        return DEFAULT_WINDOW_W;
    }

    @Override
    protected int getDefaultHeight() {
        return GEAR_MENU_H;
    }

    @Override
    public int getMinWindowWidth() {
        return MIN_WINDOW_W;
    }

    @Override
    public int getMinWindowHeight() {
        return GEAR_MENU_MIN_H;
    }

    @Override
    protected boolean shouldUseSdfBackground() {
        return true;
    }

    @Override
    protected int getPanelBgColor() { return UiPalette.bg(); }

    @Override
    protected int getPanelBorderColor() { return UiPalette.accent(); }

    @Override
    protected int getPanelHoverBgColor() { return UiPalette.hoverBorder(); }

    @Override
    protected int getTitleBarBgColor() { return UiPalette.border(); }

    @Override
    protected int contentY() { return bounds.getY() + getTitleBarHeight() + 2; }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        boolean legacyDefaultBounds = width == LEGACY_DEFAULT_WINDOW_W && height == LEGACY_DEFAULT_WINDOW_H;
        int restoredWidth = legacyDefaultBounds ? DEFAULT_WINDOW_W : width;
        int restoredHeight = legacyDefaultBounds ? GEAR_MENU_H : height;
        super.setBounds(x, y, restoredWidth, restoredHeight);
    }

    @Override
    protected int getMaxWindowWidth() {
        if (this.screen == null) {
            return super.getMaxWindowWidth();
        }
        int viewportLimit = Math.max(getMinWindowWidth(), (this.screen.getUiWidth() * 2) / 3);
        return Math.min(super.getMaxWindowWidth(), viewportLimit);
    }

    @Override
    protected int getMaxWindowHeight() {
        if (this.screen == null) {
            return super.getMaxWindowHeight();
        }
        int viewportLimit = Math.max(getMinWindowHeight(), (this.screen.getUiHeight() * 2) / 3);
        return Math.min(super.getMaxWindowHeight(), viewportLimit);
    }

    @Override
    protected void computeDefaultPosition() {
        // 统一基准：水平居中 + 垂直居中，顶部避开顶栏（TOP_H+6）、底部留 8px 边距
        positionCentered(TOP_H + 6, 8);
    }

    public CameraModule getCameraModule() {
        return cameraModule;
    }
}
