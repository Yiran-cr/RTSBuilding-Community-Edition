package com.rtsbuilding.uifw.window.window.handler;

import com.rtsbuilding.uifw.window.window.UiPanel;

public class PanelDragPerformanceOptimizer {
    
    private static UiPanel currentlyDraggingPanel = null;
    
    
    public static boolean isPanelBeingDragged(UiPanel panel) {
        return currentlyDraggingPanel == panel;
    }
    
    
    public static void setCurrentlyDraggingPanel(UiPanel panel) {
        currentlyDraggingPanel = panel;
    }
    
    
    public static void clearDraggingPanel() {
        currentlyDraggingPanel = null;
    }
    
    
    public static boolean isAnyPanelBeingDragged() {
        return currentlyDraggingPanel != null;
    }
}