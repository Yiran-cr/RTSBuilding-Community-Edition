package com.rtsbuilding.rtsbuilding.core.network;

/**
 * 客户端 → 服务端动作类型。
 *
 * <p><b>协议编码约定</b>：本枚举按显式 {@code id} 编解码（见 {@link #id()} 与 {@link #fromId(int)}），
 * 不再依赖 ordinal。新增类型时：
 * <ul>
 *   <li>在枚举<strong>末尾</strong>追加，分配 <code>id = 当前最大 id + 1</code>；</li>
 *   <li>不要删除已有值——如需停用，保留并标记 {@code @Deprecated}（占位保护序号，防止新旧端混连协议错位）；</li>
 *   <li>修改后运行 <code>ActionTypeProtocolTest</code> 校验 id 唯一性与映射完整性。</li>
 * </ul>
 */
public enum ActionType {
    SET_MODE(0),
    TOGGLE_CAMERA(1),
    SET_AUTO_STORE(2),
    SET_BD_NETWORK(3),
    LINK_STORAGE(4),
    UNLINK_STORAGE(5),
    UPDATE_LINKED_STORAGE(6),
    LINKED_QUICK_MOVE(7),
    LINKED_PICKUP(8),
    IMPORT_MENU_SLOT(9),
    FILL_INVENTORY(10),
    CLOSE_REMOTE_MENU(11),
    STORE_HOTBAR_SLOT(12),
    RETURN_CARRIED(13),
    REQUEST_PAGE(14),
    /**
     * @deprecated 合成终端链路已整体移除（客户端网关与服务端分支均删除）。
     * 保留占位：C2SAction 按 id 编解码，删除该值会使后续类型序号前移，
     * 导致新旧端混连时协议错位。
     */
    @Deprecated
    CRAFT_RECIPE(15),
    /**
     * @deprecated 合成终端链路已整体移除。保留占位（同 {@link #CRAFT_RECIPE}）。
     */
    @Deprecated
    REQUEST_CRAFTABLES(16),
    /**
     * @deprecated 合成终端链路已整体移除。保留占位（同 {@link #CRAFT_RECIPE}）。
     */
    @Deprecated
    OPEN_CRAFT_TERMINAL(17),
    /**
     * @deprecated 客户端插件请求逻辑已删除（网关 sendRequestPlugins / 模块 requestPlugins 均移除）。
     * 保留占位：C2SAction 按 id 编解码，删除该值会使后续类型序号前移，
     * 导致新旧端混连时协议错位（如旧端 id 25 被解析为 PATHFIND）。
     */
    @Deprecated
    REQUEST_PLUGINS(18),
    PATHFIND(19),
    PLACE_BLOCK(20),
    PLACE_BATCH(21),
    PLACE_FLUID(22),
    ROTATE_BLOCK(23),
    SUBMIT_PENDING(24),
    MINE_BLOCK(25),
    AREA_MINE(26),
    AREA_DESTROY(27),
    ULTIMINE(28),
    BREAK(29),
    INTERACT_BLOCK(30),
    STORE_FLUID(31),
    QUICK_DROP(32),
    UNDO(33),
    PAUSE_WORKFLOW(34),
    DELETE_WORKFLOW(35),
    CAMERA_POSE(36),
    FUNNEL_PICKUP(37),
    FUNNEL_BOX_PICKUP(38),
    /**
     * 客户端在切换“物品拾取（漏斗）”按钮时同步开启/关闭状态到服务端，
     * 服务端据此允许/拒绝漏斗请求（客户端在服务端开启前不会真正吸物）。
     */
    SET_FUNNEL(39),
    /**
     * 客户端从“最近使用”栏删除一条条目时通知服务端，服务端从会话的
     * recentEntries 中真正移除，避免条目在重进/重启后复活。
     * 仅在枚举末尾追加（id 编码协议，禁止插入到已有值之前）。
     */
    REMOVE_RECENT_ENTRY(40),
    /**
     * 客户端右面板下嵌层调节器把漏斗（物品拾取）吸取半径（格）同步到服务端，
     * 服务端按玩家保存半径供球心吸取任务使用。
     * 仅在枚举末尾追加（id 编码协议，禁止插入到已有值之前）。
     */
    SET_FUNNEL_RADIUS(41);

    private final int id;

    ActionType(int id) {
        this.id = id;
    }

    /** 协议编码值。稳定：与历史 ordinal 一致，不受后续插入/删除影响。 */
    public int id() {
        return id;
    }

    /**
     * 按协议 id 反解动作类型。
     *
     * @param id 协议编码值
     * @return 对应动作；未知 id 返回 {@code null}（与 decode 越界行为一致，防恶意包 NPE）
     */
    public static ActionType fromId(int id) {
        return switch (id) {
            case 0 -> SET_MODE;
            case 1 -> TOGGLE_CAMERA;
            case 2 -> SET_AUTO_STORE;
            case 3 -> SET_BD_NETWORK;
            case 4 -> LINK_STORAGE;
            case 5 -> UNLINK_STORAGE;
            case 6 -> UPDATE_LINKED_STORAGE;
            case 7 -> LINKED_QUICK_MOVE;
            case 8 -> LINKED_PICKUP;
            case 9 -> IMPORT_MENU_SLOT;
            case 10 -> FILL_INVENTORY;
            case 11 -> CLOSE_REMOTE_MENU;
            case 12 -> STORE_HOTBAR_SLOT;
            case 13 -> RETURN_CARRIED;
            case 14 -> REQUEST_PAGE;
            case 15 -> CRAFT_RECIPE;
            case 16 -> REQUEST_CRAFTABLES;
            case 17 -> OPEN_CRAFT_TERMINAL;
            case 18 -> REQUEST_PLUGINS;
            case 19 -> PATHFIND;
            case 20 -> PLACE_BLOCK;
            case 21 -> PLACE_BATCH;
            case 22 -> PLACE_FLUID;
            case 23 -> ROTATE_BLOCK;
            case 24 -> SUBMIT_PENDING;
            case 25 -> MINE_BLOCK;
            case 26 -> AREA_MINE;
            case 27 -> AREA_DESTROY;
            case 28 -> ULTIMINE;
            case 29 -> BREAK;
            case 30 -> INTERACT_BLOCK;
            case 31 -> STORE_FLUID;
            case 32 -> QUICK_DROP;
            case 33 -> UNDO;
            case 34 -> PAUSE_WORKFLOW;
            case 35 -> DELETE_WORKFLOW;
            case 36 -> CAMERA_POSE;
            case 37 -> FUNNEL_PICKUP;
            case 38 -> FUNNEL_BOX_PICKUP;
            case 39 -> SET_FUNNEL;
            case 40 -> REMOVE_RECENT_ENTRY;
            case 41 -> SET_FUNNEL_RADIUS;
            default -> null;
        };
    }
}
