package com.rtsbuilding.rtsbuilding.client.render.util;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 高效轮廓边提取器：从方块位置集合提取<strong>外表面区域边界</strong>，
 * 再按主轴合并共线连续单位边为长线段，供线框渲染使用。
 *
 * <p><b>性能背景：</b>逐方块渲染完整角支架线框时每方块 12 段 × 6 quad × 双层
 * ≈ 576 顶点；形状画笔位置可达 {@code NetworkConstants.MAX_POSITIONS = 32768}，
 * 满额约 1900 万顶点/帧导致卡顿。本类把渲染顶点量从 O(n) 降到 O(轮廓边数)。</p>
 *
 * <p><b>算法（面级区域边界）：</b>对每个方块检查 6 个面，邻居缺失的面为外表面
 * 面单元（1×1 正方形）；同一平面的相邻外表面面单元连通成一个表面区域，仅该
 * 区域的<strong>边界边</strong>进入轮廓（避免底面/大平面上渲染内部网格线）；
 * 随后按主轴合并共线单位边为长线段。</p>
 */
public final class OutlineEdgeExtractor {

    private static final int AXIS_X = 0;
    private static final int AXIS_Y = 1;
    private static final int AXIS_Z = 2;

    /** 坐标偏移：世界坐标 + OFF 后为非负，20 位可表示 ±524288 格。 */
    private static final long OFF = 0x80000L;

    private static final long MASK = (1L << 20) - 1;

    private OutlineEdgeExtractor() {
    }

    /**
     * 提取位置集合的外轮廓边线（含共线合并）。
     *
     * @param positions 方块位置集合（可为空）
     * @return 外轮廓边线段列表；空输入返回空列表
     */
    public static List<UltimineBlockMerger.EdgeLine> extractEdges(Collection<BlockPos> positions) {
        if (positions.isEmpty()) return List.of();
        Set<Long> posSet = new HashSet<>(positions.size());
        for (BlockPos p : positions) posSet.add(p.asLong());

        // 1. 收集 6 个方向的外表面面单元（2D 格子集合）。
        //    面键 faceKey = (axis, 法线正负, 平面坐标)；格子键 = 两个切线坐标打包。
        Map<Long, Set<Long>> faces = new HashMap<>();
        for (long lp : posSet) {
            BlockPos p = BlockPos.of(lp);
            int x = p.getX(), y = p.getY(), z = p.getZ();
            if (!posSet.contains(BlockPos.asLong(x + 1, y, z))) addFace(faces, AXIS_X, 1, x + 1, y, z);
            if (!posSet.contains(BlockPos.asLong(x - 1, y, z))) addFace(faces, AXIS_X, 0, x, y, z);
            if (!posSet.contains(BlockPos.asLong(x, y + 1, z))) addFace(faces, AXIS_Y, 1, y + 1, x, z);
            if (!posSet.contains(BlockPos.asLong(x, y - 1, z))) addFace(faces, AXIS_Y, 0, y, x, z);
            if (!posSet.contains(BlockPos.asLong(x, y, z + 1))) addFace(faces, AXIS_Z, 1, z + 1, x, y);
            if (!posSet.contains(BlockPos.asLong(x, y, z - 1))) addFace(faces, AXIS_Z, 0, z, x, y);
        }
        if (faces.isEmpty()) return List.of();

        // 2. 对每个表面区域提取边界单位边（相邻格子缺失的边）。
        List<UltimineBlockMerger.EdgeLine> unit = new ArrayList<>();
        for (Map.Entry<Long, Set<Long>> e : faces.entrySet()) {
            long fk = e.getKey();
            int axis = (int) (fk >> 41);
            int plane = (int) ((fk & MASK) - OFF);
            Set<Long> cells = e.getValue();
            for (long gk : cells) {
                int a = (int) (((gk >> 20) & MASK) - OFF);
                int b = (int) ((gk & MASK) - OFF);
                boolean amin = cells.contains(pack2(a - 1, b));
                boolean apos = cells.contains(pack2(a + 1, b));
                boolean bmin = cells.contains(pack2(a, b - 1));
                boolean bpos = cells.contains(pack2(a, b + 1));
                // 格子 (a, b) 的 4 条边：a 方向缺失沿切线 b，b 方向缺失沿切线 a
                if (axis == AXIS_X) {
                    // 面在 x=plane，格子 (y= a, z= b)
                    if (!amin) unit.add(new UltimineBlockMerger.EdgeLine(plane, a, b, plane, a, b + 1));
                    if (!apos) unit.add(new UltimineBlockMerger.EdgeLine(plane, a + 1, b, plane, a + 1, b + 1));
                    if (!bmin) unit.add(new UltimineBlockMerger.EdgeLine(plane, a, b, plane, a + 1, b));
                    if (!bpos) unit.add(new UltimineBlockMerger.EdgeLine(plane, a, b + 1, plane, a + 1, b + 1));
                } else if (axis == AXIS_Y) {
                    // 面在 y=plane，格子 (x= a, z= b)
                    if (!amin) unit.add(new UltimineBlockMerger.EdgeLine(a, plane, b, a, plane, b + 1));
                    if (!apos) unit.add(new UltimineBlockMerger.EdgeLine(a + 1, plane, b, a + 1, plane, b + 1));
                    if (!bmin) unit.add(new UltimineBlockMerger.EdgeLine(a, plane, b, a + 1, plane, b));
                    if (!bpos) unit.add(new UltimineBlockMerger.EdgeLine(a, plane, b + 1, a + 1, plane, b + 1));
                } else {
                    // 面在 z=plane，格子 (x= a, y= b)
                    if (!amin) unit.add(new UltimineBlockMerger.EdgeLine(a, b, plane, a, b + 1, plane));
                    if (!apos) unit.add(new UltimineBlockMerger.EdgeLine(a + 1, b, plane, a + 1, b + 1, plane));
                    if (!bmin) unit.add(new UltimineBlockMerger.EdgeLine(a, b, plane, a + 1, b, plane));
                    if (!bpos) unit.add(new UltimineBlockMerger.EdgeLine(a, b + 1, plane, a + 1, b + 1, plane));
                }
            }
        }
        if (unit.isEmpty()) return List.of();
        return mergeUnitEdges(unit);
    }

