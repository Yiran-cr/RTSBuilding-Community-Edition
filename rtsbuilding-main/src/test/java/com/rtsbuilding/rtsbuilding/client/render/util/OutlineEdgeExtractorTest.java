package com.rtsbuilding.rtsbuilding.client.render.util;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OutlineEdgeExtractor} 轮廓提取正确性测试：
 * 单方块/相邻方块/平面均应输出闭合盒体的全部 12 条外轮廓边，
 * 且相邻共线单位边被合并为长线段。
 */
class OutlineEdgeExtractorTest {

    @Test
    void emptyCollectionReturnsEmpty() {
        assertEquals(0, OutlineEdgeExtractor.extractEdges(List.of()).size());
    }

    @Test
    void singleBlockHasTwelveEdges() {
        List<UltimineBlockMerger.EdgeLine> edges =
                OutlineEdgeExtractor.extractEdges(List.of(new BlockPos(0, 0, 0)));
        assertEquals(12, edges.size());
        // 单个方块：全部边长度均为 1
        for (UltimineBlockMerger.EdgeLine e : edges) {
            assertEquals(1.0, length(e), 1.0e-6);
        }
    }

    @Test
    void twoAdjacentBlocksMergeColinearEdges() {
        List<BlockPos> pos = List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0));
        List<UltimineBlockMerger.EdgeLine> edges = OutlineEdgeExtractor.extractEdges(pos);
        // 2×1×1 长方体轮廓 = 12 条边
        assertEquals(12, edges.size());
        // 沿 X 方向的 4 条边被合并为长度 2 的长线段
        long mergedX = edges.stream()
                .filter(e -> e.y1() == e.y2() && e.z1() == e.z2()
                        && Math.abs(e.x2() - e.x1()) == 2.0)
                .count();
        assertEquals(4, mergedX);
    }

    @Test
    void flatTwoByTwoIsClosedBox() {
        List<BlockPos> pos = List.of(
                new BlockPos(0, 0, 0), new BlockPos(1, 0, 0),
                new BlockPos(0, 0, 1), new BlockPos(1, 0, 1));
        List<UltimineBlockMerger.EdgeLine> edges = OutlineEdgeExtractor.extractEdges(pos);
        // 2×1×2 长方体轮廓 = 12 条边
        assertEquals(12, edges.size());
    }

    @Test
    void separatedBlocksDoNotMerge() {
        // 两个不相邻方块：各自 12 条边 = 24 条
        List<BlockPos> pos = List.of(new BlockPos(0, 0, 0), new BlockPos(5, 0, 0));
        List<UltimineBlockMerger.EdgeLine> edges = OutlineEdgeExtractor.extractEdges(pos);
        assertEquals(24, edges.size());
    }

    @Test
    void largeSolidVolumeExtractsWithinTime() {
        // 性能冒烟：40×20×25 = 20000 方块实心体积，提取应远快于逐方块角支架渲染
        List<BlockPos> pos = new ArrayList<>(20000);
        for (int x = 0; x < 40; x++) {
            for (int y = 0; y < 20; y++) {
                for (int z = 0; z < 25; z++) {
                    pos.add(new BlockPos(x, y, z));
                }
            }
        }
        assertTimeout(() -> OutlineEdgeExtractor.extractEdges(pos));
    }

    private static double length(UltimineBlockMerger.EdgeLine e) {
        return Math.abs(e.x2() - e.x1()) + Math.abs(e.y2() - e.y1()) + Math.abs(e.z2() - e.z1());
    }

    private static void assertTimeout(Runnable action) {
        long start = System.nanoTime();
        action.run();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(elapsedMs < 2000L, "extraction took " + elapsedMs + " ms");
    }
}
