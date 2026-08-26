package com.rtsbuilding.rtsbuilding.planetrise.block.entity;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.api.energy.Action;
import com.rtsbuilding.rtsbuilding.api.energy.AutomationType;
import com.rtsbuilding.rtsbuilding.common.energy.BasicEnergyContainer;
import com.rtsbuilding.rtsbuilding.planetrise.client.power.PowerRangeVisualStore;
import com.rtsbuilding.rtsbuilding.planetrise.power.IPowerGridNode;
import com.rtsbuilding.rtsbuilding.planetrise.power.PowerGridManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 所有 RTS 能量机器（戴森球计划风格的能量节点）的抽象基类。
 * <p>
 * 统一封装能量机器最通用、最稳定的样板逻辑，子类只需专注各自的业务行为
 * （产电 / 无线搬运 / 存储 / 耗电）。本基类提供：
 * <ul>
 *   <li><b>能量缓冲</b>：一个 {@link BasicEnergyContainer}（FE 存储），供子类产电 / 耗电 / 被外部能力读写；</li>
 *   <li><b>能量能力工厂</b>：{@link #createEnergyStorage(boolean, boolean)} 生成封装了收/发配置的
 *       {@link ContainerEnergyStorage}，供方块能力注册（{@code EnergyCapabilities}）复用；</li>
 *   <li><b>内部充/放电便捷方法</b>：{@link #addEnergy(long)} / {@link #consumeEnergy(long)}，供机器
 *       自己产电 / 耗电时调用（内部 AutomationType）；</li>
 *   <li><b>服务端 / 客户端 tick 模板</b>：{@code tickServer()} / {@code tickClient()} 先做通用守卫
 *       （level / 客户端 / {@code Config.isTechnologizedEnabled()} 开关）与<b>多占位同步</b>
 *       （首次 tick 强制把占位 mainPos 重发给客户端，参考 Mekanism {@code syncMasterPosition}），
 *       再调用子类钩子 {@link #onServerTick()} / {@link #onClientTick()}；</li>
 *   <li><b>NBT 持久化</b>：energy 缓冲读写 + {@link #saveEnergyAdditional} / {@link #loadEnergyAdditional}
 *       扩展钩子（供子类存岩浆罐等业务数据）。</li>
 * </ul>
 * 子类若不具有业务 tick（如储能单元）或不需要多占位，继承后不覆写相应钩子即可。
 */
public abstract class AbstractEnergyMachineBlockEntity extends BlockEntity {

    private static final String NBT_ENERGY = "energy";
    private static final String NBT_GRID_OWNER = "gridOwner";

    /** 本机器的能量缓冲（FE 存储）。 */
    private final BasicEnergyContainer buffer;

    /** 本节点归属的电网所有者（放置者玩家 UUID）；null 表示尚未归属。 */
    @Nullable
    private UUID gridOwnerId;

    /** 多占位机器：服务端首次 tick 是否需要对占位方块强制重发 mainPos（参照 Mekanism syncMasterToBounding）。 */
    private boolean syncMasterToBounding = true;

    /**
     * @param type     本机器对应的方块实体类型。
     * @param pos      方块位置。
     * @param state    方块状态。
     * @param capacity 能量缓冲容量（FE），来自对应的 {@code Config} 配置。
     */
    protected AbstractEnergyMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, long capacity) {
        super(type, pos, state);
        this.buffer = BasicEnergyContainer.create(capacity, this::markChanged);
    }

    /** 内容变更时标记方块待保存（供能量缓冲监听器回调）。 */
    protected void markChanged() {
        setChanged();
    }

    /** @return 本机器的能量缓冲，供方块能力注册 / 业务逻辑读取。 */
    public BasicEnergyContainer getBuffer() {
        return buffer;
    }

    /** 本节点归属的电网所有者玩家 UUID；未归属时返回 null。 */
    @Nullable
    public UUID gridOwner() {
        return gridOwnerId;
    }

    /** 设置本节点归属的电网所有者玩家 UUID（null 表示解除归属）。 */
    public void setGridOwner(@Nullable UUID ownerId) {
        if (!java.util.Objects.equals(this.gridOwnerId, ownerId)) {
            this.gridOwnerId = ownerId;
            setChanged();
        }
    }

    /**
     * 构造一个封装了收/发配置的能量能力，供方块能力注册复用。
     * <p>
     * eg. 发电机传出时传 {@code (false, true)}（只能被抽走），双向缓冲传 {@code (true, true)}。
     *
     * @param canReceive 是否允许外部充能。
     * @param canExtract 是否允许外部抽能。
     * @return 封装本机器缓冲的能量能力。
     */
    protected ContainerEnergyStorage createEnergyStorage(boolean canReceive, boolean canExtract) {
        return new ContainerEnergyStorage(buffer, canReceive, canExtract);
    }

    /** 机器内部向缓冲注入能量（产电时用），返回实际注入量。 */
    protected long addEnergy(long amount) {
        return buffer.insert(amount, Action.EXECUTE, AutomationType.INTERNAL);
    }

    /** 机器内部从缓冲取能量（耗电时用），返回实际取出量。 */
    protected long consumeEnergy(long amount) {
        return buffer.extract(amount, Action.EXECUTE, AutomationType.INTERNAL);
    }

    /**
     * 服务端每 tick 模板（由方块 {@code getTicker} 调用）。
     * <p>
     * 先做通用守卫与多占位同步，再把业务逻辑交给 {@link #onServerTick()}。
     */
    public final void tickServer() {
        if (level == null || level.isClientSide || !Config.isTechnologizedEnabled()) {
            return;
        }
        // 多占位机器首次 tick 兜底：占位方块的 mainPos 可能因客户端 onPlace 未执行或实体更新包
        // 时序竞态未同步到客户端（客户端占位格无碰撞/无选择箱）。此时强制重发实体更新包，参照
        // Mekanism TileEntityMekanism.tickServer → syncMasterToBounding → syncMasterPosition。
        if (this instanceof IBoundingBlock) {
            if (syncMasterToBounding) {
                syncMasterToBounding = false;
                ((IBoundingBlock) this).syncBoundingBlocks();
            }
        }
        onServerTick();
    }

    /** 子类在服务端每 tick 的具体业务逻辑（产电 / 搬运等）；无 tick 的机器（如纯存储单元）可不覆写。 */
    protected void onServerTick() {
    }

    /**
     * 客户端每 tick 模板（由方块 {@code getTicker} 调用）。
     * <p>
     * 默认无操作；需要驱动客户端动画（如风力发电机风叶旋转）的子类覆写 {@link #onClientTick()}。
     */
    public final void tickClient() {
        onClientTick();
    }

    /** 子类在客户端每 tick 的具体业务逻辑（纯视觉）。 */
    protected void onClientTick() {
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put(NBT_ENERGY, buffer.serializeNBT(provider));
        if (gridOwnerId != null) {
            tag.putUUID(NBT_GRID_OWNER, gridOwnerId);
        }
        saveEnergyAdditional(tag, provider);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains(NBT_ENERGY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            buffer.deserializeNBT(provider, tag.getCompound(NBT_ENERGY));
        }
        if (tag.contains(NBT_GRID_OWNER)) {
            gridOwnerId = tag.getUUID(NBT_GRID_OWNER);
        }
        loadEnergyAdditional(tag, provider);
    }

    /** 子类扩展 NBT 持久化（如岩浆罐），在能量缓冲之后写入。 */
    protected void saveEnergyAdditional(CompoundTag tag, HolderLookup.Provider provider) {
    }

    /** 子类扩展 NBT 读取（如岩浆罐），在能量缓冲之后读取。 */
    protected void loadEnergyAdditional(CompoundTag tag, HolderLookup.Provider provider) {
    }

    /** 两端装载时：可组网节点向对应侧的电网/范围存储注册（客户端供电网渲染用）。 */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && this instanceof IPowerGridNode node) {
            // 服务端节点由每次 tickServer 回归注册（冗余安全），此处不做强注册，避免卸载不一致。
            return;
        }
        if (level != null && level.isClientSide && this instanceof IPowerGridNode node) {
            PowerRangeVisualStore.INSTANCE.register(worldPosition, node);
        }
    }

    /** 方块拆除/区块卸载：从服务端电网或客户端范围存储注销。 */
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level == null || !(this instanceof IPowerGridNode node)) {
            return;
        }
        if (level.isClientSide) {
            PowerRangeVisualStore.INSTANCE.unregister(worldPosition);
        } else {
            PowerGridManager.get(level).unregister(worldPosition);
        }
    }
}
