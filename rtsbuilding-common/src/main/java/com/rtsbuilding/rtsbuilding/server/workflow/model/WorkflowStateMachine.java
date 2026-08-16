package com.rtsbuilding.rtsbuilding.server.workflow.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 工作流状态机（阶段四 4.2）。
 *
 * <p>定义 {@link WorkflowState} 之间的合法转换关系，并提供从现有布尔标志
 * （occupied/suspended/paused/complete/failed）推导状态的映射。
 * 纯逻辑、无外部依赖，可单测。
 */
public final class WorkflowStateMachine {

    private WorkflowStateMachine() {}

    /**
     * 合法状态转换表（白名单）。
     * 允许的转换：
     * <pre>
     *   IDLE ──start──▶ RUNNING
     *   RUNNING ─pause─▶ PAUSED   RUNNING ─suspend─▶ SUSPENDED   RUNNING ─done─▶ COMPLETED/FAILED
     *   PAUSED ─resume─▶ RUNNING  PAUSED ─cancel─▶ FAILED
     *   SUSPENDED ─resume─▶ RUNNING  SUSPENDED ─cancel─▶ FAILED
     *   COMPLETED/FAILED ──（终态，不再转换；槽位重置回 IDLE 由 slot 复用处理）
     * </pre>
     */
    private static final Map<WorkflowState, Set<WorkflowState>> TRANSITIONS = buildTransitions();

    private static Map<WorkflowState, Set<WorkflowState>> buildTransitions() {
        Map<WorkflowState, Set<WorkflowState>> m = new EnumMap<>(WorkflowState.class);
        m.put(WorkflowState.IDLE, EnumSet.of(WorkflowState.RUNNING));
        m.put(WorkflowState.RUNNING, EnumSet.of(
                WorkflowState.PAUSED, WorkflowState.SUSPENDED,
                WorkflowState.COMPLETED, WorkflowState.FAILED));
        m.put(WorkflowState.PAUSED, EnumSet.of(WorkflowState.RUNNING, WorkflowState.FAILED));
        m.put(WorkflowState.SUSPENDED, EnumSet.of(WorkflowState.RUNNING, WorkflowState.FAILED));
        m.put(WorkflowState.COMPLETED, EnumSet.noneOf(WorkflowState.class));
        m.put(WorkflowState.FAILED, EnumSet.noneOf(WorkflowState.class));
        return m;
    }

    /** 状态是否允许 {@code from → to} 转换。 */
    public static boolean canTransition(WorkflowState from, WorkflowState to) {
        if (from == to) return true;
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(WorkflowState.class)).contains(to);
    }

    /**
     * 从现有布尔标志推导状态。
     *
     * @param occupied  是否占用槽位（type != null）
     * @param suspended 是否挂起
     * @param paused    是否暂停
     * @param complete  是否完成
     * @param failed    是否失败
     */
    public static WorkflowState fromFlags(boolean occupied, boolean suspended, boolean paused,
                                          boolean complete, boolean failed) {
        if (!occupied) return WorkflowState.IDLE;
        if (failed) return WorkflowState.FAILED;
        if (complete) return WorkflowState.COMPLETED;
        if (suspended) return WorkflowState.SUSPENDED;
        if (paused) return WorkflowState.PAUSED;
        return WorkflowState.RUNNING;
    }

    /**
     * 状态 → snapshot 的 holdType 语义（0=运行中，1=手动暂停，2=挂起）。
     * 兼容 {@code RtsWorkflowStatus.holdType}。
     */
    public static int toHoldType(WorkflowState state) {
        return switch (state) {
            case PAUSED -> 1;
            case SUSPENDED -> 2;
            default -> 0;
        };
    }
}
