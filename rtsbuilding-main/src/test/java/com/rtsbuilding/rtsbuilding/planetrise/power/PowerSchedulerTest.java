package com.rtsbuilding.rtsbuilding.planetrise.power;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PowerScheduler#allocateByGrid} 电网<b>物理分区</b>的纯逻辑测试。
 * <p>
 * 电网 = <b>物理链路连通图</b>：节点按「链路连接 + 塔覆盖接入」union-find 分区，输出每个物理电网
 * 的 {@link PowerScheduler.GridAllocation}（{@code totalGeneration} + 组内节点下标）。
 * 覆盖融合的统一广播分配在 {@link PowerGridManager}（集成层），此处仅验证<b>组网正确性</b>：
 * 链路阈值取两端最大值、覆盖接入、发电-发电不直连、断开即独立电网、外部发电上报聚合。
 */
class PowerSchedulerTest {

    private static GridNode gen(long x, long z, long link, long power) {
        return new GridNode(x, 0, z, PowerRole.GENERATOR, link, 0, 0, power, 0, 0);
    }

    private static GridNode tower(long x, long z, long link, long throughput) {
        return new GridNode(x, 0, z, PowerRole.TOWER, link, 10, throughput, 0, 0, 0);
    }

    private static GridNode towerWithRange(long x, long z, long link, long powerRange, long throughput) {
        return new GridNode(x, 0, z, PowerRole.TOWER, link, powerRange, throughput, 0, 0, 0);
    }

    private static GridNode towerExt(long x, long z, long link, long powerRange, long externalGen) {
        return new GridNode(x, 0, z, PowerRole.TOWER, link, powerRange, 1000, 0, 0, externalGen);
    }

    /** 校验：把输入节点下标归属到其在各物理电网中的 {@code totalGeneration} 汇总。 */
    private static Map<Integer, PowerScheduler.GridAllocation> alloc(List<GridNode> nodes) {
        return PowerScheduler.allocateByGrid(nodes);
    }

    @Test
    void generatorAndTowerWithinLinkShareOneGrid() {
        // 发电机与塔距离 10 ≤ max link=32 → 同一物理电网；总发电=100。
        List<GridNode> nodes = List.of(gen(0, 0, 32, 100), tower(10, 0, 32, 1000));
        Map<Integer, PowerScheduler.GridAllocation> allocs = alloc(nodes);
        assertEquals(1, allocs.size());
        for (PowerScheduler.GridAllocation a : allocs.values()) {
            assertEquals(100, a.totalGeneration());
            assertEquals(2, a.groupIndices().size());
        }
    }

    @Test
    void physicallyIsolatedNodeSeparateGrid() {
        // 发电机与塔距离 5000 > max link=32 → 两个独立物理电网。
        List<GridNode> nodes = List.of(gen(0, 0, 32, 150), tower(5000, 0, 32, 1000));
        assertEquals(2, alloc(nodes).size());
    }

    @Test
    void linkThresholdUsesMaxOfBothSides() {
        // 链路阈值取两端最大：发电机 link=64、塔 link=32，距离 40 ≤ 64 → 同一电网。
        List<GridNode> nodes = List.of(gen(0, 0, 64, 150), tower(0, 40, 32, 1000));
        Map<Integer, PowerScheduler.GridAllocation> allocs = alloc(nodes);
        assertEquals(1, allocs.size());
        for (PowerScheduler.GridAllocation a : allocs.values()) {
            assertEquals(150, a.totalGeneration());
        }
    }

    @Test
    void generatorsNeverLinkDirectlyToEachOther() {
        // 发电-发电不建有效通路：两台发电机即使距离满足，也不并入同一电网（各自节点）。
        List<GridNode> nodes = List.of(gen(0, 0, 32, 100), gen(10, 0, 32, 100));
        assertEquals(2, alloc(nodes).size());
    }

    @Test
    void generatorsNeverFeedConsumersDirectly() {
        // 仅两台发电机、无塔：各自独立电网，每组无塔可派发 → 无电网内供电（分区独立即可）。
        List<GridNode> nodes = List.of(gen(0, 0, 32, 100), gen(5, 0, 32, 100));
        assertEquals(2, alloc(nodes).size());
    }

    @Test
    void brokenLinkSplitsIntoIndependentGrids() {
        // 发电机(0,0) 连 塔1(10,0)；塔2(200,0) 超距断开 → 两个物理电网。
        List<GridNode> nodes = List.of(
                gen(0, 0, 32, 120),
                tower(10, 0, 32, 1000),
                tower(200, 0, 32, 1000));
        assertEquals(2, alloc(nodes).size());
    }

    @Test
    void generatorCoveredByTowerRangeFeedsGrid() {
        // 覆盖接入：发电机距离 40 > max link=32，但处于塔供电范围(powerRange=50)内 → 同一电网。
        List<GridNode> nodes = List.of(gen(0, 0, 32, 150), towerWithRange(0, 40, 32, 50, 1000));
        Map<Integer, PowerScheduler.GridAllocation> allocs = alloc(nodes);
        assertEquals(1, allocs.size());
        assertTrue(allocs.values().iterator().next().totalGeneration() == 150);
    }

    @Test
    void generatorOutsideTowerRangeAndLinkStaysIsolated() {
        // 发电机既超链路范围又超出塔供电范围 → 两个物理电网。
        List<GridNode> nodes = List.of(gen(0, 0, 32, 150), towerWithRange(0, 60, 32, 50, 1000));
        assertEquals(2, alloc(nodes).size());
    }

    @Test
    void towerRelayFormsOneGrid() {
        // 发电机(0,0) → 塔A(10,0) → 塔B(30,0)，两塔距离 20 ≤ 32 互相连通，全部并入同一电网。
        List<GridNode> nodes = List.of(
                gen(0, 0, 32, 120),
                tower(10, 0, 32, 1000),
                tower(30, 0, 32, 1000));
        Map<Integer, PowerScheduler.GridAllocation> allocs = alloc(nodes);
        assertEquals(1, allocs.size());
        for (PowerScheduler.GridAllocation a : allocs.values()) {
            assertEquals(120, a.totalGeneration());
            assertEquals(3, a.groupIndices().size());
        }
    }

    @Test
    void externalGeneratorGenerationAggregated() {
        // 塔A 提取外部电 120，与塔B 链路连通 → 同一电网，总发电=120（外部电上报）。
        List<GridNode> nodes = List.of(
                towerExt(0, 0, 32, 10, 120),
                tower(30, 0, 32, 1000));
        Map<Integer, PowerScheduler.GridAllocation> allocs = alloc(nodes);
        assertEquals(1, allocs.size());
        for (PowerScheduler.GridAllocation a : allocs.values()) {
            assertEquals(120, a.totalGeneration());
        }
    }
}
