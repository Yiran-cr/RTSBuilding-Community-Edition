package com.rtsbuilding.rtsbuilding.planetrise.power;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link PowerScheduler} 电网组聚合/分配的纯逻辑测试
 * （电网多人系统：组 = 电网，组内全互通；保留发电经塔中转的角色约束）。
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
        // 组内 1 发电机(100) + 1 塔(需求200)：总发电足额，塔得到配额=发电量(100)。
        List<GridNode> nodes = List.of(gen(0, 0, 32, 100), tower(10, 0, 32, 1000, 200));
        assertEquals(100, PowerScheduler.allocate(nodes)[1]);
    }

    @Test
    void gridCapsByGenerationWhenUnderSupply() {
        // 组内发电50 < 需求200：塔配额=发电量50。
        List<GridNode> nodes = List.of(gen(0, 0, 32, 50), tower(10, 0, 32, 1000, 200));
        assertEquals(50, PowerScheduler.allocate(nodes)[1]);
    }

    @Test
    void throughputCapsAllocation() {
        // 组内发电1000；两塔各需求500、吞吐200。每塔配额 = min(500, 200) = 200。
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
        // 组内发电300；两塔需求占比 100:200 → 配额 100/200。
        List<GridNode> nodes = List.of(
                gen(0, 0, 64, 300),
                tower(20, 0, 32, 1000, 100),
                tower(20, 40, 32, 1000, 200));
        long[] quota = PowerScheduler.allocate(nodes);
        assertEquals(100, quota[1]);
        assertEquals(200, quota[2]);
    }

    @Test
    void ignoringDistanceSameGridStillShares() {
        // 组内全互通：即便发电机与塔距离远超链路范围，仍同组供电（组=电网，无视距离）。
        List<GridNode> nodes = List.of(gen(0, 0, 32, 150), tower(5000, 0, 32, 1000, 300));
        assertEquals(150, PowerScheduler.allocate(nodes)[1]);
    }

    @Test
    void generatorsNeverFeedConsumersDirectly() {
        // 只有发电机器、无输电塔时：无塔可派发 → 组内没有任何配额（发电机不直连用电器）。
        List<GridNode> nodes = List.of(gen(0, 0, 32, 100), gen(5, 0, 32, 100));
        long[] quota = PowerScheduler.allocate(nodes);
        assertEquals(0, quota[0]);
        assertEquals(0, quota[1]);
    }

    @Test
    void noDemandYieldsZeroQuota() {
        // 组内发电充足但无塔/无需求 → 所有配额为零。
        List<GridNode> nodes = List.of(gen(0, 0, 32, 100));
        long[] quota = PowerScheduler.allocate(nodes);
        assertEquals(0, quota[0]);
    }

    @Test
    void towerRelayWithinSameGridShared() {
        // 同组：发电机 → 塔A(中继) → 塔B；两塔同属一组，发电120 → 各 60。
        List<GridNode> nodes = List.of(
                gen(0, 0, 32, 120),
                tower(10, 0, 32, 1000, 60),
                tower(30, 0, 32, 1000, 60));
        long[] quota = PowerScheduler.allocate(nodes);
        assertEquals(60, quota[1]);
        assertEquals(60, quota[2]);
    }
}
