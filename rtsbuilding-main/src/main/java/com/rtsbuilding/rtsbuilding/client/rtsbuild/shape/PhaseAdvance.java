package com.rtsbuilding.rtsbuilding.client.rtsbuild.shape;

/**
 * 形状阶段推进的结果。
 *
 * <p>由 {@link BuildShape#advance(Phase)} 返回，指示状态机右键推进后的下一步：
 * 要么进入指定阶段（{@link ToPhase}），要么直接确认建造（{@link Build}）。</p>
 */
public sealed interface PhaseAdvance permits PhaseAdvance.Build, PhaseAdvance.ToPhase {

    /** 进入指定阶段，尚未到建造确认。 */
    record ToPhase(Phase phase) implements PhaseAdvance {
    }

    /** 右键确认：可以建造。 */
    record Build() implements PhaseAdvance {
    }
}
