package com.rtsbuilding.rtsbuilding.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.common.entity.RtsDroneEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 碰撞箱显示（F3+B）时跳过 RTS 无人机的碰撞箱渲染。
 *
 * <p>无人机的碰撞箱仅用于名称标签定位与模型包裹（{@code noPhysics}，无物理碰撞），
 * 不应出现在调试碰撞箱显示中。拦截 {@link EntityRenderDispatcher} 的两个碰撞箱渲染
 * 静态方法：本地实体碰撞箱 {@code renderHitbox} 与服务端实体追踪碰撞箱
 * {@code renderServerSideHitbox}，对 {@link RtsDroneEntity} 直接取消。</p>
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(
            method = "renderHitbox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/Entity;FFFF)V",
            at = @At("HEAD"),
            cancellable = true)
    private static void rtsbuilding$skipDroneHitbox(PoseStack poseStack, VertexConsumer vertexConsumer,
            Entity entity, float partialTick, float offsetX, float offsetY, float offsetZ,
            CallbackInfo ci) {
        if (entity instanceof RtsDroneEntity) {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderServerSideHitbox(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"),
            cancellable = true)
    private static void rtsbuilding$skipDroneServerSideHitbox(PoseStack poseStack, Entity entity,
            MultiBufferSource bufferSource, CallbackInfo ci) {
        if (entity instanceof RtsDroneEntity) {
            ci.cancel();
        }
    }
}
