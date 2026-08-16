package com.rtsbuilding.rtsbuilding.server.workflow.model;

/**
 * 工作流生命周期状态（阶段四 4.2 状态机化）。
 *
 * <p>将 {@code RtsWorkflowEntry} 原本用 {@code suspended}/{@code paused} 两个布尔表达的
 * 隐式状态收敛为显式枚举。状态转换合法性见 {@link WorkflowStateMachine}。
 */
public enum WorkflowState {

    /** 槽位空闲，无工作流。 */
    IDLE,

    /** 运行中（正在处理方块）。 */
    RUNNING,

    /** 用户手动暂停（可继续）。 */
    PAUSED,

    /** 挂起（等待材料/工具，需恢复面板处理）。 */
    SUSPENDED,

    /** 全部方块处理完成。 */
    COMPLETED,

    /** 处理终止（失败/取消）。 */
    FAILED,
}
