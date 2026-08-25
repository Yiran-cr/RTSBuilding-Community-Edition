package com.rtsbuilding.rtsbuilding.planetrise.power;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link PowerScheduler} 电网组网/聚合/分配的纯逻辑测试（参照 power_system_design.md）。
 */
class PowerSchedulerTest {

    private static GridNode gen(long x, long z, long link, long power) {
        return new GridNode(x, 0, z, PowerRole.GENERATOR, link, 0, 0, power, 0);
    }

    private static GridNode tower(long x, long z, long link, long throughput, long demand) {
        return new GridNode(x, 0, z, PowerRole.TOWER, link, 10, throughput, 0, demand);
    }

    @Test
    void gridAllocatesFullDemandWhenGenerationSufficient() {
        // 1 发电机(100) + 1 塔(需求200)：电网内发电足额，塔得到配额=发电量(100)。
        List<GridNode> nodes = List.of(gen(0, 0, 32, 100), tower(10, 0, 32, 1000, 200));
        long[] quota = PowerScheduler.allocate(nodes);
        assertEquals(100, quota[1]);
    }

    @Test
    void gridCapsByGenerationWhenUnderSupply() {
        // 发电50 < 需求200：塔配额=发电量50（按需求占比100%，但总量扣顶）。
        List<GridNode> nodes = List.of(gen(0, 0, 32, 50), tower(10, 0, 32, 1000, 200));
        assertEquals(50, PowerScheduler.allocate(nodes)[1]);
    }

    @Test
    void throughputCapsAllocation() {
        // 发电1000；两塔各需求500、吞吐200。每塔配额 = min(1000*500/1000=500, 200) = 200。
        List<GridNode> nodes = List.of(
                gen(0, 0, 64, 1000),
                tower(20, 0, 32, 200, 500),
                tower(20, 40, 32, 200, 500));
        long[] quota = PowerScheduler.allocate(nodes);
        assertEquals(200, quota[1]);
        assertEquals(200, quota[2]);
    }

    @Test
    void demandProportionalAllocation() {
        // 发电300；两塔同网，需求占比 100:200 → 配额 100/200。
        List<GridNode> nodes = List.of(
                gen(0, 0, 64, 300),
                tower(20, 0, 32, 1000, 100),
                tower(20, 40, 32, 1000, 200));
        long[] quota = PowerScheduler.allocate(nodes);
        assertEquals(100, quota[1]);
        assertEquals(200, quota[2]);
    }

    @Test
    void generatorsNeverLinkToEachOther() {
        // 两个发电机器近距离但无输电塔：gen-gen 不建立供电链路，导致无电网、无配额。
        List<GridNode> nodes = List.of(gen(0, 0, 32, 100), gen(5, 0, 32, 100));
        long[] quota = PowerScheduler.allocate(nodes);
        assertEquals(0, quota[0]);
        assertEquals(0, quota[1]);
    }

    @Test
    void generatorKeptOutOfGridWithoutTower() {
        // 发电机在链路范围内但没有塔参与时，不构成能供电的电网 → 塔不存在故配额数组无意义。
        // 此处验证无塔时不会崩，且发电被忽略（无输出）。
        List<GridNode> nodes = List.of(gen(0, 0, 32, 100));
        long[] quota = PowerScheduler.allocate(nodes);
        assertEquals(0, quota[0]);
    }

    @Test
    void beyondLinkRangeDisconnected() {
        // 发电机与塔距离(50) > 两者链路范围较大值(32) → 断开：塔独立电网、无发电 → 配额0。
        List<GridNode> nodes = List.of(gen(0, 0, 32, 100), tower(50, 0, 32, 1000, 200));
        assertEquals(0, PowerScheduler.allocate(nodes)[1]);
    }

    @Test
    void towerRelayMultiHopAllocatedSharedGrid() {
        // 发电机(链接32) → 塔A(链接32) → 塔B(链接32)：经塔A中继，B与A同电网。
        // 发电120；A、B 需求各 60；无吞吐限制 → 各 60。
        List<GridNode> nodes = List.of(
                gen(0, 0, 32, 120),
                tower(10, 0, 32, 1000, 60),
                tower(30, 0, 32, 1000, 60));
        long[] quota = PowerScheduler.allocate(nodes);
        assertEquals(60, quota[1]);
        assertEquals(60, quota[2]);
    }
}
