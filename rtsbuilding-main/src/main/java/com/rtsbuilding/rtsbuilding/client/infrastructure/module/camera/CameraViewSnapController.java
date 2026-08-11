package com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera;

import net.minecraft.util.Mth;

/**
 * 相机视角跳转控制器 —— 处理点击 XYZ 轴调节器后的视角切换与平滑旋转动画。
 * <p>
 * 三种相机模式行为不同：
 * <ul>
 *   <li>自由视角：以相机自身为原点，仅平滑旋转朝向，位置不变；</li>
 *   <li>玩家环绕：以玩家实体为原点，改变相机位置与朝向；</li>
 *   <li>方块环绕：以选中方块为原点，改变相机位置与朝向。</li>
 * </ul>
 * 动画由 {@link #advance(CameraState)} 逐帧推进，用户手动操作相机时调用
 * {@link #cancel()} 取消。
 * </p>
 */
public final class CameraViewSnapController {

    /** 自由视角朝向跳转动画时长（毫秒） */
    private static final long FREE_SNAP_ANIM_MS = 450L;

    /** 环绕跳转动画时长（毫秒） */
    private static final long ORBIT_SNAP_ANIM_MS = 450L;

    /** 是否正在播放自由视角朝向跳转动画 */
    private boolean freeSnapAnimating;

    /** 动画起始偏航角（度） */
    private float freeSnapFromYaw;

    /** 动画起始俯仰角（度） */
    private float freeSnapFromPitch;

    /** 动画目标偏航角（度） */
    private float freeSnapTargetYaw;

    /** 动画目标俯仰角（度） */
    private float freeSnapTargetPitch;

    /** 动画开始时间戳（毫秒） */
    private long freeSnapStartMs;

    /** 是否正在播放环绕轴向跳转动画 */
    private boolean orbitSnapAnimating;

    /** 动画起始水平角（弧度） */
    private double orbitSnapFromAngle;

    /** 动画起始俯仰角（弧度） */
    private double orbitSnapFromPitch;

    /** 动画目标水平角（弧度） */
    private double orbitSnapTargetAngle;

    /** 动画目标俯仰角（弧度） */
    private double orbitSnapTargetPitch;

    /** 动画开始时间戳（毫秒） */
    private long orbitSnapStartMs;

    /**
     * 点击 XYZ 轴视角调节器时切换视角。
     *
     * @param axis     轴索引：0=X、1=Y、2=Z
     * @param negative true 表示从轴负方向观察
     */
    public void snapViewToAxis(CameraState state, int axis, boolean negative) {
        if (!state.enabled || !state.localReady) return;

        if (state.orbitMode || state.playerOrbitMode) {
            snapOrbitToAxis(state, axis, negative);
        } else {
            snapFreeViewToAxis(state, axis, negative);
        }
    }

    /**
     * 自由视角：以相机自身为原点，平滑旋转朝向到指定轴向，位置保持不变。
     */
    private void snapFreeViewToAxis(CameraState state, int axis, boolean negative) {
        float yaw, pitch;
        switch (axis) {
            case 0 -> { yaw = negative ? 90f : -90f; pitch = 0f; }   // ±X
            case 1 -> { yaw = 0f; pitch = negative ? 90f : -90f; }   // ±Y
            default -> { yaw = negative ? 180f : 0f; pitch = 0f; }   // ±Z
        }
        this.freeSnapFromYaw = state.localYaw;
        this.freeSnapFromPitch = state.localPitch;
        this.freeSnapTargetYaw = yaw;
        this.freeSnapTargetPitch = pitch;
        this.freeSnapStartMs = net.minecraft.Util.getMillis();
        this.freeSnapAnimating = true;
    }

