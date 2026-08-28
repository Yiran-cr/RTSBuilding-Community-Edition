package com.rtsbuilding.rtsbuilding.planetrise.server.powergrid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端<b>电网时序历史存储</b>：按电网组所有者 UUID 分组，为每个组维护三档
 * （5 秒 / 1 分钟 / 1 小时）发耗电环形缓冲，供「电网总览」仪表盘柱状图渲染。
 * <p>
 * 由 {@code PowerGridSync} 每 20 tick（1 秒）调用 {@link #sampleIfNeeded}，内部按
 * 服务端 tick 数判断是否到达各档采样时间，到点才写入对应环形缓冲，避免每秒冗余采样。
 * <p>
 * 数据<b>不持久化</b>：服务器重启后历史清空（仪表盘重新累计）。设计取舍：避免新增磁盘
 * I/O 与跨会话状态管理复杂度；客户端 UI 在打开仪表盘时调用 {@code requestHistory} 触发
 * 服务端把当前累计的三档数据 S2C 回推。
 */
public final class PowerGridHistoryStore {

    public static final PowerGridHistoryStore INSTANCE = new PowerGridHistoryStore();

    /** 5 秒档容量（24 点 = 2 分钟）。 */
    private static final int CAP_5S = 24;
    /** 1 分钟档容量（60 点 = 1 小时）。 */
    private static final int CAP_1M = 60;
    /** 1 小时档容量（24 点 = 24 小时）。 */
    private static final int CAP_1H = 24;

    /** 各档采样间隔（以 sync tick 计——{@link PowerGridSync} 中 HISTORY_TICK 每 1 秒 +1）。 */
    private static final long INTERVAL_5S_TICK = 5L;
    private static final long INTERVAL_1M_TICK = 60L;
    private static final long INTERVAL_1H_TICK = 3600L;

    private final Map<UUID, GridHistory> histories = new ConcurrentHashMap<>();

    private PowerGridHistoryStore() {
    }

    public static PowerGridHistoryStore get() {
        return INSTANCE;
    }

    /**
     * 由 {@code PowerGridSync} 每 20 tick 调用：按服务端 tick 数判断各档是否到采样点，
     * 到达则把当前 {gen, demand} 写入对应档环形缓冲。<b>不影响未到点的档</b>，避免重复。
     * <p>注：tick 0 时三档都会被采到一次（初始样本），用于 UI 首次打开时立即有点位渲染。
     */
    public void sampleIfNeeded(UUID owner, long generation, long demand, long serverTick) {
        if (owner == null) {
            return;
        }
        GridHistory h = histories.computeIfAbsent(owner, k -> new GridHistory());
        // 注意：三档间隔需互为倍数，否则单次采样会漏采某些档。
        // 5s=5, 1m=60, 1h=3600 —— 1m 是 5s 的 12 倍、1h 是 1m 的 60 倍，符合倍数关系。
        if (serverTick % INTERVAL_1H_TICK == 0) {
            h.push1h(generation, demand);
        }
        if (serverTick % INTERVAL_1M_TICK == 0) {
            h.push1m(generation, demand);
        }
        if (serverTick % INTERVAL_5S_TICK == 0) {
            h.push5s(generation, demand);
        }
    }

    /** 5 秒档快照（按时间顺序，旧 → 新）。 */
    public List<long[]> snapshot5s(UUID owner) {
        GridHistory h = owner == null ? null : histories.get(owner);
        return h == null ? List.of() : h.snap5s();
    }

    /** 1 分钟档快照（按时间顺序，旧 → 新）。 */
    public List<long[]> snapshot1m(UUID owner) {
        GridHistory h = owner == null ? null : histories.get(owner);
        return h == null ? List.of() : h.snap1m();
    }

    /** 1 小时档快照（按时间顺序，旧 → 新）。 */
    public List<long[]> snapshot1h(UUID owner) {
        GridHistory h = owner == null ? null : histories.get(owner);
        return h == null ? List.of() : h.snap1h();
    }

    /** 当某电网组被销毁/所有者清空时清理历史（避免内存泄漏）。 */
    public void remove(UUID owner) {
        if (owner != null) {
            histories.remove(owner);
        }
    }

    /** 单个电网组的三档环形缓冲。 */
    private static final class GridHistory {
        private final RingBuffer buf5s = new RingBuffer(CAP_5S);
        private final RingBuffer buf1m = new RingBuffer(CAP_1M);
        private final RingBuffer buf1h = new RingBuffer(CAP_1H);

        void push5s(long g, long d) { buf5s.push(g, d); }
        void push1m(long g, long d) { buf1m.push(g, d); }
        void push1h(long g, long d) { buf1h.push(g, d); }

        List<long[]> snap5s() { return buf5s.snapshot(); }
        List<long[]> snap1m() { return buf1m.snapshot(); }
        List<long[]> snap1h() { return buf1h.snapshot(); }
    }

    /**
     * 简单定容环形缓冲：固定容量数组 + 头索引 + 已写入数量。
     * 写入溢出时覆盖最老元素；快照按时间顺序（旧 → 新）克隆返回。
     */
    private static final class RingBuffer {
        private final long[][] data;
        private int head;   // 下一个写入位置
        private int size;   // 已写入数量（饱和到容量）

        RingBuffer(int capacity) {
            this.data = new long[capacity][];
        }

        void push(long g, long d) {
            data[head] = new long[]{g, d};
            head = (head + 1) % data.length;
            if (size < data.length) {
                size++;
            }
        }

        List<long[]> snapshot() {
            List<long[]> out = new ArrayList<>(size);
            if (size == 0) {
                return out;
            }
            // 最老元素位置：head 回退 size 步（取模正化）。
            int start = ((head - size) % data.length + data.length) % data.length;
            for (int i = 0; i < size; i++) {
                int idx = (start + i) % data.length;
                long[] v = data[idx];
                if (v != null) {
                    out.add(v.clone());
                }
            }
            return out;
        }
    }
}
