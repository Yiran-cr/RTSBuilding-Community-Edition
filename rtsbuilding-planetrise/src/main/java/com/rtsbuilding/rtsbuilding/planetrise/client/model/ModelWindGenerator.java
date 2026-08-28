package com.rtsbuilding.rtsbuilding.planetrise.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.render.util.RtsAlphaVertexConsumer;
import com.rtsbuilding.rtsbuilding.planetrise.EnergyMod;
import com.rtsbuilding.rtsbuilding.planetrise.block.WindGeneratorBlock;
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
 * 风力发电机 Java 模型——几何来自 Blockbench 导出的 {@code Wind_Turbine.java}（3 格高塔身）。
 * <p>
 * <b>模型结构</b>（纹理 128×128）：
 * <ul>
 *   <li>{@code bb_main}：塔身（底座/塔杆/顶部平台），y 从 -48..0 像素（相对自身原点，
 *       经偏移后 3 格高）；</li>
 *   <li>{@code bone}：机舱 + 三片风叶（{@code bone_r1..r4}/{@code bone3_r1..r3} 叶臂），
 *       以竖直方向为轴心<b>绕 y 轴旋转</b>——渲染时每帧递增 {@code bone.yRot}
 *       驱动风叶旋转动画，与 {@link #render} 的 {@code angle} 参数（度数）对应。</li>
 * </ul>
 * <b>坐标系</b>：沿用 Blockbench 导出的实体模型坐标（Y 向下为正），渲染器负责把模型原点
 * 对准方块底部中心（见 {@code RenderWindGenerator} 的 {@code translate(0.5, 1.5, 0.5)}，
 * 使 3 格高塔身落于方块 y 0..3）。碰撞箱由 {@link WindGeneratorBlock#MODEL} 的
 * {@code buildShape()} 生成，与塔身视觉同源；风叶为旋转部件不参与碰撞。
 */
public class ModelWindGenerator {

    /** 模型图层位置，供 {@code registerLayerDefinition} 注册。 */
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(EnergyMod.MODID, "wind_generator"), "main");

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(EnergyMod.MODID, "textures/block/wind_generator/wind_generator.png");

    private static final float DEG_TO_RAD = Mth.DEG_TO_RAD;

    private final ModelPart bone;
    private final ModelPart bbMain;

    public ModelWindGenerator(ModelPart root) {
        this.bone = root.getChild("bone");
        this.bbMain = root.getChild("bb_main");
    }

    /**
     * 构建图层定义（几何照搬 Blockbench 导出的 {@code Wind_Turbine.createBodyLayer}，
     * 纹理分辨率 128×128）。风叶组 {@code bone} 的整体旋转由渲染时 {@code zRot} 驱动。
     */
    public static LayerDefinition createLayerDefinition() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();

        PartDefinition bone = partDefinition.addOrReplaceChild("bone", CubeListBuilder.create()
                .texOffs(24, 58).addBox(-9.0F, 21.75F, -9.0F, 15.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(56, 0).addBox(-10.0F, 18.75F, -9.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(48, 19).addBox(-11.0F, 15.75F, -9.0F, 17.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -32.75F, 0.0F));

        PartDefinition bone_r1 = bone.addOrReplaceChild("bone_r1", CubeListBuilder.create()
                .texOffs(36, 85).addBox(2.75F, -1.0F, -6.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(76, 81).addBox(0.75F, -2.0F, -10.0F, 6.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 16.0F, 0.0F, 3.1416F, 0.0F, 1.5708F));

        PartDefinition bone_r2 = bone.addOrReplaceChild("bone_r2", CubeListBuilder.create()
                .texOffs(24, 77).addBox(0.75F, -2.0F, -10.0F, 6.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(24, 52).addBox(2.75F, -1.0F, -6.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 16.0F, 0.0F, 0.0F, 0.0F, 1.5708F));

        PartDefinition bone_r3 = bone.addOrReplaceChild("bone_r3", CubeListBuilder.create()
                .texOffs(24, 48).addBox(2.75F, -1.0F, -6.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(76, 73).addBox(0.75F, -2.0F, -10.0F, 6.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 16.0F, 0.0F, -1.5708F, 0.0F, 1.5708F));

        PartDefinition bone3_r1 = bone.addOrReplaceChild("bone3_r1", CubeListBuilder.create()
                .texOffs(58, 58).addBox(-9.0F, 21.75F, -9.0F, 15.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(56, 4).addBox(-10.0F, 18.75F, -9.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(48, 23).addBox(-11.0F, 15.75F, -9.0F, 17.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition bone3_r2 = bone.addOrReplaceChild("bone3_r2", CubeListBuilder.create()
                .texOffs(58, 62).addBox(-9.0F, 21.75F, -9.0F, 15.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(56, 12).addBox(-10.0F, 18.75F, -9.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(48, 31).addBox(-11.0F, 15.75F, -9.0F, 17.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.5708F, 0.0F));

        PartDefinition bone_r4 = bone.addOrReplaceChild("bone_r4", CubeListBuilder.create()
                .texOffs(86, 22).addBox(2.75F, -1.0F, -6.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 78).addBox(0.75F, -2.0F, -10.0F, 6.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 16.0F, 0.0F, 1.5708F, 0.0F, 1.5708F));

        PartDefinition bone3_r3 = bone.addOrReplaceChild("bone3_r3", CubeListBuilder.create()
                .texOffs(48, 27).addBox(-11.0F, 15.75F, -9.0F, 17.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(56, 8).addBox(-10.0F, 18.75F, -9.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(24, 62).addBox(-9.0F, 21.75F, -9.0F, 15.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -3.1416F, 0.0F, 3.1416F));

        PartDefinition bb_main = partDefinition.addOrReplaceChild("bb_main", CubeListBuilder.create()
                .texOffs(64, 47).addBox(-7.0F, -3.0F, -8.0F, 14.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 19).addBox(-6.0F, -10.0F, -6.0F, 12.0F, 5.0F, 12.0F, new CubeDeformation(0.0F))
                .texOffs(64, 35).addBox(-3.0F, -16.0F, -3.0F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(60, 66).addBox(-2.0F, -30.0F, -2.0F, 4.0F, 13.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(0, 70).addBox(-3.0F, -32.0F, -3.0F, 6.0F, 2.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(32, 36).addBox(-4.0F, -41.0F, -4.0F, 8.0F, 3.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(32, 47).addBox(-4.0F, -36.0F, -4.0F, 8.0F, 3.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(0, 36).addBox(-4.0F, -47.0F, -4.0F, 8.0F, 4.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(24, 70).addBox(-3.0F, -48.0F, -3.0F, 6.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(60, 83).addBox(-2.0F, -11.0F, 3.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(10, 86).addBox(-2.0F, -10.0F, 6.0F, 4.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(76, 66).addBox(-3.0F, -17.0F, -3.0F, 6.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(0, 48).addBox(-3.0F, -43.0F, -3.0F, 6.0F, 11.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition bb_main_r1 = bb_main.addOrReplaceChild("bb_main_r1", CubeListBuilder.create()
                .texOffs(30, 66).addBox(-7.0F, -3.0F, -8.0F, 14.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 3.1416F, 0.0F));

        PartDefinition bone6_r1 = bb_main.addOrReplaceChild("bone6_r1", CubeListBuilder.create()
                .texOffs(86, 16).addBox(-2.0F, -8.0F, 6.0F, 4.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(20, 85).addBox(-2.0F, -9.0F, 3.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(0, 66).addBox(-7.0F, -1.0F, -8.0F, 14.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.0F, 1.5708F, 0.0F));

        PartDefinition bone9_r1 = bb_main.addOrReplaceChild("bone9_r1", CubeListBuilder.create()
                .texOffs(0, 86).addBox(-2.0F, -8.0F, 6.0F, 4.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(44, 82).addBox(-2.0F, -9.0F, 3.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(64, 51).addBox(-7.0F, -1.0F, -8.0F, 14.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition bb_main_r2 = bb_main.addOrReplaceChild("bb_main_r2", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-7.0F, -5.0F, -7.0F, 14.0F, 5.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(44, 77).addBox(-2.0F, -11.0F, 3.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(48, 70).addBox(-2.0F, -10.0F, 6.0F, 4.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -3.1416F, 0.0F, 3.1416F));

        return LayerDefinition.create(meshDefinition, 128, 128);
    }

    /**
     * 渲染整座塔。
     * <p>
 * Blockbench 的 {@code modded_entity} 导出使用 <b>Y 向下为正</b> 的实体坐标系
 * （{@code flip_y=True}），而方块世界 Y 向上为正。因此这里先
 * {@code translate(0, 1.5, 0)} 让塔底抬到地面 y=0（塔身跨 y -24..24 像素
 * = 3 格高，塔顶在 y=3），再 {@code scale(-1, -1, 1)}（等价绕 Z 轴 180°，
 * X/Y 同时翻转，与实体渲染链 {@code LivingEntityRenderer} 的
 * {@code scale(-1,-1,1)} 一致）把塔翻转正立。<b>顺序不能反</b>：先 scale 后
 * translate 会把塔整体翻到地下。调用方（方块实体渲染器/物品渲染器）只需把原点
 * 对准方块底部中心/物品体素中心即可。
     *
     * @param angle 风叶当前旋转角（度），驱动 {@code bone} 组绕 y 轴（竖直方向）旋转。
     */
    public void render(PoseStack poseStack, MultiBufferSource buffer, float angle, int light, int overlay) {
        render(poseStack, buffer, angle, light, overlay, 1.0F);
    }

    /**
     * 渲染整座塔（带透明度）。
     * <p>
     * {@code alpha} &lt; 1 时使用半透明实体层（{@code RenderType.entityTranslucent}，
     * TRANSLUCENT_TRANSPARENCY 混合 + NO_CULL，顶点格式 NEW_ENTITY 与不透明层一致），
     * 并把顶点 alpha 统一乘以 {@code alpha}，用于 RTS 放置虚影；{@code alpha} = 1 时退化为
     * 常规不透明 cutout 渲染（方块实体/物品显示）。
     *
     * @param angle 风叶当前旋转角（度），驱动 {@code bone} 组绕 y 轴（竖直方向）旋转。
     */
    public void render(PoseStack poseStack, MultiBufferSource buffer, float angle, int light, int overlay,
                       float alpha) {
        // 风叶组绕 y 轴（竖直方向为轴心）旋转，形成旋转动画。
        bone.yRot = angle * DEG_TO_RAD;

        boolean translucent = alpha < 0.999F;
        RenderType renderType = translucent
                ? RenderType.entityTranslucent(TEXTURE)
                : RenderType.entityCutoutNoCull(TEXTURE);

        poseStack.pushPose();
        // 实体坐标（Y 向下为正）→ 方块世界坐标（Y 向上为正）。
        // 顺序很关键：必须先平移再翻转（等价 Mek 的 translate → mulPose(ZP180)）。
        // PoseStack 是右乘矩阵，后调用的变换先作用于顶点；若先 scale 后 translate，
        // 顶点会先被抬到 y=0..3 再翻转成 y=-3..0，整塔落到地下。
        poseStack.translate(0, 1.5F, 0);
        poseStack.scale(-1, -1, 1);

        VertexConsumer base = buffer.getBuffer(renderType);
        VertexConsumer consumer = translucent ? new RtsAlphaVertexConsumer(base, alpha) : base;
        bone.render(poseStack, consumer, light, overlay, 0xFFFFFFFF);
        bbMain.render(poseStack, consumer, light, overlay, 0xFFFFFFFF);
        poseStack.popPose();
    }
}
