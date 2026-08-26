package com.rtsbuilding.rtsbuilding.api.powergrid;

/**
 * 电网设备角色（供 UI 区分显示）。
 */
public enum RtsDeviceRole {

    /** 发电机器（向电网注入电量）。 */
    GENERATOR,

    /** 输电塔（唯一广播供电建筑）。 */
    TOWER,

    /** 用电机器 / 存储节点（被动接收电力）。 */
    CONSUMER
}
