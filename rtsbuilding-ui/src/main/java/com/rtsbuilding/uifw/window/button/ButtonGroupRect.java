package com.rtsbuilding.uifw.window.button;

/**
 * 按钮矩形区域（不可变坐标）。业务侧布局器返回的几何信息。
 */
public record ButtonGroupRect(int x, int y, int width, int height) {

    public boolean contains(int px, int py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }
}
