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

    /** 三档历史时序数据：每点 [generation, demand]，按时间顺序（旧 → 新）。 */
    private volatile List<long[]> history5s = List.of();
    private volatile List<long[]> history1m = List.of();
    private volatile List<long[]> history1h = List.of();

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

    /** 更新历史时序数据缓存（由 S2C 历史回包写入）。 */
    public synchronized void updateHistory(List<long[]> h5, List<long[]> h1m, List<long[]> h1h) {
        this.history5s = h5 == null ? List.of() : List.copyOf(h5);
        this.history1m = h1m == null ? List.of() : List.copyOf(h1m);
        this.history1h = h1h == null ? List.of() : List.copyOf(h1h);
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

    /** 5 秒档历史时序（只读副本，旧 → 新）。 */
    public List<long[]> history5s() {
        return history5s;
    }

    /** 1 分钟档历史时序（只读副本，旧 → 新）。 */
    public List<long[]> history1m() {
        return history1m;
    }

    /** 1 小时档历史时序（只读副本，旧 → 新）。 */
    public List<long[]> history1h() {
        return history1h;
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
