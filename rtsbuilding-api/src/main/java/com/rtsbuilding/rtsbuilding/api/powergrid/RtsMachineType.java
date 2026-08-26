package com.rtsbuilding.rtsbuilding.api.powergrid;

/**
 * 外部模组机器类型（玩家可指定）。仅允许 General/Consumer 二选一；纯传输（Substation）
 * 为模组内部硬编码、不开放给玩家配置。
 */
public enum RtsMachineType {

    /** 标记为输电塔/发电接口：注入电网。 */
    GENERATOR,

    /** 标记为用电器：被动接收电力。 */
    CONSUMER
}
