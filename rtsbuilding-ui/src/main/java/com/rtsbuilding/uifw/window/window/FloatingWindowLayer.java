package com.rtsbuilding.uifw.window.window;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FloatingWindowLayer {

    private final List<UiPanel> frontToBackWindows;
    
    private boolean sortDirty;

    
    public void markSortDirty() {
        this.sortDirty = true;
    }

    public FloatingWindowLayer(UiPanel... frontToBackWindows) {
        this.frontToBackWindows = new ArrayList<>(List.of(frontToBackWindows));
        this.sortDirty = true;
        for (int i = frontToBackWindows.length - 1; i >= 0; i--) {
            frontToBackWindows[i].markBroughtToFront();
        }
    }

    
    public List<UiPanel> frontToBackWindows() {
        return this.frontToBackWindows;
    }

    

    public void renderFloatingWindows(GuiGraphics g, int mouseX, int mouseY) {
        if (this.frontToBackWindows.isEmpty()) return;

        
        if (this.sortDirty) {
            this.frontToBackWindows.sort(Comparator.comparingLong(UiPanel::getLastClickTime));
            this.sortDirty = false;
        }

        int topmostHoverIdx = -1;
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            UiPanel window = this.frontToBackWindows.get(i);
            if (window.isOpen() && window.isInsideWindow(mouseX, mouseY)) {
                topmostHoverIdx = i;
                break;
            }
        }

        for (int i = 0; i < this.frontToBackWindows.size(); i++) {
            UiPanel window = this.frontToBackWindows.get(i);
            if (!window.isOpen()) continue;

            
            RenderSystem.disableDepthTest();

            boolean shouldSuppress = topmostHoverIdx >= 0 && i != topmostHoverIdx
                    && window.isInsideWindow(mouseX, mouseY);
            window.setSkipHoverDetection(shouldSuppress);
            try {
                window.render(g, mouseX, mouseY, 0.0F);
            } finally {
                window.setSkipHoverDetection(false);
            }
        }
    }

    public void renderFloatingWindowOverlays(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            UiPanel window = this.frontToBackWindows.get(i);
            if (window.isOpen() && window.isInsideWindow(mouseX, mouseY)) {
                window.renderOverlays(g, mouseX, mouseY);
                return;
            }
        }
    }

    public UiPanel.ResizeCursor resizeCursorAt(double mouseX, double mouseY) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            UiPanel window = this.frontToBackWindows.get(i);
            if (!window.isOpen()) continue;
            UiPanel.ResizeCursor cursor = window.currentResizeCursor(mouseX, mouseY);
            if (cursor != UiPanel.ResizeCursor.DEFAULT) {
                return cursor;
            }
            
            
            if (window.isInsideWindow(mouseX, mouseY)) {
                return UiPanel.ResizeCursor.DEFAULT;
            }
        }
        return UiPanel.ResizeCursor.DEFAULT;
    }

    public boolean isMouseOverWindowOrResizableBorder(double mouseX, double mouseY) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            UiPanel window = this.frontToBackWindows.get(i);
            if (window.isOpen()
                    && (window.isInsideWindow(mouseX, mouseY) || window.isInsideResizableBorder(mouseX, mouseY))) {
                return true;
            }
        }
        return false;
    }

    

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        
        int snapshotSize = this.frontToBackWindows.size();
        long[] timestamps = new long[snapshotSize];
        for (int j = 0; j < snapshotSize; j++) {
            timestamps[j] = this.frontToBackWindows.get(j).getLastClickTime();
        }

        for (int i = snapshotSize - 1; i >= 0; i--) {
            
            if (i >= this.frontToBackWindows.size()) continue;
            UiPanel window = this.frontToBackWindows.get(i);
            if (!window.isOpen()) continue;
            int windowIdx = i;
            if (window.mouseClicked(mouseX, mouseY, button)) {
                
                
                
                boolean otherPanelBroughtToFront = false;
                
                if (this.frontToBackWindows.size() != snapshotSize) {
                    otherPanelBroughtToFront = true;
                } else {
                    for (int j = 0; j < snapshotSize; j++) {
                        if (j != windowIdx && this.frontToBackWindows.get(j).getLastClickTime() > timestamps[j]) {
                            otherPanelBroughtToFront = true;
                            break;
                        }
                    }
                }
                if (!otherPanelBroughtToFront) {
                    window.markBroughtToFront();
                }
                this.sortDirty = true;
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            UiPanel window = this.frontToBackWindows.get(i);
            if (!window.isOpen()) continue;
            if (window.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        for (UiPanel window : this.frontToBackWindows) {
            if (!window.isOpen()) continue;
            handled = window.mouseReleased(mouseX, mouseY, button) || handled;
        }
        return handled;
    }

        public void mouseMoved(double mouseX, double mouseY) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            UiPanel window = this.frontToBackWindows.get(i);
            if (!window.isOpen()) continue;
            if (window.mouseMoved(mouseX, mouseY)) {
                return;
            }
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            UiPanel window = this.frontToBackWindows.get(i);
            if (!window.isOpen()) continue;
            if (window.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            UiPanel window = this.frontToBackWindows.get(i);
            if (!window.isOpen()) continue;

            
            if (window.handleWindowKeyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }

            
            if (keyCode == GLFW.GLFW_KEY_ESCAPE && window.closable) {
                window.setOpen(false);
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            if (this.frontToBackWindows.get(i).charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return false;
    }
}
