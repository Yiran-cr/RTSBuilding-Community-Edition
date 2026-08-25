package com.rtsbuilding.rtsbuilding.planetrise.block.entity;

import com.mojang.logging.LogUtils;
import com.rtsbuilding.rtsbuilding.planetrise.EnergyBlocks;
import com.rtsbuilding.rtsbuilding.planetrise.block.BoundingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;

/**
 * 多占位方块接口（参考 Mekanism {@code IBoundingBlock}）——由「主方块」的方块实体实现。
 * <p>
 * 主方块占据世界中的多个格子：自己所在的格子 + 若干「占位方块」（{@link BoundingBlock}）。
 * 占位方块不可见、无自身逻辑，所有形状/交互/破坏均<b>代理</b>回主方块实体。接口默认提供
 * 占位方块的<b>创建/清除</b>（{@link #placeBoundingBlocks()} / {@link #removeBoundingBlocks()}）
 * 与<b>交互转发</b>，子类只需声明占位相对偏移（{@link #boundingOffsets()}）并提供整体
 * 碰撞形状（{@link #proxyShape()}）。
 * <p>
 * 递归防护：清除占位方块时必须<b>先移除方块实体、再移除方块</b>，使占位方块在
 * {@code onRemove} 中查不到 {@code mainPos}，从而避免「占位破坏 → 连带破坏主方块 → 再清占位」
 * 的无限递归（与 Mekanism {@code AttributeHasBounding} 一致）。
 */
public interface IBoundingBlock {

    /** 诊断日志。 */
    Logger LOGGER = LogUtils.getLogger();

    /**
     * 占位方块相对本主方块的偏移列表（不含主方块自身）。
     * <p>
     * 例如风力发电机的塔身：{@code above(1) ~ above(2)} 两个占位格子。
     *
     * @return 占位偏移集合；无占位时返回空列表。
     */
    default List<BlockPos> boundingOffsets() {
        return List.of();
    }

    /**
     * 在所有占位位置创建不可见占位方块，并把它们的 {@code mainPos} 指向本主方块。
     *
     * @return 全部占位创建成功返回 {@code true}；任一位置不可放置/创建失败返回 {@code false}。
     *         调用方（主方块 {@code onPlace}）在返回 {@code false} 时应回滚自身放置。
     */
    default boolean placeBoundingBlocks() {
        if (!(this instanceof BlockEntity be) || be.getLevel() == null) {
            return false;
        }
        Level level = be.getLevel();
        BlockPos mainPos = be.getBlockPos();
        boolean allPlaced = true;
        LOGGER.debug("[BB-PLACE] side={} main={} offsets={}", level.isClientSide ? "CLIENT" : "SERVER", mainPos, boundingOffsets());
        for (BlockPos offset : boundingOffsets()) {
            BlockPos p = mainPos.offset(offset);
            BlockState stateAt = level.getBlockState(p);
            if (stateAt.isAir() || stateAt.canBeReplaced()) {
                // 目标可放置：放置占位方块。
                level.setBlockAndUpdate(p, EnergyBlocks.BOUNDING_BLOCK.get().defaultBlockState());
            } else if (!(stateAt.getBlock() instanceof BoundingBlock)) {
                // 目标被其他方块占用：占位失败，由主方块 onPlace 回滚整个放置。
                allPlaced = false;
                LOGGER.debug("[BB-PLACE] {} occupied by {} -> FAIL", p, stateAt.getBlock());
                continue;
            }
            // 无论刚放置还是已是占位（服务端同步先到、setBlockAndUpdate 因同 state 返回 false），
            // 都幂等绑定 mainPos。服务端绑定后主动同步给客户端（sync=true），因为客户端不一定
            // 执行主方块 onPlace（如 RTS 远程放置），占位 mainPos 必须靠服务端实体更新包恢复，
            // 否则客户端占位格子会失去碰撞/选择箱/连带破坏逻辑。
            if (level.getBlockEntity(p) instanceof BoundingBlockEntity bounding) {
                bounding.setMainLocation(mainPos, !level.isClientSide);
                LOGGER.debug("[BB-PLACE] {} bound -> main={} (be={})", p, mainPos, bounding);
            } else {
                allPlaced = false;
                LOGGER.debug("[BB-PLACE] {} no BoundingBlockEntity after place -> FAIL", p);
            }
        }
        return allPlaced;
    }

