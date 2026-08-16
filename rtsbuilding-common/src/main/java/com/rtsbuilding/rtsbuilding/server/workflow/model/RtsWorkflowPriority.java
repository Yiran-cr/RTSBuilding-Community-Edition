package com.rtsbuilding.rtsbuilding.server.workflow.model;

/**
 * Priority levels for workflow operations.
 *
 * <p>Priority determines how the system handles conflicts, resource allocation, and UI emphasis
 * when multiple workflows may be active simultaneously, or when one operation needs to preempt another.</p>
 */
public enum RtsWorkflowPriority {

    /** Background / low-importance tasks (e.g. idle area fill). */
    LOW(0),

    /** Default priority for most player-initiated operations. */
    NORMAL(1),

    /** Higher priority tasks, should interrupt lower priority work. */
    HIGH(2),

    /** Critical tasks that must be completed first (e.g. tool about to break). */
    CRITICAL(3);

    private final int rank;

    RtsWorkflowPriority(int rank) {
        this.rank = rank;
    }

    /**
     * Returns the numeric rank of this priority. Higher values indicate greater urgency.
     */
    public int rank() {
        return this.rank;
    }

    /**
     * Returns {@code true} if this priority is strictly higher than the given one.
     */
    public boolean isHigherThan(RtsWorkflowPriority other) {
        return this.rank > other.rank;
    }

    /**
     * 按协议 rank 反解；未知 rank 返回 {@link #NORMAL}（跨端兼容默认值）。
     */
    public static RtsWorkflowPriority fromRank(int rank) {
        return switch (rank) {
            case 0 -> LOW;
            case 1 -> NORMAL;
            case 2 -> HIGH;
            case 3 -> CRITICAL;
            default -> NORMAL;
        };
    }
}
