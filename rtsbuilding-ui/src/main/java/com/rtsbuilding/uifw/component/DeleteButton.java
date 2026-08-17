package com.rtsbuilding.uifw.component;

import com.rtsbuilding.uifw.animate.AnimFloat;
import com.rtsbuilding.uifw.animate.ColorAnimation;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.render.UiPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 文字删除按钮：SDF 圆角背景（悬停渐变）+ 本地化文字（中文「删除」/英文「Del」）。
 * <p>支持确认态：{@code confirm} 为 true 时背景切换为警示红、文字切换为确认文本
 * （「删除?」/「Del?」），提示需要再次点击才执行删除（防误删）。</p>
 * <p>按钮宽度按当前语言文字的渲染宽度自适应（普通态与确认态取较宽者，保证
 * 二次确认切换时宽度不跳动、行布局稳定），高度固定 {@link #SIZE}。</p>
 */
public final class DeleteButton {

    /** 按钮高度（px）。 */
    public static final int SIZE = 14;
    /** 文字左右内边距（px）。 */
    private static final int TEXT_PAD = 6;
    private static final int RADIUS = 3;

    /** 普通态按钮文字 key（走 uifw 自己的语言文件）。 */
    private static final String LABEL_KEY = "button.uifw.delete";
    /** 确认态按钮文字 key。 */
    private static final String CONFIRM_KEY = "button.uifw.delete_confirm";

    private final AnimFloat hoverState = AnimFloat.hover();

    /**
     * 按钮宽度（px）：普通态与确认态文字渲染宽度的较大者 + 左右内边距，
     * 保证确认切换时按钮宽度不变、渲染与命中坐标一致。
     */
    public static int width() {
        Font font = Minecraft.getInstance().font;
        int textW = Math.max(font.width(Component.translatable(LABEL_KEY).getString()),
                font.width(Component.translatable(CONFIRM_KEY).getString()));
        return textW + TEXT_PAD * 2;
    }

    /**
     * 渲染按钮。
     *
     * @param x,y    按钮左上角
     * @param confirm 是否处于「二次确认」状态
     */
    public void render(GuiGraphics g, int mouseX, int mouseY, int x, int y, boolean confirm) {
        boolean hovering = hit(mouseX, mouseY, x, y);
        float t = this.hoverState.track(hovering);

        int w = width();
        int base = confirm ? UiPalette.get("list_delete") : UiPalette.get("list_btn");
        int bg = ColorAnimation.lerpRGB(base, UiPalette.get("list_btn_hover"), t);
        SdfRenderer.drawRoundedRect(g, x, y, w, SIZE, RADIUS, bg);

        Font font = Minecraft.getInstance().font;
        String text = Component.translatable(confirm ? CONFIRM_KEY : LABEL_KEY).getString();
        TextRenderer.drawCentered(g, font, text,
                x + w / 2, y + (SIZE - font.lineHeight) / 2 + 1, UiPalette.get("tooltip_text"));
    }

    /** 命中检测（与渲染坐标一致）。 */
    public boolean hit(double mx, double my, int x, int y) {
        return mx >= x && mx < x + width() && my >= y && my < y + SIZE;
    }
}
