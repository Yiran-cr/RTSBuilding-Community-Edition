package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin：RTS 模式（{@link BuilderScreen} 打开）下完全阻断非 RTS 鼠标按键。
 * <p>
 * RTS 仅使用左(0)/右(1)/中(2)三个鼠标键（建造、视角拖拽、缩放等），不会用到
 * 鼠标侧键。原版 {@link MouseHandler#onPress} 在转发给屏幕前会先触发
 * {@code InputEvent.MouseButton}，其他模组若绑定了鼠标键位仍可借此收到事件。
 * 此 Mixin 在 HEAD 直接取消侧键（索引 &gt; 2）的整个处理，避免其他模组通过
 * {@code InputEvent.MouseButton} / {@code KeyMapping} 检测到按键。
 */
@Mixin(MouseHandler.class)
abstract class MouseInputMixin {

    private static boolean isRtsScreenActive() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.screen instanceof BuilderScreen;
    }

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$blockForeignMouseButtons(long windowPointer, int button, int action, int modifiers, CallbackInfo ci) {
        if (!isRtsScreenActive()) return;
        if (button > GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            ci.cancel();
        }
    }
}
