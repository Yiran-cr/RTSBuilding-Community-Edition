package com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar;

import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * 轨道球输入处理器 —— 负责轴段点击跳转、左键拖拽旋转与拖拽期间的光标隐藏/恢复。
 */
final class AxisGizmoInputHandler {

    private final TrackballProjection projection;

    AxisGizmoInputHandler(TrackballProjection projection) {
        this.projection = projection;
    }

    /**
     * 鼠标点击处理：左键命中轴段则切换视角。
     * <p>翻转逻辑：若相机已正对命中的按钮方向（该按钮在前方正中），
     * 则点击跳转到其反方向（去观察被遮挡的背面按钮），否则跳转到该按钮方向。</p>
     * <p>仅左键触发轴端跳转：右键/中键不跳转（右键在框选等操作中另有语义，
     * 本层会消费掉 gizmo 区域内的点击避免误触发框选）。</p>
     *
     * @return 是否命中并消费了本次点击
     */
    boolean mouseClicked(AxisGizmoState state, double mouseX, double mouseY, int button) {
        projection.updateViewBasis();

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int segment = AxisGizmoRenderer.hitAxis(projection, state, (int) mouseX, (int) mouseY);
            if (segment >= 0) {
                int axis = segment % 3;
                boolean negative = segment >= 3;
                // 判断相机是否正对该按钮方向：是则翻转（跳转到反方向）
                double sign = negative ? -1.0D : 1.0D;
                double[] view = projection.computeViewVector();
                double dot = TrackballProjection.worldAxis(axis, 0) * sign * view[0]
                        + TrackballProjection.worldAxis(axis, 1) * sign * view[1]
                        + TrackballProjection.worldAxis(axis, 2) * sign * view[2];
                boolean flip = dot > TrackballProjection.FACING_FLIP_THRESHOLD;

                CameraModule cam = cameraModule();
                if (cam != null) {
                    cam.snapViewToAxis(axis, negative != flip);
                }
                return true;
            }
        }

        // 仅左键可在圆形背景范围内开始拖拽旋转（点击线段不触发跳转，但拖拽无限制）
        if (button == 0 && distance(mouseX, mouseY, state.getCx(), state.getCy())
                <= TrackballProjection.RADIUS) {
            state.setDragging(true);
            hideCursorAndRecordStart(state);
            return true;
        }
        return false;
    }

    /**
     * 拖拽处理：处于拖拽状态时把像素位移交给相机模块旋转视角。
     * <p>拖拽期间光标处于 {@code GLFW_CURSOR_DISABLED}（锁定+隐藏）：GLFW 只上报相对位移
     * （虚拟位置），不会产生绝对位置跳变，因此自读差分恒等于用户真实拖动位移，
     * 无合成事件干扰、无抖动、无轴向偏移；同时光标锁定中心可实现无限旋转。</p>
     *
     * @return 是否消费了本次拖拽
     */
    boolean mouseDragged(AxisGizmoState state, double dragX, double dragY) {
        if (!state.isDragging()) return false;
        rotateByCursorDelta(state);
        return true;
    }

    /** 基于光标绝对位置计算旋转增量（DISABLED 模式下为相对位移）。 */
    private void rotateByCursorDelta(AxisGizmoState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        long win = mc.getWindow().getWindow();
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(win, x, y);
        double dx = x[0] - state.getLastDragCursorX();
        double dy = y[0] - state.getLastDragCursorY();
        // 灵敏度缩放与亚像素死区：降低灵敏度、过滤噪声，旋转更丝滑
        final double SENSITIVITY = 0.5;
        final double DEADZONE = 0.2;
        if (Math.abs(dx) < DEADZONE) dx = 0;
        if (Math.abs(dy) < DEADZONE) dy = 0;
        CameraModule cam = cameraModule();
        if (cam != null) {
            cam.queueRotateDrag(dx * SENSITIVITY, dy * SENSITIVITY);
        }
        state.setLastDragCursorX(x[0]);
        state.setLastDragCursorY(y[0]);
    }

    /**
     * 释放鼠标：结束拖拽旋转状态，恢复光标并回到进入拖拽时的位置。
     */
    void mouseReleased(AxisGizmoState state) {
        state.setDragging(false);
        restoreCursor(state);
    }

    /**
     * 强制恢复光标（供上层在屏幕关闭等场景调用，避免光标残留隐藏）。
     */
    void releaseCursorIfNeeded(AxisGizmoState state) {
        state.setDragging(false);
        restoreCursor(state);
    }

    /**
     * 隐藏并锁定系统光标（GLFW_CURSOR_DISABLED，只上报相对位移），记录起始物理像素位置
     * （用于释放时回到该处，也作为拖拽增量计算基点）。
     */
    private void hideCursorAndRecordStart(AxisGizmoState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        long win = mc.getWindow().getWindow();
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(win, x, y);
        state.setDragStartCursorX(x[0]);
        state.setDragStartCursorY(y[0]);
        // DISABLED 模式：光标锁定+隐藏，glfwGetCursorPos 返回相对位移（虚拟位置），
        // 不产生绝对位置跳变 → 无限旋转且无合成事件干扰
        GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        state.setCursorHidden(true);
        // 切换到 DISABLED 后读取一次虚拟位置作为差分基点
        GLFW.glfwGetCursorPos(win, x, y);
        state.setLastDragCursorX(x[0]);
        state.setLastDragCursorY(y[0]);
    }

    /**
     * 恢复系统光标并移动回进入拖拽时的位置。
     */
    private void restoreCursor(AxisGizmoState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || !state.isCursorHidden()) return;
        long win = mc.getWindow().getWindow();
        GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        GLFW.glfwSetCursorPos(win, state.getDragStartCursorX(), state.getDragStartCursorY());
        state.setCursorHidden(false);
    }

    private static double distance(double x0, double y0, double x1, double y1) {
        double dx = x0 - x1;
        double dy = y0 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static CameraModule cameraModule() {
        com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel kernel =
                com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel.get();
        return kernel == null ? null : kernel.module(CameraModule.class);
    }
}
