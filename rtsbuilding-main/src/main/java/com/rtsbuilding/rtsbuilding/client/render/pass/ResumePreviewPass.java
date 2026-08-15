package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.presentation.plugin.resume.ResumeWorkflowState;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.render.util.UltimineBlockMerger;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 工作流恢复预览线框 pass：
 * <ul>
 *   <li>剩余待放置位置 → 绿色线框。</li>
 *   <li>冲突（被非空气不同方块占据）位置 → 橙色线框。</li>
 * </ul>
 * 数据来自服务端恢复扫描结果 {@link ResumeWorkflowState}。
 */
public final class ResumePreviewPass implements RenderPass {

    private static final float EDGE_ALPHA = 1.0F;
    private static final BlockPos[] FACE_OFFSETS = {
            new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
            new BlockPos(0, 1, 0), new BlockPos(0, -1, 0),
            new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)
    };

    @Override
    public boolean shouldRender(Minecraft mc) {
        return !ResumeWorkflowState.getAll().isEmpty();
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex) {
        if (mc.level == null) return;
        // 每个工作流独立渲染：剩余绿线框 + 冲突橙线框
        for (var payload : ResumeWorkflowState.getAll()) {
            if (payload.remainingPositions().isEmpty() && payload.conflictPositions().isEmpty()) continue;
            renderPositions(alloc, poseStack, payload.remainingPositions(), 0.35F, 1.0F, 0.35F);
            renderPositions(alloc, poseStack, payload.conflictPositions(), 1.0F, 0.60F, 0.10F);
        }
    }

    private void renderPositions(BufferAllocator alloc, PoseStack poseStack,
                                 List<Long> longs, float r, float g, float b) {
        if (longs.isEmpty()) return;
        List<BlockPos> blocks = new ArrayList<>(longs.size());
        for (long l : longs) {
            blocks.add(BlockPos.of(l));
        }
        List<BlockPos> outer = filterOuterBlocks(blocks);
        List<UltimineBlockMerger.EdgeLine> edges = UltimineBlockMerger.getEdgeLines(outer);
        if (edges.isEmpty()) return;

        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (BlockPos pos : outer) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX() + 1);
            maxY = Math.max(maxY, pos.getY() + 1);
            maxZ = Math.max(maxZ, pos.getZ() + 1);
        }
        var camera = Minecraft.getInstance().getCameraEntity();
        Vec3 camPos = camera != null ? camera.getEyePosition(0.0F) : Vec3.ZERO;
        double distance = camPos.distanceTo(new Vec3(
                (minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5));

        CornerBracketRenderer.renderEdges(poseStack, alloc.brackets(), edges, r, g, b, EDGE_ALPHA, distance);
        CornerBracketRenderer.renderEdges(poseStack, alloc.noDepth(), edges, r, g, b,
                CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA, distance);
    }

    private static List<BlockPos> filterOuterBlocks(List<BlockPos> blocks) {
        Set<BlockPos> all = new HashSet<>(blocks);
        List<BlockPos> outer = new ArrayList<>();
        for (BlockPos pos : blocks) {
            boolean isOuter = false;
            for (BlockPos off : FACE_OFFSETS) {
                if (!all.contains(pos.offset(off))) {
                    isOuter = true;
                    break;
                }
            }
            if (isOuter) outer.add(pos);
        }
        return outer;
    }

    @Override
    public int requiredBuffers() {
        return 1 | 4; // lines + noDepth
    }
}
