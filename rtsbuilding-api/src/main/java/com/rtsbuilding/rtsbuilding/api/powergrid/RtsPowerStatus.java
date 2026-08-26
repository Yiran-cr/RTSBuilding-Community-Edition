package com.rtsbuilding.rtsbuilding.api.powergrid;

/**
 * 电网设备供电状态（UI 显示用）。用电机器状态由输电塔覆盖与电网供需共同判定。
 */
public enum RtsPowerStatus {

    /** 正常供电。 */
    POWERED,

    /** 未覆盖：不在任何输电塔供电范围内。 */
    UNCOVERED,

    /** 电力不足：被覆盖但电网总发电不足。 */
    UNDERSUPPLIED,

    /** 离线 / 不可用。 */
    OFFLINE
}
