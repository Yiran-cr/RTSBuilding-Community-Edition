package com.rtsbuilding.uifw.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.uifw.render.model.NineSliceRegion;
import com.rtsbuilding.uifw.render.model.NineSliceTiler;
import com.rtsbuilding.uifw.render.model.SpriteRegion;
import com.rtsbuilding.uifw.render.model.TextureInfo;
import com.rtsbuilding.uifw.theme.ThemeManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

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
        drawSprite(g, region, 0, dstX, dstY, dstW, dstH);
    }

    public static void drawSprite(GuiGraphics g, SpriteRegion region, int themeOffset,
                                   int dstX, int dstY, int dstW, int dstH) {
        drawSprite(g, region, themeOffset, dstX, dstY, dstW, dstH, 1f);
    }

    public static void drawSprite(GuiGraphics g, SpriteRegion region, int themeOffset,
                                   int dstX, int dstY, int dstW, int dstH, float alpha) {
        if (dstW <= 0 || dstH <= 0) return;
        var texture = region.texture().location();
        var texInfo = region.texture();
        int texW = texInfo.fullWidth();
        int texH = texInfo.fullHeight();
        var renderType = GuiRenderTypes.fromTextureInfo(texture, texInfo.filterMode());
        var buffer = g.bufferSource().getBuffer(renderType);
        var matrix = g.pose().last().pose();
        emitQuad(buffer, matrix, texW, texH,
                region.u() + themeOffset, region.v(),
                region.regionWidth(), region.regionHeight(),
                dstX, dstY, dstW, dstH, alpha);
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
        emitQuad(buffer, matrix, texW, texH,
                region.u() + themeOffset, region.v(),
                region.regionWidth(), region.regionHeight(),
                dstX, dstY, dstW, dstH, 1f);
    }

    /**
     * 写一个带 UV 归一化的 QUAD 顶点。
     * 源区域用整数像素坐标 (sx,sy,sw,sh)，除以整幅纹理宽高得到归一化 UV，
     * 目标位置 dst 用整数屏幕坐标，统一保证所有精灵绘制的精度口径一致。
     */
    private static void emitQuad(VertexConsumer buffer, Matrix4f matrix,
                                 int texW, int texH,
                                 int sx, int sy, int sw, int sh,
                                 int dstX, int dstY, int dstW, int dstH, float alpha) {
        float u0 = (float) sx / texW;
        float v0 = (float) sy / texH;
        float u1 = (float) (sx + sw) / texW;
        float v1 = (float) (sy + sh) / texH;
        buffer.addVertex(matrix, dstX, dstY + dstH, 0).setUv(u0, v1).setColor(1f, 1f, 1f, alpha);
        buffer.addVertex(matrix, dstX + dstW, dstY + dstH, 0).setUv(u1, v1).setColor(1f, 1f, 1f, alpha);
        buffer.addVertex(matrix, dstX + dstW, dstY, 0).setUv(u1, v0).setColor(1f, 1f, 1f, alpha);
        buffer.addVertex(matrix, dstX, dstY, 0).setUv(u0, v0).setColor(1f, 1f, 1f, alpha);
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
                (sx, sy, sw, sh, dx, dy, dw, dh) ->
                        emitQuad(buffer, matrix, texW, texH, sx, sy, sw, sh, dx, dy, dw, dh, alpha));
    }

    private static void drawNineSliceRaw(GuiGraphics g, TextureInfo texInfo,
                                          int u, int v, int regionW, int regionH, int border,
                                          int dstX, int dstY, int dstW, int dstH) {
        drawNineSliceRaw(g, texInfo, u, v, regionW, regionH, border, dstX, dstY, dstW, dstH, 1f);
    }

    

    
    public static void drawTiledRow(GuiGraphics g, SpriteRegion region,
                                     int dstX, int dstY, int tileW, int tileH, int cols) {
        drawTiledRowRange(g, region, 0, dstX, dstY, tileW, tileH, 0, cols - 1);
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

        int stride = tileH + gap;
        for (int row = 0; row < rows; row++) {
            int rowY = originY + row * stride - scroll;
            if (rowY + tileH <= clipTop || rowY >= clipBottom) continue;
            for (int col = 0; col < cols; col++) {
                int dx = originX + col * (tileW + gap);
                emitQuad(buffer, matrix, texW, texH,
                        region.u() + themeOffset, region.v(),
                        region.regionWidth(), region.regionHeight(),
                        dx, rowY, tileW, tileH, 1f);
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

        for (int col = startCol; col <= endCol; col++) {
            int dx = dstX + col * tileW;
            emitQuad(buffer, matrix, texW, texH,
                    region.u() + themeOffset, region.v(),
                    region.regionWidth(), region.regionHeight(),
                    dx, dstY, tileW, tileH, 1f);
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
