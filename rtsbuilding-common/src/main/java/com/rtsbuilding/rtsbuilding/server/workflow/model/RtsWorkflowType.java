package com.rtsbuilding.rtsbuilding.server.workflow.model;

/**
 * Types of workflows that can be tracked by the workflow system.
 *
 * <p>Each enum constant represents a different category of remote operation:
 * single or batch, mine or place. This type is used to identify active workflows in the UI
 * and to determine which progress/reporting format to use.</p>
 *
 * <p><b>协议编码约定</b>：本枚举按显式 {@code id} 跨端编解码（见 {@link #id()} 与 {@link #fromId(int)}），
 * 不依赖 ordinal。新增/删除值时保持 id 稳定——删除改用 {@code @Deprecated} 占位，禁止改变既有 id。
 * 修改后运行 <code>RtsWorkflowTypeProtocolTest</code>。</p>
 */
public enum RtsWorkflowType {

    /** Single block remote mining. */
    MINE_SINGLE(0),

    /** Chain (ultimine) batch mining. */
    ULTIMINE(1),

    /** Area mining operation within a defined 3D volume. */
    AREA_MINE(2),

    /** Shape destruction operation in quick-build preview. */
    AREA_DESTROY(3),

    /** Single block remote placement. */
    PLACE_SINGLE(4),

    /** Multi-block batch placement (interactive position-by-position placement). */
    PLACE_BATCH(5),

    /** Quick build (pre-resolved state) shape placement. */
    QUICK_BUILD(6),

    /** Blueprint file remote placement build. */
    BLUEPRINT_BUILD(7),

    /**
     * Standalone stop mining operation (no new mining will start afterward).
     *
     * <p>Used when the player explicitly cancels a mining operation or disables RTS mode.
     * Unlike the implicit stop inside {@code StopPreviousPipe},
     * this is a user-initiated stop.</p>
     */
    STOP_MINING(8);

    private final int id;

    RtsWorkflowType(int id) {
        this.id = id;
    }

    /** 协议编码值。稳定：与历史 ordinal 一致，不受后续插入/删除影响。 */
    public int id() {
        return id;
    }

    /** 按协议 id 反解；未知 id 返回 {@code null}（与解码越界行为一致，防恶意包）。 */
    public static RtsWorkflowType fromId(int id) {
        return switch (id) {
            case 0 -> MINE_SINGLE;
            case 1 -> ULTIMINE;
            case 2 -> AREA_MINE;
            case 3 -> AREA_DESTROY;
            case 4 -> PLACE_SINGLE;
            case 5 -> PLACE_BATCH;
            case 6 -> QUICK_BUILD;
            case 7 -> BLUEPRINT_BUILD;
            case 8 -> STOP_MINING;
            default -> null;
        };
    }
}
