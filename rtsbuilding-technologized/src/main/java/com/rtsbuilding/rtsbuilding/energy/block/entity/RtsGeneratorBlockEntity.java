package com.rtsbuilding.rtsbuilding.energy.block.entity;

import com.rtsbuilding.rtsbuilding.api.energy.IEnergyContainer;
import com.rtsbuilding.rtsbuilding.common.energy.BasicEnergyContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Block entity for the basic generator. Needs circumstance to produce FE.
 * <p>
 * The internal buffer is part of the owner's energy grid.
 * it has storage to store fluid and generated energy.
 */
public abstract class RtsGeneratorBlockEntity extends RtsEnergyBlockEntity {

    /** FE generated per tick while burning. */
    private final long generationPerTick;
    /** Internal FE buffer capacity. */
    private final long bufferCapacity;
    /** Lava tank capacity in millibuckets. */
    private final int tankCapacity;
    /** Is fluid generator. */
    private final boolean isFluidGenerator;

    private static final String NBT_ENERGY = "energy";
    private final String NBT_FLUID;

    private final BasicEnergyContainer buffer;
    private final FluidTank tank;// = new FluidTank(tankCapacity, fluid -> fluid.getFluid() == net.minecraft.world.level.material.Fluids.LAVA);


    public RtsGeneratorBlockEntity(BlockPos pos, BlockState state, BlockEntityType<?> blockEntityType, long generationPerTick, long bufferCapacity, int tankCapacity, String nbtFluid, Fluid fluidType) {
        super(blockEntityType, pos, state);
        this.generationPerTick = generationPerTick;
        this.bufferCapacity = bufferCapacity;
        this.tankCapacity = tankCapacity;
        this.NBT_FLUID = nbtFluid;
        this.isFluidGenerator = true;
        this.buffer = BasicEnergyContainer.create(bufferCapacity, this::markChanged);
        this.tank = new FluidTank(tankCapacity, fluid -> fluid.getFluid() == fluidType);
    }

    public RtsGeneratorBlockEntity(BlockPos pos, BlockState state, BlockEntityType<?> blockEntityType, long generationPerTick, long bufferCapacity) {
        super(blockEntityType, pos, state);
        this.generationPerTick = generationPerTick;
        this.bufferCapacity = bufferCapacity;
        this.tankCapacity = 0;
        this.NBT_FLUID = null;
        this.isFluidGenerator = false;
        this.tank = null;
        this.buffer = BasicEnergyContainer.create(bufferCapacity, this::markChanged);
    }

    private void markChanged() {
        setChanged();
    }

    public FluidTank getTank() {
        return tank;
    }

    public BasicEnergyContainer getBuffer() {
        return this.buffer;
    }

    /**
     * Handles right-click with a lava bucket (fills the tank) or an empty bucket
     * (drains the tank), swapping buckets in the player's hand.
     */
    public abstract net.minecraft.world.ItemInteractionResult interactWithBucket(ItemStack stack, net.minecraft.world.entity.player.Player player);
    public abstract boolean checkGenerate();
    public abstract void generateBehavior();

    @Override
    public IEnergyContainer getEnergyBuffer() {
        return buffer;
    }

    @Override
    public long getGeneration() {
        return checkGenerate() ? generationPerTick : 0;
    }

    /** Server tick: check circumstances, generate FE, and consumes circumstances. */
    public void tickServer() {
        if (level == null || level.isClientSide || !com.rtsbuilding.rtsbuilding.Config.isTechnologizedEnabled()) {
            return;
        }
        if (this.checkGenerate()) {
            this.generateBehavior();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put(NBT_ENERGY, buffer.serializeNBT(provider));
        if (isFluidGenerator && !tank.isEmpty()) {
            tag.put(NBT_FLUID, tank.writeToNBT(provider, new CompoundTag()));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains(NBT_ENERGY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            buffer.deserializeNBT(provider, tag.getCompound(NBT_ENERGY));
        }
        if (isFluidGenerator && tag.contains(NBT_FLUID, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            tank.readFromNBT(provider, tag.getCompound(NBT_FLUID));
        }
    }
}
