package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin：RTS 模式（{@link BuilderScreen} 打开）下拦截原版方块破坏事件。
 *
 * <p>方块破坏事件由服务端 {@code levelEvent(2001)} 广播，客户端在
 * {@link ClientPacketListener#handleLevelEvent} 收到后于
 * {@code LevelRenderer} 播放原版破坏音与粒子。RTS 远程破坏的破坏音已由客户端
 * {@code RtsClientNetworkHandlers.handleBreakAnimation} 在本地主相机位置播放，
 * 若原版破坏音/粒子再播放一次会形成双份效果；此处直接吞掉 2001 事件。</p>
 *
 * <p>2001 事件同时携带破坏粒子，与 {@code ClientLevelMixin} 的
 * {@code addDestroyBlockEffect} 拦截互为兜底，两处拦截保证破坏效果在 RTS 模式下静默。</p>
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    private static final int EVENT_BLOCK_BREAK = 2001;

    private static boolean isRtsScreenActive() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.screen instanceof BuilderScreen;
    }

    @Inject(method = "handleLevelEvent(Lnet/minecraft/network/protocol/game/ClientboundLevelEventPacket;)V",
            at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$suppressBreakParticlesInRts(ClientboundLevelEventPacket packet, CallbackInfo ci) {
        if (isRtsScreenActive() && packet.getType() == EVENT_BLOCK_BREAK) {
            ci.cancel();
        }
    }
}
