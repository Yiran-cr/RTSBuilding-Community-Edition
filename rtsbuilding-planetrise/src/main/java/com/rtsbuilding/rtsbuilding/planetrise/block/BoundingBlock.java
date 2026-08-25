package com.rtsbuilding.rtsbuilding.planetrise.block;

import com.mojang.serialization.MapCodec;
import com.rtsbuilding.rtsbuilding.planetrise.EnergyBlockEntities;
import com.rtsbuilding.rtsbuilding.planetrise.block.entity.BoundingBlockEntity;
import com.rtsbuilding.rtsbuilding.planetrise.block.entity.IBoundingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * 占位方块（参考 Mekanism {@code BlockBounding}）——多占位方块（如风力发电机）的
 * <b>不可见代理方块</b>。
 * <p>
 * 占位方块自身不渲染（{@link RenderShape#INVISIBLE}）、无 tick、无能力，仅通过方块实体
 * {@link BoundingBlockEntity} 持有的 {@code mainPos} 找到主方块，把以下行为<b>全部代理</b>
 * 给主方块：
 * <ul>
 *   <li><b>形状</b>：{@link #getShape} / {@link #getCollisionShape} 返回主方块整体形状
 *       （{@link IBoundingBlock#proxyShape()}）按相对偏移平移后的对应段；</li>
 *   <li><b>交互</b>：右键（含手持物品）转发给主方块方块；</li>
 *   <li><b>破坏</b>：占位被破坏时连带破坏主方块，由主方块 {@code onRemove} 清除其余占位；</li>
 *   <li><b>其他</b>：拾取/挖掘进度/爆炸抗性均转发主方块。</li>
 * </ul>
 * <p>
 * 由于形状依赖具体位置，方块属性必须使用 {@code dynamicShape()} 禁用方块状态级缓存。
 */
public class BoundingBlock extends Block implements EntityBlock {

    public static final MapCodec<BoundingBlock> CODEC = simpleCodec(BoundingBlock::new);

    public BoundingBlock(BlockBehaviour.Properties properties) {
        // dynamicShape：占位方块的形状随 mainPos 变化，不能走方块状态级缓存。
        // noOcclusion：避免遮挡形状带来的相邻方块渲染剔除误判。
        // pushReaction(BLOCK)：防止任何 mod（如 Quark）推动带方块实体的占位。
        super(properties.dynamicShape().noOcclusion().pushReaction(PushReaction.BLOCK));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /**
     * 读取位置 {@code pos} 处占位方块所关联的主方块位置。
     *
     * @return 主方块位置；不是占位方块、或未关联时返回 {@code null}。
     */
    @Nullable
    public static BlockPos getMainPos(BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof BoundingBlockEntity bounding && bounding.canRedirectFrom(pos)) {
            return bounding.getMainPos();
        }
        return null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoundingBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return proxyShape(state, level, pos, context, BlockStateBase::getShape);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return proxyShape(state, level, pos, context, BlockStateBase::getCollisionShape);
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return proxyShape(state, level, pos, context, BlockStateBase::getVisualShape);
    }

    /**
     * 把主方块的形状按「自身位置 - 主方块位置」平移，得到占位格子对应的那一段碰撞。
     */
    private VoxelShape proxyShape(BlockState state, BlockGetter level, BlockPos pos,
          @Nullable CollisionContext context, ShapeGetter getter) {
        BlockPos mainPos = getMainPos(level, pos);
        if (mainPos == null) {
            // 孤儿占位：视为空气，允许玩家走进去。
            return Shapes.empty();
        }
        BlockEntity be = level.getBlockEntity(mainPos);
        if (!(be instanceof IBoundingBlock ib)) {
            return Shapes.empty();
        }
        BlockPos offset = pos.subtract(mainPos);
        VoxelShape shape = ib.proxyShape().move(-offset.getX(), -offset.getY(), -offset.getZ());
        return shape;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos mainPos = getMainPos(level, pos);
        if (mainPos == null) {
            return InteractionResult.PASS;
        }
        BlockState mainState = level.getBlockState(mainPos);
        return mainState.isAir() ? InteractionResult.PASS : mainState.useWithoutItem(level, player, withHitForMain(hit, mainPos));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
          Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos mainPos = getMainPos(level, pos);
        if (mainPos == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        BlockState mainState = level.getBlockState(mainPos);
        return mainState.isAir() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                : mainState.useItemOn(stack, level, player, hand, withHitForMain(hit, mainPos));
    }

    /** 把命中结果重定向到主方块位置中心（方向保持不变）。 */
    private BlockHitResult withHitForMain(BlockHitResult hit, BlockPos mainPos) {
        Vec3 location = mainPos.getCenter();
        return new BlockHitResult(location, hit.getDirection(), mainPos, hit.isInside());
    }

    /**
     * 占位被替换/移除时：连带破坏主方块（若主方块还在），由主方块的 {@code onRemove}
     * 统一清除其余占位，避免残留孤儿占位。
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockPos mainPos = getMainPos(level, pos);
            if (mainPos != null) {
                BlockState mainState = level.getBlockState(mainPos);
                if (!mainState.isAir()) {
                    level.removeBlock(mainPos, false);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack stack) {
        // 掉落与拆除都交给主方块：主方块掉落物品，然后移除占位（占位 onRemove 连带破坏主方块）。
        BlockPos mainPos = getMainPos(level, pos);
        if (mainPos != null) {
            BlockState mainState = level.getBlockState(mainPos);
            if (!mainState.isAir()) {
                mainState.getBlock().playerDestroy(level, player, mainPos, mainState, level.getBlockEntity(mainPos), stack);
            }
        }
        level.removeBlock(pos, false);
    }

    /**
     * 破坏占位方块时的关键处理（参考 Mekanism {@code BlockBounding.onDestroyedByPlayer}）：
     * <ul>
     *   <li><b>willHarvest=true（生存挖掘）</b>：<b>不移除占位方块</b>，直接返回 {@code true}。
     *       否则默认实现会先把占位 {@code setBlock(air)}，导致随后的 {@link #playerDestroy}
     *       转发主方块时占位实体已消失、{@code mainPos} 取不到，主方块物品掉落丢失；</li>
     *   <li><b>willHarvest=false（创造/命令）</b>：显式破坏主方块（由其清理其余占位），
     *       再走默认流程移除占位自身。</li>
     * </ul>
     */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluidState) {
        if (willHarvest) {
            return true;
        }
        BlockPos mainPos = getMainPos(level, pos);
        if (mainPos != null) {
            BlockState mainState = level.getBlockState(mainPos);
            if (!mainState.isAir()) {
                mainState.onDestroyedByPlayer(level, mainPos, player, false, mainState.getFluidState());
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, false, fluidState);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // 破坏前把游戏事件/状态变化委托给主方块，避免在占位位置重复触发。
        BlockPos mainPos = getMainPos(level, pos);
        if (mainPos != null) {
            BlockState mainState = level.getBlockState(mainPos);
            if (!mainState.isAir()) {
                mainState.getBlock().playerWillDestroy(level, mainPos, mainState, player);
                return state;
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onExplosionHit(BlockState state, Level level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
        // 爆炸破坏占位 → 委托给主方块：主方块掉落自身物品并被移除（onRemove 清理其余占位）。
        BlockPos mainPos = getMainPos(level, pos);
        if (mainPos == null) {
            super.onExplosionHit(state, level, pos, explosion, dropConsumer);
        } else {
            level.getBlockState(mainPos).onExplosionHit(level, mainPos, explosion, dropConsumer);
        }
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        // 允许替换"孤儿占位"（主方块被命令/异常移除后残留的占位），防止占位卡死位置。
        return getMainPos(context.getLevel(), context.getClickedPos()) == null;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean isMoving) {
        // 红石/邻居变化转发给主方块处理。
        BlockPos mainPos = getMainPos(level, pos);
        if (mainPos != null) {
            level.getBlockState(mainPos).handleNeighborChanged(level, mainPos, neighborBlock, neighborPos, isMoving);
        }
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        BlockPos mainPos = getMainPos(level, pos);
        if (mainPos == null) {
            return ItemStack.EMPTY;
        }
        BlockState mainState = level.getBlockState(mainPos);
        if (mainState.isAir()) {
            return ItemStack.EMPTY;
        }
        return mainState.getBlock().getCloneItemStack(mainState, target, level, mainPos, player);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        BlockPos mainPos = getMainPos(level, pos);
        if (mainPos == null) {
            return super.getDestroyProgress(state, player, level, pos);
        }
        return level.getBlockState(mainPos).getDestroyProgress(player, level, mainPos);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        // 占位方块不可行走（塔身区域挡路），与 Mekanism 一致。
        return false;
    }

    private interface ShapeGetter {

        VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context);
    }
}
