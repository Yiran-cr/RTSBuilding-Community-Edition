package com.rtsbuilding.uifw.window.window;

import com.rtsbuilding.uifw.component.CloseButton;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.render.UiPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 浮窗面板窗口框渲染：纯矢量（SDF 圆角背景 + 阴影）、标题栏、标题文本与矢量关闭按钮，无贴图资源。
 */
public final class WindowFrameRenderer {

    private WindowFrameRenderer() {}

    public static CloseButton createCloseButton() {
        return new CloseButton();
    }

    public record Context(
            int windowX,
            int windowY,
            int windowWidth,
            int windowHeight,
            int titleBarHeight,
            int panelBgColor,
            int panelHoverBgColor,
            int panelBorderColor,
            int titleBarBgColor,
            int titleTextColor,
            Component title,
            boolean closable,
            CloseButton closeButton,
            float hoverAnimProgress,
            boolean useSdfBackground
    ) {}

    public static void renderFrame(GuiGraphics g, int mouseX, int mouseY, Context ctx) {
        renderSdfPanelBackground(g, ctx);
        renderTitleBar(g, mouseX, mouseY, ctx);
    }

    private static void renderSdfPanelBackground(GuiGraphics g, Context ctx) {
        float t = ctx.hoverAnimProgress();
        int wx = ctx.windowX(), wy = ctx.windowY();
        int ww = ctx.windowWidth(), wh = ctx.windowHeight();
        int radius = 8;
        int borderWidth = 1;
        int defaultBorder = ctx.panelBorderColor();
        int hoverBorder = ctx.panelHoverBgColor();
        int borderColor = lerpArgb(defaultBorder, hoverBorder, t);

        int shExpand = Math.round(4 + t * 4);
        float shAlpha = 0.10f + t * 0.25f;
        int shX = wx - shExpand, shY = wy - shExpand;
        int shW = ww + 2 * shExpand, shH = wh + 2 * shExpand;
        int shRad1 = radius + shExpand;
        int shRad2 = radius + shExpand + 2;
        SdfRenderer.drawRoundedRect(g, shX, shY, shW, shH, shRad1, UiPalette.get("window_shadow"), shAlpha);
        g.flush();
        SdfRenderer.drawRoundedRect(g, shX, shY, shW, shH, shRad2, UiPalette.get("window_shadow_soft"), shAlpha * 0.5f);
        g.flush();

        SdfRenderer.drawBorderedRoundedRect(g, wx, wy, ww, wh, radius,
                borderColor, ctx.panelBgColor(), borderWidth);
    }

    private static int lerpArgb(int from, int to, float t) {
        if (t <= 0.005f) return from;
        if (t >= 0.995f) return to;
        int a = lerpComp((from >> 24) & 0xFF, (to >> 24) & 0xFF, t);
        int r = lerpComp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int gr = lerpComp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerpComp(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    private static int lerpComp(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }

    private static void renderTitleBar(GuiGraphics g, int mouseX, int mouseY, Context ctx) {
        int titleH = ctx.titleBarHeight();
        if (titleH <= 0) return;

        renderTitleBarBackground(g, ctx, titleH);
        renderTitleText(g, ctx, titleH);

        if (ctx.closable() && ctx.closeButton() != null) {
            renderCloseButton(g, mouseX, mouseY, ctx);
        }
    }

    private static void renderTitleBarBackground(GuiGraphics g, Context ctx, int titleH) {
        int tint = ctx.titleBarBgColor();
        SdfRenderer.drawRoundedRectTopOnly(g, ctx.windowX() + 1, ctx.windowY() + 1,
                ctx.windowWidth() - 2, titleH, 7, tint, 1.0f);
    }

    private static void renderTitleText(GuiGraphics g, Context ctx, int titleH) {
        String title = TextRenderer.trimToWidth(Minecraft.getInstance().font, ctx.title().getString(),
                Math.max(8, ctx.windowWidth() - 36));
        int textY = ctx.windowY() + Math.max(1, (titleH - Minecraft.getInstance().font.lineHeight) / 2) + 2;
        TextRenderer.draw(g, title, ctx.windowX() + 8, textY, ctx.titleTextColor());
    }

    private static void renderCloseButton(GuiGraphics g, int mouseX, int mouseY, Context ctx) {
        int btnX = ctx.windowX() + ctx.windowWidth() - CloseButton.SIZE - 5;
        int btnY = ctx.windowY() + 5;
        CloseButton btn = ctx.closeButton();
        btn.setX(btnX);
        btn.setY(btnY);
        btn.render(g, mouseX, mouseY);
    }
}
