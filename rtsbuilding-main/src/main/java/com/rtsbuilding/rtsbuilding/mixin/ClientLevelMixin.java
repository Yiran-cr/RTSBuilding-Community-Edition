package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin：RTS 模式（{@link BuilderScreen} 打开）下抑制方块破坏粒子效果。
 *
 * <p>方块破坏粒子由 {@code levelEvent(2001, ...)} 事件触发，
 * 客户端最终统一汇聚到 {@link ClientLevel#addDestroyBlockEffect} 渲染。
 * RTS 模式下远程破坏（连锁挖掘/区域破坏/放置回收）会高频触发破坏粒子，
 * 造成画面闪烁干扰；此处直接跳过粒子生成，
 * 一条路径即可覆盖模组自发的粒子与服务端广播的粒子。</p>
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    private static boolean isRtsScreenActive() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.screen instanceof BuilderScreen;
    }

    /**
     * 在 {@code addDestroyBlockEffect} 头部拦截：RTS 模式激活时取消破坏粒子生成。
     */
    @Inject(method = "addDestroyBlockEffect(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$suppressBreakParticlesInRts(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (isRtsScreenActive()) {
            ci.cancel();
        }
    }
}
