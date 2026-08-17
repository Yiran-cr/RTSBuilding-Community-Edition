package com.rtsbuilding.uifw.layout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GridLayout / UiSize 纯逻辑测试。
 */
class GridLayoutTest {

    @Test
    void gridTwoColumns() {
        List<UiRect> r = GridLayout.grid(2, 0, 0, 0, 0, 100, 50, 4);
        assertEquals(4, r.size());
        assertEquals(new UiRect(0, 0, 50, 25), r.get(0));
        assertEquals(new UiRect(50, 0, 50, 25), r.get(1));
        assertEquals(new UiRect(0, 25, 50, 25), r.get(2));
        assertEquals(new UiRect(50, 25, 50, 25), r.get(3));
    }

    @Test
    void gridWithGap() {
        // 2 列，宽 40，gapX 2 → 每格 (40-2)/2 = 19
        List<UiRect> r = GridLayout.grid(2, 2, 2, 0, 0, 40, 40, 2);
        assertEquals(new UiRect(0, 0, 19, 40), r.get(0));
        assertEquals(new UiRect(21, 0, 19, 40), r.get(1));
    }

    @Test
    void gridSingleRowRemainder() {
        // 5 个，3 列 → 2 行
        List<UiRect> r = GridLayout.grid(3, 0, 0, 0, 0, 90, 20, 5);
        assertEquals(5, r.size());
        assertEquals(new UiRect(30, 0, 30, 10), r.get(1)); // 第 0 行
        assertEquals(new UiRect(0, 10, 30, 10), r.get(3)); // 第 1 行
        assertEquals(new UiRect(30, 10, 30, 10), r.get(4));
    }

    @Test
    void gridCenteredCell() {
        // 指定 cell 40x20，格 60x30 → 居中偏移 10/5
        List<UiRect> r = GridLayout.grid(1, 0, 0, 0, 0, 60, 30, 1, 40, 20);
        assertEquals(new UiRect(10, 5, 40, 20), r.get(0));
    }

    @Test
    void uiSizePixelResolve() {
        assertEquals(20, UiSize.px(20).resolve(100, 0, 0));
        assertEquals(50, UiSize.percent(50).resolve(100, 0, 0));
        assertEquals(0, UiSize.auto().resolve(100, 0, 0));
    }

    @Test
    void uiSizeClamp() {
        assertEquals(20, UiSize.px(50).resolve(100, 10, 20));
        assertEquals(30, UiSize.px(5).resolve(100, 30, 0));
    }
}
