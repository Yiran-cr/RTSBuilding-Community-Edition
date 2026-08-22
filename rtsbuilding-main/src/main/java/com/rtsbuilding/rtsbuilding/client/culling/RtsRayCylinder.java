package com.rtsbuilding.rtsbuilding.client.culling;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 直线圆柱的纯几何计算（无 MC 状态依赖，可单测）。
 *
 * <p>圆柱 = 轴线段「相机位置 − 方向 × 距离 → 相机位置 + 方向 × 距离」向外膨胀半径
 * {@code radius} 的圆柱体（含端点面）。即圆柱以相机位置为中心、沿摄像机朝向直线
 * <b>双向</b>延伸（前方与后方各 {@code distance} 格）。方块以自身中心点参与判定：
 * 中心点在圆柱体内即认为该方块被剔除。</p>
 *
 * <p>与范围剔除盒（参考实现 {@code RtsCullingBox}）一样只描述几何形状，
 * 不持有 UI、渲染刷新或输入状态，便于复用同一份判定逻辑。</p>
 */
public final class RtsRayCylinder {

    /** 方块中心偏移半格。 */
    private static final double HALF_BLOCK = 0.5D;

    private RtsRayCylinder() {
    }

    /**
     * 判断方块中心点是否落在直线圆柱体内。
     *
     * @param origin    圆柱轴中点（相机位置，圆柱向前后各延伸 {@code distance}）
     * @param direction 轴线单位方向（已归一化，摄像机朝向）
     * @param distance  单侧轴线长度（向前/向后各剔除该距离）
     * @param radius    圆柱半径
     * @param pos       待判定方块位置
     * @return 中心点在圆柱体内返回 true
     */
    public static boolean contains(Vec3 origin, Vec3 direction, double distance, double radius, BlockPos pos) {
        if (origin == null || direction == null || pos == null || distance <= 0.0D || radius <= 0.0D) {
            return false;
        }
        double px = pos.getX() + HALF_BLOCK;
        double py = pos.getY() + HALF_BLOCK;
        double pz = pos.getZ() + HALF_BLOCK;
        // 方块中心到轴中点的向量沿轴线的投影长度（方向已归一化）
        double wx = px - origin.x;
        double wy = py - origin.y;
        double wz = pz - origin.z;
        double t = wx * direction.x + wy * direction.y + wz * direction.z;
        if (t < -distance || t > distance) {
            // 位于轴线段前后两个端面之外 → 不在圆柱体内
            return false;
        }
        // 轴向最近点 → 求垂直距离平方
        double cx = origin.x + direction.x * t;
        double cy = origin.y + direction.y * t;
        double cz = origin.z + direction.z * t;
        double dx = px - cx;
        double dy = py - cy;
        double dz = pz - cz;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /**
     * 计算圆柱的轴对齐包围盒（AABB），用于区块网格失效定位。
     *
     * <p>在几何边界外再膨胀 {@code radius + 1.0}：既覆盖整块方块（中心判定 + 半格），
     * 又为区块重建留出安全边缘。</p>
     *
     * @param origin    轴中点（相机位置）
     * @param direction 轴线单位方向
     * @param distance  单侧轴线长度（向前/向后各该距离）
     * @param radius    圆柱半径
     * @return 覆盖圆柱的 AABB（可能为 null，参数非法时）
     */
    public static AABB axisAlignedBounds(Vec3 origin, Vec3 direction, double distance, double radius) {
        if (origin == null || direction == null || distance <= 0.0D || radius <= 0.0D) {
            return null;
        }
        Vec3 forward = origin.add(direction.scale(distance));
        Vec3 backward = origin.subtract(direction.scale(distance));
        double m = radius + 1.0D;
        return new AABB(
                Math.min(backward.x, forward.x) - m,
                Math.min(backward.y, forward.y) - m,
                Math.min(backward.z, forward.z) - m,
                Math.max(backward.x, forward.x) + m,
                Math.max(backward.y, forward.y) + m,
                Math.max(backward.z, forward.z) + m);
    }
}