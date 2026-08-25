package com.rtsbuilding.rtsbuilding.planetrise.power;

/**
 * 电力系统调度器的<b>输入节点视图</b>——一份与 Minecraft 解耦的纯数据快照，
 * 描述一个「可组网节点」（发电机器或输电塔）在某时刻的状态。
 * <p>
 * {@link PowerScheduler} 只依赖本类，便于单测；方块实体层（{@code PowerTowerBlockEntity}
 * 等）负责把它构造出来。坐标以三个 {@code long} 存储，避免依赖 {@code BlockPos}，
 * 使算法模块保持纯逻辑、可独立测试。
 */
public record GridNode(
        long x,
        long y,
        long z,
        PowerRole role,
        long linkRange,
        long powerRange,
        long throughput,
        long generation,
        long demand) {

    /** 位置编码为单一 long，供 union-find 索引 / 电网身份查询使用。 */
    public long key() {
        return (x * 0x9E3779B97F4A7C15L) ^ (y * 0xBF58476D1CE4E5B9L) ^ z;
    }

    /** 与另一节点的中心距离（平方，单位：格），用于链路范围判定。 */
    public long squaredDistance(GridNode other) {
        long dx = x - other.x();
        long dy = y - other.y();
        long dz = z - other.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
