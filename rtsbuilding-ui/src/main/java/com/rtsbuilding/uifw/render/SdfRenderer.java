package com.rtsbuilding.uifw.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.rtsbuilding.uifw.animate.ColorAnimation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

public final class SdfRenderer {

    private SdfRenderer() {}

    public static void drawRoundedRect(GuiGraphics g, int x, int y, int w, int h,
                                        float radius, int color, float alpha) {
        drawRoundedRect(g, (float) x, (float) y, w, h, radius, color, alpha);
    }

    /** 浮点坐标版本的圆角矩形绘制（SDF shader，亚像素精度，用于平滑取色器指示器）。 */
    public static void drawRoundedRect(GuiGraphics g, float x, float y, float w, float h,
                                        float radius, int color, float alpha) {
        if (w <= 0 || h <= 0) return;

        g.flush();

        ShaderInstance shader = UiShaders.roundedRect;
        if (shader == null) return;

        float halfW = w / 2f;
        float halfH = h / 2f;
        float clampedRadius = Math.min(radius, Math.min(halfW, halfH));

        float r = ((color >> 16) & 0xFF) / 255f;
        float gr = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f * alpha;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);

        shader.safeGetUniform("u_Size");
        shader.safeGetUniform("u_Size").set(halfW, halfH);
        shader.safeGetUniform("u_Radius");
        shader.safeGetUniform("u_Radius").set(clampedRadius);

        var matrix = g.pose().last().pose();

        var backing = new ByteBufferBuilder(256);
        var builder = new BufferBuilder(backing, VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);

        builder.addVertex(matrix, x, y + h, 0).setUv(-halfW, halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + w, y + h, 0).setUv(halfW, halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + w, y, 0).setUv(halfW, -halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x, y, 0).setUv(-halfW, -halfH).setColor(r, gr, b, a);

