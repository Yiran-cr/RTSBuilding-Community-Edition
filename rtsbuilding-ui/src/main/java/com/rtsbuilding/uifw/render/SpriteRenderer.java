package com.rtsbuilding.uifw.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.uifw.render.model.NineSliceRegion;
import com.rtsbuilding.uifw.render.model.NineSliceTiler;
import com.rtsbuilding.uifw.render.model.SpriteRegion;
import com.rtsbuilding.uifw.render.model.TextureInfo;
import com.rtsbuilding.uifw.theme.ThemeManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class SpriteRenderer {

    


    

    

    



    private SpriteRenderer() {}

    

    
    public static int getThemeOffset(SpriteRegion region) {
        return switch (region.texture().themeLayout()) {
            case HORIZONTAL_PAIR ->
                    ThemeManager.getInstance().isLightMode() ? region.texture().halfWidth() : 0;
            case NONE -> 0;
        };
    }

    
    public static int getNineSliceThemeOffset(NineSliceRegion spec) {
        return getThemeOffset(spec.region());
    }

    

    
    public static void drawSprite(GuiGraphics g, SpriteRegion region,
                                   int dstX, int dstY, int dstW, int dstH) {
        if (dstW <= 0 || dstH <= 0) return;
        var texture = region.texture().location();
        var texInfo = region.texture();
        int texW = texInfo.fullWidth();
        int texH = texInfo.fullHeight();
        var renderType = GuiRenderTypes.fromTextureInfo(texture, texInfo.filterMode());
        var buffer = g.bufferSource().getBuffer(renderType);
        var matrix = g.pose().last().pose();
        float u0 = (float) region.u() / texW;
        float v0 = (float) region.v() / texH;
        float u1 = (float) (region.u() + region.regionWidth()) / texW;
        float v1 = (float) (region.v() + region.regionHeight()) / texH;
        buffer.addVertex(matrix, dstX, dstY + dstH, 0).setUv(u0, v1).setColor(1f, 1f, 1f, 1f);
        buffer.addVertex(matrix, dstX + dstW, dstY + dstH, 0).setUv(u1, v1).setColor(1f, 1f, 1f, 1f);
        buffer.addVertex(matrix, dstX + dstW, dstY, 0).setUv(u1, v0).setColor(1f, 1f, 1f, 1f);
        buffer.addVertex(matrix, dstX, dstY, 0).setUv(u0, v0).setColor(1f, 1f, 1f, 1f);
    }

    
    public static void drawSprite(GuiGraphics g, SpriteRegion region, int themeOffset,
                                   int dstX, int dstY, int dstW, int dstH) {
        if (dstW <= 0 || dstH <= 0) return;
        var texture = region.texture().location();
        var texInfo = region.texture();
        int texW = texInfo.fullWidth();
        int texH = texInfo.fullHeight();
        var renderType = GuiRenderTypes.fromTextureInfo(texture, texInfo.filterMode());
        var buffer = g.bufferSource().getBuffer(renderType);
        var matrix = g.pose().last().pose();
        float u0 = (float) (region.u() + themeOffset) / texW;
        float v0 = (float) region.v() / texH;
        float u1 = (float) (region.u() + themeOffset + region.regionWidth()) / texW;
        float v1 = (float) (region.v() + region.regionHeight()) / texH;
        buffer.addVertex(matrix, dstX, dstY + dstH, 0).setUv(u0, v1).setColor(1f, 1f, 1f, 1f);
        buffer.addVertex(matrix, dstX + dstW, dstY + dstH, 0).setUv(u1, v1).setColor(1f, 1f, 1f, 1f);
        buffer.addVertex(matrix, dstX + dstW, dstY, 0).setUv(u1, v0).setColor(1f, 1f, 1f, 1f);
        buffer.addVertex(matrix, dstX, dstY, 0).setUv(u0, v0).setColor(1f, 1f, 1f, 1f);
    }

    
    private static void drawSpriteImmediate(GuiGraphics g, SpriteRegion region, int themeOffset,
                                             int dstX, int dstY, int dstW, int dstH) {
        if (dstW <= 0 || dstH <= 0) return;
        FilterState.getInstance().apply(region.texture());
        var texture = region.texture().location();
        var texInfo = region.texture();
        int texW = texInfo.fullWidth();
        int texH = texInfo.fullHeight();
        var renderType = GuiRenderTypes.fromTextureInfo(texture, texInfo.filterMode());
        var buffer = g.bufferSource().getBuffer(renderType);
        var matrix = g.pose().last().pose();
        float u0 = (float) (region.u() + themeOffset) / texW;
        float v0 = (float) region.v() / texH;
        float u1 = (float) (region.u() + themeOffset + region.regionWidth()) / texW;
        float v1 = (float) (region.v() + region.regionHeight()) / texH;
        buffer.addVertex(matrix, dstX, dstY + dstH, 0).setUv(u0, v1).setColor(1f, 1f, 1f, 1f);
        buffer.addVertex(matrix, dstX + dstW, dstY + dstH, 0).setUv(u1, v1).setColor(1f, 1f, 1f, 1f);
        buffer.addVertex(matrix, dstX + dstW, dstY, 0).setUv(u1, v0).setColor(1f, 1f, 1f, 1f);
        buffer.addVertex(matrix, dstX, dstY, 0).setUv(u0, v0).setColor(1f, 1f, 1f, 1f);
    }

    

    
    public static void drawNineSlice(GuiGraphics g, NineSliceRegion spec,
                                      int dstX, int dstY, int dstW, int dstH) {
        drawNineSlice(g, spec, 0, dstX, dstY, dstW, dstH);
    }

    public static void drawNineSlice(GuiGraphics g, NineSliceRegion spec,
                                      int dstX, int dstY, int dstW, int dstH, float alpha) {
        drawNineSlice(g, spec, 0, dstX, dstY, dstW, dstH, alpha);
    }

    
    public static void drawNineSlice(GuiGraphics g, NineSliceRegion spec, int themeOffset,
                                      int dstX, int dstY, int dstW, int dstH) {
        drawNineSlice(g, spec, themeOffset, dstX, dstY, dstW, dstH, 1f);
    }

    public static void drawNineSlice(GuiGraphics g, NineSliceRegion spec, int themeOffset,
                                      int dstX, int dstY, int dstW, int dstH, float alpha) {
        SpriteRegion r = spec.region();
        drawNineSliceRaw(g, r.texture(),
                r.u() + themeOffset, r.v(),
                r.regionWidth(), r.regionHeight(), spec.border(),
                dstX, dstY, dstW, dstH, alpha);
    }

    private static void drawNineSliceRaw(GuiGraphics g, TextureInfo texInfo,
                                          int u, int v, int regionW, int regionH, int border,
                                          int dstX, int dstY, int dstW, int dstH, float alpha) {
        if (dstW <= 0 || dstH <= 0) return;

        ResourceLocation texture = texInfo.location();
        int texW = texInfo.fullWidth();
        int texH = texInfo.fullHeight();

        var renderType = GuiRenderTypes.fromTextureInfo(texture, texInfo.filterMode());
        VertexConsumer buffer = g.bufferSource().getBuffer(renderType);
        var matrix = g.pose().last().pose();

        NineSliceTiler.forEachTile(
                u, v, regionW, regionH, border,
                dstX, dstY, dstW, dstH,
                (sx, sy, sw, sh, dx, dy, dw, dh) -> {
                    float u0 = (float) sx / texW;
                    float v0 = (float) sy / texH;
                    float u1 = (float) (sx + sw) / texW;
                    float v1 = (float) (sy + sh) / texH;
                    buffer.addVertex(matrix, dx,     dy + dh, 0).setUv(u0, v1).setColor(1f, 1f, 1f, alpha);
                    buffer.addVertex(matrix, dx + dw, dy + dh, 0).setUv(u1, v1).setColor(1f, 1f, 1f, alpha);
                    buffer.addVertex(matrix, dx + dw, dy,      0).setUv(u1, v0).setColor(1f, 1f, 1f, alpha);
                    buffer.addVertex(matrix, dx,     dy,      0).setUv(u0, v0).setColor(1f, 1f, 1f, alpha);
                });
    }

    private static void drawNineSliceRaw(GuiGraphics g, TextureInfo texInfo,
                                          int u, int v, int regionW, int regionH, int border,
                                          int dstX, int dstY, int dstW, int dstH) {
        drawNineSliceRaw(g, texInfo, u, v, regionW, regionH, border, dstX, dstY, dstW, dstH, 1f);
    }

    

    
    public static void drawTiledRow(GuiGraphics g, SpriteRegion region,
                                     int dstX, int dstY, int tileW, int tileH, int cols) {
        if (cols <= 0 || tileW <= 0 || tileH <= 0) return;
        var texture = region.texture().location();
        var texInfo = region.texture();
        int texW = texInfo.fullWidth();
        int texH = texInfo.fullHeight();
        var renderType = GuiRenderTypes.fromTextureInfo(texture, texInfo.filterMode());
        var buffer = g.bufferSource().getBuffer(renderType);
        var matrix = g.pose().last().pose();

        float u0 = (float) region.u() / texW;
        float v0 = (float) region.v() / texH;
        float u1 = (float) (region.u() + region.regionWidth()) / texW;
        float v1 = (float) (region.v() + region.regionHeight()) / texH;

        for (int col = 0; col < cols; col++) {
            int dx = dstX + col * tileW;
            buffer.addVertex(matrix, dx,     dstY + tileH, 0).setUv(u0, v1).setColor(1f, 1f, 1f, 1f);
            buffer.addVertex(matrix, dx + tileW, dstY + tileH, 0).setUv(u1, v1).setColor(1f, 1f, 1f, 1f);
            buffer.addVertex(matrix, dx + tileW, dstY,       0).setUv(u1, v0).setColor(1f, 1f, 1f, 1f);
            buffer.addVertex(matrix, dx,     dstY,       0).setUv(u0, v0).setColor(1f, 1f, 1f, 1f);
        }
    }

    

    
    public static void drawTiledRow(GuiGraphics g, SpriteRegion region, int themeOffset,
                                     int dstX, int dstY, int tileW, int tileH, int cols) {
        drawTiledRowRange(g, region, themeOffset, dstX, dstY, tileW, tileH, 0, cols - 1);
    }

    

    
    public static void drawTiledGrid(GuiGraphics g, SpriteRegion region, int themeOffset,
                                      int originX, int originY,
                                      int tileW, int tileH, int gap,
                                      int cols, int rows,
                                      int scroll, int clipTop, int clipBottom) {
        if (cols <= 0 || rows <= 0 || tileW <= 0 || tileH <= 0) return;
        var texture = region.texture().location();
        var texInfo = region.texture();
        int texW = texInfo.fullWidth();
        int texH = texInfo.fullHeight();
        var renderType = GuiRenderTypes.fromTextureInfo(texture, texInfo.filterMode());
        var buffer = g.bufferSource().getBuffer(renderType);
        var matrix = g.pose().last().pose();

        float u0 = (float) (region.u() + themeOffset) / texW;
        float v0 = (float) region.v() / texH;
        float u1 = (float) (region.u() + themeOffset + region.regionWidth()) / texW;
        float v1 = (float) (region.v() + region.regionHeight()) / texH;

        int stride = tileH + gap;
        for (int row = 0; row < rows; row++) {
            int rowY = originY + row * stride - scroll;
            if (rowY + tileH <= clipTop || rowY >= clipBottom) continue;
            for (int col = 0; col < cols; col++) {
                int dx = originX + col * (tileW + gap);
                buffer.addVertex(matrix, dx,         rowY + tileH, 0).setUv(u0, v1).setColor(1f, 1f, 1f, 1f);
                buffer.addVertex(matrix, dx + tileW, rowY + tileH, 0).setUv(u1, v1).setColor(1f, 1f, 1f, 1f);
                buffer.addVertex(matrix, dx + tileW, rowY,         0).setUv(u1, v0).setColor(1f, 1f, 1f, 1f);
                buffer.addVertex(matrix, dx,         rowY,         0).setUv(u0, v0).setColor(1f, 1f, 1f, 1f);
            }
        }
    }

    
    private static void drawTiledRowRange(GuiGraphics g, SpriteRegion region, int themeOffset,
                                           int dstX, int dstY, int tileW, int tileH,
                                           int startCol, int endCol) {
        if (startCol > endCol || tileW <= 0 || tileH <= 0) return;
        var texture = region.texture().location();
        var texInfo = region.texture();
        int texW = texInfo.fullWidth();
        int texH = texInfo.fullHeight();
        var renderType = GuiRenderTypes.fromTextureInfo(texture, texInfo.filterMode());
        var buffer = g.bufferSource().getBuffer(renderType);
        var matrix = g.pose().last().pose();

        float u0 = (float) (region.u() + themeOffset) / texW;
        float v0 = (float) region.v() / texH;
        float u1 = (float) (region.u() + themeOffset + region.regionWidth()) / texW;
        float v1 = (float) (region.v() + region.regionHeight()) / texH;

        for (int col = startCol; col <= endCol; col++) {
            int dx = dstX + col * tileW;
            buffer.addVertex(matrix, dx,         dstY + tileH, 0).setUv(u0, v1).setColor(1f, 1f, 1f, 1f);
            buffer.addVertex(matrix, dx + tileW, dstY + tileH, 0).setUv(u1, v1).setColor(1f, 1f, 1f, 1f);
            buffer.addVertex(matrix, dx + tileW, dstY,         0).setUv(u1, v0).setColor(1f, 1f, 1f, 1f);
            buffer.addVertex(matrix, dx,         dstY,         0).setUv(u0, v0).setColor(1f, 1f, 1f, 1f);
        }
    }

    

    
    public static void drawStateSprite(GuiGraphics g,
                                        SpriteRegion normal, SpriteRegion hovered, SpriteRegion selected,
                                        boolean isSelected, float hoverT,
                                        int dstX, int dstY, int dstW, int dstH) {
        
        int themeOffset = getThemeOffset(normal);
        if (isSelected) {
            drawSprite(g, selected, themeOffset, dstX, dstY, dstW, dstH);
            return;
        }
        CrossFadeRenderer.render(g, hoverT,
                () -> drawSpriteImmediate(g, normal, themeOffset, dstX, dstY, dstW, dstH),
                () -> drawSpriteImmediate(g, hovered, themeOffset, dstX, dstY, dstW, dstH));
    }
}
