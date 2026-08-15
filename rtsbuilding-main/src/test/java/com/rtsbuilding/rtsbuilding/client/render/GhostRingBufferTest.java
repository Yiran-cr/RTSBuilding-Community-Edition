package com.rtsbuilding.rtsbuilding.client.render;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GhostRingBuffer} 覆盖/去重/空洞复用逻辑测试。
 * 验证批量写入时缓冲满丢弃新条目（保护已入缓冲动画）、同位置去重刷新、prune 空洞后可复用。
 * （state 参数传 null：add 仅存引用，不检查非空，避免测试环境初始化 MC 注册表。）
 */
class GhostRingBufferTest {

    @Test
    void addBeyondCapacityDropsNewest() {
        GhostRingBuffer buf = new GhostRingBuffer(4);
        for (int i = 0; i < 4; i++) {
            assertTrue(buf.add(new BlockPos(i, 0, 0), null, i * 10L));
        }
        // 第 5 个超出容量被丢弃（返回 false），前 4 个保留
        assertFalse(buf.add(new BlockPos(4, 0, 0), null, 40L));
        assertEquals(4, buf.size());
        assertTrue(contains(buf, new BlockPos(0, 0, 0)));
        assertTrue(contains(buf, new BlockPos(3, 0, 0)));
        assertFalse(contains(buf, new BlockPos(4, 0, 0)));
    }

    @Test
    void duplicatePositionRefreshes() {
        GhostRingBuffer buf = new GhostRingBuffer(4);
        buf.add(new BlockPos(0, 0, 0), null, 100L);
        buf.add(new BlockPos(1, 0, 0), null, 200L);
        buf.add(new BlockPos(0, 0, 0), null, 300L);
        assertEquals(2, buf.size());
    }

    @Test
    void duplicateKeepsOriginalStartTime() {
        GhostRingBuffer buf = new GhostRingBuffer(4);
        buf.add(new BlockPos(0, 0, 0), null, 100L);
        // 同位置重复写入只刷新状态，保留原播放进度（addedAtMs 不变），避免动画跳变
        buf.add(new BlockPos(0, 0, 0), null, 300L);
        assertEquals(1, buf.size());
        AtomicLong seen = new AtomicLong(-1L);
        buf.forEach((k, s, t) -> seen.set(t));
        assertEquals(100L, seen.get());
    }

    @Test
    void scheduleQueuesWhenFullThenDrains() {
        GhostRingBuffer buf = new GhostRingBuffer(4);
        for (int i = 0; i < 4; i++) {
            buf.add(new BlockPos(i, 0, 0), null, 0L);
        }
        // 环形区已满：schedule 排入等待队列而非丢弃
        buf.schedule(new BlockPos(4, 0, 0), null, 100L);
        assertEquals(4, buf.size());
        assertFalse(contains(buf, new BlockPos(4, 0, 0)));
        // 环形区释放空间后 drainPending 将等待队列补入
        buf.prune(200L, 50L);
        buf.drainPending();
        assertTrue(contains(buf, new BlockPos(4, 0, 0)));
        assertEquals(1, buf.size());
    }

    @Test
    void clearDropsPending() {
        GhostRingBuffer buf = new GhostRingBuffer(4);
        for (int i = 0; i < 4; i++) {
            buf.add(new BlockPos(i, 0, 0), null, 0L);
        }
        buf.schedule(new BlockPos(4, 0, 0), null, 100L);
        buf.clear();
        // 清空后环形区与等待队列均被清空，pending 不会在后续 drain 中复活
        buf.drainPending();
        assertEquals(0, buf.size());
        assertTrue(buf.isEmpty());
    }

    @Test
    void pruneRemovesExpiredAndFreesSlot() {
        GhostRingBuffer buf = new GhostRingBuffer(4);
        buf.add(new BlockPos(0, 0, 0), null, 0L);
        buf.add(new BlockPos(1, 0, 0), null, 0L);
        buf.add(new BlockPos(2, 0, 0), null, 0L);
        buf.prune(100L, 50L);
        assertEquals(0, buf.size());
        assertTrue(buf.isEmpty());
        // 空槽可复用
        buf.add(new BlockPos(9, 0, 0), null, 200L);
        assertTrue(contains(buf, new BlockPos(9, 0, 0)));
        assertEquals(1, buf.size());
    }

    @Test
    void forEachScansAllSlotsAfterHoles() {
        GhostRingBuffer buf = new GhostRingBuffer(4);
        buf.add(new BlockPos(0, 0, 0), null, 0L);
        buf.add(new BlockPos(1, 0, 0), null, 100L);
        buf.add(new BlockPos(2, 0, 0), null, 200L);
        // 0 号过期形成空洞
        buf.prune(150L, 100L);
        assertFalse(contains(buf, new BlockPos(0, 0, 0)));
        assertTrue(contains(buf, new BlockPos(1, 0, 0)));
        assertTrue(contains(buf, new BlockPos(2, 0, 0)));
        // 空洞后 add 复用空槽，不覆盖活跃条目
        buf.add(new BlockPos(8, 0, 0), null, 300L);
        assertEquals(3, buf.size());
        assertTrue(contains(buf, new BlockPos(1, 0, 0)));
        assertTrue(contains(buf, new BlockPos(2, 0, 0)));
        assertTrue(contains(buf, new BlockPos(8, 0, 0)));
    }

    private static boolean contains(GhostRingBuffer buf, BlockPos pos) {
        AtomicInteger found = new AtomicInteger();
        buf.forEach((k, s, t) -> {
            if (BlockPos.of(k).equals(pos)) found.incrementAndGet();
        });
        return found.get() > 0;
    }
}
