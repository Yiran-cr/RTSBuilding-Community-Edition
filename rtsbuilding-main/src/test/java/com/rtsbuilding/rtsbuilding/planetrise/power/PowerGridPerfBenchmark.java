package com.rtsbuilding.rtsbuilding.planetrise.power;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 电网<b>性能极限基准</b>（纯 JVM，无 MC 依赖）。
 * <p>
 * 精确复刻 {@code PowerTowerBlockEntity} 每 tick 的两大热路径，用于度量<b>大量不同范围输电塔内
 * 大量工作设备传输电量</b>时的相对成本：
 * <ul>
 *   <li><b>scanRange 分片扫描</b>（每 {@code SCAN_INTERVAL} tick 轮询一圈供电范围），发现供电范围内
 *       有能量能力的设备；</li>
 *   <li><b>broadcast 广播注能</b>（每 tick），按权威角色/能力判定方向 → 收集可注入设备 → 按容量占比
 *       注入标准 FE 能力对象。</li>
 * </ul>
 * 真实的 Minecraft {@code level.getCapability} / {@code getBlockEntity} 无法在纯 JVM 复现，故本基准输出
 * <b>两类指标</b>（这两项才是主线程压力的来源）：
 * <ol>
 *   <li><b>确定性调用计数</b>：每 tick 的 {@code capabilityQueries}（=getCapability，NeoForge 最昂贵操作）、
 *       {@code blockEntityLookups}、{@code receiveEnergyCalls}、{@code simulateCalls}（模拟注入）、
 *       {@code roleKeyAllocs}（每 consumer 一次字符串分配）。</li>
 *   <li><b>按单次成本估算</b>：把上述调用乘以经验单次成本，折算每 tick 毫秒，与 20 TPS 的 50ms 预算
 *       比较，给出主线程占用百分比。</li>
 * </ol>
 * 运行方式：IDE 直接跑 {@link #main(String[])}，或执行
 * {@code .\gradlew.bat :rtsbuilding-main:test --tests "*PowerGridPerfBenchmark*"}。
 */
public final class PowerGridPerfBenchmark {

    // ── 复刻 PowerTowerBlockEntity 关键常量（与真实实现保持一致） ──
    private static final int SCAN_INTERVAL = 20;
    private static final int MAX_CONSUMERS = 128;
    private static final int PROBE_SIDES = 7;   // null + 6 面（任意面优先命中即早退）
    private static final long SUPPLY_CAP = Integer.MAX_VALUE; // Config.powerTowerThroughput()
    /** 稳态取平均的连续 tick 数（调用计数按此折算为【每 tick】）。 */
    private static final int ITERS = 200;
    // 扫描自适应退避（复刻优化 2）
    private static final int SCAN_PERIOD_MIN = SCAN_INTERVAL;
    private static final int SCAN_PERIOD_MAX = 200;

    // ── 经验单次成本（纳秒量级，用于把调用计数折算为每 tick 毫秒） ──
    private static final long NS_CAPABILITY = 150;   // level.getCapability（遍历 provider、可能分配）
    private static final long NS_BE_LOOKUP = 40;     // level.getBlockEntity
    private static final long NS_RECEIVE = 30;       // IEnergyStorage.receiveEnergy（真实写）
    private static final long NS_SIMULATE = 20;      // IEnergyStorage.*Energy(..., simulate)
    private static final long NS_STRING = 40;        // key 字符串分配
    private static final long NS_MAP_GET = 15;       // HashMap.get / contains

    // ── 调用计数器 ──
    private long capabilityQueries;
    private long blockEntityLookups;
    private long receiveCalls;
    private long simulateCalls;
    private long roleKeyAllocs;
    private long mapGets;

    // ── 模拟世界 ──
    private int gridX, gridY, gridZ, rangeSize;
    private final List<Integer> consumerFlat = new ArrayList<>(); // 供电范围内设备（flat index）
    private final List<Integer> consumers = new ArrayList<>();    // 塔当前跟踪的设备（稳态=全部）
    private final Map<Long, SimStorage> deviceStore = new HashMap<>();
    private int scanCursor;
    private long scanGateTick = Long.MIN_VALUE;
    private int scanPeriod = SCAN_PERIOD_MIN;
    private boolean scanWindowActive;
    private boolean scanRoundAdded;
    private long gameTime;

    /** 模拟 FE 存储：容量 + 当前能量 + 收发开关。 */
    private static final class SimStorage {
        final int capacity;
        int energy;
        final boolean canReceive;
        final boolean canExtract;

        SimStorage(int capacity, int energy, boolean canReceive, boolean canExtract) {
            this.capacity = capacity;
            this.energy = energy;
            this.canReceive = canReceive;
            this.canExtract = canExtract;
        }

        int room() {
            return Math.max(0, capacity - energy);
        }

        int receive(int amount) {
            int acc = Math.min(amount, room());
            energy += acc;
            return acc;
        }
    }

    /** JUnit 5 要求测试类只声明<b>一个构造</b>；基准逻辑全在静态 {@link #main} 与实例方法，故唯一构造为空。 */
    private PowerGridPerfBenchmark() {
    }

    /** 初始化一个模拟世界：在 <pre>powerRange × verticalRadius</pre> 供电范围内撒 deviceCount 台工作设备。 */
    private PowerGridPerfBenchmark init(int powerRange, int verticalRadius, int deviceCount, Random rng) {
        this.gridX = powerRange * 2 + 1;
        this.gridY = verticalRadius * 2 + 1;
        this.gridZ = powerRange * 2 + 1;
        this.rangeSize = gridX * gridY * gridZ;

        int placed = Math.min(deviceCount, MAX_CONSUMERS);
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        while (seen.size() < placed) {
            int flat = rng.nextInt(rangeSize);
            if (seen.add(flat)) {
                int cap = 100_000 + rng.nextInt(400_000);
                SimStorage s = new SimStorage(cap, cap / 4, true, true);
                deviceStore.put((long) flat, s);
                consumerFlat.add(flat);
            }
        }
        return this;
    }

    private void resetCounters() {
        capabilityQueries = 0;
        blockEntityLookups = 0;
        receiveCalls = 0;
        simulateCalls = 0;
        roleKeyAllocs = 0;
        mapGets = 0;
    }

    private boolean isDevice(int flat) {
        return deviceStore.containsKey((long) flat);
    }

    /* ================= scanRange：分片扫描（复刻优化后：每格 1 次 getBlockEntity） ================= */

    /** 复刻优化后的 scanRange：每格<b>仅 1 次</b> getBlockEntity；返回是否完成一整圈。 */
    private boolean scanRangePerTick() {
        if (consumers.size() >= MAX_CONSUMERS) {
            return false;
        }
        int perTick = Math.max(1, (int) Math.ceil(rangeSize / (double) SCAN_INTERVAL));
        for (int i = 0; i < perTick && scanCursor < rangeSize; i++, scanCursor++) {
            int flat = scanCursor;
            boolean be = isDevice(flat);
            blockEntityLookups++;                        // 每格仅 1 次 getBlockEntity
            if (flat == 0 /* worldPosition=原点 */ || consumers.contains(flat)) {
                mapGets++;                               // consumers.contains
                continue;
            }
            if (!be) {
                continue;                                 // 无方块实体：早退，不查能力。
            }
            // hasEnergyCapability：设备存在 → null 面优先命中（1 次）。
            capabilityQueries++;
            consumers.add(flat);
            scanRoundAdded = true;
            if (consumers.size() >= MAX_CONSUMERS) {
                break;
            }
        }
        if (scanCursor >= rangeSize) {
            scanCursor = 0;
            return true;
        }
        return false;
    }

    /** 复刻优化后的 maybeScan：自适应退避调度（电网稳定时不逐 tick 扫描）。 */
    private void maybeScanPerTick(long now) {
        if (consumers.size() >= MAX_CONSUMERS) {
            scanWindowActive = false;                    // 满载：无需再发现，关闭窗口。
            return;
        }
        if (!scanWindowActive) {
            if (now < scanGateTick) {
                return;                                  // 冷却期：零扫描成本。
            }
            scanWindowActive = true;
            scanRoundAdded = false;
        }
        if (scanRangePerTick()) {                        // 完成一整圈
            scanWindowActive = false;
            scanPeriod = scanRoundAdded ? SCAN_PERIOD_MIN
                    : Math.min(scanPeriod * 2, SCAN_PERIOD_MAX);
            scanGateTick = now + scanPeriod;
        }
    }

    /* ================= broadcast：每 tick 广播注能（复刻方向判定/缓存/占比注入） ================= */

    /** 复刻 findUsableStorage 缓存（pos → 能力对象），容量占比注入复刻。 */
    private final Map<Long, SimStorage> usableCache = new HashMap<>();

    private SimStorage findUsableStorage(int flat, boolean wantExtract) {
        SimStorage cached = usableCache.get((long) flat);
        if (cached != null) {
            blockEntityLookups++;                        // level.getBlockEntity == owner
            if (canInteract(cached, wantExtract)) {
                return cached;
            }
        }
        // 未命中：7 面回退，命中早退。设备存在 → null 面命中（1 次）；否则 7 次。
        capabilityQueries += isDevice(flat) ? 1 : PROBE_SIDES;
        SimStorage storage = deviceStore.get((long) flat);
        if (storage != null && canInteract(storage, wantExtract)) {
            usableCache.put((long) flat, storage);
            return storage;
        }
        return null;
    }

    private boolean canInteract(SimStorage s, boolean wantExtract) {
        if (wantExtract) {
            simulateCalls++;
            return s.canExtract && s.energy > 0;
        }
        simulateCalls++;                                 // receiveEnergy(simulate)
        return s.canReceive && s.room() > 0;
    }

    private long getDeviceRoleOverride(int flat) {
        roleKeyAllocs++;                                 // roleKey = dim+"|"+x+","+y+","+z（每 consumer 一次字符串分配）
        mapGets++;                                       // DEVICE_ROLE_OVERRIDES.get(roleKey)
        return 0L;                                       // 基准：无覆盖（null）
    }

    private void broadcastPerTick() {
        // 模拟电网调度本 tick 分给塔的配额（真实来自 PowerScheduler，按需求占比+吞吐分配）。
        long gridQuota = 800L;
        long externalGen = 0L;
        long injectedTotal = 0L;
        List<Integer> alive = new ArrayList<>();
        Map<Integer, SimStorage> usable = new HashMap<>();
        long totalCap = 0L;
        long roomSum = 0L;

        Main: for (Integer flat : consumers) {
            long role = getDeviceRoleOverride(flat);
            if (role == 1L) {                            // RtsDeviceRole.GENERATOR：只提取
                SimStorage src = findUsableStorage(flat, true);
                if (src != null) {
                    externalGen += Math.min(src.energy, SUPPLY_CAP);
                    src.energy -= (int) Math.min(src.energy, Math.min(SUPPLY_CAP, Integer.MAX_VALUE));
                }
                continue Main;
            }
            // 用电端（CONSUMER 覆盖 或 自动判定）：
            SimStorage storage = findUsableStorage(flat, false);
            if (storage == null) {
                continue Main;                           // 不可注入：简化（非组件节点，保留展示）
            }
            long cap = storage.capacity;
            if (cap <= 0L) {
                continue Main;
            }
            alive.add(flat);
            usable.put(flat, storage);
            totalCap += cap;
            roomSum += Math.max(0, cap - storage.energy);
        }

        if (alive.isEmpty() || totalCap <= 0L) {
            return;
        }

        // 塔本 tick 可支配总电量 = 电网配额（进塔） + 外部提取（进塔），受吞吐上限钳制。
        long totalQuota = Math.min(gridQuota + externalGen, SUPPLY_CAP);
        long remaining = totalQuota;
        for (Integer flat : alive) {
            SimStorage storage = usable.get(flat);
            long share = (long) ((double) totalQuota * storage.capacity / totalCap);
            if (share <= 0L) {
                continue;
            }
            long room = storage.room();
            if (room <= 0L) {
                continue;
            }
            long toMove = Math.min(share, room);
            simulateCalls++;                             // getMaxEnergyStored / getEnergyStored 次数近似
            receiveCalls++;                              // receiveEnergy(实际写)
            int accepted = storage.receive((int) Math.min(toMove, Integer.MAX_VALUE));
            if (accepted > 0) {
                remaining -= accepted;
                injectedTotal += accepted;
            }
        }
        // 尾差补一轮（复刻真实逻辑）
        if (remaining > 0L) {
            for (Integer flat : alive) {
                if (remaining <= 0L) {
                    break;
                }
                SimStorage storage = usable.get(flat);
                long room = storage.room();
                if (room <= 0L) {
                    continue;
                }
                simulateCalls++;
                receiveCalls++;
                int accepted = storage.receive((int) Math.min(room, remaining));
                if (accepted > 0) {
                    remaining -= accepted;
                    injectedTotal += accepted;
                }
            }
        }
        // 耗电追踪（复刻 prevEnergyLevels）
        for (Integer flat : alive) {
            simulateCalls++;                             // getEnergyStored
        }
    }

    /* ================= 基准引擎 ================= */

    private static final class Result {
        String label;
        long capQ, beQ, recvQ, simQ, keyQ, mapQ;
        long avgBytesNs;
    }

    private static Result runWaitForStable(int powerRange, int verticalRadius, int deviceCount) {
        Random rng = new Random(12345L);
        PowerGridPerfBenchmark b = new PowerGridPerfBenchmark().init(powerRange, verticalRadius, deviceCount, rng);
        // 稳态热身：让 consumers 稳定（=全部设备）、扫描退避到 MAX（电网无变动），并填满能力缓存。
        b.gameTime = 0;
        for (int t = 0; t < 800; t++) {
            b.gameTime++;
            b.resetCounters();
            b.maybeScanPerTick(b.gameTime);
            b.broadcastPerTick();
        }
        Result r = new Result();
        r.label = String.format("power=%d vRange=%d devices=%d range=%d", powerRange, verticalRadius,
                b.consumers.size(), b.rangeSize) + "  [退避后稳态]";
        // 统计 + 计时：连续 ITERS 个"稳态 tick"取平均。
        b.resetCounters();
        long ns = System.nanoTime();
        for (int t = 0; t < ITERS; t++) {
            b.gameTime++;
            b.maybeScanPerTick(b.gameTime);
            b.broadcastPerTick();
        }
        long elapsed = (System.nanoTime() - ns) / ITERS;
        r.capQ = b.capabilityQueries;
        r.beQ = b.blockEntityLookups;
        r.recvQ = b.receiveCalls;
        r.simQ = b.simulateCalls;
        r.keyQ = b.roleKeyAllocs;
        r.mapQ = b.mapGets;
        r.avgBytesNs = elapsed;
        return r;
    }

    /** 把 <b>累计 200 tick</b> 的调用计数按经验单次成本折算为【每 tick】纳秒。 */
    private static long estimatedTickNs(Result r) {
        return (r.capQ * NS_CAPABILITY + r.beQ * NS_BE_LOOKUP + r.recvQ * NS_RECEIVE
                + r.simQ * NS_SIMULATE + r.keyQ * NS_STRING + r.mapQ * NS_MAP_GET) / ITERS;
    }

    public static void main(String[] args) {
        int[][] matrix = {
                {12, 8, 128},
                {12, 8, 64},
                {24, 8, 128},
                {24, 8, 64},
                {32, 8, 128},
                {48, 8, 128},
                {64, 8, 128},
        };
        printHeader();
        for (int[] s : matrix) {
            Result r = runWaitForStable(s[0], s[1], s[2]);
            System.out.printf("%-46s%11d%10d%9d%9d%8d%11d  est=%7.3fms  est/50ms=%5.2f%%%n",
                    r.label, r.capQ, r.beQ, r.recvQ, r.simQ, r.keyQ, r.mapQ,
                    estimatedTickNs(r) / 1_000_000.0,
                    estimatedTickNs(r) / 50_000_000.0 * 100);
        }
        System.out.println();
        System.out.println("说明：");
        System.out.println("  est / est/50ms 已折算到【每 tick】（调用计数列为累计 200 tick 值）。");
        System.out.println("  est = 按经验单次成本折算的【单塔】每 tick 主线程耗时；");
        System.out.println("  est/50ms = 单塔占 20 TPS 整帧预算(50ms) 的百分比（不含多塔相乘）。");
        System.out.println("  多塔场景：把 est 乘以塔数 N 即得 N 塔电网的每 tick 主线程占用（假设线性叠加）。");
        System.out.println("  稳态热点：blockEntityLookups(扫描 getBlockEntity) 占绝对大头；");
        System.out.println("  getCapability 在【设备变动/新接入】时才爆发（本基准为稳态，故 getCap≈0）。");
    }

    private static void printHeader() {
        System.out.printf("%-46s%11s%10s%9s%9s%8s%11s  %s%n", "场景", "getCap", "getBe", "recv", "sim", "strKey", "mapGet", "折算");
        System.out.println("─".repeat(150));
    }

    /** 供 Gradle `:rtsbuilding-main:test` 一键跑的入口（展示完整矩阵，不依赖 IDE）。 */
    @org.junit.jupiter.api.Test
    void runPerfMatrix() {
        main(new String[0]);
    }
}
