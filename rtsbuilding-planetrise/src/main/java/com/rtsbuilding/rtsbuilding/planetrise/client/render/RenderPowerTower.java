package com.rtsbuilding.rtsbuilding.planetrise.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.planetrise.block.entity.PowerTowerBlockEntity;
import com.rtsbuilding.rtsbuilding.planetrise.client.model.ModelPowerTower;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * 无线输电塔方块实体渲染器。
 * <p>
 * 把整座塔的 Java 模型（{@link ModelPowerTower}，5 格高）从主方块底部中心一次性平移绘制，
 * 跨越上方 4 个占位格子；占位方块本身不可见。顶部天线的旋转/缩放动画由游戏时间驱动。
 */
public class RenderPowerTower implements BlockEntityRenderer<PowerTowerBlockEntity> {

    private final ModelPowerTower model;

    public RenderPowerTower(BlockEntityRendererProvider.Context context) {
        this.model = new ModelPowerTower(context.bakeLayer(ModelPowerTower.LAYER));
    }

    @Override
    public void render(PowerTowerBlockEntity blockEntity, float partialTick, PoseStack poseStack,
          MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        // 模型原点对准主方块底部中心。
        poseStack.translate(0.5, 0, 0.5);
        // 动画时间：游戏刻 + 帧间插值，再换算成秒。
        float timeSeconds = 0.0F;
        if (blockEntity.getLevel() != null && blockEntity.getLevel().getGameTime() > 0) {
            timeSeconds = (blockEntity.getLevel().getGameTime() + partialTick) / 20.0F;
        }
        model.render(poseStack, buffer, timeSeconds, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(PowerTowerBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(PowerTowerBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return AABB.encapsulatingFullBlocks(pos.offset(-2, 0, -2), pos.offset(2, 5, 2));
    }
}
