package com.rtsbuilding.rtsbuilding.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;

/**
 * 特效环形缓冲：保存最近的特效条目（位置 + 方块状态 + 加入时间 + 服务端权威动画时长），
 * 供渲染 pass 消费。
 *
 * <p><b>服务端权威时长：</b>每个条目携带由服务端动画包下发的 {@code durationMs}（服务端 tick × 50ms），
 * 渲染 pass 按条目自身的时长播放动画并清理，动画节奏由服务端控制而非客户端硬编码。</p>
 *
 * <p><b>容量策略：</b>批量操作（区域破坏/批量放置每 tick 最多 64 格）会在短时间内
 * 写入大量条目，容量不足会把<strong>正在播放的动画</strong>挤出。因此本缓冲采用
 * 「写入侧排期 + 满则排队」两级背压：{@link #schedule} 先补入等待队列
 * {@link #pending}，再尝试写入环形区；环形区满时新条目进入 {@link #pending}，
 * 由渲染 pass 每帧 {@link #drainPending} 在环形区释放后补入——动画只延迟不丢失，
 * 不会出现"一帧同时爆发、超限直接丢弃"的不完整现象。</p>
 *
 * <p><b>同位置重复写入：</b>同 key 已存在时只刷新方块状态与时长、<strong>保留原播放进度
 * （{@code addedAtMs} 不变）</strong>，避免"破坏→放置"高速交替时动画反复从头播放
 * 产生跳变。</p>
 *
 * <p><b>全槽遍历：</b>去重查找与 {@link #forEach} 均全槽扫描——{@link #prune}
 * 清除中间条目会形成空洞，依赖连续窗口会漏项。</p>
 */
public final class GhostRingBuffer {

    /** 默认容量（2 的幂）。 */
    public static final int DEFAULT_CAPACITY = 32;

    /** 等待队列上限：超过则丢弃最新条目，防止极端持续高速操作时内存无限增长。 */
    private static final int MAX_PENDING = 512;

    private final int capacity;
    private final int mask;
    private final long[] keys;
    private final BlockState[] states;
    private final long[] addedAtMs;
    private final long[] durationMs;
    private final boolean[] active;
    private int head;
    private int count;
    private final ArrayDeque<PendingEffect> pending = new ArrayDeque<>();

    /** 等待补入的排期特效：startMs 为预期动画启动时刻（毫秒时间戳），durationMs 为服务端权威动画时长。 */
    private record PendingEffect(long key, BlockState state, long startMs, long durationMs) {
    }

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
        this.durationMs = new long[c];
        this.active = new boolean[c];
    }

    public int capacity() {
        return capacity;
    }

    /**
     * 排期写入一个特效条目（推荐入口）。
     *
     * <p>先补入等待队列，再尝试写入环形区；环形区满时进入等待队列，
     * 由后续 {@link #drainPending} 补入。等待队列超限时丢弃该条目。
     *
     * @param startMs    预期的动画启动时刻（毫秒时间戳），可用于错峰（如
     *                   {@code now + seq * staggerMs}）
     * @param durationMs 服务端权威动画时长（毫秒），渲染 pass 按此播放
     */
    public void schedule(BlockPos pos, BlockState state, long startMs, long durationMs) {
        drainPending();
        if (!addToSlot(pos.asLong(), state, startMs, durationMs) && pending.size() < MAX_PENDING) {
            pending.addLast(new PendingEffect(pos.asLong(), state, startMs, durationMs));
        }
    }

    /**
     * 将等待队列中的条目补入环形区（环形区释放出空间后生效）。
     * <p>由渲染 pass 每帧调用；环形区仍满时保持排队，等待下一帧。</p>
     */
    public void drainPending() {
        while (!pending.isEmpty()) {
            PendingEffect e = pending.peekFirst();
            if (!addToSlot(e.key(), e.state(), e.startMs(), e.durationMs())) {
                break;
            }
            pending.removeFirst();
        }
    }

    /**
     * 记录一个特效条目（低层写入，缓冲满返回 {@code false}）。
     *
     * @return {@code true} 写入成功；缓冲已满时返回 {@code false}（调用方应使用
     *         {@link #schedule} 让其进入等待队列而非直接丢弃）
     */
    public boolean add(BlockPos pos, BlockState state, long nowMs, long durationMs) {
        return addToSlot(pos.asLong(), state, nowMs, durationMs);
    }

    private boolean addToSlot(long key, BlockState state, long startMs, long entryDurationMs) {
        // 全槽去重（prune 可能产生空洞，不能依赖连续窗口）
        for (int i = 0; i < capacity; i++) {
            if (active[i] && keys[i] == key) {
                // 同位置重复写入：保留原播放进度（addedAtMs 不变），只刷新方块状态与时长，
                // 避免"破坏→放置"高速交替时动画反复从头播放产生跳变
                states[i] = state;
                durationMs[i] = entryDurationMs;
                return true;
            }
        }
        // 找空槽写入
        for (int i = 0; i < capacity; i++) {
            if (!active[i]) {
                keys[i] = key;
                states[i] = state;
                addedAtMs[i] = startMs;
                durationMs[i] = entryDurationMs;
                active[i] = true;
                count++;
                return true;
            }
        }
        // 缓冲已满：返回失败，由 schedule 排入等待队列
        return false;
    }

    /**
     * 遍历全部槽位中仍活跃的条目（全槽扫描，prune 空洞不漏项）。
     */
    public void forEach(SlotConsumer consumer) {
        for (int i = 0; i < capacity; i++) {
            if (active[i]) {
                consumer.accept(keys[i], states[i], addedAtMs[i], durationMs[i]);
            }
        }
    }

    /**
     * 清理已超过<b>各自服务端权威时长</b>的过期条目，并重新统计活跃数。
     */
    public void prune(long nowMs) {
        for (int i = 0; i < capacity; i++) {
            if (active[i] && (nowMs - addedAtMs[i]) > durationMs[i]) {
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
        pending.clear();
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public int size() {
        return count;
    }

    @FunctionalInterface
    public interface SlotConsumer {
        void accept(long key, BlockState state, long addedAtMs, long durationMs);
    }
}
