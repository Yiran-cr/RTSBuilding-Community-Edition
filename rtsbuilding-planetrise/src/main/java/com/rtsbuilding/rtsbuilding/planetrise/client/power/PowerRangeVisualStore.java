package com.rtsbuilding.rtsbuilding.planetrise.client.power;

import com.rtsbuilding.rtsbuilding.planetrise.power.IPowerGridNode;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 客户端<b>能量节点范围圈存储</b>（参照 power_system_design.md 第六节视觉反馈）。
 * <p>
 * 所有「可组网节点」（发电机器 / 输电塔）的方块实体在客户端装载（{@code onLoad}）时注册
 * 到本单例、移除时注销；{@link PowerRangeOverlayRenderer} 在渲染阶段读取这些节点，绘制其
 * 链路范围圈（蓝）与供电范围圈（黄）。
 * <p>
 * 由 {@link com.rtsbuilding.rtsbuilding.planetrise.client.EnergyClient} 注册的快捷键触发
 * {@link #setGlobalShow(boolean)}——按住时全局显示所有已注册节点的半径圈。
 * <p>
 * 客户端主线程访问，无需并发容器。
 */
public final class PowerRangeVisualStore {

    public static final PowerRangeVisualStore INSTANCE = new PowerRangeVisualStore();

    private final Map<BlockPos, IPowerGridNode> nodes = new HashMap<>();
    private boolean globalShow;

    private PowerRangeVisualStore() {
    }

    /** 能量节点方块实体客户端装载时注册（幂等）。 */
    public void register(BlockPos pos, IPowerGridNode node) {
        nodes.put(pos.immutable(), node);
    }

    /** 能量节点方块实体移除时注销。 */
    public void unregister(BlockPos pos) {
        nodes.remove(pos);
    }

    /** 当前所有已注册的能量节点（供渲染遍历）。 */
    public Collection<IPowerGridNode> nodes() {
        return nodes.values();
    }

    /** 基于某个焦点的全局显示开关（渲染过滤器用当前位置剔除远处节点）。 */
    public boolean isGlobalShow() {
        return globalShow;
    }

    public void setGlobalShow(boolean value) {
        this.globalShow = value;
    }

    public void toggleGlobalShow() {
        this.globalShow = !this.globalShow;
    }
}
