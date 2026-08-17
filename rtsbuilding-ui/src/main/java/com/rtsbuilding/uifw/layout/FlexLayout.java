package com.rtsbuilding.uifw.layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Flex 布局器：沿主轴（行/列）排布子项，支持固定/百分比/auto 尺寸、flex 权重分配剩余空间
 * 与主轴/交叉轴对齐。借鉴 CSS flexbox 子集，纯 Java 无依赖。
 *
 * <p>布局流程：解析每个子项主轴偏好尺寸 → 计算剩余空间 → 按 flex 权重分配（或按
 * {@link Justify} 排布）→ 交叉轴按 {@link Align} 定位 → 输出 {@link UiRect} 列表
 * （与传入 children 顺序一一对应）。</p>
 */
public final class FlexLayout {

    public enum Direction { ROW, COLUMN }

    /** 主轴对齐（无 flex 子项时的剩余空间处理）。 */
    public enum Justify { START, CENTER, END, SPACE_BETWEEN, SPACE_EVENLY }

    /** 交叉轴对齐。 */
    public enum Align { STRETCH, START, CENTER, END }

    private FlexLayout() {}

    /**
     * 在 {@code (x, y, w, h)} 区域内沿 {@code direction} 排布子项。
     *
     * @param justify 主轴对齐（有 flex 子项时忽略，剩余空间全部按权重分配）
     * @param align   交叉轴对齐
     * @param gap     相邻子项间距
     * @return 与 children 同顺序的 UiRect 列表
     */
    public static List<UiRect> layout(Direction direction, Justify justify, Align align, int gap,
                                      int x, int y, int w, int h, List<UiBox> children) {
        int n = children.size();
        List<UiRect> out = new ArrayList<>(n);
        if (n == 0) return out;
        if (gap < 0) gap = 0;

        boolean row = direction == Direction.ROW;
        int mainSize = row ? w : h;
        int crossSize = row ? h : w;

        // 1. 解析主轴偏好尺寸
        int[] sizes = new int[n];
        int[] minMain = new int[n];
        int[] maxMain = new int[n];
        float[] flex = new float[n];
        int total = 0;
        for (int i = 0; i < n; i++) {
            UiBox box = children.get(i);
            UiSize sz = row ? box.width() : box.height();
            sizes[i] = sz.resolve(mainSize, 0, Integer.MAX_VALUE);
            minMain[i] = row ? box.minWidth() : box.minHeight();
            maxMain[i] = row ? box.maxWidth() : box.maxHeight();
            flex[i] = box.flex();
            total += sizes[i];
        }
        int remaining = mainSize - total - gap * (n - 1);

        // 2. 主轴排布：起始偏移 + 间隙
        int[] gaps = new int[Math.max(0, n - 1)];
        Arrays.fill(gaps, gap);
        int pos = 0;
        if (remaining > 0) {
            float totalFlex = 0;
            for (float f : flex) if (f > 0) totalFlex += f;
            if (totalFlex > 0) {
                distributeFlex(sizes, flex, maxMain, total, remaining, totalFlex);
            } else {
                switch (justify) {
                    case CENTER -> pos = remaining / 2;
                    case END -> pos = remaining;
                    case SPACE_BETWEEN -> {
                        if (n > 1) {
                            int extra = remaining / (n - 1);
                            for (int i = 0; i < gaps.length; i++) gaps[i] += extra;
                        }
                    }
                    case SPACE_EVENLY -> {
                        pos = remaining / (n + 1);
                        if (n > 1) {
                            int extra = (remaining % (n + 1)) / (n - 1);
                            for (int i = 0; i < gaps.length; i++) gaps[i] += extra;
                        }
                    }
                    default -> { /* START */ }
                }
            }
        }

        // 3. 交叉轴定位
        int[] crossStart = new int[n];
        int[] crossLen = new int[n];
        for (int i = 0; i < n; i++) {
            UiBox box = children.get(i);
            UiSize crossDecl = row ? box.height() : box.width();
            int cmin = row ? box.minHeight() : box.minWidth();
            int cmax = row ? box.maxHeight() : box.maxWidth();
            if (align == Align.STRETCH || crossDecl.unit() == UiSize.Unit.AUTO) {
                crossStart[i] = 0;
                crossLen[i] = crossSize;
            } else {
                int len = crossDecl.resolve(crossSize, cmin, cmax);
                crossStart[i] = switch (align) {
                    case CENTER -> (crossSize - len) / 2;
                    case END -> crossSize - len;
                    default -> 0;
                };
                crossLen[i] = len;
            }
        }

        // 4. 组装矩形
        for (int i = 0; i < n; i++) {
            int start = pos;
            if (row) {
                out.add(new UiRect(x + start, y + crossStart[i], sizes[i], crossLen[i]));
            } else {
                out.add(new UiRect(x + crossStart[i], y + start, crossLen[i], sizes[i]));
            }
            pos = start + sizes[i] + (i < n - 1 ? gaps[i] : 0);
        }
        return out;
    }

    /** 按 flex 权重分配剩余空间（带 max 约束，一次分配 + 尾差补偿）。 */
    private static void distributeFlex(int[] sizes, float[] flex, int[] maxMain,
                                       int totalBase, int remaining, float totalFlex) {
        int assigned = 0;
        int n = sizes.length;
        for (int i = 0; i < n; i++) {
            if (flex[i] <= 0) continue;
            int room = maxMain[i] <= 0 ? Integer.MAX_VALUE : maxMain[i] - sizes[i];
            int share = (int) (remaining * flex[i] / totalFlex);
            if (room <= 0) share = 0;
            else share = Math.min(share, room);
            sizes[i] += share;
            assigned += share;
        }
        // 尾差（取整丢失）补偿给第一个未达上限的 flex 子项
        int leftover = remaining - assigned;
        if (leftover != 0) {
            for (int i = 0; i < n; i++) {
                if (flex[i] <= 0) continue;
                int room = maxMain[i] <= 0 ? Integer.MAX_VALUE : maxMain[i] - sizes[i];
                if (room > 0) {
                    sizes[i] += Math.min(leftover, room);
                    break;
                }
            }
        }
    }
}
