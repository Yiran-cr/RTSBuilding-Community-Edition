package com.rtsbuilding.rtsbuilding.client.rtsbuild.shape;

import com.rtsbuilding.rtsbuilding.client.render.pass.LineBrushSelector;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 形状填充模式（实心/空心/框架）几何验证测试。
 */
class BuildShapeFillModeTest {

    /** 3×3×3 体：走向线 X 方向 3 格，竖直 up=3、水平 width=3（沿 Z）。 */
    private static ShapeInput boxInput(FillMode fill) {
        return new ShapeInput(new BlockPos(0, 0, 0), new BlockPos(2, 0, 0),
                0, 0, 3, 0, 3, 0, 1, false, fill);
    }

    @Test
    void boxSolidIsFullVolume() {
        List<BlockPos> pos = BuildShape.SOLID.compute(boxInput(FillMode.SOLID));
        assertEquals(27, pos.size());
    }

    @Test
    void boxHollowRemovesCore() {
        List<BlockPos> pos = BuildShape.SOLID.compute(boxInput(FillMode.HOLLOW));
        assertEquals(26, pos.size());
        assertFalse(pos.contains(new BlockPos(1, 1, 1)));
    }

    @Test
    void boxFrameKeepsTwelveEdges() {
        // 3×3×3 框架 = 8 角 + 每条棱 1 个中间格 × 12 = 20
        List<BlockPos> pos = BuildShape.SOLID.compute(boxInput(FillMode.FRAME));
        assertEquals(20, pos.size());
    }

    @Test
    void cylinderFrameIsSideWallOnly() {
        // 半径 2、高 3（y=0..2）的圆柱：框架应只剩 y=1 的侧壁一圈
        ShapeInput in = new ShapeInput(new BlockPos(0, 0, 0), new BlockPos(2, 0, 0),
                0, 0, 3, 0, 1, 0, 1, false, FillMode.FRAME);
        List<BlockPos> pos = BuildShape.CIRCLE.compute(in);
        assertFalse(pos.isEmpty());
        for (BlockPos p : pos) {
            assertEquals(1, p.getY(), "侧壁应位于中间层: " + p);
            int d2 = p.getX() * p.getX() + p.getZ() * p.getZ();
            assertTrue(d2 <= 4 && d2 > 1, "格应在半径 2 的圆周上: " + p);
        }
    }

    @Test
    void sphereFrameLiesOnOrthogonalPlanes() {
        // 半径 2、球心 (0,5,0)：框架 = 球壳落在 x=0 / z=0 / y=5 三个主平面上
        ShapeInput in = new ShapeInput(new BlockPos(0, 5, 0), null,
                0, 0, 1, 0, 1, 0, 2, false, FillMode.FRAME);
        List<BlockPos> pos = BuildShape.SPHERE.compute(in);
        assertFalse(pos.isEmpty());
        assertTrue(pos.contains(new BlockPos(2, 5, 0)), "赤道环应包含 (2,5,0)");
        assertTrue(pos.contains(new BlockPos(0, 5, 2)), "子午环应包含 (0,5,2)");
        assertTrue(pos.contains(new BlockPos(0, 3, 0)), "子午环应包含 (0,3,0)");
        for (BlockPos p : pos) {
            int dx = p.getX(), dy = p.getY() - 5, dz = p.getZ();
            assertTrue(dx == 0 || dz == 0 || dy == 0, "框架球应落在主平面上: " + p);
        }
    }

    @Test
    void lineConnectedIsOrthogonalNoDiagonal() {
        // 连接模式：从 (0,0,0) 到 (1,1,0)，必须经直角拐点 (1,0,0)，禁止对角相邻
        ShapeInput in = new ShapeInput(new BlockPos(0, 0, 0), new BlockPos(1, 1, 0),
                0, 0, 1, 0, 1, 0, 1, false, FillMode.CONNECTED);
        List<BlockPos> pos = BuildShape.LINE.compute(in);
        assertEquals(3, pos.size());
        assertTrue(pos.contains(new BlockPos(0, 0, 0)));
        assertTrue(pos.contains(new BlockPos(1, 0, 0)), "应包含直角拐点 (1,0,0)");
        assertTrue(pos.contains(new BlockPos(1, 1, 0)));
    }

