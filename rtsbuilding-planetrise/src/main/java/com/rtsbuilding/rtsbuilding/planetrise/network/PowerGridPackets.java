package com.rtsbuilding.rtsbuilding.planetrise.network;

import com.rtsbuilding.rtsbuilding.planetrise.EnergyMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

/**
 * 电网多人系统网络包（C2S / S2C）。
 * <p>
 * C2S：刷新电网信息、邀请/移除成员、外部机器类型配置；S2C：把电网组快照推回客户端供 UI 展示。
 * 所有包均为轻量 record，用 {@link FriendlyByteBuf} 手写编解码。
 */
public final class PowerGridPackets {

    private PowerGridPackets() {
    }

    /** S2C 网格组成员条目：id、名称、在线、访问等级(0=OWNER,1=ADMIN,2=USER)。 */
    public record MemberEntry(String id, String name, boolean online, byte access) {
    }

    /** S2C 网格设备条目：role(0=GEN,1=TOWER,2=CONSUMER)、坐标、status(0..3)、量值、显示名(翻译键)、物品id。 */
    public record DeviceEntry(byte role, long x, long y, long z, byte status, long metric, String label,
                              String itemId) {
    }

    /** S2C 外部机器配置条目。 */
    public record ExternalConfigEntry(String machineId, String type, double powerValue,
                                      double linkRange, int priority) {
    }

    // ---- C2S ----

