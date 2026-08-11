package com.rtsbuilding.rtsbuilding.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 特效环形缓冲：保存最近的特效条目（位置 + 方块状态 + 加入时间），
 * 供渲染 pass 消费。
 *
 * <p><b>容量策略：</b>批量操作（区域破坏/批量放置每 tick 最多 64 格）会在短时间内
 * 写入大量条目，容量不足会把<strong>正在播放的动画</strong>挤出。写入侧（
 * {@code RtsClientNetworkHandlers}）已按 tick 限流，且 {@link #add} 在缓冲满时
 * <strong>丢弃新条目</strong>而非覆盖——保证已入缓冲的动画完整播放。</p>
 *
 * <p><b>全槽遍历：</b>去重查找与 {@link #forEach} 均全槽扫描——{@link #prune}
 * 清除中间条目会形成空洞，依赖连续窗口会漏项。</p>
 */
public final class GhostRingBuffer {

    /** 默认容量（2 的幂）。 */
    public static final int DEFAULT_CAPACITY = 32;

    private final int capacity;
    private final int mask;
    private final long[] keys;
    private final BlockState[] states;
    private final long[] addedAtMs;
    private final boolean[] active;
    private int head;
    private int count;

    public GhostRingBuffer() {
        this(DEFAULT_CAPACITY);
    }

    /** @param capacity 目标容量，向上取整为 2 的幂（至少 4）。 */
    public GhostRingBuffer(int capacity) {
        int c = Integer.highestOneBit(Math.max(4, capacity));
        if (c < capacity) c <<= 1;
        this.capacity = c;
        this.mask = c - 1;
        this.keys = new long[c];
        this.states = new BlockState[c];
        this.addedAtMs = new long[c];
        this.active = new boolean[c];
    }

    public int capacity() {
        return capacity;
    }

    /**
     * 记录一个特效条目。
     *
     * @return {@code true} 写入成功；缓冲已满时返回 {@code false}（丢弃新条目，
     *         保证已入缓冲的动画能完整播放，不被后续高频写入中断）
     */
    public boolean add(BlockPos pos, BlockState state, long nowMs) {
        long key = pos.asLong();
        // 全槽去重（prune 可能产生空洞，不能依赖连续窗口）
        for (int i = 0; i < capacity; i++) {
            if (active[i] && keys[i] == key) {
                states[i] = state;
                addedAtMs[i] = nowMs;
                return true;
            }
        }
        // 找空槽写入
        for (int i = 0; i < capacity; i++) {
            if (!active[i]) {
                keys[i] = key;
                states[i] = state;
                addedAtMs[i] = nowMs;
                active[i] = true;
                count++;
                return true;
            }
        }
        // 缓冲已满：丢弃新条目，保护正在播放的动画不被覆盖
        return false;
    }

    /**
     * 遍历全部槽位中仍活跃的条目（全槽扫描，prune 空洞不漏项）。
     */
    public void forEach(SlotConsumer consumer) {
        for (int i = 0; i < capacity; i++) {
            if (active[i]) {
                consumer.accept(keys[i], states[i], addedAtMs[i]);
            }
        }
    }

    /**
     * 清理超过 {@code maxAgeMs} 的过期条目，并重新统计活跃数。
     */
    public void prune(long nowMs, long maxAgeMs) {
        for (int i = 0; i < capacity; i++) {
            if (active[i] && (nowMs - addedAtMs[i]) > maxAgeMs) {
                active[i] = false;
            }
        }
        int c = 0;
        for (int i = 0; i < capacity; i++) {
            if (active[i]) c++;
        }
        count = c;
    }

    public void clear() {
        for (int i = 0; i < capacity; i++) {
            active[i] = false;
        }
        head = 0;
        count = 0;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public int size() {
        return count;
    }

    @FunctionalInterface
    public interface SlotConsumer {
        void accept(long key, BlockState state, long addedAtMs);
    }
}
