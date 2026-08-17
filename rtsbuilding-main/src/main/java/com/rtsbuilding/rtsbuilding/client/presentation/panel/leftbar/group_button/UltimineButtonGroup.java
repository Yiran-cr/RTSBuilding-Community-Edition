package com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.group_button;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.uifw.window.button.AbstractButtonGroup;
import com.rtsbuilding.uifw.animate.ColorAnimation;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.render.model.TextureInfo;
import com.rtsbuilding.uifw.state.TooltipController;
import com.rtsbuilding.uifw.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 左侧栏连锁挖掘（Ultimine）按钮。
 * <p>连锁挖掘的启用由按住 {@code RtsKeyMappings.ULTIMINE_KEY}（默认 `` ` ``/~ 键）控制，
 * 本按钮仅作为<b>状态指示灯</b>：激活状态（选中点亮）每帧由 {@link #setActive} 同步按键状态，
 * 点击不改变启用状态。</p>
 */
public final class UltimineButtonGroup extends AbstractButtonGroup {

    
    public static final ResourceLocation ULTIMINE_BTN = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/button/ultimine.png");

    
    private boolean show = false;

    
    private final TooltipController ultimineBtnTooltip = TooltipController.builder()
            .direction(TooltipController.Direction.RIGHT).build();

    public UltimineButtonGroup() {
        super(Direction.VERTICAL, DEFAULT_BTN_SIZE, DEFAULT_INNER_GAP, true,
                null, null, null,
                TextureInfo.FilterMode.HQ,
                ULTIMINE_BTN);
    }

    
    public void setShow(boolean show) {
        this.show = show;
        if (!show) {
            selected[0] = false;
        }
    }

    
    public boolean isShow() {
        return show;
    }

    /**
     * 同步连锁挖掘激活状态（指示灯点亮）：启用由按住按键控制，选中态仅反映按键状态。
     */
    public void setActive(boolean active) {
        selected[0] = active;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, int originX, int originY) {
        if (!show) return;
        renderOnlyBg(g, mouseX, mouseY, 0, originX, originY);
        renderSinglePattern(g, mouseX, mouseY, 0, originX, originY);
    }

    
    private void renderOnlyBg(GuiGraphics g, int mouseX, int mouseY, int index, int bx, int by) {
        boolean hovering = mouseX >= bx && mouseX < bx + buttonSize
                && mouseY >= by && mouseY < by + buttonSize;
        float hoverT = this.hoverStates[index].track(hovering);
        SdfRenderer.drawButtonBg(g, 3, false, selected[index], hoverT,
                bx, by, buttonSize, buttonSize);
    }

    @Override
    public int mouseClicked(double mx, double my, int originX, int originY) {
        if (!show) return -1;
        if (mx >= originX && mx < originX + buttonSize
                && my >= originY && my < originY + buttonSize) {
            onButtonClick(0);
            return 0;
        }
        return -1;
    }

    @Override
    protected void onButtonClick(int index) {
        // 连锁挖掘启用由按住按键控制，按钮仅作状态指示灯，点击不改变启用状态。
        // 选中态由 LeftSidebarPanel.render 每帧通过 setActive 同步按键状态。
    }

    
    public void tickTooltips(int mouseX, int mouseY, int originX, int originY) {
        if (!show) {
            ultimineBtnTooltip.update(false, false);
            return;
        }
        boolean hover = mouseX >= originX && mouseX < originX + buttonSize
                && mouseY >= originY && mouseY < originY + buttonSize;
        ultimineBtnTooltip.update(hover, false);
    }

    
    public void renderTooltipOverlay(GuiGraphics g, int originX, int originY,
                                     int screenW, int screenH) {
        if (!show || !ultimineBtnTooltip.shouldRender()) return;
        int textColor = ThemeManager.getTextColor();
        int shortcutColor = ColorAnimation.scale(textColor, 0.6f);
        String text = Component.translatable("tooltip.rtsbuilding.left.ultimine").getString() + "\n"
                + Component.translatable("tooltip.rtsbuilding.left.ultimine.desc").getString();
        renderTooltipRight(g, ultimineBtnTooltip, originX, originY, buttonSize, buttonSize,
                text, textColor, shortcutColor, screenW, screenH);
    }

    
    private static void renderTooltipRight(GuiGraphics g, TooltipController tooltip,
                                            int btnX, int btnY, int btnW, int btnH,
                                            String text, int color, int shortcutColor,
                                            int screenW, int screenH) {
        float alpha = tooltip.getAlpha();
        var font = Minecraft.getInstance().font;

        String[] lines = text.split("\n");
        int lineHeight = font.lineHeight;
        int lineGap = 1;
        float scaledLineH = lineHeight * 0.75f;
        float scaledLineGap = lineGap * 0.75f;
        int maxLineW = 0;
        for (String line : lines) {
            maxLineW = Math.max(maxLineW, font.width(line));
        }
        int padH = 6, padV = 3;
        int tipW = (int)(maxLineW * 0.75f) + padH * 2;
        int tipH = (int)(scaledLineH * lines.length + scaledLineGap * (lines.length - 1)) + padV * 2;

        
        int tipX = btnX + btnW + 2;
        int tipY = btnY;
        tipX = Math.max(0, Math.min(tipX, screenW - tipW));
        tipY = Math.max(0, Math.min(tipY, screenH - tipH));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        SdfRenderer.drawVectorFloatingPanel(g, tipX, tipY, tipW, tipH, false, alpha);

        float textY = tipY + padV;
        for (int i = 0; i < lines.length; i++) {
            int lineColor = (i == lines.length - 1) ? shortcutColor : color;
            g.pose().pushPose();
            g.pose().translate(tipX + padH, textY, 0);
            g.pose().scale(0.75f, 0.75f, 1.0f);
            TextRenderer.draw(g, lines[i], 0, 0, lineColor);
            g.pose().popPose();
            textY += scaledLineH + scaledLineGap;
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }
}
