package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsPlaceAnimationPayload;
import com.rtsbuilding.rtsbuilding.platform.Platform;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.service.SoundService;
import com.rtsbuilding.rtsbuilding.server.service.beam.RtsDroneBeamService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RTS 远程方块放置的声音播放和动画效果。
 *
 * <p>此类负责在远程放置成功后向玩家发送听觉和视觉反馈。
 * 所有方法均为 {@code static}，类本身为不可实例化的工具类。
 *
 * <p><b>核心方法：</b>
 * <ul>
 *   <li>{@link #playRemotePlacedBlockAnimation(ServerPlayer, BlockPos)} —
 *       发送方块放置动画数据包（{@link S2CRtsPlaceAnimationPayload}）给玩家</li>
 *   <li>{@link #playRemotePlacedBlockSound(ServerPlayer, ServerLevel, BlockPos)} —
 *       播放远程放置方块的放置声音，每 tick 每玩家限流 {@value #MAX_SOUNDS_PER_TICK} 次</li>
 * </ul>
 *
 * <p><b>破坏音效说明：</b>破坏音由客户端 {@code RtsClientNetworkHandlers.handleBreakAnimation}
 * 在本地主相机位置播放（音源=听者，无跨端坐标依赖），不经过本类。</p>
 *
 * <p><b>限流机制：</b>使用静态映射 {@link #PER_PLAYER_SOUNDS_THIS_TICK} 跟踪
 * 每个游戏 tick 每玩家的放置声音次数，超过 {@value #MAX_SOUNDS_PER_TICK} 次后静音，
 * 避免大批量放置时产生噪音干扰。
 *
 * <p><b>声音定位：</b>声音通过 {@link SoundService#sendDirectSound} 在相机位置
 * （若玩家处于远程相机模式）或玩家眼睛位置播放，确保沉浸式听觉体验。
 *
 * <p><b>设计原则：</b>此类故意不执行放置、物品提取或批处理作业管理，
 * 这些职责位于放置包的其它类中。
 */
public final class RtsPlacementSound {

    private static final int MAX_SOUNDS_PER_TICK = 1;
    private static final Map<UUID, Integer> PER_PLAYER_SOUNDS_THIS_TICK = new HashMap<>();
    private static long SOUND_RESET_TICK = -1L;

    private RtsPlacementSound() {
    }

    /**
     * 向玩家发送给定位置的方块放置动画数据包。
     * <p>同时广播一条建造蓝光（无人机摄像头 → 目标方块）给其他玩家。</p>
     */
    public static void playRemotePlacedBlockAnimation(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return;
        }
        BlockState state = player.serverLevel().getBlockState(pos);
        Platform.sendPacket(player, new S2CRtsPlaceAnimationPayload(pos.immutable(), state));
        // 建造光束：只对其他玩家可见（主控不接收），两端追踪方块位置与无人机摄像头位置
        RtsDroneBeamService.broadcastPlace(player, pos);
    }

    /**
     * 播放远程放置方块的位置声音。
     * <p>
     * 声音被限制为每个游戏 tick 每个玩家最多播放 {@link #MAX_SOUNDS_PER_TICK} 次，
     * 以避免大批量放置时产生噪音。
     */
    public static void playRemotePlacedBlockSound(ServerPlayer player, ServerLevel level,
                                                   BlockPos pos) {
        if (player == null || level == null || pos == null || !level.hasChunkAt(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        if (!tryAcquireSoundSlot(player, level)) {
            return;
        }
        SoundType soundType = state.getSoundType(level, pos, player);
        Vec3 soundPos = cameraOrEyePos(player);
        SoundService.sendDirectSound(
                player,
                soundType.getPlaceSound(),
                SoundSource.BLOCKS,
                soundPos.x,
                soundPos.y,
                soundPos.z,
                (soundType.getVolume() + 1.0F) / 2.0F,
                soundType.getPitch() * 0.8F);
    }

    /**
     * 尝试为本玩家本 tick 获取一次放置声音播放额度。
     * <p>每 tick 每玩家最多 {@link #MAX_SOUNDS_PER_TICK} 次，
     * 跨 tick 自动重置，超量静音。返回 {@code false} 表示本 tick 已用满配额，应跳过播放。</p>
     */
    private static boolean tryAcquireSoundSlot(ServerPlayer player, ServerLevel level) {
        long currentTick = level.getGameTime();
        if (currentTick != SOUND_RESET_TICK) {
            SOUND_RESET_TICK = currentTick;
            PER_PLAYER_SOUNDS_THIS_TICK.clear();
        }
        int count = PER_PLAYER_SOUNDS_THIS_TICK.getOrDefault(player.getUUID(), 0);
        if (count >= MAX_SOUNDS_PER_TICK) {
            return false;
        }
        PER_PLAYER_SOUNDS_THIS_TICK.put(player.getUUID(), count + 1);
        return true;
    }

    private static Vec3 cameraOrEyePos(ServerPlayer player) {
        Vec3 pos = RtsCameraManager.getCameraPosition(player);
        return pos != null ? pos : player.getEyePosition();
    }
}
