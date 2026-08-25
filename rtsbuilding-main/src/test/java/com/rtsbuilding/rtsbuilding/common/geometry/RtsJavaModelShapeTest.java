package com.rtsbuilding.rtsbuilding.common.geometry;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@link RtsJavaModelShape}：纯几何盒数据 → 碰撞/选择箱的转换（像素 → 格坐标、OR 合并）。
 */
class RtsJavaModelShapeTest {

    @Test
    void emptyShapeHasNoContent() {
        VoxelShape shape = RtsJavaModelShape.builder().buildShape();
        assertTrue(shape.isEmpty(), "空形状不应包含任何格子");
    }

    @Test
    void singleBoxConvertsPixelsToBlockCoordinates() {
        // 单个 4×4×4 像素盒，从 (8, 8, 8) 到 (12, 12, 12)。
        RtsJavaModelShape shapeDef = RtsJavaModelShape.builder()
                .addBox(8, 8, 8, 4, 4, 4);
        VoxelShape shape = shapeDef.buildShape();

        assertFalse(shape.isEmpty(), "单盒形状不应为空");
        // VoxelShape 的 AABB 是格坐标（0..1），像素 /16。
        AABB bounds = shape.bounds();
        assertEquals(0.5, bounds.minX, 1e-6);
        assertEquals(0.5, bounds.minY, 1e-6);
        assertEquals(0.5, bounds.minZ, 1e-6);
        assertEquals(0.75, bounds.maxX, 1e-6);
        assertEquals(0.75, bounds.maxY, 1e-6);
        assertEquals(0.75, bounds.maxZ, 1e-6);
    }

    @Test
    void multiBoxShapeIsOrCombined() {
        // 两个分离的盒：底部 (0,0,0,4,4,4) 与顶部 (8,8,8,4,4,4)。
        RtsJavaModelShape shapeDef = RtsJavaModelShape.builder()
                .addBox(0, 0, 0, 4, 4, 4)
                .addBox(8, 8, 8, 4, 4, 4);
        VoxelShape shape = shapeDef.buildShape();

        assertFalse(shape.isEmpty(), "多盒合并形状不应为空");
        // OR 合并后的包围盒覆盖两个盒的总范围。
        AABB bounds = shape.bounds();
        assertEquals(0.0, bounds.minX, 1e-6);
        assertEquals(0.0, bounds.minY, 1e-6);
        assertEquals(0.0, bounds.minZ, 1e-6);
        assertEquals(0.75, bounds.maxX, 1e-6);
        assertEquals(0.75, bounds.maxY, 1e-6);
        assertEquals(0.75, bounds.maxZ, 1e-6);
    }

    @Test
    void crossBlockBoxAllowsYBeyondOneBlock() {
        // 跨格盒：y 从 0 到 80 像素（0..5 格），模拟风机塔身。
        RtsJavaModelShape shapeDef = RtsJavaModelShape.builder()
                .addBox(6, 0, 6, 4, 80, 4);
        VoxelShape shape = shapeDef.buildShape();

        AABB bounds = shape.bounds();
        assertEquals(6.0 / 16.0, bounds.minX, 1e-6);
        assertEquals(0.0, bounds.minY, 1e-6);
        assertEquals(6.0 / 16.0, bounds.minZ, 1e-6);
        assertEquals(10.0 / 16.0, bounds.maxX, 1e-6);
        assertEquals(80.0 / 16.0, bounds.maxY, 1e-6); // 5 格
        assertEquals(10.0 / 16.0, bounds.maxZ, 1e-6);
    }

    @Test
    void boxesViewIsImmutableSnapshot() {
        RtsJavaModelShape shapeDef = RtsJavaModelShape.builder()
                .addBox(0, 0, 0, 4, 4, 4);
        assertEquals(1, shapeDef.boxes().size());
        assertFalse(shapeDef.boxes().isEmpty());
        // 追加不应改变已有视图。
        shapeDef.addBox(8, 8, 8, 4, 4, 4);
        assertEquals(2, shapeDef.boxes().size());
    }
}
