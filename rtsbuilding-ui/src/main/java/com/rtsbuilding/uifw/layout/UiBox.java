package com.rtsbuilding.uifw.layout;

/**
 * 待布局子项声明：主轴/交叉轴的尺寸偏好、min/max 约束与 flex 权重。
 *
 * <p>工厂语义：{@link #fixed} 固定像素；{@link #content} 内容尺寸（同 fixed，
 * 可附 flex 权重参与剩余空间分配）；{@link #fill} 主轴 auto + flex 权重（占满剩余）。
 * 在 {@link FlexLayout} 中：ROW 方向主轴为宽度（height 为交叉轴），COLUMN 反之。</p>
 */
public final class UiBox {

    private final UiSize width;
    private final UiSize height;
    private final int minWidth;
    private final int minHeight;
    private final int maxWidth;
    private final int maxHeight;
    private final float flex;

    private UiBox(UiSize width, UiSize height,
                  int minWidth, int minHeight, int maxWidth, int maxHeight,
                  float flex) {
        this.width = width;
        this.height = height;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.flex = flex;
    }

    /** 固定像素尺寸，不参与剩余空间分配。 */
    public static UiBox fixed(int width, int height) {
        return new UiBox(UiSize.px(width), UiSize.px(height), 0, 0, 0, 0, 0);
    }

    /** 固定像素尺寸 + flex 权重（主轴剩余空间按权重分配）。 */
    public static UiBox fixed(int width, int height, float flex) {
        return new UiBox(UiSize.px(width), UiSize.px(height), 0, 0, 0, 0, flex);
    }

    /** 内容尺寸（即固定像素），可附 flex 权重。 */
    public static UiBox content(int width, int height, float flex) {
        return fixed(width, height, flex);
    }

    /** 主轴 auto + flex 1，占满父容器剩余空间。 */
    public static UiBox fill() {
        return fill(1f);
    }

    /** 主轴 auto + 指定 flex 权重。 */
    public static UiBox fill(float flex) {
        return new UiBox(UiSize.auto(), UiSize.auto(), 0, 0, 0, 0, flex);
    }

    /** 百分比宽度（如 {@code UiSize.percent(50)}），高度固定。 */
    public static UiBox percentWidth(float pct, int height) {
        return new UiBox(UiSize.percent(pct), UiSize.px(height), 0, 0, 0, 0, 0);
    }

    /** 百分比高度，宽度固定。 */
    public static UiBox percentHeight(int width, float pct) {
        return new UiBox(UiSize.px(width), UiSize.percent(pct), 0, 0, 0, 0, 0);
    }

    /** 主轴最小尺寸约束（row 方向为 minWidth，column 方向为 minHeight）。 */
    public UiBox minMain(int min, boolean row) {
        return row
                ? new UiBox(width, height, min, minHeight, maxWidth, maxHeight, flex)
                : new UiBox(width, height, minWidth, min, maxWidth, maxHeight, flex);
    }

    /** 主轴最大尺寸约束（row 方向为 maxWidth，column 方向为 maxHeight；0 表示无限制）。 */
    public UiBox maxMain(int max, boolean row) {
        return row
                ? new UiBox(width, height, minWidth, minHeight, max, maxHeight, flex)
                : new UiBox(width, height, minWidth, minHeight, maxWidth, max, flex);
    }

    public UiSize width() { return width; }
    public UiSize height() { return height; }
    public int minWidth() { return minWidth; }
    public int minHeight() { return minHeight; }
    public int maxWidth() { return maxWidth; }
    public int maxHeight() { return maxHeight; }
    public float flex() { return flex; }
}
