package com.rtsbuilding.rtsbuilding.planetrise.power;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 电力系统调度核心算法（纯逻辑，参照 power_system_design.md）。
 * <p>
 * <b>电网 = 物理链路连通图</b>：可组网节点（发电机器 + 输电塔）之间按<b>链路范围</b>建立双向
 * 链路，互相连通的节点构成一个电网（union-find 物理分区）。能量只在<b>同一物理电网</b>内聚合
 * 与分配——物理隔离（超出彼此链路范围）的节点不共享电力，即使它们归属同一管辖组。
 * <p>
 * 本类对一个<b>输入节点集合</b>完成「组网分区 + 发电聚合」：
 * <ol>
 *   <li><b>物理分区</b>：按链路范围 union-find 把节点划分为若干物理电网（子集）；</li>
 *   <li><b>发电聚合</b>：每个物理电网内统计全部发电机器（{@code GENERATOR}）的本 tick 产电总量
 *       {@code totalGeneration}（含各塔从外部设备提取的 {@code externalGeneration}），供组级
 *       广播器使用。</li>
 * </ol>
 * <b>链路规则</b>（设计文档第五节 + 覆盖接入）：
 * <ul>
 *   <li>链路为<b>双向</b>连接，两节点各自链路范围取<b>最大值</b>作为判定阈值（距离 &le; 阈值才建立）；</li>
 *   <li><b>发电-发电不建立有效通路</b>——即使距离满足，电力也必须经过至少一个输电塔中转
 *       （因此 union-find 只在「至少一端是塔」的节点对间合并）；</li>
 *   <li><b>覆盖接入</b>：发电机器若处于某个输电塔的<b>供电范围（powerRange）</b>内，
 *       即使超出彼此链路范围，也视为与该塔连通并接入其所在物理电网（"被罩住即可供入"）。</li>
 *   <li>若既无链路又未被覆盖，节点与主电网断开，各自成为独立物理电网。</li>
 * </ul>
 * <p>
 * <b>覆盖融合统一供电</b>：分配不再按塔 1:1。电网内所有塔的供电范围融合成联合覆盖区，电网总发电
 * 对该区域内所有用电器<b>按需求占比统一供电</b>（多塔覆盖去重）。因此本类只负责<b>组网 + 发电聚合</b>，
 * 输出每组的 {@link GridAllocation}（含 {@code totalGeneration}）；联合需求去重与统一广播注入由
 * {@code PowerGridManager} 的组级广播器执行（它可访问各塔的用电器清单）。
 */
public final class PowerScheduler {

    /** 经一次输电塔转发的电力损失比例（本次设计为 0，即不衰减）。 */
    public static final double PER_HOP_LOSS = 0.0D;

    private PowerScheduler() {
    }

    /**
     * 对<b>输入节点集合</b>做一次完整调度：先按链路范围物理分区（union-find），再对每个物理电网
     * 聚合总发电量，输出每个物理电网的 {@link GridAllocation}（含 {@code totalGeneration}）。
     * <p>
     * 联合需求去重与统一广播由调用方（组级广播器）基于各塔的用电器清单完成，本类不涉及。
     *
     * @param nodes 输入节点列表（发电机器 + 输电塔）状态快照，下标即索引。
     * @return 每个<b>物理电网</b>的分配快照（按 find 根索引顺序，键为组根索引入参下标）。
     */
    public static Map<Integer, GridAllocation> allocateByGrid(List<GridNode> nodes) {
        int n = nodes.size();
        if (n == 0) {
            return Map.of();
        }

        // ---- 按物理链路范围 union-find 划分物理电网 ----
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            GridNode a = nodes.get(i);
            for (int j = i + 1; j < n; j++) {
                GridNode b = nodes.get(j);
                // 发电-发电不建立有效通路：至少一端是塔才可连（电力必须经塔中转）。
                if (a.role() == PowerRole.GENERATOR && b.role() == PowerRole.GENERATOR) {
                    continue;
                }
                if (samePhysicalGrid(a, b)) {
                    union(parent, i, j);
                }
            }
        }

