package com.rtsbuilding.rtsbuilding.client.render;

public final class RingBufferHolder {

    /** 放置动画缓冲：方块从天降落建造特效（由 {@code handlePlaceAnimation} 写入）。 */
    public static final GhostRingBuffer INSTANCE = new GhostRingBuffer(256);

    /** 破坏特效缓冲：方块破坏后向上飘散（由 {@code handleBreakAnimation} 写入）。
     *  容量较大以承接批量破坏（区域破坏每 tick 最多 64 格）的瞬时写入。 */
    public static final GhostRingBuffer BREAK_EFFECTS = new GhostRingBuffer(256);

    private RingBufferHolder() {
    }
}
