package com.rtsbuilding.uifw.layout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FlexLayout 布局数学测试（纯逻辑，无 Minecraft 依赖）。
 */
class FlexLayoutTest {

    private static UiBox fx(int w, int h) {
        return UiBox.fixed(w, h);
    }

    @Test
    void rowFixedNoGap() {
        List<UiRect> r = FlexLayout.layout(FlexLayout.Direction.ROW, FlexLayout.Justify.START,
                FlexLayout.Align.STRETCH, 0, 0, 0, 40, 10,
                List.of(fx(10, 10), fx(10, 10), fx(10, 10)));
        assertEquals(3, r.size());
        assertEquals(new UiRect(0, 0, 10, 10), r.get(0));
        assertEquals(new UiRect(10, 0, 10, 10), r.get(1));
        assertEquals(new UiRect(20, 0, 10, 10), r.get(2));
    }

    @Test
    void rowWithGap() {
        List<UiRect> r = FlexLayout.layout(FlexLayout.Direction.ROW, FlexLayout.Justify.START,
                FlexLayout.Align.STRETCH, 4, 0, 0, 100, 10,
                List.of(fx(10, 10), fx(10, 10)));
        assertEquals(new UiRect(0, 0, 10, 10), r.get(0));
        assertEquals(new UiRect(14, 0, 10, 10), r.get(1));
    }

    @Test
    void flexFillsRemaining() {
        // fill(1) 占满剩余：容器 100，固定 20，剩余 80 给 fill
        List<UiRect> r = FlexLayout.layout(FlexLayout.Direction.ROW, FlexLayout.Justify.START,
                FlexLayout.Align.STRETCH, 0, 0, 0, 100, 10,
                List.of(fx(20, 10), UiBox.fill(1f)));
        assertEquals(20, r.get(0).w());
        assertEquals(80, r.get(1).w());
        assertEquals(20, r.get(1).x());
    }

    @Test
    void flexWeightSplit() {
        // 两个 fill 权重 1:2 → 剩余 90 按 1:2 分 30/60
        List<UiRect> r = FlexLayout.layout(FlexLayout.Direction.ROW, FlexLayout.Justify.START,
                FlexLayout.Align.STRETCH, 0, 0, 0, 90, 10,
                List.of(UiBox.fill(1f), UiBox.fill(2f)));
        assertEquals(30, r.get(0).w());
        assertEquals(60, r.get(1).w());
    }

    @Test
    void justifyCenter() {
        // 三个固定 10 宽，容器 40，剩余 10，居中 → 起点 5
        List<UiRect> r = FlexLayout.layout(FlexLayout.Direction.ROW, FlexLayout.Justify.CENTER,
                FlexLayout.Align.STRETCH, 0, 0, 0, 40, 10,
                List.of(fx(10, 10), fx(10, 10), fx(10, 10)));
        assertEquals(5, r.get(0).x());
        assertEquals(15, r.get(1).x());
        assertEquals(25, r.get(2).x());
    }

    @Test
    void justifyEnd() {
        List<UiRect> r = FlexLayout.layout(FlexLayout.Direction.ROW, FlexLayout.Justify.END,
                FlexLayout.Align.STRETCH, 0, 0, 0, 40, 10,
                List.of(fx(10, 10), fx(10, 10)));
        assertEquals(20, r.get(0).x());
        assertEquals(30, r.get(1).x());
    }

    @Test
    void justifySpaceBetween() {
        // 容器 40，两个 10 宽，剩余 20 → 间隙 20
        List<UiRect> r = FlexLayout.layout(FlexLayout.Direction.ROW, FlexLayout.Justify.SPACE_BETWEEN,
                FlexLayout.Align.STRETCH, 0, 0, 0, 40, 10,
                List.of(fx(10, 10), fx(10, 10)));
        assertEquals(0, r.get(0).x());
        assertEquals(30, r.get(1).x());
    }

    @Test
    void columnDirection() {
        List<UiRect> r = FlexLayout.layout(FlexLayout.Direction.COLUMN, FlexLayout.Justify.START,
                FlexLayout.Align.STRETCH, 0, 0, 0, 10, 40,
                List.of(fx(10, 10), fx(10, 10)));
        assertEquals(new UiRect(0, 0, 10, 10), r.get(0));
        assertEquals(new UiRect(0, 10, 10, 10), r.get(1));
    }

    @Test
    void percentWidth() {
        // 50% 宽，容器 100
        List<UiRect> r = FlexLayout.layout(FlexLayout.Direction.ROW, FlexLayout.Justify.START,
                FlexLayout.Align.STRETCH, 0, 0, 0, 100, 10,
                List.of(UiBox.percentWidth(50, 10)));
        assertEquals(50, r.get(0).w());
    }

    @Test
    void alignCenterCrossAxis() {
        // 交叉轴高 20，子项高 10 → 交叉居中 y=5
        List<UiRect> r = FlexLayout.layout(FlexLayout.Direction.ROW, FlexLayout.Justify.START,
                FlexLayout.Align.CENTER, 0, 0, 0, 100, 20,
                List.of(UiBox.fixed(10, 10)));
        assertEquals(5, r.get(0).y());
        assertEquals(10, r.get(0).h());
    }

    @Test
    void stretchCrossAxis() {
        List<UiRect> r = FlexLayout.layout(FlexLayout.Direction.ROW, FlexLayout.Justify.START,
                FlexLayout.Align.STRETCH, 0, 0, 0, 100, 20,
                List.of(UiBox.fill(1f)));
        assertEquals(0, r.get(0).y());
        assertEquals(20, r.get(0).h());
    }
}
