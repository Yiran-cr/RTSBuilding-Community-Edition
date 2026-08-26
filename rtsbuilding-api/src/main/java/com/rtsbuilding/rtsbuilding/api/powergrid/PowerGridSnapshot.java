package com.rtsbuilding.rtsbuilding.api.powergrid;

import java.util.List;
import java.util.UUID;

/**
 * 电网组快照（客户端展示用，由插件 S2C 推送）。 
 * <p>
 * 包含：所有者、成员（含在线状态）、组内设备（节点 + 用电器）列表，以及总发电 / 总耗电。
 */
public record PowerGridSnapshot(
        UUID owner,
        String ownerName,
        List<GridMember> members,
        List<PowerDevice> devices,
        long totalGeneration,
        long totalDemand) {

    /** 是否包含某成员。 */
    public boolean isMember(UUID playerId) {
        return members.stream().anyMatch(m -> m.id().equals(playerId));
    }
}
