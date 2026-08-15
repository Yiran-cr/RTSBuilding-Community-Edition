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
import net.minecraft.world.phys.Vec3;

/**
 * 放置成功特效：消费 {@link GhostRingBuffer} 中由
 * {@code RtsClientNetworkHandlers.handlePlaceAnimation} 写入的放置位置，
 * 渲染"方块从天降落建造"——角支架线框方块从目标位置正上方
 * {@link #FALL_HEIGHT} 格处下落到目标位置，落定后快速淡出。
 *
 * <p>线框颜色取自目标方块自身的 {@link MapColor}，随方块区分；
 * 批量放置时缓冲满由 {@link GhostRingBuffer#add} 覆盖最旧条目，自然限流。</p>
 *
 * <p>每帧渲染后调用 {@link GhostRingBuffer#prune} 清理过期条目。</p>
 */
public final class PlaceAnimationPass implements RenderPass {

    /** 下落高度（格）：方块从目标位置正上方多少格开始落下。 */
    private static final double FALL_HEIGHT = 2.0;

    /** 下落动画时长（毫秒）。 */
    private static final long FALL_DURATION_MS = 350L;

    /** 落定后的淡出时长（毫秒）。 */
    private static final long FADE_DURATION_MS = 250L;

    /** 总生命周期 = 下落 + 淡出，超过后从缓冲清除。 */
    private static final long LIFETIME_MS = FALL_DURATION_MS + FADE_DURATION_MS;

    /** 下落/淡出阶段的线框不透明度。 */
    private static final float BASE_ALPHA = 0.85F;

    /** 线框相对方块的偏移量（向内略缩，避免与方块表面深度穿插）。 */
    private static final double LINE_OFFSET = 0.01D;

    private static final CornerBracketRenderer.Rgb color = new CornerBracketRenderer.Rgb();

    @Override
    public boolean shouldRender(Minecraft mc) {
        if (!(mc.screen instanceof BuilderScreen)) return false;
        return !RingBufferHolder.INSTANCE.isEmpty();
    }

    @Override
    public void render(Minecraft mc, RenderPass.BufferAllocator alloc, PoseStack poseStack,
                       float partialTick, int frameIndex) {
        if (mc.level == null) return;
        long now = System.currentTimeMillis();
        GhostRingBuffer buffer = RingBufferHolder.INSTANCE;
        // 补入等待队列中的动画（环形区释放出空间后生效），保证批量放置动画不丢失
        buffer.drainPending();

        Vec3 cameraPos = mc.getCameraEntity() != null
                ? mc.getCameraEntity().getEyePosition(partialTick) : Vec3.ZERO;

        buffer.forEach((key, state, addedAtMs) -> {
            long age = now - addedAtMs;
            if (age < 0 || age >= LIFETIME_MS) return;
            BlockPos p = BlockPos.of(key);

            // 下落进度：0 → 1
            double fallT = Math.min(1.0, age / (double) FALL_DURATION_MS);
            // 当前 Y：从 y + FALL_HEIGHT 线性下落到 y（落地后停在目标位置）
            double yPos = p.getY() + FALL_HEIGHT * (1.0 - fallT);

            // 透明度：下落中恒定，落定后线性淡出
            float alpha;
            if (fallT < 1.0) {
                alpha = BASE_ALPHA;
            } else {
                double fadeT = (age - FALL_DURATION_MS) / (double) FADE_DURATION_MS;
                alpha = BASE_ALPHA * (float) (1.0 - fadeT);
            }

            color.update(colorFor(state, mc.level, p));
            float r = color.r, g = color.g, b = color.b;
            double minX = p.getX() - LINE_OFFSET;
            double minY = yPos - LINE_OFFSET;
            double minZ = p.getZ() - LINE_OFFSET;
            double maxX = p.getX() + 1.0D + LINE_OFFSET;
            double maxY = yPos + 1.0D + LINE_OFFSET;
            double maxZ = p.getZ() + 1.0D + LINE_OFFSET;
            double distance = cameraPos.distanceTo(
                    new Vec3(p.getX() + 0.5D, yPos + 0.5D, p.getZ() + 0.5D));

            // 深度层：角支架线框
            CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.brackets(),
                    minX, minY, minZ, maxX, maxY, maxZ, r, g, b, alpha, distance);
            // 穿透层：深度测试开启时额外渲染半透明线框
            if (BoxSelectionPass.depthTestEnabled) {
                CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.noDepth(),
                        minX, minY, minZ, maxX, maxY, maxZ, r, g, b,
                        CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA * alpha, distance);
            }
        });

        // 清理过期条目，避免缓冲累积
        buffer.prune(now, LIFETIME_MS);
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
