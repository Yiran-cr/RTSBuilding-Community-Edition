package com.rtsbuilding.uifw.window.overlay;

public interface OverlayContext {
    int getX();
    int getY();
    int getWidth();
    int getHeight();
    int getLastMouseX();
    int getLastMouseY();
    boolean contains(int px, int py);
    boolean isDividerDragging();
}
