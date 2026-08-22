package com.rtsbuilding.rtsbuilding.client.scene;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 蓝图结构预览场景渲染器 —— 把一组方块位置渲染成 3D 场景缩略图并绘制到 GUI 面板。
 * <p>
 * 参考 LDLib2 的 {@code WorldSceneRenderer} / {@code FBOWorldSceneRenderer}（同为
 * NeoForge 1.21.1），做了最小化裁剪：只保留「VBO 缓存编译 + FBO 相机渲染 + 纹理绘制」核心，
 * 去掉实体 / 粒子 / 异步编译 / 射线拾取等无关能力。方块模型复用 Minecraft 的
 * {@link BlockRenderDispatcher}，与 LDLib 结构预览效果一致。
 * <p>
 * 相机用球坐标控制（yaw / pitch / radius），支持拖拽旋转与滚轮缩放。
 */
public class RtsSceneRenderer {

    private static final double DEG_TO_RAD = 0.017453292519943295;

    /** 预览 FBO 分辨率（正方形，与面板预览区无关，绘制时自动缩放）。 */
    private static final int FBO_SIZE = 512;

    private final Level world;
    /** 当前参与渲染的方块位置集合。 */
    private final Set<BlockPos> renderedBlocks = new HashSet<>();
    /** 方块集合变化后需要重新编译 VBO。 */
    private boolean needRecompile = true;

    private RenderTarget fbo;
    private VertexBuffer[] vertexBuffers;
    private boolean[] vertexBuffersUsingMark;

    // ── 相机（球坐标） ─────────────────────────────────────────────
    private Vec3 lookAt = new Vec3(0, 0, 0);
    private float radius = 8f;
    private float yaw = 45f;
    private float pitch = 30f;
    private float fov = 45f;
    /** 场景背景清屏色（暗色主题下半透明）。 */
    private float clearRed = 0.05f, clearGreen = 0.08f, clearBlue = 0.12f, clearAlpha = 0.95f;

    public RtsSceneRenderer(Level world) {
        this.world = world;
    }

    // ── 场景数据 ───────────────────────────────────────────────────

    /** 设置参与渲染的方块集合（相对坐标）。集合变化后下一帧自动重新编译 VBO。 */
    public void setRenderedBlocks(Collection<BlockPos> blocks) {
        this.renderedBlocks.clear();
        if (blocks != null) {
            this.renderedBlocks.addAll(blocks);
        }
        this.needRecompile = true;
    }

    /** 当前方块数量。 */
    public int getBlockCount() {
        return this.renderedBlocks.size();
    }

    // ── 相机控制 ───────────────────────────────────────────────────

    /**
     * 把相机对准结构中心并自动取景：yaw=45°、pitch=30°，半径按结构尺寸自适应。
     *
     * @param center 结构中心
     * @param size   结构最长边（格）
     */
    public void frameStructure(Vec3 center, double size) {
        this.lookAt = center;
        this.radius = (float) (size * 1.3 + 3);
        this.yaw = 45f;
        this.pitch = 30f;
    }

    /** 拖拽旋转（dYaw/dPitch 单位为度，dx 向右 → yaw 增）。pitch 不设上限，允许任意累加，
     *  配合矩阵视图实现 360° 无死角连续旋转（越过正上/正下方不卡顿、不翻转）。 */
    public void rotate(float dYaw, float dPitch) {
        this.yaw = (this.yaw + dYaw) % 360f;
        this.pitch = (this.pitch + dPitch) % 360f;
    }

    /** 滚轮缩放：factor &gt; 1 拉远，&lt; 1 拉近。 */
    public void zoom(float factor) {
        this.radius = Mth.clamp(this.radius * factor, 1.5f, 600f);
    }

    // ── 渲染 ───────────────────────────────────────────────────────

    /**
     * 渲染场景到 FBO 并绘制到 GUI 指定区域。
     *
     * @param g   GuiGraphics（坐标系统为虚拟坐标，与 UiPanel 一致）
     * @param x   预览区左上角 x
     * @param y   预览区左上角 y
     * @param w   预览区宽
     * @param h   预览区高
     */
    public void render(GuiGraphics g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (needRecompile) {
            compileCache();
        }
        if (vertexBuffers == null) {
            drawEmpty(g, x, y, w, h);
            return;
        }
        drawSceneToFbo();
        drawFboToGui(g, x, y, w, h);
    }

