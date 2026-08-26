package com.rtsbuilding.rtsbuilding.planetrise.power;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 服务端<b>维度级电网调度器</b>——每个 {@link ServerLevel}（维度）一个实例。
 * <p>
 * 所有「可组网节点」的方块实体在创建时向本管理器 {@link #register}（携带其归属
 * {@link IPowerGridNode#gridOwner()}）、拆除时 {@link #unregister}。管理器由节点方块的
 * tick 惰性驱动（任意节点 tick 都会调用 {@link #tick(ServerLevel)}，内部用游戏时刻去重）。
 * <p>
 * 电网<b>按所有权分组</b>（电网多人系统：组 = 电网，组内全互通、无视距离）——节点按
 * {@link PowerGridOwnership#ownerOf(UUID)} 归类到其放置者玩家所属的电网组；<b>电网组与成员
 * 由全局 {@link PowerGridOwnership} 管理</b>（跨维度），本类只负责本维度节点的调度与聚合。
 * <p>
 * 每 tick 调度：把注册节点按「归属组」分组，对每组调用 {@link PowerScheduler#allocate(List)}
 * 聚合发电量与需求、按需求占比+吞吐封顶分配各输电塔配额，再 {@link IPowerGridNode#acceptQuota}
 * 写回。组间完全隔离；组内角色约束保留（发电机器只注入电网、输电塔广播供电）。
 * <p>
 * «广播供电»（塔把配额注入供电范围内用电器）由各输电塔方块实体在下一次 tick 执行。
 */
public final class PowerGridManager {

    /** 维度级管理器缓存：弱引用，维度卸载后自动回收。 */
    private static final Map<Level, PowerGridManager> INSTANCES = new WeakHashMap<>();

    /** 注册的全部节点（pos → 节点）。 */
    private final Map<BlockPos, IPowerGridNode> registered = new HashMap<>();

    private long lastScheduledGameTime = Long.MIN_VALUE;

    private PowerGridManager() {
    }

    /** 获取（或创建）某服务端维度对应的电网调度器。 */
    public static PowerGridManager get(Level level) {
        return INSTANCES.computeIfAbsent(level, k -> new PowerGridManager());
    }

    /** 节点方块实体创建/首次就绪时注册（幂等）；未归属节点只登记、待归属。 */
    public void register(BlockPos pos, IPowerGridNode node) {
        registered.put(pos.immutable(), node);
        if (node.gridOwner() != null) {
            PowerGridOwnership.get().ensureGrid(ownerOf(node.gridOwner()));
        }
    }

    /** 节点方块实体拆除时注销。 */
    public void unregister(BlockPos pos) {
        registered.remove(pos);
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

        // ---- 收集仍在位的节点，并按「归属组」分组 ----
        Map<UUID, List<IPowerGridNode>> grouped = new HashMap<>();
        List<BlockPos> stale = new ArrayList<>();
        for (Map.Entry<BlockPos, IPowerGridNode> e : registered.entrySet()) {
            BlockEntity be = level.getBlockEntity(e.getKey());
            if (be instanceof IPowerGridNode node && e.getValue() == node) {
                UUID owner = node.gridOwner();
                if (owner == null) {
                    continue;            // 未归属节点：暂不参与调度，等待放置归属绑定
                }
                UUID groupOwner = ownerOf(owner);
                grouped.computeIfAbsent(groupOwner, k -> new ArrayList<>()).add(node);
            } else {
                stale.add(e.getKey());
            }
        }
        if (!stale.isEmpty()) {
            for (BlockPos pos : stale) {
                registered.remove(pos);
            }
        }
        if (grouped.isEmpty()) {
            return;
        }

        // ---- 每组：聚合 + 分配 + 写回 ----
        for (Map.Entry<UUID, List<IPowerGridNode>> entry : grouped.entrySet()) {
            List<IPowerGridNode> nodes = entry.getValue();
            List<GridNode> views = new ArrayList<>(nodes.size());
            for (IPowerGridNode node : nodes) {
                if (node instanceof BlockEntity be) {
                    views.add(toView(node, be.getBlockPos()));
                }
            }
            long[] quota = PowerScheduler.allocate(views);
            for (int i = 0; i < nodes.size(); i++) {
                nodes.get(i).acceptQuota(quota[i]);
            }
        }
    }

    /** 某归属组（电网所有者）在本维度内的全部节点。 */
    public List<IPowerGridNode> nodesOf(UUID groupOwner) {
        List<IPowerGridNode> result = new ArrayList<>();
        for (IPowerGridNode node : registered.values()) {
            if (node.gridOwner() != null && groupOwner.equals(ownerOf(node.gridOwner()))) {
                result.add(node);
            }
        }
        return result;
    }

    /** 某归属组在本维度内的总发电 / 总<b>实际耗电</b>聚合。
     * <p>耗电项统计的是各输电塔最近一次广播<b>实际注入</b>的用电量（{@link IPowerGridNode#injectedRate()}），
     * 而非调度用的需求缺口（{@link IPowerGridNode#demand()}），供快照做「真实耗电」展示。
     * {@code [generation, actualDemand]}。 */
    public long[] aggregate(UUID groupOwner) {
        long generation = 0L;
        long demand = 0L;
        for (IPowerGridNode node : nodesOf(groupOwner)) {
            generation += node.generation();
            demand += node.injectedRate();
        }
        return new long[]{generation, demand};
    }

    /** 某玩家当前所属电网组所有者（委托全局所有权）。 */
    public UUID ownerOf(UUID player) {
        return PowerGridOwnership.get().ownerOf(player);
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
