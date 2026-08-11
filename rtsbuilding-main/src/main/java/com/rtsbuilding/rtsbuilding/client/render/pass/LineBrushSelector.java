package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.rtsbuilding.rtsbuilding.client.build.shape.AdjustKind;
import com.rtsbuilding.rtsbuilding.client.build.shape.BuildShape;
import com.rtsbuilding.rtsbuilding.client.build.shape.Phase;
import com.rtsbuilding.rtsbuilding.client.build.shape.PhaseAdvance;
import com.rtsbuilding.rtsbuilding.client.build.shape.ShapeInput;
import com.rtsbuilding.rtsbuilding.client.build.shape.ShapeParams;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 画笔建造的通用状态机（点选式）。
 *
 * <p>右键单击选择起点进入 {@link Phase#PICK_START}，移动鼠标实时预览线段，
 * 再次右键单击选择终点/球心：各形状由自身声明进入的调整阶段
 * （{@link BuildShape#pickEndPhase()}）。调整阶段内 Shift+滚轮调整参数
 * （{@link BuildShape#supportsAdjust} + {@link ShapeParams#adjust}），
 * 右键推进/确认（{@link BuildShape#advance}），ESC 逐级回退
 * （{@link BuildShape#cancel}）。</p>
 *
 * <p>本类<b>不感知具体形状</b>：所有形状特判都收敛在 {@link BuildShape} 内部，
 * 本类只做通用驱动——持有 {@link ShapeParams} 参数容器与各阶段状态。</p>
 */
public final class LineBrushSelector {

    /** 扩展量上限（格，向上/向下、两侧分别限制）。 */
    public static final int MAX_EXTEND = BuildShape.MAX_EXTEND;

    /** 圆面/圆柱半径上限（格）。 */
    public static final int MAX_RADIUS = BuildShape.MAX_RADIUS;

    private BuildShape shape = BuildShape.LINE;

    /** 各形状共享的可变几何参数。 */
    private final ShapeParams params = new ShapeParams();

    private Phase phase = Phase.IDLE;
    private BlockPos start;
    private BlockPos hover;

    /** 画笔归属：{@code true} 表示当前画笔用于破坏（确认时发 AREA_DESTROY），
     *  {@code false} 表示用于建造（确认时发 PLACE_BATCH）。由调用方启动画笔时设置。 */
    private boolean breakActive;

    /** 形状计算结果缓存：输入（形状/阶段/端点/参数）未变时复用，避免每帧重算大形状几何。 */
    private List<BlockPos> cachedPositions;

    /** 与 {@link #cachedPositions} 对应的输入快照（hash），用于命中检测。 */
    private long cachedStamp;

    public Phase getPhase() {
        return phase;
    }

    /** 当前建造形状。 */
    public BuildShape getShape() {
        return shape;
    }

    /** 是否处于起点选择/画线预览阶段。 */
    public boolean isPicking() {
        return phase == Phase.PICK_START;
    }

    /** 是否处于阶段一（线：微调两端高度待确认）。 */
    public boolean isAdjusting() {
        return phase == Phase.ADJUST;
    }

    /** 是否处于宽度调整阶段（面/体阶段二）。 */
    public boolean isWidthAdjusting() {
        return phase == Phase.WIDTH;
    }

    /** 是否处于高度调整阶段（墙阶段二 / 体阶段三）。 */
    public boolean isHeightAdjusting() {
        return phase == Phase.HEIGHT;
    }

    /** 是否处于球半径调节阶段。 */
    public boolean isRadiusAdjusting() {
        return phase == Phase.RADIUS;
    }

    /** 当前形状是否为球。 */
    public boolean isSphereActive() {
        return shape == BuildShape.SPHERE;
    }

    /** 当前形状是否处于任一确认阶段（用于渲染预览与状态清理）。 */
    public boolean isActive() {
        return phase == Phase.PICK_START || phase == Phase.ADJUST
                || phase == Phase.WIDTH || phase == Phase.HEIGHT
                || phase == Phase.RADIUS;
    }

    @Nullable
    public BlockPos getStart() {
        return start;
    }

    @Nullable
    public BlockPos getHover() {
        return hover;
    }

    /** 球半径（格）。 */
    public int getSphereRadius() {
        return params.getSphereRadius();
    }

    /** 当前向上墙高（格）。 */
    public int getWallHeight() {
        return params.getWallHeight();
    }

    /** 当前向下墙高（格）。 */
    public int getWallDown() {
        return params.getWallDown();
    }

    /** 当前面宽度（一侧，格）。 */
    public int getFaceWidth() {
        return params.getFaceWidth();
    }

    /** 当前面另一侧宽度（格）。 */
    public int getFaceDown() {
        return params.getFaceDown();
    }

    /** 单击选择起点并进入画线预览（shape 决定后续扩展方向与阶段流转）。 */
    public boolean start(BlockPos pos, BuildShape shape) {
        return start(pos, shape, false);
    }

    /**
     * 单击选择起点并进入画线预览，指定画笔归属（建造/破坏）。
     * 驱动键由调用方决定：建造侧由右键触发、破坏侧由左键触发。
     */
    public boolean start(BlockPos pos, BuildShape shape, boolean breakActive) {
        if (pos == null || shape == null) return false;
        this.start = pos.immutable();
        this.shape = shape;
        this.breakActive = breakActive;
        this.params.reset();
        this.phase = Phase.PICK_START;
        return true;
    }

    /** 当前画笔是否用于破坏。 */
    public boolean isBreakActive() {
        return breakActive;
    }

    /** 移动鼠标更新预览终点（仅起点选择阶段跟随）。 */
    public void updateHover(@Nullable BlockPos pos) {
        if (phase != Phase.PICK_START) {
            // 已选终点：锁定预览，不随鼠标移动改变
            return;
        }
        this.hover = pos == null ? null : pos.immutable();
    }

    /**
     * 右键单击选择终点/球心：由形状声明进入的调整阶段。
     * 球无需第二点（{@code hover} 可为 null），其余形状需已选终点。
     */
    public boolean pickEnd() {
        if (phase != Phase.PICK_START || start == null) {
            return false;
        }
        if (shape != BuildShape.SPHERE && hover == null) {
            return false;
        }
        phase = shape.pickEndPhase();
        return true;
    }

    /**
     * 右键推进确认阶段：由形状声明下一步（进入下一阶段或直接建造）。
     *
     * @return {@code true} 表示可以建造，{@code false} 表示进入下一阶段或当前阶段无操作
     */
    public boolean advancePhase() {
        PhaseAdvance adv = shape.advance(phase);
        if (adv == null) {
            return false;
        }
        if (adv instanceof PhaseAdvance.ToPhase to) {
            phase = to.phase();
            return false;
        }
        return true;
    }

    /**
     * 计算当前应建造/预览的方块列表（含扩展与两端高度偏移）。
     * 根据「形状 × 阶段」确定当前生效的形态（{@link BuildShape#renderShape}）。
     * 渲染预览与确认提交共用本方法，避免两套派发逻辑。
     *
     * <p><b>性能：</b>结果按输入快照缓存——形状参数（墙高/面宽/半径/端点）在
     * 扩展阶段固定不变，而渲染每帧调用，缓存可避免大形状（如球 r=64 约 200 万次
     * 迭代）每帧重算。</p>
     */
    public List<BlockPos> computePositions() {
        long stamp = inputStamp();
        if (cachedPositions != null && stamp == cachedStamp) {
            return cachedPositions;
        }
        cachedPositions = shape.renderShape(phase).compute(toShapeInput());
        cachedStamp = stamp;
        return cachedPositions;
    }

    /** 输入快照 hash：形状/阶段/端点/全部扩展参数/平直标志任一变化都会改变。 */
    private long inputStamp() {
        long s = shape.ordinal() * 31L + phase.ordinal();
        s = s * 31 + (start == null ? 0 : start.asLong());
        s = s * 31 + (hover == null ? 0 : hover.asLong());
        s = s * 31 + params.getStartDy();
        s = s * 31 + params.getEndDy();
        s = s * 31 + params.getWallHeight();
        s = s * 31 + params.getWallDown();
        s = s * 31 + params.getFaceWidth();
        s = s * 31 + params.getFaceDown();
        s = s * 31 + params.getSphereRadius();
        s = s * 31 + (RtsKeyMappings.isLineFlatDown() ? 1 : 0);
        return s;
    }

    /** 将当前状态快照为形状计算的不可变输入。 */
    private ShapeInput toShapeInput() {
        return new ShapeInput(start, hover, params.getStartDy(), params.getEndDy(),
                params.getWallHeight(), params.getWallDown(),
                params.getFaceWidth(), params.getFaceDown(),
                params.getSphereRadius(), RtsKeyMappings.isLineFlatDown());
    }

    /**
     * 滚轮调整参数：由形状声明当前阶段是否支持该调整，参数增减统一由 {@link ShapeParams} 执行。
     */
    private void adjust(AdjustKind kind, int delta) {
        if (shape.supportsAdjust(phase, kind)) {
            params.adjust(kind, delta);
        }
    }

    /** 调整起始点高度（画线/微调阶段，方向 +1/-1）。 */
    public void adjustStartHeight(int delta) {
        adjust(AdjustKind.START_HEIGHT, delta);
    }

    /** 调整终止点高度（画线/微调阶段，方向 +1/-1）。 */
    public void adjustEndHeight(int delta) {
        adjust(AdjustKind.END_HEIGHT, delta);
    }

    /** 高度阶段（墙/体/圆）调整竖直扩展量，对称逻辑：先回收反侧再延伸正侧。 */
    public void adjustHeightExtend(int delta) {
        adjust(AdjustKind.HEIGHT_EXTEND, delta);
    }

    /** 宽度阶段（面/体）调整水平扩展量，对称逻辑：先回收反侧再延伸正侧。 */
    public void adjustWidthExtend(int delta) {
        adjust(AdjustKind.WIDTH_EXTEND, delta);
    }

    /** 宽度阶段按住 Shift+Alt 滚轮：两侧同时延展（左右对称加宽/收窄），走向线始终保持居中。 */
    public void adjustFaceBothSides(int delta) {
        adjust(AdjustKind.FACE_BOTH_SIDES, delta);
    }

    /** 球半径阶段 Shift+滚轮：调节球半径（方向 +1/-1）。 */
    public void adjustSphereRadius(int delta) {
        adjust(AdjustKind.SPHERE_RADIUS, delta);
    }

    /**
     * ESC 逐级取消：由形状声明回退目标。每按一次 ESC 只回退一个阶段，不会一次性取消整个流程；
     * 已退到最前时完全取消（reset）。
     */
    public void cancelStage() {
        Phase target = shape.cancel(phase);
        if (target != null) {
            phase = target;
        } else {
            reset();
        }
    }

    /**
     * 当前阶段的交互提示文案；非交互阶段返回 {@code null}。
     * 破坏侧由左键驱动（左键选取），将"建造"替换为"破坏"、"右键"替换为"左键"。
     */
    @Nullable
    public String currentHint() {
        if (!isActive()) return null;
        String hint = shape.hint(phase, params);
        if (hint == null || !breakActive) return hint;
        return hint.replace("建造", "破坏").replace("右键", "左键");
    }

    public void reset() {
        phase = Phase.IDLE;
        shape = BuildShape.LINE;
        start = null;
        hover = null;
        breakActive = false;
        params.reset();
        cachedPositions = null;
        cachedStamp = 0;
    }
}
