package com.rtsbuilding.uifw.window.button;

/**
 * 按钮组布局源（前置 UI mod 解耦点）。
 *
 * <p>业务侧（如顶栏布局器）实现本接口，为按钮组提供每个按钮的矩形坐标，
 * 使 {@link AbstractButtonGroup} 不依赖具体布局器实现。
 */
public interface ButtonGroupLayout {

    /** 按钮组中按钮数量。 */
    int count();

    /** 第 {@code index} 个按钮的矩形区域。 */
    ButtonGroupRect rect(int index);
}
