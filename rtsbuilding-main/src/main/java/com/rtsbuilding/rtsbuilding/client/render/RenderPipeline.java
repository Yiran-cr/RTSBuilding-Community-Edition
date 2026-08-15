package com.rtsbuilding.rtsbuilding.client.render;

import com.mojang.blaze3d.vertex.*;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass.BufferAllocator;
import com.rtsbuilding.rtsbuilding.client.render.pass.*;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster.CursorRay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class RenderPipeline {

    
    
    

    private static final RenderType CHUNK_XRAY_FILL = createXrayType("rtsbuilding_chunk_xray_fill");
    private static final RenderType CHUNK_XRAY_LINES = createXrayType("rtsbuilding_chunk_xray_lines");
    private static final RenderType BRACKET_QUADS = createBracketType();
    private static final RenderType TARGET_NO_DEPTH = createNoDepthType();
    private static final RenderType BOUNDARY_BARRIER = createBoundaryBarrierType();
    private static final RenderType LINES = RenderType.lines();
    private static final RenderType FILLED_BOX = RenderType.debugFilledBox();

    /**
     * 方块缩放动画（放置 grow / 破坏 shrink）专用 RenderType：
     * 使用真实方块模型的顶点格式（纹理 + 光照 + 法线），半透明渲染便于淡入淡出。
     */
    public static final RenderType BLOCK_ANIMATION = createBlockAnimationType();

    /**
     * 放置动画（grow）专用 RenderType：不透明 + 写深度，纯缩放无透明度，
     * 对齐 BuildingGadgets2 的 {@code RenderType.cutout()} 放置虚影效果。
     */
    public static final RenderType BLOCK_ANIMATION_OPAQUE = createBlockAnimationOpaqueType();

    
    
    

    private static final int KB = 1024;
    private final Buf linesBuf, fill, brackets, noDepth, barrier, blockBuf, blockOpaqueBuf;

    /**
     * 将方块模型渲染请求路由到 {@link #blockBuf} 的 {@link MultiBufferSource}。
     * 渲染 pass 通过 {@link RenderPass.BufferAllocator#blockSource()} 获取。
     * 必须在构造器中（{@code blockBuf} 赋值之后）初始化，见 {@link RenderPipeline#RenderPipeline()}。
     */
    private final MultiBufferSource blockSource;

    /** 放置动画不透明方块渲染的 {@link MultiBufferSource}，路由到 {@link #blockOpaqueBuf}。 */
    private final MultiBufferSource blockOpaqueSource;

    
    private static final class Buf {
        final ByteBufferBuilder backing;
        BufferBuilder builder;
        final RenderType type;
        Buf(RenderType type, int sizeKB) {
            this.backing = new ByteBufferBuilder(sizeKB * KB);
            this.builder = new BufferBuilder(this.backing, type.mode, type.format);
            this.type = type;
        }
        void reset() {
            this.backing.clear();
            
            this.builder = new BufferBuilder(this.backing, this.type.mode, this.type.format);
        }
        void draw() {
            MeshData data = this.builder.build();
            if (data != null) this.type.draw(data);
        }
    }

    
    
    

    private final List<RenderPass> passes = new ArrayList<>();
    private int frameIndex;
    
    long frameMillis;
    
    public final BoxSelector boxSelector = new BoxSelector();
    
    public BoxSelectionPass boxSelectionPass;
    
    public LinkedStoragePass linkedStoragePass;
    
    public EntitySelectHighlightPass entitySelectHighlightPass;
    
    public UltiminePreviewPass ultiminePreviewPass;

    /** 线/墙/面模式建造画笔状态机。 */
    public final LineBrushSelector lineBrush = new LineBrushSelector();

    /** 线/墙/面模式建造预览。 */
    public LineBrushRenderPass lineBrushRenderPass;

    
    
    

    public RenderPipeline() {
        this.linesBuf = new Buf(LINES, 256);
        this.fill = new Buf(FILLED_BOX, 256);
        // 线框缓冲容量较大：形状预览/放置动画/连锁挖掘等大量粗线段一次性提交，
        // 容量不足会触发 ByteBufferBuilder 运行时扩容（内存分配 + 拷贝）
        this.brackets = new Buf(BRACKET_QUADS, 512);
        this.noDepth = new Buf(TARGET_NO_DEPTH, 512);
        this.barrier = new Buf(BOUNDARY_BARRIER, 64);
        // 方块缩放动画缓冲容量较大：批量放置/破坏每 tick 最多 64 格，每格一个完整方块模型。
        // 超出时 ByteBufferBuilder 会自动扩容，此容量仅作初始分配。
        this.blockBuf = new Buf(BLOCK_ANIMATION, 2048);
        // 放置动画不透明缓冲：与破坏动画（半透明）分离，各自独立 RenderType 与混合状态
        this.blockOpaqueBuf = new Buf(BLOCK_ANIMATION_OPAQUE, 2048);
        // blockSource 必须在 blockBuf 赋值之后初始化（lambda 运行时访问 blockBuf.builder）
        this.blockSource = renderType -> this.blockBuf.builder;
        this.blockOpaqueSource = renderType -> this.blockOpaqueBuf.builder;

        registerPass(new BoundaryPass());
        registerPass(new InteractionTargetPass());
        registerPass(new FunnelRangePass());
        registerPass(new LinkedStoragePass());
        var lsp = (LinkedStoragePass) passes.get(passes.size() - 1);
        this.linkedStoragePass = lsp;
        
        registerPass(new LocateMarkerPass());
        var bsp = new BoxSelectionPass(boxSelector);
        this.boxSelectionPass = bsp;
        registerPass(bsp);
        var eshp = new EntitySelectHighlightPass();
        this.entitySelectHighlightPass = eshp;
        registerPass(eshp);
        
        this.ultiminePreviewPass = new UltiminePreviewPass();
        registerPass(this.ultiminePreviewPass);

        // 工作流恢复预览：剩余位置绿线框 / 冲突位置橙线框
        registerPass(new ResumePreviewPass());

        var lbrp = new LineBrushRenderPass(lineBrush);
        this.lineBrushRenderPass = lbrp;
        registerPass(lbrp);

        // 放置成功动画：方块从天降落建造特效（消费 GhostRingBuffer.INSTANCE）
        registerPass(new PlaceAnimationPass());
        // 破坏成功特效：方块碎块向上飘散（消费 GhostRingBuffer.BREAK_EFFECTS）
        registerPass(new BreakEffectPass());
    }

    
    
    

    public void registerPass(RenderPass pass) {
        this.passes.add(pass);
    }

    
    public long frameMillis() {
        return this.frameMillis;
    }

    
    public void onRenderFrame(float partialTick, PoseStack poseStack) {
        
        reset();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        this.frameMillis = System.currentTimeMillis();

        
        BuilderScreen screen = mc.screen instanceof BuilderScreen bs ? bs : null;
        CursorRay cursorRay = screen != null ? CursorRaycaster.computeCursorRay(mc, screen) : null;

        BufferAllocator alloc = new BufferAllocator(
                linesBuf.builder, fill.builder, brackets.builder, noDepth.builder, barrier.builder,
                cursorRay, blockSource, blockOpaqueSource);
        for (RenderPass pass : passes) {
            if (!pass.shouldRender(mc)) continue;
            pass.render(mc, alloc, poseStack, partialTick, frameIndex);
        }

        
        flush();
        this.frameIndex++;
    }

    
    public void reset() {
        linesBuf.reset();    fill.reset();
        brackets.reset();    noDepth.reset();
        barrier.reset();     blockBuf.reset();
        blockOpaqueBuf.reset();
    }

    
    public void flush() {
        linesBuf.draw();     fill.draw();
        brackets.draw();     noDepth.draw();
        barrier.draw();      blockBuf.draw();
        blockOpaqueBuf.draw();
    }

    
    
    

    private static RenderType createXrayType(String name) {
        return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 512, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                        .setOutputState(RenderStateShard.MAIN_TARGET)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false));
    }

    private static RenderType createBracketType() {
        return RenderType.create("rtsbuilding_bracket_quads", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 512, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setOutputState(RenderStateShard.MAIN_TARGET)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false));
    }

    /**
     * 边界屏障墙 RenderType：带 <code>barrier.png</code> 纹理的无光照半透明渲染。
     * <p>不使用 <code>entityTranslucent</code>——光影（Iris/OptiFine）会把它映射为
     * 带光照/阴影采样的实体材质，把半透明墙渲染成暗色条带伪影；改用
     * <code>POSITION_TEX_COLOR</code> + <code>position_tex_color</code>（unlit shader，
     * 不采样光照/阴影，Iris 对 basic 管线通常直通），原版与光影下均为带贴图的半透明墙。</p>
     */
    private static RenderType createBoundaryBarrierType() {
        return RenderType.create("rtsbuilding_boundary_barrier", DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 512, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "textures/misc/barrier.png"),
                                false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setOutputState(RenderStateShard.MAIN_TARGET)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false));
    }

    private static RenderType createNoDepthType() {
        return RenderType.create("rtsbuilding_target_no_depth_quads", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 512, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                        .setOutputState(RenderStateShard.MAIN_TARGET)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false));
    }

    /**
     * 方块缩放动画 RenderType：使用 {@link DefaultVertexFormat#BLOCK}（含纹理 UV / 光照 / 法线），
     * 配合半透明混合与 LEQUAL 深度测试，让放置/破坏动画方块带真实光照并与世界正确遮挡。
     * <p>渲染时机在 {@code AFTER_TRANSLUCENT_BLOCKS}（世界渲染完成后），因此深度缓冲中
     * 已有世界内容，无需依赖排序。</p>
     */
    private static RenderType createBlockAnimationType() {
        return RenderType.create("rtsbuilding_block_animation", DefaultVertexFormat.BLOCK,
                VertexFormat.Mode.QUADS, 2097152, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_SOLID_SHADER)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                        .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setCullState(RenderStateShard.CULL)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false));
    }

    /**
     * 放置动画（grow）专用 RenderType：不透明（不启用混合）+ 写入深度，
     * 对齐 BuildingGadgets2 放置虚影用 {@code RenderType.cutout()} 的"全程不透明纯缩放"效果。
     */
    private static RenderType createBlockAnimationOpaqueType() {
        return RenderType.create("rtsbuilding_block_animation_opaque", DefaultVertexFormat.BLOCK,
                VertexFormat.Mode.QUADS, 2097152, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_SOLID_SHADER)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                        .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setCullState(RenderStateShard.CULL)
                        .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                        .createCompositeState(false));
    }
}
