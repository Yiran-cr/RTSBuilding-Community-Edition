package com.rtsbuilding.rtsbuilding.energy.block;

import com.mojang.serialization.MapCodec;
import com.rtsbuilding.rtsbuilding.energy.block.entity.RtsEnergyCellBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 储能单元——大容量 FE 缓冲存储方块。
 * <p>
 * 暴露双向 {@code IEnergyStorage} capability（可充可抽），可被无线输电塔在覆盖范围内
 * 当作源/目标搬运，也可被外部管道直接充能/抽能。右键方块会在 actionbar 显示当前
 * 电量与容量（{@code message.rtsbuilding_technologized.energy_cell_info}）。
 * <p>
 * 碰撞/选择盒由模型 {@code models/block/energy_cell.json} 经 {@link RtsEnergyBlock} 自动生成。
 */
public class RtsEnergyCellBlock extends RtsEnergyBlock implements EntityBlock {

    public static final MapCodec<RtsEnergyCellBlock> CODEC = simpleCodec(RtsEnergyCellBlock::new);

    public RtsEnergyCellBlock(BlockBehaviour.Properties properties) {
        super(properties, "models/block/energy_cell.json");
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RtsEnergyCellBlockEntity(pos, state);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
          Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (level.getBlockEntity(pos) instanceof RtsEnergyCellBlockEntity cell) {
            player.displayClientMessage(Component.translatable(
                    "message.rtsbuilding_technologized.energy_cell_info",
                    cell.getBuffer().getEnergy(), cell.getBuffer().getMaxEnergy()), true);
        }
        return ItemInteractionResult.sidedSuccess(true);
    }
}
