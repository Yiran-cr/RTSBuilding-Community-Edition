package com.rtsbuilding.rtsbuilding.client.presentation.panel.rightbar;

import com.rtsbuilding.uifw.window.api.UiPanelApi;
import com.rtsbuilding.uifw.window.component.EdgeResizeHandler;
import com.rtsbuilding.uifw.window.overlay.DownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.rightbar.overlay.LowerRightOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.rightbar.overlay.UpperRightOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.uifw.render.UiPalette;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

public final class RightSidebarPanel implements UiPanelApi {

    
    private com.rtsbuilding.uifw.window.api.UiPanelHost screen;

    
    private int currentWidth = RightSidebarLayoutHelper.SIDEBAR_WIDTH;

    
    public void setCurrentWidth(int width) {
        this.currentWidth = Math.max(30, Math.min(width, maxWidthLimit()));
    }

    /**
     * 宽度 clamp 上限：统一使用 RTS 虚拟坐标宽/4，与拖拽保存时的基准一致。
     * 鼠标事件期间 {@code screen.getUiWidth()} 会被 {@code BuilderScreenScaleManager}
     * 临时改写为虚拟宽，而 init() 恢复时为 GUI 宽——若恢复沿用 GUI 宽/4 会与保存基准
     * 不一致，导致持久化的宽度在恢复时被截断（如被压到默认值）。
     */
    private int maxWidthLimit() {
        if (this.screen instanceof com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen bs) {
            return Math.max(30, bs.getRtsVirtualWidth() / 4);
        }
        return this.screen != null ? Math.max(30, this.screen.getUiWidth() / 4) : 2000;
    }

    /** 当前上下分区的分隔高度（供 UI 状态持久化读取；未拖动过返回 -1 表示使用默认比例）。 */
    public int getUpperOverlayHeight() {
        return this.upperOverlayHeight;
    }

    /** 直接设置上下分区分隔高度（供 UI 状态持久化恢复使用；{@code <=0} 表示使用默认比例）。 */
    public void setUpperOverlayHeight(int height) {
        this.upperOverlayHeight = height;
    }

    
    public int getCurrentWidth() {
        return currentWidth;
    }

    

    


    
    private final RightSidebarLayoutHelper layout = new RightSidebarLayoutHelper();

    
    private final EdgeResizeHandler resizeHandler = new EdgeResizeHandler(
            EdgeResizeHandler.Orientation.HORIZONTAL,
            EdgeResizeHandler.Side.LEADING,
            20);

    

    
    private final UpperRightOverlayLayer upperLayer = new UpperRightOverlayLayer();
    
    private final LowerRightOverlayLayer lowerLayer = new LowerRightOverlayLayer();

    

    
    private int upperOverlayHeight = -1;

    
    private boolean isDraggingOverlayDivider;

    
    private int dragOverlayDividerStartY;

    
    private int dragOverlayDividerStartUpperH;

    
    private static final int OVERLAY_DIVIDER_HALF_HIT = 2;

    
    private static final int OVERLAY_MIN_SIZE = 20;

