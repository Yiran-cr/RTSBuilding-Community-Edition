package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.hud.IRtsOverlayAccess;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Gui mixin —— 暴露原版 actionbar（overlay message）字段，供 RTS 覆盖式 Screen
 * 在下面板之上补渲染悬浮文字（原版 HUD 在 Screen 打开时不渲染）。
 */
@Mixin(Gui.class)
public abstract class RtsGuiOverlayMixin implements IRtsOverlayAccess {

    @Shadow
    private Component overlayMessageString;

    @Shadow
    private int overlayMessageTime;

    @Override
    public Component rtsbuilding$getOverlayMessage() {
        return this.overlayMessageString;
    }

    @Override
    public int rtsbuilding$getOverlayMessageTime() {
        return this.overlayMessageTime;
    }
}
