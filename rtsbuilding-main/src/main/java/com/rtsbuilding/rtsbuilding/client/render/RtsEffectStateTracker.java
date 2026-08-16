package com.rtsbuilding.rtsbuilding.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 放置/破坏动画与客户端方块状态变化的绑定器。
 *
 * <p><b>放置</b>（BuildingGadgets2「动画即落位」语义）：服务端延迟落位，动画包先于方块落位到达，
 * 客户端收到动画包立即播放生长动画，动画结束（服务端落位、BlockUpdate 到达）时方块出现。
 * 不依赖状态变化命中，因此无等待集合。</p>
 *
 * <p><b>破坏</b>：服务端立即移除方块，动画启动时刻锚定到<b>客户端实际看到该位置变为空气的
 * 那一刻</b>（BlockUpdate 驱动 {@code ClientPacketListenerMixin#handleBlockUpdate} 调用
 * {@link #onBlockChanged}），缩小动画与方块实况同帧对齐。</p>
 *
 * <p><b>破坏顺序容错：</b>
 * <ul>
 *   <li>BlockUpdate 先于动画包到达：登记时检查当前世界状态，已就绪立即播放；</li>
 *   <li>动画包先到：登记入等待集合，等待后续 {@link #onBlockChanged} 命中触发；</li>
 *   <li>状态一直未到（BlockUpdate 丢失等极端情况）：{@link #tick} 超时兜底延迟播放，保证不丢失。</li>
 * </ul>
 */
public final class RtsEffectStateTracker {

    /** 等待状态就绪的破坏动画：posKey → (登记时刻, 破坏前状态, 服务端权威动画时长)。 */
    private static final Map<Long, PendingBreak> PENDING_BREAK = new HashMap<>();

    /** 状态变化等待超时（毫秒）：超过仍未等到对应状态变化则延迟播放兜底。 */
    private static final long PENDING_TIMEOUT_MS = 2000L;

    private record PendingBreak(long registeredMs, BlockState state, long durationMs) {
    }

    private RtsEffectStateTracker() {
    }

    /**
     * 登记一次放置动画。
     *
     * <p>BuildingGadgets2 触发/结束语义：服务端在发送动画包后将真实方块落位延迟到动画周期结束，
     * 因此动画包先于方块落位到达客户端。客户端收到动画包<b>立即播放生长动画</b>（不再等待
     * 状态就绪），动画总时长 = 服务端权威 {@code durationMs}，动画结束瞬间（服务端落位、
     * BlockUpdate 到达）方块出现 —— 「生长完成即落位」。</p>
     *
     * <p>若因网络乱序 BlockUpdate 先到（方块已落位），立即播放仍成立（生长动画叠加在已落位
     * 方块上，退化为旧行为，视觉可接受）。</p>
     *
     * @param durationMs 服务端权威动画时长（毫秒），来自动画包 {@code durationTicks * 50}
     */
    public static void registerPlace(BlockPos pos, BlockState targetState, long durationMs) {
        RingBufferHolder.INSTANCE.schedule(pos, targetState, System.currentTimeMillis(), durationMs);
    }

    /**
     * 登记一次破坏动画。若目标位置客户端状态已就绪（已变为空气，BlockUpdate 先到），立即播放；
     * 否则等待 {@link #onBlockChanged} 捕获到该位置变为空气时播放。
     * 动画时长使用服务端权威 {@code durationMs}。
     */
    public static void registerBreak(BlockPos pos, BlockState breakBeforeState, long durationMs) {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            BlockState cur = level.getBlockState(pos);
            if (cur.isAir()) {
                RingBufferHolder.BREAK_EFFECTS.schedule(pos, breakBeforeState, System.currentTimeMillis(), durationMs);
                return;
            }
        }
        PENDING_BREAK.put(pos.asLong(), new PendingBreak(System.currentTimeMillis(), breakBeforeState, durationMs));
    }

    /**
     * 由 {@code ClientPacketListenerMixin} 在 {@code ClientPacketListener.handleBlockUpdate} 时调用，
     * 检查本次状态变化是否命中登记的预期破坏动画，命中则立即播放（启动时刻 = 状态变化时刻）。
     *
     * @param oldState 变化前的方块状态
     * @param newState 变化后的方块状态
     */
    public static void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
        if (oldState.equals(newState)) {
            return;
        }
        if (!newState.isAir() || oldState.isAir()) {
            // 仅破坏方向（非空气 → 空气）会命中登记的破坏动画；放置由动画包直接驱动
            return;
        }
        long key = pos.asLong();
        long nowMs = System.currentTimeMillis();
        PendingBreak p = PENDING_BREAK.remove(key);
        if (p != null) {
            RingBufferHolder.BREAK_EFFECTS.schedule(pos, p.state(), nowMs, p.durationMs());
        }
    }

    /**
     * 每 tick 调用：状态已就绪的登记项立即播放、等待超时的登记项延迟播放兜底，
     * 避免动画因状态同步异常而永久丢失。
     */
    public static void tick() {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            clear();
            return;
        }
        tickBreak(level, System.currentTimeMillis());
    }

    private static void tickBreak(Level level, long nowMs) {
        Iterator<Map.Entry<Long, PendingBreak>> it = PENDING_BREAK.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, PendingBreak> e = it.next();
            BlockPos pos = BlockPos.of(e.getKey());
            PendingBreak p = e.getValue();
            BlockState cur = level.getBlockState(pos);
            if (cur.isAir()) {
                it.remove();
                RingBufferHolder.BREAK_EFFECTS.schedule(pos, p.state(), nowMs, p.durationMs());
            } else if (nowMs - p.registeredMs() > PENDING_TIMEOUT_MS) {
                it.remove();
                RingBufferHolder.BREAK_EFFECTS.schedule(pos, p.state(), nowMs, p.durationMs());
            }
        }
    }

    public static void clear() {
        PENDING_BREAK.clear();
    }
}
