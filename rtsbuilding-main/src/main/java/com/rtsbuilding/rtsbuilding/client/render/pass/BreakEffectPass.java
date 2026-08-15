package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.GhostRingBuffer;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.RenderPipeline;
import com.rtsbuilding.rtsbuilding.client.render.RingBufferHolder;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.render.util.RtsAlphaVertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * 破坏成功特效：消费 {@link GhostRingBuffer#BREAK_EFFECTS} 中由
 * {@code RtsClientNetworkHandlers.handleBreakAnimation} 写入的破坏位置，
 * 渲染"方块瓦解"——被破坏方块的真实模型从 1 缩小到 0（shrink，参考 BuildingGadgets2），
 * 同时若干带真实纹理的小碎块从方块中心向确定性方向飘散并上升、透明度随时间淡出。
 *
 * <p>效果参考 BuildingGadgets2 的 {@code RenderBlock} shrink 动画 + {@code ItemFlowParticle}
 * 粒子飞出：方块缩小的同时，方块碎粒（物品粒子）沿斜上方向飞离中心。
 * 非完整模型方块（流体等）回退为半透明色块。
 *
 * <p>每帧渲染后调用 {@link GhostRingBuffer#prune} 清理过期条目；
 * 写入侧已按 tick 限流、缓冲满时丢弃新条目，保证已入缓冲动画完整播放。</p>
 */
public final class BreakEffectPass implements RenderPass {

    /** 方块缩小时长（毫秒）：从 1 缩小到 0。 */
    private static final long SHRINK_DURATION_MS = 400L;

    /** 碎片粒子数量（每方块）。 */
    private static final int FRAGMENT_COUNT = 4;

    /** 总动画时长（毫秒）：方块缩小与碎片飘散并行进行。 */
    private static final long DURATION_MS = 600L;

    /** 碎片水平飘散半径（格）。 */
    private static final double SPREAD = 1.8D;

    /** 碎片上升高度（格）。 */
    private static final double RISE_HEIGHT = 2.0D;

    private static final CornerBracketRenderer.Rgb color = new CornerBracketRenderer.Rgb();

    @Override
    public boolean shouldRender(Minecraft mc) {
        if (!(mc.screen instanceof BuilderScreen)) return false;
        return !RingBufferHolder.BREAK_EFFECTS.isEmpty();
    }

    @Override
    public void render(Minecraft mc, RenderPass.BufferAllocator alloc, PoseStack poseStack,
                       float partialTick, int frameIndex) {
        if (mc.level == null) return;
        long now = System.currentTimeMillis();
        GhostRingBuffer buffer = RingBufferHolder.BREAK_EFFECTS;
        // 补入等待队列中的破坏动画（环形区释放出空间后生效），保证批量破坏动画不丢失
        buffer.drainPending();

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();

        buffer.forEach((key, state, addedAtMs) -> {
            long age = now - addedAtMs;
            if (age < 0 || age >= DURATION_MS) return;
            BlockPos p = BlockPos.of(key);

            // 方块缩小 1 → 0（轻微缓动让收尾自然）
            double shrinkT = Math.min(1.0, age / (double) SHRINK_DURATION_MS);
            float scale = (float) Math.pow(1.0 - shrinkT, 1.5D);
            if (scale > 0.02F) {
                // 缩小时段内，scale 过小后透明度随之淡出，避免方块消失突兀
                float alpha = scale < 0.4F ? scale / 0.4F : 1.0F;
                renderScaledBlock(mc.level, p, state, scale, alpha, dispatcher, poseStack, alloc);
            }

            // 碎片粒子向四周飞出 + 上升
            renderFragments(mc.level, p, state, age / (double) DURATION_MS, key, dispatcher, poseStack, alloc);
        });

        buffer.prune(now, DURATION_MS);
    }

    /** 以方块中心按 scale 缩放渲染真实方块模型（shrink）；非完整模型方块回退为半透明色块。 */
    private static void renderScaledBlock(Level level, BlockPos pos, BlockState state, float scale, float alpha,
                                          BlockRenderDispatcher dispatcher, PoseStack poseStack, BufferAllocator alloc) {
        if (state == null || state.isAir()) return;
        if (state.getRenderShape() != RenderShape.MODEL) {
            renderFallbackCube(level, pos, state, scale, alpha, poseStack, alloc);
            return;
        }
        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        poseStack.translate((1.0F - scale) / 2.0F, (1.0F - scale) / 2.0F, (1.0F - scale) / 2.0F);
        poseStack.scale(scale, scale, scale);
        int light = LevelRenderer.getLightColor(level, pos);
        MultiBufferSource src = rt -> new RtsAlphaVertexConsumer(alloc.blockSource().getBuffer(rt), alpha);
        dispatcher.renderSingleBlock(state, poseStack, src, light, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, RenderPipeline.BLOCK_ANIMATION);
        poseStack.popPose();
    }

    /** 碎片粒子：带真实方块纹理的小碎块从方块中心向确定性方向飘散并上升，透明度随时间淡出。 */
    private static void renderFragments(Level level, BlockPos pos, BlockState state, double t,
                                        long key, BlockRenderDispatcher dispatcher,
                                        PoseStack poseStack, BufferAllocator alloc) {
        if (state == null || state.isAir()) return;
        float alpha = (float) (0.9F * (1.0 - t));
        if (alpha <= 0.01F) return;

        // 基于位置确定性散列，让各碎片方向稳定不随机抖动
        long seed = key * 0x9E3779B97F4A7C15L;
        for (int i = 0; i < FRAGMENT_COUNT; i++) {
            double ang = ((seed >>> ((i % 8) * 8)) & 0xFFFF) / 65535.0 * Math.PI * 2.0;
            double dist = t * SPREAD * (0.4 + 0.6 * (i % 2) / 2.0);
            double yOff = t * RISE_HEIGHT + 0.15 * (i % 2);
            float size = 0.22F + 0.08F * (i % 2);

            double cx = pos.getX() + 0.5 + Math.cos(ang) * dist;
            double cz = pos.getZ() + 0.5 + Math.sin(ang) * dist;
            double cy = pos.getY() + 0.5 + yOff;

            renderScaledBlockAt(level, state, cx, cy, cz, size, alpha, dispatcher, poseStack, alloc);
        }
    }

    /** 在任意中心坐标按 size 缩放渲染真实方块模型（碎片粒子）；非完整模型方块回退为半透明色块。 */
    private static void renderScaledBlockAt(Level level, BlockState state, double cx, double cy, double cz,
                                            float size, float alpha, BlockRenderDispatcher dispatcher,
                                            PoseStack poseStack, BufferAllocator alloc) {
        if (state.getRenderShape() != RenderShape.MODEL) {
            color.update(colorFor(state, level, BlockPos.containing(cx, cy, cz)));
            float r = color.r, g = color.g, b = color.b;
            double half = 0.5D * size;
            CornerBracketRenderer.renderFilledFaces(alloc.brackets(), poseStack,
                    cx - half, cy - half, cz - half,
                    cx + half, cy + half, cz + half,
                    r, g, b, alpha);
            return;
        }
        poseStack.pushPose();
        poseStack.translate(cx, cy, cz);
        poseStack.translate(-size / 2.0F, -size / 2.0F, -size / 2.0F);
        poseStack.scale(size, size, size);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(cx, cy, cz));
        MultiBufferSource src = rt -> new RtsAlphaVertexConsumer(alloc.blockSource().getBuffer(rt), alpha);
        dispatcher.renderSingleBlock(state, poseStack, src, light, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, RenderPipeline.BLOCK_ANIMATION);
        poseStack.popPose();
    }

    /** 流体/特殊方块回退：以中心缩放的半透明色块。 */
    private static void renderFallbackCube(Level level, BlockPos pos, BlockState state, float scale, float alpha,
                                           PoseStack poseStack, BufferAllocator alloc) {
        color.update(colorFor(state, level, pos));
        float r = color.r, g = color.g, b = color.b;
        double half = 0.5D * scale;
        double cx = pos.getX() + 0.5D, cy = pos.getY() + 0.5D, cz = pos.getZ() + 0.5D;
        CornerBracketRenderer.renderFilledFaces(alloc.brackets(), poseStack,
                cx - half, cy - half, cz - half,
                cx + half, cy + half, cz + half,
                r, g, b, alpha);
    }

    /** 从方块状态的地图色提取 ARGB 颜色；空气/未知回退浅灰。 */
    private static int colorFor(BlockState state, Level level, BlockPos pos) {
        if (state == null || state.isAir()) return 0xFFCCCCCC;
        var mapColor = state.getMapColor(level, pos);
        if (mapColor == null) return 0xFFCCCCCC;
        return 0xFF000000 | mapColor.col;
    }

    @Override
    public int requiredBuffers() {
        return 4 | 8;
    }
}
