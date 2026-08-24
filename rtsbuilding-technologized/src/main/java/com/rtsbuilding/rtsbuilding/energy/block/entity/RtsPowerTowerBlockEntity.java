package com.rtsbuilding.rtsbuilding.energy.block.entity;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.api.energy.Action;
import com.rtsbuilding.rtsbuilding.api.energy.AutomationType;
import com.rtsbuilding.rtsbuilding.common.energy.BasicEnergyContainer;
import com.rtsbuilding.rtsbuilding.energy.RtsEnergyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 无线输电塔方块实体——戴森球式能量传输的核心设施。
 * <p>
 * 输电塔自带 FE 缓冲（容量由 {@code Config.powerTowerCapacity()} 配置），并通过覆盖范围
 * （水平半径 {@code powerTowerHorizontalRadius}、上下垂直半径 {@code powerTowerVerticalRadius}，
 * 均为可配置）内的标准 {@code IEnergyStorage} 能力做<b>无线能量搬运</b>：
 * <ul>
 *   <li><b>范围吸取</b>：把范围内可提取的 FE 源（如热能发电机缓冲、其他机器/塔）抽入自身缓冲；</li>
 *   <li><b>范围分发</b>：把自身缓冲的能量注入范围内需要能量的存储（如各类用电机器/电池）。</li>
 * </ul>
 * 塔与塔之间可互为源/目标实现中继，能量只搬运、不凭空产生（守恒）。覆盖范围在创建时从
 * 配置读取一次，改配置需重启生效。
 * <p>
 * <b>搬运调度（公平按需调度，欠账补偿）</b>：吸取与分发不再是简单的先到先得，而是每个
 * 源/目标都维护一份「欠账」credit：
 * <ul>
 *   <li><b>分发侧</b>：每 tick 按各目标<b>需求余量占比</b>累计配额（需求越大配额越高），
 *       随后每次都优先服务「欠账最高」的目标——快满的目标配额自然趋零，被跳过的目标
 *       欠账累积、下次优先补偿，长期看每个目标按需求比例获得能量，不会饿死也不会硬塞；</li>
 *   <li><b>提取侧</b>：每 tick 按各源<b>存量占比</b>累计配额，均衡抽取——不会盯住一个源
 *       掏空，长期让各源存量趋于均衡。</li>
 * </ul>
 * <p>
 * <b>性能优化</b>（覆盖范围默认 33×17×33 格）：
 * <ul>
 *   <li><b>分片扫描</b>：把整个范围按 {@link #SCAN_INTERVAL} tick 分摊，每 tick 只扫描一小段，
 *       新源/新目标最多 1 秒内被发现；</li>
 *   <li><b>每 tick 限流</b>：吸取与分发各受每 tick 处理数上限（{@link #MAX_PROCESS_PER_TICK}）
 *       与速率预算（{@code powerTowerTransferRate}）约束，配合欠账补偿保证公平。</li>
 * </ul>
 * 目标/源只缓存坐标，每 tick 实时查询能力（一次任意面 + 个别方块回退 6 面），兼容所有注册了
 * FE 能量能力的模组方块。
 */
public class RtsPowerTowerBlockEntity extends BlockEntity {

    /** 一轮全量范围扫描的间隔（tick）。 */
    private static final int SCAN_INTERVAL = 20;
    /** 同时跟踪的可提取源数量上限（防列表膨胀）。 */
    private static final int MAX_SOURCES = 64;
    /** 同时跟踪的可接收目标数量上限（防列表膨胀）。 */
    private static final int MAX_TARGETS = 128;
    /** 每 tick 实际处理的源/目标数上限（控制单 tick 开销）。 */
    private static final int MAX_PROCESS_PER_TICK = 32;

    private static final String NBT_ENERGY = "energy";

    /** 输电塔自身 FE 缓冲（可被外部管道充能/抽能，也可被范围内其他塔搬运）。 */
    private final BasicEnergyContainer buffer;

    /** 已跟踪的可提取能量源坐标（分片扫描发现）。 */
    private final List<BlockPos> sources = new ArrayList<>();
    /** 已跟踪的可接收能量目标坐标（分片扫描发现）。 */
    private final List<BlockPos> targets = new ArrayList<>();

    /**
     * 提取侧欠账表：{@code sourceCredit[p]} = 该源累计应提而未提的 FE（按存量比例配额累积）。
     * 欠账越高的源在下一次调度中越优先被提取，保证长期均衡。
     */
    private final Map<BlockPos, Long> sourceCredit = new HashMap<>();
    /**
     * 分发侧欠账表：{@code targetCredit[p]} = 该目标累计应得而未得的 FE（按需求比例配额累积）。
     * 欠账越高的目标在下一次调度中越优先被充能，保证长期公平。
     */
    private final Map<BlockPos, Long> targetCredit = new HashMap<>();

    private final int radiusX;
    private final int radiusY;
    private final int radiusZ;
    private final int rangeX;
    private final int rangeY;
    private final int rangeZ;
    private final int rangeSize;

    /** 分片扫描游标（展平后的一维下标，0..rangeSize-1）。 */
    private int scanCursor;

    public RtsPowerTowerBlockEntity(BlockPos pos, BlockState state) {
        super(RtsEnergyBlockEntities.POWER_TOWER.get(), pos, state);
        this.buffer = BasicEnergyContainer.create(Config.powerTowerCapacity(), this::markChanged);
        this.radiusX = Config.powerTowerHorizontalRadius();
        this.radiusY = Config.powerTowerVerticalRadius();
        this.radiusZ = Config.powerTowerHorizontalRadius();
        this.rangeX = radiusX * 2 + 1;
        this.rangeY = radiusY * 2 + 1;
        this.rangeZ = radiusZ * 2 + 1;
        this.rangeSize = rangeX * rangeY * rangeZ;
    }

    private void markChanged() {
        setChanged();
    }

    public BasicEnergyContainer getBuffer() {
        return buffer;
    }

    /** 服务端每 tick：分片扫描发现源/目标，再按速率预算搬运能量。 */
    public void tickServer() {
        if (level == null || level.isClientSide || !Config.isTechnologizedEnabled()) {
            return;
        }
        long rate = Config.powerTowerTransferRate();
        if (rate <= 0 || rangeSize <= 0) {
            return;
        }
        scanSlice();
        pullFromSources(rate);
        pushToTargets(rate);
    }

    /** 扫描展平范围中的一小段，发现新的能量源/目标加入对应列表。 */
    private void scanSlice() {
        if (level == null || rangeSize <= 0) {
            return;
        }
        int perTick = Math.max(1, (int) Math.ceil(rangeSize / (double) SCAN_INTERVAL));
        for (int i = 0; i < perTick && scanCursor < rangeSize; i++, scanCursor++) {
            BlockPos p = worldPosition.offset(offsetForIndex(scanCursor));
            // 跳过自身（避免塔把自己的缓冲当作源/目标空转）。
            if (p.equals(worldPosition)) {
                continue;
            }
            if (!level.isLoaded(p)) {
                continue;
            }
            if (sources.contains(p) || targets.contains(p)) {
                continue;
            }
            // 无方块实体的位置基本不存在能量能力——快速跳过。
            if (level.getBlockEntity(p) == null) {
                continue;
            }
            IEnergyStorage storage = findStorage(p);
            if (storage == null) {
                continue;
            }
            if (sources.size() < MAX_SOURCES && buffer.getNeeded() > 0
                    && storage.canExtract() && storage.getEnergyStored() > 0) {
                sources.add(p);
                sourceCredit.put(p, 0L);
            } else if (targets.size() < MAX_TARGETS && buffer.getEnergy() > 0 && storage.canReceive()) {
                // 模拟注入探测真实可接收量——canReceive() 可能恒 true 但内部拒绝，
                // 仅把真实可注入的目标加入列表。
                long room = (long) storage.getMaxEnergyStored() - storage.getEnergyStored();
                if (room > 0) {
                    int simulated = storage.receiveEnergy((int) Math.min(room, Integer.MAX_VALUE), true);
                    if (simulated > 0) {
                        targets.add(p);
                        targetCredit.put(p, 0L);
                    }
                }
            }
        }
        if (scanCursor >= rangeSize) {
            scanCursor = 0;
        }
    }

    /**
     * 从已跟踪的源均衡提取能量进缓冲。
     * <p>
     * 公平调度（提取侧）：每 tick 按各源<b>存量占比</b>累计配额
     * {@code credit_i += rate * stored_i / totalStored}，再反复选取「欠账最高」的源实际提取
     * {@code min(credit_i, stored_i, 缓冲余量, 剩余预算)} 并扣减其欠账。多抽的（缓冲瞬间占满）
     * 退回源。长期看每个源按相同比例被消耗，不会盯住单一源掏空。
     */
    private void pullFromSources(long rate) {
        if (rate <= 0 || sources.isEmpty() || buffer.getNeeded() <= 0) {
            return;
        }
        // 快照有效源（可提取且有余量），同时移除失效/抽空的源。
        List<BlockPos> valid = new ArrayList<>();
        long[] stored = new long[sources.size()];
        long totalStored = 0;
        for (Iterator<BlockPos> it = sources.iterator(); it.hasNext(); ) {
            BlockPos p = it.next();
            IEnergyStorage s = findStorage(p);
            long avail = s == null || !s.canExtract() ? 0 : s.getEnergyStored();
            if (avail <= 0) {
                it.remove();
                sourceCredit.remove(p);
                continue;
            }
            valid.add(p);
            stored[valid.size() - 1] = avail;
            totalStored = saturatingAdd(totalStored, avail);
        }
        if (valid.isEmpty() || totalStored <= 0) {
            return;
        }
        // 按存量占比累计本 tick 配额。
        for (int i = 0; i < valid.size(); i++) {
            long share = (long) ((double) rate * stored[i] / totalStored);
            if (share > 0) {
                sourceCredit.merge(valid.get(i), share, Long::sum);
            }
        }
        long remaining = rate;
        int processed = 0;
        while (processed < MAX_PROCESS_PER_TICK && remaining > 0
                && buffer.getNeeded() > 0 && !valid.isEmpty()) {
            // 选取欠账最高的源。
            int bestIdx = pickHighestCredit(valid, sourceCredit);
            if (bestIdx < 0) {
                break;
            }
            BlockPos best = valid.get(bestIdx);
            IEnergyStorage s = findStorage(best);
            if (s == null || !s.canExtract() || s.getEnergyStored() <= 0) {
                valid.remove(bestIdx);
                sources.remove(best);
                sourceCredit.remove(best);
                continue;
            }
            long credit = sourceCredit.getOrDefault(best, 0L);
            if (credit <= 0) {
                break;
            }
            long toTake = Math.min(Math.min(credit, s.getEnergyStored()),
                    Math.min(remaining, buffer.getNeeded()));
            if (toTake <= 0) {
                sourceCredit.put(best, 0L);
                break;
            }
            int extracted = s.extractEnergy((int) Math.min(toTake, Integer.MAX_VALUE), false);
            if (extracted > 0) {
                long leftover = buffer.insert(extracted, Action.EXECUTE, AutomationType.INTERNAL);
                long actuallyKept = extracted - leftover;
                if (leftover > 0) {
                    // 缓冲被瞬间占满，把多抽出的能量退回源。
                    s.receiveEnergy((int) Math.min(leftover, Integer.MAX_VALUE), false);
                }
                sourceCredit.compute(best, (k, v) -> v == null ? 0L : Math.max(0L, v - actuallyKept));
                remaining -= actuallyKept;
            }
            processed++;
        }
    }

    /**
     * 把缓冲能量按需求比例公平分发给目标。
     * <p>
     * 公平调度（分发侧）：每 tick 按各目标<b>需求余量占比</b>累计配额
     * {@code credit_i += rate * room_i / totalRoom}，再反复选取「欠账最高」的目标实际注入
     * {@code min(credit_i, room_i, 缓冲余量, 剩余预算)} 并扣减其欠账。多抽出的能量退回缓冲。
     * 长期看每个目标按需求比例获得能量：快满的目标配额趋零、不会硬塞；被跳过的目标欠账累积、
     * 下次优先补偿、不会饿死。
     */
    private void pushToTargets(long rate) {
        if (rate <= 0 || targets.isEmpty() || buffer.getEnergy() <= 0) {
            return;
        }
        // 快照有效目标（可接收且有余量），同时移除失效/已满的目标。
        List<BlockPos> valid = new ArrayList<>();
        long[] rooms = new long[targets.size()];
        long totalRoom = 0;
        for (Iterator<BlockPos> it = targets.iterator(); it.hasNext(); ) {
            BlockPos p = it.next();
            IEnergyStorage s = findStorage(p);
            long room = s == null || !s.canReceive()
                    ? 0 : (long) s.getMaxEnergyStored() - s.getEnergyStored();
            if (room <= 0) {
                it.remove();
                targetCredit.remove(p);
                continue;
            }
            valid.add(p);
            rooms[valid.size() - 1] = room;
            totalRoom = saturatingAdd(totalRoom, room);
        }
        if (valid.isEmpty() || totalRoom <= 0) {
            return;
        }
        // 按需求占比累计本 tick 配额。
        for (int i = 0; i < valid.size(); i++) {
            long share = (long) ((double) rate * rooms[i] / totalRoom);
            if (share > 0) {
                targetCredit.merge(valid.get(i), share, Long::sum);
            }
        }
        long remaining = rate;
        int processed = 0;
        while (processed < MAX_PROCESS_PER_TICK && remaining > 0
                && buffer.getEnergy() > 0 && !valid.isEmpty()) {
            // 选取欠账最高的目标。
            int bestIdx = pickHighestCredit(valid, targetCredit);
            if (bestIdx < 0) {
                break;
            }
            BlockPos best = valid.get(bestIdx);
            IEnergyStorage s = findStorage(best);
            if (s == null || !s.canReceive()) {
                valid.remove(bestIdx);
                targets.remove(best);
                targetCredit.remove(best);
                continue;
            }
            long room = (long) s.getMaxEnergyStored() - s.getEnergyStored();
            if (room <= 0) {
                valid.remove(bestIdx);
                targets.remove(best);
                targetCredit.remove(best);
                continue;
            }
            long credit = targetCredit.getOrDefault(best, 0L);
            if (credit <= 0) {
                break;
            }
            long toMove = Math.min(Math.min(credit, room),
                    Math.min(remaining, buffer.getEnergy()));
            if (toMove <= 0) {
                targetCredit.put(best, 0L);
                break;
            }
            long extracted = buffer.extract(toMove, Action.EXECUTE, AutomationType.INTERNAL);
            if (extracted > 0) {
                int accepted = s.receiveEnergy((int) Math.min(extracted, Integer.MAX_VALUE), false);
                if (accepted < extracted) {
                    // 容器被瞬间塞满，把多抽出的能量退回自身缓冲。
                    buffer.insert(extracted - accepted, Action.EXECUTE, AutomationType.INTERNAL);
                }
                targetCredit.compute(best, (k, v) -> v == null ? 0L : Math.max(0L, v - accepted));
                remaining -= accepted;
            }
            processed++;
        }
    }

    /** 返回 {@code creditMap} 中欠账最高的元素在 {@code valid} 中的下标；全部非正时返回 -1。 */
    private int pickHighestCredit(List<BlockPos> valid, Map<BlockPos, Long> creditMap) {
        int bestIdx = -1;
        long bestCredit = 0;
        for (int i = 0; i < valid.size(); i++) {
            long c = creditMap.getOrDefault(valid.get(i), 0L);
            if (c > bestCredit) {
                bestCredit = c;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /** 饱和加法：累加溢出时钳制到 {@link Long#MAX_VALUE}，避免总量异常破坏比例计算。 */
    private static long saturatingAdd(long a, long b) {
        long sum = a + b;
        return (sum < a) ? Long.MAX_VALUE : sum;
    }

    /**
     * 查询指定位置的标准能量能力。先按「任意面」查询，失败再回退 6 个方向逐一查询，
     * 保证兼容只在特定面暴露能力的方块（如 Mekanism 机器的输入/输出面）。
     */
    @Nullable
    private IEnergyStorage findStorage(BlockPos pos) {
        IEnergyStorage direct = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        if (direct != null) {
            return direct;
        }
        for (Direction side : Direction.values()) {
            IEnergyStorage sided = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
            if (sided != null) {
                return sided;
            }
        }
        return null;
    }

    /** 把展平下标解码为相对塔位置的偏移。 */
    private BlockPos offsetForIndex(int index) {
        int dz = index % rangeZ - radiusZ;
        int dy = (index / rangeZ) % rangeY - radiusY;
        int dx = index / (rangeZ * rangeY) - radiusX;
        return new BlockPos(dx, dy, dz);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put(NBT_ENERGY, buffer.serializeNBT(provider));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains(NBT_ENERGY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            buffer.deserializeNBT(provider, tag.getCompound(NBT_ENERGY));
        }
    }
}
