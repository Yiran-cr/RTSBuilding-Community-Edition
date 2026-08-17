package com.rtsbuilding.uifw.layout;

/**
 * 布局矩形：布局器为子项分配的最终位置与尺寸。
 */
public record UiRect(int x, int y, int w, int h) {

    public static final UiRect ZERO = new UiRect(0, 0, 0, 0);
}
