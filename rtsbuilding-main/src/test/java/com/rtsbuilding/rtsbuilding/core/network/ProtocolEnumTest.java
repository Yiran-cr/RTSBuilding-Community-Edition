package com.rtsbuilding.rtsbuilding.core.network;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 协议枚举稳定性护栏：BuilderMode / RtsStorageSort / RtsWorkflowType。
 *
 * <p>三者均按显式 id 跨端编解码（网络包 + NBT 存档）。本测试断言 id 与历史 ordinal
 * 一致且全局唯一——任何插入/删除/重排都会在此失败，防止新旧端混连错位。
 * 新增枚举值需同步在对应 {@code fromId} 的 switch 中注册并补充断言。
 */
class ProtocolEnumTest {

    // ── BuilderMode（api 模块，网络 SET_MODE 编解码）──

    @Test
    void builderModeIdsMatchOrdinal() {
        BuilderMode[] values = BuilderMode.values();
        for (int i = 0; i < values.length; i++) {
            assertEquals(i, values[i].id(), "BuilderMode." + values[i] + " id 应等于历史 ordinal " + i);
        }
    }

    @Test
    void builderModeIdsUniqueAndRoundTrip() {
        assertUniqueIds(idsOfBuilderMode());
        for (BuilderMode m : BuilderMode.values()) {
            assertEquals(m, BuilderMode.fromId(m.id()));
        }
    }

    @Test
    void builderModeUnknownReturnsNull() {
        assertNull(BuilderMode.fromId(-1));
        assertNull(BuilderMode.fromId(99));
    }

    // ── RtsStorageSort（网络 + 玩家存档 NBT 编解码）──

    @Test
    void storageSortIdsMatchOrdinal() {
        RtsStorageSort[] values = RtsStorageSort.values();
        for (int i = 0; i < values.length; i++) {
            assertEquals(i, values[i].id(), "RtsStorageSort." + values[i] + " id 应等于历史 ordinal " + i);
        }
    }

    @Test
    void storageSortIdsUniqueAndRoundTrip() {
        assertUniqueIds(idsOfStorageSort());
        for (RtsStorageSort s : RtsStorageSort.values()) {
            assertEquals(s, RtsStorageSort.fromId(s.id()));
        }
    }

    @Test
    void storageSortUnknownFallsBackToQuantity() {
        // 存档/网络越界时回退默认排序（向后兼容）
        assertEquals(RtsStorageSort.QUANTITY, RtsStorageSort.fromId(-1));
        assertEquals(RtsStorageSort.QUANTITY, RtsStorageSort.fromId(99));
    }

    // ── RtsWorkflowType（common 模块，工作流进度跨端编解码）──

    @Test
    void workflowTypeIdsMatchOrdinal() {
        RtsWorkflowType[] values = RtsWorkflowType.values();
        for (int i = 0; i < values.length; i++) {
            assertEquals(i, values[i].id(), "RtsWorkflowType." + values[i] + " id 应等于历史 ordinal " + i);
        }
    }

    @Test
    void workflowTypeIdsUniqueAndRoundTrip() {
        assertUniqueIds(idsOfWorkflowType());
        for (RtsWorkflowType t : RtsWorkflowType.values()) {
            assertEquals(t, RtsWorkflowType.fromId(t.id()));
        }
    }

    @Test
    void workflowTypeUnknownReturnsNull() {
        assertNull(RtsWorkflowType.fromId(-1));
        assertNull(RtsWorkflowType.fromId(99));
    }

    // ── RtsWorkflowPriority（rank 显式值，跨端编解码）──

    @Test
    void workflowPriorityRanksMatchOrdinal() {
        RtsWorkflowPriority[] values = RtsWorkflowPriority.values();
        for (int i = 0; i < values.length; i++) {
            assertEquals(i, values[i].rank(), "RtsWorkflowPriority." + values[i] + " rank 应等于历史 ordinal " + i);
        }
    }

    @Test
    void workflowPriorityRankRoundTrip() {
        for (RtsWorkflowPriority p : RtsWorkflowPriority.values()) {
            assertEquals(p, RtsWorkflowPriority.fromRank(p.rank()));
        }
    }

    @Test
    void workflowPriorityUnknownFallsBackToNormal() {
        assertEquals(RtsWorkflowPriority.NORMAL, RtsWorkflowPriority.fromRank(-1));
        assertEquals(RtsWorkflowPriority.NORMAL, RtsWorkflowPriority.fromRank(99));
    }

    // ── 通用 ──

    private static void assertUniqueIds(int[] ids) {
        Set<Integer> seen = new HashSet<>();
        for (int id : ids) {
            assertEquals(true, seen.add(id), "id " + id + " 重复");
        }
    }

    private static int[] idsOfBuilderMode() {
        BuilderMode[] v = BuilderMode.values();
        int[] r = new int[v.length];
        for (int i = 0; i < v.length; i++) r[i] = v[i].id();
        return r;
    }

    private static int[] idsOfStorageSort() {
        RtsStorageSort[] v = RtsStorageSort.values();
        int[] r = new int[v.length];
        for (int i = 0; i < v.length; i++) r[i] = v[i].id();
        return r;
    }

    private static int[] idsOfWorkflowType() {
        RtsWorkflowType[] v = RtsWorkflowType.values();
        int[] r = new int[v.length];
        for (int i = 0; i < v.length; i++) r[i] = v[i].id();
        return r;
    }
}
