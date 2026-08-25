package com.rtsbuilding.rtsbuilding.planetrise.power;

/**
 * 电力系统节点角色（参照 power_system_design.md）。
 * <p>
 * 系统只存在两类<b>可组网</b>的节点——发电机器与输电塔；用电机器不参与组网、
 * 只在输电塔的供电范围内被动受电。因此电网连通图只由 {@link #GENERATOR} 与
 * {@link #TOWER} 构成，且禁止「发电-发电」直连。
 */
public enum PowerRole {

    /** 发电机器：拥有链路范围，把电力注入电网；无供电范围，不能直接为用电器供电。 */
    GENERATOR,

    /** 输电塔：拥有链路范围 + 供电范围，是系统内唯一能向用电机器广播供电的建筑。 */
    TOWER
}
