package com.rtsbuilding.rtsbuilding.client.rtsbuild.shape;

import com.rtsbuilding.rtsbuilding.network.NetworkConstants;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
            return in.fillMode() == FillMode.CONNECTED ? connectedLinePositions(in) : linePositions(in);
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
                return "画线：右键选择终点  ·  Shift+滚轮调起点高度 / Shift+Alt+滚轮调终点高度  ·  滚轮缩放相机  ·  ESC 取消";
            }
            if (phase == Phase.ADJUST) {
                return "确认：右键建造  ·  Shift+滚轮调起点高度 / Shift+Alt+滚轮调终点高度  ·  滚轮缩放相机  ·  ESC 返回";
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
            return wallFillPositions(in);
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
                return "画线：右键选择终点  ·  Shift+滚轮调起点高度 / Shift+Alt+滚轮调终点高度  ·  滚轮缩放相机  ·  ESC 取消";
            }
            if (phase == Phase.HEIGHT) {
                return "墙高 ↑" + params.getWallHeight() + " ↓" + params.getWallDown()
                        + "：Shift+滚轮调墙高 · 滚轮缩放相机  ·  右键建造  ·  ESC 返回";
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
            return faceFillPositions(in);
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
                return "画线：右键选择终点  ·  Shift+滚轮调起点高度 / Shift+Alt+滚轮调终点高度  ·  滚轮缩放相机  ·  ESC 取消";
            }
            if (phase == Phase.WIDTH) {
                return "面宽 ↑" + params.getFaceWidth() + " ↓" + params.getFaceDown()
                        + "：Shift+滚轮调宽度 · Shift+Alt+滚轮双边延展 · 滚轮缩放相机  ·  右键建造  ·  ESC 返回";
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
            return solidFillPositions(in);
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
                return "画线：右键选择终点  ·  Shift+滚轮调起点高度 / Shift+Alt+滚轮调终点高度  ·  滚轮缩放相机  ·  ESC 取消";
            }
            if (phase == Phase.WIDTH) {
                return "体宽 ↑" + params.getFaceWidth() + " ↓" + params.getFaceDown()
                        + "：Shift+滚轮调宽度 · Shift+Alt+滚轮双边延展 · 滚轮缩放相机  ·  右键进入高度  ·  ESC 返回";
            }
            if (phase == Phase.HEIGHT) {
                return "体高 ↑" + params.getWallHeight() + " ↓" + params.getWallDown()
                        + "：Shift+滚轮调高度 · 滚轮缩放相机  ·  右键建造  ·  ESC 返回"
;
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
            return cylinderFillPositions(in);
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
                return "圆柱：右键选择圆上一点（定半径）  ·  Shift+滚轮调圆心高度  ·  滚轮缩放相机  ·  ESC 取消";
            }
            if (phase == Phase.HEIGHT) {
                return "圆柱 ↑" + params.getWallHeight() + " ↓" + params.getWallDown()
                        + "：Shift+滚轮调高度 · 滚轮缩放相机  ·  右键建造  ·  ESC 返回"
;
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
            return sphereFillPositions(in);
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
                return "球：右键选择球心  ·  Shift+滚轮调球心高度  ·  滚轮缩放相机  ·  ESC 取消";
            }
            if (phase == Phase.RADIUS) {
                return "球半径 " + params.getSphereRadius()
                        + "：Shift+滚轮调半径  ·  滚轮缩放相机  ·  右键建造  ·  ESC 返回"
;
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

    // ==================== 各形状几何实现 ====================

    /** 线：起点到终点（含两端高度偏移）的连续方块列表。 */
    private static List<BlockPos> linePositions(ShapeInput in) {
        if (in.start() == null || in.hover() == null) return List.of();
        BlockPos start = in.effectiveStart();
        BlockPos end = in.effectiveEnd();
        if (end == null) return List.of();
        return lineBetween(start, end);
    }

    /** 线：连接模式（路径方块直角连接，不斜向相连）。 */
    private static List<BlockPos> connectedLinePositions(ShapeInput in) {
        if (in.start() == null || in.hover() == null) return List.of();
        BlockPos end = in.effectiveEnd();
        if (end == null) return List.of();
        return connectedLineBetween(in.effectiveStart(), end);
    }

    /**
     * 管道式连接线段：以断点（3D DDA）路径为基础，逐对检查相邻方块——
     * 凡是斜向断开（未共享面，曼哈顿距离 &gt; 1）的相邻对，在两者之间补一个
     * 中间方块使其面连接，从而把断点串成连续的管道，且不改变断点路径的走向。
     */
    private static List<BlockPos> connectedLineBetween(BlockPos a, BlockPos b) {
        List<BlockPos> base = lineBetween(a, b);
        if (base.isEmpty()) {
            return base;
        }
        List<BlockPos> result = new ArrayList<>(base.size() * 2);
        result.add(base.get(0));
        for (int i = 1; i < base.size(); i++) {
            BlockPos prev = result.get(result.size() - 1);
            BlockPos cur = base.get(i);
            int dx = cur.getX() - prev.getX();
            int dy = cur.getY() - prev.getY();
            int dz = cur.getZ() - prev.getZ();
            // 斜向断开：在 prev 与 cur 之间补中间连接块
            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 1) {
                BlockPos mid = midStep(prev, dx, dy, dz);
                result.add(mid);
                int dx2 = cur.getX() - mid.getX();
                int dy2 = cur.getY() - mid.getY();
                int dz2 = cur.getZ() - mid.getZ();
                // 三轴同时变化时补一个仍不面连接，再补第二个
                if (Math.abs(dx2) + Math.abs(dy2) + Math.abs(dz2) > 1) {
                    result.add(midStep(mid, dx2, dy2, dz2));
                }
            }
            result.add(cur);
            if (result.size() >= MAX_POSITIONS) {
                break;
            }
        }
        return result;
    }

    /** 从 {@code prev} 出发沿第一个非零分量前进一步（用于补中间连接块）。 */
    private static BlockPos midStep(BlockPos prev, int dx, int dy, int dz) {
        if (dx != 0) {
            return new BlockPos(prev.getX() + Integer.signum(dx), prev.getY(), prev.getZ());
        }
        if (dy != 0) {
            return new BlockPos(prev.getX(), prev.getY() + Integer.signum(dy), prev.getZ());
        }
        return new BlockPos(prev.getX(), prev.getY(), prev.getZ() + Integer.signum(dz));
    }

    /** 墙：实心为完整竖直扩展，框架为矩形四边框。 */
    private static List<BlockPos> wallFillPositions(ShapeInput in) {
        List<BlockPos> line = linePositions(in);
        return switch (in.fillMode()) {
            case FRAME -> wallFramePositions(in, line);
            default -> extendVertical(line, in.wallUp(), in.wallDown());
        };
    }

    /** 墙框架：矩形墙的四条棱边——走向线两端竖直边 + 顶部/底部沿走向线。 */
    private static List<BlockPos> wallFramePositions(ShapeInput in, List<BlockPos> line) {
        if (line.isEmpty()) return List.of();
        BlockPos startP = line.get(0);
        BlockPos endP = line.get(line.size() - 1);
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        // 两端竖直边
        for (int dy = -in.wallDown(); dy < in.wallUp(); dy++) {
            addIfRoom(result, new BlockPos(startP.getX(), startP.getY() + dy, startP.getZ()));
            addIfRoom(result, new BlockPos(endP.getX(), endP.getY() + dy, endP.getZ()));
        }
        // 顶部/底部沿走向线
        int top = in.wallUp() - 1;
        int bottom = -in.wallDown();
        for (BlockPos p : line) {
            addIfRoom(result, new BlockPos(p.getX(), p.getY() + top, p.getZ()));
            addIfRoom(result, new BlockPos(p.getX(), p.getY() + bottom, p.getZ()));
        }
        return new ArrayList<>(result);
    }

    /** 面：实心为完整水平扩展，框架为矩形四边框。 */
    private static List<BlockPos> faceFillPositions(ShapeInput in) {
        List<BlockPos> line = linePositions(in);
        return switch (in.fillMode()) {
            case FRAME -> faceFramePositions(in, line);
            default -> extendHorizontal(line, in.faceWidth(), in.faceDown(), horizontalExtendAxis(in));
        };
    }

    /** 面框架：矩形面的四条棱边——走向线两端列 + 两侧最外水平行。 */
    private static List<BlockPos> faceFramePositions(ShapeInput in, List<BlockPos> line) {
        if (line.isEmpty()) return List.of();
        boolean alongX = horizontalExtendAxis(in);
        BlockPos startP = line.get(0);
        BlockPos endP = line.get(line.size() - 1);
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        // 两端列（走向线首尾各沿水平方向一列）
        for (int d = -in.faceDown(); d < in.faceWidth(); d++) {
            addIfRoom(result, alongX
                    ? new BlockPos(startP.getX() + d, startP.getY(), startP.getZ())
                    : new BlockPos(startP.getX(), startP.getY(), startP.getZ() + d));
            addIfRoom(result, alongX
                    ? new BlockPos(endP.getX() + d, endP.getY(), endP.getZ())
                    : new BlockPos(endP.getX(), endP.getY(), endP.getZ() + d));
        }
        // 两侧最外水平行（沿走向线）
        int near = in.faceWidth() - 1;
        int far = -in.faceDown();
        for (BlockPos p : line) {
            addIfRoom(result, alongX
                    ? new BlockPos(p.getX() + near, p.getY(), p.getZ())
                    : new BlockPos(p.getX(), p.getY(), p.getZ() + near));
            addIfRoom(result, alongX
                    ? new BlockPos(p.getX() + far, p.getY(), p.getZ())
                    : new BlockPos(p.getX(), p.getY(), p.getZ() + far));
        }
        return new ArrayList<>(result);
    }

    /** 超限保护地向集合添加方块。 */
    private static void addIfRoom(LinkedHashSet<BlockPos> set, BlockPos p) {
        if (set.size() < MAX_POSITIONS) {
            set.add(p);
        }
    }

    /** 墙：走向线上的每个方块向上/向下扩展。 */
    private static List<BlockPos> extendVertical(List<BlockPos> line, int up, int down) {
        if (line.isEmpty()) return List.of();
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        for (BlockPos p : line) {
            for (int dy = -down; dy < up; dy++) {
                result.add(new BlockPos(p.getX(), p.getY() + dy, p.getZ()));
                if (result.size() >= MAX_POSITIONS) {
                    return new ArrayList<>(result);
                }
            }
        }
        return new ArrayList<>(result);
    }

    /** 面：走向线上的每个方块沿垂直的水平主轴扩展。 */
    private static List<BlockPos> extendHorizontal(List<BlockPos> line, int width, int down, boolean alongX) {
        if (line.isEmpty()) return List.of();
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        for (BlockPos p : line) {
            for (int d = -down; d < width; d++) {
                result.add(alongX
                        ? new BlockPos(p.getX() + d, p.getY(), p.getZ())
                        : new BlockPos(p.getX(), p.getY(), p.getZ() + d));
                if (result.size() >= MAX_POSITIONS) {
                    return new ArrayList<>(result);
                }
            }
        }
        return new ArrayList<>(result);
    }

    /** 体：走向线 × 竖直 × 水平的三维填充。 */
    private static List<BlockPos> extendSolid(List<BlockPos> line, int up, int down,
                                              int width, int faceDown, boolean alongX) {
        if (line.isEmpty()) return List.of();
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        for (BlockPos p : line) {
            for (int dy = -down; dy < up; dy++) {
                for (int d = -faceDown; d < width; d++) {
                    result.add(alongX
                            ? new BlockPos(p.getX() + d, p.getY() + dy, p.getZ())
                            : new BlockPos(p.getX(), p.getY() + dy, p.getZ() + d));
                    if (result.size() >= MAX_POSITIONS) {
                        return new ArrayList<>(result);
                    }
                }
            }
        }
        return new ArrayList<>(result);
    }

    /** 圆面/圆柱：以圆心（含起点高度偏移）为轴心，dx²+dz²≤r² 判定填充。 */
    private static List<BlockPos> cylinderPositions(ShapeInput in) {
        if (in.start() == null || in.hover() == null) return List.of();
        int radius = circleRadius(in);
        if (radius < 0) return List.of();
        int cy = in.effectiveStart().getY();
        List<BlockPos> result = new ArrayList<>();
        for (int dy = -in.wallDown(); dy < in.wallUp(); dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz <= radius * radius) {
                        result.add(new BlockPos(in.start().getX() + dx, cy + dy, in.start().getZ() + dz));
                        if (result.size() >= MAX_POSITIONS) {
                            return result;
                        }
                    }
                }
            }
        }
        return result;
    }

    /** 球：以球心（含起点高度偏移）为中心，dx²+dy²+dz²≤r² 判定填充。 */
    private static List<BlockPos> spherePositions(ShapeInput in) {
        if (in.start() == null) return List.of();
        int r = Math.max(1, in.sphereRadius());
        int cy = in.effectiveStart().getY();
        List<BlockPos> result = new ArrayList<>();
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= r * r) {
                        result.add(new BlockPos(in.start().getX() + dx, cy + dy, in.start().getZ() + dz));
                        if (result.size() >= MAX_POSITIONS) {
                            return result;
                        }
                    }
                }
            }
        }
        return result;
    }

    // ==================== 填充模式（实心 / 空心 / 框架） ====================

    /**
     * 体：按填充模式生成——实心全填 / 空心外壳 / 框架 12 条棱边。
     */
    private static List<BlockPos> solidFillPositions(ShapeInput in) {
        List<BlockPos> solid = extendSolid(linePositions(in), in.wallUp(), in.wallDown(),
                in.faceWidth(), in.faceDown(), horizontalExtendAxis(in));
        return switch (in.fillMode()) {
            case SOLID -> solid;
            case HOLLOW -> hollowFilter(solid);
            case FRAME -> frameOfSolid(solid);
            default -> solid;
        };
    }

    /**
     * 圆柱：按填充模式生成——实心全填 / 空心外壳（侧壁 + 顶底）/ 框架侧壁薄壳。
     */
    private static List<BlockPos> cylinderFillPositions(ShapeInput in) {
        List<BlockPos> solid = cylinderPositions(in);
        return switch (in.fillMode()) {
            case SOLID -> solid;
            case HOLLOW -> hollowFilter(solid);
            case FRAME -> sideWallOf(solid);
            default -> solid;
        };
    }

    /**
     * 球：按填充模式生成——实心全填 / 空心球壳 / 框架赤道大圆环 + 两条子午线环。
     */
    private static List<BlockPos> sphereFillPositions(ShapeInput in) {
        List<BlockPos> solid = spherePositions(in);
        List<BlockPos> hollow = hollowFilter(solid);
        return switch (in.fillMode()) {
            case SOLID -> solid;
            case HOLLOW -> hollow;
            case FRAME -> sphereFrameOf(hollow, in.start(), in.effectiveStart().getY());
            default -> solid;
        };
    }

    /** 空心过滤：保留至少有一个 6 邻格不在集合内的边界格（外壳薄层）。 */
    private static List<BlockPos> hollowFilter(List<BlockPos> positions) {
        if (positions.isEmpty()) return List.of();
        Set<BlockPos> set = new HashSet<>(positions);
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos p : positions) {
            if (!set.contains(p.offset(1, 0, 0)) || !set.contains(p.offset(-1, 0, 0))
                    || !set.contains(p.offset(0, 1, 0)) || !set.contains(p.offset(0, -1, 0))
                    || !set.contains(p.offset(0, 0, 1)) || !set.contains(p.offset(0, 0, -1))) {
                result.add(p);
                if (result.size() >= MAX_POSITIONS) {
                    break;
                }
            }
        }
        return result;
    }

    /** 体框架：至少两个轴方向有缺失邻格的格子（12 条棱 + 角点）。 */
    private static List<BlockPos> frameOfSolid(List<BlockPos> solid) {
        if (solid.isEmpty()) return List.of();
        Set<BlockPos> set = new HashSet<>(solid);
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos p : solid) {
            int missingAxes = 0;
            if (!set.contains(p.offset(1, 0, 0)) || !set.contains(p.offset(-1, 0, 0))) missingAxes++;
            if (!set.contains(p.offset(0, 1, 0)) || !set.contains(p.offset(0, -1, 0))) missingAxes++;
            if (!set.contains(p.offset(0, 0, 1)) || !set.contains(p.offset(0, 0, -1))) missingAxes++;
            if (missingAxes >= 2) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * 圆柱框架：保留圆周侧壁薄壳（水平方向至少一侧无邻格、y 方向两侧都有邻格），
     * 即去掉顶面与底面后的侧壁一圈。
     */
    private static List<BlockPos> sideWallOf(List<BlockPos> solid) {
        if (solid.isEmpty()) return List.of();
        Set<BlockPos> set = new HashSet<>(solid);
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos p : solid) {
            // 圆周边界：水平方向至少一侧缺失
            if (!set.contains(p.offset(1, 0, 0)) || !set.contains(p.offset(-1, 0, 0))
                    || !set.contains(p.offset(0, 0, 1)) || !set.contains(p.offset(0, 0, -1))) {
                boolean top = set.contains(p.offset(0, 1, 0));
                boolean bottom = set.contains(p.offset(0, -1, 0));
                if (top && bottom) {
                    result.add(p);
                    if (result.size() >= MAX_POSITIONS) {
                        break;
                    }
                }
            }
        }
        return result;
    }

    /**
     * 球框架：球壳中位于三个正交主平面（x=球心x / z=球心z / y=球心y）上的格子，
     * 构成赤道大圆环 + 两条子午线环（外轮廓线框）。
     */
    private static List<BlockPos> sphereFrameOf(List<BlockPos> hollow, BlockPos center, int cy) {
        int cx = center.getX();
        int cz = center.getZ();
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos p : hollow) {
            if (p.getX() == cx || p.getZ() == cz || p.getY() == cy) {
                result.add(p);
            }
        }
        return result;
    }

    // ==================== 几何工具 ====================

    /** 圆面/圆柱半径：圆心到当前悬停点的水平距离，端点缺失时返回 -1。 */
    private static int circleRadius(ShapeInput in) {
        if (in.start() == null || in.hover() == null) return -1;
        return Math.min(MAX_RADIUS, (int) Math.round(Math.sqrt(
                Math.pow(in.hover().getX() - in.start().getX(), 2)
                        + Math.pow(in.hover().getZ() - in.start().getZ(), 2))));
    }

    /** 走向线垂直水平方向的扩展主轴：|dz|≥|dx| 时沿 X，否则沿 Z。 */
    private static boolean horizontalExtendAxis(ShapeInput in) {
        return Math.abs(in.hover().getZ() - in.start().getZ())
                >= Math.abs(in.hover().getX() - in.start().getX());
    }

    /**
     * 整数 3D DDA 插值线段：返回从 a 到 b 沿最长轴均匀分布的连续方块（含两端）。
     * 使用整数误差累积替代浮点步进，避免超长线段下的精度漂移。
     * 长度超过 {@link #MAX_POSITIONS} 时提前截断，防止超大形状耗尽内存。
     */
    public static List<BlockPos> lineBetween(BlockPos a, BlockPos b) {
        List<BlockPos> result = new ArrayList<>();
        if (a == null || b == null) return result;
        int x0 = a.getX(), y0 = a.getY(), z0 = a.getZ();
        int x1 = b.getX(), y1 = b.getY(), z1 = b.getZ();
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int dz = Math.abs(z1 - z0);
        int steps = Math.max(dx, Math.max(dy, dz));
        if (steps == 0) {
            result.add(a.immutable());
            return result;
        }
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int errX = steps / 2;
        int errY = steps / 2;
        int errZ = steps / 2;
        int x = x0, y = y0, z = z0;
        result.add(a.immutable());
        for (int i = 1; i <= steps; i++) {
            errX -= dx;
            if (errX < 0) { errX += steps; x += sx; }
            errY -= dy;
            if (errY < 0) { errY += steps; y += sy; }
            errZ -= dz;
            if (errZ < 0) { errZ += steps; z += sz; }
            result.add(new BlockPos(x, y, z));
            if (result.size() >= MAX_POSITIONS) {
                break;
            }
        }
        return result;
    }
}
