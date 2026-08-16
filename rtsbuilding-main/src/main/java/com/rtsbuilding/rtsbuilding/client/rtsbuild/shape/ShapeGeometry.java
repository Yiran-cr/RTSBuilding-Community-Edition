package com.rtsbuilding.rtsbuilding.client.rtsbuild.shape;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 建造形状的纯几何计算（阶段四 4.3 拆分）。
 *
 * <p>从 {@link BuildShape} 抽离的纯静态几何方法：输入 {@link ShapeInput} 输出方块位置列表，
 * 不依赖形状枚举的实例状态/交互阶段。每个方法都是纯函数，可独立单测。
 * 上限常量沿用 {@link BuildShape#MAX_POSITIONS} / {@link BuildShape#MAX_RADIUS}。
 */
public final class ShapeGeometry {

    private ShapeGeometry() {}

    // ==================== 线 ====================

    /** 线：起点到终点（含两端高度偏移）的连续方块列表。 */
    public static List<BlockPos> linePositions(ShapeInput in) {
        if (in.start() == null || in.hover() == null) return List.of();
        BlockPos start = in.effectiveStart();
        BlockPos end = in.effectiveEnd();
        if (end == null) return List.of();
        return lineBetween(start, end);
    }

    /** 线：连接模式（路径方块直角连接，不斜向相连）。 */
    public static List<BlockPos> connectedLinePositions(ShapeInput in) {
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
            if (result.size() >= BuildShape.MAX_POSITIONS) {
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

    // ==================== 墙 / 面 ====================

    /** 墙：实心为完整竖直扩展，框架为矩形四边框。 */
    public static List<BlockPos> wallFillPositions(ShapeInput in) {
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
    public static List<BlockPos> faceFillPositions(ShapeInput in) {
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

    // ==================== 扩展 ====================

    /** 超限保护地向集合添加方块。 */
    private static void addIfRoom(LinkedHashSet<BlockPos> set, BlockPos p) {
        if (set.size() < BuildShape.MAX_POSITIONS) {
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
                if (result.size() >= BuildShape.MAX_POSITIONS) {
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
                if (result.size() >= BuildShape.MAX_POSITIONS) {
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
                    if (result.size() >= BuildShape.MAX_POSITIONS) {
                        return new ArrayList<>(result);
                    }
                }
            }
        }
        return new ArrayList<>(result);
    }

    // ==================== 圆 / 球 ====================

    /** 圆面/圆柱：以圆心（含起点高度偏移）为轴心，dx²+dz²≤r² 判定填充。 */
    public static List<BlockPos> cylinderPositions(ShapeInput in) {
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
                        if (result.size() >= BuildShape.MAX_POSITIONS) {
                            return result;
                        }
                    }
                }
            }
        }
        return result;
    }

    /** 球：以球心（含起点高度偏移）为中心，dx²+dy²+dz²≤r² 判定填充。 */
    public static List<BlockPos> spherePositions(ShapeInput in) {
        if (in.start() == null) return List.of();
        int r = Math.max(1, in.sphereRadius());
        int cy = in.effectiveStart().getY();
        List<BlockPos> result = new ArrayList<>();
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= r * r) {
                        result.add(new BlockPos(in.start().getX() + dx, cy + dy, in.start().getZ() + dz));
                        if (result.size() >= BuildShape.MAX_POSITIONS) {
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
    public static List<BlockPos> solidFillPositions(ShapeInput in) {
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
    public static List<BlockPos> cylinderFillPositions(ShapeInput in) {
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
    public static List<BlockPos> sphereFillPositions(ShapeInput in) {
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
                if (result.size() >= BuildShape.MAX_POSITIONS) {
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
                    if (result.size() >= BuildShape.MAX_POSITIONS) {
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
        return Math.min(BuildShape.MAX_RADIUS, (int) Math.round(Math.sqrt(
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
     * 长度超过 {@link BuildShape#MAX_POSITIONS} 时提前截断，防止超大形状耗尽内存。
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
            if (result.size() >= BuildShape.MAX_POSITIONS) {
                break;
            }
        }
        return result;
    }
}
