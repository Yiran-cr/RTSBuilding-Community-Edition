package com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar;

import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;

/**
 * 轨道球的可变状态 —— 位置、拖拽状态、光标状态与六段轴的悬停动画。
 * <p>与渲染（{@link AxisGizmoRenderer}）和输入（{@link AxisGizmoInputHandler}）共享，
 * 由 {@link AxisViewGizmo} 统一持有。</p>
 */
public final class AxisGizmoState {

    /** 面板左上角 X（RTS 虚拟坐标） */
    private int x;

    /** 面板左上角 Y（RTS 虚拟坐标） */
    private int y;

    /** 球心 X */
    private int cx;

    /** 球心 Y */
    private int cy;

    /** 是否正处于拖拽旋转状态 */
    private boolean dragging;

    /** 拖拽时光标是否已被隐藏 */
    private boolean cursorHidden;

    /** 进入拖拽时系统光标的物理像素 X（用于释放时恢复位置） */
    private double dragStartCursorX;

    /** 进入拖拽时系统光标的物理像素 Y（用于释放时恢复位置） */
    private double dragStartCursorY;

    /** 六个轴段（正/负各三）的悬停动画 */
    private final AnimFloat[] hoverAnims = {
            AnimFloat.hover(), AnimFloat.hover(), AnimFloat.hover(),
            AnimFloat.hover(), AnimFloat.hover(), AnimFloat.hover()
    };

    /**
     * 设置面板位置并更新球心。
     */
    public void setBounds(int x, int y) {
        this.x = x;
        this.y = y;
        this.cx = x + TrackballProjection.WIDTH / 2;
        this.cy = y + TrackballProjection.HEIGHT / 2;
    }

    /**
     * 命中检测：坐标是否落在面板矩形内。
     */
    public boolean contains(int px, int py) {
        return px >= x && px < x + TrackballProjection.WIDTH
                && py >= y && py < y + TrackballProjection.HEIGHT;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getCx() { return cx; }
    public int getCy() { return cy; }

    public boolean isDragging() { return dragging; }
    public void setDragging(boolean dragging) { this.dragging = dragging; }

    public boolean isCursorHidden() { return cursorHidden; }
    public void setCursorHidden(boolean cursorHidden) { this.cursorHidden = cursorHidden; }

    public double getDragStartCursorX() { return dragStartCursorX; }
    public void setDragStartCursorX(double v) { this.dragStartCursorX = v; }
    public double getDragStartCursorY() { return dragStartCursorY; }
    public void setDragStartCursorY(double v) { this.dragStartCursorY = v; }

    public AnimFloat[] getHoverAnims() { return hoverAnims; }
}
