package com.rtsbuilding.rtsbuilding.core.network;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 校验 {@link ActionType} 协议 id 的稳定性。
 *
 * <p>ActionType 按显式 id 编解码（见 {@link ActionType#id()} / {@link ActionType#fromId(int)}），
 * 该测试是协议护栏：任何导致 id 前移/重复/丢失的改动都会在此失败，防止新旧端混连错位。
 * 新增枚举值时同步在本测试补充断言（id = 当前最大 id + 1，在枚举末尾追加）。
 */
class ActionTypeProtocolTest {

    /** id 必须与历史 ordinal 保持一致（迁移基线，零协议破坏）。 */
    @Test
    void idsMatchHistoricalOrdinal() {
        ActionType[] values = ActionType.values();
        for (int i = 0; i < values.length; i++) {
            assertEquals(i, values[i].id(),
                    "ActionType." + values[i] + " 的 id 应等于历史 ordinal " + i);
        }
    }

    /** id 必须全局唯一（重复会破坏 fromId 反解）。 */
    @Test
    void idsAreUnique() {
        Set<Integer> seen = new HashSet<>();
        for (ActionType t : ActionType.values()) {
            assertEquals(true, seen.add(t.id()), "ActionType." + t + " 的 id " + t.id() + " 重复");
        }
    }

    /** 全部 id 从 0 连续递增（协议编码不应存在空洞，防止未来误占）。 */
    @Test
    void idsAreContiguousFromZero() {
        for (int i = 0; i < ActionType.values().length; i++) {
            assertEquals(i, ActionType.values()[i].id());
        }
    }

    /** fromId 必须完整覆盖每个枚举值。 */
    @Test
    void fromIdCoversAllValues() {
        for (ActionType t : ActionType.values()) {
            assertEquals(t, ActionType.fromId(t.id()),
                    "fromId(" + t.id() + ") 应返回 " + t);
        }
    }

    /** 未知 id 返回 null（decode 越界行为，防恶意包 NPE）。 */
    @Test
    void fromIdUnknownReturnsNull() {
        assertNull(ActionType.fromId(-1));
        assertNull(ActionType.fromId(1000));
        assertNull(ActionType.fromId(42));
    }

    /** 关键动作抽查：常用的动作 id 必须稳定（防止无意识重排）。 */
    @Test
    void criticalIdsStable() {
        assertEquals(ActionType.SET_MODE, ActionType.fromId(0));
        assertEquals(ActionType.TOGGLE_CAMERA, ActionType.fromId(1));
        assertEquals(ActionType.PLACE_BATCH, ActionType.fromId(21));
        assertEquals(ActionType.MINE_BLOCK, ActionType.fromId(25));
        assertEquals(ActionType.ULTIMINE, ActionType.fromId(28));
        assertEquals(ActionType.PATHFIND, ActionType.fromId(19));
        assertEquals(ActionType.SET_FUNNEL_RADIUS, ActionType.fromId(41));
        assertNotNull(ActionType.fromId(41));
    }

    /** 保留占位符必须带 @Deprecated（协议约束：禁止删除导致 id 前移）。 */
    @Test
    void deprecatedPlaceholdersPresent() throws NoSuchFieldException {
        var deprecated = Deprecated.class;
        assertEquals(true, ActionType.CRAFT_RECIPE.getClass().getField("CRAFT_RECIPE").isAnnotationPresent(deprecated));
        assertEquals(true, ActionType.REQUEST_CRAFTABLES.getClass().getField("REQUEST_CRAFTABLES").isAnnotationPresent(deprecated));
        assertEquals(true, ActionType.OPEN_CRAFT_TERMINAL.getClass().getField("OPEN_CRAFT_TERMINAL").isAnnotationPresent(deprecated));
        assertEquals(true, ActionType.REQUEST_PLUGINS.getClass().getField("REQUEST_PLUGINS").isAnnotationPresent(deprecated));
    }
}
