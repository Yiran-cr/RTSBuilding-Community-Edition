package com.rtsbuilding.rtsbuilding.client.hud;

import net.minecraft.network.chat.Component;

/**
 * RTS HUD 访问接口 —— 由 {@code Gui} 的 mixin（{@code RtsGuiOverlayMixin}）实现，
 * 暴露原版 actionbar（悬浮消息）内容与剩余时间，供 RTS 覆盖式 Screen 在下面板之上补渲染。
 * <p>原版 HUD 在 Screen 打开时不渲染，而这些字段在 1.21.1 无公开 getter，故用 mixin 暴露。</p>
 */
public interface IRtsOverlayAccess {

    /** 当前 actionbar 悬浮消息（可能为 null）。 */
    Component rtsbuilding$getOverlayMessage();

    /** actionbar 剩余显示时间（tick，&gt;0 表示应显示）。 */
    int rtsbuilding$getOverlayMessageTime();
}