    @Test
    void lineConnectedIsPipeLikeAndFaceAdjacent() {
        // 连接模式：以断点（3D DDA）路径为基础，把斜向断开的相邻对补块连接——
        // 结果所有相邻格曼哈顿距离恒为 1（共享面），且路径走向与断点一致
        ShapeInput in = new ShapeInput(new BlockPos(0, 0, 0), new BlockPos(2, 3, 0),
                0, 0, 1, 0, 1, 0, 1, false, FillMode.CONNECTED);
        List<BlockPos> pos = BuildShape.LINE.compute(in);
        assertEquals(6, pos.size());
        // 断点 DDA 斜向段 (0,0,0)->(1,1,0) 与 (1,2,0)->(2,3,0) 应被补块连接
        assertTrue(pos.contains(new BlockPos(1, 0, 0)), "应补连接块 (1,0,0)");
        assertTrue(pos.contains(new BlockPos(2, 2, 0)), "应补连接块 (2,2,0)");
        // 每对相邻格必须共享面
        for (int i = 1; i < pos.size(); i++) {
            BlockPos prev = pos.get(i - 1);
            BlockPos cur = pos.get(i);
            int manhattan = Math.abs(cur.getX() - prev.getX())
                    + Math.abs(cur.getY() - prev.getY())
                    + Math.abs(cur.getZ() - prev.getZ());
            assertEquals(1, manhattan, "相邻格必须共享面连接: " + prev + " -> " + cur);
        }
        assertEquals(new BlockPos(2, 3, 0), pos.get(pos.size() - 1));
    }

    @Test
    void lineSeparatedIsDiagonalDDA() {
        // 断点模式：原 DDA 行为，(0,0,0) 到 (1,1,0) 只有 2 格对角相连
        ShapeInput in = new ShapeInput(new BlockPos(0, 0, 0), new BlockPos(1, 1, 0),
                0, 0, 1, 0, 1, 0, 1, false, FillMode.SEPARATED);
        List<BlockPos> pos = BuildShape.LINE.compute(in);
        assertEquals(2, pos.size());
        assertFalse(pos.contains(new BlockPos(1, 0, 0)), "断点模式不应包含直角拐点");
    }

    @Test
    void wallFrameIsRectangleBorder() {
        // 3×3 墙（走向线 X 3 格、向上 3 格）：框架 = 两端竖边 + 顶/底边，共 8 格
        ShapeInput in = new ShapeInput(new BlockPos(0, 0, 0), new BlockPos(2, 0, 0),
                0, 0, 3, 0, 1, 0, 1, false, FillMode.FRAME);
        List<BlockPos> pos = BuildShape.WALL.compute(in);
        assertEquals(8, pos.size());
        assertTrue(pos.contains(new BlockPos(1, 2, 0)), "顶边应包含 (1,2,0)");
        assertTrue(pos.contains(new BlockPos(1, 0, 0)), "底边应包含 (1,0,0)");
        assertFalse(pos.contains(new BlockPos(1, 1, 0)), "面中央应被去除");
    }

    @Test
    void faceFrameIsRectangleBorder() {
        // 3×3 面（走向线 X 3 格、沿 Z 扩展 3 格）：框架 = 两端列 + 两侧行，共 8 格
        ShapeInput in = new ShapeInput(new BlockPos(0, 0, 0), new BlockPos(2, 0, 0),
                0, 0, 1, 0, 3, 0, 1, false, FillMode.FRAME);
        List<BlockPos> pos = BuildShape.FACE.compute(in);
        assertEquals(8, pos.size());
        assertTrue(pos.contains(new BlockPos(1, 0, 2)), "远侧边应包含 (1,0,2)");
        assertTrue(pos.contains(new BlockPos(1, 0, 0)), "近侧边应包含 (1,0,0)");
        assertFalse(pos.contains(new BlockPos(1, 0, 1)), "面中央应被去除");
    }

    @Test
    void modeSubsetsPerShape() {
        FillMode[] solid3 = FillMode.modesFor(BuildShape.SOLID);
        assertArrayEquals(new FillMode[]{FillMode.SOLID, FillMode.HOLLOW, FillMode.FRAME}, solid3);
        FillMode[] wall = FillMode.modesFor(BuildShape.WALL);
        assertArrayEquals(new FillMode[]{FillMode.SOLID, FillMode.FRAME}, wall);
        FillMode[] line = FillMode.modesFor(BuildShape.LINE);
        assertArrayEquals(new FillMode[]{FillMode.SEPARATED, FillMode.CONNECTED}, line);
    }

    @Test
    void lineDefaultsToSeparated() {
        LineBrushSelector brush = new LineBrushSelector();
        assertEquals(FillMode.SEPARATED, brush.getFillModeFor(BuildShape.LINE), "线默认应为断点");
        assertEquals(FillMode.SOLID, brush.getFillModeFor(BuildShape.SOLID), "体默认应为实心");
    }
}
