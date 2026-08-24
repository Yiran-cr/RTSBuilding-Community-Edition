package com.rtsbuilding.rtsbuilding.planetrise.block.entity;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.energy.BasicEnergyContainer;
import com.rtsbuilding.rtsbuilding.planetrise.EnergyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 储能单元的方块实体——大容量纯 FE 缓冲存储。
 * <p>
 * 缓冲容量由 {@code Config.energyCellCapacity()} 配置，并暴露为双向
 * {@code IEnergyStorage} capability：既可被管道/机器充能抽能，也会被覆盖范围内
 * 的无线输电塔当作源/目标搬运。方块无需每 tick 逻辑，右键查看电量由方块
 * {@code EnergyCellBlock} 直接读取缓冲。
 */
public class EnergyCellBlockEntity extends BlockEntity {

    private static final String NBT_ENERGY = "energy";

    private final BasicEnergyContainer buffer = BasicEnergyContainer.create(Config.energyCellCapacity(), this::markChanged);

    public EnergyCellBlockEntity(BlockPos pos, BlockState state) {
        super(EnergyBlockEntities.ENERGY_CELL.get(), pos, state);
    }

    private void markChanged() {
        setChanged();
    }

    public BasicEnergyContainer getBuffer() {
        return buffer;
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