    /** 记录一个外表面面单元：法线方向 (axis, signIdx) 的 plane 平面上，切线格子 (a, b)。 */
    private static void addFace(Map<Long, Set<Long>> faces, int axis, int signIdx, int plane, int a, int b) {
        long fk = ((long) axis << 41) | ((long) signIdx << 40) | ((plane + OFF) & MASK);
        long gk = pack2(a, b);
        faces.computeIfAbsent(fk, k -> new HashSet<>()).add(gk);
    }

    /** 按「主轴 + 两个固定坐标」分组，组内对变量坐标排序并合并连续单位边为长线段。 */
    private static List<UltimineBlockMerger.EdgeLine> mergeUnitEdges(List<UltimineBlockMerger.EdgeLine> unit) {
        Map<Long, List<Integer>> groups = new HashMap<>();
        for (UltimineBlockMerger.EdgeLine e : unit) {
            int axis, c1, c2, v;
            if (e.y1() == e.y2() && e.z1() == e.z2()) {
                axis = AXIS_X;
                c1 = (int) e.y1();
                c2 = (int) e.z1();
                v = (int) e.x1();
            } else if (e.x1() == e.x2() && e.z1() == e.z2()) {
                axis = AXIS_Y;
                c1 = (int) e.x1();
                c2 = (int) e.z1();
                v = (int) e.y1();
            } else {
                axis = AXIS_Z;
                c1 = (int) e.x1();
                c2 = (int) e.y1();
                v = (int) e.z1();
            }
            long gk = ((long) axis << 40) | (((c1 + OFF) & MASK) << 20) | ((c2 + OFF) & MASK);
            groups.computeIfAbsent(gk, k -> new ArrayList<>()).add(v);
        }

        List<UltimineBlockMerger.EdgeLine> edges = new ArrayList<>();
        for (Map.Entry<Long, List<Integer>> e : groups.entrySet()) {
            List<Integer> vars = e.getValue();
            vars.sort(Integer::compareTo);
            int axis = (int) (e.getKey() >> 40);
            int c1 = (int) (((e.getKey() >> 20) & MASK) - OFF);
            int c2 = (int) ((e.getKey() & MASK) - OFF);
            int start = vars.get(0);
            int prev = start;
            for (int i = 1; i < vars.size(); i++) {
                int v = vars.get(i);
                if (v == prev) {
                    // 同一单位边被相邻外表面面单元重复记录：跳过，保持起点不变
                    continue;
                } else if (v == prev + 1) {
                    prev = v;
                } else {
                    emitEdge(edges, axis, c1, c2, start, prev);
                    start = prev = v;
                }
            }
            emitEdge(edges, axis, c1, c2, start, prev);
        }
        return edges;
    }

    /** 由合并段起始/结束单位边起点生成世界坐标线段（覆盖 [s1, s2+1]）。 */
    private static void emitEdge(List<UltimineBlockMerger.EdgeLine> edges, int axis, int c1, int c2, int s1, int s2) {
        if (axis == AXIS_X) {
            edges.add(new UltimineBlockMerger.EdgeLine(s1, c1, c2, s2 + 1, c1, c2));
        } else if (axis == AXIS_Y) {
            edges.add(new UltimineBlockMerger.EdgeLine(c1, s1, c2, c1, s2 + 1, c2));
        } else {
            edges.add(new UltimineBlockMerger.EdgeLine(c1, c2, s1, c1, c2, s2 + 1));
        }
    }

    /** 两个切线坐标打包（20 位 each）。 */
    private static long pack2(int a, int b) {
        return (((a + OFF) & MASK) << 20) | ((b + OFF) & MASK);
    }
}
