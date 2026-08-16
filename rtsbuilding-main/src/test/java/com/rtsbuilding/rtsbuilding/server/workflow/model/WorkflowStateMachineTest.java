package com.rtsbuilding.rtsbuilding.server.workflow.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WorkflowStateMachine 状态机测试（阶段四 4.2）。
 *
 * <p>验证合法转换白名单、fromFlags 推导与 toHoldType 映射。
 */
class WorkflowStateMachineTest {

    // ── canTransition 白名单 ──

    @Test
    void allowedTransitions() {
        assertTrue(WorkflowStateMachine.canTransition(WorkflowState.IDLE, WorkflowState.RUNNING));
        assertTrue(WorkflowStateMachine.canTransition(WorkflowState.RUNNING, WorkflowState.PAUSED));
        assertTrue(WorkflowStateMachine.canTransition(WorkflowState.RUNNING, WorkflowState.SUSPENDED));
        assertTrue(WorkflowStateMachine.canTransition(WorkflowState.RUNNING, WorkflowState.COMPLETED));
        assertTrue(WorkflowStateMachine.canTransition(WorkflowState.RUNNING, WorkflowState.FAILED));
        assertTrue(WorkflowStateMachine.canTransition(WorkflowState.PAUSED, WorkflowState.RUNNING));
        assertTrue(WorkflowStateMachine.canTransition(WorkflowState.PAUSED, WorkflowState.FAILED));
        assertTrue(WorkflowStateMachine.canTransition(WorkflowState.SUSPENDED, WorkflowState.RUNNING));
        assertTrue(WorkflowStateMachine.canTransition(WorkflowState.SUSPENDED, WorkflowState.FAILED));
    }

    @Test
    void forbiddenTransitions() {
        // 暂停/挂起不能互相直接转换（必须经 RUNNING）
        assertFalse(WorkflowStateMachine.canTransition(WorkflowState.PAUSED, WorkflowState.SUSPENDED));
        assertFalse(WorkflowStateMachine.canTransition(WorkflowState.SUSPENDED, WorkflowState.PAUSED));
        // 终态不可继续转换
        assertFalse(WorkflowStateMachine.canTransition(WorkflowState.COMPLETED, WorkflowState.RUNNING));
        assertFalse(WorkflowStateMachine.canTransition(WorkflowState.COMPLETED, WorkflowState.FAILED));
        assertFalse(WorkflowStateMachine.canTransition(WorkflowState.FAILED, WorkflowState.RUNNING));
        // IDLE 不能直接暂停/挂起
        assertFalse(WorkflowStateMachine.canTransition(WorkflowState.IDLE, WorkflowState.PAUSED));
        assertFalse(WorkflowStateMachine.canTransition(WorkflowState.IDLE, WorkflowState.SUSPENDED));
    }

    @Test
    void selfTransitionAllowed() {
        assertTrue(WorkflowStateMachine.canTransition(WorkflowState.RUNNING, WorkflowState.RUNNING));
        assertTrue(WorkflowStateMachine.canTransition(WorkflowState.COMPLETED, WorkflowState.COMPLETED));
    }

    // ── fromFlags 推导 ──

    @Test
    void fromFlagsDerivesStates() {
        assertEquals(WorkflowState.IDLE, WorkflowStateMachine.fromFlags(false, false, false, false, false));
        assertEquals(WorkflowState.RUNNING, WorkflowStateMachine.fromFlags(true, false, false, false, false));
        assertEquals(WorkflowState.PAUSED, WorkflowStateMachine.fromFlags(true, false, true, false, false));
        assertEquals(WorkflowState.SUSPENDED, WorkflowStateMachine.fromFlags(true, true, false, false, false));
        assertEquals(WorkflowState.COMPLETED, WorkflowStateMachine.fromFlags(true, false, false, true, false));
        assertEquals(WorkflowState.FAILED, WorkflowStateMachine.fromFlags(true, false, false, false, true));
    }

    @Test
    void failedTakesPrecedenceOverCompleted() {
        // 失败优先于完成（即使两者都成立）
        assertEquals(WorkflowState.FAILED, WorkflowStateMachine.fromFlags(true, false, false, true, true));
    }

    // ── toHoldType 映射 ──

    @Test
    void holdTypeMapping() {
        assertEquals(0, WorkflowStateMachine.toHoldType(WorkflowState.RUNNING));
        assertEquals(0, WorkflowStateMachine.toHoldType(WorkflowState.IDLE));
        assertEquals(1, WorkflowStateMachine.toHoldType(WorkflowState.PAUSED));
        assertEquals(2, WorkflowStateMachine.toHoldType(WorkflowState.SUSPENDED));
        assertEquals(0, WorkflowStateMachine.toHoldType(WorkflowState.COMPLETED));
        assertEquals(0, WorkflowStateMachine.toHoldType(WorkflowState.FAILED));
    }
}
