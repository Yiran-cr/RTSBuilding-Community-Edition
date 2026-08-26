package com.rtsbuilding.rtsbuilding.api.powergrid;

import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.UUID;

/**
 * 电网多人系统 API（客户端视角）。 
 * <p>
 * 供主模组 / 第三方从 {@code rtsbuilding-planetrise} 插件读取当前玩家所在的电网组信息并触发
 * 服务端操作（成员邀请/移除、外部机器类型配置、设备定位）。插件为内部实现，通过
 * {@link #setImplementation(RtsPowerGrid)} 注入。
 * <p>
 * 查询方法返回的是<b>客户端已同步的缓存快照</b>（由插件通过 S2C 网络包推送），因此可在
 * UI 渲染线程安全读取；动作方法会向服务端发送 C2S 请求。
 */
public interface RtsPowerGrid {

    /** 获取全局电网 API 实例（插件初始化后可用）。 */
    static RtsPowerGrid get() {
        return Holder.INSTANCE;
    }

    /** 触发动作：向服务器请求打开/刷新电网管理面板（服务端回推最新快照）。 */
    void requestRefresh();

    /** 当前玩家（本地玩家）所在的电网组快照；未加入任何组时返回 {@code null}。 */
    PowerGridSnapshot currentGrid();

    /** 当前玩家名下可选电网组的所有者列表（含自己）；通常仅一个。 */
    Collection<UUID> gridOwners();

    /** 全体外部机器类型配置（modId:machineId → 配置）。 */
    Collection<ExternalMachineConfig> externalMachines();

    /** 获取某外部机器类型配置；未配置时返回 {@code null}。 */
    ExternalMachineConfig externalConfig(String machineId);

    /** 邀请指定<b>玩家名</b>的在线玩家加入当前电网组（服务端解析名称/校验邀请者为所有者）。 */
    void inviteMember(String playerName);

    /** 从当前电网组移除成员（服务端校验权限：OWNER 可移除任意非 OWNER；ADMIN 可移除 USER）。 */
    void removeMember(UUID playerId);

    /** 设置某成员的访问等级（升降级；服务端校验：OWNER 可升降任意成员，ADMIN 仅降级 USER）。 */
    void setMemberAccess(UUID memberId, RtsAccessLevel level);

    /** 把电网所有权转移给某成员（仅 OWNER，转移后旧主降为 USER）。 */
    void transferOwnership(UUID memberId);

    /** 最近一次成员操作的结果码（0=成功，1=无管理员权限，2=无所有者权限，3=目标无效/不在线）。 */
    int lastActionResult();

    /** 为外部机器设置类型与默认值（服务端持久化）。 */
    void setExternalMachineType(String machineId, RtsMachineType type, double powerValue, double linkRange);

    /** 清除某外部机器的类型配置（服务端持久化）。 */
    void clearExternalMachineType(String machineId);

    /** 在世界中定位某设备（UI「点击定位」，客户端把相机/视角移动到该设备）。 */
    void locateDevice(long x, long y, long z);

    /**
     * 设置内部实现。仅由 {code rtsbuilding-planetrise} 在初始化期间调用。
     *
     * @throws IllegalStateException 若实现已被设置。
     */
    @ApiStatus.Internal
    static void setImplementation(RtsPowerGrid implementation) {
        if (Holder.INSTANCE != null && implementation != null) {
            throw new IllegalStateException("RtsPowerGrid implementation already set");
        }
        Holder.INSTANCE = implementation;
    }

    final class Holder {
        private Holder() {
        }

        static RtsPowerGrid INSTANCE;
    }
}
