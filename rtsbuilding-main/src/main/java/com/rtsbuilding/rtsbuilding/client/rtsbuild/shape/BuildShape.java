package com.rtsbuilding.rtsbuilding.client.rtsbuild.shape;

import com.rtsbuilding.rtsbuilding.network.NetworkConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * RTS 建造画笔的一等公民形状抽象：每种形状自行负责
 * <ul>
 *   <li><b>几何计算</b>——从 {@link ShapeInput} 计算出要建造（或破坏）的方块位置列表；</li>
 *   <li><b>阶段流转</b>——声明自己的交互流程（选点后进入的阶段、右键推进、ESC 回退）；</li>
 *   <li><b>参数调整</b>——声明当前阶段支持哪些 {@link AdjustKind} 调整；</li>
 *   <li><b>提示文案与渲染形态</b>——提供各阶段的交互提示与应渲染的形态。</li>
 * </ul>
 *
 * <p>画笔状态机（{@code LineBrushSelector}）只做通用驱动，不再感知具体形状：
 * 它调用 {@link #compute(ShapeInput)}、{@link #advance(Phase)}、{@link #cancel(Phase)}、
 * {@link #supportsAdjust(Phase, AdjustKind)}、{@link #hint(Phase, ShapeParams)}，
 * 所有形状特判都收敛在本枚举内部。</p>
 */
public enum BuildShape {

    /** 线：沿走向线单排放置。连接模式下路径方块直角连接（不斜向），断点模式为 DDA 对角。 */
    LINE {
        @Override
        public List<BlockPos> compute(ShapeInput in) {
            return in.fillMode() == FillMode.CONNECTED ? ShapeGeometry.connectedLinePositions(in) : ShapeGeometry.linePositions(in);
        }

        @Override
        public Phase pickEndPhase() {
            return Phase.ADJUST;
        }

        @Override
        public PhaseAdvance advance(Phase phase) {
            return phase == Phase.ADJUST ? new PhaseAdvance.Build() : null;
        }

        @Override
        public Phase cancel(Phase phase) {
            return phase == Phase.ADJUST ? Phase.PICK_START : null;
        }

        @Override
        public String hint(Phase phase, ShapeParams params) {
            if (phase == Phase.PICK_START) {
                return tr("screen.rtsbuilding.shape.hint.line.pick");
            }
            if (phase == Phase.ADJUST) {
                return tr("screen.rtsbuilding.shape.hint.line.adjust");
            }
            return null;
        }

        @Override
        public BuildShape renderShape(Phase phase) {
            return BuildShape.LINE;
        }
    },
    /** 墙：走向线沿竖直方向（上下）扩展。实心为完整墙，框架为矩形四边框。 */
    WALL {
        @Override
        public List<BlockPos> compute(ShapeInput in) {
            return ShapeGeometry.wallFillPositions(in);
        }

        @Override
        public Phase pickEndPhase() {
            return Phase.HEIGHT;
        }

        @Override
        public PhaseAdvance advance(Phase phase) {
            return phase == Phase.HEIGHT ? new PhaseAdvance.Build() : null;
        }

        @Override
        public Phase cancel(Phase phase) {
            return phase == Phase.HEIGHT ? Phase.PICK_START : null;
        }

        @Override
        public boolean supportsAdjust(Phase phase, AdjustKind kind) {
            return super.supportsAdjust(phase, kind)
                    || (phase == Phase.HEIGHT && kind == AdjustKind.HEIGHT_EXTEND);
        }

        @Override
        public String hint(Phase phase, ShapeParams params) {
            if (phase == Phase.PICK_START) {
                return tr("screen.rtsbuilding.shape.hint.line.pick");
            }
            if (phase == Phase.HEIGHT) {
                return tr("screen.rtsbuilding.shape.hint.wall.adjust",
                        params.getWallHeight(), params.getWallDown());
            }
            return null;
        }

        @Override
        public BuildShape renderShape(Phase phase) {
            return phase == Phase.HEIGHT ? BuildShape.WALL : BuildShape.LINE;
        }
    },
    /** 面（条形面）：走向线沿垂直的水平方向（左右）扩展。实心为完整面，框架为矩形四边框。 */
    FACE {
        @Override
        public List<BlockPos> compute(ShapeInput in) {
            return ShapeGeometry.faceFillPositions(in);
        }

        @Override
        public Phase pickEndPhase() {
            return Phase.WIDTH;
        }

        @Override
        public PhaseAdvance advance(Phase phase) {
            return phase == Phase.WIDTH ? new PhaseAdvance.Build() : null;
        }

        @Override
        public Phase cancel(Phase phase) {
            return phase == Phase.WIDTH ? Phase.PICK_START : null;
        }

        @Override
        public boolean supportsAdjust(Phase phase, AdjustKind kind) {
            return super.supportsAdjust(phase, kind)
                    || (phase == Phase.WIDTH
                        && (kind == AdjustKind.WIDTH_EXTEND || kind == AdjustKind.FACE_BOTH_SIDES));
        }

        @Override
        public String hint(Phase phase, ShapeParams params) {
            if (phase == Phase.PICK_START) {
                return tr("screen.rtsbuilding.shape.hint.line.pick");
            }
            if (phase == Phase.WIDTH) {
                return tr("screen.rtsbuilding.shape.hint.face.adjust",
                        params.getFaceWidth(), params.getFaceDown());
            }
            return null;
        }

        @Override
        public BuildShape renderShape(Phase phase) {
            return phase == Phase.WIDTH ? BuildShape.FACE : BuildShape.LINE;
        }
    },
    /** 体（实心/空心/框架）：走向线同时沿竖直与水平方向扩展。 */
    SOLID {
        @Override
        public List<BlockPos> compute(ShapeInput in) {
            return ShapeGeometry.solidFillPositions(in);
        }

        @Override
        public Phase pickEndPhase() {
            return Phase.WIDTH;
        }

        @Override
        public PhaseAdvance advance(Phase phase) {
            if (phase == Phase.WIDTH) {
                return new PhaseAdvance.ToPhase(Phase.HEIGHT);
            }
            return phase == Phase.HEIGHT ? new PhaseAdvance.Build() : null;
        }

        @Override
        public Phase cancel(Phase phase) {
            if (phase == Phase.HEIGHT) return Phase.WIDTH;
            return phase == Phase.WIDTH ? Phase.PICK_START : null;
        }

        @Override
        public boolean supportsAdjust(Phase phase, AdjustKind kind) {
            return super.supportsAdjust(phase, kind)
                    || (phase == Phase.WIDTH
                        && (kind == AdjustKind.WIDTH_EXTEND || kind == AdjustKind.FACE_BOTH_SIDES))
                    || (phase == Phase.HEIGHT && kind == AdjustKind.HEIGHT_EXTEND);
        }

        @Override
        public String hint(Phase phase, ShapeParams params) {
            if (phase == Phase.PICK_START) {
                return tr("screen.rtsbuilding.shape.hint.line.pick");
            }
            if (phase == Phase.WIDTH) {
                return tr("screen.rtsbuilding.shape.hint.solid.width",
                        params.getFaceWidth(), params.getFaceDown());
            }
            if (phase == Phase.HEIGHT) {
                return tr("screen.rtsbuilding.shape.hint.solid.height",
                        params.getWallHeight(), params.getWallDown());
            }
            return null;
        }

        @Override
        public BuildShape renderShape(Phase phase) {
            return (phase == Phase.WIDTH || phase == Phase.HEIGHT) ? BuildShape.SOLID : BuildShape.LINE;
        }
    },
    /** 圆面/圆柱（实心/空心/框架）：圆心 + 半径 + 高度扩展。 */
    CIRCLE {
        @Override
        public List<BlockPos> compute(ShapeInput in) {
            return ShapeGeometry.cylinderFillPositions(in);
        }

        @Override
        public Phase pickEndPhase() {
            return Phase.HEIGHT;
        }

        @Override
        public PhaseAdvance advance(Phase phase) {
            return phase == Phase.HEIGHT ? new PhaseAdvance.Build() : null;
        }

        @Override
        public Phase cancel(Phase phase) {
            return phase == Phase.HEIGHT ? Phase.PICK_START : null;
        }

        @Override
        public boolean supportsAdjust(Phase phase, AdjustKind kind) {
            return super.supportsAdjust(phase, kind)
                    || (phase == Phase.HEIGHT && kind == AdjustKind.HEIGHT_EXTEND);
        }

        @Override
        public String hint(Phase phase, ShapeParams params) {
            if (phase == Phase.PICK_START) {
                return tr("screen.rtsbuilding.shape.hint.cylinder.pick");
            }
            if (phase == Phase.HEIGHT) {
                return tr("screen.rtsbuilding.shape.hint.cylinder.adjust",
                        params.getWallHeight(), params.getWallDown());
            }
            return null;
        }

        @Override
        public BuildShape renderShape(Phase phase) {
            return (phase == Phase.PICK_START || phase == Phase.HEIGHT) ? BuildShape.CIRCLE : BuildShape.LINE;
        }
    },
    /** 球/椭球（实心/空心/框架）：球心 + 水平半径 + 高度半径。 */
    SPHERE {
        @Override
        public List<BlockPos> compute(ShapeInput in) {
            return ShapeGeometry.sphereFillPositions(in);
        }

        @Override
        public Phase pickEndPhase() {
            return Phase.RADIUS;
        }

        @Override
        public PhaseAdvance advance(Phase phase) {
            return phase == Phase.RADIUS ? new PhaseAdvance.Build() : null;
        }

        @Override
        public Phase cancel(Phase phase) {
            return phase == Phase.RADIUS ? Phase.PICK_START : null;
        }

        @Override
        public boolean supportsAdjust(Phase phase, AdjustKind kind) {
            return super.supportsAdjust(phase, kind)
                    || (phase == Phase.RADIUS && kind == AdjustKind.SPHERE_RADIUS);
        }

        @Override
        public String hint(Phase phase, ShapeParams params) {
            if (phase == Phase.PICK_START) {
                return tr("screen.rtsbuilding.shape.hint.sphere.pick");
            }
            if (phase == Phase.RADIUS) {
                return tr("screen.rtsbuilding.shape.hint.sphere.adjust", params.getSphereRadius());
            }
            return null;
        }

        @Override
        public BuildShape renderShape(Phase phase) {
            return (phase == Phase.PICK_START || phase == Phase.RADIUS) ? BuildShape.SPHERE : BuildShape.LINE;
        }
    };

    /** 扩展量上限（格，向上/向下、两侧分别限制）。 */
    public static final int MAX_EXTEND = 64;

    /** 中文显示名（下嵌层标题、提示等 UI 用），经 lang 语言文件本地化。 */
    public String label() {
        String key = switch (this) {
            case LINE -> "line";
            case WALL -> "wall";
            case FACE -> "plane";
            case SOLID -> "solid";
            case CIRCLE -> "circle_face";
            case SPHERE -> "sphere";
        };
        return net.minecraft.network.chat.Component.translatable(
                "tooltip.rtsbuilding.left.shape." + key).getString();
    }

    /** 圆面/圆柱半径上限（格）。 */
    public static final int MAX_RADIUS = 64;

    /** 单次形状生成的位置数量上限（与网络包上限一致）。超过后停止生成，
     *  避免超大形状（如 r=64 的球约 109 万格）在客户端全量计算再截断。 */
    public static final int MAX_POSITIONS = NetworkConstants.MAX_POSITIONS;

    /** 翻译 lang key（UI 交互提示统一走 lang，见 AGENTS.md 语言约定）。 */
    static String tr(String key) {
        return Component.translatable(key).getString();
    }

    /** 翻译带参数 lang key（提示中的数字/尺寸用 %d/%s 占位）。 */
    static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /**
     * 根据输入参数计算该形状覆盖的全部方块位置。
     *
     * @param in 画笔状态快照
     * @return 方块位置列表；参数不完整（缺端点等）时返回空列表
     */
    public abstract List<BlockPos> compute(ShapeInput in);

    // ==================== 交互行为声明（形状自持） ====================

    /**
     * 选终点（{@link Phase#PICK_START}）后进入的初始调整阶段。
     * 各形状自行声明：线→ADJUST、墙/圆→HEIGHT、面/体→WIDTH、球→RADIUS。
     */
    public abstract Phase pickEndPhase();

    /**
     * 右键推进当前阶段：返回 {@link PhaseAdvance.ToPhase} 表示进入下一阶段，
     * 返回 {@link PhaseAdvance.Build} 表示可确认建造，返回 {@code null} 表示当前阶段无操作。
     */
    public abstract PhaseAdvance advance(Phase phase);

    /**
     * ESC 逐级回退：返回应回退到的阶段；返回 {@code null} 表示已退到最前，应完全取消（reset）。
     */
    public abstract Phase cancel(Phase phase);

    /**
     * 当前阶段是否支持某类参数调整（由滚轮触发）。
     *
     * <p>基类默认实现：选点/线微调阶段（{@link Phase#PICK_START} / {@link Phase#ADJUST}）的
     * 两端高度偏移（{@link AdjustKind#START_HEIGHT} / {@link AdjustKind#END_HEIGHT}）是所有形状
     * 在画线阶段都支持的操作；各形状 override 时用 {@code super.supportsAdjust(...)} 叠加自己
     * 扩展阶段（HEIGHT/WIDTH/RADIUS）特有的调整权限。</p>
     */
    public boolean supportsAdjust(Phase phase, AdjustKind kind) {
        if (kind == AdjustKind.START_HEIGHT || kind == AdjustKind.END_HEIGHT) {
            return phase == Phase.PICK_START || phase == Phase.ADJUST;
        }
        return false;
    }

    /**
     * 当前阶段的交互提示文案；无提示（非交互阶段）返回 {@code null}。
     */
    public abstract String hint(Phase phase, ShapeParams params);

    /**
     * 当前「形状 × 阶段」下应生效的渲染形态。
     * 圆/球在选点与调整阶段即渲染完整形状，墙/面/体仅在各自扩展阶段渲染扩展结果，其余阶段渲染走向线。
     */
    public abstract BuildShape renderShape(Phase phase);
}
