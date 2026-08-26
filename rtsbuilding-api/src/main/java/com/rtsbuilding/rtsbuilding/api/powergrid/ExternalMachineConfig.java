package com.rtsbuilding.rtsbuilding.api.powergrid;

/**
 * 外部模组机器类型配置（内部表示，参照需求文档 4.4）。
 * <p>
 * {@code machineId} 形如 {@code "mod_id:machine_id"}。纯传输类型（Substation）不由玩家配置，
 * 仅模组内部硬编码；本结构只承载 Generator/Consumer 两类及其默认参数。
 *
 * @param machineId 机器标识 {@code mod_id:machine_id}。
 * @param type      类型（Generator / Consumer）。
 * @param powerValue 默认产电(Generator)/耗电(Consumer)值（FE/t，可选微调）。
 * @param linkRange 链路范围（发电机参与组网；组内互通时仅展示用途）。
 * @param priority  调度优先级（越大越优先，默认 0）。
 */
public record ExternalMachineConfig(
        String machineId,
        RtsMachineType type,
        double powerValue,
        double linkRange,
        int priority) {
}
