package com.rtsbuilding.rtsbuilding.server.workflow.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RtsWorkflowStatus 派生计算测试（纯逻辑，无 Minecraft 运行时依赖）。
 *
 * <p>覆盖 {@link RtsWorkflowStatus#fromRaw} 的剩余/进度/完成判定、hold 语义、
 * lang key 推导与空值防御。这些是工作流进度展示的核心数据来源。
 */
class RtsWorkflowStatusTest {

    @Test
    void fromRawComputesRemaining() {
        var s = RtsWorkflowStatus.fromRaw(
                RtsWorkflowType.MINE_SINGLE, RtsWorkflowPriority.NORMAL,
                10, 4, 2, List.of(), "", 0, 1);
        assertEquals(4, s.remainingBlocks(), "10 - (4+2) = 4");
    }

    @Test
    void fromRawClampsRemainingToZero() {
        var s = RtsWorkflowStatus.fromRaw(
                RtsWorkflowType.MINE_SINGLE, RtsWorkflowPriority.NORMAL,
                10, 8, 5, List.of(), "", 0, 1);
        assertEquals(0, s.remainingBlocks(), "完成+失败超总量应截断为 0");
    }

    @Test
    void fromRawComputesProgress() {
        var s = RtsWorkflowStatus.fromRaw(
                RtsWorkflowType.ULTIMINE, RtsWorkflowPriority.HIGH,
                100, 25, 0, List.of(), "", 0, 1);
        assertEquals(0.25F, s.progress(), 1e-6f, "25/100 = 0.25");
    }

    @Test
    void fromRawZeroTotalYieldsZeroProgress() {
        var s = RtsWorkflowStatus.fromRaw(
                RtsWorkflowType.ULTIMINE, RtsWorkflowPriority.NORMAL,
                0, 0, 0, List.of(), "", 0, 1);
        assertEquals(0.0F, s.progress(), 1e-6f);
        assertEquals(0, s.remainingBlocks());
        assertFalse(s.isComplete(), "总量为 0 不算完成");
    }

    @Test
    void fromRawCompleteWhenDoneEqualsTotal() {
        var s = RtsWorkflowStatus.fromRaw(
                RtsWorkflowType.PLACE_BATCH, RtsWorkflowPriority.NORMAL,
                5, 3, 2, List.of(), "", 0, 1);
        assertTrue(s.isComplete(), "完成+失败 == 总量应判定完成");
        assertEquals(0, s.remainingBlocks());
    }

    @Test
    void holdTypesDriveOnHold() {
        var running = RtsWorkflowStatus.fromRaw(
                RtsWorkflowType.AREA_MINE, RtsWorkflowPriority.NORMAL,
                10, 0, 0, List.of(), "", 0, 1);
        assertFalse(running.onHold(), "holdType=0 表示运行中");

        var paused = RtsWorkflowStatus.fromRaw(
                RtsWorkflowType.AREA_MINE, RtsWorkflowPriority.NORMAL,
                10, 0, 0, List.of(), "", 1, 1);
        assertTrue(paused.onHold(), "holdType=1 表示手动暂停");

        var suspended = RtsWorkflowStatus.fromRaw(
                RtsWorkflowType.AREA_MINE, RtsWorkflowPriority.NORMAL,
                10, 0, 0, List.of(), "", 2, 1);
        assertTrue(suspended.onHold(), "holdType=2 表示挂起");
    }

    @Test
    void typeLabelKeyPerType() {
        assertEquals("screen.rtsbuilding.workflow.type.mine_single",
                RtsWorkflowStatus.fromRaw(RtsWorkflowType.MINE_SINGLE, RtsWorkflowPriority.NORMAL,
                        1, 0, 0, List.of(), "", 0, 1).typeLabelKey());
        assertEquals("screen.rtsbuilding.workflow.type.blueprint_build",
                RtsWorkflowStatus.fromRaw(RtsWorkflowType.BLUEPRINT_BUILD, RtsWorkflowPriority.NORMAL,
                        1, 0, 0, List.of(), "", 0, 1).typeLabelKey());
        assertEquals("screen.rtsbuilding.workflow.type.idle", RtsWorkflowStatus.idle().typeLabelKey());
    }

    @Test
    void nullCollectionsDefended() {
        var s = RtsWorkflowStatus.fromRaw(
                RtsWorkflowType.AREA_DESTROY, RtsWorkflowPriority.CRITICAL,
                10, 0, 0, null, null, 0, 1);
        assertTrue(s.missingItems().isEmpty(), "null missingItems 应防御为空列表");
        assertEquals("", s.detailMessage(), "null detailMessage 应防御为空串");
        assertFalse(s.hasMissingItems());
    }
}
