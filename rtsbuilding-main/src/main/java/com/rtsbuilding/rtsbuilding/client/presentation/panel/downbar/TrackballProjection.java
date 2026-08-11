package com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar;

import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraState;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;

/**
 * 轨道球 3D 投影数学 —— 负责视图基底构建、世界轴投影与透视缩放。
 * <p>
 * 将世界坐标三轴依据相机 yaw/pitch 正交投影到屏幕，并统一提供
 * 渲染与命中两处共用的屏幕坐标计算，保证位置永远一致。
 * </p>
 */
public final class TrackballProjection {

    /** 球体/背景圆半径（所有元素均约束在此圆内） */
    public static final int RADIUS = 32;

    /** 轴端字母圆点半径 */
    public static final int LABEL_DOT_RADIUS = 6;

    /** 透视缩放：深度为最远（depth=-1）时轴段/圆点的最小缩放系数 */
    private static final double PERSPECTIVE_MIN_SCALE = 0.80D;

    /** 透视缩放：深度为最近（depth=+1）时轴段/圆点的最大缩放系数 */
    private static final double PERSPECTIVE_MAX_SCALE = 1.15D;

    /** 面板总宽度（自动按背景圆半径扩展） */
    public static final int WIDTH = (RADIUS + 4) * 2;

    /** 面板总高度（自动按背景圆半径扩展） */
    public static final int HEIGHT = (RADIUS + 4) * 2;

    /**
     * 轴段长度（从球心到轴端圆点中心的距离，自动适配）。
     * <p>考虑透视最大放大（×PERSPECTIVE_MAX_SCALE）后轴段末端与圆点仍落在背景圆内：
     * AXIS_LEN * MAX + LABEL_DOT_RADIUS * MAX ≤ RADIUS。</p>
     */
    public static final int AXIS_LEN =
            (int) Math.floor(RADIUS / PERSPECTIVE_MAX_SCALE) - LABEL_DOT_RADIUS;

    /** 中心小球半径 */
    public static final int CENTER_RADIUS = 7;

    /** 判定"相机朝向对应轴"的点积阈值 */
    public static final double ACTIVE_AXIS_THRESHOLD = 0.5D;

    /** 判定"相机正对某按钮方向"的点积阈值（超过则点击翻转） */
    public static final double FACING_FLIP_THRESHOLD = 0.9D;

    /** 世界坐标三轴方向（基方向，正负由符号参数区分） */
    private static final double[][] WORLD_AXES = {
            { 1.0D, 0.0D, 0.0D },
            { 0.0D, 1.0D, 0.0D },
            { 0.0D, 0.0D, 1.0D }
    };

    /** 当前视角的视图基底（right / up / forward），由相机姿态计算 */
    private double rx, ry, rz, ux, uy, uz, fx, fy, fz;

    /**
     * 根据当前相机姿态更新视图基底（right / up / forward 正交基）。
     * <p>默认视角取 yaw=0、pitch=-30（略俯视），相机不可用或未激活时回退该值。</p>
     */
    public void updateViewBasis() {
        CameraModule cam = cameraModule();
        float yaw = 0f, pitch = -30f;
        if (cam != null) {
            CameraState st = cam.getState();
            if (st != null && st.isEnabled() && st.isLocalReady()) {
                yaw = st.getYaw();
                pitch = st.getPitch();
            }
        }
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosYaw = Math.cos(yawRad), sinYaw = Math.sin(yawRad);
        double cosPitch = Math.cos(pitchRad), sinPitch = Math.sin(pitchRad);

        // right（屏幕 x 正方向）：取水平单位向量并纠正手系方向。
        // MC 相机中 yaw=0 朝向 +Z（南），此时右手边是 -X，
        // 因此 right = (-cosYaw, 0, -sinYaw)，保证正负轴与视角一致。
        rx = -cosYaw; ry = 0.0D; rz = -sinYaw;
        // forward（视线方向，与 FreeCameraMode 的相机朝向一致）
        fx = -sinYaw * cosPitch; fy = -sinPitch; fz = cosYaw * cosPitch;
        // up = right × forward（世界向上方向的投影）
        ux = ry * fz - rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy - ry * fx;
    }

    /**
     * 将世界坐标轴（带方向符号）投影到屏幕。
     * <p>GUI 坐标系 y 轴向下，故屏幕 Y 取负的 up 分量。</p>
     *
     * @return {屏幕X偏移, 屏幕Y偏移, 深度}，深度 &gt; 0 表示朝向观察者
     */
    public double[] project(int axis, int sign) {
        double ax = WORLD_AXES[axis][0] * sign;
        double ay = WORLD_AXES[axis][1] * sign;
        double az = WORLD_AXES[axis][2] * sign;
        double sx = ax * rx + ay * ry + az * rz;
        double sy = -(ax * ux + ay * uy + az * uz);
        double depth = ax * fx + ay * fy + az * fz;
        return new double[] { sx, sy, depth };
    }

