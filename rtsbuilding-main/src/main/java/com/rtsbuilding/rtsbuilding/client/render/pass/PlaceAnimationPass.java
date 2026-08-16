package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.GhostRingBuffer;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.RenderPipeline;
import com.rtsbuilding.rtsbuilding.client.render.RingBufferHolder;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * 放置成功特效：消费 {@link GhostRingBuffer} 中由
 * {@code RtsClientNetworkHandlers.handlePlaceAnimation} 写入的放置位置，
 * 渲染"方块生长"——目标方块的真实模型从方块中心由 0 放大到 1（grow）。
 *
 * <p>效果参考 BuildingGadgets2 的 {@code RenderBlock} grow 动画：以方块中心为基准缩放
 * （{@code translate((1-scale)/2 ...) + scale(scale)}），<b>全程不透明纯缩放、无透明度变化</b>
 * （对齐其 {@code RenderType.cutout()} 放置虚影），带真实纹理与光照。
 * 非完整模型方块（流体等）回退为不透明色块，保证任何方块都有可见反馈。
 *
 * <p>动画总时长取<b>服务端权威</b>的 {@code durationMs}（动画包 durationTicks × 50ms）：
 * 服务端在该时长后真正落位方块，客户端动画结束瞬间 BlockUpdate 到达方块出现 ——
 * 「动画结束 = 方块落位」的节奏由服务端控制，客户端不再硬编码时长。</p>
 *
 * <p>每帧渲染后调用 {@link GhostRingBuffer#prune} 按条目时长清理过期条目。</p>
 */
public final class PlaceAnimationPass implements RenderPass {

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

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();

        buffer.forEach((key, state, addedAtMs, durationMs) -> {
            long age = now - addedAtMs;
            if (age < 0 || age >= durationMs) return;
            BlockPos p = BlockPos.of(key);

            // 生长进度 0 → 1（easeOutCubic，收尾柔和），总时长由服务端权威控制
            double growT = Math.min(1.0, age / (double) durationMs);
            float scale = (float) (1.0 - Math.pow(1.0 - growT, 3.0));
            if (scale <= 0.01F) return;

            renderScaledBlock(mc.level, p, state, scale, dispatcher, poseStack, alloc);
        });

        // 清理过期条目（按各自服务端权威时长），避免缓冲累积
        buffer.prune(now);
    }

    /**
     * 以方块中心按 scale 缩放渲染真实方块模型（grow，不透明纯缩放）；
     * 非完整模型方块回退为不透明色块。
     */
    private static void renderScaledBlock(Level level, BlockPos pos, BlockState state, float scale,
                                          BlockRenderDispatcher dispatcher, PoseStack poseStack, BufferAllocator alloc) {
        if (state == null || state.isAir()) return;
        // 流体/特殊方块（renderShape 非 MODEL）无模型可渲染，回退为色块
        if (state.getRenderShape() != RenderShape.MODEL) {
            renderFallbackCube(level, pos, state, scale, poseStack, alloc);
            return;
        }
        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        // 以方块中心（0.5,0.5,0.5）为基准缩放，与 BuildingGadgets2 grow 动画一致
        poseStack.translate((1.0F - scale) / 2.0F, (1.0F - scale) / 2.0F, (1.0F - scale) / 2.0F);
        poseStack.scale(scale, scale, scale);
        int light = LevelRenderer.getLightColor(level, pos);
        dispatcher.renderSingleBlock(state, poseStack, alloc.blockOpaqueSource(), light, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, RenderPipeline.BLOCK_ANIMATION_OPAQUE);
        poseStack.popPose();
    }

    /** 流体/特殊方块回退：以方块中心缩放的色块（不透明，与放置虚影一致）。 */
    private static void renderFallbackCube(Level level, BlockPos pos, BlockState state, float scale,
                                           PoseStack poseStack, BufferAllocator alloc) {
        color.update(colorFor(state, level, pos));
        float r = color.r, g = color.g, b = color.b;
        double half = 0.5D * scale;
        double cx = pos.getX() + 0.5D, cy = pos.getY() + 0.5D, cz = pos.getZ() + 0.5D;
        CornerBracketRenderer.renderFilledFaces(alloc.brackets(), poseStack,
                cx - half, cy - half, cz - half,
                cx + half, cy + half, cz + half,
                r, g, b, 1.0F);
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