    /** 请求刷新当前电网信息（服务端回推 S2C 快照）。 */
    public record C2SPowerGridRefresh() implements CustomPacketPayload {
        public static final Type<C2SPowerGridRefresh> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(EnergyMod.MODID, "c2s_powergrid_refresh"));
        public static final StreamCodec<FriendlyByteBuf, C2SPowerGridRefresh> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                }, buf -> new C2SPowerGridRefresh());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 邀请指定玩家加入当前电网组。 */
    public record C2SPowerGridInvite(String inviteeUuid) implements CustomPacketPayload {
        public static final Type<C2SPowerGridInvite> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(EnergyMod.MODID, "c2s_powergrid_invite"));
        public static final StreamCodec<FriendlyByteBuf, C2SPowerGridInvite> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.inviteeUuid()),
                buf -> new C2SPowerGridInvite(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 从当前电网组移除成员。 */
    public record C2SPowerGridRemove(String memberUuid) implements CustomPacketPayload {
        public static final Type<C2SPowerGridRemove> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(EnergyMod.MODID, "c2s_powergrid_remove"));
        public static final StreamCodec<FriendlyByteBuf, C2SPowerGridRemove> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.memberUuid()),
                buf -> new C2SPowerGridRemove(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 设置外部机器类型（machineType: GENERATOR|CONSUMER；clear=true 时清除配置）。 */
    public record C2SPowerGridExternalConfig(String machineId, String machineType, double powerValue,
                                             double linkRange, boolean clear) implements CustomPacketPayload {
        public static final Type<C2SPowerGridExternalConfig> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(EnergyMod.MODID, "c2s_powergrid_external_config"));
        public static final StreamCodec<FriendlyByteBuf, C2SPowerGridExternalConfig> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.machineId());
                    buf.writeUtf(p.machineType());
                    buf.writeDouble(p.powerValue());
                    buf.writeDouble(p.linkRange());
                    buf.writeBoolean(p.clear());
                },
                buf -> new C2SPowerGridExternalConfig(buf.readUtf(), buf.readUtf(), buf.readDouble(),
                        buf.readDouble(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 成员等级操作：action(1=SET_USER,2=SET_ADMIN,3=CANCEL,4=TRANSFER)，targetUuid。 */
    public record C2SPowerGridMemberAction(byte action, String targetUuid) implements CustomPacketPayload {
        public static final Type<C2SPowerGridMemberAction> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(EnergyMod.MODID, "c2s_powergrid_member_action"));
        public static final StreamCodec<FriendlyByteBuf, C2SPowerGridMemberAction> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.action());
                    buf.writeUtf(p.targetUuid());
                },
                buf -> new C2SPowerGridMemberAction(buf.readByte(), buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- S2C ----

    /** 成员操作结果回传：code(0=SUCCESS,1=NO_ADMIN,2=NO_OWNER,3=INVALID)。 */
    public record S2CPowerGridActionResult(byte code) implements CustomPacketPayload {
        public static final Type<S2CPowerGridActionResult> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(EnergyMod.MODID, "s2c_powergrid_action_result"));
        public static final StreamCodec<FriendlyByteBuf, S2CPowerGridActionResult> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeByte(p.code()),
                buf -> new S2CPowerGridActionResult(buf.readByte()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 电网组快照推回客户端。owner / ownerName 可能是空字符串（未加入组）。 */
    public record S2CPowerGridSnapshot(String owner, String ownerName, List<MemberEntry> members,
                                       List<DeviceEntry> devices, List<ExternalConfigEntry> externalConfigs,
                                       long totalGeneration, long totalDemand)
            implements CustomPacketPayload {
        public static final Type<S2CPowerGridSnapshot> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(EnergyMod.MODID, "s2c_powergrid_snapshot"));
        public static final StreamCodec<FriendlyByteBuf, S2CPowerGridSnapshot> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.owner());
                    buf.writeUtf(p.ownerName());
                    buf.writeVarInt(p.members().size());
                    for (MemberEntry m : p.members()) {
                        buf.writeUtf(m.id());
                        buf.writeUtf(m.name());
                        buf.writeBoolean(m.online());
                        buf.writeByte(m.access());
                    }
                    buf.writeVarInt(p.devices().size());
                    for (DeviceEntry d : p.devices()) {
                        buf.writeByte(d.role());
                        buf.writeLong(d.x());
                        buf.writeLong(d.y());
                        buf.writeLong(d.z());
                        buf.writeByte(d.status());
                        buf.writeLong(d.metric());
                        buf.writeUtf(d.label());
                        buf.writeUtf(d.itemId());
                    }
                    buf.writeVarInt(p.externalConfigs().size());
                    for (ExternalConfigEntry e : p.externalConfigs()) {
                        buf.writeUtf(e.machineId());
                        buf.writeUtf(e.type());
                        buf.writeDouble(e.powerValue());
                        buf.writeDouble(e.linkRange());
                        buf.writeVarInt(e.priority());
                    }
                    buf.writeLong(p.totalGeneration());
                    buf.writeLong(p.totalDemand());
                },
                buf -> {
                    String owner = buf.readUtf();
                    String ownerName = buf.readUtf();
                    int mc = buf.readVarInt();
                    List<MemberEntry> members = new ArrayList<>(mc);
                    for (int i = 0; i < mc; i++) {
                        members.add(new MemberEntry(buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readByte()));
                    }
                    int dc = buf.readVarInt();
                    List<DeviceEntry> devices = new ArrayList<>(dc);
                    for (int i = 0; i < dc; i++) {
                        devices.add(new DeviceEntry(buf.readByte(), buf.readLong(), buf.readLong(),
                                buf.readLong(), buf.readByte(), buf.readLong(), buf.readUtf(), buf.readUtf()));
                    }
                    int ec = buf.readVarInt();
                    List<ExternalConfigEntry> externalConfigs = new ArrayList<>(ec);
                    for (int i = 0; i < ec; i++) {
                        externalConfigs.add(new ExternalConfigEntry(buf.readUtf(), buf.readUtf(),
                                buf.readDouble(), buf.readDouble(), buf.readVarInt()));
                    }
                    return new S2CPowerGridSnapshot(owner, ownerName, members, devices, externalConfigs,
                            buf.readLong(), buf.readLong());
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 注册全部电网多人网络包。 */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(C2SPowerGridRefresh.TYPE, C2SPowerGridRefresh.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> PowerGridServerHandler.handleRefresh(ctx)));
        registrar.playToServer(C2SPowerGridInvite.TYPE, C2SPowerGridInvite.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> PowerGridServerHandler.handleInvite(p, ctx)));
        registrar.playToServer(C2SPowerGridRemove.TYPE, C2SPowerGridRemove.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> PowerGridServerHandler.handleRemove(p, ctx)));
        registrar.playToServer(C2SPowerGridExternalConfig.TYPE, C2SPowerGridExternalConfig.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> PowerGridServerHandler.handleExternalConfig(p, ctx)));
        registrar.playToServer(C2SPowerGridMemberAction.TYPE, C2SPowerGridMemberAction.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> PowerGridServerHandler.handleMemberAction(p, ctx)));
        registrar.playToClient(S2CPowerGridSnapshot.TYPE, S2CPowerGridSnapshot.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> PowerGridClientHandler.handleSnapshot(p)));
        registrar.playToClient(S2CPowerGridActionResult.TYPE, S2CPowerGridActionResult.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> PowerGridClientHandler.handleActionResult(p)));
    }
}
