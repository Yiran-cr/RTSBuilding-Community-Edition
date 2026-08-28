package com.rtsbuilding.rtsbuilding.planetrise.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.render.util.RtsAlphaVertexConsumer;
import com.rtsbuilding.rtsbuilding.planetrise.EnergyMod;
import com.rtsbuilding.rtsbuilding.planetrise.block.PowerTowerBlock;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 无线输电塔 Java 模型——几何来自 Blockbench 导出的
 * {@code Wireless_Energy_Transmission_Tower.java}（5 格高塔身 + 顶部天线）。
 * <p>
 * <b>模型结构</b>（纹理 128×128）：
 * <ul>
 *   <li>{@code body}：塔身（底座/塔杆/顶部平台/天线支架），y 从 -78..0 像素
 *       （相对自身原点，经偏移翻转后 5 格高）；</li>
 *   <li>{@code rotate}：顶部可旋转天线（绕 y 轴），含十字天线臂
 *       （{@code bb_main_r1..r8}）。当前渲染为静态；如需旋转动画，设置
 *       {@code rotate.yRot = angle} 即可（与风叶同理）。</li>
 * </ul>
 * <b>坐标系</b>：沿用 Blockbench 导出的实体模型坐标（Y 向下为正），渲染器负责把模型
 * 原点对准方块底部中心（见 {@code RenderPowerTower} 的 {@code translate(0.5, 0, 0.5)}，
 * 配合模型内 {@code translate(0,1.5,0) + scale(-1,-1,1)} 使塔底落在 y=0、塔顶在 y≈5）。
 * 碰撞箱由 {@link PowerTowerBlock#MODEL} 的 {@code buildShape()} 生成，与塔身视觉同源。
 */
public class ModelPowerTower {

    /** 模型图层位置，供 {@code registerLayerDefinition} 注册。 */
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(EnergyMod.MODID, "power_tower"), "main");

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(EnergyMod.MODID, "textures/block/power_tower/power_tower.png");

    /** 顶部天线动画周期（秒），与 Blockbench 导出的 {@code Wireless_Energy_Transmission_TowerAnimation} 一致。 */
    private static final float ANIM_DURATION = 3.0F;

    private static final float DEG_TO_RAD = Mth.DEG_TO_RAD;

    private final ModelPart rotate;
    private final ModelPart body;

    public ModelPowerTower(ModelPart root) {
        this.rotate = root.getChild("rotate");
        this.body = root.getChild("body");
    }

    /**
     * 构建图层定义（几何照搬 Blockbench 导出的
     * {@code Wireless_Energy_Transmission_Tower.createBodyLayer}，纹理分辨率 128×128）。
     */
    public static LayerDefinition createLayerDefinition() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();

        PartDefinition rotate = partDefinition.addOrReplaceChild("rotate", CubeListBuilder.create()
                .texOffs(84, 0).addBox(-9.0F, -7.0F, -5.0F, 2.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(80, 55).addBox(-9.0F, -15.0F, -5.0F, 2.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(56, 0).addBox(-10.0F, -11.0F, -6.0F, 2.0F, 2.0F, 12.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -30.5F, 0.0F));

        rotate.addOrReplaceChild("bb_main_r1", CubeListBuilder.create()
                        .texOffs(56, 14).addBox(-9.0F, -15.0F, -6.0F, 2.0F, 2.0F, 12.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 4.0F, -1.0F, 0.0F, -1.5708F, 0.0F));

        rotate.addOrReplaceChild("bb_main_r2", CubeListBuilder.create()
                        .texOffs(84, 12).addBox(-9.0F, -15.0F, -5.0F, 2.0F, 2.0F, 10.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, -3.1416F, 0.0F, 3.1416F));

        rotate.addOrReplaceChild("bb_main_r3", CubeListBuilder.create()
                        .texOffs(16, 69).addBox(-9.0F, -15.0F, -6.0F, 2.0F, 2.0F, 12.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(1.0F, 4.0F, 0.0F, -3.1416F, 0.0F, 3.1416F));

        rotate.addOrReplaceChild("bb_main_r4", CubeListBuilder.create()
                        .texOffs(76, 80).addBox(-9.0F, -15.0F, -5.0F, 2.0F, 2.0F, 10.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -3.1416F, 0.0F, 3.1416F));

        rotate.addOrReplaceChild("bb_main_r5", CubeListBuilder.create()
                        .texOffs(16, 83).addBox(-9.0F, -15.0F, -5.0F, 2.0F, 2.0F, 10.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.5708F, 0.0F));

        rotate.addOrReplaceChild("bb_main_r6", CubeListBuilder.create()
                        .texOffs(48, 67).addBox(-9.0F, -15.0F, -6.0F, 2.0F, 2.0F, 12.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 4.0F, 1.0F, 0.0F, 1.5708F, 0.0F));

        rotate.addOrReplaceChild("bb_main_r7", CubeListBuilder.create()
                        .texOffs(68, 92).addBox(-9.0F, -15.0F, -5.0F, 2.0F, 2.0F, 10.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.0F, 1.5708F, 0.0F));

        rotate.addOrReplaceChild("bb_main_r8", CubeListBuilder.create()
                        .texOffs(84, 24).addBox(-9.0F, -15.0F, -5.0F, 2.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
                        .texOffs(44, 81).addBox(-9.0F, -23.0F, -5.0F, 2.0F, 2.0F, 10.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition body = partDefinition.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-7.0F, -5.0F, -7.0F, 14.0F, 5.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(0, 38).addBox(-6.0F, -10.0F, -6.0F, 12.0F, 5.0F, 12.0F, new CubeDeformation(0.0F))
                .texOffs(76, 67).addBox(-3.0F, -17.0F, -3.0F, 6.0F, 7.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(0, 55).addBox(-2.0F, -55.0F, -2.0F, 4.0F, 38.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(48, 55).addBox(-4.0F, -59.0F, -4.0F, 8.0F, 4.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(16, 55).addBox(-4.0F, -76.0F, -4.0F, 8.0F, 6.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(56, 28).addBox(-3.0F, -78.0F, -3.0F, 6.0F, 2.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(44, 69).addBox(-3.0F, -70.0F, 2.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(40, 83).addBox(-3.0F, -70.0F, -3.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(62, 93).addBox(2.0F, -70.0F, -3.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(42, 99).addBox(2.0F, -70.0F, 2.0F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(46, 93).addBox(-2.0F, -70.0F, -2.0F, 4.0F, 11.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(16, 99).addBox(-2.0F, -11.0F, 3.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(100, 77).addBox(-2.0F, -10.0F, 6.0F, 4.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        body.addOrReplaceChild("bone6_r1", CubeListBuilder.create()
                        .texOffs(92, 100).addBox(-2.0F, -8.0F, 6.0F, 4.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(96, 44).addBox(-7.0F, -1.0F, -8.0F, 14.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(100, 67).addBox(-2.0F, -9.0F, 3.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));

        body.addOrReplaceChild("bone8_r1", CubeListBuilder.create()
                        .texOffs(100, 83).addBox(-2.0F, -10.0F, 6.0F, 4.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(100, 72).addBox(-2.0F, -11.0F, 3.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -3.1416F, 0.0F, 3.1416F));

        body.addOrReplaceChild("bb_main_r9", CubeListBuilder.create()
                        .texOffs(92, 96).addBox(-7.0F, -3.0F, -8.0F, 14.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

        body.addOrReplaceChild("bb_main_r10", CubeListBuilder.create()
                        .texOffs(96, 48).addBox(-7.0F, -3.0F, -8.0F, 14.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        body.addOrReplaceChild("bone9_r1", CubeListBuilder.create()
                        .texOffs(32, 99).addBox(-2.0F, -8.0F, 6.0F, 4.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 97).addBox(-2.0F, -9.0F, 3.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

        body.addOrReplaceChild("bb_main_r11", CubeListBuilder.create()
                        .texOffs(96, 36).addBox(-7.0F, -3.0F, -8.0F, 14.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 3.1416F, 0.0F));

        return LayerDefinition.create(meshDefinition, 128, 128);
    }

    /**
     * 渲染整座塔。
     * <p>
     * Blockbench 的 {@code modded_entity} 导出使用 <b>Y 向下为正</b> 的实体坐标系
     * （{@code flip_y=True}），而方块世界 Y 向上为正。因此这里先
     * {@code translate(0, 1.5, 0)} 让塔底抬到地面 y=0（塔身跨 y -54..24 像素 ≈ 5 格高，
     * 塔顶在 y≈5），再 {@code scale(-1, -1, 1)}（等价绕 Z 轴 180°，X/Y 同时翻转，
     * 与实体渲染链 {@code LivingEntityRenderer} 一致）把塔翻转正立。
     * <b>顺序不能反</b>：PoseStack 是右乘矩阵，先 scale 后 translate 会把整塔翻到地下。
     * <p>
     * 顶部天线（{@code rotate} 组）按 Blockbench 动画驱动：
     * <ul>
     *   <li><b>旋转</b>：3 秒绕 y 轴转一整圈（0°→-360°，线性）；</li>
     *   <li><b>缩放</b>：3 秒周期内 1.0→0.8→1.0 线性脉冲（呼吸式缩放）。</li>
     * </ul>
     *
     * @param timeSeconds 动画时间（秒），由调用方提供（方块实体渲染器传游戏时间 + 部分 tick，
     *                    物品渲染器在无世界时传固定值）。
     */
    public void render(PoseStack poseStack, MultiBufferSource buffer, float timeSeconds, int light, int overlay) {
        render(poseStack, buffer, timeSeconds, light, overlay, 1.0F);
    }

    /**
     * 渲染整座塔（带透明度）。
     * <p>
     * {@code alpha} &lt; 1 时使用半透明实体层（{@code RenderType.entityTranslucent}，
     * TRANSLUCENT_TRANSPARENCY 混合 + NO_CULL，顶点格式 NEW_ENTITY 与不透明层一致），
     * 并把顶点 alpha 统一乘以 {@code alpha}，用于 RTS 放置虚影；{@code alpha} = 1 时退化为
     * 常规不透明 cutout 渲染（方块实体/物品显示）。同一处方块发射的顶点格式一致，
     * 保证半透明与不透明路径共用同一份几何数据。
     */
    public void render(PoseStack poseStack, MultiBufferSource buffer, float timeSeconds, int light, int overlay,
                       float alpha) {
        // 顶部天线动画：绕 y 轴旋转 + 缩放脉冲（周期 3 秒）。
        float animTime = timeSeconds % ANIM_DURATION;
        rotate.yRot = (-360.0F * animTime / ANIM_DURATION) * DEG_TO_RAD;
        float half = ANIM_DURATION / 2.0F;
        float scale = animTime < half
                ? 1.0F + (0.8F - 1.0F) * (animTime / half)        // 1.0 → 0.8
                : 0.8F + (1.0F - 0.8F) * ((animTime - half) / half); // 0.8 → 1.0
        rotate.xScale = rotate.yScale = rotate.zScale = scale;

        boolean translucent = alpha < 0.999F;
        RenderType renderType = translucent
                ? RenderType.entityTranslucent(TEXTURE)
                : RenderType.entityCutoutNoCull(TEXTURE);

        poseStack.pushPose();
        poseStack.translate(0, 1.5F, 0);
        poseStack.scale(-1, -1, 1);

        VertexConsumer base = buffer.getBuffer(renderType);
        VertexConsumer consumer = translucent ? new RtsAlphaVertexConsumer(base, alpha) : base;
        rotate.render(poseStack, consumer, light, overlay, 0xFFFFFFFF);
        body.render(poseStack, consumer, light, overlay, 0xFFFFFFFF);
        poseStack.popPose();
    }
}
