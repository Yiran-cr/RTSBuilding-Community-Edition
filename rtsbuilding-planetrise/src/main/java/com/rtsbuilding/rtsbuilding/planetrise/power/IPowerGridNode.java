package com.rtsbuilding.rtsbuilding.planetrise.power;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 电网节点接口——由「可组网节点」的方块实体（发电机器 / 输电塔）实现。
 * <p>
 * 节点方块实体把自己的状态暴露给 {@link PowerGridManager}，manager 每 tick 读取
 * 这些状态、按 {@link PowerScheduler} 划分物理电网并聚合发电量；组级广播器据此对该电网
 * 内所有塔的<b>联合覆盖区</b>统一供电（覆盖融合，按用电器需求占比分配）。
 * <p>
 * 用电器<b>不</b>实现本接口（它们无半径、不参与组网），只作为输电塔供电范围内的
 * 标准能量接收方被被动发现。
 */
public interface IPowerGridNode {

    /** 本节点的角色（发电 / 输电）。 */
    PowerRole role();

    /** 本节点的链路范围（格）。参与物理电网组网（发电与塔之间）。 */
    long linkRange();

    /** 本节点的供电范围（格）。仅输电塔有意义，发电机返回 0。 */
    long powerRange();

    /** 输电塔的最大吞吐（FE/t）；发电机恒为 0。组级统一广播下仅作展示/兼容，不再逐塔限流。 */
    long throughput();

    /** 发电机本 tick 实际可注入电网的产电速率（FE/t）；输电塔恒为 0。 */
    long generation();

    /** 输电塔本 tick 供电范围内用电器需求速率（FE/t）；发电机恒为 0。 */
    long demand();

    /**
     * 输电塔供电范围内<b>发现的用电器坐标清单</b>（供组级广播器做联合覆盖去重）。
     * 发电机返回空列表。
     */
    default List<BlockPos> coveredConsumers() {
        return List.of();
    }

    /**
     * 输电塔在指定用电器位置提供的<b>可注入缺口</b>（FE），即该位置当前还能吸收多少电量；
     * 用于组级广播按需求占比分配。默认返回 0（不查询）。
     */
    default long consumerRoom(BlockPos pos) {
        return 0L;
    }

    /**
     * 组级广播器向指定用电器位置<b>注入</b>电量（覆盖融合统一供电）。实际由输电塔方块实体
     * 执行（复用其能力判定与注入逻辑），返回实际注入量（&le; 请求量）。默认返回 0（未处理）。
     *
     * @param pos    用电器位置。
     * @param amount 请求注入量（FE）。
     * @return 实际注入量（FE）。
     */
    default long injectTo(BlockPos pos, long amount) {
        return 0L;
    }

    /**
     * 输电塔最近一次广播<b>实际注入</b>供电范围内用电器总电量（FE/t）——真实耗电；
     * 发电机恒为 0。供快照展示。默认返回 0。
     */
    default long injectedRate() {
        return 0L;
    }

    /** 输电塔从供电范围内<b>外部发电机</b>（可提取设备）提取并注入电网的产电贡献（FE/t）；
     * 发电机恒为 0。并入电网总发电（{@code PowerScheduler} 聚合），并计入电网总览「总发电」。 */
    default long externalGeneration() {
        return 0L;
    }

    /** 本节点归属的电网所有者（放置者玩家 UUID）。null 表示尚未归属、不参与调度。 */
    @Nullable
    UUID gridOwner();

    /**
     * 重置本节点最近一次广播周期的注入/提取统计（{@code lastInjected}/{@code injectedByConsumer}/
     * {@code actualRoleByPos}）。由 {@code PowerGridManager} 在每次组级广播<b>开始时</b>对电网内各塔
     * 调用（先清后注入），使统计保留至下一次广播——避免「塔每 tick 清零 + 广播每心跳 tick 注入」的
     * 节拍不一致导致面板耗电常读 0。发电机无此项统计，默认空实现。
     */
    default void resetInjectionStats() {
    }

    /** 用电器在供电范围内发出的状态（供诊断/客户端，可选实现）。 */
    @Nullable
    default Object consumerStatus() {
        return null;
    }
}
