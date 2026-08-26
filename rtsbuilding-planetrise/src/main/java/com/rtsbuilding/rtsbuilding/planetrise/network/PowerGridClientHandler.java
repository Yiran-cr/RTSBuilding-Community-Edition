package com.rtsbuilding.rtsbuilding.planetrise.network;

import com.rtsbuilding.rtsbuilding.api.powergrid.ExternalMachineConfig;
import com.rtsbuilding.rtsbuilding.api.powergrid.GridMember;
import com.rtsbuilding.rtsbuilding.api.powergrid.PowerDevice;
import com.rtsbuilding.rtsbuilding.api.powergrid.PowerGridSnapshot;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsAccessLevel;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsDeviceRole;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsMachineType;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsPowerStatus;
import com.rtsbuilding.rtsbuilding.planetrise.client.powergrid.PowerGridClientCache;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 电网多人系统<b>客户端处理器</b>：接收 {@link S2CPowerGridSnapshot} 与成员操作结果并写入缓存。
 */
public final class PowerGridClientHandler {

    private PowerGridClientHandler() {
    }

    public static void handleSnapshot(PowerGridPackets.S2CPowerGridSnapshot payload) {
        UUID owner = payload.owner().isBlank() ? null : UUID.fromString(payload.owner());
        List<GridMember> members = new ArrayList<>();
        for (PowerGridPackets.MemberEntry m : payload.members()) {
            UUID id = UUID.fromString(m.id());
            members.add(new GridMember(id, m.name(), m.online(), accessOf(m.access())));
        }
        List<PowerDevice> devices = new ArrayList<>();
        for (PowerGridPackets.DeviceEntry d : payload.devices()) {
            devices.add(new PowerDevice(roleOf(d.role()), d.x(), d.y(), d.z(),
                    statusOf(d.status()), d.metric(), d.label(), d.itemId()));
        }
        List<ExternalMachineConfig> configs = new ArrayList<>();
        for (PowerGridPackets.ExternalConfigEntry e : payload.externalConfigs()) {
            configs.add(new ExternalMachineConfig(e.machineId(), typeOf(e.type()),
                    e.powerValue(), e.linkRange(), e.priority()));
        }
        PowerGridSnapshot snapshot = owner == null ? null
                : new PowerGridSnapshot(owner, payload.ownerName(), members, devices,
                payload.totalGeneration(), payload.totalDemand());
        PowerGridClientCache.INSTANCE.update(snapshot, configs);
    }

    /** 成员操作结果：写入缓存供 UI 显示。 */
    public static void handleActionResult(PowerGridPackets.S2CPowerGridActionResult payload) {
        PowerGridClientCache.INSTANCE.setActionResult(payload.code());
    }

    private static RtsAccessLevel accessOf(byte b) {
        RtsAccessLevel[] v = RtsAccessLevel.values();
        return b >= 0 && b < v.length ? v[b] : RtsAccessLevel.USER;
    }

    private static RtsDeviceRole roleOf(byte b) {
        RtsDeviceRole[] v = RtsDeviceRole.values();
        return b >= 0 && b < v.length ? v[b] : RtsDeviceRole.CONSUMER;
    }

    private static RtsPowerStatus statusOf(byte b) {
        RtsPowerStatus[] v = RtsPowerStatus.values();
        return b >= 0 && b < v.length ? v[b] : RtsPowerStatus.OFFLINE;
    }

    @Nullable
    private static RtsMachineType typeOf(String s) {
        try {
            return RtsMachineType.valueOf(s);
        } catch (Exception e) {
            return RtsMachineType.GENERATOR;
        }
    }
}
