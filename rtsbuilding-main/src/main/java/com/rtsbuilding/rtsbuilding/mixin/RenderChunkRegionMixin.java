package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.culling.RtsRayCylinderCullingState;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 射线圆柱剔除的 vanilla chunk 编译入口。
 *
 * <p>这里比 {@link BlockRenderDispatcherMixin} 更早：chunk mesh 读取方块状态时，
 * 圆柱体内的位置直接表现为空气。这样既能跳过模型渲染，也能避免流体和方块实体
 * 被加入本次编译结果。</p>
 */
@Mixin(RenderChunkRegion.class)
public abstract class RenderChunkRegionMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$cullBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (RtsRayCylinderCullingState.shouldCull(pos)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$cullFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (RtsRayCylinderCullingState.shouldCull(pos)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState().getFluidState());
        }
    }

    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$cullBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (RtsRayCylinderCullingState.shouldCull(pos)) {
            cir.setReturnValue(null);
        }
    }
}