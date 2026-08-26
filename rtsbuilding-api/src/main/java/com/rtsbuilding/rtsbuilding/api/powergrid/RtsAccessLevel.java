package com.rtsbuilding.rtsbuilding.api.powergrid;

/**
 * 电网成员访问等级（参照 Flux-Networks 的 AccessLevel 模型）。
 * <p>
 * 电网必须且只有一个所有者（OWNER）；OWNER / ADMIN 可编辑成员，ADMIN 不可转移所有权/不可移除
 * OWNER；USER 仅使用电力。被移除的玩家不再属于组；快照中还会包含<b>在线但未入组</b>的玩家
 * （标记为 BLOCKED=陌生人），供成员列表直观展示并一键邀请加入——邀请方式参照 Flux-Networks：
 * 点击「陌生人」行 → 弹窗点「设为用户」即完成邀请，无需输入玩家名。
 */
public enum RtsAccessLevel {

    /** 所有者：唯一，可转移所有权、可增删/升降级成员。 */
    OWNER,

    /** 管理员：可增删/降级 USER 成员，但不可转移所有权、不可操作 OWNER。 */
    ADMIN,

    /** 普通成员：仅使用电网电力。 */
    USER,

    /**
     * 陌生人（在线但未加入本电网组）：仅作为快照中的「可邀请」候选人展示（参照 Flux-Networks
     * AccessLevel.BLOCKED），canEdit/canTransfer 恒为 false，可被「设为用户（邀请加入）」。
     */
    BLOCKED;

    /** 能否编辑成员（增删/升降级）。 */
    public boolean canEdit() {
        return this == OWNER || this == ADMIN;
    }

    /** 能否执行转移所有权 / 升降 ADMIN（仅所有者）。 */
    public boolean canTransfer() {
        return this == OWNER;
    }
}
