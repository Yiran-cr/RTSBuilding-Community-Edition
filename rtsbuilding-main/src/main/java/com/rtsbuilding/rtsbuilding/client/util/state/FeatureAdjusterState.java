package com.rtsbuilding.rtsbuilding.client.util.state;

import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import net.minecraft.util.Mth;

/**
 * 客户端功能调节器状态（右面板下嵌层滑块）。
 *
 * <p>维护可在 RTS 运行中动态调节的功能参数：
 * <ul>
 *   <li>漏斗（物品拾取）吸取范围半径（格）——服务端权威执行球心吸取，变化时同步到服务端。</li>
 *   <li>连锁挖掘（Ultimine）方块数量上限——随启动包发送，纯客户端偏好（预览与服务端一致）。</li>
 * </ul>
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

    private static double funnelRadius = DEFAULT_FUNNEL_RADIUS;
    private static int ultimineLimit = DEFAULT_ULTIMINE_LIMIT;

    private FeatureAdjusterState() {
    }

    public static double getFunnelRadius() {
        return funnelRadius;
    }

    /**
     * 设置漏斗吸取半径（0.1 格粒度）并同步到服务端。
     * 仅实际值变化时发送，避免拖动过程中的重复包。
     */
    public static void setFunnelRadius(double radius) {
        double clamped = Math.round(Mth.clamp(radius, MIN_FUNNEL_RADIUS, MAX_FUNNEL_RADIUS) * 10.0) / 10.0;
        if (Double.compare(funnelRadius, clamped) == 0) {
            return;
        }
        funnelRadius = clamped;
        RtsClientPacketGateway.sendSetFunnelRadius(funnelRadius);
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
