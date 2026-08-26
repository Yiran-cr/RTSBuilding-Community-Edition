package com.rtsbuilding.rtsbuilding.planetrise.network;

import com.rtsbuilding.rtsbuilding.api.powergrid.ExternalMachineConfig;
import com.rtsbuilding.rtsbuilding.api.powergrid.GridMember;
import com.rtsbuilding.rtsbuilding.api.powergrid.PowerDevice;
import com.rtsbuilding.rtsbuilding.api.powergrid.PowerGridSnapshot;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsAccessLevel;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsDeviceRole;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsMachineType;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsPowerStatus;
import com.rtsbuilding.rtsbuilding.planetrise.block.entity.PowerTowerBlockEntity;
import com.rtsbuilding.rtsbuilding.planetrise.network.PowerGridPackets.C2SPowerGridExternalConfig;
import com.rtsbuilding.rtsbuilding.planetrise.network.PowerGridPackets.C2SPowerGridInvite;
import com.rtsbuilding.rtsbuilding.planetrise.network.PowerGridPackets.C2SPowerGridMemberAction;
import com.rtsbuilding.rtsbuilding.planetrise.network.PowerGridPackets.C2SPowerGridRemove;
import com.rtsbuilding.rtsbuilding.planetrise.network.PowerGridPackets.DeviceEntry;
import com.rtsbuilding.rtsbuilding.planetrise.network.PowerGridPackets.ExternalConfigEntry;
import com.rtsbuilding.rtsbuilding.planetrise.network.PowerGridPackets.MemberEntry;
import com.rtsbuilding.rtsbuilding.planetrise.network.PowerGridPackets.S2CPowerGridSnapshot;
import com.rtsbuilding.rtsbuilding.planetrise.power.IPowerGridNode;
import com.rtsbuilding.rtsbuilding.planetrise.power.PowerGridManager;
import com.rtsbuilding.rtsbuilding.planetrise.power.PowerGridOwnership;
import com.rtsbuilding.rtsbuilding.planetrise.power.PowerRole;
import com.rtsbuilding.rtsbuilding.planetrise.server.powergrid.PowerGridExternalConfigStore;
import com.rtsbuilding.rtsbuilding.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 电网多人系统<b>服务端处理器</b>：处理 C2S 刷新/邀请/移除/外部机器配置，构造并回推
 * {@link S2CPowerGridSnapshot} 给相关玩家。
 * <p>
 * 组与成员的变更落在全局 {@link PowerGridOwnership}；设备/发电/耗电统计落在请求者当前维度的
 * {@link PowerGridManager}。
 */
public final class PowerGridServerHandler {

    private PowerGridServerHandler() {
    }

    /** 刷新：把请求者所属电网组最新快照回推给请求者。 */
    public static void handleRefresh(IPayloadContext ctx) {
        ServerPlayer player = (ctx.player() instanceof ServerPlayer sp) ? sp : null;
        if (player != null) {
            pushSnapshot(player, buildSnapshot(player));
        }
    }

    /** 邀请：操作者把某玩家加入其电网组（加入要求目标在线，参照 Flux changeMembership）。 */
    public static void handleInvite(C2SPowerGridInvite payload, IPayloadContext ctx) {
        ServerPlayer player = (ctx.player() instanceof ServerPlayer sp) ? sp : null;
        if (player == null) {
            return;
        }
        // 先尝试解析为 UUID，不能解析则按在线玩家名查找。
        UUID target = parseUuid(payload.inviteeUuid());
        if (target == null) {
            ServerPlayer byName = player.server.getPlayerList().getPlayerByName(payload.inviteeUuid());
            target = byName == null ? null : byName.getUUID();
        }
        if (target == null) {
            sendResult(player, PowerGridOwnership.RESULT_INVALID);
            return;
        }
        boolean online = player.server.getPlayerList().getPlayer(target) != null;
        byte code = PowerGridOwnership.get().changeMembership(player.getUUID(), target,
                PowerGridOwnership.ACTION_SET_USER, online);
        if (code == PowerGridOwnership.RESULT_SUCCESS) {
            ServerPlayer mp = player.server.getPlayerList().getPlayer(target);
            if (mp != null) {
                pushSnapshot(mp, buildSnapshot(mp));
            }
        }
        sendResult(player, code);
    }

