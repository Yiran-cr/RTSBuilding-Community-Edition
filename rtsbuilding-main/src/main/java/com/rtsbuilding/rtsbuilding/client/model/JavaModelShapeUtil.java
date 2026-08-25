package com.rtsbuilding.rtsbuilding.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.common.geometry.RtsJavaModelShape;
import com.rtsbuilding.rtsbuilding.common.geometry.RtsVoxelShapeUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Java 模型 ↔ 碰撞箱互转工具（client-only，依赖 {@link ModelPart} 等渲染类）。
 * <p>
 * 与 {@link RtsJavaModelShape}（纯几何数据）配合实现「模型与碰撞同源」：
 * <ul>
 *   <li><b>数据 → 模型</b>：{@link #toCubeListBuilder(RtsJavaModelShape)} 把方块像素盒列表
 *       转成渲染用的 {@link CubeListBuilder}，供 {@code LayerDefinition} 构建；</li>
 *   <li><b>模型 → 碰撞</b>：{@link #fromModelPart(ModelPart, Collection)} 遍历已烘焙的
 *       {@link ModelPart}，把每个 {@link ModelPart.Cube} 的 8 个角点经 part 平移/旋转矩阵变换后
 *       取包围盒，合成碰撞/选择箱——用于把「手写 CubeListBuilder 模型」快速转成碰撞箱、或校验
 *       渲染与碰撞是否一致。</li>
 * </ul>
 * 注意：方块碰撞箱在服务端同样会被查询（寻路/交互/合法性校验），因此<b>不得</b>在方块
 * 静态初始化里调用本类；碰撞箱必须来自 {@link RtsJavaModelShape#buildShape()}（纯数据）。
 * 本类仅用于客户端建模与诊断。
 */
public final class JavaModelShapeUtil {

    /** 模型像素单位：1 格 = 16 像素。 */
    private static final float PIXELS_PER_BLOCK = 16.0F;

    private JavaModelShapeUtil() {
    }

    /**
     * 把纯几何盒数据转成渲染模型构建器。
     * <p>
     * {@link RtsJavaModelShape} 的盒坐标是<b>方块像素坐标</b>（0–16，可跨格），而渲染模型的
     * {@code CubeListBuilder} 使用<b>模型局部坐标</b>——模型原点在方块中的位置不同，局部坐标
     * 相对方块坐标会有平移。本方法把每个盒坐标减去 {@code origin}（模型原点在方块中的像素
     * 位置）得到模型局部坐标再调用 {@link CubeListBuilder#addBox}。
     * <p>
     * 例如风力发电机模型原点对准方块底部中心，则 {@code origin = (8, 0, 8)}：方块盒
     * {@code x 0..16} 对应模型盒 {@code x -8..8}。
     *
     * @param shape  几何盒数据
     * @param origin 模型原点在方块中的像素位置（模型坐标 = 方块坐标 - origin）
     * @return 可直接用于 {@code PartDefinition.addOrReplaceChild} 的 {@link CubeListBuilder}。
     */
    public static CubeListBuilder toCubeListBuilder(RtsJavaModelShape shape, float originX, float originY, float originZ) {
        CubeListBuilder builder = CubeListBuilder.create();
        for (RtsJavaModelShape.Box box : shape.boxes()) {
            builder.addBox(box.x() - originX, box.y() - originY, box.z() - originZ,
                    box.sizeX(), box.sizeY(), box.sizeZ());
        }
        return builder;
    }

    /**
     * 等价于 {@link #toCubeListBuilder(RtsJavaModelShape, float, float, float)}，模型原点默认
     * 对准方块原点（{@code origin = (0, 0, 0)}），适合模型与方块坐标一致的简单盒。
     */
    public static CubeListBuilder toCubeListBuilder(RtsJavaModelShape shape) {
        return toCubeListBuilder(shape, 0, 0, 0);
    }

    /**
     * 从已烘焙的 {@link ModelPart} 提取碰撞/选择箱。
     * <p>
     * 借助 {@link ModelPart#visit(PoseStack, ModelPart.Visitor)} 遍历根部件下所有 {@code Cube}，
     * 每个 cube 的 8 个角点（局部像素坐标除以 16 得格坐标）经 part 平移/旋转矩阵变换到世界格
     * 坐标，取各轴 min/max 得到该 cube 的轴对齐包围盒，最后 OR 合并。旋转 part（如风叶）会
     * 产生「旋转后的包围盒」，通常应通过 {@code ignoreParts} 排除。
     *
     * @param root        已烘焙的根 {@link ModelPart}（如 {@code context.bakeLayer(layer)} 产物）。
     * @param ignoreParts 需要排除的部件名片段集合（与部件路径做包含匹配，如 {@code "blade"}）；
     *                    为空表示不排除。
     * @return 合并后的 {@link VoxelShape}；无任何 cube 时返回空形状。
     */
    public static VoxelShape fromModelPart(ModelPart root, Collection<String> ignoreParts) {
        List<VoxelShape> shapes = new ArrayList<>();
        PoseStack stack = new PoseStack();
        root.visit(stack, (pose, path, index, cube) -> {
            if (isIgnored(path, ignoreParts)) {
                return;
            }
            // pose.pose() 为完整变换矩阵（含该 cube 所在 part 及祖先的平移/旋转）。
            Matrix4f matrix = new Matrix4f(pose.pose());
            // cube 的 8 个角点：局部像素坐标 / 16 → 格，再经矩阵变换。
            float[] xs = {cube.minX, cube.maxX};
            float[] ys = {cube.minY, cube.maxY};
            float[] zs = {cube.minZ, cube.maxZ};
            Vector4f p = new Vector4f();
            float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
            for (float x : xs) {
                for (float y : ys) {
                    for (float z : zs) {
                        p.set(x / PIXELS_PER_BLOCK, y / PIXELS_PER_BLOCK, z / PIXELS_PER_BLOCK, 1.0F);
                        matrix.transform(p);
                        float tx = p.x() / p.w();
                        float ty = p.y() / p.w();
                        float tz = p.z() / p.w();
                        minX = Math.min(minX, tx);
                        minY = Math.min(minY, ty);
                        minZ = Math.min(minZ, tz);
                        maxX = Math.max(maxX, tx);
                        maxY = Math.max(maxY, ty);
                        maxZ = Math.max(maxZ, tz);
                    }
                }
            }
            if (maxX >= minX && maxY >= minY && maxZ >= minZ) {
                shapes.add(Shapes.create(new AABB(minX, minY, minZ, maxX, maxY, maxZ)));
            }
        });
        if (shapes.isEmpty()) {
            return Shapes.empty();
        }
        return RtsVoxelShapeUtils.combine(shapes);
    }

    /**
     * 判断部件路径是否应被排除（路径片段包含 ignoreParts 中任意名称）。
     */
    private static boolean isIgnored(String path, Collection<String> ignoreParts) {
        if (ignoreParts == null || ignoreParts.isEmpty() || path == null) {
            return false;
        }
        for (String ignore : ignoreParts) {
            if (!ignore.isEmpty() && path.contains(ignore)) {
                return true;
            }
        }
        return false;
    }
}