    @Override
    public void init(com.rtsbuilding.uifw.window.api.UiPanelHost screen) {
        this.screen = Objects.requireNonNull(screen,
                "RightSidebarPanel.init() called with null screen");
    }

    
    private int defaultUpperOverlayHeight(int totalH) {
        int gap = 1;
        return Math.max(OVERLAY_MIN_SIZE, (totalH - gap) * 8 / 21);
    }

    
    private int clampUpperOverlayHeight(int h, int totalH) {
        int gap = 1;
        int maxUpper = totalH - gap - OVERLAY_MIN_SIZE;
        return Math.max(OVERLAY_MIN_SIZE, Math.min(maxUpper, h));
    }

    
    private int resolveUpperOverlayHeight() {
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        if (this.upperOverlayHeight <= 0) {
            return defaultUpperOverlayHeight(sb.height());
        }
        return clampUpperOverlayHeight(this.upperOverlayHeight, sb.height());
    }

    

    
    private RightSidebarLayoutHelper.Rect layoutRect() {
        return layout.sidebarRect(
                this.screen.getUiWidth(), this.screen.getUiHeight(), this.currentWidth);
    }

    

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        g.fill(sb.x(), sb.y(), sb.x() + sb.width(), sb.y() + sb.height(), UiPalette.bg());
    }

    
    @Override
    public void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        
        int ox = sb.x() + 1;
        int ow = sb.width() - 1;
        if (ow <= 0) return;

        int totalH = sb.height();
        int gap = 1;
        
        int upperH = resolveUpperOverlayHeight();

        
        upperLayer.setLastMousePos(mouseX, mouseY);
        upperLayer.setBounds(ox, sb.y(), ow, upperH);
        upperLayer.render(g, isDraggingOverlayDivider || isMouseInLayer(upperLayer, mouseX, mouseY));

        
        int bottomY = sb.y() + upperH + gap;
        int lowerH = totalH - upperH - gap;
        if (lowerH > 0) {
            lowerLayer.setLastMousePos(mouseX, mouseY);
            lowerLayer.setBounds(ox, bottomY, ow, lowerH);
            lowerLayer.render(g, isDraggingOverlayDivider || isMouseInLayer(lowerLayer, mouseX, mouseY));
        }
    }

    
    private boolean isMouseInLayer(DownOverlayLayer layer, int mouseX, int mouseY) {
        if (this.screen == null || ((com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen) this.screen).isMouseOverUI(mouseX, mouseY)) return false;
        return layer.contains(mouseX, mouseY);
    }

    
    public boolean isMouseOverOverlayDivider(int mx, int my) {
        return isMouseOverRightOverlayDivider(mx, my);
    }

    
    private boolean isMouseOverRightOverlayDivider(int mx, int my) {
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        if (sb.width() <= 0 || sb.height() <= 0) return false;
        
        int ox = sb.x() + 1;
        int ow = sb.width() - 1;
        if (mx < ox || mx >= ox + ow) return false;
        int divY = overlayDividerY();
        return my >= divY - OVERLAY_DIVIDER_HALF_HIT && my < divY + OVERLAY_DIVIDER_HALF_HIT + 1;
    }

    
    private int overlayDividerY() {
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        int totalH = sb.height();
        if (totalH <= 0) return 0;
        int upperH = resolveUpperOverlayHeight();
        return sb.y() + upperH;
    }

    

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int mx = (int) mouseX;
        int my = (int) mouseY;
        
        if (upperLayer.contains(mx, my) && upperLayer.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (lowerLayer.contains(mx, my) && lowerLayer.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        
        if (isMouseOverRightOverlayDivider(mx, my)) {
            isDraggingOverlayDivider = true;
            dragOverlayDividerStartY = my;
            dragOverlayDividerStartUpperH = resolveUpperOverlayHeight();
            return true;
        }
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        return resizeHandler.tryBegin(mouseX, mouseY,
                sb.x(), sb.y(), sb.height(),
                currentWidth, this.screen.getUiWidth());
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (upperLayer.contains(mx, my) && upperLayer.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (lowerLayer.contains(mx, my) && lowerLayer.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (isDraggingOverlayDivider) {
            isDraggingOverlayDivider = false;
            return true;
        }
        if (resizeHandler.isActive()) {
            resizeHandler.end();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) return false;
        int mx = (int) mouseX;
        int my = (int) mouseY;
        // 下层调节器滑块拖动优先：divider/resize 仅在未命中 layer 时接管
        if (upperLayer.contains(mx, my) && upperLayer.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (lowerLayer.contains(mx, my) && lowerLayer.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (isDraggingOverlayDivider) {
            int deltaY = my - dragOverlayDividerStartY;
            int newUpperH = dragOverlayDividerStartUpperH + deltaY;
            RightSidebarLayoutHelper.Rect sb = layoutRect();
            int totalH = sb.height();
            if (totalH > 0) {
                this.upperOverlayHeight = clampUpperOverlayHeight(newUpperH, totalH);
            }
            return true;
        }
        if (!resizeHandler.isActive()) return false;
        this.currentWidth = resizeHandler.computeNewSize(mouseX);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (upperLayer.contains(mx, my) && upperLayer.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return lowerLayer.contains(mx, my)
                && lowerLayer.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (upperLayer.keyPressed(keyCode, scanCode, modifiers)) return true;
        return lowerLayer.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (upperLayer.charTyped(codePoint, modifiers)) return true;
        return lowerLayer.charTyped(codePoint, modifiers);
    }

    

    
    public void resetOverlayDividerDrag() {
        isDraggingOverlayDivider = false;
    }

    
    public boolean isMouseOverLeftEdge(int mx, int my) {
        RightSidebarLayoutHelper.Rect sb = layoutRect();
        return resizeHandler.isOverEdge(mx, my, sb.x(), sb.y(), sb.height());
    }

    /** 鼠标是否悬浮于「方块剔除」调节框内（右栏下嵌层，供圆柱预览 pass 判定）。 */
    public boolean isMouseOverCullingAdjuster() {
        return lowerLayer.isMouseOverCullingAdjuster();
    }
}
