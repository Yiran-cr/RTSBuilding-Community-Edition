package com.rtsbuilding.rtsbuilding.planetrise.block.entity;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.planetrise.EnergyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 储能单元的方块实体——大容量纯 FE 缓冲存储。
 * <p>
 * 缓冲容量由 {@code Config.energyCellCapacity()} 配置，并暴露为双向
 * {@code IEnergyStorage} capability：既可被管道/机器充能抽能，也会被覆盖范围内
 * 的无线输电塔当作源/目标搬运。方块无需每 tick 逻辑，右键查看电量由方块
 * {@code EnergyCellBlock} 直接读取缓冲（{@link #getBuffer()}）。
 * <p>
 * 继承 {@link AbstractEnergyMachineBlockEntity}：能量缓冲 / 能力工厂 / NBT 持久化
 * 均由基类承担，本类无需业务 tick。
 */
public class EnergyCellBlockEntity extends AbstractEnergyMachineBlockEntity {

    public EnergyCellBlockEntity(BlockPos pos, BlockState state) {
        super(EnergyBlockEntities.ENERGY_CELL.get(), pos, state, Config.energyCellCapacity());
    }
}
