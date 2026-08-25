package com.rtsbuilding.rtsbuilding.planetrise.block.entity;

import com.mojang.logging.LogUtils;
import com.rtsbuilding.rtsbuilding.planetrise.EnergyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 占位方块实体——仅保存「主方块」的位置引用，无任何玩法逻辑。
 * <p>
 * 占位方块本身不可见、无 tick、无能力，所有形状/交互/破坏都通过 {@link #getMain()}
 * 代理到主方块实体（{@link IBoundingBlock}）。{@code mainPos} 通过 NBT 持久化，
 * 因此世界重启/区块重载后占位方块依然能重定向回主方块。
 */
public class BoundingBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String NBT_MAIN = "main";

    /** 主方块位置；为 null 时表示这是一个"孤儿"占位（主方块被命令/异常移除）。 */
    @Nullable
    private BlockPos mainPos;

    public BoundingBlockEntity(BlockPos pos, BlockState state) {
        super(EnergyBlockEntities.BOUNDING_BLOCK.get(), pos, state);
    }

    /**
     * 设置主方块位置。
     *
     * @param mainPos 主方块位置，{@code null} 表示解除关联。
     * @param sync    服务端为 {@code true} 时主动把 {@code mainPos} 同步给客户端。
     *                NeoForge 1.21 的 {@code ClientboundBlockUpdatePacket} 已不携带方块实体数据，
     *                必须显式发送 {@link ClientboundBlockEntityDataPacket}（参考 Mekanism
     *                {@code TileEntityBoundingBlock.setMainLocation(pos, sync)} → {@code sendUpdatePacket()}），
     *                客户端经 {@code handleUpdateTag} → {@link #loadAdditional} 恢复 {@code mainPos}。
     */
    public void setMainLocation(@Nullable BlockPos mainPos, boolean sync) {
        this.mainPos = mainPos;
        if (sync && level instanceof ServerLevel serverLevel && !isRemoved()) {
            setChanged();
            ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(this);
            ChunkPos chunkPos = serverLevel.getChunkAt(worldPosition).getPos();
            for (ServerPlayer player : serverLevel.players()) {
                if (player.getChunkTrackingView().contains(chunkPos)) {
                    player.connection.send(packet);
                }
            }
        }
    }

    /** 是否能从给定位置重定向到主方块（排除主方块自身，防止自己代理自己）。 */
    public boolean canRedirectFrom(BlockPos boundingPos) {
        return mainPos != null && !mainPos.equals(boundingPos);
    }

    /** 主方块位置；未关联时回退为自身位置（此时不产生代理）。 */
    public BlockPos getMainPos() {
        return mainPos == null ? worldPosition : mainPos;
    }

    /**
     * 查找主方块实体。
     *
     * @return 主方块的 {@link IBoundingBlock} 实体；主方块未加载或不是多占位方块时返回 {@code null}。
     */
    @Nullable
    public IBoundingBlock getMain() {
        if (level == null || mainPos == null) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(mainPos);
        return be instanceof IBoundingBlock ib ? ib : null;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        boolean has = tag.contains(NBT_MAIN);
        LOGGER.debug("[BB-NBT] load side={} pos={} containsMain={} tag={}", level != null && level.isClientSide ? "CLIENT" : "SERVER", worldPosition, has, tag);
        if (has) {
            mainPos = NbtUtils.readBlockPos(tag, NBT_MAIN).orElse(null);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (mainPos != null) {
            tag.put(NBT_MAIN, NbtUtils.writeBlockPos(mainPos));
        }
    }

    /**
     * 实体更新包内容：显式只携带 {@code mainPos}（参照 Mekanism
     * {@code TileEntityBoundingBlock.getReducedUpdateTag}），确保占位方块实体更新时
     * 客户端必能恢复主方块位置，而不依赖整段 NBT。
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        if (mainPos != null) {
            tag.put(NBT_MAIN, NbtUtils.writeBlockPos(mainPos));
        }
        return tag;
    }

    /** 客户端接收实体更新包后恢复 {@code mainPos}。 */
    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider provider) {
        super.handleUpdateTag(tag, provider);
        if (tag.contains(NBT_MAIN)) {
            mainPos = NbtUtils.readBlockPos(tag, NBT_MAIN).orElse(null);
        }
    }
}
