package com.rtsbuilding.rtsbuilding.client.rtsbuild.shape;

/**
 * 画笔交互阶段枚举。
 *
 * <p>与具体形状解耦：每个 {@link BuildShape} 自行声明自己的阶段流转
 * （{@link BuildShape#pickEndPhase()} / {@link BuildShape#advance(Phase)} /
 * {@link BuildShape#cancel(Phase)}），{@code Phase} 只作为通用的状态载体。</p>
 */
public enum Phase {
    /** 空闲：未开始任何形状交互。 */
    IDLE,
    /** 起点已选择，移动鼠标实时预览，右键选择终点/球心。 */
    PICK_START,
    /** 阶段一（线）：终点已选择，滚轮微调两端高度，右键确认建造。 */
    ADJUST,
    /** 阶段二（面/体）：滚轮调整宽度，面右键确认建造，体右键进入高度阶段。 */
    WIDTH,
    /** 阶段三（墙/圆：阶段二；体：阶段三）：滚轮调整高度，右键确认建造。 */
    HEIGHT,
    /** 球半径阶段：Shift+滚轮调节球半径，右键确认建造。 */
    RADIUS
}
