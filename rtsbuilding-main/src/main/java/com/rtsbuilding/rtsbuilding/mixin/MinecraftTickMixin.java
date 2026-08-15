package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin：RTS GUI + 玩家实体环绕模式下恢复原版键位动作处理（mc 层兜底）。
 * <p>
 * 原版 {@link Minecraft#tick()} 只在 {@code overlay == null && screen == null}
 * 时才调用 {@code handleKeybinds()}（疾跑切换、F5 视角、物品栏、热键栏等）。
 * RTS 模式下 {@link BuilderScreen} 常驻 {@code mc.screen}，这些键位动作因此
 * 完全停摆；玩家实体环绕模式允许移动角色后，疾跑切换等依赖键位的动作会随之失效。
 * <p>
 * 此 Mixin 在 {@code Minecraft.tick} HEAD 注入：当 BuilderScreen 打开且相机处于
 * 玩家实体环绕模式时，主动调用 {@code handleKeybinds()}，让原版键位处理照常执行。
 * 由于 RTS 键盘输入已被 {@code KeyboardInputMixin} 接管，攻击/使用等 consumeClick
 * 键在 RTS 下不会被置为按下，不会误触发破坏行为。
 */
@Mixin(Minecraft.class)
abstract class MinecraftTickMixin {

    /** 访问原版私有的 {@code handleKeybinds()}（只在 RTS 玩家环绕模式下调用） */
    @Invoker("handleKeybinds")
    abstract void rtsbuilding$invokeHandleKeybinds();

    @Inject(method = "tick", at = @At("HEAD"))
    private void rtsbuilding$restoreKeybindsInPlayerOrbit(CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;
        if (!(self.screen instanceof BuilderScreen)) return;
        RtsClientKernel kernel = RtsClientKernel.get();
        if (kernel == null) return;
        CameraModule cam = kernel.module(CameraModule.class);
        if (cam == null || !cam.isCameraEnabled() || !cam.isPlayerOrbitMode()) return;
        this.rtsbuilding$invokeHandleKeybinds();
    }
}
