package com.rtsbuilding.rtsbuilding.planetrise.client.powergrid;

import com.rtsbuilding.rtsbuilding.api.powergrid.ExternalMachineConfig;
import com.rtsbuilding.rtsbuilding.api.powergrid.PowerGridSnapshot;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 客户端<b>电网组缓存</b>：保存从服务端同步来的 {@link PowerGridSnapshot}（电网信息/成员/设备）
 * 与外部机器类型配置，供电网管理面板（uifw）实时读取。
 * <p>
 * 由 {@code PowerGridClientHandler} 在收到 S2C 快照包时更新；客户端主线程访问，无需并发容器。
 */
public final class PowerGridClientCache {

    public static final PowerGridClientCache INSTANCE = new PowerGridClientCache();

    @Nullable
    private PowerGridSnapshot grid;
    private final List<ExternalMachineConfig> externalConfigs = new ArrayList<>();

    /** 最近一次成员操作结果码（0=成功，1=无管理员权限，2=无所有者权限，3=无效/不在线）。 */
    private int actionResult;

    private PowerGridClientCache() {
    }

    /** 更新快照与外部配置缓存。 */
    public synchronized void update(@Nullable PowerGridSnapshot snapshot, List<ExternalMachineConfig> configs) {
        this.grid = snapshot;
        this.externalConfigs.clear();
        if (configs != null) {
            this.externalConfigs.addAll(configs);
        }
    }

    /** 记录最近一次成员操作结果。 */
    public synchronized void setActionResult(int code) {
        this.actionResult = code;
    }

    /** 读取最近一次成员操作结果。 */
    public synchronized int actionResult() {
        return actionResult;
    }

    /** 当前玩家所在电网组快照；未加入任何组时返回 null。 */
    @Nullable
    public synchronized PowerGridSnapshot grid() {
        return grid;
    }

    /** 外部机器类型配置（只读副本）。 */
    public synchronized List<ExternalMachineConfig> externalConfigs() {
        return Collections.unmodifiableList(new ArrayList<>(externalConfigs));
    }

    /** 按机器 ID 查询外部配置；未配置时返回 null。 */
    @Nullable
    public synchronized ExternalMachineConfig externalConfig(String machineId) {
        for (ExternalMachineConfig c : externalConfigs) {
            if (c.machineId().equals(machineId)) {
                return c;
            }
        }
        return null;
    }
}
