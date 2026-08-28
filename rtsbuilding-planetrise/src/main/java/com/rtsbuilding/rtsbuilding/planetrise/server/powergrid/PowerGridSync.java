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
import java.util.concurrent.atomic.AtomicLong;

/**
 * 电网多人系统<b>实时同步器</b>：服务端每 20 tick（约 1 秒）为每个<b>在线成员</b>推送其
 * 所属电网组的快照（发电机/塔/用电器的数据与状态实时更新）。
 * <p>
 * 只推送「组内在当前维度有节点」的玩家，避免对无电网的玩家空转；快照构建与推送复用
 * {@link PowerGridServerHandler}。由 {@code ServerTickEvent.Post} 驱动（仅服务端触发）。
 * <p>
 * <b>调度心跳兜底</b>：{@link PowerGridManager#tick} 原本<b>纯靠节点方块实体每 tick 惰性驱动</b>。
 * 当维度内电网的<b>最后一个节点被移除</b>后，没有节点再调用 {@code tick()}，导致 {@code lastGrouped}
 * 缓存永久残留被移除的节点（被挖掉的输电塔），快照读到它 → 塔基位置被渲染成「空气」、且塔的
 * {@code actualTransferredRate()} 残留最后一次广播值 → 面板上看似该塔仍在「输电 X/tick」。本方法在
 * <b>每个 tick</b> 对所有维度兜底驱动一次调度：即使电网已无节点，也能及时清理残留并重建组索引，
 * 使快照真实反映节点移除。仅服务端生效；与节点 tick 同 tick 调用时由 {@code tick()} 内部游戏时刻
 * 去重，不会重复执行（成本 O(1)）。
 */
@EventBusSubscriber(modid = EnergyMod.MODID)
public final class PowerGridSync {

    private static final int SYNC_INTERVAL = 20;
    /** 用于历史采样的服务端 tick 计数（与 {@link ServerTickEvent} 同步累加）。 */
    private static final AtomicLong HISTORY_TICK = new AtomicLong(-1L);

    private PowerGridSync() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server == null) {
            return;
        }
        // 每 tick 兜底驱动一次所有维度的电网调度心跳（详见类注释：防止最后一个节点被移除后
        // lastGrouped 残留）。该调用置于同步间隔判断之前，确保节点增删的即时性不依赖快照周期。
        for (ServerLevel level : server.getAllLevels()) {
            com.rtsbuilding.rtsbuilding.planetrise.power.PowerGridManager.get(level).tick(level);
        }
        if (server.getTickCount() % SYNC_INTERVAL != 0) {
            return;
        }
        long tick = HISTORY_TICK.incrementAndGet();
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                UUID owner = PowerGridOwnership.get().ownerOf(player.getUUID());
                // 无组内节点的玩家不推送（空转）。
                var manager = com.rtsbuilding.rtsbuilding.planetrise.power.PowerGridManager.get(level);
                if (manager.nodesOf(owner).isEmpty()) {
                    continue;
                }
                // 历史采样：每秒评估是否到各档采样点（5s/1m/1h），仅在到点档位写入。
                // 注：每秒调用一次 aggregate 是 O(节点数) 线性扫描，对节点数有限的电网组开销可忽略。
                long[] agg = manager.aggregate(owner);
                PowerGridHistoryStore.get().sampleIfNeeded(owner, agg[0], agg[1], tick);
                PowerGridServerHandler.pushPlayerSnapshot(player);
            }
        }
    }
}