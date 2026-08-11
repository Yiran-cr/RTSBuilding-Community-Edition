package com.rtsbuilding.rtsbuilding.client.rtsbuild.shape;

/**
 * 画笔参数调整类型枚举。
 *
 * <p>描述滚轮可以调整的几何参数维度。每种 {@link BuildShape} 通过
 * {@link BuildShape#supportsAdjust(Phase, AdjustKind)} 声明在当前阶段
 * 支持哪些调整，由 {@link ShapeParams} 统一执行参数增减。</p>
 */
public enum AdjustKind {
    /** 起点高度偏移（画线/微调阶段 Shift+滚轮）。 */
    START_HEIGHT,
    /** 终点高度偏移（画线/微调阶段 Shift+Alt+滚轮）。 */
    END_HEIGHT,
    /** 竖直扩展量（墙/体/圆的高度阶段 Shift+滚轮）。 */
    HEIGHT_EXTEND,
    /** 单侧水平扩展量（面/体的宽度阶段 Shift+滚轮）。 */
    WIDTH_EXTEND,
    /** 双侧对称水平扩展量（面/体的宽度阶段 Shift+Alt+滚轮）。 */
    FACE_BOTH_SIDES,
    /** 球半径（球半径阶段 Shift+滚轮）。 */
    SPHERE_RADIUS
}