    /**
     * 环绕模式（玩家环绕以玩家实体为原点、方块环绕以选中方块为原点）：
     * 将相机平滑旋转到目标轴向。位置由 orbitAngle/orbitPitch 决定，
     * 这里启动角度插值动画，由 {@link #advance(CameraState)} 逐帧推进。
     */
    private void snapOrbitToAxis(CameraState state, int axis, boolean negative) {
        // 目标轴方向的单位向量
        double nx = 0.0D, ny = 0.0D, nz = 0.0D;
        double sign = negative ? -1.0D : 1.0D;
        switch (axis) {
            case 0 -> nx = sign;
            case 1 -> ny = sign;
            default -> nz = sign;
        }

        // 目标水平角/俯仰角（相机位于该轴向时对应的环绕角度）
        double targetAngle = Math.atan2(nx, nz);
        double targetPitch = Math.asin(Math.max(-1.0D, Math.min(1.0D, ny)));

        this.orbitSnapFromAngle = state.orbitAngle;
        this.orbitSnapFromPitch = state.orbitPitch;
        this.orbitSnapTargetAngle = targetAngle;
        this.orbitSnapTargetPitch = targetPitch;
        this.orbitSnapStartMs = net.minecraft.Util.getMillis();
        this.orbitSnapAnimating = true;
    }

    /**
     * 逐帧推进视角跳转动画（由相机模块在渲染帧调用）。
     */
    public void advance(CameraState state) {
        boolean orbit = state.orbitMode || state.playerOrbitMode;
        // 模式切换保护：动画与当前模式不匹配时立即取消，避免切回原模式后残留旧动画继续插值
        if (this.orbitSnapAnimating && !orbit) {
            this.orbitSnapAnimating = false;
        }
        if (this.freeSnapAnimating && orbit) {
            this.freeSnapAnimating = false;
        }
        if (this.orbitSnapAnimating) {
            advanceOrbitSnapAnimation(state);
        }
        if (this.freeSnapAnimating) {
            advanceFreeSnapAnimation(state);
        }
    }

    /**
     * 取消正在进行的视角跳转动画（用户手动操作相机时调用）。
     */
    public void cancel() {
        this.orbitSnapAnimating = false;
        this.freeSnapAnimating = false;
    }

    /**
     * 是否正在播放任何视角跳转动画。
     */
    public boolean isAnimating() {
        return this.orbitSnapAnimating || this.freeSnapAnimating;
    }

    /**
     * 逐帧推进自由视角朝向跳转动画，将 yaw/pitch 插值写入相机状态。
     * <p>偏航角走最短弧，俯仰角线性插值，使用缓出曲线让旋转更自然。</p>
     */
    private void advanceFreeSnapAnimation(CameraState state) {
        long elapsed = net.minecraft.Util.getMillis() - this.freeSnapStartMs;
        double t = Math.min(1.0D, elapsed / (double) FREE_SNAP_ANIM_MS);
        double eased = 1.0D - Math.pow(1.0D - t, 3);

        state.localYaw = (float) lerpAngleShortest(
                Math.toRadians(this.freeSnapFromYaw), Math.toRadians(this.freeSnapTargetYaw), eased);
        state.localYaw = Mth.wrapDegrees((float) Math.toDegrees(state.localYaw));
        state.localPitch = this.freeSnapFromPitch
                + (this.freeSnapTargetPitch - this.freeSnapFromPitch) * (float) eased;

        if (t >= 1.0D) {
            this.freeSnapAnimating = false;
        }
    }

    /**
     * 逐帧推进环绕轴向跳转动画，将角度插值写入相机状态。
     * <p>水平角走最短弧，俯仰角线性插值，使用缓出曲线让旋转更自然。</p>
     */
    private void advanceOrbitSnapAnimation(CameraState state) {
        long elapsed = net.minecraft.Util.getMillis() - this.orbitSnapStartMs;
        double t = Math.min(1.0D, elapsed / (double) ORBIT_SNAP_ANIM_MS);
        double eased = 1.0D - Math.pow(1.0D - t, 3);

        state.orbitAngle = lerpAngleShortest(this.orbitSnapFromAngle, this.orbitSnapTargetAngle, eased);
        state.orbitPitch = this.orbitSnapFromPitch
                + (this.orbitSnapTargetPitch - this.orbitSnapFromPitch) * eased;

        if (t >= 1.0D) {
            this.orbitSnapAnimating = false;
        }
    }

    /**
     * 角度插值，结果保持在与 from 相差不超过半圈的最短路径上。
     */
    private static double lerpAngleShortest(double from, double to, double t) {
        double delta = Math.atan2(Math.sin(to - from), Math.cos(to - from));
        return from + delta * t;
    }
}
