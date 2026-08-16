package com.rtsbuilding.rtsbuilding.client.state;

import net.minecraft.util.Mth;

import java.util.function.DoubleConsumer;

/**
 * 客户端功能调节器状态（右面板下嵌层滑块）。
 *
 * <p>维护可在 RTS 运行中动态调节的功能参数：
 * <ul>
 *   <li>漏斗（物品拾取）吸取范围半径（格）——服务端权威执行球心吸取，变化时同步到服务端。</li>
 *   <li>连锁挖掘（Ultimine）方块数量上限——随启动包发送，纯客户端偏好（预览与服务端一致）。</li>
 * </ul>
 *
 * <p>漏斗半径变化需同步到服务端；本类不依赖具体网络实现，由业务层（主 mod）
 * 在启动时通过 {@link #setFunnelRadiusSync(DoubleConsumer)} 注入同步回调（解耦 UI 与网络）。
 */
public final class FeatureAdjusterState {

    // ==================== 漏斗吸取范围 ====================

    /** 漏斗吸取范围默认半径（格），与服务端 RtsFunnelService 默认一致。 */
    public static final double DEFAULT_FUNNEL_RADIUS = 2.0D;
    public static final double MIN_FUNNEL_RADIUS = 1.0D;
    public static final double MAX_FUNNEL_RADIUS = 5.0D;

    // ==================== 连锁挖掘数量 ====================

    /** 连锁挖掘数量默认上限，与服务端 RtsMiningValidator.ULTIMINE_MAX_BLOCKS 一致。 */
    public static final int DEFAULT_ULTIMINE_LIMIT = 256;
    public static final int MIN_ULTIMINE_LIMIT = 16;
    public static final int MAX_ULTIMINE_LIMIT = 256;
    /** 滑块步进：1 个 1 个地增加/减少。 */
    public static final int ULTIMINE_LIMIT_STEP = 1;

    /** 漏斗半径变化同步回调（由业务层注入，如发送 C2S 包）；未注入时为 no-op。 */
    private static volatile DoubleConsumer funnelRadiusSync = r -> {};

    private static double funnelRadius = DEFAULT_FUNNEL_RADIUS;
    private static int ultimineLimit = DEFAULT_ULTIMINE_LIMIT;

    private FeatureAdjusterState() {
    }

    /**
     * 注入漏斗半径同步回调（业务层调用；UI mod 自身不感知网络）。
     *
     * @param sync 接收新半径并同步到服务端的消费器；传 null 恢复 no-op
     */
    public static void setFunnelRadiusSync(DoubleConsumer sync) {
        funnelRadiusSync = sync == null ? r -> {} : sync;
    }

    public static double getFunnelRadius() {
        return funnelRadius;
    }

    /**
     * 设置漏斗吸取半径（0.1 格粒度）并触发同步回调。
     * 仅实际值变化时发送，避免拖动过程中的重复包。
     */
    public static void setFunnelRadius(double radius) {
        double clamped = Math.round(Mth.clamp(radius, MIN_FUNNEL_RADIUS, MAX_FUNNEL_RADIUS) * 10.0) / 10.0;
        if (Double.compare(funnelRadius, clamped) == 0) {
            return;
        }
        funnelRadius = clamped;
        funnelRadiusSync.accept(funnelRadius);
    }

    public static int getUltimineLimit() {
        return ultimineLimit;
    }

    /**
     * 设置连锁挖掘数量上限（按 {@link #ULTIMINE_LIMIT_STEP} 步进取整）。
     */
    public static void setUltimineLimit(int limit) {
        int step = ULTIMINE_LIMIT_STEP;
        int stepped = Math.round((float) (Mth.clamp(limit, MIN_ULTIMINE_LIMIT, MAX_ULTIMINE_LIMIT) - MIN_ULTIMINE_LIMIT) / step)
                * step + MIN_ULTIMINE_LIMIT;
        if (ultimineLimit == stepped) {
            return;
        }
        ultimineLimit = stepped;
    }
}
