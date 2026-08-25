package com.rtsbuilding.rtsbuilding.planetrise.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.planetrise.client.model.ModelWindGenerator;
import com.rtsbuilding.rtsbuilding.planetrise.block.entity.WindGeneratorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * 风力发电机方块实体渲染器（参考 Mekanism {@code RenderWindGenerator}）。
 * <p>
 * 把整座塔的 Java 模型（{@link ModelWindGenerator}，3 格高）从主方块底部中心一次性平移绘制，
 * 跨越上方 2 个占位格子；占位方块本身不可见。塔顶风叶的旋转角度取自方块实体的
 * 客户端动画状态（{@link WindGeneratorBlockEntity#getClientAngle(float)}）。
 */
public class RenderWindGenerator implements BlockEntityRenderer<WindGeneratorBlockEntity> {

    private final ModelWindGenerator model;

    public RenderWindGenerator(BlockEntityRendererProvider.Context context) {
        this.model = new ModelWindGenerator(context.bakeLayer(ModelWindGenerator.LAYER));
    }

    @Override
    public void render(WindGeneratorBlockEntity blockEntity, float partialTick, PoseStack poseStack,
          MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        // 模型原点对准主方块底部中心。
        poseStack.translate(0.5, 0, 0.5);
        model.render(poseStack, buffer, blockEntity.getClientAngle(partialTick), packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(WindGeneratorBlockEntity blockEntity) {
        // 塔高 3 格，风叶可能甩出默认渲染范围，始终允许离屏渲染。
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(WindGeneratorBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return AABB.encapsulatingFullBlocks(pos.offset(-2, 0, -2), pos.offset(2, 3, 2));
    }
}
