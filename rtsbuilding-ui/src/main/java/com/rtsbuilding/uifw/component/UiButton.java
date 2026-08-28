package com.rtsbuilding.uifw.component;

import com.rtsbuilding.uifw.animate.AnimFloat;
import com.rtsbuilding.uifw.animate.ColorAnimation;
import com.rtsbuilding.uifw.render.CrossFadeRenderer;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.SpriteRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.render.UiMetrics;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.render.model.SpriteRegion;
import com.rtsbuilding.uifw.render.model.TextureInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class UiButton extends AbstractButton {

    public interface OnPress {
        void onPress(UiButton button);
    }

    private final OnPress onPress;
    
    private final TextureInfo texInfo;
    
    private final SpriteRegion normalRegion;
    
    private final SpriteRegion hoveredRegion;

    private final AnimFloat hoverState = AnimFloat.hover();

    
    public UiButton(int x, int y, int width, int height, Component message,
                     ResourceLocation textureLocation, int textureU, int textureV,
                     int textureWidth, int textureHeight, int hoverTextureV, int hoverTextureHeight,
                     int fullTextureWidth, int fullTextureHeight, OnPress onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
        
        if (textureLocation != null && textureWidth > 0 && textureHeight > 0) {
            this.texInfo = new TextureInfo(
                    textureLocation, fullTextureWidth, fullTextureHeight,
                    TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
                    TextureInfo.FilterMode.PIXEL);
            this.normalRegion = new SpriteRegion(texInfo, textureU, textureV, textureWidth, textureHeight);
            this.hoveredRegion = new SpriteRegion(texInfo, textureU, hoverTextureV, textureWidth, hoverTextureHeight);
        } else {
            this.texInfo = null;
            this.normalRegion = null;
            this.hoveredRegion = null;
        }
    }

    @Override
    public void onPress() {
        this.onPress.onPress(this);
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();

        
        if (normalRegion != null && hoveredRegion != null) {
            
            renderWithSprite(guiGraphics);
        } else {
            
            renderWithSolidColor(guiGraphics);
        }

        
        float hoverT = this.hoverState.track(isHovered);
        int textColor = this.active
                ? ColorAnimation.lerpRGB(UiPalette.get("button_text"), UiPalette.get("button_text_hover"), hoverT)
                : UiPalette.get("button_text_disabled");
        String label = TextRenderer.trimToWidth(minecraft.font, this.getMessage().getString(),
                Math.max(4, this.width - 8));
        int textWidth = minecraft.font.width(label);
        int textX = this.getX() + (this.width - textWidth) / 2;
        int textY = this.getY() + (this.height - 8) / 2;

        
        if (!label.isEmpty()) {
            TextRenderer.draw(guiGraphics, label, textX, textY, textColor);
        }
    }

    
    private void renderWithSprite(GuiGraphics guiGraphics) {
        float t = this.hoverState.get();
        CrossFadeRenderer.render(guiGraphics, t,
                () -> SpriteRenderer.drawSprite(guiGraphics, normalRegion.withTheme(), this.getX(), this.getY(), this.width, this.height),
                () -> SpriteRenderer.drawSprite(guiGraphics, hoveredRegion.withTheme(), this.getX(), this.getY(), this.width, this.height));
    }

    
    private void renderWithSolidColor(GuiGraphics guiGraphics) {
        // 矢量回退：与整套按钮体系统一为 SDF 圆角 + 描边（不再使用直角硬边框凸起风）
        float t = this.hoverState.get();
        int backgroundColor = ColorAnimation.lerpRGB(UiPalette.get("button_bg"), UiPalette.get("button_hover_bg"), t);
        SdfRenderer.drawBorderedRoundedRect(guiGraphics, this.getX(), this.getY(), this.width, this.height,
                UiMetrics.RADIUS_CONTROL, UiPalette.get("button_border_dark"), backgroundColor);
    }

    

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }

}
