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
import java.util.List;

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
 * <b>性能优化</b>（覆盖范围默认 33×17×33 格）：
 * <ul>
 *   <li><b>分片扫描</b>：把整个范围按 {@link #SCAN_INTERVAL} tick 分摊，每 tick 只扫描一小段，
 *       新源/新目标最多 1 秒内被发现；</li>
 *   <li><b>每 tick 限流 + 公平轮转</b>：吸取与分发各受每 tick 处理数上限与速率预算
 *       （{@code powerTowerTransferRate}）约束，并用游标轮转保证大范围内多个目标轮流服务。</li>
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
    /** 每 tick 实际处理的源/目标数上限（配合轮转控制单 tick 开销）。 */
    private static final int MAX_PROCESS_PER_TICK = 32;

    private static final String NBT_ENERGY = "energy";

    /** 输电塔自身 FE 缓冲（可被外部管道充能/抽能，也可被范围内其他塔搬运）。 */
    private final BasicEnergyContainer buffer;

    /** 已跟踪的可提取能量源坐标（分片扫描发现）。 */
    private final List<BlockPos> sources = new ArrayList<>();
    /** 已跟踪的可接收能量目标坐标（分片扫描发现）。 */
    private final List<BlockPos> targets = new ArrayList<>();

    private final int radiusX;
    private final int radiusY;
    private final int radiusZ;
    private final int rangeX;
    private final int rangeY;
    private final int rangeZ;
    private final int rangeSize;

    /** 分片扫描游标（展平后的一维下标，0..rangeSize-1）。 */
    private int scanCursor;
    /** 每 tick 处理游标（公平轮转）。 */
    private int sourceIndex;
    private int targetIndex;

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
            } else if (targets.size() < MAX_TARGETS && buffer.getEnergy() > 0 && storage.canReceive()) {
                // 模拟注入探测真实可接收量——canReceive() 可能恒 true 但内部拒绝，
                // 仅把真实可注入的目标加入列表。
                long room = (long) storage.getMaxEnergyStored() - storage.getEnergyStored();
                if (room > 0) {
                    int simulated = storage.receiveEnergy((int) Math.min(room, Integer.MAX_VALUE), true);
                    if (simulated > 0) {
                        targets.add(p);
                    }
                }
            }
        }
        if (scanCursor >= rangeSize) {
            scanCursor = 0;
        }
    }

    /** 从已跟踪的源按预算吸取能量进缓冲，多抽的（缓冲瞬间占满）退回源。 */
    private void pullFromSources(long rate) {
        if (rate <= 0 || sources.isEmpty() || buffer.getNeeded() <= 0) {
            return;
        }
        long remaining = rate;
        int checked = 0;
        while (checked < MAX_PROCESS_PER_TICK && remaining > 0
                && buffer.getNeeded() > 0 && !sources.isEmpty()) {
            int idx = (sourceIndex + checked) % sources.size();
            BlockPos p = sources.get(idx);
            IEnergyStorage storage = findStorage(p);
            if (storage == null || !storage.canExtract() || storage.getEnergyStored() <= 0) {
                // 源已失效/被抽空 → 移除并刷新。
                sources.remove(idx);
                continue;
            }
            long toTake = Math.min(remaining, Math.min(storage.getEnergyStored(), buffer.getNeeded()));
            if (toTake <= 0) {
                checked++;
                continue;
            }
            int extracted = storage.extractEnergy((int) Math.min(toTake, Integer.MAX_VALUE), false);
            if (extracted > 0) {
                long leftover = buffer.insert(extracted, Action.EXECUTE, AutomationType.INTERNAL);
                if (leftover > 0) {
                    // 缓冲被瞬间占满，把多抽出的能量退回源。
                    storage.receiveEnergy((int) Math.min(leftover, Integer.MAX_VALUE), false);
                }
                remaining -= (extracted - leftover);
            }
            checked++;
        }
        sourceIndex = (sourceIndex + checked) % Math.max(1, sources.size());
    }

    /** 把缓冲能量按预算轮转注入各目标，多抽出的能量退回缓冲。 */
    private void pushToTargets(long rate) {
        if (rate <= 0 || targets.isEmpty() || buffer.getEnergy() <= 0) {
            return;
        }
        long remaining = rate;
        int checked = 0;
        while (checked < MAX_PROCESS_PER_TICK && remaining > 0
                && buffer.getEnergy() > 0 && !targets.isEmpty()) {
            int idx = (targetIndex + checked) % targets.size();
            BlockPos p = targets.get(idx);
            IEnergyStorage storage = findStorage(p);
            if (storage == null) {
                // 能力已失效（方块被移除/替换/卸载）→ 移除目标并刷新。
                targets.remove(idx);
                continue;
            }
            long room = (long) storage.getMaxEnergyStored() - storage.getEnergyStored();
            if (room > 0) {
                long toMove = Math.min(remaining, Math.min(room, buffer.getEnergy()));
                long extracted = buffer.extract(toMove, Action.EXECUTE, AutomationType.INTERNAL);
                if (extracted > 0) {
                    int accepted = storage.receiveEnergy((int) Math.min(extracted, Integer.MAX_VALUE), false);
                    if (accepted < extracted) {
                        // 容器被瞬间塞满，把多抽出的能量退回自身缓冲。
                        buffer.insert(extracted - accepted, Action.EXECUTE, AutomationType.INTERNAL);
                    }
                    remaining -= accepted;
                }
            }
            checked++;
        }
        targetIndex = (targetIndex + checked) % Math.max(1, targets.size());
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
