package com.rtsbuilding.rtsbuilding.server.service.beam;

import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsDroneBeamPayload;
import com.rtsbuilding.rtsbuilding.platform.Platform;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RTS 无人机建造/破坏光束广播服务。
 *
 * <p>当 RTS 模式玩家远程放置（建造）或破坏方块时，向<b>除该玩家外</b>的所有在线玩家广播
 * 一条光束：建造为蓝光（无人机摄像头 → 目标方块），破坏为红光（目标方块 → 无人机摄像头）。
 * 主控玩家收不到此包，因此看不到光束；其它玩家客户端通过无人机实体 ID 实时追踪端点。</p>
 *
 * <p>批量放置/破坏（蓝图、区域操作）会瞬间触发大量事件，故按 tick 对每个发起玩家限流，
 * 超限的光束直接丢弃，避免刷屏与网络拥塞（区域破坏每 tick 最多 64 格，不可能全部广播）。</p>
 */
public final class RtsDroneBeamService {

    /** 每 tick 每个发起玩家最多广播的光束数量。 */
    private static final int MAX_BEAMS_PER_PLAYER_PER_TICK = 4;

    /** 当前 tick 每个发起玩家已广播的光束计数（按发起玩家统计）。 */
    private static final Map<UUID, Integer> BEAMS_THIS_TICK = new HashMap<>();
    private static long RESET_TICK = -1L;

    private RtsDroneBeamService() {
    }

    /** 建造蓝光：无人机摄像头 → 目标方块（仅广播给其他玩家）。 */
    public static void broadcastPlace(ServerPlayer player, BlockPos pos) {
        broadcast(player, pos, true);
    }

    /** 破坏红光：目标方块 → 无人机摄像头（仅广播给其他玩家）。 */
    public static void broadcastBreak(ServerPlayer player, BlockPos pos) {
        broadcast(player, pos, false);
    }

    /**
     * 构造光束包并向除主控外的所有在线玩家广播。
     * <p>无人机不存在（RTS 未激活 / 实体缺失）或本 tick 已超限时静默丢弃。</p>
     */
    private static void broadcast(ServerPlayer player, BlockPos pos, boolean place) {
        MinecraftServer server = player == null ? null : player.getServer();
        if (server == null || pos == null) {
            return;
        }
        // 无人机未生成则无法确定"摄像头"端点
        Vec3 camera = RtsCameraManager.getDroneCameraPosition(player);
        int droneEntityId = RtsCameraManager.getDroneEntityId(player);
        if (camera == null || droneEntityId < 0) {
            return;
        }
        // 节流：批量放置/破坏时限制每 tick 广播次数，超出丢弃
        if (!acquireSlot(server, player)) {
            return;
        }

        S2CRtsDroneBeamPayload payload = new S2CRtsDroneBeamPayload(
                droneEntityId, pos.immutable(), place, camera.x, camera.y, camera.z);

        // 只发给其他玩家：主控自己看不到光束
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == player) {
                continue;
            }
            Platform.sendPacket(other, payload);
        }
    }

    /** 每 tick 每发起玩家限流一次广播额度；跨 tick 自动重置。 */
    private static boolean acquireSlot(MinecraftServer server, ServerPlayer player) {
        long tick = server.getTickCount();
        if (tick != RESET_TICK) {
            RESET_TICK = tick;
            BEAMS_THIS_TICK.clear();
        }
        int count = BEAMS_THIS_TICK.getOrDefault(player.getUUID(), 0);
        if (count >= MAX_BEAMS_PER_PLAYER_PER_TICK) {
            return false;
        }
        BEAMS_THIS_TICK.put(player.getUUID(), count + 1);
        return true;
    }
}