        MeshData data = builder.build();
        if (data != null) {
            BufferUploader.drawWithShader(data);
        }
        backing.close();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
    }

    public static void drawRoundedRect(GuiGraphics g, int x, int y, int w, int h,
                                        float radius, int color) {
        drawRoundedRect(g, x, y, w, h, radius, color, 1f);
    }

    public static void drawRoundedRectTopOnly(GuiGraphics g, int x, int y, int w, int h,
                                               float radius, int color, float alpha) {
        if (w <= 0 || h <= 0) return;

        g.flush();

        ShaderInstance shader = UiShaders.roundedRectTop;
        if (shader == null) return;

        float halfW = w / 2f;
        float halfH = h / 2f;
        float clampedRadius = Math.min(radius, Math.min(halfW, halfH));
        float cx = x + halfW;
        float cy = y + halfH;

        float r = ((color >> 16) & 0xFF) / 255f;
        float gr = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f * alpha;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);

        shader.safeGetUniform("u_Size");
        shader.safeGetUniform("u_Size").set(halfW, halfH);
        shader.safeGetUniform("u_Radius");
        shader.safeGetUniform("u_Radius").set(clampedRadius);

        var matrix = g.pose().last().pose();

        var backing = new ByteBufferBuilder(256);
        var builder = new BufferBuilder(backing, VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);

        builder.addVertex(matrix, x, y + h, 0).setUv(-halfW, halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + w, y + h, 0).setUv(halfW, halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + w, y, 0).setUv(halfW, -halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x, y, 0).setUv(-halfW, -halfH).setColor(r, gr, b, a);

        MeshData data = builder.build();
        if (data != null) {
            BufferUploader.drawWithShader(data);
        }
        backing.close();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
    }

    public static void drawPill(GuiGraphics g, int x, int y, int w, int h, int color) {
        drawRoundedRect(g, x, y, w, h, Math.min(w, h) / 2f, color);
    }

    public static void drawPill(GuiGraphics g, int x, int y, int w, int h, int color, float alpha) {
        drawRoundedRect(g, x, y, w, h, Math.min(w, h) / 2f, color, alpha);
    }

    public static void drawCircle(GuiGraphics g, int cx, int cy, int radius, int color) {
        int d = radius * 2;
        drawPill(g, cx - radius, cy - radius, d, d, color);
    }

    /** 浮点坐标版本圆绘制（亚像素精度，用于平滑取色器指示器）。 */
    public static void drawCircleF(GuiGraphics g, float cx, float cy, int radius, int color) {
        float d = radius * 2;
        drawRoundedRect(g, cx - radius, cy - radius, d, d, radius, color, 1f);
    }

    /**
     * 矢量绘制 HSV 色轮（SDF shader，fragment 内计算色相/饱和度着色，无贴图位图）。
     * {@code size} 为色轮外接正方形边长，圆边缘抗锯齿。
     */
    public static void drawColorWheel(GuiGraphics g, int x, int y, int size) {
        if (size <= 0) return;

        g.flush();

        ShaderInstance shader = UiShaders.colorwheel;
        if (shader == null) return;

        float half = size / 2f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);

        shader.safeGetUniform("u_Size").set(half, half);

        var matrix = g.pose().last().pose();
        var backing = new ByteBufferBuilder(256);
        var builder = new BufferBuilder(backing, VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);

        builder.addVertex(matrix, x, y + size, 0).setUv(-half, half).setColor(1f, 1f, 1f, 1f);
        builder.addVertex(matrix, x + size, y + size, 0).setUv(half, half).setColor(1f, 1f, 1f, 1f);
        builder.addVertex(matrix, x + size, y, 0).setUv(half, -half).setColor(1f, 1f, 1f, 1f);
        builder.addVertex(matrix, x, y, 0).setUv(-half, -half).setColor(1f, 1f, 1f, 1f);

        MeshData data = builder.build();
        if (data != null) {
            BufferUploader.drawWithShader(data);
        }
        backing.close();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
    }

    public static void drawCircle(GuiGraphics g, int cx, int cy, int radius, int color, float alpha) {
        int d = radius * 2;
        drawPill(g, cx - radius, cy - radius, d, d, color, alpha);
    }

    public static void drawRoundedRectBottomOnly(GuiGraphics g, int x, int y, int w, int h,
                                                   float radius, int color, float alpha) {
        if (w <= 0 || h <= 0) return;

        g.flush();

        ShaderInstance shader = UiShaders.roundedRectBottom;
        if (shader == null) return;

        float halfW = w / 2f;
        float halfH = h / 2f;
        float clampedRadius = Math.min(radius, Math.min(halfW, halfH));
        float cx = x + halfW;
        float cy = y + halfH;

        float r = ((color >> 16) & 0xFF) / 255f;
        float gr = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f * alpha;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);

        shader.safeGetUniform("u_Size");
        shader.safeGetUniform("u_Size").set(halfW, halfH);
        shader.safeGetUniform("u_Radius");
        shader.safeGetUniform("u_Radius").set(clampedRadius);

        var matrix = g.pose().last().pose();

        var backing = new ByteBufferBuilder(256);
        var builder = new BufferBuilder(backing, VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);

        builder.addVertex(matrix, x, y + h, 0).setUv(-halfW, halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + w, y + h, 0).setUv(halfW, halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + w, y, 0).setUv(halfW, -halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x, y, 0).setUv(-halfW, -halfH).setColor(r, gr, b, a);

        MeshData data = builder.build();
        if (data != null) {
            BufferUploader.drawWithShader(data);
        }
        backing.close();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
    }

    public static void drawRoundedRectBottomOnly(GuiGraphics g, int x, int y, int w, int h,
                                                   float radius, int color) {
        drawRoundedRectBottomOnly(g, x, y, w, h, radius, color, 1f);
    }

    public static void drawRoundedRectLeftOnly(GuiGraphics g, int x, int y, int w, int h,
                                                float radius, int color, float alpha) {
        if (w <= 0 || h <= 0) return;

        g.flush();

        ShaderInstance shader = UiShaders.roundedRectLeft;
        if (shader == null) return;

        float halfW = w / 2f;
        float halfH = h / 2f;
        float clampedRadius = Math.min(radius, Math.min(halfW, halfH));

        float r = ((color >> 16) & 0xFF) / 255f;
        float gr = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f * alpha;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);

        shader.safeGetUniform("u_Size");
        shader.safeGetUniform("u_Size").set(halfW, halfH);
        shader.safeGetUniform("u_Radius");
        shader.safeGetUniform("u_Radius").set(clampedRadius);

        var matrix = g.pose().last().pose();

        var backing = new ByteBufferBuilder(256);
        var builder = new BufferBuilder(backing, VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);

        builder.addVertex(matrix, x, y + h, 0).setUv(-halfW, halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + w, y + h, 0).setUv(halfW, halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + w, y, 0).setUv(halfW, -halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x, y, 0).setUv(-halfW, -halfH).setColor(r, gr, b, a);

        MeshData data = builder.build();
        if (data != null) {
            BufferUploader.drawWithShader(data);
        }
        backing.close();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
    }

    public static void drawRoundedRectLeftOnly(GuiGraphics g, int x, int y, int w, int h,
                                                float radius, int color) {
        drawRoundedRectLeftOnly(g, x, y, w, h, radius, color, 1f);
    }

    public static void drawRoundedRectRightOnly(GuiGraphics g, int x, int y, int w, int h,
                                                 float radius, int color, float alpha) {
        if (w <= 0 || h <= 0) return;

        g.flush();

        ShaderInstance shader = UiShaders.roundedRectRight;
        if (shader == null) return;

        float halfW = w / 2f;
        float halfH = h / 2f;
        float clampedRadius = Math.min(radius, Math.min(halfW, halfH));

        float r = ((color >> 16) & 0xFF) / 255f;
        float gr = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f * alpha;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);

        shader.safeGetUniform("u_Size");
        shader.safeGetUniform("u_Size").set(halfW, halfH);
        shader.safeGetUniform("u_Radius");
        shader.safeGetUniform("u_Radius").set(clampedRadius);

        var matrix = g.pose().last().pose();

        var backing = new ByteBufferBuilder(256);
        var builder = new BufferBuilder(backing, VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);

        builder.addVertex(matrix, x, y + h, 0).setUv(-halfW, halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + w, y + h, 0).setUv(halfW, halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + w, y, 0).setUv(halfW, -halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x, y, 0).setUv(-halfW, -halfH).setColor(r, gr, b, a);

        MeshData data = builder.build();
        if (data != null) {
            BufferUploader.drawWithShader(data);
        }
        backing.close();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
    }

    public static void drawRoundedRectRightOnly(GuiGraphics g, int x, int y, int w, int h,
                                                 float radius, int color) {
        drawRoundedRectRightOnly(g, x, y, w, h, radius, color, 1f);
    }

    public static void drawRoundedRectTopOnly(GuiGraphics g, int x, int y, int w, int h,
                                                float radius, int color) {
        drawRoundedRectTopOnly(g, x, y, w, h, radius, color, 1f);
    }

    public static void drawChevron(GuiGraphics g, int x, int y, int w, int h, int color) {
        drawChevron(g, x, y, w, h, color, 2f);
    }

    public static void drawChevron(GuiGraphics g, int x, int y, int w, int h, int color, float radius) {
        if (w <= 0 || h <= 0) return;

        g.flush();

        ShaderInstance shader = UiShaders.chevron;
        if (shader == null) return;

        float halfW = w / 2f;
        float halfH = h / 2f;
        float clampedRadius = Math.min(radius, Math.min(halfW, halfH));

        float r = ((color >> 16) & 0xFF) / 255f;
        float gr = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);

        if (shader.safeGetUniform("u_P0") != null)
            shader.safeGetUniform("u_P0").set(-halfW * 0.7f, -halfH * 0.7f);
        if (shader.safeGetUniform("u_P1") != null)
            shader.safeGetUniform("u_P1").set(halfW * 0.7f, 0f);
        if (shader.safeGetUniform("u_P2") != null)
            shader.safeGetUniform("u_P2").set(-halfW * 0.7f, halfH * 0.7f);
        if (shader.safeGetUniform("u_Radius") != null)
            shader.safeGetUniform("u_Radius").set(clampedRadius);

        var matrix = g.pose().last().pose();
        var backing = new ByteBufferBuilder(256);
        var builder = new BufferBuilder(backing, VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);

        builder.addVertex(matrix, x, y + h, 0).setUv(-halfW, halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + w, y + h, 0).setUv(halfW, halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + w, y, 0).setUv(halfW, -halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x, y, 0).setUv(-halfW, -halfH).setColor(r, gr, b, a);

        MeshData data = builder.build();
        if (data != null) BufferUploader.drawWithShader(data);
        backing.close();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
    }

    public static void drawBorderedRoundedRect(GuiGraphics g, int x, int y, int w, int h,
                                                 float radius, int borderColor, int fillColor) {
        drawBorderedRoundedRect(g, x, y, w, h, radius, borderColor, fillColor, 1);
    }

    public static void drawBorderedRoundedRect(GuiGraphics g, int x, int y, int w, int h,
                                                 float radius, int borderColor, int fillColor,
                                                 int borderWidth) {
        if (w <= 0 || h <= 0) return;
        g.flush();
        SdfRenderer.drawRoundedRect(g, x, y, w, h, radius, borderColor);
        g.flush();
        int inset = Math.min(borderWidth, Math.min(w, h) / 2);
        SdfRenderer.drawRoundedRect(g, x + inset, y + inset,
                w - 2 * inset, h - 2 * inset,
                Math.max(0, radius - inset), fillColor);
        g.flush();
    }

    public static void drawInputBox(GuiGraphics g, int x, int y, int w, int h,
                                     float focusT, float radius) {
        drawInputBox(g, x, y, w, h, focusT, 0f, radius);
    }

    public static void drawInputBox(GuiGraphics g, int x, int y, int w, int h,
                                     float focusT, float hoverT, float radius) {
        if (focusT > 0.01f) {
            int borderColor = ColorAnimation.lerpRGB(UiPalette.border(), UiPalette.accent(), focusT);
            drawBorderedRoundedRect(g, x, y, w, h, radius, borderColor, UiPalette.bg(), 1);
            g.flush();
            int glowColor = (UiPalette.accent() & 0x00FFFFFF) | (Math.round(40 * focusT) << 24);
            drawRoundedRect(g, x, y, w, h, radius, glowColor);
        } else if (hoverT > 0.01f) {
            int fillColor = ColorAnimation.lerpRGB(UiPalette.bg(), UiPalette.accent(), hoverT * 0.3f);
            drawRoundedRect(g, x, y, w, h, radius, fillColor);
        } else {
            drawBorderedRoundedRect(g, x, y, w, h, radius, UiPalette.border(), UiPalette.bg(), 1);
        }
    }

    public static void drawPlayIcon(GuiGraphics g, int cx, int cy, int size, int color) {
        int half = size / 2;
        for (int x = 0; x < size; x++) {
            int colH = Math.round((float) (x + 1) / size * size);
            int y1 = cy - colH / 2;
            g.fill(cx - half + x, y1, cx - half + x + 1, y1 + colH, color);
        }
    }

    public static void drawPauseIcon(GuiGraphics g, int cx, int cy, int size, int color) {
        int barW = Math.max(1, size * 3 / 10);
        int gap = Math.max(1, size * 2 / 10);
        int leftX = cx - size / 2 + gap / 2;
        int rightX = leftX + barW + gap;
        int barH = size - gap;
        int y = cy - barH / 2;
        int r = barW / 2;
        drawRoundedRect(g, leftX, y, barW, barH, r, color);
        drawRoundedRect(g, rightX, y, barW, barH, r, color);
    }

    /**
     * 用矢量线段绘制一个等距视角的正方体线框（12 条棱，正方形外接区域）。
     * 投影采用 2:1 等距：顶面为菱形，左/右两棱的斜率为 1/2。
     */
    public static void drawCubeIcon(GuiGraphics g, int x, int y, int size, int color) {
        if (size <= 0) return;
        int half = Math.round(size / 2f);
        int quarter = Math.round(size / 4f);
        int cx = x + half;
        int cy = y + half;

        int[][] v = {
                {cx, cy - half},
                {cx + half, cy},
                {cx, cy + quarter},
                {cx - half, cy},
                {cx, cy},
                {cx + half, cy + quarter},
                {cx, cy + half},
                {cx - half, cy + quarter},
        };

        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7},
        };

        for (int[] e : edges) {
            drawLinePixels(g, v[e[0]][0], v[e[0]][1], v[e[1]][0], v[e[1]][1], color);
        }
    }

    private static void drawLinePixels(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    public static void drawProgressBar(GuiGraphics g, int x, int y, int w, int h,
                                        float progress, int trackColor,
                                        int fillColorStart, int fillColorEnd) {
        drawProgressBar(g, x, y, w, h, progress, trackColor, fillColorStart, fillColorEnd, 0);
    }

    public static void drawProgressBar(GuiGraphics g, int x, int y, int w, int h,
                                        float progress, int trackColor,
                                        int fillColorStart, int fillColorEnd,
                                        int borderColor) {
        if (w <= 0 || h <= 0) return;
        float radius = h / 2f;
        if (borderColor != 0) {
            drawBorderedRoundedRect(g, x, y, w, h, radius, borderColor, trackColor, 1);
            int fillW = Math.round(w * Math.max(0f, Math.min(1f, progress)));
            int innerFillW = Math.max(0, Math.min(fillW, w - 1) - 1);
            if (innerFillW > 0) {
                drawGradientRoundedRect(g, x + 1, y + 1, innerFillW, h - 2,
                        Math.max(0, radius - 1), fillColorStart, fillColorEnd);
            }
        } else {
            drawRoundedRect(g, x, y, w, h, radius, trackColor);
            int fillW = Math.round(w * Math.max(0f, Math.min(1f, progress)));
            if (fillW > 0) {
                drawGradientRoundedRect(g, x, y, fillW, h, radius, fillColorStart, fillColorEnd);
            }
        }
    }

    private static void drawGradientRoundedRect(GuiGraphics g, int x, int y, int w, int h,
                                                  float radius, int colorLeft, int colorRight) {
        if (w <= 0 || h <= 0) return;
        g.flush();

        ShaderInstance shader = UiShaders.roundedRect;
        if (shader == null) return;

        float halfW = w / 2f;
        float halfH = h / 2f;
        float clampedRadius = Math.min(radius, Math.min(halfW, halfH));

        float r1 = ((colorLeft >> 16) & 0xFF) / 255f;
        float g1 = ((colorLeft >> 8) & 0xFF) / 255f;
        float b1 = (colorLeft & 0xFF) / 255f;
        float a1 = ((colorLeft >> 24) & 0xFF) / 255f;

        float r2 = ((colorRight >> 16) & 0xFF) / 255f;
        float g2 = ((colorRight >> 8) & 0xFF) / 255f;
        float b2 = (colorRight & 0xFF) / 255f;
        float a2 = ((colorRight >> 24) & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);

        shader.safeGetUniform("u_Size").set(halfW, halfH);
        shader.safeGetUniform("u_Radius").set(clampedRadius);

        var matrix = g.pose().last().pose();
        var backing = new ByteBufferBuilder(256);
        var builder = new BufferBuilder(backing, VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);

        builder.addVertex(matrix, x, y + h, 0).setUv(-halfW, halfH).setColor(r1, g1, b1, a1);
        builder.addVertex(matrix, x + w, y + h, 0).setUv(halfW, halfH).setColor(r2, g2, b2, a2);
        builder.addVertex(matrix, x + w, y, 0).setUv(halfW, -halfH).setColor(r2, g2, b2, a2);
        builder.addVertex(matrix, x, y, 0).setUv(-halfW, -halfH).setColor(r1, g1, b1, a1);

        MeshData data = builder.build();
        if (data != null) {
            BufferUploader.drawWithShader(data);
        }
        backing.close();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
    }

    public static void drawRoundedOutline(GuiGraphics g, int x, int y, int w, int h,
                                           float radius, int color) {
        drawRoundedOutline(g, x, y, w, h, radius, color, 1);
    }

    public static void drawRoundedOutline(GuiGraphics g, int x, int y, int w, int h,
                                           float radius, int color, int borderWidth) {
        if (w <= 0 || h <= 0 || borderWidth <= 0) return;

        ShaderInstance shader = UiShaders.roundedRectOutline;
        if (shader == null) return;

        float halfW = w / 2f;
        float halfH = h / 2f;
        float clampedRadius = Math.min(radius, Math.min(halfW, halfH));
        int bw = Math.min(borderWidth, Math.min(w, h) / 2);

        int out = 1;
        drawRoundedFillPass(g, shader, x - out, y - out, w + 2 * out, h + 2 * out,
                halfW, halfH, clampedRadius, bw,
                -halfW - out, halfH + out, halfW + out, -halfH - out,
                UiPalette.bg());
        drawRoundedOutlinePass(g, shader, x, y, w, h, halfW, halfH, clampedRadius, bw,
                0x00000000, color);
    }

    private static void drawRoundedFillPass(GuiGraphics g, ShaderInstance shader,
                                              int x, int y, int w, int h,
                                              float halfW, float halfH,
                                              float radius, int bw,
                                              float u0, float v0, float u1, float v1,
                                              int fillColor) {
        g.flush();

        float fr = ((fillColor >> 16) & 0xFF) / 255f;
        float fg = ((fillColor >> 8) & 0xFF) / 255f;
        float fb = (fillColor & 0xFF) / 255f;
        float fa = ((fillColor >> 24) & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);

        shader.safeGetUniform("u_Size").set(halfW, halfH);
        shader.safeGetUniform("u_Radius").set(radius);
        shader.safeGetUniform("u_Thickness").set((float) bw);
        shader.safeGetUniform("u_FillColor").set(fr, fg, fb, fa);

        var matrix = g.pose().last().pose();
        var backing = new ByteBufferBuilder(256);
        var builder = new BufferBuilder(backing, VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);

        builder.addVertex(matrix, x, y + h, 0).setUv(u0, v0).setColor(0f, 0f, 0f, 0f);
        builder.addVertex(matrix, x + w, y + h, 0).setUv(u1, v0).setColor(0f, 0f, 0f, 0f);
        builder.addVertex(matrix, x + w, y, 0).setUv(u1, v1).setColor(0f, 0f, 0f, 0f);
        builder.addVertex(matrix, x, y, 0).setUv(u0, v1).setColor(0f, 0f, 0f, 0f);

        MeshData data = builder.build();
        if (data != null) {
            BufferUploader.drawWithShader(data);
        }
        backing.close();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
    }

    private static void drawRoundedOutlinePass(GuiGraphics g, ShaderInstance shader,
                                                 int x, int y, int w, int h,
                                                 float halfW, float halfH,
                                                 float radius, int bw,
                                                 int fillColor, int outlineColor) {
        g.flush();

        float fr = ((fillColor >> 16) & 0xFF) / 255f;
        float fg = ((fillColor >> 8) & 0xFF) / 255f;
        float fb = (fillColor & 0xFF) / 255f;
        float fa = ((fillColor >> 24) & 0xFF) / 255f;

        float or = ((outlineColor >> 16) & 0xFF) / 255f;
        float og = ((outlineColor >> 8) & 0xFF) / 255f;
        float ob = (outlineColor & 0xFF) / 255f;
        float oa = ((outlineColor >> 24) & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);

        shader.safeGetUniform("u_Size").set(halfW, halfH);
        shader.safeGetUniform("u_Radius").set(radius);
        shader.safeGetUniform("u_Thickness").set((float) bw);
        shader.safeGetUniform("u_FillColor").set(fr, fg, fb, fa);

        var matrix = g.pose().last().pose();
        var backing = new ByteBufferBuilder(256);
        var builder = new BufferBuilder(backing, VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);

        builder.addVertex(matrix, x, y + h, 0).setUv(-halfW, halfH).setColor(or, og, ob, oa);
        builder.addVertex(matrix, x + w, y + h, 0).setUv(halfW, halfH).setColor(or, og, ob, oa);
        builder.addVertex(matrix, x + w, y, 0).setUv(halfW, -halfH).setColor(or, og, ob, oa);
        builder.addVertex(matrix, x, y, 0).setUv(-halfW, -halfH).setColor(or, og, ob, oa);

        MeshData data = builder.build();
        if (data != null) {
            BufferUploader.drawWithShader(data);
        }
        backing.close();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
    }

    public static void drawResetIcon(GuiGraphics g, int x, int y, int size, int color) {
        if (size <= 0) return;
        g.flush();

        ShaderInstance shader = UiShaders.resetIcon;
        if (shader == null) return;

        float half = size / 2f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float gr = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);

        if (shader.safeGetUniform("u_Size") != null)
            shader.safeGetUniform("u_Size").set(half, half);
        if (shader.safeGetUniform("u_Radius") != null)
            shader.safeGetUniform("u_Radius").set(half * 0.7f);
        if (shader.safeGetUniform("u_Thickness") != null)
            shader.safeGetUniform("u_Thickness").set(half * 0.3f);
        if (shader.safeGetUniform("u_Gap") != null)
            shader.safeGetUniform("u_Gap").set(120f);

        var matrix = g.pose().last().pose();
        var backing = new ByteBufferBuilder(256);
        var builder = new BufferBuilder(backing, VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);

        builder.addVertex(matrix, x, y + size, 0).setUv(-half, half).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + size, y + size, 0).setUv(half, half).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + size, y, 0).setUv(half, -half).setColor(r, gr, b, a);
        builder.addVertex(matrix, x, y, 0).setUv(-half, -half).setColor(r, gr, b, a);

        MeshData data = builder.build();
        if (data != null) BufferUploader.drawWithShader(data);
        backing.close();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        float cx = x + half;
        float cy = y + half;
        float ringRadius = half * 0.7f;
        float arrowSize = half * 0.6f;
        float ax = cx + ringRadius * (float) Math.cos(-2.269f);
        float ay = cy + ringRadius * (float) Math.sin(-2.269f);
        int as = Math.round(arrowSize);
        g.pose().pushPose();
        g.pose().translate(ax, ay, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(140f));
        drawChevron(g, -as / 2, -as / 2, as, as, color, 0.5f);
        g.pose().popPose();
    }

    /**
     * 矢量垃圾桶图标（删除）：由盖子横条 + 提手竖条 + 桶身圆角矩形 + 中央分隔线
     * 四段 SDF 圆角矩形组合而成，无贴图。
     *
     * @param x,y   图标左上角（所在区域）
     * @param size  图标边长（px）
     * @param color 图标颜色
     */
    public static void drawTrashIcon(GuiGraphics g, int x, int y, int size, int color) {
        if (size <= 0) return;
        int lidW = (int) Math.round(size * 0.72f);
        int lidH = Math.max(2, (int) Math.round(size * 0.2f));
        int lidX = x + (size - lidW) / 2;
        int handleW = Math.max(2, (int) Math.round(size * 0.14f));
        int handleH = Math.max(2, (int) Math.round(size * 0.18f));
        int handleX = x + (size - handleW) / 2;
        int binW = (int) Math.round(size * 0.56f);
        int binX = x + (size - binW) / 2;
        int binY = y + lidH;
        int divW = Math.max(1, (int) Math.round(size * 0.06f));
        int divX = x + (size - divW) / 2;
        int divY = binY + (int) Math.round(size * 0.15f);
        int divH = Math.max(2, (int) Math.round(size * 0.4f));

        int rad = Math.max(1, size / 8);
        drawRoundedRect(g, lidX, y, lidW, lidH, rad, color);
        drawRoundedRect(g, handleX, y - handleH, handleW, handleH, handleW / 2f, color);
        drawRoundedRect(g, binX, binY, binW, size - lidH, rad, color);
        drawRoundedRect(g, divX, divY, divW, divH, divW / 2f, color);
    }

    public static void drawTexturedRect(GuiGraphics g, int x, int y, int w, int h,
                                         ResourceLocation texture, float u0, float v0,
                                         float u1, float v1, int color) {
        drawTexturedRect(g, x, y, w, h, texture, u0, v0, u1, v1, color, false);
    }

    public static void drawTexturedRect(GuiGraphics g, int x, int y, int w, int h,
                                         ResourceLocation texture, float u0, float v0,
                                         float u1, float v1, int color, boolean mipmap) {
        if (w <= 0 || h <= 0) return;

        g.flush();

        ShaderInstance shader = UiShaders.textured;
        if (shader == null) return;

        float halfW = w / 2f;
        float halfH = h / 2f;

        float r = ((color >> 16) & 0xFF) / 255f;
        float gr = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture);
        if (mipmap) {
            var abstractTex = net.minecraft.client.Minecraft.getInstance().getTextureManager().getTexture(texture);
            if (abstractTex != null) abstractTex.setFilter(true, true);
        }
        RenderSystem.setShader(() -> shader);

        if (shader.safeGetUniform("u_Size") != null)
            shader.safeGetUniform("u_Size").set(halfW, halfH);
        if (shader.safeGetUniform("u_TexBounds") != null)
            shader.safeGetUniform("u_TexBounds").set(u0, v0, u1, v1);

        var matrix = g.pose().last().pose();
        var backing = new ByteBufferBuilder(256);
        var builder = new BufferBuilder(backing, VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);

        builder.addVertex(matrix, x, y + h, 0).setUv(-halfW, halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + w, y + h, 0).setUv(halfW, halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + w, y, 0).setUv(halfW, -halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x, y, 0).setUv(-halfW, -halfH).setColor(r, gr, b, a);

        MeshData data = builder.build();
        if (data != null) BufferUploader.drawWithShader(data);
        backing.close();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
    }

    public static void drawTexturedRect(GuiGraphics g, int x, int y, int w, int h,
                                         ResourceLocation texture, int color) {
        drawTexturedRect(g, x, y, w, h, texture, 0f, 0f, 1f, 1f, color);
    }

    public static void drawVectorFloatingPanel(GuiGraphics g, int x, int y, int w, int h,
                                                boolean hovered, float alpha) {
        if (w <= 0 || h <= 0) return;
        float radius = 6;
        int fillColor = UiPalette.bg();
        int borderColor = hovered ? UiPalette.hoverBorder() : UiPalette.border();
        int fa = (fillColor >> 24) & 0xFF;
        fillColor = (fillColor & 0x00FFFFFF) | (Math.round(fa * alpha) << 24);
        int ba = (borderColor >> 24) & 0xFF;
        borderColor = (borderColor & 0x00FFFFFF) | (Math.round(ba * alpha) << 24);
        drawBorderedRoundedRect(g, x, y, w, h, radius, borderColor, fillColor, 1);
    }

    public static void drawVectorFloatingPanel(GuiGraphics g, int x, int y, int w, int h,
                                                boolean hovered) {
        drawVectorFloatingPanel(g, x, y, w, h, hovered, 1f);
    }

    public static void drawButtonBg(GuiGraphics g, int type, boolean horizontal,
                                     boolean selected, float hoverT,
                                     int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;

        float radius = Math.min(w, h) * 0.2f;
        int color;

        if (selected) {
            color = UiPalette.toggleOn();
        } else {
            color = ColorAnimation.lerpRGB(UiPalette.bg(), UiPalette.accent(), hoverT);
        }

        if (horizontal) {
            switch (type) {
                case 0 -> drawRoundedRectLeftOnly(g, x, y, w, h, radius, color);
                case 1 -> drawRoundedRect(g, x, y, w, h, 0, color);
                case 2 -> drawRoundedRectRightOnly(g, x, y, w, h, radius, color);
                default -> drawRoundedRect(g, x, y, w, h, radius, color);
            }
        } else {
            switch (type) {
                case 0 -> drawRoundedRectBottomOnly(g, x, y, w, h, radius, color);
                case 1 -> drawRoundedRect(g, x, y, w, h, 0, color);
                case 2 -> drawRoundedRectTopOnly(g, x, y, w, h, radius, color);
                default -> drawRoundedRect(g, x, y, w, h, radius, color);
            }
        }
    }
}
