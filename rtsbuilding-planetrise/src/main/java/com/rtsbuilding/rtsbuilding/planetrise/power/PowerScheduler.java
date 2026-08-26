package com.rtsbuilding.rtsbuilding.planetrise.power;

import java.util.List;

/**
 * 电力系统调度核心算法（纯逻辑，参照 power_system_design.md + 电网多人系统：组=电网、
 * 组内全互通）。
 * <p>
 * 输入一律视为<b>同一个电网组</b>的所有节点列表（组成员间节点无条件互通、无视距离，
 * 因此不再按物理链路范围做 union-find 子图划分）。本类对这一个组完成一次完整调度：
 * <ol>
 *   <li><b>聚合</b>：统计组内全部发电机器（{@code GENERATOR}）的本 tick 产电总量
 *       {@code totalGeneration}，以及全部输电塔（{@code TOWER}）供电范围内用电器需求总量
 *       {@code totalDemand}；</li>
 *   <li><b>分配</b>：总发电量按各输电塔的<b>需求占比</b>分配，同时受限于各塔的<b>最大吞吐</b>
 *       上限；需求总和为零时每塔配额为零。</li>
 * </ol>
 * 仍保留<b>角色约束</b>：发电机器只把电量注入电网（不能直接为用电器供电），任意用电器获得
 * 电力的唯一方式是处于某个输电塔的供电范围内，而该输电塔又从本组电网分得电量——发电机器与
 * 用电机器之间不存在直连供电路径。电力经输电塔转发<b>不衰减</b>。
 * <p>
 * 本类只做数学分配；「广播供电」（塔把配额注入供电范围内用电器）由各输电塔方块实体自行执行。
 */
public final class PowerScheduler {

    /** 经一次输电塔转发的电力损失比例（本次设计为 0，即不衰减）。 */
    public static final double PER_HOP_LOSS = 0.0D;

    private PowerScheduler() {
    }

    /**
     * 对<b>单个电网组</b>内所有可组网节点做一次调度。
     *
     * @param nodes 同一个电网组内的全部节点（发电机器 + 输电塔）状态快照，下标即索引。
     * @return 与 {@code nodes} 等长的一维数组；{@code result[i]} 为第 i 个节点本 tick
     *         分得的供电配额（发电机器恒为 0，只有输电塔非零）。
     */
    public static long[] allocate(List<GridNode> nodes) {
        int n = nodes.size();
        long[] quota = new long[n];

        long totalGeneration = 0L;
        long totalDemand = 0L;
        for (GridNode node : nodes) {
            if (node.role() == PowerRole.GENERATOR) {
                totalGeneration = saturatingAdd(totalGeneration, node.generation());
            } else {
                totalDemand = saturatingAdd(totalDemand, node.demand());
            }
        }

        if (totalGeneration <= 0L || totalDemand <= 0L) {
            // 组内无发电或无需求：所有塔零配额（发电机同样为 0）。
            return quota;
        }
        for (int i = 0; i < n; i++) {
            GridNode node = nodes.get(i);
            if (node.role() != PowerRole.TOWER) {
                continue;
            }
            // 按需求占比分配，再受吞吐封顶。
            long share = (long) ((double) totalGeneration * node.demand() / totalDemand);
            long capped = Math.min(share, node.throughput());
            quota[i] = Math.max(0L, capped);
        }
        return quota;
    }

    /** 饱和加法：累加溢出时钳制到 {@link Long#MAX_VALUE}。 */
    private static long saturatingAdd(long a, long b) {
        long sum = a + b;
        return sum < a ? Long.MAX_VALUE : sum;
    }
}
