package com.rtsbuilding.rtsbuilding.common.geometry;

import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Java 模型的纯几何盒数据（与加载器无关，服务端/客户端均可用）。
 * <p>
 * 用于「模型与碰撞同源」：方块的碰撞/选择箱（{@link #buildShape()}）与客户端渲染模型
 * （由 {@link com.rtsbuilding.rtsbuilding.client.model.JavaModelShapeUtil#toCubeListBuilder(RtsJavaModelShape)}
 * 生成 {@code CubeListBuilder}）共用同一份几何盒定义，保证二者永远一致，避免手写
 * {@code Shapes.or(Block.box(...))} 与 Java 模型脱节。
 * <p>
 * 坐标约定与 {@link com.rtsbuilding.rtsbuilding.common.geometry.RtsVoxelShapeUtils#box} 一致：
 * 盒用<b>方块像素坐标</b>（0–16，可跨格，如塔身 y 0..84），内部生成 {@link VoxelShape} 时
 * 统一除以 16 换算成格坐标。
 */
public final class RtsJavaModelShape {

    /**
     * 单个轴对齐盒（方块像素坐标）。
     *
     * @param x,y,z 盒最小角像素坐标
     * @param sizeX,sizeY,sizeZ 盒三轴尺寸（像素）
     */
    public record Box(float x, float y, float z, float sizeX, float sizeY, float sizeZ) {

        public float maxX() {
            return x + sizeX;
        }

        public float maxY() {
            return y + sizeY;
        }

        public float maxZ() {
            return z + sizeZ;
        }
    }

    private final List<Box> boxes = new ArrayList<>();

    private RtsJavaModelShape() {
    }

    /**
     * 创建新的形状构建器。
     */
    public static RtsJavaModelShape builder() {
        return new RtsJavaModelShape();
    }

    /**
     * 追加一个轴对齐盒（方块像素坐标，0–16 可跨格）。
     *
     * @return 本构建器，支持链式调用。
     */
    public RtsJavaModelShape addBox(float x, float y, float z, float sizeX, float sizeY, float sizeZ) {
        boxes.add(new Box(x, y, z, sizeX, sizeY, sizeZ));
        return this;
    }

    /**
     * @return 已登记的盒列表（不可变视图）。
     */
    public List<Box> boxes() {
        return Collections.unmodifiableList(boxes);
    }

    /**
     * 合并所有盒为方块碰撞/选择箱。
     *
     * @return 各盒 OR 合并后的 {@link VoxelShape}；无盒时返回空形状。
     */
    public VoxelShape buildShape() {
        if (boxes.isEmpty()) {
            return net.minecraft.world.phys.shapes.Shapes.empty();
        }
        List<VoxelShape> shapes = new ArrayList<>(boxes.size());
        for (Box box : boxes) {
            shapes.add(RtsVoxelShapeUtils.box(box.x(), box.y(), box.z(), box.maxX(), box.maxY(), box.maxZ()));
        }
        return RtsVoxelShapeUtils.combine(shapes);
    }
}
