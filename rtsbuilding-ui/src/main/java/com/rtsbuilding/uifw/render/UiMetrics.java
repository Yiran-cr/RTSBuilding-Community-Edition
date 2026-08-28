package com.rtsbuilding.uifw.render;

/**
 * uifw 统一视觉度量常量：控件圆角半径等设计 token 的唯一来源。
 *
 * <p>所有 SDF 圆角绘制（按钮/输入框/小图标按钮/面板圆角）一律引用本类常量，
 * 禁止在调用点散落硬编码数字，保证整套 UI 圆角语言一致。</p>
 */
public final class UiMetrics {

    /** 控件级圆角半径：按钮、输入框、小图标按钮等交互控件。 */
    public static final float RADIUS_CONTROL = 3f;

    /** 面板级圆角半径：禁用遮罩等覆盖整块区域的大面积圆角。 */
    public static final float RADIUS_PANEL = 4f;

    private UiMetrics() {}
}
