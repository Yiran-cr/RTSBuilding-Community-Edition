package com.rtsbuilding.rtsbuilding.planetrise.power;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 电力系统调度核心算法（HTTP 无关的纯逻辑，参照 power_system_design.md 第三、五节）。
 * <p>
 * 给定若干「可组网节点」（发电机器 / 输电塔）的状态快照，本类完成一次完整的电网
 * 组网 → 聚合 → 分配：
 * <ol>
 *   <li><b>组网</b>：以 union-find 构造连通图。两个节点之间是否存在链路，取决于
 *       两者距离是否小于等于「两者链路范围的较大值」；但<b>发电机器之间不建立有效
 *       供电链路</b>（即使距离满足），电力必须经过至少一个输电塔中转才能抵达用电器。</li>
 *   <li><b>聚合</b>：对每个连通图（电网）统计内部发电总量与各输电塔覆盖范围内的
 *       用电需求总量。</li>
 *   <li><b>分配</b>：电网总发电量按各输电塔的<b>需求占比</b>分配给每个输电塔，
 *       同时受限于各塔的<b>最大吞吐</b>上限；需求总和为零时每塔配额为零。</li>
 * </ol>
 * 电力经过输电塔转发<b>不衰减</b>（由{@code perHopLoss=0}体现）。本类只做数学分配，
 * 具体「广播供电」（塔向用电机器注能）由各输电塔方块实体自行执行。
 * <p>
 * 复杂度：节点总数 N 很小（电网规模），两两距离检查 O(N^2) 足够。
 */
public final class PowerScheduler {

    /** 每经一次输电塔转发的电力损失比例（本次设计为 0，即不衰减）。 */
    public static final double PER_HOP_LOSS = 0.0D;

    private PowerScheduler() {
    }

    /**
     * 据此输入计算每个节点的<b>本 tick 供电配额</b>。
     *
     * @param nodes 所有可组网节点（发电机器 + 输电塔）的状态快照，下标即索引。
     * @return 与 {@code nodes} 等长的一维数组；{@code result[i]} 为第 i 个节点本 tick
     *         分得的配额（发电机器恒为 0，只有输电塔非零）。
     */
    public static long[] allocate(List<GridNode> nodes) {
        int n = nodes.size();
        long[] quota = new long[n];

        // ---- 1. 组网（union-find）----
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            GridNode a = nodes.get(i);
            for (int j = i + 1; j < n; j++) {
                if (linkable(a, nodes.get(j))) {
                    union(parent, i, j);
                }
            }
        }

        // ---- 2/3. 每个电网：聚合 + 分配 ----
        Map<Integer, List<Integer>> grids = new HashMap<>();
        for (int i = 0; i < n; i++) {
            grids.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        }
        for (List<Integer> members : grids.values()) {
            long totalGeneration = 0L;
            long totalDemand = 0L;
            for (int m : members) {
                GridNode node = nodes.get(m);
                if (node.role() == PowerRole.GENERATOR) {
                    totalGeneration = saturatingAdd(totalGeneration, node.generation());
                } else {
                    totalDemand = saturatingAdd(totalDemand, node.demand());
                }
            }
            if (totalGeneration <= 0L || totalDemand <= 0L) {
                // 电网无发电或无需求：所有塔零配额。
                for (int m : members) {
                    if (nodes.get(m).role() == PowerRole.TOWER) {
                        quota[m] = 0L;
                    }
                }
                continue;
            }
            for (int m : members) {
                GridNode node = nodes.get(m);
                if (node.role() != PowerRole.TOWER) {
                    continue;
                }
                // 按需求占比分配，再受吞吐封顶。
                long share = (long) ((double) totalGeneration * node.demand() / totalDemand);
                long capped = Math.min(share, node.throughput());
                quota[m] = Math.max(0L, capped);
            }
        }
        return quota;
    }

    /**
     * 两个节点能否建立供电链路。
     * <p>
     * 发电机器之间<b>永不</b>建立有效供电链路（即使距离满足）；其余任意组合
     * （发电↔输电、输电↔输电）在距离小于等于「两者链路范围较大值」时建立。
     */
    private static boolean linkable(GridNode a, GridNode b) {
        if (a.role() == PowerRole.GENERATOR && b.role() == PowerRole.GENERATOR) {
            return false;
        }
        long threshold = Math.max(a.linkRange(), b.linkRange());
        if (threshold <= 0L) {
            return false;
        }
        return a.squaredDistance(b) <= threshold * threshold;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[rb] = ra;
        }
    }

    /** 饱和加法：累加溢出时钳制到 {@link Long#MAX_VALUE}。 */
    private static long saturatingAdd(long a, long b) {
        long sum = a + b;
        return sum < a ? Long.MAX_VALUE : sum;
    }
}
