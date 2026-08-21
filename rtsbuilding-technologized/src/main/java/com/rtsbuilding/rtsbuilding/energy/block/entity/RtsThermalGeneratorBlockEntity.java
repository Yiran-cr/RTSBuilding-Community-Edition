package com.rtsbuilding.rtsbuilding.energy.block.entity;

import com.rtsbuilding.rtsbuilding.api.energy.Action;
import com.rtsbuilding.rtsbuilding.api.energy.AutomationType;
import com.rtsbuilding.rtsbuilding.energy.RtsEnergyBlockEntities;
import com.rtsbuilding.rtsbuilding.energy.block.RtsThermalGeneratorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * Block entity for the thermal generator. Burns lava to produce FE.
 * <p>
 * The internal buffer is part of the owner's energy grid. The block is lit while
 * it has lava and room to store generated energy.
 */
public class RtsThermalGeneratorBlockEntity extends RtsGeneratorBlockEntity {

    /** FE generated per tick while burning. */
    public static final long GENERATION_PER_TICK = 60;
    /** Server ticks per millibucket of lava consumed. */
    public static final int TICKS_PER_LAVA_MB = 20;
    /** Internal FE buffer capacity. */
    public static final long BUFFER_CAPACITY = 20_000L;
    /** Lava tank capacity in millibuckets. */
    public static final int TANK_CAPACITY = 8_000;

    private static final String NBT_ENERGY = "energy";
    private static final String NBT_LAVA = "lava";

    private int burnTimer;

    public RtsThermalGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state, RtsEnergyBlockEntities.THERMAL_GENERATOR.get(), GENERATION_PER_TICK, BUFFER_CAPACITY, TANK_CAPACITY, NBT_ENERGY, net.minecraft.world.level.material.Fluids.LAVA);
    }

    private void markChanged() {
        setChanged();
    }

    /**
     * Handles right-click with a lava bucket (fills the tank) or an empty bucket
     * (drains the tank), swapping buckets in the player's hand.
     */
    public net.minecraft.world.ItemInteractionResult interactWithBucket(ItemStack stack, net.minecraft.world.entity.player.Player player) {
        if (stack.getItem() == net.minecraft.world.item.Items.LAVA_BUCKET && this.getTank().getSpace() >= 1000) {
            if (this.getTank().fill(new FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000), FluidAction.EXECUTE) > 0) {
                swapBucket(player, stack, net.minecraft.world.item.Items.BUCKET);
                markChanged();
                if (level != null && !level.isClientSide) {
                    level.playSound(null, worldPosition,
                        BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.withDefaultNamespace("item.bucket.fill_lava")),
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                return net.minecraft.world.ItemInteractionResult.sidedSuccess(true);
            }
        }
        if (stack.getItem() == net.minecraft.world.item.Items.BUCKET && this.getTank().getFluidAmount() >= 1000) {
            this.getTank().drain(1000, FluidAction.EXECUTE);
            swapBucket(player, stack, net.minecraft.world.item.Items.LAVA_BUCKET);
            markChanged();
            if (level != null && !level.isClientSide) {
                level.playSound(null, worldPosition,
                        BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.withDefaultNamespace("item.bucket.empty_lava")),
                        SoundSource.BLOCKS, 1.0F, 1.0F);
            }
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

    @Override
    public boolean checkGenerate() {
        return this.getTank().getFluidAmount() > 0 && this.getBuffer().getNeeded() > 0;
    }

    @Override
    public void generateBehavior() {
        this.getBuffer().insert(GENERATION_PER_TICK, Action.EXECUTE, AutomationType.INTERNAL);
        burnTimer++;
        if (burnTimer >= TICKS_PER_LAVA_MB) {
            burnTimer = 0;
            this.getTank().drain(1, FluidAction.EXECUTE);
        }
        markChanged();
        
    }

    @Override
    public void tickServer() {
        super.tickServer();
        BlockState state = level.getBlockState(worldPosition);
        boolean burning = checkGenerate();
        if (state.hasProperty(RtsThermalGeneratorBlock.LIT) && state.getValue(RtsThermalGeneratorBlock.LIT) != burning) {
            level.setBlock(worldPosition, state.setValue(RtsThermalGeneratorBlock.LIT, burning), 2);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put(NBT_ENERGY, this.getBuffer().serializeNBT(provider));
        if (!this.getTank().isEmpty()) {
            tag.put(NBT_LAVA, this.getTank().writeToNBT(provider, new CompoundTag()));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains(NBT_ENERGY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            this.getBuffer().deserializeNBT(provider, tag.getCompound(NBT_ENERGY));
        }
        if (tag.contains(NBT_LAVA, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            this.getTank().readFromNBT(provider, tag.getCompound(NBT_LAVA));
        }
    }
}
