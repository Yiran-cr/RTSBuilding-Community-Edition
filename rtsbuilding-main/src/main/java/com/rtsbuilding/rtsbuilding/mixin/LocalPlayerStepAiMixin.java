package com.rtsbuilding.rtsbuilding.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraState;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin：RTS GUI + 玩家实体环绕模式下恢复玩家实体移动（人物 step ai 兜底）。
 * <p>
 * 进入 RTS 后 {@link BuilderScreen} 打开，{@code KeyboardHandler} 被
 * {@code KeyboardInputMixin} 完全接管，原版 {@link net.minecraft.client.KeyMapping}
 * （WASD/跳跃/下潜）状态不再更新；而 {@link LocalPlayer#aiStep()} 中
 * {@link Input#tick} 只读 KeyMapping 状态，导致 {@code player.input} 永远为 0，
 * 玩家实体即使处于"玩家实体环绕"（以玩家实体为原点、相机跟随实体）也无法移动。
 * <p>
 * 此 Mixin 在 {@code LocalPlayer.aiStep} 的 {@code input.tick} 之后注入：当
 * BuilderScreen 打开且相机处于玩家实体环绕模式时，直接以 GLFW 物理按键状态
 * 覆写 {@code player.input} 的移动/跳跃/下潜/疾跑字段，让角色仍可原地行走。
 * 其他模式（方块环绕、自由视角、非 RTS）不受影响，仍走原版输入逻辑。
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerStepAiMixin {

    /** 是否处于 RTS GUI 且玩家实体环绕模式 */
    private static boolean isPlayerOrbitActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !(mc.screen instanceof BuilderScreen)) return false;
        RtsClientKernel kernel = RtsClientKernel.get();
        if (kernel == null) return false;
        CameraModule cam = kernel.module(CameraModule.class);
        return cam != null && cam.isCameraEnabled() && cam.isPlayerOrbitMode();
    }

    /** GLFW 物理按键是否按下（兼容鼠标键绑定） */
    private static boolean isKeyDown(long window, InputConstants.Key key) {
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return false;
    }

    /** 与原版 {@code KeyboardInput.calculateImpulse} 一致的双向冲量计算 */
    private static float calculateImpulse(boolean input, boolean otherInput) {
        if (input == otherInput) return 0.0F;
        return input ? 1.0F : -1.0F;
    }

    @Inject(method = "aiStep",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/Input;tick(ZF)V",
                    shift = At.Shift.AFTER))
    private void rtsbuilding$restorePlayerOrbitMovement(CallbackInfo ci) {
        if (!isPlayerOrbitActive()) return;

        LocalPlayer self = (LocalPlayer) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        Options options = mc.options;

        boolean up = isKeyDown(window, options.keyUp.getKey());
        boolean down = isKeyDown(window, options.keyDown.getKey());
        boolean left = isKeyDown(window, options.keyLeft.getKey());
        boolean right = isKeyDown(window, options.keyRight.getKey());
        boolean jump = isKeyDown(window, options.keyJump.getKey());
        boolean sneak = isKeyDown(window, options.keyShift.getKey());
        boolean sprint = isKeyDown(window, options.keySprint.getKey());

        Input input = self.input;
        input.up = up;
        input.down = down;
        input.left = left;
        input.right = right;
        input.forwardImpulse = calculateImpulse(up, down);
        input.leftImpulse = calculateImpulse(left, right);
        input.jumping = jump;
        input.shiftKeyDown = sneak;

        // 移动方向跟随环绕相机视角：玩家 yaw 对齐相机朝向，使 W=屏幕上方
        if (input.forwardImpulse != 0.0F || input.leftImpulse != 0.0F) {
            RtsClientKernel kernel = RtsClientKernel.get();
            CameraModule cam = kernel == null ? null : kernel.module(CameraModule.class);
            if (cam != null) {
                // 玩家开始移动时取消自动回正：回正目标基于移动前的朝向，移动后目标已失效
                cam.cancelPlayerOrbitAutoReturn();
                CameraState state = cam.getState();
                // 相机位于玩家 orbitAngle 方向看向玩家，localYaw = 180° - degrees(orbitAngle)；
                // 实体 lookAngle = (-sin(yaw), cos(yaw))，取 localYaw 时前进方向为
                // (-sin(180-θ), cos(180-θ)) = (-sinθ, -cosθ) = 远离相机 = 屏幕上方
                float yaw = Mth.wrapDegrees(state.getYaw());
                self.setYRot(yaw);
                self.setYHeadRot(yaw);
                self.yBodyRot = yaw;
                self.yBodyRotO = yaw;
            }
        }

        // 疾跑：仅在有前进输入、饥饿允许且非飞行时启用（与 LocalPlayer.aiStep 原版条件一致）
        if (sprint && input.forwardImpulse > 0.0F
                && !self.getAbilities().flying
                && self.getFoodData().getFoodLevel() > 6) {
            self.setSprinting(true);
        } else if (!sprint) {
            self.setSprinting(false);
        }
    }
}
