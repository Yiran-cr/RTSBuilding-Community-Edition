package com.rtsbuilding.rtsbuilding.planetrise.power;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 服务端<b>电网调度器</b>——每个 {@link ServerLevel}（维度）一个实例。
 * <p>
 * 所有「可组网节点」的方块实体，在创建时向本管理器 {@link #register}、拆除时
 * {@link #unregister}。管理器由节点方块的 tick 惰性驱动（任意节点 tick 都会调用
 * {@link #tick(ServerLevel)}，内部用游戏时刻去重保证每 tick 至多调度一次）。
 * <p>
 * 每 tick 调度流程：
 * <ol>
 *   <li>遍历已注册坐标，从世界重新取回方块实体，过滤出仍在位的 {@link IPowerGridNode}；</li>
 *   <li>把每个节点的状态快照成 {@link GridNode} 列表；</li>
 *   <li>调用 {@link PowerScheduler#allocate(List)} 计算各输电塔配额；</li>
 *   <li>把配额写回各输电塔（{@link IPowerGridNode#acceptQuota}）。</li>
 * </ol>
 * 「广播供电」（塔把配额注入供电范围内用电器）由各输电塔方块实体在下一次 tick 执行，
 * 使用的即上一 tick 分得的配额。因此组网结果最多延迟 1 tick，可接受。
 * <p>
 * 调度只建模<b>组网与分配</b>；输电塔供电范围内的用电器发现与注能由塔自己负责。
 */
public final class PowerGridManager {

    /** 维度级管理器缓存：弱引用，维度卸载后自动回收。 */
    private static final Map<Level, PowerGridManager> INSTANCES = new WeakHashMap<>();

    private final Map<BlockPos, IPowerGridNode> pending = new java.util.HashMap<>();
    private long lastScheduledGameTime = Long.MIN_VALUE;

    private PowerGridManager() {
    }

    /** 获取（或创建）某服务端维度对应的电网调度器。 */
    public static PowerGridManager get(Level level) {
        return INSTANCES.computeIfAbsent(level, k -> new PowerGridManager());
    }

    /** 节点方块实体创建/首次就绪时注册（幂等）。 */
    public void register(BlockPos pos, IPowerGridNode node) {
        pending.put(pos.immutable(), node);
    }

    /** 节点方块实体拆除时注销。 */
    public void unregister(BlockPos pos) {
        pending.remove(pos);
    }

    /**
     * 惰性驱动的调度入口。任何节点方块每 tick 调用；本处用游戏时刻去重，
     * 保证每 tick 只跑一次全部组网。
     */
    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now == lastScheduledGameTime) {
            return;
        }
        lastScheduledGameTime = now;

        // ---- 收集仍在位的节点 ----
        List<IPowerGridNode> nodes = new ArrayList<>();
        List<GridNode> views = new ArrayList<>();
        List<BlockPos> stale = new ArrayList<>();
        for (BlockPos pos : pending.keySet()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof IPowerGridNode node) {
                nodes.add(node);
                views.add(toView(node, pos));
            } else {
                stale.add(pos);
            }
        }
        if (!stale.isEmpty()) {
            for (BlockPos pos : stale) {
                pending.remove(pos);
            }
        }
        if (views.isEmpty()) {
            return;
        }

        // ---- 组网 + 分配 ----
        long[] quota = PowerScheduler.allocate(views);
        for (int i = 0; i < nodes.size(); i++) {
            nodes.get(i).acceptQuota(quota[i]);
        }
    }

    /** 把节点方块实体的实时状态快照成算法输入。 */
    private static GridNode toView(IPowerGridNode node, BlockPos pos) {
        return new GridNode(
                pos.getX(), pos.getY(), pos.getZ(),
                node.role(),
                node.linkRange(),
                node.powerRange(),
                node.throughput(),
                node.generation(),
                node.demand());
    }
}
