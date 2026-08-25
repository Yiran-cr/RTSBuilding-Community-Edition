package com.rtsbuilding.rtsbuilding.planetrise.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.rtsbuilding.rtsbuilding.common.geometry.RtsJavaModelShape;
import com.rtsbuilding.rtsbuilding.planetrise.EnergyBlockEntities;
import com.rtsbuilding.rtsbuilding.planetrise.block.entity.IBoundingBlock;
import com.rtsbuilding.rtsbuilding.planetrise.block.entity.WindGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 风力发电机——参考 Mekanism 的多占位方块示例。
 * <p>
 * 占用 <b>1 主方块 + 上方 2 个不可见占位方块</b>（{@link BoundingBlock}），塔身
 * 碰撞由 {@link #SHAPE} 提供（跨 3 格），占位方块按自身相对偏移平移共享该形状。
 * 视觉上整座塔由一个方块实体渲染器（Java 模型平移绘制）呈现，占位方块只负责
 * 碰撞与交互代理，不参与渲染。
 * <p>
 * 放置/拆除钩子：
 * <ul>
 *   <li>{@link #onPlace}：放置成功后创建 2 个占位方块；任一占位位置不可放置则回滚整个放置；</li>
 *   <li>{@link #onRemove}：拆除时清除全部占位方块（先移除实体再移除方块，防递归）。</li>
 * </ul>
 * RTS 远程放置/挖掘走 {@code level.setBlock}，同样会触发这两个钩子，因此多占位
 * 在 RTS 模式下自动生效。
 */
public class WindGeneratorBlock extends Block implements EntityBlock {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final MapCodec<WindGeneratorBlock> CODEC = simpleCodec(WindGeneratorBlock::new);

    /**
     * 塔身几何（方块像素坐标，模型原点对准方块底部中心，可跨格）——<b>与渲染模型同源</b>：
     * 逐盒对应 Blockbench {@code Wind_Turbine} 的 {@code bb_main} 塔身（y 0..48 像素 = 3 格高）：
     * 底座 y0..5 → 中层 y5..16 → 塔杆 y17..30 → 装饰环 y30..43 → 塔顶 y43..48，含各段收窄/凸台。
     * 由 {@link RtsJavaModelShape#buildShape()} 自动生成碰撞箱（服务端安全），客户端渲染模型
     * 由 {@code com.rtsbuilding.rtsbuilding.client.model.JavaModelShapeUtil#toCubeListBuilder(RtsJavaModelShape, float, float, float)}
     * 以原点 {@code (8, 0, 8)}（方块底部中心）从同一份数据构建，保证碰撞与视觉一致。
     */
    public static final RtsJavaModelShape MODEL = RtsJavaModelShape.builder()
            // 底座（含外圈四边板合并后的全宽平台）
            .addBox(1, 0, 1, 14, 5, 14)
            // 中层收窄
            .addBox(2, 5, 2, 12, 5, 12)
            .addBox(5, 10, 5, 6, 6, 6)
            .addBox(5, 16, 5, 6, 1, 6)
            // 塔杆
            .addBox(6, 17, 6, 4, 13, 4)
            .addBox(5, 30, 5, 6, 2, 6)
            // 装饰环（机舱区下方）
            .addBox(5, 32, 5, 6, 11, 6)
            .addBox(4, 33, 4, 8, 3, 8)
            .addBox(4, 38, 4, 8, 3, 8)
            // 塔顶
            .addBox(4, 43, 4, 8, 4, 8)
            .addBox(5, 47, 5, 6, 1, 6);

    /** 整塔碰撞/选择形状（跨 3 格，由几何数据生成）。 */
    public static final VoxelShape SHAPE = MODEL.buildShape();

    public WindGeneratorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WindGeneratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != EnergyBlockEntities.WIND_GENERATOR.get()) {
            return null;
        }
        if (level.isClientSide) {
            // 客户端 tick 仅驱动风叶旋转动画。
            return (lvl, pos, blockState, be) -> ((WindGeneratorBlockEntity) be).tickClient();
        }
        return (lvl, pos, blockState, be) -> ((WindGeneratorBlockEntity) be).tickServer();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        LOGGER.debug("[WIND-ONPLACE] side={} pos={} old={} be={}", level.isClientSide ? "CLIENT" : "SERVER", pos,
                oldState.getBlock(), level.getBlockEntity(pos));
        if (state.is(oldState.getBlock())) {
            return;
        }
        // 服务端与客户端各自放置占位方块并设置 mainPos（两端各自创建，无需网络同步实体 NBT），
        // 保证客户端占位格子同样具备碰撞/交互/连带破坏逻辑。
        if (level.getBlockEntity(pos) instanceof IBoundingBlock ib) {
            boolean allPlaced = ib.placeBoundingBlocks();
            LOGGER.debug("[WIND-ONPLACE] side={} allPlaced={}", level.isClientSide ? "CLIENT" : "SERVER", allPlaced);
            if (!allPlaced && !level.isClientSide) {
                // 服务端：某个占位位置被占用，回滚整个放置，避免残缺塔身。
                level.removeBlock(pos, false);
            }
        } else {
            LOGGER.debug("[WIND-ONPLACE] side={} be is not IBoundingBlock", level.isClientSide ? "CLIENT" : "SERVER");
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            // 主方块被拆除/替换：两端各自清除全部占位方块（幂等）。
            if (level.getBlockEntity(pos) instanceof IBoundingBlock ib) {
                ib.removeBoundingBlocks();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