        // ---- 每个物理电网：聚合总发电 + 记录组内节点下标 ----
        Map<Integer, GridAllocation> result = new HashMap<>();
        for (int i = 0; i < n; i++) {
            GridNode node = nodes.get(i);
            long g = node.role() == PowerRole.GENERATOR
                    ? node.generation()
                    : node.externalGeneration();
            int root = find(parent, i);
            GridAllocation alloc = result.get(root);
            if (alloc == null) {
                alloc = new GridAllocation(g, new ArrayList<>());
                result.put(root, alloc);
            } else {
                alloc = new GridAllocation(saturatingAdd(alloc.totalGeneration(), g), alloc.groupIndices());
            }
            alloc.groupIndices().add(i);
        }
        return result;
    }

    /** 网格分配快照：一个物理电网的聚合信息（供组级广播器分配）。 */
    public record GridAllocation(long totalGeneration, List<Integer> groupIndices) {
    }

    // ---- union-find（路径压缩 + 按秩合并）----

    /**
     * 判定两个可组网节点是否位于<b>同一物理电网</b>（即二者之间存在有效电力通路）：
     * <ol>
     *   <li><b>链路连接</b>：距离 &le; 两端各自链路范围的<b>最大值</b>（双向链路）；</li>
     *   <li><b>覆盖接入</b>：一端是输电塔（有供电范围）、另一端是发电机器，且发电机处于
     *       该塔的<b>供电范围（powerRange）</b>内——即使超出链路范围也视为连通。</li>
     * </ol>
     * 适用于「至少一端是塔」的节点对（发电-发电对由调用方提前排除）。
     */
    private static boolean samePhysicalGrid(GridNode a, GridNode b) {
        return sameNetwork(a.x(), a.y(), a.z(), a.role(), a.linkRange(), a.powerRange(),
                b.x(), b.y(), b.z(), b.role(), b.linkRange(), b.powerRange());
    }

    /**
     * <b>组网连通权威判定</b>（渲染 pass 与服务端<b>共用同一份规则</b>，单一事实来源，杜绝两端分歧）：
     * 服务端 {@link #samePhysicalGrid} 实际据此组网；客户端渲染连接虚线也调用本方法判定「可连/超范围」，
     * 保证<b>渲染所见即服务端所连</b>。规则：
     * <ul>
     *   <li>发电-发电不建立有效通路（至少一端是塔才可连）；</li>
     *   <li><b>链路连接</b>：距离平方 &le; max(两端链路范围)²；</li>
     *   <li><b>覆盖接入</b>：一端是塔（供电范围 &gt; 0）覆盖另一端发电机，距离平方 &le; 塔供电范围²。</li>
     * </ul>
     * 坐标用方块坐标（距离平方），等价「方块中心」距离（两端同 +0.5 相互抵消）。距离单位：格。
     */
    public static boolean sameNetwork(long ax, long ay, long az, PowerRole aRole, long aLink, long aPower,
                                      long bx, long by, long bz, PowerRole bRole, long bLink, long bPower) {
        if (aRole == PowerRole.GENERATOR && bRole == PowerRole.GENERATOR) {
            return false;
        }
        long dx = ax - bx, dy = ay - by, dz = az - bz;
        long distSq = dx * dx + dy * dy + dz * dz;
        long link = Math.max(aLink, bLink);
        if (distSq <= link * link) {
            return true;
        }
        return coveredByRange(aRole, aPower, bRole, distSq)
                || coveredByRange(bRole, bPower, aRole, distSq);
    }

    /** 塔（供电范围 &gt; 0）是否覆盖了另一端<b>发电机</b>。覆盖接入不适用于发电-发电（调用方已预排除）。 */
    private static boolean coveredByRange(PowerRole towerRole, long towerPower, PowerRole genRole, long distSq) {
        return towerRole == PowerRole.TOWER && towerPower > 0L
                && genRole == PowerRole.GENERATOR && distSq <= towerPower * towerPower;
    }

    /**
     * <b>供电范围覆盖权威判定</b>：塔（中心 {@code (cx,cy,cz)}，供电半径 {@code powerRange}）是否覆盖
     * 目标方块 {@code (tx,ty,tz)}——方块中心距离平方 &le; 供电范围²。供塔扫描（{@code isInSupplyRange}）
     * 与客户端供电范围圈渲染共用同一份规则，保证覆盖范围「所见即所得」。距离单位：格。
     */
    public static boolean isCoveredBySupplyRange(long tx, long ty, long tz,
                                                 long cx, long cy, long cz, long powerRange) {
        if (powerRange <= 0L) {
            return false;
        }
        long dx = tx - cx, dy = ty - cy, dz = tz - cz;
        return dx * dx + dy * dy + dz * dz <= powerRange * powerRange;
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
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

