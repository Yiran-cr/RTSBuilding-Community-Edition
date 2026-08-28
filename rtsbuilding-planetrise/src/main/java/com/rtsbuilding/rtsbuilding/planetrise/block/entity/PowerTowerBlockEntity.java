package com.rtsbuilding.rtsbuilding.planetrise.block.entity;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsDeviceRole;
import com.rtsbuilding.rtsbuilding.planetrise.EnergyBlockEntities;
import com.rtsbuilding.rtsbuilding.planetrise.PowerTowerWakeup;
import com.rtsbuilding.rtsbuilding.planetrise.block.PowerTowerBlock;
import com.rtsbuilding.rtsbuilding.planetrise.network.PowerGridServerHandler;
import com.rtsbuilding.rtsbuilding.planetrise.power.IPowerGridNode;
import com.rtsbuilding.rtsbuilding.planetrise.power.PowerGridManager;
import com.rtsbuilding.rtsbuilding.planetrise.power.PowerRole;
import com.rtsbuilding.rtsbuilding.planetrise.power.PowerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 输电塔方块实体——电网系统中<b>唯一能向用电机器广播供电</b>的建筑
 * （参照 power_system_design.md）。
 * <p>
 * 输电塔同时拥有：
 * <ul>
 *   <li><b>链路范围</b>（{@link Config#powerTowerLinkRange()}）：与附近发电机器/输电塔建立
 *       双向链路，延伸电网；</li>
 *   <li><b>供电范围</b>（{@link Config#powerTowerPowerRange()}）：从塔中心向外辐射的圆形区域，
 *       塔仅在此范围内向用电机器广播电力。</li>
 * </ul>
 * 自身<b>不发电</b>：塔在每次电网调度中从所连电网分得一笔<b>本 tick 供电配额</b>
 * （{@link PowerGridManager} 调用 {@link #acceptQuota(long)} 写入），然后在其供电范围内把
 * 配额广播给用电机器（任何可注入的标准 {@code IEnergyStorage} 方块）。电力经塔转发<b>不衰减</b>。
 * <p>
 * <b>需求计算</b>：每 tick 分片扫描供电范围，发现可注入能量的用电方块并统计其可吸收空间
 * （{@link #demand()}），供 {@link PowerGridManager} 按需求占比分配电网发电量。
 * <p>
 * 塔自身仍保留一个小型 FE 缓冲（容量 {@code Config.powerTowerCapacity()}），仅供外部管道/能力
 * 接口查看或充能；<b>不做</b>网络能量搬运（旧的全网吸取/分发已废弃）。
 * <p>
 * <b>多占位</b>：实现 {@link IBoundingBlock}，主方块位于塔基，上方 {@link #BOUNDING_HEIGHT}
 * 格为不可见占位方块（整塔 5 格高）。
 * <p>
 * 性能：供电范围分片扫描（{@link #SCAN_INTERVAL} tick 分摊），用电器只缓存坐标、每 tick
 * 实时查询能力（任意面 → 6 面回退，避开只读代理）。
 */
public class PowerTowerBlockEntity extends AbstractEnergyMachineBlockEntity implements IBoundingBlock, IPowerGridNode {

    /** 一轮全量供电范围扫描的间隔（tick）。 */
    private static final int SCAN_INTERVAL = 20;
    /** 同时跟踪的用电方块数量上限（防列表膨胀）。 */
    private static final int MAX_CONSUMERS = 128;

    // ── 扫描调度（优化：自适应退避降频）──
    /** 扫描周期下限（tick）：设备有变动/新接入时的密集发现频率（=SCAN_INTERVAL，保持原有体验）。 */
    private static final int SCAN_PERIOD_MIN = SCAN_INTERVAL;
    /** 扫描周期上限（tick）：电网稳定时退避到的低频兜底（10 秒扫一圈，仍是持续发现新设备只是延迟）。 */
    private static final int SCAN_PERIOD_MAX = 200;

    /** 塔身占位高度（主方块上方格数）。 */
    public static final int BOUNDING_HEIGHT = 4;
    /** 塔身占位偏移：主方块上方 1~4 格（整塔 5 格高）。 */
    private static final List<BlockPos> BOUNDING_OFFSETS = List.of(
            BlockPos.ZERO.above(1),
            BlockPos.ZERO.above(2),
            BlockPos.ZERO.above(3),
            BlockPos.ZERO.above(4));

    /** 方块能量能力缓存：{@code pos -> CachedStorage}，降低每 tick 能力查询开销。 */
    private final Map<BlockPos, CachedStorage> storageCache = new HashMap<>();

    /** 已发现的可注入用电方块坐标（分片扫描发现）。 */
    private final List<BlockPos> consumers = new ArrayList<>();

    /** 供电范围<b>标准球体</b>半径（格），创建时从配置读取一次（三轴同半径、高度=半径）。 */
    private final int rangeRadiusX;
    private final int rangeRadiusY;
    private final int rangeRadiusZ;
    /** 供电球体半径平方（球内判定用，避免每格开方/相乘溢出）。 */
    private final long rangeSq;
    private final int rangeX;
    private final int rangeY;
    private final int rangeZ;
    private final int rangeSize;

    /** 分片扫描游标（展平后的一维下标，0..rangeSize-1）。 */
    private int scanCursor;

    /** 最近一次<b>组级注入</b>本塔供电范围内用电器的总电量（FE/t）——塔本 tick 送出的电量。
     * <b>不含</b>从外部发电机<b>提取</b>的收入（{@link #externalGen}，另计）——提取属「进塔」、
     * 注入属「出塔」，二者不应相加（否则把输电与用电加在一起误报）。
     * 供塔设备行「输电 X/tick」展示。 */
    private long lastInjected;

    /** 最近一次广播各用电器实际注入量（FE/t），{@code pos -> 注入量}，供发电端（被提取的外部发电机）展示。 */
    private final Map<BlockPos, Long> injectedByConsumer = new HashMap<>();

    /** 本 tick 实际注入到各用电器位置的量（FE），仅供实测功耗测量使用（每 tick 清，独立于广播周期注入统计）。 */
    private final Map<BlockPos, Long> tickInjected = new HashMap<>();

    /** 各用电器上一采样 tick 的能量存量（FE），用于反推实测功耗 = 注入 + 存量下降。 */
    private final Map<BlockPos, Long> prevEnergyStored = new HashMap<>();

    /** 各用电器<b>实测每 tick 功耗</b>（FE/t），{@code pos -> 功耗}，反映机器真实吞电速率
     * （供电充足时≈满配方，不足时=降速后的实际值）。供设备列表/快照展示。 */
    private final Map<BlockPos, Long> measuredRates = new HashMap<>();

    /** 本塔供电范围内所有用电器实测功耗之和（FE/t），供 {@link #injectedRate()} 上报聚合（电网总耗电）。 */
    private long measuredTotal;

    /** 最近一次广播中各节点的<b>权威角色</b>（实际按注入/提取处理），{@code pos -> 角色}。
     * <p>方向由玩家显式标记优先（{@link PowerGridServerHandler#getDeviceRoleOverride}），无覆盖时按能力
     * 自动判定（可注入→用电，否则可提取→发电）。供快照 {@code collectDevices} 复用，使设备列表的
     * 角色与实际供电流向完全一致（权威级设计）。</p> */
    private final Map<BlockPos, RtsDeviceRole> actualRoleByPos = new HashMap<>();

    /** 本 tick 从<b>外部</b>（非组网）可提取设备累计提取并入电网的能量（FE）。
     * 属「进塔」方向——上报为<b>电网发电</b>（{@link #externalGeneration()} 随调度器并入
     * {@code totalGeneration}，经 PowerScheduler 聚合后由组级广播器按需求占比分配给本电网各用电器）。
     * 同时计入电网总览「总发电」（见 {@link PowerGridManager#aggregate}）。 */
    private long externalGen = 0L;

    /** 上一轮清理用电器列表的游戏时刻（节流用）。 */
    private long lastPruneGameTime = Long.MIN_VALUE;

    /** 扫描冷却门：进入下一扫描窗口的最早游戏时刻。电网稳定时把窗口拉开（退避降频），
     * 有变动/新设备时置为当前时刻立即重新扫描。 */
    private long scanGateTick = Long.MIN_VALUE;
    /** 当前扫描周期（tick）：设备未变化时逐轮翻倍退避至 {@link #SCAN_PERIOD_MAX}（省扫描成本）。 */
    private int scanPeriod = SCAN_PERIOD_MIN;
    /** 当前是否处于「扫描窗口」内：窗口为连续 {@link #SCAN_INTERVAL} tick 分摊扫完一整圈。 */
    private boolean scanWindowActive;
    /** 本扫描窗口内是否发现过新设备（用于决定本轮收窄为快扫）。 */
    private boolean scanRoundAdded;

    /** 上一 tick 统计的供电范围内可注入用电方块的<b>空闲容量总和</b>（FE）。
     * <p>{@link #demand()} 直接读本缓存，避免每 tick 对所有 consumer 重新做一次能力查询
     * （roomOf → findUsableStorage）。缓存由 {@link #broadcast()} 注入循环顺带刷新，
     * 与真实注入共用一次能力读取，从而消除「调度阶段需求计算 + 广播阶段注入」的重复 World/能力访问。
     * 最多滞后一个心跳周期，对 1 Hz 快照展示无感。 */
    private long cachedTotalRoom;

    public PowerTowerBlockEntity(BlockPos pos, BlockState state) {
        super(EnergyBlockEntities.POWER_TOWER.get(), pos, state, Config.powerTowerCapacity());
        long powerRange = Config.powerTowerPowerRange();
        // 标准球体：三轴同半径，高度 = 半径（不再用单独的竖直半径，1.1.4 起供电范围为球体）。
        this.rangeRadiusX = (int) powerRange;
        this.rangeRadiusY = (int) powerRange;
        this.rangeRadiusZ = (int) powerRange;
        this.rangeSq = powerRange * powerRange;
        // 枚举量仍为外接立方体，实际以球体半径判定（rangeSq）过滤球外格子，降低 World 查询。
        this.rangeX = rangeRadiusX * 2 + 1;
        this.rangeY = rangeRadiusY * 2 + 1;
        this.rangeZ = rangeRadiusZ * 2 + 1;
        this.rangeSize = rangeX * rangeY * rangeZ;
    }

    // ---- IBoundingBlock：多占位 ----

    /** 塔身占位：主方块上方 1~4 格。 */
    @Override
    public List<BlockPos> boundingOffsets() {
        return BOUNDING_OFFSETS;
    }

    /** 整塔碰撞形状（跨 5 格），由主方块/占位方块共享并各自平移。 */
    @Override
    public VoxelShape proxyShape() {
        return PowerTowerBlock.SHAPE;
    }

    /** 占位格子（塔身）也可被外部接口检查：暴露与主方块相同的缓冲。 */
    @Override
    public IEnergyStorage getBoundingEnergyStorage(@Nullable Direction side, BlockPos offset) {
        return createEnergyStorage(true, true);
    }

    /** 供电范围内有方块放置/破坏事件时由 {@link PowerTowerWakeup} 调用：立即唤醒重扫（收窄扫描周期），
     * 使新接入设备的供电响应从「退避稳态的最长 {@link #SCAN_PERIOD_MAX} tick（10 秒）」降为
     * 「下个 tick 起扫一圈（≤{@link #SCAN_INTERVAL} tick / 1 秒）」。退避在稳定期仍省主线程成本，
     * 事件唤醒只在供电范围发生方块变化时触发。 */
    public void forceRescan() {
        if (level == null || level.isClientSide) {
            return;
        }
        scanWindowActive = false;
        scanPeriod = SCAN_PERIOD_MIN;
        scanGateTick = level.getGameTime();
    }

    /** 某世界坐标是否位于本塔供电<b>球体</b>内（三轴同半径、高度=半径的标准球体）。
     * <p>复用 {@link PowerScheduler#isCoveredBySupplyRange} 权威判定，与客户端供电范围圈渲染同一份规则。 */
    public boolean isInSupplyRange(BlockPos pos) {
        return PowerScheduler.isCoveredBySupplyRange(
                pos.getX(), pos.getY(), pos.getZ(),
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                rangeRadiusX);
    }

    /** 供电球体水平半径（X/Z 方向，供空间索引计算覆盖单元）。 */
    public int supplyRadiusX() {
        return rangeRadiusX;
    }

    /** 供电球体竖直半径（Y 方向，等于水平半径——标准球体）。 */
    public int supplyRadiusY() {
        return rangeRadiusY;
    }

    /** 供电球体水平半径（X/Z 方向，供空间索引计算覆盖单元）。 */
    public int supplyRadiusZ() {
        return rangeRadiusZ;
    }

    /** 装载时注册到事件唤醒控制器（供电范围变化感知）。 */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            PowerTowerWakeup.register(serverLevel, this);
        }
    }

    /** 卸载时从事件唤醒控制器注销。 */
    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            PowerTowerWakeup.unregister(serverLevel, this);
        }
        super.setRemoved();
    }

    /** 服务端每 tick：清理失效用电器 → 自适应节流扫描供电范围 → 注册并驱动电网调度。
     * <p>扫描采用<b>自适应退避</b>（见 {@link #maybeScan}）：电网稳定时不逐 tick 轮询，显著降低大型
     * 电网每 tick 的 {@code getBlockEntity}/getCapability 成本（原持续扫描是大范围塔的主线程热点）。
     * <p><b>覆盖融合统一供电</b>：本塔不再自主广播——实际注入由 {@link PowerGridManager} 的组级
     * 广播器经 {@link #coveredConsumers()}/{@link #consumerRoom(BlockPos)}/{@link #injectTo(BlockPos, long)}
     * 统一执行（把本塔扫描发现的用电器并入电网联合覆盖区）。 */
    @Override
    protected void onServerTick() {
        if (level instanceof ServerLevel serverLevel) {
            // 定期清理已被移除的用电器坐标（防残留在列表中 → 快照显示「空气」），节流降低能力查询开销。
            long now = serverLevel.getGameTime();
            if (now - lastPruneGameTime >= SCAN_INTERVAL) {
                lastPruneGameTime = now;
                if (pruneConsumers()) {
                    // 有失效设备被移除：清出名额 → 收窄周期并立即重扫补位。
                    scanWindowActive = false;
                    scanPeriod = SCAN_PERIOD_MIN;
                    scanGateTick = now;
                }
            }
            maybeScan(serverLevel, now);
            PowerGridManager mgr = PowerGridManager.get(serverLevel);
            mgr.register(worldPosition, this);
            // 组级统一广播：广播内部会先 reset 各塔注入统计，再注入填充，统计保留至下次广播（面板稳定读取）。
            // 注意：不在本塔每 tick 清零 lastInjected/injectedByConsumer/actualRoleByPos，否则会清掉
            // 「由其它节点触发广播」时本塔已被写入的注入量，导致面板耗电常读 0。
            mgr.tick(serverLevel);
            // 先提取外部发电机电量（上报为电网发电）——移到广播之后，避免广播开头的 resetInjectionStats
            // 清除掉刚写入的发电机提取记录；提取量以覆盖式（put）写入 injectedByConsumer，不随广播周期累积。
            extractExternals();
            // 刷新需求/耗电统计缓存（供快照 demand/injectedRate 展示）。
            refreshStats();
        }
    }

    /**
     * <b>自适应退避</b>驱动的供电范围扫描。每个扫描周期（{@link #scanPeriod}）内用
     * {@link #SCAN_INTERVAL} tick 分摊扫完一整圈，其余 tick 完全跳过（零扫描成本）。
     * <ul>
     *   <li>本圈发现新设备 → 周期收窄回 {@link #SCAN_PERIOD_MIN}（密集发现）；</li>
     *   <li>本圈无新增 → 周期翻倍退避至 {@link #SCAN_PERIOD_MAX}（稳定电网低频兜底）。</li>
     * </ul>
     * 收益：稳态扫描成本降到原来的 {@code SCAN_INTERVAL/scanPeriod}（≤1/10）。
     * 发现新设备的延迟最多 {@link #SCAN_PERIOD_MAX} tick（10 秒），对持续供电的电网可接受。
     */
    private void maybeScan(ServerLevel level, long now) {
        // 满载：无需再发现新设备（scanRange 满载本就 return），关闭窗口、零成本。
        if (consumers.size() >= MAX_CONSUMERS) {
            scanWindowActive = false;
            return;
        }
        if (!scanWindowActive) {
            if (now < scanGateTick) {
                return;                          // 冷却期：完全跳过，零扫描成本。
            }
            scanWindowActive = true;
            scanRoundAdded = false;
        }
        if (scanRange()) {                       // 已完成一整圈
            scanWindowActive = false;
            scanPeriod = scanRoundAdded ? SCAN_PERIOD_MIN
                    : Math.min(scanPeriod * 2, SCAN_PERIOD_MAX);
            scanGateTick = now + scanPeriod;
        }
    }

    // ==================== 组级统一广播（覆盖融合）接口 ====================
    // 本塔不再自主广播。PowerGridManager 的组级广播器把本塔扫描发现的用电器并入电网联合覆盖区，
    // 用电网总发电按各用电器需求占比统一注入。本塔只负责：扫描发现、暴露覆盖清单、按位置注入/取缺口。

    /** 供电范围内<b>可用电器</b>坐标清单（可注入，非外部发电机）——供组级广播器做联合覆盖去重。 */
    @Override
    public List<BlockPos> coveredConsumers() {
        List<BlockPos> out = new ArrayList<>();
        if (level == null) {
            return out;
        }
        for (BlockPos pos : consumers) {
            if (isInjectableConsumer(pos)) {
                out.add(pos);
            }
        }
        return out;
    }

    /** 指定用电器位置的<b>可注入缺口</b>（FE）；不可注入/无能力返回 0。供组级广播按需求占比分配。 */
    @Override
    public long consumerRoom(BlockPos pos) {
        if (level == null) {
            return 0L;
        }
        IEnergyStorage storage = findUsableStorage(pos, false);
        if (storage == null) {
            return 0L;
        }
        return Math.max(0L, storage.getMaxEnergyStored() - storage.getEnergyStored());
    }

    /** 向指定用电器位置<b>注入</b>电量（组级广播调用）。复用能力判定与注入逻辑，返回实际注入量。 */
    @Override
    public long injectTo(BlockPos pos, long amount) {
        if (level == null || amount <= 0L) {
            return 0L;
        }
        IEnergyStorage storage = findUsableStorage(pos, false);
        if (storage == null) {
            return 0L;
        }
        long room = Math.max(0L, storage.getMaxEnergyStored() - storage.getEnergyStored());
        long toMove = Math.min(amount, room);
        if (toMove <= 0L) {
            return 0L;
        }
        int accepted = storage.receiveEnergy((int) Math.min(toMove, Integer.MAX_VALUE), false);
        if (accepted > 0) {
            injectedByConsumer.merge(pos, (long) accepted, Long::sum);
            tickInjected.merge(pos, (long) accepted, Long::sum);
            lastInjected = saturatingAdd(lastInjected, accepted);
            actualRoleByPos.put(pos, RtsDeviceRole.CONSUMER);
        }
        return accepted;
    }

    /** 该位置是否<b>可注入用电端</b>（有真实可注入能力；非外部发电机）。 */
    private boolean isInjectableConsumer(BlockPos pos) {
        if (level == null) {
            return false;
        }
        RtsDeviceRole override = PowerGridServerHandler.getDeviceRoleOverride(level.dimension(), pos);
        if (override == RtsDeviceRole.GENERATOR) {
            return false;
        }
        IEnergyStorage storage = findUsableStorage(pos, false);
        if (storage == null) {
            return false;
        }
        return storage.getMaxEnergyStored() > 0L
                && storage.getEnergyStored() < storage.getMaxEnergyStored();
    }

    /**
     * 供电范围内<b>外部</b>发电机提取：扫描 {@link #consumers}，把标记为发电/不可注入但可提取的设备
     * 提取注入电网（{@link #externalGen} 上报为电网发电）。供电网聚合时被 {@code PowerScheduler}
     * 计入 {@code totalGeneration}。这是塔在 onServerTick 中仍需执行的部分（注入由组级广播完成）。
     */
    private void extractExternals() {
        if (level == null || consumers.isEmpty()) {
            externalGen = 0L;
            return;
        }
        long gen = 0L;
        for (BlockPos pos : consumers) {
            RtsDeviceRole override = PowerGridServerHandler.getDeviceRoleOverride(level.dimension(), pos);
            boolean isExternal = false;
            if (override == RtsDeviceRole.GENERATOR) {
                isExternal = true;
            } else if (override != RtsDeviceRole.CONSUMER) {
                // 自动判定<b>外部发电机</b>：必须「确实能提取」且「不支持接收能量」（纯输出设备）。
                // 判别依据：真正的外部发电机其能量槽 {@code canReceive()==false}（只出不进）；
                // 而<b>缓冲已满（room=0）</b>的用电机器虽然当前注入失败（模拟 receiveEnergy 无效），
                // 但仍可充电（{@code canReceive()==true}）——若仅凭「不可注入 + 可提取」判定，
                // 会把满电用电器误判为发电机：能量被无故提取、实测功耗被跳过，设备列表「用电」恒显示 0
                // （本模组储能单元充电满后即落入此坑）。
                // findUsableStorage(..., true) 已剔除只读代理（Mek 对 side=null 的 canReceive 恒 true），
                // 返回值是真实可提取面，其 canReceive 可靠反映该端口的接收方向。
                IEnergyStorage extract = findUsableStorage(pos, true);
                if (extract != null && !extract.canReceive()) {
                    isExternal = true;
                }
            }
            if (isExternal) {
                // 记录权威角色为发电端，供快照 collectDevices 显示（无玩家显式覆盖时）。
                actualRoleByPos.put(pos, RtsDeviceRole.GENERATOR);
                gen = saturatingAdd(gen, extractFrom(pos));
            }
        }
        externalGen = gen;
    }

    /** 更新本 tick 需求/耗电统计缓存（供 {@link #demand()}/{@link #injectedRate()} 快照展示）。
     * <p>一次遍历顺带完成两件事：①累计可注入缺口（{@code cachedTotalRoom}，容量语义，供
     * {@link #demand()} 展示）；②<b>实测每 tick 功耗</b>（{@code measuredRates}）——对供电范围内
     * 非发电端用电器，按「本 tick 注入 + 上一采样存量 − 当前存量」反推其真实吞电速率，稳定反映机器
     * 实际功耗（供电不足时机器降速，测得值即降速后实际值），并累加 {@code measuredTotal}。</p> */
    private void refreshStats() {
        if (level == null) {
            cachedTotalRoom = 0L;
            measuredTotal = 0L;
            return;
        }
        long roomSum = 0L;
        long consSum = 0L;
        for (BlockPos pos : consumers) {
            if (actualRoleByPos.get(pos) == RtsDeviceRole.GENERATOR) {
                // 发电端（被塔提取）：存量下降属「出能」，不计入用电；清除其功耗/存量基准防误判。
                prevEnergyStored.remove(pos);
                measuredRates.remove(pos);
                tickInjected.remove(pos);
                continue;
            }
            IEnergyStorage storage = readStorageForStats(pos);
            if (storage == null) {
                // 能力失效：清理该位置统计（防止残留导致快照报出「空气」或虚耗电）。
                prevEnergyStored.remove(pos);
                measuredRates.remove(pos);
                tickInjected.remove(pos);
                continue;
            }
            long cur = storage.getEnergyStored();
            long max = storage.getMaxEnergyStored();
            long room = Math.max(0L, max - cur);
            if (room > 0L) {
                roomSum = saturatingAdd(roomSum, room);
            }
            // 实测功耗：本 tick 注入 + 上一采样存量 − 当前存量。首帧无基准仅记录，不误判。
            Long prev = prevEnergyStored.get(pos);
            if (prev == null) {
                prevEnergyStored.put(pos, cur);
                measuredRates.remove(pos);
            } else {
                long consumption = tickInjected.getOrDefault(pos, 0L) + prev - cur;
                if (consumption < 0L) {
                    consumption = 0L;
                }
                measuredRates.put(pos, consumption);
                consSum = saturatingAdd(consSum, consumption);
                prevEnergyStored.put(pos, cur);
            }
            tickInjected.remove(pos);
        }
        cachedTotalRoom = roomSum;
        measuredTotal = consSum;
    }

    /** 读取指定位置的能量存量（不限可注入，用于功耗测量）。优先复用 {@link #storageCache} 命中，
     * 失效则按探测方向取首个非空能力并缓存，避免每 tick 重复 capability 查询（扫描热点）。 */
    @Nullable
    private IEnergyStorage readStorageForStats(BlockPos pos) {
        CachedStorage cached = storageCache.get(pos);
        if (cached != null && level.getBlockEntity(pos) == cached.owner) {
            return cached.storage;
        }
        for (Direction side : PROBE_SIDES) {
            IEnergyStorage storage = queryStorage(pos, side);
            if (storage != null) {
                storageCache.put(pos, new CachedStorage(storage, level.getBlockEntity(pos)));
                return storage;
            }
        }
        return null;
    }

    /** 从指定设备提取能量（权威发电端：玩家标记发电 或 自动识别的外部发电机）。
     * <p>限制单台不超过塔吞吐量/ tick；提取量同时计入 {@link #externalGen}（并入本 tick 供电力）与
     * {@link #injectedByConsumer}（供设备列表展示该发电机「发电」量）。返回本 tick 实际提取量。</p>
     * <p><b>仅提取非本模组组网节点</b>的外部发电机能量：本模组发电机/输电塔等 {@link IPowerGridNode}
     * 的产电已由其 {@link IPowerGridNode#generation()} 计入电网聚合与分配，不应再被塔作为「外部设备」
     * 单独提取，否则会造成电网总览发电双算与重复搬运。</p> */
    private long extractFrom(BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof IPowerGridNode) {
            return 0L;
        }
        IEnergyStorage src = findUsableStorage(pos, true);
        if (src == null) {
            return 0L;
        }
        long maxPull = Math.min(src.getEnergyStored(), Math.min(throughput(), Integer.MAX_VALUE));
        int pulled = src.extractEnergy((int) maxPull, false);
        if (pulled > 0) {
            externalGen = saturatingAdd(externalGen, pulled);
            // 覆盖式记录发电机本周期的提取量（供设备列表展示），而非 merge 累加——injectedByConsumer 只在
            // 广播开始时清一次，跨 tick 的 merge 会让发电机条目不断累积，必须每 tick 以最新值覆盖。
            injectedByConsumer.put(pos, (long) pulled);
        }
        return pulled;
    }

    /**
     * 扫描供电范围中的一小段，发现可注入的用电方块加入列表。
     * <p><b>优化</b>：每格只执行 <b>1 次</b> {@code getBlockEntity}（原实现对同一格重复查询 3 次），
     * 结果同时用于：归一化多占位主方块、空判断、组网节点排除。</p>
     *
     * @return 本次调用是否<b>完成了一整圈扫描</b>（游标回绕），供退避调度评估。
     */
    private boolean scanRange() {
        if (level == null || rangeSize <= 0) {
            return false;
        }
        if (consumers.size() >= MAX_CONSUMERS) {
            return false;
        }
        int perTick = Math.max(1, (int) Math.ceil(rangeSize / (double) SCAN_INTERVAL));
        for (int i = 0; i < perTick && scanCursor < rangeSize; i++, scanCursor++) {
            // 标准球体：先按半径过滤球外格子（绕过 World 查询），再对外接立方体内剩余格子做真实扫描。
            BlockPos off = offsetForIndex(scanCursor);
            long dx = off.getX(), dy = off.getY(), dz = off.getZ();
            if (dx * dx + dy * dy + dz * dz > rangeSq) {
                continue;
            }
            BlockPos raw = worldPosition.offset(off);
            if (!level.isLoaded(raw)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(raw);   // 每格仅 1 次（优化：合并原重复查询）
            if (be == null) {
                continue;                                  // 空气/无方块实体：早退，不查能力。
            }
            // 归一化：把多占位方块的占位格归一到主方块坐标，避免同一能量实体被重复当作独立用电器。
            // 同时更新 be 到主方块实体，使后续 IPowerGridNode 检查能正确识别多占位组网节点（如风力发电机）。
            BlockPos pos;
            if (be instanceof BoundingBlockEntity bounding) {
                pos = bounding.getMainPos();
                be = level.getBlockEntity(pos);
                if (be == null) {
                    continue;
                }
            } else {
                pos = raw;
            }
            if (pos.equals(worldPosition) || consumers.contains(pos)) {
                continue;
            }
            // 跳过<b>本模组组网节点</b>（发电机/另一输电塔）：它们是电网框架节点，不属于「供电范围用电器」。
            // 其产电已由 {@link IPowerGridNode#generation()} 声明进电网、塔间互供走组网而非供电范围。若混入
            // consumers 会导致：设备列表重复出现组网节点、塔把其它塔当用电器注电、塔默认从发电机 buffer
            // 攻电（造成电网发电双算）。
            if (be instanceof IPowerGridNode) {
                continue;
            }
            // 仅以「存在能量能力」作为轻量判据加入列表——不在这里做模拟注入（canInteract）验证，
            // 显著降低大型电网每 tick 的能力查询成本（null 面优先命中即返回，避免 7 面回退 + 模拟注入）。
            // 注入/提取的严格区分由 broadcast 的 findUsableStorage 兜底分类（不可注入者按外部发电机处理）。
            if (hasEnergyCapability(pos)) {
                consumers.add(pos);
                scanRoundAdded = true;
            }
        }
        if (scanCursor >= rangeSize) {
            scanCursor = 0;
            return true;
        }
        return false;
    }

    /**
     * 查找指定位置<b>确实能按用途交互</b>的能量能力对象，并把验证有效的那个按位置缓存。
     * <p>
     * 无法信任 {@code canReceive()} 标志（Mekanism 等对 side=null 会返回只读代理，canReceive
     * 恒 true 但注入全拒），因此以<b>模拟注入 &gt; 0</b> 作为真实可交互判据；先查缓存（仍有效则
     * 复用），失效则按「任意面 → 6 面回退」逐面探测。
     *
     * @param pos 目标位置。
     * @return 验证可注入的能力对象；任意面均无法注入时返回 {@code null}。
     */
    @Nullable
    private IEnergyStorage findUsableStorage(BlockPos pos, boolean wantExtract) {
        CachedStorage cached = storageCache.get(pos);
        if (cached != null && level.getBlockEntity(pos) == cached.owner && canInteract(cached.storage, wantExtract)) {
            return cached.storage;
        }
        for (Direction side : PROBE_SIDES) {
            IEnergyStorage storage = queryStorage(pos, side);
            if (storage != null && canInteract(storage, wantExtract)) {
                storageCache.put(pos, new CachedStorage(storage, level.getBlockEntity(pos)));
                return storage;
            }
        }
        return null;
    }

    /** 探测方向顺序：先任意面（null），再 6 个实心面——避开只读代理、命中真实可交互的注入面。 */
    private static final Direction[] PROBE_SIDES = {
            null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    /** 判断能力对象按用途是否<b>能真正交互</b>（模拟注入 &gt; 0）。 */
    private boolean canInteract(IEnergyStorage storage, boolean wantExtract) {
        if (wantExtract) {
            long stored = storage.getEnergyStored();
            return storage.canExtract() && stored > 0
                    && storage.extractEnergy((int) Math.min(stored, Integer.MAX_VALUE), true) > 0;
        }
        long room = (long) storage.getMaxEnergyStored() - storage.getEnergyStored();
        return storage.canReceive() && room > 0
                && storage.receiveEnergy((int) Math.min(room, Integer.MAX_VALUE), true) > 0;
    }

    /** 实际执行能力查询（不做缓存）：在指定面查询标准 FE 能力。 */
    @Nullable
    private IEnergyStorage queryStorage(BlockPos pos, @Nullable Direction side) {
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
    }

    /**
     * 该位置是否仍存在<b>任何</b>能量能力方块（不要求可注入）。
     * <p>用于区分「用电方块仍在但已充满电」与「方块已被移除」：前者应保留在
     * {@link #consumers} 列表中供设备列表展示，后者应清理。
     */
    private boolean hasEnergyCapability(BlockPos pos) {
        for (Direction side : PROBE_SIDES) {
            if (queryStorage(pos, side) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 清理 {@link #consumers} 中<b>已被移除</b>（能量能力彻底失效）的用电器坐标。
     * <p>仅移除「方块不存在」的条目；仍在但满电的保留（满电设备仍需在列表中展示，
     * 其能力对象仍在，只为使其不被误删）。被挖掉的方块因能力彻底失效而被清理，
     * 从而避免设备列表残留坐标导致快照渲染出「空气」。
     *
     * @return 是否有失效设备被移除（供上层决定是否收窄周期、立即重扫补位）。
     */
    private boolean pruneConsumers() {
        if (level == null || consumers.isEmpty()) {
            return false;
        }
        int before = consumers.size();
        consumers.removeIf(pos -> !hasEnergyCapability(pos));
        return consumers.size() < before;
    }

    /** 该坐标是否仍是一个有效的用电器（能量能力仍在），供服务端快照/设备列表过滤用。 */
    public boolean isConsumerAlive(BlockPos pos) {
        return hasEnergyCapability(pos);
    }

    /** 缓存的能力对象及其来源方块实体：位置指向同一实体时才视为有效。 */
    private static final class CachedStorage {
        @Nullable
        final IEnergyStorage storage;
        @Nullable
        final BlockEntity owner;

        CachedStorage(@Nullable IEnergyStorage storage, @Nullable BlockEntity owner) {
            this.storage = storage;
            this.owner = owner;
        }
    }

    /** 把展平下标解码为相对塔位置的偏移。 */
    private BlockPos offsetForIndex(int index) {
        int dz = index % rangeZ - rangeRadiusZ;
        int dy = (index / rangeZ) % rangeY - rangeRadiusY;
        int dx = index / (rangeZ * rangeY) - rangeRadiusX;
        return new BlockPos(dx, dy, dz);
    }

    // ---- 电网节点（输电塔）----

    /** 输电塔。 */
    @Override
    public PowerRole role() {
        return PowerRole.TOWER;
    }

    /** 输电塔链路范围（用于与发电机器/其它塔组网）。 */
    @Override
    public long linkRange() {
        return Config.powerTowerLinkRange();
    }

    /** 输电塔供电范围（竖直方向沿用 {@code powerTowerVerticalRadius}）。 */
    @Override
    public long powerRange() {
        return Config.powerTowerPowerRange();
    }

    /** 输电塔最大吞吐（FE/t）。 */
    @Override
    public long throughput() {
        return Config.powerTowerThroughput();
    }

    /**
     * 重置本广播周期的注入/提取统计（供 {@link PowerGridManager} 在每次组级广播开始时调用）。
     * <p>仅清「出塔注入/权威角色」统计；外部发电机提取量 {@link #externalGen} 不在此清，由
     * {@link #extractExternals()} 每 tick 覆盖重算（它位于广播之后执行）。</p>
     */
    @Override
    public void resetInjectionStats() {
        lastInjected = 0L;
        injectedByConsumer.clear();
        actualRoleByPos.clear();
    }

    /** 输电塔不发电。 */
    @Override
    public long generation() {
        return 0L;
    }

    /** 供电范围内用电器需求速率（读取 {@link #refreshStats()} 刷新的 {@link #cachedTotalRoom} 缓存）。
     * <p>组级统一广播下需求分配按用电器 {@link #consumerRoom(BlockPos)} 实时统计，本值仅为快照展示。 */
    @Override
    public long demand() {
        return cachedTotalRoom;
    }

    /** 某位置最近一次广播<b>实际提取</b>（外部发电机）或<b>注入</b>的量（FE/t），供快照对发电端展示。 */
    public long consumerInjectedRate(BlockPos pos) {
        return injectedByConsumer.getOrDefault(pos, 0L);
    }

    /** 某用电器<b>实测每 tick 功耗</b>（FE/t）——机器真实且稳定的吞电速率，供快照「耗电」展示。
     * <p>由 {@link #refreshStats()} 每 tick 按「注入 + 存量变化」反推；供电不足时即机器降速后的实际值。 */
    public long consumerConsumptionRate(BlockPos pos) {
        return measuredRates.getOrDefault(pos, 0L);
    }

    /** 某节点在最近一次广播中实际担当的<b>权威角色</b>（注入→用电 / 提取→发电）。
     * 供快照 {@code collectDevices} 复用；从未广播过的节点默认返回 {@link RtsDeviceRole#CONSUMER}。 */
    public RtsDeviceRole actualRoleAt(BlockPos pos) {
        RtsDeviceRole role = actualRoleByPos.get(pos);
        return role == null ? RtsDeviceRole.CONSUMER : role;
    }

    /** 本塔供电范围内所有用电器<b>实测功耗</b>合计（FE/t）——真实的用电需求速率，供电网总耗电展示。 */
    @Override
    public long injectedRate() {
        return measuredTotal;
    }

    /** 输电塔从供电范围内<b>外部发电机</b>（可提取设备）提取并注入电网的产电贡献（FE/t）。
     * <p>即最近一次广播从外部设备<b>提取</b>的总量（{@link #externalGen}）。此类电力属「外部模组
     * 设备经塔中转」——作为<b>电网发电</b>上报（{@code PowerScheduler} 会把它并入 {@code totalGeneration}
     * 参与跨塔配额分配），同时计入电网总览「总发电」以体现外部设备的输电贡献
     * （见 {@link PowerGridManager#aggregate}）。</p> */
    @Override
    public long externalGeneration() {
        return externalGen;
    }

    /** 本塔最近一次广播<b>实际输送（注入）</b>供电范围内用电器的总电量（FE/t），即 {@link #lastInjected}。
     * <p>纯「出塔」方向：从外部发电机<b>提取</b>的进塔能量（{@link #externalGeneration()}）不计入，
     * 避免把「输电（进）」与「用电（出）」相加造成误报。供设备列表「输电 X/tick」展示。 */
    public long actualTransferredRate() {
        return lastInjected;
    }

    /** 供电范围内已发现的用电器坐标（财务快照/设备列表用）。 */
    public java.util.List<BlockPos> consumers() {
        return java.util.Collections.unmodifiableList(consumers);
    }

    /** 饱和加法。 */
    private static long saturatingAdd(long a, long b) {
        long sum = a + b;
        return sum < a ? Long.MAX_VALUE : sum;
    }
}