    /** 移除：把某成员移出所属电网组（权限校验在 changeMembership 内）。 */
    public static void handleRemove(C2SPowerGridRemove payload, IPayloadContext ctx) {
        ServerPlayer player = (ctx.player() instanceof ServerPlayer sp) ? sp : null;
        UUID member = parseUuid(payload.memberUuid());
        if (player == null || member == null) {
            return;
        }
        byte code = PowerGridOwnership.get().changeMembership(player.getUUID(), member,
                PowerGridOwnership.ACTION_CANCEL, true);
        if (code == PowerGridOwnership.RESULT_SUCCESS) {
            pushSnapshot(player, buildSnapshot(player));
            ServerPlayer mp = player.server.getPlayerList().getPlayer(member);
            if (mp != null) {
                pushSnapshot(mp, buildSnapshot(mp));
            }
        }
        sendResult(player, code);
    }

    /** 成员等级操作：升/降级（SET_USER/SET_ADMIN）与转移所有权（TRANSFER）。 */
    public static void handleMemberAction(C2SPowerGridMemberAction payload, IPayloadContext ctx) {
        ServerPlayer player = (ctx.player() instanceof ServerPlayer sp) ? sp : null;
        UUID target = parseUuid(payload.targetUuid());
        if (player == null || target == null) {
            return;
        }
        boolean online = player.server.getPlayerList().getPlayer(target) != null;
        byte code = PowerGridOwnership.get().changeMembership(player.getUUID(), target,
                payload.action(), online);
        if (code == PowerGridOwnership.RESULT_SUCCESS) {
            pushSnapshot(player, buildSnapshot(player));
            ServerPlayer mp = player.server.getPlayerList().getPlayer(target);
            if (mp != null) {
                pushSnapshot(mp, buildSnapshot(mp));
            }
        }
        sendResult(player, code);
    }

    /** 把成员操作结果回传给请求者（UI 显示原因）。 */
    private static void sendResult(ServerPlayer player, byte code) {
        Platform.sendPacket(player, new PowerGridPackets.S2CPowerGridActionResult(code));
    }

    /** 外部机器类型配置：写入全局存储并持久化。 */
    public static void handleExternalConfig(C2SPowerGridExternalConfig payload, IPayloadContext ctx) {
        RtsMachineType type;
        try {
            type = RtsMachineType.valueOf(payload.machineType());
        } catch (Exception e) {
            type = RtsMachineType.GENERATOR;
        }
        if (payload.clear()) {
            PowerGridExternalConfigStore.clear(payload.machineId());
        } else {
            PowerGridExternalConfigStore.set(new ExternalMachineConfig(
                    payload.machineId(), type, payload.powerValue(), payload.linkRange(), 0));
        }
    }

    /** 构造请求者所属电网组的快照（数据来自全局所有权 + 当前维度管理器）。 */
    private static PowerGridSnapshot buildSnapshot(ServerPlayer requester) {
        PowerGridOwnership ownership = PowerGridOwnership.get();
        UUID playerId = requester.getUUID();
        UUID owner = ownership.ownerOf(playerId);
        ownership.ensureGrid(owner);

        String ownerName = displayName(requester.server.getPlayerList(), owner);

        List<GridMember> members = new ArrayList<>();
        List<UUID> groupIds = new ArrayList<>(ownership.members(owner).keySet());
        for (UUID m : groupIds) {
            boolean online = requester.server.getPlayerList().getPlayer(m) != null;
            members.add(new GridMember(m, displayName(requester.server.getPlayerList(), m),
                    online, ownership.accessOf(m)));
        }
        // 参照 Flux-Networks（writeCustomTag NBT_NET_MEMBERS）：把「在线但未加入」的玩家以
        // BLOCKED（陌生人）追加进快照，成员 Tab 据此展示可直接点击邀请的候选人。
        for (ServerPlayer p : requester.server.getPlayerList().getPlayers()) {
            if (!groupIds.contains(p.getUUID())) {
                members.add(new GridMember(p.getUUID(), p.getGameProfile().getName(),
                        true, RtsAccessLevel.BLOCKED));
            }
        }

        ServerLevel level = requester.serverLevel();
        PowerGridManager manager = PowerGridManager.get(level);
        List<PowerDevice> devices = new ArrayList<>();
        collectDevices(manager, owner, level, devices);
        long[] agg = manager.aggregate(owner);

        return new PowerGridSnapshot(owner, ownerName, members, devices, agg[0], agg[1]);
    }