    /**
     * 服务端首次 tick 兜底：对每个占位方块强制重发实体更新包，把 {@code mainPos} 同步到客户端。
     * <p>
     * 两端各自放置（{@link #placeBoundingBlocks()}）依赖客户端执行 {@code onPlace}，但 RTS
     * 远程放置走服务端 {@code setBlock}，客户端可能不触发 {@code onPlace}；且放置时立即发送的
     * 实体更新包存在时序竞态——包到达时客户端占位实体可能尚未创建而被静默丢弃，导致客户端
     * 占位实体的 {@code mainPos} 恒为 null（占位格无碰撞/无选择箱）。参照 Mekanism
     * {@code TileEntityMekanism.tickServer → syncMasterPosition}：主方块实体首次 tick 时调用本方法，
     * 此时占位 BlockState 与实体必然已在客户端就绪，重发的实体包不会再丢。
     */
    default void syncBoundingBlocks() {
        if (!(this instanceof BlockEntity be) || be.getLevel() == null || be.getLevel().isClientSide) {
            return;
        }
        Level level = be.getLevel();
        BlockPos mainPos = be.getBlockPos();
        for (BlockPos offset : boundingOffsets()) {
            BlockPos p = mainPos.offset(offset);
            if (level.getBlockEntity(p) instanceof BoundingBlockEntity bounding) {
                bounding.setMainLocation(mainPos, true);
            }
        }
    }

    /**
     * 清除本主方块的全部占位方块。
     * <p>
     * 顺序必须为：先 {@code removeBlockEntity}（令占位方块失去 {@code mainPos} 引用），
     * 再 {@code removeBlock}。否则占位方块 {@code onRemove} 会因仍能定位到主方块而递归
     * 触发「连带破坏主方块」。
     */
    default void removeBoundingBlocks() {
        if (!(this instanceof BlockEntity be) || be.getLevel() == null) {
            return;
        }
        Level level = be.getLevel();
        BlockPos mainPos = be.getBlockPos();
        for (BlockPos offset : boundingOffsets()) {
            BlockPos p = mainPos.offset(offset);
            if (level.getBlockEntity(p) instanceof BoundingBlockEntity bounding && mainPos.equals(bounding.getMainPos())) {
                level.removeBlockEntity(p);
                level.removeBlock(p, false);
            }
        }
    }

    /**
     * 主方块的整体碰撞/选择形状，可跨多个格子（模型坐标系原点在主方块原点）。
     * <p>
     * 占位方块会把该形状按自身相对主方块的偏移平移，从而让每个格子拥有塔身上
     * 对应的那一段碰撞。形状缓存时请注意：占位方块的形状依赖位置，必须使用
     * {@code dynamicShape()} 禁用方块状态级形状缓存。
     *
     * @return 整体形状；通常由若干 {@code Block.box} 组合而成。
     */
    VoxelShape proxyShape();

    /**
     * 占位方块被右键（空手）时的交互转发，默认不做任何事。
     * <p>
     * {@code boundingPos} 为被点击的占位方块位置；需要真正操作主方块时以主方块位置
     * 重新构造 {@link BlockHitResult}。
     *
     * @return 交互结果；返回 {@link InteractionResult#PASS} 表示未处理。
     */
    default InteractionResult onBoundingUse(Level level, BlockPos boundingPos, Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    /**
     * 占位方块被右键（手持物品）时的交互转发，默认不做任何事。
     *
     * @return 交互结果；返回 {@link ItemInteractionResult#PASS_TO_DEFAULT_BLOCK_INTERACTION}
     *         表示未处理。
     */
    default ItemInteractionResult onBoundingUseItem(ItemStack stack, Level level, BlockPos boundingPos, Player player, InteractionHand hand, BlockHitResult hit) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * 占位方块查询能量能力时的代理入口——返回主方块在该面/偏移处提供的能量能力。
     * <p>
     * 默认不提供任何能力；需要让占位格子（如风力发电机塔身）可被取能量的主方块覆写此方法。
     * 占位方块 {@code BoundingBlock} 的方块能力注册会调用这里完成代理。
     *
     * @param side   查询方向（可为 null 表示内部/任意方向）。
     * @param offset 占位方块相对主方块的位置偏移。
     * @return 对应的 {@code IEnergyStorage}，不提供时返回 {@code null}。
     */
    @Nullable
    default IEnergyStorage getBoundingEnergyStorage(@Nullable Direction side, BlockPos offset) {
        return null;
    }
}
