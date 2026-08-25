package com.rtsbuilding.rtsbuilding.planetrise.block.entity;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.planetrise.EnergyBlockEntities;
import com.rtsbuilding.rtsbuilding.planetrise.block.PowerTowerBlock;
import com.rtsbuilding.rtsbuilding.planetrise.power.IPowerGridNode;
import com.rtsbuilding.rtsbuilding.planetrise.power.PowerGridManager;
import com.rtsbuilding.rtsbuilding.planetrise.power.PowerRole;
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

    /** 供电范围半径（格），创建时从配置读取一次。 */
    private final int rangeRadiusX;
    private final int rangeRadiusY;
    private final int rangeRadiusZ;
    private final int rangeX;
    private final int rangeY;
    private final int rangeZ;
    private final int rangeSize;

    /** 分片扫描游标（展平后的一维下标，0..rangeSize-1）。 */
    private int scanCursor;

    /** 电网调度器分给本塔的<b>上 tick 供电配额</b>（FE/t）。 */
    private long lastQuota;

    public PowerTowerBlockEntity(BlockPos pos, BlockState state) {
        super(EnergyBlockEntities.POWER_TOWER.get(), pos, state, Config.powerTowerCapacity());
        long powerRange = Config.powerTowerPowerRange();
        this.rangeRadiusX = (int) powerRange;
        this.rangeRadiusY = Config.powerTowerVerticalRadius();
        this.rangeRadiusZ = (int) powerRange;
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

    /** 服务端每 tick：扫描供电范围内用电器 → 上报需求 → 驱动电网调度 → 广播上 quota。 */
    @Override
    protected void onServerTick() {
        scanRange();
        if (level instanceof ServerLevel serverLevel) {
            PowerGridManager mgr = PowerGridManager.get(serverLevel);
            mgr.register(worldPosition, this);
            // 调度会读取本塔最新 demand() 并为所有塔计算 quota，随后回调 acceptQuota 更新 lastQuota。
            mgr.tick(serverLevel);
        }
        broadcast();
    }

    /** 用上一 tick 分得的配额向供电范围内用电方块广播注能。 */
    private void broadcast() {
        if (lastQuota <= 0L || level == null || consumers.isEmpty()) {
            return;
        }
        long remaining = lastQuota;
        int processed = 0;
        while (remaining > 0 && processed < MAX_CONSUMERS && !consumers.isEmpty()) {
            int best = pickBestRoom();          // 选取「最需要」的用电方块（剩余空间最大）。
            if (best < 0) {
                break;
            }
            BlockPos pos = consumers.get(best);
            IEnergyStorage storage = findUsableStorage(pos, false);
            long room = storage == null ? 0L
                    : (long) storage.getMaxEnergyStored() - storage.getEnergyStored();
            if (storage == null || room <= 0L) {
                consumers.remove(best);
                continue;
            }
            long toMove = Math.min(room, remaining);
            int accepted = storage.receiveEnergy((int) Math.min(toMove, Integer.MAX_VALUE), false);
            if (accepted > 0) {
                remaining -= accepted;
            }
            processed++;
        }
        lastQuota = 0L;                    // 本 tick 配额已用尽。
    }

    /** 返回「剩余可注入空间最大」的用电方块下标；无满足条件的返回 -1。 */
    private int pickBestRoom() {
        int bestIdx = -1;
        long bestRoom = 0L;
        for (int i = 0; i < consumers.size(); i++) {
            long room = roomOf(consumers.get(i));
            if (room > bestRoom) {
                bestRoom = room;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /** 查询某用电方块的可注入空间（0 表示已满/不可注入）。 */
    private long roomOf(BlockPos pos) {
        IEnergyStorage storage = findUsableStorage(pos, false);
        return storage == null ? 0L : (long) storage.getMaxEnergyStored() - storage.getEnergyStored();
    }

    /** 扫描供电范围中的一小段，发现可注入的用电方块加入列表。 */
    private void scanRange() {
        if (level == null || rangeSize <= 0) {
            return;
        }
        if (consumers.size() >= MAX_CONSUMERS) {
            return;
        }
        int perTick = Math.max(1, (int) Math.ceil(rangeSize / (double) SCAN_INTERVAL));
        for (int i = 0; i < perTick && scanCursor < rangeSize; i++, scanCursor++) {
            BlockPos raw = worldPosition.offset(offsetForIndex(scanCursor));
            if (!level.isLoaded(raw)) {
                continue;
            }
            // 归一化：把多占位方块的占位格归一到主方块坐标，避免同一能量实体被重复当作独立用电器。
            BlockPos pos = normalizeMain(raw);
            if (pos.equals(worldPosition) || consumers.contains(pos)) {
                continue;
            }
            if (level.getBlockEntity(raw) == null) {
                continue;
            }
            if (findUsableStorage(raw, false) != null) {
                consumers.add(pos);
            }
        }
        if (scanCursor >= rangeSize) {
            scanCursor = 0;
        }
    }

    /** 把坐标归一化为其所属多占位结构的主方块坐标（见 {@link PowerTowerBlockEntity} 扫描注释）。 */
    private BlockPos normalizeMain(BlockPos pos) {
        if (level == null) {
            return pos;
        }
        if (level.getBlockEntity(pos) instanceof BoundingBlockEntity bounding) {
            return bounding.getMainPos();
        }
        return pos;
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

    /** 输电塔不发电。 */
    @Override
    public long generation() {
        return 0L;
    }

    /** 供电范围内用电器需求速率（由 {@link #scanRange} 更新）。 */
    @Override
    public long demand() {
        long total = 0L;
        for (BlockPos pos : consumers) {
            total = saturatingAdd(total, roomOf(pos));
        }
        return total;
    }

    /** 电网调度器分给本塔的供电配额。 */
    @Override
    public void acceptQuota(long quota) {
        this.lastQuota = quota;
    }

    /** 饱和加法。 */
    private static long saturatingAdd(long a, long b) {
        long sum = a + b;
        return sum < a ? Long.MAX_VALUE : sum;
    }
}