    /** 收集组内设备：组网节点（发电/塔）+ 各塔供电范围内用电器。 */
    private static void collectDevices(PowerGridManager manager, UUID groupOwner, ServerLevel level,
                                       List<PowerDevice> out) {
        Map<BlockPos, PowerDevice> consumers = new LinkedHashMap<>();
        for (IPowerGridNode node : manager.nodesOf(groupOwner)) {
            BlockPos pos = blockPosOf(node);
            if (node.role() == PowerRole.GENERATOR) {
                out.add(new PowerDevice(RtsDeviceRole.GENERATOR, pos.getX(), pos.getY(), pos.getZ(),
                        node.generation() > 0 ? RtsPowerStatus.POWERED : RtsPowerStatus.OFFLINE,
                        node.generation(), labelAt(level, pos), itemIdOf(level, pos)));
            } else if (node instanceof PowerTowerBlockEntity tower) {
                // 传输电量 = 最近一次电网分配的本塔配额（broadcast 用后不清零，避免快照恒显示 0）。
                long quota = tower.lastAssignedQuota();
                // 输电塔不标记「电力不足」，仅区分通电 / 离线（不足由电动机器反映）。
                RtsPowerStatus st = quota > 0 ? RtsPowerStatus.POWERED : RtsPowerStatus.OFFLINE;
                out.add(new PowerDevice(RtsDeviceRole.TOWER, pos.getX(), pos.getY(), pos.getZ(),
                        st, quota, labelAt(level, pos), itemIdOf(level, pos)));
                for (BlockPos p : tower.consumers()) {
                    // 过滤已被移除的用电器坐标（能量能力失效），避免设备列表残留坐标渲染成「空气」。
                    if (!tower.isConsumerAlive(p)) {
                        continue;
                    }
                    if (!consumers.containsKey(p)) {
                        // 耗电 = 该用电器最近一次广播<b>实际注入</b>的电量（真实耗电，而非可注入缺口）。
                        long metric = tower.consumerInjectedRate(p);
                        consumers.put(p, new PowerDevice(RtsDeviceRole.CONSUMER,
                                p.getX(), p.getY(), p.getZ(),
                                metric > 0 ? RtsPowerStatus.POWERED : RtsPowerStatus.OFFLINE,
                                metric, labelAt(level, p), itemIdOf(level, p)));
                    }
                }
            }
        }
        out.addAll(consumers.values());
    }

/** 为某玩家构建其所属电网组快照并推送（实时同步用；组内无节点时无操作）。 */
    public static void pushPlayerSnapshot(ServerPlayer player) {
        PowerGridOwnership ownership = PowerGridOwnership.get();
        UUID owner = ownership.ownerOf(player.getUUID());
        if (PowerGridManager.get(player.serverLevel()).nodesOf(owner).isEmpty()) {
            return;
        }
        pushSnapshot(player, buildSnapshot(player));
    }

    /** 把快照推给一个玩家（转成 S2C 包发送）。 */
    public static void pushSnapshot(ServerPlayer player, PowerGridSnapshot snapshot) {
        List<MemberEntry> members = new ArrayList<>();
        for (GridMember m : snapshot.members()) {
            members.add(new MemberEntry(m.id().toString(), m.name(), m.online(), (byte) m.access().ordinal()));
        }
        List<DeviceEntry> devices = new ArrayList<>();
        for (PowerDevice d : snapshot.devices()) {
            devices.add(new DeviceEntry((byte) d.role().ordinal(), d.x(), d.y(), d.z(),
                    (byte) d.status().ordinal(), d.metric(), d.label(), d.itemId()));
        }
        List<ExternalConfigEntry> configs = new ArrayList<>();
        for (ExternalMachineConfig c : PowerGridExternalConfigStore.all()) {
            configs.add(new ExternalConfigEntry(c.machineId(), c.type().name(),
                    c.powerValue(), c.linkRange(), c.priority()));
        }
        Platform.sendPacket(player, new S2CPowerGridSnapshot(
                snapshot.owner() == null ? "" : snapshot.owner().toString(),
                snapshot.ownerName() == null ? "" : snapshot.ownerName(),
                members, devices, configs, snapshot.totalGeneration(), snapshot.totalDemand()));
    }

    // ---- helpers ----

    private static BlockPos blockPosOf(IPowerGridNode node) {
        return node instanceof BlockEntity be ? be.getBlockPos() : BlockPos.ZERO;
    }

    private static String labelAt(ServerLevel level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return block == null ? "" : block.getDescriptionId();
    }

    /** 该位置方块作为物品的注册 id（{@code mod_id:item}），供 UI 绘制物品图标；无对应物品时返回空串。 */
    private static String itemIdOf(ServerLevel level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
            return "";
        }
        Item item = block.asItem();
        if (item == null || item == Items.AIR) {
            return "";
        }
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static String displayName(net.minecraft.server.players.PlayerList players, UUID uuid) {
        ServerPlayer p = players.getPlayer(uuid);
        if (p != null) {
            return p.getGameProfile().getName();
        }
        String s = uuid.toString();
        return "UUID:" + s.substring(Math.max(0, s.length() - 8));
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }
}

