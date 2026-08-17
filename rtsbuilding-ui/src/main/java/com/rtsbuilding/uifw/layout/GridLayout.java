package com.rtsbuilding.uifw.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * 网格布局器：按固定列数在容器内均分格子，支持行列间距。纯 Java 无依赖。
 *
 * <p>每格宽度 = 容器宽均分（扣除间隙），行数 = 子项数按列数向上取整，
 * 每行高度 = 容器高均分（扣除行间隙）。返回与 0..count-1 顺序对应的 {@link UiRect}。</p>
 */
public final class GridLayout {

    private GridLayout() {}

    /**
     * 在 {@code (x, y, w, h)} 区域内排布 {@code count} 个均分格子。
     *
     * @param cols 每行列数（>0）
     */
    public static List<UiRect> grid(int cols, int gapX, int gapY,
                                    int x, int y, int w, int h, int count) {
        return grid(cols, gapX, gapY, x, y, w, h, count, 0, 0);
    }

    /**
     * 均分网格，并允许指定内容尺寸的格子居中放置。
     *
     * @param cellW 指定格子宽（&gt;0 时格子按此宽在格内居中，0 表示撑满）
     * @param cellH 指定格子高（&gt;0 时格子按此高在格内居中，0 表示撑满）
     */
    public static List<UiRect> grid(int cols, int gapX, int gapY,
                                    int x, int y, int w, int h, int count,
                                    int cellW, int cellH) {
        List<UiRect> out = new ArrayList<>(count);
        if (count <= 0 || cols <= 0) return out;
        if (gapX < 0) gapX = 0;
        if (gapY < 0) gapY = 0;

        int cellFullW = (w - gapX * (cols - 1)) / cols;
        int rows = (count + cols - 1) / cols;
        int cellFullH = (h - gapY * (rows - 1)) / rows;

        for (int i = 0; i < count; i++) {
            int col = i % cols;
            int row = i / cols;
            int gx = x + col * (cellFullW + gapX);
            int gy = y + row * (cellFullH + gapY);
            if (cellW > 0 || cellH > 0) {
                int cw = cellW > 0 ? Math.min(cellW, cellFullW) : cellFullW;
                int ch = cellH > 0 ? Math.min(cellH, cellFullH) : cellFullH;
                gx += (cellFullW - cw) / 2;
                gy += (cellFullH - ch) / 2;
                out.add(new UiRect(gx, gy, cw, ch));
            } else {
                out.add(new UiRect(gx, gy, cellFullW, cellFullH));
            }
        }
        return out;
    }
}
