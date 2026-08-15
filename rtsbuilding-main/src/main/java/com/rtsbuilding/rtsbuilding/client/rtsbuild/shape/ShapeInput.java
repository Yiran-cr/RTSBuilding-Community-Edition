package com.rtsbuilding.rtsbuilding.client.rtsbuild.shape;

import net.minecraft.core.BlockPos;

/**
 * 形状计算的不可变输入参数：由 {@link com.rtsbuilding.rtsbuilding.client.render.pass.LineBrushSelector}
 * 在每次计算时快照生成，使 {@link BuildShape} 的计算逻辑与画笔状态机完全解耦。
 *
 * <p>字段含义与画笔参数一一对应：{@code start}/{@code hover} 为原始端点（不含高度偏移），
 * {@code startDy}/{@code endDy} 为两端高度偏移，{@code wallUp}/{@code wallDown} 为竖直扩展，
 * {@code faceWidth}/{@code faceDown} 为水平扩展，{@code sphereRadius} 为球半径，
 * {@code flatDown} 为画线平直锁定（V 键）标志，{@code fillMode} 为形状填充模式
 * （体/圆柱/球的实心、空心、框架）。</p>
 */
public record ShapeInput(
        BlockPos start,
        BlockPos hover,
        int startDy,
        int endDy,
        int wallUp,
        int wallDown,
        int faceWidth,
        int faceDown,
        int sphereRadius,
        boolean flatDown,
        FillMode fillMode) {

    /** 应用起点高度偏移后的实际起点。 */
    public BlockPos effectiveStart() {
        return new BlockPos(start.getX(), start.getY() + startDy, start.getZ());
    }

    /**
     * 应用终点高度偏移（或平直锁定到起点高度）后的实际终点。
     * 端点缺失时返回 {@code null}。
     */
    public BlockPos effectiveEnd() {
        if (hover == null) return null;
        if (flatDown) {
            BlockPos s = effectiveStart();
            return new BlockPos(hover.getX(), s.getY(), hover.getZ());
        }
        return new BlockPos(hover.getX(), hover.getY() + endDy, hover.getZ());
    }
}
