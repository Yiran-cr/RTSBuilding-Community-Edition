package com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar;

import net.minecraft.client.gui.GuiGraphics;

/**
 * XYZ 轴视角调节器 —— 类似 Blender 右上角的 3D 导航球（trackball / navigation gizmo）。
 * <p>
 * 门面类：组合投影数学（{@link TrackballProjection}）、渲染（{@link AxisGizmoRenderer}）
 * 与输入（{@link AxisGizmoInputHandler}），对外暴露统一的渲染/输入接口。
 * </p>
 * <ul>
 *   <li>点击朝向观察者的轴端：切换视角到对应轴方向（正端看向正方向、负端看向负方向）；</li>
 *   <li>按住球体任意处拖拽：自由旋转相机视角（与相机拖拽旋转同一通道）。</li>
 * </ul>
 * 纯客户端相机操作，不产生网络交互。
 */
public final class AxisViewGizmo {

    /** 面板总宽度 */
    public static final int WIDTH = TrackballProjection.WIDTH;

    /** 面板总高度 */
    public static final int HEIGHT = TrackballProjection.HEIGHT;

    private final AxisGizmoState state = new AxisGizmoState();
    private final TrackballProjection projection = new TrackballProjection();
    private final AxisGizmoRenderer renderer = new AxisGizmoRenderer(projection);
    private final AxisGizmoInputHandler inputHandler = new AxisGizmoInputHandler(projection);

    /**
     * 设置面板位置。
     *
     * @param x 左上角 X
     * @param y 左上角 Y
     */
    public void setBounds(int x, int y) {
        state.setBounds(x, y);
    }

    /**
     * 命中检测：坐标是否落在面板矩形内。
     */
    public boolean contains(int px, int py) {
        return state.contains(px, py);
    }

    /**
     * 查询是否处于拖拽旋转状态（供上层转发拖拽事件判断）。
     */
    public boolean isDragging() {
        return state.isDragging();
    }

    /**
     * 渲染 3D 轨道球。
     */
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        renderer.render(g, state, mouseX, mouseY);
    }

    /**
     * 鼠标点击处理。
     *
     * @return 是否命中并消费了本次点击
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return inputHandler.mouseClicked(state, mouseX, mouseY, button);
    }

    /**
     * 拖拽处理。
     *
     * @return 是否消费了本次拖拽
     */
    public boolean mouseDragged(double dragX, double dragY) {
        return inputHandler.mouseDragged(state, dragX, dragY);
    }

    /**
     * 释放鼠标：结束拖拽旋转状态，恢复光标。
     */
    public void mouseReleased() {
        inputHandler.mouseReleased(state);
    }

    /**
     * 强制恢复光标（供上层在屏幕关闭等场景调用）。
     */
    public void releaseCursorIfNeeded() {
        inputHandler.releaseCursorIfNeeded(state);
    }
}
