package com.rtsbuilding.uifw.layout;

/**
 * 尺寸声明：固定像素 / 父容器百分比 / 自适应（auto）。
 *
 * <p>借鉴 CSS/flexbox 的尺寸模型：{@code px} 固定值、{@code percent} 相对父容器、
 * {@code auto} 由 flex 权重分配剩余空间。纯布局数学，不依赖渲染。</p>
 */
public final class UiSize {

    public enum Unit {
        PIXELS,
        PERCENT,
        AUTO
    }

    private final Unit unit;
    private final float value;

    private UiSize(Unit unit, float value) {
        this.unit = unit;
        this.value = value;
    }

    /** 固定像素尺寸。 */
    public static UiSize px(float value) {
        return new UiSize(Unit.PIXELS, value);
    }

    /** 父容器尺寸的百分比（0-100）。 */
    public static UiSize percent(float value) {
        return new UiSize(Unit.PERCENT, value);
    }

    /** 自适应（内容/剩余空间分配）。 */
    public static UiSize auto() {
        return new UiSize(Unit.AUTO, 0);
    }

    public Unit unit() {
        return unit;
    }

    public float value() {
        return value;
    }

    /**
     * 在给定父尺寸下解析为像素，夹在 {@code [min, max]}（{@code max <= 0} 表示无上限）。
     * {@code AUTO} 解析为 0（由布局器按 flex 权重分配剩余空间）。
     */
    public int resolve(int parentSize, int min, int max) {
        int raw = switch (unit) {
            case PIXELS -> Math.round(value);
            case PERCENT -> Math.round(parentSize * value / 100f);
            case AUTO -> 0;
        };
        int upper = max <= 0 ? Integer.MAX_VALUE : max;
        return Math.max(min, Math.min(upper, raw));
    }

    @Override
    public String toString() {
        return switch (unit) {
            case PIXELS -> value + "px";
            case PERCENT -> value + "%";
            case AUTO -> "auto";
        };
    }
}
