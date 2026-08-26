package com.rtsbuilding.rtsbuilding.planetrise.server.powergrid;

import com.rtsbuilding.rtsbuilding.planetrise.EnergyMod;
import com.rtsbuilding.rtsbuilding.planetrise.network.PowerGridServerHandler;
import com.rtsbuilding.rtsbuilding.planetrise.power.PowerGridOwnership;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

/**
 * 电网多人系统<b>实时同步器</b>：服务端每 20 tick（约 1 秒）为每个<b>在线成员</b>推送其
 * 所属电网组的快照（发电机/塔/用电器的数据与状态实时更新）。
 * <p>
 * 只推送「组内在当前维度有节点」的玩家，避免对无电网的玩家空转；快照构建与推送复用
 * {@link PowerGridServerHandler}。由 {@code ServerTickEvent.Post} 驱动（仅服务端触发）。
 */
@EventBusSubscriber(modid = EnergyMod.MODID)
public final class PowerGridSync {

    private static final int SYNC_INTERVAL = 20;

    private PowerGridSync() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server == null || server.getTickCount() % SYNC_INTERVAL != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                UUID owner = PowerGridOwnership.get().ownerOf(player.getUUID());
                // 无组内节点的玩家不推送（空转）。
                if (com.rtsbuilding.rtsbuilding.planetrise.power.PowerGridManager.get(level)
                        .nodesOf(owner).isEmpty()) {
                    continue;
                }
                PowerGridServerHandler.pushPlayerSnapshot(player);
            }
        }
    }
}