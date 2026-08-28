package com.rtsbuilding.rtsbuilding.api.powergrid;

/**
 * 电网设备条目（UI 设备列表/定位用）。
 *
 * @param role          设备角色。
 * @param x/y/z         设备世界坐标。
 * @param status        供电状态。
 * @param metric        主要量值：发电机=产电速率，输电塔=配额，用电器=需求。
 * @param label         显示名称（翻译键，客户端用 {@code Component.translatable} 显示）。
 * @param itemId        该方块对应物品的注册 id（{@code mod_id:item}），供 UI 绘制物品图标。
 * @param canToggleRole 是否允许用户在 UI 中切换角色（用电 ↔ 发电），仅外部模组设备可切换。
 */
public record PowerDevice(
        RtsDeviceRole role,
        long x, long y, long z,
        RtsPowerStatus status,
        long metric,
        String label,
        String itemId,
        boolean canToggleRole) {

    /**
     * 旧版构造器（无 {@code canToggleRole}），默认 {@code false}。
     *
     * @deprecated 请使用全参构造器并显式指定 canToggleRole。
     */
    @Deprecated
    public PowerDevice(RtsDeviceRole role, long x, long y, long z, RtsPowerStatus status,
                       long metric, String label, String itemId) {
        this(role, x, y, z, status, metric, label, itemId, false);
    }
}
