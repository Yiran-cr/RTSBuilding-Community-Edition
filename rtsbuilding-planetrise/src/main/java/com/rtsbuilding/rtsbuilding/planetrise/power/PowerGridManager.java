package com.rtsbuilding.rtsbuilding.planetrise.power;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Collections;
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
 * <b>电网 = 物理链路连通图</b>：节点先按归属（{@link PowerGridOwnership#ownerOf(UUID)}，管辖区）
 * 收集，再在 {@link PowerScheduler#allocateByGrid} 内部按「链路连接 + 塔覆盖接入」union-find 划分出
 * 真正的<b>物理电网</b>；能量只在同一物理电网内共享。覆盖接入指发电机器处于某输电塔<b>供电范围</b>
 * 内即视为与该塔连通（"被罩住即可供入"），即使超出链路范围。管辖组（归属组）用于「电网多人」的
 * 成员/权限管理，物理链路用于实际供电路径——两层语义分离。
 * <p>
 * 每 tick 调度：把注册节点按「归属组」分组，对每组调用 {@link PowerScheduler#allocateByGrid(List)}
 * （内部按物理链路细分电网、逐电网聚合发电量），再对每个<b>物理电网</b>执行 <b>组级统一广播</b>
 * （{@link #broadcastToGrid}）：电网内所有塔的供电范围<b>融合</b>成联合覆盖区，用电网总发电按
 * 各用电器<b>需求占比</b>统一注入（覆盖重叠去重；忽略单塔吞吐）。物理隔离的节点不共享电力。
 * <p>
 * «广播供电»（联合覆盖区分发）由本管理器在调度 tick 内统一执行，不再由各输电塔独立广播。
 */
public final class PowerGridManager {

    /** 维度级管理器缓存：弱引用，维度卸载后自动回收。 */
    private static final Map<Level, PowerGridManager> INSTANCES = new WeakHashMap<>();

    /** 电网聚合调度的心跳间隔（tick）。即使无任何改动，也至少每隔这么多 tick 聚合一次，
     * 保证按最新 demand/generation 静默重算；节点增删/成员变更时通过脏标记立即唤醒一次。 */
    private static final int HEARTBEAT_INTERVAL = 5;

    /** 注册的全部节点（pos → 节点）。 */
    private final Map<BlockPos, IPowerGridNode> registered = new HashMap<>();

    /** 待聚合标志：节点增删时置位，下一 tick 立即执行一次调度（保证即时性，不随心跳等待）。
     * <p>成员变更（{@link PowerGridOwnership#changeMembership}）不在此置脏——它通过 {@code ownerOf()}
     * 已影响节点归类，随下个心跳（≤{@link #HEARTBEAT_INTERVAL} tick）自然重组。 */
    private boolean dirty;

    /** 最近一次聚合的游戏时刻（去重：同一 tick 内多个节点调用 tick 只跑一次）。 */
    private long lastScheduledGameTime = Long.MIN_VALUE;

    /** 最近一次聚合时按「归属组」分组的节点索引，供 {@link #nodesOf}/{@link #aggregate} 快速查询，
     * 避免每次调用都对全维度 registered 线性扫描（大型电网/多玩家时显著降低主线程负担）。 */
    private final Map<UUID, List<IPowerGridNode>> lastGrouped = new HashMap<>();

    private PowerGridManager() {
    }

    /** 获取（或创建）某服务端维度对应的电网调度器。 */
    public static PowerGridManager get(Level level) {
        return INSTANCES.computeIfAbsent(level, k -> new PowerGridManager());
    }

    /** 节点方块实体创建/首次就绪时注册（幂等）；未归属节点只登记、待归属。
     * <p>仅在<b>实际新增/替换节点</b>时置脏标记，避免 onServerTick 每 tick 重复 register
     * 让脏标记永不为假，从而绕过心跳（这是降频生效的关键前提）。 */
    public void register(BlockPos pos, IPowerGridNode node) {
        BlockPos immutable = pos.immutable();
        IPowerGridNode prev = registered.put(immutable, node);
        if (prev != node) {
            dirty = true;
        }
        if (node.gridOwner() != null) {
            PowerGridOwnership.get().ensureGrid(ownerOf(node.gridOwner()));
        }
    }

    /** 节点方块实体拆除时注销。 */
    public void unregister(BlockPos pos) {
        if (registered.remove(pos) != null) {
            dirty = true;
        }
    }

    /**
     * 惰性驱动的调度入口。任何节点方块每 tick 调用；本处用「心跳 + 脏标记」去重与降频：
     * <ul>
     *   <li>同一 tick 内仅第一个调用者执行（游戏时刻去重）；</li>
     *   <li>仅在<b>置脏</b>（节点增删）或<b>到达心跳时刻</b>时才执行一次完整聚合，其余帧仅 O(1) 判定后即返回；</li>
     * </ul>
     * 这样空闲电网从「每 tick 全量聚合」降到「每 {@link #HEARTBEAT_INTERVAL} tick 一次」，而设备增删仍即时响应。
     */
    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now == lastScheduledGameTime) {
            return;
        }
        if (!dirty && now % HEARTBEAT_INTERVAL != 0) {
            return;
        }
        lastScheduledGameTime = now;
        dirty = false;

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
        // ---- 缓存本次组索引（供 nodesOf / aggregate 直接读取，免全扫 registered）----
        lastGrouped.clear();
        lastGrouped.putAll(grouped);
        if (grouped.isEmpty()) {
            return;
        }

        // ---- 每组：按物理电网分区 → 组级统一广播（覆盖融合）----
        for (Map.Entry<UUID, List<IPowerGridNode>> entry : grouped.entrySet()) {
            List<IPowerGridNode> nodes = entry.getValue();
            List<GridNode> views = new ArrayList<>(nodes.size());
            Map<Integer, IPowerGridNode> byIndex = new HashMap<>();
            for (int i = 0; i < nodes.size(); i++) {
                IPowerGridNode node = nodes.get(i);
                if (node instanceof BlockEntity be) {
                    views.add(toView(node, be.getBlockPos()));
                    byIndex.put(i, node);
                }
            }
            // 每个物理电网独立聚合发电量，并对该电网的联合覆盖区统一广播（覆盖融合、按用电器需求占比）。
            Map<Integer, PowerScheduler.GridAllocation> allocs = PowerScheduler.allocateByGrid(views);
            for (Map.Entry<Integer, PowerScheduler.GridAllocation> e : allocs.entrySet()) {
                PowerScheduler.GridAllocation alloc = e.getValue();
                List<IPowerGridNode> gridNodes = new ArrayList<>(alloc.groupIndices().size());
                for (int idx : alloc.groupIndices()) {
                    IPowerGridNode node = byIndex.get(idx);
                    if (node != null) {
                        gridNodes.add(node);
                    }
                }
                broadcastToGrid(level, gridNodes, alloc.totalGeneration());
            }
        }
    }

    /**
     * <b>组级统一广播</b>：对一个物理电网的<b>联合覆盖区</b>统一供电（覆盖融合）。
     * <p>
     * 电网内所有塔的供电范围<b>融合</b>成联合区域；遍历组内全部塔的覆盖用电器 <b>按 BlockPos 去重</b>
     * （被多塔覆盖的用电器只算一次），统计联合需求（Σ {@code consumerRoom}），用电网总发电
     * {@code totalGeneration} 按<b>各用电器需求占比</b>分配注入（不足则按占比分配），
     * 每个用电器由覆盖它的任一塔执行实际注入。忽略单塔吞吐（组级统一供电语义）。
     */
    private static void broadcastToGrid(ServerLevel level, List<IPowerGridNode> gridNodes, long totalGeneration) {
        if (gridNodes.isEmpty()) {
            return;
        }
        // 新广播周期开始：先重置各塔上一周期的注入/提取统计（lastInjected/injectedByConsumer/
        // actualRoleByPos），随后本周期注入会重建这些值并保留至下次广播，供面板稳定读取。
        // 修正原「塔每 tick 清零 + 广播每心跳 tick 注入」节拍不一致导致面板耗电常读 0 的问题。
        // 注意：重置必须置于 totalGeneration 判定<b>之前</b>——若发电量降为 0（发电机燃料耗尽/被移除）
        // 或塔与发电源物理断开（自成无发电电网）时广播提前 return，塔的 lastInjected 仍残留上次广播值，
        // 面板上该塔「输电 X/tick」会虚高且状态误判为 POWERED。提前清零即可正确归零显示。
        for (IPowerGridNode node : gridNodes) {
            node.resetInjectionStats();
        }
        if (totalGeneration <= 0L) {
            return;
        }
        // 收集联合覆盖区用电器（去重）：BlockPos -> 需求缺口
        java.util.LinkedHashMap<BlockPos, Long> consumers = new java.util.LinkedHashMap<>();
        List<IPowerGridNode> towers = new ArrayList<>();
        for (IPowerGridNode node : gridNodes) {
            if (node.role() != PowerRole.TOWER) {
                continue;
            }
            towers.add(node);
            for (BlockPos pos : node.coveredConsumers()) {
                // 需求缺口：任一覆盖它的塔返回的 room（同塔内不重复；跨塔去重由内层 map 保证）
                long room = node.consumerRoom(pos);
                if (room > 0L && !consumers.containsKey(pos)) {
                    consumers.put(pos, room);
                }
            }
        }
        if (consumers.isEmpty() || towers.isEmpty()) {
            return;
        }
        long totalDemand = 0L;
        for (long room : consumers.values()) {
            totalDemand = saturatingAdd(totalDemand, room);
        }
        if (totalDemand <= 0L) {
            return;
        }
        // 按各用电器需求占比分配；足额则全满足，不足则按占比缩水。
        long remaining = totalGeneration;
        for (Map.Entry<BlockPos, Long> ce : consumers.entrySet()) {
            BlockPos pos = ce.getKey();
            long room = ce.getValue();
            long share = (long) ((double) totalGeneration * room / totalDemand);
            if (share <= 0L) {
                continue;
            }
            long toMove = Math.min(share, room);
            // 由组内任一覆盖该位置的塔执行注入（多塔覆盖时按网格顺序取第一个）。
            for (IPowerGridNode tower : towers) {
                if (tower.consumerRoom(pos) > 0L) {
                    long accepted = tower.injectTo(pos, toMove);
                    remaining = Math.max(0L, remaining - accepted);
                    break;
                }
            }
        }
        // 余数补发：占比向下取整损失的残余电量，按顺序再补一轮（尽量不浪费）。
        if (remaining > 0L) {
            for (Map.Entry<BlockPos, Long> ce : consumers.entrySet()) {
                if (remaining <= 0L) {
                    break;
                }
                BlockPos pos = ce.getKey();
                long room = ce.getValue();
                long toMove = Math.min(room, remaining);
                for (IPowerGridNode tower : towers) {
                    if (tower.consumerRoom(pos) > 0L) {
                        remaining = Math.max(0L, remaining - tower.injectTo(pos, toMove));
                        break;
                    }
                }
            }
        }
    }

    /** 某归属组（电网所有者）在本维度内的全部节点。
     * <p>读取最近一次聚合构建的组索引（最多滞后一个心跳周期，对 1 Hz 快照展示无感），
     * 由 O(全维度节点数) 降为 O(该组节点数)，大型电网/多玩家维度收益显著。 */
    public List<IPowerGridNode> nodesOf(UUID groupOwner) {
        List<IPowerGridNode> group = lastGrouped.get(groupOwner);
        return group == null ? Collections.emptyList() : Collections.unmodifiableList(group);
    }

    /** 某归属组在本维度内的总发电 / 总<b>实际耗电</b>聚合。
     * <p>发电项 = 组网节点本机产电（{@link IPowerGridNode#generation()}）<b>+</b> 各输电塔从
     * 供电范围内<b>外部发电机</b>提取并入电网的产电（{@link IPowerGridNode#externalGeneration()}，
     * 默认 0）。后者体现外部模组发电设备经塔中转的输电贡献——若不含，电网总览会漏计外部设备发电。
     * <p>耗电项统计的是各输电塔供电范围内用电器<b>实测每 tick 功耗</b>（{@link IPowerGridNode#injectedRate()}，
     * 按「注入 + 存量变化」反推，供电不足=降速后实际值），而非调度用的需求缺口（{@link IPowerGridNode#demand()}），
     * 供快照做「真实耗电」展示。{@code [generation, actualDemand]}。 */
    public long[] aggregate(UUID groupOwner) {
        long generation = 0L;
        long demand = 0L;
        for (IPowerGridNode node : nodesOf(groupOwner)) {
            generation += node.generation() + node.externalGeneration();
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
                node.demand(),
                node.externalGeneration());
    }

    /** 饱和加法：累加溢出时钳制到 {@link Long#MAX_VALUE}。 */
    private static long saturatingAdd(long a, long b) {
        long sum = a + b;
        return sum < a ? Long.MAX_VALUE : sum;
    }
}