    /** 无方块时的占位绘制（半透明暗底）。 */
    private void drawEmpty(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xAA101820);
    }

    // ── VBO 编译（主线程同步，方块数受 Config 上限约束） ─────────────

    private void compileCache() {
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher brd = mc.getBlockRenderer();
        List<RenderType> layers = RenderType.chunkBufferLayers();
        ensureVertexBuffers(layers);
        if (vertexBuffers == null) {
            this.needRecompile = false;
            return;
        }
        RandomSource randomSource = RandomSource.createNewThreadLocalInstance();
        PoseStack poseStack = new PoseStack();
        for (int i = 0; i < layers.size(); i++) {
            RenderType layer = layers.get(i);
            BufferBuilder buffer = new BufferBuilder(
                    new ByteBufferBuilder(layer.bufferSize()), VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            for (BlockPos pos : renderedBlocks) {
                renderSingleBlock(poseStack, brd, layer, buffer, pos, randomSource);
            }
            MeshData data = buffer.build();
            if (data == null) {
                vertexBuffersUsingMark[i] = false;
                continue;
            }
            vertexBuffersUsingMark[i] = true;
            VertexBuffer vb = vertexBuffers[i];
            if (vb != null && !vb.isInvalid()) {
                vb.bind();
                vb.upload(data);
                VertexBuffer.unbind();
            } else {
                data.close();
            }
        }
        this.needRecompile = false;
    }

    private void ensureVertexBuffers(List<RenderType> layers) {
        if (renderedBlocks.isEmpty()) {
            releaseVertexBuffers();
            return;
        }
        if (vertexBuffers == null || vertexBuffers.length != layers.size()) {
            releaseVertexBuffers();
            this.vertexBuffers = new VertexBuffer[layers.size()];
            this.vertexBuffersUsingMark = new boolean[layers.size()];
            for (int i = 0; i < layers.size(); i++) {
                this.vertexBuffers[i] = new VertexBuffer(VertexBuffer.Usage.STATIC);
            }
        }
    }

    /** 把单个方块按指定 RenderType 渲染进 buffer（真实方块模型，含简单流体面）。 */
    private void renderSingleBlock(PoseStack poseStack, BlockRenderDispatcher brd, RenderType layer,
                                   VertexConsumer buffer, BlockPos pos, RandomSource randomSource) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return;
        var model = brd.getBlockModel(state);
        if (model == null) return;
        var modelData = world.getModelData(pos);
        modelData = model.getModelData(world, pos, state, modelData);
        randomSource.setSeed(state.getSeed(pos));
        if (model.getRenderTypes(state, randomSource, modelData).contains(layer)) {
            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            brd.renderBatched(state, pos, world, poseStack, buffer, false, randomSource, modelData, layer);
            poseStack.popPose();
        }
        // 简单流体面：相对坐标通常 &lt; 64，流体 UV 偏移恒为 0，无需 VertexConsumerWrapper
        var fluidState = state.getFluidState();
        if (!fluidState.isEmpty() && ItemBlockRenderTypes.getRenderLayer(fluidState) == layer) {
            brd.renderLiquid(pos, world, buffer, state, fluidState);
        }
    }

    // ── 场景绘制（绑定 FBO → 设置相机 → 绘制各层 VBO） ──────────────

    private void drawSceneToFbo() {
        ensureFbo();
        // 绑定 FBO 前冲刷 GUI 挂起的批次，避免 GUI 几何被画进 FBO
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();

        RenderTargetScope scope = RenderTargetScope.capture();
        // FBO 场景需全尺寸绘制：关闭 GUI 的 scissor 裁剪，结束渲染后由 scope 恢复
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        fbo.setClearColor(clearRed, clearGreen, clearBlue, clearAlpha);
        fbo.clear(Minecraft.ON_OSX);
        fbo.bindWrite(true);

        RenderSystem.viewport(0, 0, fboWidth(), fboHeight());
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(true);

        RenderSystem.backupProjectionMatrix();
        float aspect = fboWidth() / (float) Math.max(1, fboHeight());
        Vec3 eye = computeEye();
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setPerspective((float) (fov * DEG_TO_RAD), aspect, 0.1f, 10000f),
                VertexSorting.byDistance(new org.joml.Vector3f((float) eye.x, (float) eye.y, (float) eye.z)));

        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        try {
            stack.identity();
            // 轨道相机视图矩阵 = Rx(pitch) · Ry(-yaw) · T(-eye)：
            // 行向量 (right, up, forward) 与 lookAt(eye, lookAt, worldUp) 一致，
            // 但避免了视线与 up 平行时的叉积退化，pitch 越过 ±90° 时视角连续翻转，
            // 360° 无死角且画面不突变。
            stack.rotateX((float) (pitch * DEG_TO_RAD));
            stack.rotateY((float) (-yaw * DEG_TO_RAD));
            stack.translate((float) -eye.x, (float) -eye.y, (float) -eye.z);
            RenderSystem.applyModelViewMatrix();

            drawUploadedBuffers();
        } finally {
            stack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.disableDepthTest();
            scope.close();
        }
    }

    private void drawUploadedBuffers() {
        List<RenderType> layers = RenderType.chunkBufferLayers();
        for (int i = 0; i < layers.size(); i++) {
            VertexBuffer vb = vertexBuffers[i];
            if (vb == null || !vertexBuffersUsingMark[i] || vb.isInvalid()) continue;
            RenderType layer = layers.get(i);
            layer.setupRenderState();
            try {
                ShaderInstance shader = RenderSystem.getShader();
                if (shader == null) continue;
                for (int j = 0; j < 12; ++j) {
                    int tex = RenderSystem.getShaderTexture(j);
                    shader.setSampler("Sampler" + j, tex);
                }
                if (shader.MODEL_VIEW_MATRIX != null) {
                    shader.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
                }
                if (shader.PROJECTION_MATRIX != null) {
                    shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
                }
                if (shader.COLOR_MODULATOR != null) {
                    shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
                }
                if (shader.FOG_START != null) {
                    shader.FOG_START.set(RenderSystem.getShaderFogStart());
                }
                if (shader.FOG_END != null) {
                    shader.FOG_END.set(RenderSystem.getShaderFogEnd());
                }
                if (shader.FOG_COLOR != null) {
                    shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
                }
                if (shader.FOG_SHAPE != null) {
                    shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
                }
                if (shader.TEXTURE_MATRIX != null) {
                    shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
                }
                if (shader.GAME_TIME != null) {
                    shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
                }
                RenderSystem.setupShaderLights(shader);
                shader.apply();

                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                vb.bind();
                vb.draw();
                shader.clear();
            } finally {
                VertexBuffer.unbind();
                layer.clearRenderState();
            }
        }
    }

    /** 把 FBO 纹理绘制到 GUI 区域（y 翻转、线性采样）。 */
    private void drawFboToGui(GuiGraphics g, int x, int y, int w, int h) {
        if (fbo == null || fbo.getColorTextureId() <= 0) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, fbo.getColorTextureId());
        var pose = g.pose().last().pose();
        builder.addVertex(pose, x + w, y + h, 0).setUv(1, 0);
        builder.addVertex(pose, x + w, y, 0).setUv(1, 1);
        builder.addVertex(pose, x, y, 0).setUv(0, 1);
        builder.addVertex(pose, x, y + h, 0).setUv(0, 0);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    // ── 相机 / FBO 资源 ────────────────────────────────────────────

    private Vec3 computeEye() {
        double yawRad = yaw * DEG_TO_RAD;
        double pitchRad = pitch * DEG_TO_RAD;
        double cy = Math.cos(pitchRad);
        return new Vec3(
                lookAt.x + Math.sin(yawRad) * cy * radius,
                lookAt.y + Math.sin(pitchRad) * radius,
                lookAt.z + Math.cos(yawRad) * cy * radius);
    }

    private int fboWidth() {
        return fbo == null ? FBO_SIZE : fbo.width;
    }

    private int fboHeight() {
        return fbo == null ? FBO_SIZE : fbo.height;
    }

    private void ensureFbo() {
        if (fbo != null && fbo.frameBufferId >= 0) return;
        releaseFbo();
        fbo = new MainTarget(FBO_SIZE, FBO_SIZE);
    }

    /** 释放全部 GPU 资源（必须在渲染线程调用）。 */
    public void releaseResource() {
        releaseVertexBuffers();
        releaseFbo();
    }

    private void releaseVertexBuffers() {
        if (vertexBuffers != null) {
            for (VertexBuffer vb : vertexBuffers) {
                if (vb != null) {
                    vb.close();
                }
            }
            vertexBuffers = null;
            vertexBuffersUsingMark = null;
        }
    }

    private void releaseFbo() {
        if (fbo != null) {
            if (RenderSystem.isOnRenderThread()) {
                fbo.destroyBuffers();
            } else {
                RenderSystem.recordRenderCall(() -> fbo.destroyBuffers());
            }
            fbo = null;
        }
    }

    /** 渲染状态保存/恢复作用域：记录绑定 FBO、viewport 与 scissor，close 时还原（参考 LDLib RenderTargetScope）。 */
    private record RenderTargetScope(int framebuffer, int viewportX, int viewportY,
                                     int viewportWidth, int viewportHeight,
                                     boolean scissorEnabled, int scissorX, int scissorY,
                                     int scissorW, int scissorH) implements AutoCloseable {

        static RenderTargetScope capture() {
            RenderSystem.assertOnRenderThread();
            int[] box = new int[4];
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);
            return new RenderTargetScope(
                    GlStateManager.getBoundFramebuffer(),
                    GlStateManager.Viewport.x(), GlStateManager.Viewport.y(),
                    GlStateManager.Viewport.width(), GlStateManager.Viewport.height(),
                    GL11.glGetInteger(GL11.GL_SCISSOR_TEST) != 0,
                    box[0], box[1], box[2], box[3]);
        }

        @Override
        public void close() {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            RenderSystem.viewport(viewportX, viewportY, viewportWidth, viewportHeight);
            // 还原 scissor（FBO 场景渲染期间为全尺寸绘制被关闭，回到 GUI 需恢复裁剪）
            if (scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(scissorX, scissorY, scissorW, scissorH);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }
    }
}
