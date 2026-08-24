package com.rtsbuilding.rtsbuilding.energy.block;

import com.mojang.serialization.MapCodec;
import com.rtsbuilding.rtsbuilding.energy.RtsEnergyBlockEntities;
import com.rtsbuilding.rtsbuilding.energy.block.entity.RtsPowerTowerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 无线输电塔——戴森球式能量传输的核心设施。
 * <p>
 * 塔自带 FE 缓冲，并在可配置的覆盖范围内（水平半径 + 上下垂直半径）无线搬运能量：
 * 从范围内的能量源（热能发电机等）吸取 FE 进自身缓冲，再向范围内的用电机器分发。
 * 塔与塔之间可以互为源/目标组成接力中继，扩大无线供电覆盖。
 * <p>
 * 碰撞/选择盒由模型 {@code models/block/power_tower.json} 经 {@link RtsEnergyBlock} 自动生成。
 */
public class RtsPowerTowerBlock extends RtsEnergyBlock implements EntityBlock {

    public static final MapCodec<RtsPowerTowerBlock> CODEC = simpleCodec(RtsPowerTowerBlock::new);

    public RtsPowerTowerBlock(BlockBehaviour.Properties properties) {
        super(properties, "models/block/power_tower.json");
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RtsPowerTowerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide && type == RtsEnergyBlockEntities.POWER_TOWER.get()) {
            return (lvl, pos, blockState, be) -> ((RtsPowerTowerBlockEntity) be).tickServer();
        }
        return null;
    }
}
