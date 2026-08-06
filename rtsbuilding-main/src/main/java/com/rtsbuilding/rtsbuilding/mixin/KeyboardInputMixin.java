package com.rtsbuilding.rtsbuilding.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin：RTS 模式（{@link BuilderScreen} 打开）下完全接管键盘输入。
 * <p>
 * 进入 RTS 后 {@code BuilderScreen} 虽已接管 {@code screen.keyPressed}，但原版
 * {@link KeyboardHandler} 仍会在屏幕打开时处理 F11/F2（第 369-387 行无条件触发）、
 * {@code KeyInputEvent}（其他模组键位）、KeyMapping 等全局键，造成按键"穿透"。
 * 此 Mixin 在 {@link KeyboardHandler#keyPress} 与 {@code charTyped} 的 HEAD 直接
 * 取消整个原版处理流程，仅把事件转发给 {@link BuilderScreen} 自身消费：
 * <ul>
 *   <li>RTS 快捷键 / 面板 / 文本框输入仍由 {@code BuilderScreen} 正常处理；</li>
 *   <li>其余所有键位（F3、F11、F2、ESC 之外的任何键、其他模组键位）均被完全阻断，
 *       不触发原版全局快捷键，也不触发其他模组的 {@code KeyInputEvent} 与
 *       {@code KeyMapping#consumeClick()}。</li>
 * </ul>
 * 按键"按下/释放"状态仍通过 {@link KeyMapping#set} 维护（仅当 BuilderScreen 消费时
 * 才置为按下），避免卡键，同时杜绝其他模组用 {@code isDown()} 检测到按键。
 */
@Mixin(KeyboardHandler.class)
abstract class KeyboardInputMixin {

    private static boolean isRtsScreenActive() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.screen instanceof BuilderScreen;
    }

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$blockForeignKeys(long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (!isRtsScreenActive()) return;

        Screen screen = Minecraft.getInstance().screen;
        boolean consumed = false;
        if (screen instanceof BuilderScreen builder) {
            if (action == 1 || action == 2) {
                consumed = builder.keyPressed(key, scanCode, modifiers);
            } else if (action == 0) {
                builder.keyReleased(key, scanCode, modifiers);
            }
        }
        KeyMapping.set(InputConstants.getKey(key, scanCode), action != 0 && consumed);
        ci.cancel();
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$blockForeignChars(long windowPointer, int codePoint, int modifiers, CallbackInfo ci) {
        if (!isRtsScreenActive()) return;

        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof BuilderScreen builder) {
            builder.charTyped((char) codePoint, modifiers);
        }
        ci.cancel();
    }
}
