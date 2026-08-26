package com.rtsbuilding.rtsbuilding.planetrise.power;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsAccessLevel;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 服务端<b>全局电网所有权</b>——电网组（owner + 成员及其访问等级）与「玩家→所属组」映射。
 * <p>
 * 参照 Flux-Networks 的成员模型：电网必须且只有一个所有者（OWNER）；成员分 OWNER/ADMIN/USER 三级，
 * 支持邀请（加入）、升降级、移除与转移所有权；<b>邀请/转移只对在线玩家生效</b>。组与成员
 * 持久化到 {@code config/rts_building/powergrid_groups.json}，服务器重启后保留。
 * <p>
 * 实际电力调度仍由各维度 {@link PowerGridManager} 执行，它通过 {@link #ownerOf(UUID)} 把节点
 * 归类到其放置者玩家所属的电网组。服务端单线程访问，无需并发容器。
 */
public final class PowerGridOwnership {

    // 操作类型（参照 Flux-MEMBERSHIP_*）
    /** 把目标设为普通成员（非成员则加入；已是成员则降级为 USER）。 */
    public static final byte ACTION_SET_USER = 1;
    /** 把已是成员的�Target 升为 ADMIN。 */
    public static final byte ACTION_SET_ADMIN = 2;
    /** 移除成员。 */
    public static final byte ACTION_CANCEL = 3;
    /** 转移所有权给目标（目标须在线，旧主降级为 USER，所有权变更）。 */
    public static final byte ACTION_TRANSFER = 4;

    // 结果码
    public static final byte RESULT_SUCCESS = 0;
    public static final byte RESULT_NO_ADMIN = 1;
    public static final byte RESULT_NO_OWNER = 2;
    public static final byte RESULT_INVALID = 3;

    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("rts_building/powergrid_groups.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final PowerGridOwnership INSTANCE = new PowerGridOwnership();

    /** 电网组：所有者 → 成员访问等级映射（含所有者自身=OWNER）。 */
    private static final Map<UUID, RtsAccessLevel> gridMembers = new LinkedHashMap<>();
    /** 玩家当前所属电网组所有者：默认 = 玩家自身（自己的电网）。 */
    private static final Map<UUID, UUID> playerGrid = new LinkedHashMap<>();

    static {
        load();
    }

    private PowerGridOwnership() {
    }

    /** 获取服务端全局电网所有权实例。 */
    public static PowerGridOwnership get() {
        return INSTANCE;
    }

    /** 电网组所有者列表。 */
    public Set<UUID> owners() {
        return Collections.unmodifiableSet(gridMembers.keySet());
    }

    /** 某电网组的成员与访问等级（只读，按归属过滤）；组不存在时返回空。 */
    public Map<UUID, RtsAccessLevel> members(UUID owner) {
        if (owner == null) {
            return Collections.emptyMap();
        }
        Map<UUID, RtsAccessLevel> res = new LinkedHashMap<>();
        for (Map.Entry<UUID, RtsAccessLevel> e : gridMembers.entrySet()) {
            UUID m = e.getKey();
            if (owner.equals(playerGrid.getOrDefault(m, m))) {
                res.put(m, e.getValue());
            }
        }
        return Collections.unmodifiableMap(res);
    }

    /** 某电网组所有人的访问等级；非成员返回 null。 */
    @Nullable
    public RtsAccessLevel accessOf(UUID player) {
        UUID owner = ownerOf(player);
        return owner == null ? null : gridMembers.get(player);
    }

    /** 某玩家当前所属电网组所有者（默认 = 自身）。 */
    public UUID ownerOf(UUID player) {
        if (player == null) {
            return null;
        }
        return playerGrid.getOrDefault(player, player);
    }

    /** 确保某所有者的电网存在（惰性创建，成员初始仅所有者自身=OWNER）。 */
    public void ensureGrid(UUID owner) {
        if (owner == null) {
            return;
        }
        if (!gridMembers.containsKey(owner)) {
            gridMembers.put(owner, RtsAccessLevel.OWNER);
            playerGrid.putIfAbsent(owner, owner);
            save();
        }
    }

    /**
     * 成员操作统一入口（参照 Flux-Networks changeMembership）。
     *
     * @param actor            操作者玩家。
     * @param target           目标玩家（可能是未加入者）。
     * @param action           操作类型（ACTION_*）。
     * @param targetHasAvailable 目标是否满足"在线可加入"条件（加入/转移要求在线）。
     * @return 结果码（RESULT_*）。
     */
    public byte changeMembership(UUID actor, @Nullable UUID target, byte action, boolean targetHasAvailable) {
        if (actor == null) {
            return RESULT_INVALID;
        }
        ensureGrid(actor);
        RtsAccessLevel access = gridMembers.get(actor);
        if (access == null) {
            access = RtsAccessLevel.USER;
        }

        if (action == ACTION_TRANSFER) {
            if (!access.canTransfer()) {
                return RESULT_NO_OWNER;
            }
            if (target == null || !targetHasAvailable) {
                return RESULT_INVALID;
            }
            if (target.equals(actor)) {
                return RESULT_INVALID;   // 不能把所有权转给自己
            }
            RtsAccessLevel current = gridMembers.get(target);
            if (current == null) {
                return RESULT_INVALID;   // 目标须是成员
            }
            // 旧主降为 USER，target 升为 OWNER；其余成员等级不变。
            gridMembers.put(actor, RtsAccessLevel.USER);
            gridMembers.put(target, RtsAccessLevel.OWNER);
            // 更新玩家所属映射：actor 与 target 都归 target 的组。
            playerGrid.put(actor, target);
            playerGrid.put(target, target);
            save();
            return RESULT_SUCCESS;
        }

        if (!access.canEdit()) {
            return RESULT_NO_ADMIN;
        }
        if (target == null) {
            return RESULT_INVALID;
        }
        RtsAccessLevel current = gridMembers.get(target);
        switch (action) {
            case ACTION_SET_ADMIN -> {
                if (!access.canTransfer()) {
                    return RESULT_NO_OWNER;   // 仅所有者可升 ADMIN
                }
                if (current == null || current == RtsAccessLevel.OWNER) {
                    return RESULT_INVALID;
                }
                gridMembers.put(target, RtsAccessLevel.ADMIN);
            }
            case ACTION_SET_USER -> {
                if (current == null) {
                    if (!targetHasAvailable) {
                        return RESULT_INVALID;   // 加入须在线
                    }
                    gridMembers.put(target, RtsAccessLevel.USER);
                    playerGrid.put(target, ownerOf(actor));
                } else if (current == RtsAccessLevel.OWNER && !access.canTransfer()) {
                    return RESULT_NO_OWNER;     // 不能降级所有者（除非所有者同意转移）
                } else {
                    gridMembers.put(target, RtsAccessLevel.USER);
                }
            }
            case ACTION_CANCEL -> {
                if (current == null) {
                    return RESULT_INVALID;
                }
                if (current == RtsAccessLevel.OWNER) {
                    return RESULT_NO_OWNER;     // 不能移除所有者
                }
                gridMembers.remove(target);
                if (target.equals(playerGrid.get(target))) {
                    playerGrid.remove(target);  // 回退为自有组
                }
            }
            default -> {
                return RESULT_INVALID;
            }
        }
        save();
        return RESULT_SUCCESS;
    }

    /** 移除（不需要玩家映射回退的幂等辅助）：保留给外部使用。 */
    public boolean removeMember(UUID owner, UUID member) {
        if (owner == null || member == null) {
            return false;
        }
        RtsAccessLevel lvl = gridMembers.get(member);
        if (lvl == null || lvl == RtsAccessLevel.OWNER) {
            return false;
        }
        gridMembers.remove(member);
        save();
        return true;
    }

    // ---- 持久化 ----

    private static void save() {
        Map<String, Map<String, String>> data = new LinkedHashMap<>();
        for (Map.Entry<UUID, RtsAccessLevel> e : gridMembers.entrySet()) {
            // 只有组的所有者作为顶层 key；组内所有成员及其等级写入该组。
            UUID member = e.getKey();
            UUID owner = playerGrid.getOrDefault(member, member);
            String ownerStr = owner.toString();
            data.computeIfAbsent(ownerStr, k -> new LinkedHashMap<>())
                    .put(member.toString(), e.getValue().name());
        }
        Path tmp = PATH.resolveSibling("powergrid_groups.json.tmp");
        try {
            Files.createDirectories(PATH.getParent());
            try (var writer = Files.newBufferedWriter(tmp)) {
                GSON.toJson(data, writer);
            }
            Files.move(tmp, PATH, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
        // playerGrid 由 data 隐含（每成员归属其 owner），读取时重建。
    }

    private static void load() {
        if (!Files.exists(PATH)) {
            return;
        }
        try (var reader = Files.newBufferedReader(PATH)) {
            Map<String, Map<String, String>> data = GSON.fromJson(reader,
                    new TypeToken<Map<String, Map<String, String>>>() {
                    }.getType());
            if (data == null) {
                return;
            }
            for (Map.Entry<String, Map<String, String>> g : data.entrySet()) {
                UUID owner = parseUuid(g.getKey());
                if (owner == null) {
                    continue;
                }
                gridMembers.put(owner, RtsAccessLevel.OWNER);
                playerGrid.put(owner, owner);
                for (Map.Entry<String, String> m : g.getValue().entrySet()) {
                    UUID mid = parseUuid(m.getKey());
                    RtsAccessLevel lvl = parseLevel(m.getValue());
                    if (mid != null && lvl != null && !mid.equals(owner)) {
                        gridMembers.put(mid, lvl);
                        playerGrid.put(mid, owner);
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static RtsAccessLevel parseLevel(String s) {
        try {
            return RtsAccessLevel.valueOf(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** 电网组快照条目：所有者 + 成员（等级）副本（即时）。 */
    public record GridSnapshot(UUID owner, Map<UUID, RtsAccessLevel> members) {
        public GridSnapshot {
            members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
        }
    }
}
