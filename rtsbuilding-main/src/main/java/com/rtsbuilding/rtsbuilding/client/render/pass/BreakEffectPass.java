package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.GhostRingBuffer;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.RingBufferHolder;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 破坏成功特效：消费 {@link GhostRingBuffer#BREAK_EFFECTS} 中由
 * {@code RtsClientNetworkHandlers.handleBreakAnimation} 写入的破坏位置，
 * 渲染"方块上飘"——少量<strong>实心半透明小碎块</strong>（取自被破坏方块的地图色）
 * 从方块原位置向确定性方向飘散并上升，透明度随时间淡出。
 *
 * <p>每帧渲染后调用 {@link GhostRingBuffer#prune} 清理过期条目；
 * 写入侧已按 tick 限流、缓冲满时丢弃新条目，保证已入缓冲动画完整播放。</p>
 */
public final class BreakEffectPass implements RenderPass {

    /** 每个方块产生的碎块数量。 */
    private static final int FRAGMENT_COUNT = 3;

    /** 飘散动画时长（毫秒）。 */
    private static final long DURATION_MS = 600L;

    /** 碎块整体上升高度（格）。 */
    private static final double RISE_HEIGHT = 3.0;

    /** 碎块水平飘散半径（格）。 */
    private static final double SPREAD = 2.0;

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

        buffer.forEach((key, state, addedAtMs) -> {
            long age = now - addedAtMs;
            if (age < 0 || age >= DURATION_MS) return;
            double t = age / (double) DURATION_MS;
            BlockPos p = BlockPos.of(key);

            color.update(colorFor(state, mc.level, p));
            float r = color.r, g = color.g, b = color.b;
            float alpha = (float) (0.85F * (1.0 - t));

            // 基于位置确定性散列，让各碎块方向稳定不随机抖动
            long seed = key * 0x9E3779B97F4A7C15L;
            for (int i = 0; i < FRAGMENT_COUNT; i++) {
                double ang = ((seed >>> ((i % 8) * 8)) & 0xFFFF) / 65535.0 * Math.PI * 2.0;
                double dist = t * SPREAD * (0.4 + 0.6 * (i % 2) / 2.0);
                double yOff = t * RISE_HEIGHT + 0.15 * (i % 2);
                double size = 0.28 + 0.08 * (i % 2);

                double cx = p.getX() + 0.5 + Math.cos(ang) * dist;
                double cz = p.getZ() + 0.5 + Math.sin(ang) * dist;
                double cy = p.getY() + 0.5 + yOff;

                CornerBracketRenderer.renderFilledFaces(alloc.brackets(), poseStack,
                        cx - size / 2.0, cy - size / 2.0, cz - size / 2.0,
                        cx + size / 2.0, cy + size / 2.0, cz + size / 2.0,
                        r, g, b, alpha);
            }
        });

        buffer.prune(now, DURATION_MS);
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
        return 4;
    }
}
