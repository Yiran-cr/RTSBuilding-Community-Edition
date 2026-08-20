package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.culling.RtsRayCylinderCullingState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 阻止 Flywheel 为当前剔除圆柱重新创建方块实体 Visual。
 *
 * <p>范围变化时，已经存在的 Visual 由 {@code RtsFlywheelCullingCompat} 主动同步；
 * 这里守住 Flywheel 的统一准入点，避免机械（如 Create）因方块更新、区块重载或
 * 渲染器重建而再次出现。该适配不识别任何 Create 方块 ID，也不维护第二份隐藏状态。</p>
 */
@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage", remap = false)
public abstract class FlywheelBlockEntityStorageMixin {
    @Inject(
            method = "willAccept(Lnet/minecraft/world/level/block/entity/BlockEntity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1)
    private void rtsbuilding$rejectCulledBlockEntity(
            BlockEntity blockEntity,
            CallbackInfoReturnable<Boolean> cir) {
        if (blockEntity != null && RtsRayCylinderCullingState.shouldCull(blockEntity.getBlockPos())) {
            cir.setReturnValue(false);
        }
    }
}