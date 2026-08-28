package com.rtsbuilding.rtsbuilding.planetrise.block.entity;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.planetrise.EnergyBlockEntities;
import com.rtsbuilding.rtsbuilding.planetrise.block.ThermalGeneratorBlock;
import com.rtsbuilding.rtsbuilding.planetrise.power.IPowerGridNode;
import com.rtsbuilding.rtsbuilding.planetrise.power.PowerGridManager;
import com.rtsbuilding.rtsbuilding.planetrise.power.PowerRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Block entity for the thermal generator. Burns lava to produce FE.
 * <p>
 * Generated FE is reported to the grid via {@link #generation()} as a rate-based
 * supply and is distributed through the power grid (towers) to consumers. The
 * internal buffer is exposed as an extract-only {@code IEnergyStorage} capability
 * merely as an out-of-band hook for external pipes / third-party mods — it is no
 * longer the path the grid uses to take energy from the generator.
 * <p>
 * 继承 {@link AbstractEnergyMachineBlockEntity}（能量缓冲 / NBT / tick 模板均由基类承担），
 * 本类专注：岩浆罐燃料供给 + 每 tick 燃烧产电 + LIT 状态同步，并作为<b>发电机器</b>
 * 接入 {@link PowerGridManager} 参与电网组网（参照 power_system_design.md）。
 * <p>
 * 发电机器只拥有<b>链路范围</b>（{@link #linkRange()}），把本 tick 产电速率
 * {@link #generation()} 注入所连电网；<b>无供电范围</b>，不能直接为用电器供电，
 * 产生的电力必须经输电塔中转。
 */
public class ThermalGeneratorBlockEntity extends AbstractEnergyMachineBlockEntity implements IPowerGridNode {

    /** FE generated per tick while burning. */
    public static final long GENERATION_PER_TICK = 60;
    /** Server ticks per millibucket of lava consumed. */
    public static final int TICKS_PER_LAVA_MB = 20;
    /** Internal FE buffer capacity. */
    public static final long BUFFER_CAPACITY = 20_000L;
    /** Lava tank capacity in millibuckets. */
    public static final int TANK_CAPACITY = 8_000;

    private static final String NBT_LAVA = "lava";

    private final FluidTank tank = new FluidTank(TANK_CAPACITY, fluid -> fluid.getFluid() == net.minecraft.world.level.material.Fluids.LAVA);

    private int burnTimer;

    /** 本 tick 实际产电速率（FE/t），供电网调度读取；未燃烧时为 0。 */
    private long currentTickGeneration;

    public ThermalGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(EnergyBlockEntities.THERMAL_GENERATOR.get(), pos, state, BUFFER_CAPACITY);
    }

    public FluidTank getTank() {
        return tank;
    }

    /**
     * Handles right-click with a lava bucket (fills the tank) or an empty bucket
     * (drains the tank), swapping buckets in the player's hand.
     */
    public net.minecraft.world.ItemInteractionResult interactWithBucket(ItemStack stack, net.minecraft.world.entity.player.Player player) {
        if (stack.getItem() == net.minecraft.world.item.Items.LAVA_BUCKET && tank.getSpace() >= 1000) {
            if (tank.fill(new FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000), FluidAction.EXECUTE) > 0) {
                swapBucket(player, stack, net.minecraft.world.item.Items.BUCKET);
                markChanged();
                return net.minecraft.world.ItemInteractionResult.sidedSuccess(true);
            }
        }
        if (stack.getItem() == net.minecraft.world.item.Items.BUCKET && tank.getFluidAmount() >= 1000) {
            tank.drain(1000, FluidAction.EXECUTE);
            swapBucket(player, stack, net.minecraft.world.item.Items.LAVA_BUCKET);
            markChanged();
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(true);
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private void swapBucket(net.minecraft.world.entity.player.Player player, ItemStack inHand,
          net.minecraft.world.item.Item returnItem) {
        if (player.getAbilities().instabuild) {
            return;
        }
        ItemStack result = new ItemStack(returnItem);
        if (!player.addItem(result)) {
            player.drop(result, false);
        }
        inHand.shrink(1);
    }

    /** 服务端每 tick：燃烧岩浆产生 FE，并作为发电机器接入电网（守卫/占位同步由基类承担）。 */
    @Override
    protected void onServerTick() {
        boolean burning = tank.getFluidAmount() > 0;
        currentTickGeneration = burning ? GENERATION_PER_TICK : 0L;
        if (burning) {
            addEnergy(GENERATION_PER_TICK);
            burnTimer++;
            if (burnTimer >= TICKS_PER_LAVA_MB) {
                burnTimer = 0;
                tank.drain(1, FluidAction.EXECUTE);
            }
            markChanged();
        }
        BlockState state = level.getBlockState(worldPosition);
        if (state.hasProperty(ThermalGeneratorBlock.LIT) && state.getValue(ThermalGeneratorBlock.LIT) != burning) {
            level.setBlock(worldPosition, state.setValue(ThermalGeneratorBlock.LIT, burning), 2);
        }
        // 注册本发电机器并驱动一次电网调度。
        if (level instanceof ServerLevel serverLevel) {
            PowerGridManager mgr = PowerGridManager.get(serverLevel);
            mgr.register(worldPosition, this);
            mgr.tick(serverLevel);
        }
    }

    /** 岩浆罐持久化（能量缓冲由基类处理）。 */
    @Override
    protected void saveEnergyAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        if (!tank.isEmpty()) {
            tag.put(NBT_LAVA, tank.writeToNBT(provider, new CompoundTag()));
        }
    }

    /** 岩浆罐读取。 */
    @Override
    protected void loadEnergyAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        if (tag.contains(NBT_LAVA, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            tank.readFromNBT(provider, tag.getCompound(NBT_LAVA));
        }
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
        return currentTickGeneration;
    }

    /** 发电机器不承担用电需求。 */
    @Override
    public long demand() {
        return 0L;
    }
}
