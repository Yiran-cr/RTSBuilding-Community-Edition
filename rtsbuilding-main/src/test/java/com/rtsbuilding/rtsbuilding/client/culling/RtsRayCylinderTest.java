package com.rtsbuilding.rtsbuilding.client.culling;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 射线圆柱几何判定单测：轴向/斜向、端点边界、包围盒覆盖。
 */
class RtsRayCylinderTest {

    /** 沿 +X 轴、原点出发的圆柱：距离 10、半径 2。 */
    private static final Vec3 ORIGIN = new Vec3(0, 0, 0);
    private static final Vec3 DIR_X = new Vec3(1, 0, 0);

    @Test
    void axisContainsInside() {
        assertTrue(RtsRayCylinder.contains(ORIGIN, DIR_X, 10.0D, 2.0D, new BlockPos(5, 0, 0)));
        // 方块(5,1,0) 中心(5.5,1.5,0.5) 距轴 sqrt(1.5^2+0.5^2)≈1.58 < 半径 2
        assertTrue(RtsRayCylinder.contains(ORIGIN, DIR_X, 10.0D, 2.0D, new BlockPos(5, 1, 0)));
        // 半径扩大到 3 时，(5,2,0) 中心距轴 ≈2.55 < 3
        assertTrue(RtsRayCylinder.contains(ORIGIN, DIR_X, 10.0D, 3.0D, new BlockPos(5, 2, 0)));
    }

    @Test
    void axisContainsOutside() {
        // 超出半径：方块(5,2,0) 中心距轴 ≈2.55 > 半径 2
        assertFalse(RtsRayCylinder.contains(ORIGIN, DIR_X, 10.0D, 2.0D, new BlockPos(5, 2, 0)));
        // 前方（t > +10）端面之外
        assertFalse(RtsRayCylinder.contains(ORIGIN, DIR_X, 10.0D, 2.0D, new BlockPos(11, 0, 0)));
    }

    @Test
    void bidirectionalCulling() {
        // 直线双向剔除：轴以相机位置为中点，后方（t < 0）同样在圆柱内
        assertTrue(RtsRayCylinder.contains(ORIGIN, DIR_X, 10.0D, 2.0D, new BlockPos(-5, 0, 0)));
        // 后方（t < -10）端面之外
        assertFalse(RtsRayCylinder.contains(ORIGIN, DIR_X, 10.0D, 2.0D, new BlockPos(-11, 0, 0)));
        // 后方斜向（-X 偏差）同样生效
        assertTrue(RtsRayCylinder.contains(ORIGIN, DIR_X, 10.0D, 2.0D, new BlockPos(-4, 1, 0)));
    }

    @Test
    void verticalDirection() {
        Vec3 dirUp = new Vec3(0, 1, 0);
        assertTrue(RtsRayCylinder.contains(ORIGIN, dirUp, 5.0D, 1.0D, new BlockPos(0, 3, 0)));
        assertFalse(RtsRayCylinder.contains(ORIGIN, dirUp, 5.0D, 1.0D, new BlockPos(1, 3, 0)));
        assertFalse(RtsRayCylinder.contains(ORIGIN, dirUp, 5.0D, 1.0D, new BlockPos(0, 6, 0)));
        // 双向：相机下方同样剔除
        assertTrue(RtsRayCylinder.contains(ORIGIN, dirUp, 5.0D, 1.0D, new BlockPos(0, -3, 0)));
        assertFalse(RtsRayCylinder.contains(ORIGIN, dirUp, 5.0D, 1.0D, new BlockPos(0, -6, 0)));
    }

    @Test
    void diagonalDirectionUsesDistanceAlongAxis() {
        // 45° 斜向上方向：轴上 (3,3,0) 在圆柱内，垂直偏移落到圆外
        Vec3 dir = new Vec3(1, 1, 0).normalize();
        BlockPos axisPos = new BlockPos(3, 3, 0);
        assertTrue(RtsRayCylinder.contains(ORIGIN, dir, 10.0D, 2.0D, axisPos));
        // 距轴 3 格（垂直距离 > 半径 2）→ 圆外
        BlockPos off = new BlockPos(3, 0, 0);
        assertFalse(RtsRayCylinder.contains(ORIGIN, dir, 10.0D, 2.0D, off));
    }

    @Test
    void boundsCoverCylinder() {
        AABB bounds = RtsRayCylinder.axisAlignedBounds(ORIGIN, DIR_X, 10.0D, 2.0D);
        assertNotNull(bounds);
        // 双向圆柱：轴段 [-10, +10]，半径 2 + 安全边缘 1 → 各方向外扩 3
        assertEquals(-13.0D, bounds.minX, 1.0E-9D); // 后端 -10 - 3
        assertEquals(13.0D, bounds.maxX, 1.0E-9D); // 前端 +10 + 3
        assertEquals(-3.0D, bounds.minY, 1.0E-9D);
        assertEquals(3.0D, bounds.maxY, 1.0E-9D);
        assertEquals(-3.0D, bounds.minZ, 1.0E-9D);
        assertEquals(3.0D, bounds.maxZ, 1.0E-9D);
        // 圆柱内方块必须全部落在包围盒内（含后方）
        assertTrue(bounds.contains(Vec3.atCenterOf(new BlockPos(5, 0, 0))));
        assertTrue(bounds.contains(Vec3.atCenterOf(new BlockPos(-5, 0, 0))));
    }

    @Test
    void invalidArguments() {
        assertNull(RtsRayCylinder.axisAlignedBounds(null, DIR_X, 10.0D, 2.0D));
        assertNull(RtsRayCylinder.axisAlignedBounds(ORIGIN, DIR_X, 0.0D, 2.0D));
        assertNull(RtsRayCylinder.axisAlignedBounds(ORIGIN, DIR_X, 10.0D, -1.0D));
        assertFalse(RtsRayCylinder.contains(ORIGIN, null, 10.0D, 2.0D, new BlockPos(5, 0, 0)));
        assertFalse(RtsRayCylinder.contains(ORIGIN, DIR_X, 10.0D, 2.0D, null));
    }

    @Test
    void subBlockShiftDoesNotTriggerRebuild() {
        // 同一包围盒：不移动
        AABB a = new AABB(0, -3, -3, 13, 3, 3);
        assertFalse(RtsRayCylinderCullingState.movedBeyondThreshold(a, a));
        // 亚格位移（相机插值/微调）：任何方向端面差 < 1 格，不触发重建
        AABB micro = new AABB(0.4, -3, -3, 13.4, 3, 3);
        assertFalse(RtsRayCylinderCullingState.movedBeyondThreshold(a, micro));
    }

    @Test
    void blockShiftTriggersRebuild() {
        AABB base = new AABB(0, -3, -3, 13, 3, 3);
        // 任一方向端面差达 1 格 → 触发重建
        AABB one = new AABB(1, -3, -3, 14, 3, 3);
        assertTrue(RtsRayCylinderCullingState.movedBeyondThreshold(base, one));
        // 仅 max 端面移动 1 格同样触发
        AABB onlyMax = new AABB(0, -3, -3, 14, 3, 3);
        assertTrue(RtsRayCylinderCullingState.movedBeyondThreshold(base, onlyMax));
        // null 任一侧视为移动
        assertTrue(RtsRayCylinderCullingState.movedBeyondThreshold(null, base));
        assertTrue(RtsRayCylinderCullingState.movedBeyondThreshold(base, null));
    }
}