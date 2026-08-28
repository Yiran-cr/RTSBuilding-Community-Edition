package com.rtsbuilding.rtsbuilding.planetrise.network;

import com.rtsbuilding.rtsbuilding.api.powergrid.ExternalMachineConfig;
import com.rtsbuilding.rtsbuilding.api.powergrid.PowerGridSnapshot;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsAccessLevel;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsDeviceRole;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsMachineType;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsPowerGrid;
import com.rtsbuilding.rtsbuilding.planetrise.client.powergrid.PowerGridClientCache;
import com.rtsbuilding.rtsbuilding.planetrise.power.PowerGridOwnership;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * {@link RtsPowerGrid} 的客户端实现（由 {@code rtsbuilding-planetrise} 注入）。
 * <p>
 * 读方法返回 {@link PowerGridClientCache} 中的缓存快照（服务端 S2C 推送）；动作方法向服务端
 * 发送对应 C2S 包。客户端主线程访问。
 */
public final class PowerGridApiImpl implements RtsPowerGrid {

    /** 全局实例，commonSetup / 客户端构造时注入到 {@link RtsPowerGrid}。 */
    public static final PowerGridApiImpl INSTANCE = new PowerGridApiImpl();

    private PowerGridApiImpl() {
    }

    @Override
    public void requestRefresh() {
        PacketDistributor.sendToServer(new PowerGridPackets.C2SPowerGridRefresh());
    }

    @Override
    public PowerGridSnapshot currentGrid() {
        return PowerGridClientCache.INSTANCE.grid();
    }

    @Override
    public Collection<UUID> gridOwners() {
        PowerGridSnapshot g = currentGrid();
        return g == null ? Collections.emptyList() : Collections.singletonList(g.owner());
    }

    @Override
    public Collection<ExternalMachineConfig> externalMachines() {
        return PowerGridClientCache.INSTANCE.externalConfigs();
    }

    @Override
    public ExternalMachineConfig externalConfig(String machineId) {
        return PowerGridClientCache.INSTANCE.externalConfig(machineId);
    }

    @Override
    public void inviteMember(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        PacketDistributor.sendToServer(new PowerGridPackets.C2SPowerGridInvite(playerName));
    }

    @Override
    public void removeMember(UUID playerId) {
        if (playerId == null) {
            return;
        }
        PacketDistributor.sendToServer(new PowerGridPackets.C2SPowerGridRemove(playerId.toString()));
    }

    @Override
    public void setMemberAccess(UUID memberId, RtsAccessLevel level) {
        if (memberId == null || level == null) {
            return;
        }
        byte action = level == RtsAccessLevel.ADMIN
                ? PowerGridOwnership.ACTION_SET_ADMIN : PowerGridOwnership.ACTION_SET_USER;
        PacketDistributor.sendToServer(new PowerGridPackets.C2SPowerGridMemberAction(action, memberId.toString()));
    }

    @Override
    public void transferOwnership(UUID memberId) {
        if (memberId == null) {
            return;
        }
        PacketDistributor.sendToServer(new PowerGridPackets.C2SPowerGridMemberAction(
                PowerGridOwnership.ACTION_TRANSFER, memberId.toString()));
    }

    @Override
    public int lastActionResult() {
        return PowerGridClientCache.INSTANCE.actionResult();
    }

    @Override
    public void setExternalMachineType(String machineId, RtsMachineType type, double powerValue, double linkRange) {
        if (machineId == null || type == null) {
            return;
        }
        PacketDistributor.sendToServer(new PowerGridPackets.C2SPowerGridExternalConfig(
                machineId, type.name(), powerValue, linkRange, false));
    }

    @Override
    public void clearExternalMachineType(String machineId) {
        if (machineId == null) {
            return;
        }
        PacketDistributor.sendToServer(new PowerGridPackets.C2SPowerGridExternalConfig(
                machineId, "CONSUMER", 0, 0, true));
    }

    @Override
    public void locateDevice(long x, long y, long z) {
        // 阶段2：先记录定位意图（供主模组 RTS 相机消费/后续聚焦）；直接相机跳转挂主 mod 内核。
        LocateIntent.INSTANCE.lastLocate = new long[]{x, y, z};
    }

    @Override
    public void requestHistory() {
        PacketDistributor.sendToServer(new PowerGridPackets.C2SPowerGridHistoryRequest());
    }

    @Override
    public void toggleDeviceRole(long x, long y, long z, RtsDeviceRole newRole) {
        if (newRole == null) {
            return;
        }
        PacketDistributor.sendToServer(new PowerGridPackets.C2SDeviceRoleToggle(
                x, y, z, (byte) newRole.ordinal()));
    }

    @Override
    public void refreshTower(long x, long y, long z) {
        PacketDistributor.sendToServer(new PowerGridPackets.C2SPowerGridTowerRefresh(x, y, z));
    }

    @Override
    public List<long[]> history5s() {
        return PowerGridClientCache.INSTANCE.history5s();
    }

    @Override
    public List<long[]> history1m() {
        return PowerGridClientCache.INSTANCE.history1m();
    }

    @Override
    public List<long[]> history1h() {
        return PowerGridClientCache.INSTANCE.history1h();
    }

    /** 最近一次设备定位意图（客户端，供主模组 / UI 读取）。 */
    public static final class LocateIntent {
        public static final LocateIntent INSTANCE = new LocateIntent();
        public volatile long[] lastLocate;

        private LocateIntent() {
        }
    }
}
