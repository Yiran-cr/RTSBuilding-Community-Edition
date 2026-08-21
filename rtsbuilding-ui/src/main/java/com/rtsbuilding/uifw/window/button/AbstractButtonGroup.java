package com.rtsbuilding.uifw.window.button;


import com.rtsbuilding.uifw.animate.AnimFloat;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.SpriteRenderer;
import com.rtsbuilding.uifw.render.model.SpriteRegion;
import com.rtsbuilding.uifw.render.model.TextureInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public abstract class AbstractButtonGroup {

    

    
    public static final int TEX_W = 1024;
    
    public static final int TEX_H = 1536;
    
    public static final int ICON_TEX_H = 512;
    
    public static final int HALF_W = 512;
    
    public static final int STATE_H = 512;

    

    
    public static final int DEFAULT_BTN_SIZE = 24;
    
    public static final int DEFAULT_INNER_GAP = 0;

    
    public enum Direction { HORIZONTAL, VERTICAL }

    

    

    
    protected final ResourceLocation[] patternTextures;

    
    private final TextureInfo[] patternTexInfoCache;

    
    private final SpriteRegion[] patternRegions;

    

    
    protected final boolean hasBg;

    
    private final ResourceLocation downBg, middleBg, upBg;

    
    private final TextureInfo[] bgTexInfo;

    
    private final SpriteRegion[] bgStateRegions;

    
    protected final int[] bgTypeForButton;

    

    
    protected final boolean[] selected;

    
    protected final AnimFloat[] hoverStates;

    
    protected final int buttonSize;
    
    protected final int innerGap;
    
    protected final Direction direction;

    

    
    protected AbstractButtonGroup(Direction direction, int buttonSize, int innerGap, boolean hasBg,
                                  ResourceLocation downBg, ResourceLocation middleBg, ResourceLocation upBg,
                                  ResourceLocation... patterns) {
        this(direction, buttonSize, innerGap, hasBg, downBg, middleBg, upBg,
                TextureInfo.FilterMode.PIXEL, patterns);
    }

    /**
     * 带过滤模式的主构造：pattern 贴图统一使用指定 {@link TextureInfo.FilterMode}。
     * 默认走 {@link TextureInfo.FilterMode#PIXEL}（像素风，无插值）；传 {@code HQ} 时需由宿主
     * 提前把对应纹理注册为带 mipmap 的加载器，否则会导致纹理不完整。
     */
    protected AbstractButtonGroup(Direction direction, int buttonSize, int innerGap, boolean hasBg,
                                  ResourceLocation downBg, ResourceLocation middleBg, ResourceLocation upBg,
                                  TextureInfo.FilterMode filterMode, ResourceLocation... patterns) {
        this.direction = direction;
        this.buttonSize = buttonSize;
        this.innerGap = innerGap;
        this.hasBg = hasBg;
        this.downBg = downBg;
        this.middleBg = middleBg;
        this.upBg = upBg;
        this.patternTextures = patterns;
        int n = patterns.length;

        
        this.selected = new boolean[n];
        this.hoverStates = new AnimFloat[n];
        for (int i = 0; i < n; i++) {
            this.hoverStates[i] = AnimFloat.hover();
        }

        
        this.patternTexInfoCache = new TextureInfo[n];
        if (hasBg) {
            
            this.patternRegions = new SpriteRegion[n];
            for (int i = 0; i < n; i++) {
                if (patterns[i] == null) continue; 
                this.patternTexInfoCache[i] = new TextureInfo(
                        patterns[i], TEX_W, ICON_TEX_H,
                        TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
                        filterMode);
                this.patternRegions[i] = new SpriteRegion(patternTexInfoCache[i], 0, 0, HALF_W, ICON_TEX_H);
            }
        } else {
            
            this.patternRegions = new SpriteRegion[n * 3];
            for (int i = 0; i < n; i++) {
                this.patternTexInfoCache[i] = new TextureInfo(
                        patterns[i], TEX_W, TEX_H,
                        TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
                        filterMode);
                this.patternRegions[i * 3]     = new SpriteRegion(patternTexInfoCache[i], 0, 0,          HALF_W, STATE_H);
                this.patternRegions[i * 3 + 1] = new SpriteRegion(patternTexInfoCache[i], 0, STATE_H,    HALF_W, STATE_H);
                this.patternRegions[i * 3 + 2] = new SpriteRegion(patternTexInfoCache[i], 0, STATE_H * 2, HALF_W, STATE_H);
            }
        }

        
        if (hasBg) {
            this.bgTexInfo = null;
            this.bgStateRegions = null;

            
            this.bgTypeForButton = new int[n];
            for (int i = 0; i < n; i++) {
                if (n == 1) {
                    bgTypeForButton[i] = 1; 
                } else {
                    // 首/尾按钮用圆角端样式，中间用直角；横向与纵向布局的端号一致
                    bgTypeForButton[i] = i == 0 ? 2 : (i == n - 1 ? 0 : 1);
                }
            }
        } else {
            this.bgTexInfo = null;
            this.bgStateRegions = null;
            this.bgTypeForButton = null;
        }
    }

    
    protected AbstractButtonGroup(ResourceLocation... textures) {
        this(Direction.VERTICAL, DEFAULT_BTN_SIZE, DEFAULT_INNER_GAP, false, null, null, null, textures);
    }

    

    
    public final int buttonCount() {
        return patternTextures.length;
    }

    
    public final int totalHeight() {
        int n = patternTextures.length;
        return n * buttonSize + (n - 1) * innerGap;
    }

    

    public void render(GuiGraphics g, int mouseX, int mouseY, ButtonGroupLayout group) {
        int n = patternTextures.length;
        
        if (hasBg) {
            for (int i = 0; i < n; i++) {
                renderSingleBg(g, mouseX, mouseY, i, group.rect(i).x(), group.rect(i).y());
            }
        }
        
        for (int i = 0; i < n; i++) {
            renderSinglePattern(g, mouseX, mouseY, i, group.rect(i).x(), group.rect(i).y());
        }
        renderExtra(g, mouseX, mouseY, group);
    }

    

    public void render(GuiGraphics g, int mouseX, int mouseY, int originX, int originY) {
        int n = patternTextures.length;
        
        if (hasBg) {
            for (int i = 0; i < n; i++) {
                int bx = direction == Direction.HORIZONTAL
                        ? originX + i * (buttonSize + innerGap) : originX;
                int by = direction == Direction.VERTICAL
                        ? originY + i * (buttonSize + innerGap) : originY;
                renderSingleBg(g, mouseX, mouseY, i, bx, by);
            }
        }
        
        for (int i = 0; i < n; i++) {
            int bx = direction == Direction.HORIZONTAL
                    ? originX + i * (buttonSize + innerGap) : originX;
            int by = direction == Direction.VERTICAL
                    ? originY + i * (buttonSize + innerGap) : originY;
            renderSinglePattern(g, mouseX, mouseY, i, bx, by);
        }
    }

    

    
    protected void renderSingleBg(GuiGraphics g, int mouseX, int mouseY, int index, int bx, int by) {
        boolean hovering = mouseX >= bx && mouseX < bx + buttonSize
                && mouseY >= by && mouseY < by + buttonSize;
        float hoverT = this.hoverStates[index].track(hovering);
        int bt = bgTypeForButton[index];
        SdfRenderer.drawButtonBg(g, bt, direction == Direction.HORIZONTAL,
                selected[index], hoverT, bx, by, buttonSize, buttonSize);
    }

    

    
    protected void renderSinglePattern(GuiGraphics g, int mouseX, int mouseY, int index, int bx, int by) {
        boolean hovering = mouseX >= bx && mouseX < bx + buttonSize
                && mouseY >= by && mouseY < by + buttonSize;

        if (hasBg) {
            
            if (patternTextures[index] == null) return; 
            SpriteRenderer.drawSprite(g, patternRegions[index].withTheme(),
                    bx, by, buttonSize, buttonSize);
        } else {
            
            float hoverT = this.hoverStates[index].track(hovering);
            int si = index * 3;
            SpriteRenderer.drawStateSprite(g,
                    patternRegions[si],     
                    patternRegions[si + 1], 
                    patternRegions[si + 2], 
                    selected[index],
                    hoverT,
                    bx, by, buttonSize, buttonSize);
        }
    }

    

    protected void renderExtra(GuiGraphics g, int mouseX, int mouseY, ButtonGroupLayout group) {}

    

    public boolean mouseClicked(int mx, int my, ButtonGroupLayout group) {
        for (int i = 0; i < patternTextures.length; i++) {
            var r = group.rect(i);
            if (mx >= r.x() && mx < r.x() + buttonSize && my >= r.y() && my < r.y() + buttonSize) {
                onButtonClick(i);
                return true;
            }
        }
        return false;
    }

    

    public int mouseClicked(double mx, double my, int originX, int originY) {
        for (int i = 0; i < patternTextures.length; i++) {
            int bx = direction == Direction.HORIZONTAL
                    ? originX + i * (buttonSize + innerGap) : originX;
            int by = direction == Direction.VERTICAL
                    ? originY + i * (buttonSize + innerGap) : originY;
            if (mx >= bx && mx < bx + buttonSize && my >= by && my < by + buttonSize) {
                onButtonClick(i);
                return i;
            }
        }
        return -1;
    }

    

    protected void onButtonClick(int index) {
        java.util.Arrays.fill(selected, false);
        selected[index] = true;
    }

    

    public final void clearSelection() {
        java.util.Arrays.fill(selected, false);
    }

    public final boolean isSelected(int index) {
        return index >= 0 && index < selected.length && selected[index];
    }

    /**
     * 直接设置指定按钮的选中态（供 UI 状态持久化恢复等场景使用）。
     * 仅写入选中位，不触发互斥/联动业务逻辑——需要互斥的组由调用方组合设置。
     */
    public final void setSelected(int index, boolean value) {
        if (index >= 0 && index < selected.length) {
            selected[index] = value;
        }
    }

    

    public void tick() {
    }
}