    /**
     * 计算轴段球心屏幕坐标（渲染/命中统一使用）。
     * <p>球心固定在球面半径 AXIS_LEN 处（无缩放，避免端点伸缩跳变），
     * 缩放仅作用于球/文字大小与命中半径。坐标保持浮点，避免低分辨率下取整阶梯。</p>
     *
     * @param cx   球心屏幕 X
     * @param cy   球心屏幕 Y
     * @param axis 轴索引 0..2
     * @param sign 方向符号 +1/-1
     * @return 屏幕坐标与透视缩放信息
     */
    public SegmentScreen projectScreen(int cx, int cy, int axis, int sign) {
        double[] p = project(axis, sign);
        double x = cx + p[0] * AXIS_LEN;
        double y = cy + p[1] * AXIS_LEN;
        return new SegmentScreen(x, y, p[2], perspectiveScale(p[2]));
    }

    /**
     * 计算相机的观察向量（单位向量）。
     * <p>环绕模式下为"相机位置 − 目标"（相机在哪一侧看向哪一侧），
     * 自由视角为视线方向 forward。</p>
     */
    public double[] computeViewVector() {
        double vx, vy, vz;
        CameraModule cam = cameraModule();
        if (cam != null) {
            CameraState st = cam.getState();
            if (st != null && st.isEnabled() && st.isLocalReady()) {
                if (st.isOrbitMode() || st.isPlayerOrbitMode()) {
                    vx = st.getLocalX() - st.getOrbitTargetX();
                    vy = st.getLocalY() - st.getOrbitTargetY();
                    vz = st.getLocalZ() - st.getOrbitTargetZ();
                } else {
                    vx = fx;
                    vy = fy;
                    vz = fz;
                }
                double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (len > 1.0E-4D) {
                    vx /= len;
                    vy /= len;
                    vz /= len;
                }
                return new double[] { vx, vy, vz };
            }
        }
        return new double[] { fx, fy, fz };
    }

    /**
     * 按深度计算透视缩放系数：朝向观察者（depth 接近 +1）放大，背向（接近 -1）缩小。
     */
    public static double perspectiveScale(double depth) {
        double t = (depth + 1.0D) * 0.5D;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return PERSPECTIVE_MIN_SCALE + (PERSPECTIVE_MAX_SCALE - PERSPECTIVE_MIN_SCALE) * t;
    }

    /**
     * 获取世界轴基方向的分量。
     *
     * @param axis     轴索引 0..2
     * @param component 0=X、1=Y、2=Z
     */
    public static double worldAxis(int axis, int component) {
        return WORLD_AXES[axis][component];
    }

    /**
     * 计算相机朝向对应的世界轴段掩码（区分正负）。
     * <p>环绕模式下以"相机相对目标的位置方向"为观察向量（相机在哪一侧高亮哪一侧），
     * 自由视角以视线方向 forward 为观察向量；每个轴段方向与该向量点积 ≥ 阈值才高亮。</p>
     *
     * @return 位掩码：bit0..2 正轴 X/Y/Z，bit3..5 负轴 -X/-Y/-Z
     */
    public int computeActiveSegmentMask() {
        double[] v = computeViewVector();
        int mask = 0;
        for (int i = 0; i < 6; i++) {
            int axis = i % 3;
            int sign = i < 3 ? 1 : -1;
            double ax = worldAxis(axis, 0) * sign;
            double ay = worldAxis(axis, 1) * sign;
            double az = worldAxis(axis, 2) * sign;
            double dot = ax * v[0] + ay * v[1] + az * v[2];
            if (dot >= ACTIVE_AXIS_THRESHOLD) {
                mask |= (1 << i);
            }
        }
        return mask;
    }

    /**
     * 从客户端内核获取相机模块，可能为 null（模块未注册）。
     */
    private static CameraModule cameraModule() {
        RtsClientKernel kernel = RtsClientKernel.get();
        return kernel == null ? null : kernel.module(CameraModule.class);
    }

    /**
     * 轴段球心的屏幕投影结果（浮点坐标，避免取整阶梯）。
     *
     * @param x     球心屏幕 X
     * @param y     球心屏幕 Y
     * @param depth 投影深度（&gt;0 朝向观察者）
     * @param scale 透视缩放系数
     */
    public record SegmentScreen(double x, double y, double depth, double scale) {}
}
