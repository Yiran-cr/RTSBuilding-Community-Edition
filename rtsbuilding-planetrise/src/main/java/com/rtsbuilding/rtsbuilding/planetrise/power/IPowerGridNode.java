package com.rtsbuilding.rtsbuilding.planetrise.power;

import org.jetbrains.annotations.Nullable;

/**
 * 电网节点接口——由「可组网节点」的方块实体（发电机器 / 输电塔）实现。
 * <p>
 * 节点方块实体把自己的状态暴露给 {@link PowerGridManager}，manager 每 tick 读取
 * 这些状态、按 {@link PowerScheduler} 计算各输电塔的本 tick 供电配额，再写回。
 * <p>
 * 用电器<b>不</b>实现本接口（它们无半径、不参与组网），只作为输电塔供电范围内的
 * 标准能量接收方被被动发现。
 */
public interface IPowerGridNode {

    /** 本节点的角色（发电 / 输电）。 */
    PowerRole role();

    /** 本节点的链路范围（格）。两个节点取较大值作为组网判定阈值；发电机与塔均需此值。 */
    long linkRange();

    /** 本节点的供电范围（格）。仅输电塔有意义，发电机返回 0。 */
    long powerRange();

    /** 输电塔的最大吞吐（FE/t）；发电机恒为 0。 */
    long throughput();

    /** 发电机本 tick 实际可注入电网的产电速率（FE/t）；输电塔恒为 0。 */
    long generation();

    /** 输电塔本 tick 供电范围内用电器需求速率（FE/t）；发电机恒为 0。 */
    long demand();

    /**
     * 输电塔接受 manager 计算的<b>本 tick 供电配额</b>（FE/t）。发电机忽略。
     *
     * @param quota 本 tick 分得的配额（&ge; 0）。
     */
    void acceptQuota(long quota);

    /** 用电器在供电范围内发出的状态（供诊断/客户端，可选实现）。 */
    @Nullable
    default Object consumerStatus() {
        return null;
    }
}
