package com.rtsbuilding.rtsbuilding.planetrise.block.entity;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.planetrise.EnergyBlockEntities;
import com.rtsbuilding.rtsbuilding.planetrise.block.WindGeneratorBlock;
import com.rtsbuilding.rtsbuilding.planetrise.power.IPowerGridNode;
import com.rtsbuilding.rtsbuilding.planetrise.power.PowerGridManager;
import com.rtsbuilding.rtsbuilding.planetrise.power.PowerRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 风力发电机方块实体——参考 Mekanism {@code TileEntityWindGenerator} 的多占位风机。
 * <p>
 * 主方块位于塔基，上方 {@link #BOUNDING_HEIGHT} 格为不可见占位方块（由
 * {@link IBoundingBlock} 机制创建/代理）。产电特性：
 * <ul>
 *   <li><b>高度增益</b>：每秒（20 tick）重算一次产能倍率——塔顶（{@code above(2)}）
 *       能看见天空时，倍率 = 塔顶高度在世界建筑高度区间内的比例（越高产电越多）；</li>
 *   <li><b>缓冲</b>：FE 进入内部缓冲（容量见 {@code Config.windGeneratorCapacity()}），
 *       暴露为 extract-only {@code IEnergyStorage}，可被覆盖范围内的无线输电塔吸取；</li>
 *   <li><b>节能</b>：天空不可见（被遮挡/夜间需另行判断的简化）或缓冲满时停止产电。</li>
 * </ul>
 * <p>
 * 继承 {@link AbstractEnergyMachineBlockEntity}：能量缓冲 / 能力工厂 / tick 模板（含首次占位
 * 同步）与 NBT 持久化均由基类承担，本类专注产能倍率计算与客户端风叶动画，并作为
 * <b>发电机器</b>接入 {@link PowerGridManager} 参与电网组网（参照 power_system_design.md）。
 * <p>
 * 发电机器只拥有<b>链路范围</b>，把本 tick 产电速率 {@link #generation()} 注入所连电网；
 * 无供电范围，不能直接为用电器供电。
 */
public class WindGeneratorBlockEntity extends AbstractEnergyMachineBlockEntity implements IBoundingBlock, IPowerGridNode {

    /** 塔身占位高度（主方块上方格数）。 */
    public static final int BOUNDING_HEIGHT = 2;
    /** 产能倍率重算间隔（tick）。 */
    private static final int MULTIPLIER_RECHECK_INTERVAL = 20;
    /** 塔身占位偏移：主方块上方 1~2 格（整塔 3 格高）。 */
    private static final List<BlockPos> BOUNDING_OFFSETS = List.of(
            BlockPos.ZERO.above(1),
            BlockPos.ZERO.above(2));

    /** 当前产能倍率（0~1），缓存避免每 tick 做天空/高度计算。 */
    private float currentMultiplier;
    private int ticker;
    /** 客户端风叶旋转角（度），纯视觉。 */
    private float angle;

    public WindGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(EnergyBlockEntities.WIND_GENERATOR.get(), pos, state, Config.windGeneratorCapacity());
    }

    /** 服务端每 tick：周期性重算产能倍率并按倍率产电（守卫/占位同步由基类承担）。 */
    @Override
    protected void onServerTick() {
        ticker++;
        if (ticker % MULTIPLIER_RECHECK_INTERVAL == 0) {
            float multiplier = computeMultiplier();
            if (multiplier != currentMultiplier) {
                currentMultiplier = multiplier;
                markChanged();
            }
        }
        long generation = currentGeneration();
        if (generation > 0 && getBuffer().getNeeded() > 0) {
            addEnergy(generation);
        }
        // 注册本发电机器并驱动一次电网调度。
        if (level instanceof ServerLevel serverLevel) {
            PowerGridManager mgr = PowerGridManager.get(serverLevel);
            mgr.register(worldPosition, this);
            mgr.tick(serverLevel);
        }
    }

    /** 当前每 tick 产电量（FE）。 */
    public long currentGeneration() {
        return Math.round(Config.windGeneratorMinGeneration()
                + (Config.windGeneratorMaxGeneration() - Config.windGeneratorMinGeneration()) * currentMultiplier);
    }

    public float getCurrentMultiplier() {
        return currentMultiplier;
    }

    /** 客户端每 tick：驱动风叶旋转动画。 */
    @Override
    protected void onClientTick() {
        angle = (angle + 2.0F) % 360.0F;
    }

    /** 渲染用风叶角度（含帧间插值）。 */
    public float getClientAngle(float partialTick) {
        return (angle + 2.0F * partialTick) % 360.0F;
    }

    /**
     * 计算产能倍率：塔顶（{@code above(BOUNDING_HEIGHT)}）能看见天空时，
     * 按塔顶高度在世界建筑高度区间内的比例插值（0~1）；不可见天空返回 0。
     */
    private float computeMultiplier() {
        if (level == null) {
            return 0F;
        }
        BlockPos top = worldPosition.above(BOUNDING_HEIGHT);
        if (!level.canSeeSky(top)) {
            return 0F;
        }
        int minBuild = level.getMinBuildHeight();
        int maxBuild = level.getMaxBuildHeight();
        if (maxBuild <= minBuild) {
            return 0F;
        }
        return Math.max(0F, Math.min(1F, (float) (top.getY() - minBuild) / (maxBuild - minBuild)));
    }

    // ---- IBoundingBlock：多占位 ----

    /** 塔身占位：主方块上方 1~2 格。 */
    @Override
    public List<BlockPos> boundingOffsets() {
        return BOUNDING_OFFSETS;
    }

    /** 整塔碰撞形状（跨 3 格），由主方块/占位方块共享并各自平移。 */
    @Override
    public VoxelShape proxyShape() {
        return WindGeneratorBlock.SHAPE;
    }

    /** 占位格子（塔身）也可被取能量：暴露与主方块相同的 extract-only 缓冲。 */
    @Override
    public IEnergyStorage getBoundingEnergyStorage(@Nullable Direction side, BlockPos offset) {
        return createEnergyStorage(false, true);
    }

    // ---- 电网节点（发电机器）----

    /** 发电机器。 */
    @Override
    public PowerRole role() {
        return PowerRole.GENERATOR;
    }

    /** 发电机器链路范围（用于和输电塔组网）。 */
    @Override
    public long linkRange() {
        return Config.generatorLinkRange();
    }

    /** 发电机器无供电范围。 */
    @Override
    public long powerRange() {
        return 0L;
    }

    /** 发电机器无吞吐限制。 */
    @Override
    public long throughput() {
        return 0L;
    }

    /** 本 tick 注入电网的产电速率。 */
    @Override
    public long generation() {
        return currentGeneration();
    }

    /** 发电机器不承担用电需求。 */
    @Override
    public long demand() {
        return 0L;
    }

    /** 发电机器不接受分配给塔的配额。 */
    @Override
    public void acceptQuota(long quota) {
    }
}
