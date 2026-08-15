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
 * <p>服务端动画包（{@code S2CRtsPlaceAnimationPayload} / {@code S2CRtsBreakAnimationPayload}）
 * 到达时并不直接播放，而是把该位置登记为"预期变化"；动画的启动时刻始终锚定到
 * <b>客户端实际看到该位置状态变化的那一刻</b>（BlockUpdate 驱动
 * {@code ClientPacketListenerMixin#handleBlockUpdate} 调用 {@link #onBlockChanged}），
 * 线框下落/碎块上飘与方块实况严格同帧对齐，不依赖动画包到达时刻、也不做 seq 错峰
 * （同 tick 批量操作中方块是同时变化的，动画应同时启动，逐格延迟反而造成"动画滞后于
 * 方块"的错位）。</p>
 *
 * <p><b>顺序容错：</b>
 * <ul>
 *   <li>BlockUpdate 先于动画包到达（常态）：登记时检查当前世界状态，已就绪立即播放；</li>
 *   <li>动画包先到：登记入集合，等待后续 {@link #onBlockChanged} 命中触发；</li>
 *   <li>状态一直未到（BlockUpdate 丢失等极端情况）：{@link #tick} 超时兜底延迟播放，保证不丢失。</li>
 * </ul>
 * 所有播放路径均以触发时刻（{@link System#currentTimeMillis()}）作为动画启动时间，
 * 动画不滞后于方块。</p>
 */
public final class RtsEffectStateTracker {

    /** 等待状态就绪的放置动画：posKey → 登记时刻（仅需位置，颜色用状态就绪时的 cur）。 */
    private static final Map<Long, Long> PENDING_PLACE = new HashMap<>();

    /** 等待状态就绪的破坏动画：posKey → (登记时刻, 破坏前状态)。 */
    private static final Map<Long, PendingBreak> PENDING_BREAK = new HashMap<>();

    /** 状态变化等待超时（毫秒）：超过仍未等到对应状态变化则延迟播放兜底。 */
    private static final long PENDING_TIMEOUT_MS = 2000L;

    private record PendingBreak(long registeredMs, BlockState state) {
    }

    private RtsEffectStateTracker() {
    }

    /**
     * 登记一次放置动画。若目标位置当前状态已等于期望的放置后状态 {@code targetState}
     * （BlockUpdate 先到，通常为交互式放置路径），立即播放；否则入等待集合，
     * 由 {@link #onBlockChanged} 捕获到该位置变为目标方块时播放。
     * 用精确状态匹配而非「非空气」，避免替换模式（原位置已有旧方块）下误判为已就绪而提前播放。
     */
    public static void registerPlace(BlockPos pos, BlockState targetState) {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            BlockState cur = level.getBlockState(pos);
            if (cur.equals(targetState)) {
                RingBufferHolder.INSTANCE.schedule(pos, cur, System.currentTimeMillis());
                return;
            }
        }
        PENDING_PLACE.put(pos.asLong(), System.currentTimeMillis());
    }

    /**
     * 登记一次破坏动画。若目标位置客户端状态已就绪（已变为空气，BlockUpdate 先到），立即播放；
     * 否则等待 {@link #onBlockChanged} 捕获到该位置变为空气时播放。
     * 碎块颜色取破坏前的 {@code breakBeforeState}。
     */
    public static void registerBreak(BlockPos pos, BlockState breakBeforeState) {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            BlockState cur = level.getBlockState(pos);
            if (cur.isAir()) {
                RingBufferHolder.BREAK_EFFECTS.schedule(pos, breakBeforeState, System.currentTimeMillis());
                return;
            }
        }
        PENDING_BREAK.put(pos.asLong(), new PendingBreak(System.currentTimeMillis(), breakBeforeState));
    }

    /**
     * 由 {@code ClientPacketListenerMixin} 在 {@code ClientPacketListener.handleBlockUpdate} 时调用，
     * 检查本次状态变化是否命中登记的预期动画，命中则立即播放（启动时刻 = 状态变化时刻）。
     *
     * @param oldState 变化前的方块状态
     * @param newState 变化后的方块状态
     */
    public static void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
        if (oldState.equals(newState)) {
            return;
        }
        long key = pos.asLong();
        long nowMs = System.currentTimeMillis();
        if (!newState.isAir()) {
            // 放置方向：变为非空气方块
            if (PENDING_PLACE.remove(key) != null) {
                RingBufferHolder.INSTANCE.schedule(pos, newState, nowMs);
            }
        } else if (!oldState.isAir()) {
            // 破坏方向：变为空气
            PendingBreak p = PENDING_BREAK.remove(key);
            if (p != null) {
                RingBufferHolder.BREAK_EFFECTS.schedule(pos, p.state(), nowMs);
            }
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
        long nowMs = System.currentTimeMillis();
        tickPlace(level, nowMs);
        tickBreak(level, nowMs);
    }

    private static void tickPlace(Level level, long nowMs) {
        Iterator<Map.Entry<Long, Long>> it = PENDING_PLACE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Long> e = it.next();
            BlockPos pos = BlockPos.of(e.getKey());
            BlockState cur = level.getBlockState(pos);
            if (!cur.isAir()) {
                it.remove();
                RingBufferHolder.INSTANCE.schedule(pos, cur, nowMs);
            } else if (nowMs - e.getValue() > PENDING_TIMEOUT_MS) {
                it.remove();
                if (!cur.isAir()) {
                    RingBufferHolder.INSTANCE.schedule(pos, cur, nowMs);
                }
            }
        }
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
                RingBufferHolder.BREAK_EFFECTS.schedule(pos, p.state(), nowMs);
            } else if (nowMs - p.registeredMs() > PENDING_TIMEOUT_MS) {
                it.remove();
                RingBufferHolder.BREAK_EFFECTS.schedule(pos, p.state(), nowMs);
            }
        }
    }

    public static void clear() {
        PENDING_PLACE.clear();
        PENDING_BREAK.clear();
    }
}
