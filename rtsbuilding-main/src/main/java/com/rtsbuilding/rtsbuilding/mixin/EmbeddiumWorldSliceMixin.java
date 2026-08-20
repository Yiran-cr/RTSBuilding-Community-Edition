package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.culling.RtsRayCylinderCullingState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Embeddium 区块网格的可选隐藏入口（结构同 {@link SodiumLevelSliceMixin}）。
 *
 * <p>Embeddium 使用 {@code EmbeddiumWorldSlice}（更新版 LevelSlice 替代品）在后台
 * 线程生成区块网格，同样绕过原版 {@code RenderChunkRegion}。保持纯适配层 +
 * {@code @Pseudo} 字符串 targets，未安装 Embeddium 时无前置依赖。</p>
 */
@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.world.EmbeddiumWorldSlice", remap = false)
public abstract class EmbeddiumWorldSliceMixin {
    @Inject(
            method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void rtsbuilding$cullBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (RtsRayCylinderCullingState.shouldCull(pos)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(
            method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void rtsbuilding$cullBlockState(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (RtsRayCylinderCullingState.shouldCull(new BlockPos(x, y, z))) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(
            method = "getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void rtsbuilding$cullFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (RtsRayCylinderCullingState.shouldCull(pos)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState().getFluidState());
        }
    }

    @Inject(
            method = "getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void rtsbuilding$cullBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (RtsRayCylinderCullingState.shouldCull(pos)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(
            method = "getBlockEntity(III)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void rtsbuilding$cullBlockEntity(int x, int y, int z, CallbackInfoReturnable<BlockEntity> cir) {
        if (RtsRayCylinderCullingState.shouldCull(new BlockPos(x, y, z))) {
            cir.setReturnValue(null);
        }
    }
}