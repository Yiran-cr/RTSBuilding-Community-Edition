package com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.group_button;

import com.rtsbuilding.rtsbuilding.client.culling.RtsRayCylinderCullingState;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.TopBarLayoutHelper;
import com.rtsbuilding.uifw.animate.ColorAnimation;
import com.rtsbuilding.uifw.render.model.TextureInfo;
import com.rtsbuilding.uifw.state.TooltipController;
import com.rtsbuilding.uifw.theme.ThemeManager;
import com.rtsbuilding.uifw.window.button.AbstractButtonGroup;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 射线圆柱剔除开关按钮（顶栏独立按钮，不隶属辅助显示按钮组）。
 *
 * <p>单一图标按钮：点击切换 {@link RtsRayCylinderCullingState} 开关，开启时背景高亮，
 * 悬停显示说明与快捷键（Y）tooltip。样式与 {@link UtilityButtonGroup} 一致
 * （AHQ 三态背景 + 1024x512 明暗双半图标）。</p>
 */
public final class RayCullingButtonGroup extends AbstractButtonGroup {

    /** 射线圆柱剔除开关按钮图标（1024x512 明暗双半，左暗右亮）。 */
    public static final ResourceLocation RAY_CULLING =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/top/button/ray_culling.png");

    private static final ResourceLocation DOWN_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/down_button.png");
    private static final ResourceLocation MIDDLE_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/middle_button.png");
    private static final ResourceLocation UP_BG = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/button/up_button.png");

    /** 剔除按钮 tooltip：悬停即跟随，与剔除开关状态无关。 */
    private final TooltipController tooltip = TooltipController.builder().build();

    public RayCullingButtonGroup() {
        super(Direction.HORIZONTAL, TopBarLayoutHelper.BTN_SIZE, DEFAULT_INNER_GAP, true,
                DOWN_BG, MIDDLE_BG, UP_BG,
                TextureInfo.FilterMode.HQ,
                RAY_CULLING);
        // 单按钮组在 uifw 构造中默认直角中间样式（bgType=1，drawButtonBg case 1 圆角为 0）。
        // 顶栏独立按钮应全圆角：-1 走 drawButtonBg 的 default 分支（radius 全圆角）。
        bgTypeForButton[0] = -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, com.rtsbuilding.uifw.window.button.ButtonGroupLayout group) {
        selected[0] = RtsRayCylinderCullingState.isEnabled();
        super.render(g, mouseX, mouseY, group);
    }

    @Override
    protected void renderExtra(GuiGraphics g, int mouseX, int mouseY, com.rtsbuilding.uifw.window.button.ButtonGroupLayout group) {
        tooltip.update(group.rect(0).contains(mouseX, mouseY), false);
    }

    /** 剔除 tooltip 覆盖层（TopBarPanel 在 UI 层渲染，非父类方法）。 */
    public void renderTooltipOverlay(GuiGraphics g, com.rtsbuilding.uifw.window.button.ButtonGroupLayout group,
            int screenW, int screenH) {
        if (tooltip.shouldRender()) {
            var rect = group.rect(0);
            String keyText = RtsKeyMappings.TOGGLE_RAY_CULLING_KEY.getTranslatedKeyMessage().getString();
            int textColor = ThemeManager.getTextColor();
            int shortcutColor = ColorAnimation.scale(textColor, 0.6f);
            String text = Component.translatable("tooltip.rtsbuilding.ray_culling").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.ray_culling.desc").getString() + "\n"
                    + Component.translatable("tooltip.rtsbuilding.shortcut", keyText).getString();
            tooltip.render(g, rect.x(), rect.y(), rect.width(), rect.height(),
                    text, textColor, shortcutColor, screenW, screenH);
        }
    }

    @Override
    protected void onButtonClick(int index) {
        RtsRayCylinderCullingState.toggle();
    }
}