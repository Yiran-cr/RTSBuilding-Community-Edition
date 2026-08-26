package com.rtsbuilding.rtsbuilding.api.powergrid;

import java.util.UUID;

/**
 * 电网组成员：玩家 ID + 名称 + 是否在线 + 访问等级（OWNER/ADMIN/USER）。
 */
public record GridMember(UUID id, String name, boolean online, RtsAccessLevel access) {
}
