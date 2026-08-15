package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.RtsEffectStateTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin：RTS 模式（{@link BuilderScreen} 打开）下的方块状态变化拦截与破坏事件静默。
 *
 * <p>1. 方块破坏事件：服务端 {@code levelEvent(2001)} 广播后客户端在
 * {@link ClientPacketListener#handleLevelEvent} 收到并播放原版破坏音与粒子。RTS 远程破坏的
 * 破坏音已由客户端 {@code RtsClientNetworkHandlers.handleBreakAnimation} 在本地主相机位置播放，
 * 若原版破坏音/粒子再播放一次会形成双份效果；此处直接吞掉 2001 事件。</p>
 *
 * <p>2. 播放与状态对齐的放置/破坏动画：服务端逐格 {@code setBlock}/{@code destroyBlock} 都会
 * 向客户端广播单格 {@link ClientboundBlockUpdatePacket}，最终在此 {@code handleBlockUpdate}
 * 处理。在 HEAD 处（此时 {@code level} 仍为变化前状态）把本次变化交给
 * {@link RtsEffectStateTracker#onBlockChanged} 匹配 RTS 预期动画并触发播放，
 * 保证线框下落/碎块上飘与实际方块状态变化严格同帧对齐。</p>
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    private static final int EVENT_BLOCK_BREAK = 2001;

    /** 目标类 {@link ClientPacketListener#getLevel()} 的 shadow，用于读取变化前的方块状态。 */
    @Shadow
    public abstract ClientLevel getLevel();

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

    /**
     * 在 {@code handleBlockUpdate} 头部记录本次方块状态变化（HEAD 处 {@code level} 仍是变化前状态），
     * 匹配 {@link RtsEffectStateTracker} 中登记的预期放置/破坏动画并触发播放。
     * 不依赖是否处于 RTS 界面：登记集合非空即有匹配，集合为空时开销可忽略。
     */
    @Inject(method = "handleBlockUpdate(Lnet/minecraft/network/protocol/game/ClientboundBlockUpdatePacket;)V",
            at = @At("HEAD"))
    private void rtsbuilding$trackBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        BlockPos pos = packet.getPos();
        BlockState oldState = this.getLevel().getBlockState(pos);
        BlockState newState = packet.getBlockState();
        RtsEffectStateTracker.onBlockChanged(pos, oldState, newState);
    }
}
