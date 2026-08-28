package com.rtsbuilding.uifw.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rtsbuilding.uifw.render.model.TextureInfo;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class GuiRenderTypes {

    private static final int BUFFER_SIZE = 786432;

    /**
     * 纯色（无纹理）GUI 渲染类型：POSITION_COLOR + 位置颜色 shader + 透明混合。
     * 与 {@link GuiGraphics#fill}（内部用的 {@link RenderType#gui()}）同一管线，但显式开启
     * {@link RenderStateShard#TRANSLUCENT_TRANSPARENCY}，保证半透明顶点色在 GUI 批次中
     * 按 alpha 正确混合且一定可见（对标 {@link SpriteRenderer} 的做法）。
     */
    public static final RenderType GUI_SOLID = RenderType.create(
            "uifw_gui_solid",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            BUFFER_SIZE,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false)
    );

    private record CacheKey(ResourceLocation texture, boolean linear, boolean mipmap) {
        CacheKey {
            Objects.requireNonNull(texture);
        }
    }

    private static final Map<CacheKey, RenderType> CACHE = new HashMap<>();

    
    public static RenderType guiTextured(ResourceLocation texture, boolean linear, boolean mipmap) {
        return CACHE.computeIfAbsent(new CacheKey(texture, linear, mipmap), GuiRenderTypes::create);
    }

    
    public static RenderType fromTextureInfo(ResourceLocation texture, TextureInfo.FilterMode filterMode) {
        return switch (filterMode) {
            case PIXEL -> guiTextured(texture, false, false);
            case NORMAL -> guiTextured(texture, true, false);
            case HQ -> guiTextured(texture, true, true);
        };
    }

    private static RenderType create(CacheKey key) {
        return RenderType.create(
                "uifw_gui_textured",
                DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS,
                BUFFER_SIZE,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(key.texture, key.linear, key.mipmap))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );
    }

    private GuiRenderTypes() {}
}
