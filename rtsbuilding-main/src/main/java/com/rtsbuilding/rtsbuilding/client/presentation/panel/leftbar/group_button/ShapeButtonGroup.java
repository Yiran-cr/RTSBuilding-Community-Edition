package com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.group_button;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.button.AbstractButtonGroup;
import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.state.TooltipController;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 建造形状按钮组：单方块、线、墙、平面、体、圆面、球。
 * 背景沿用其他按钮组的矢量绘制（{@link com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer#drawButtonBg}），
 * 图案使用与其余左面板按钮一致的贴图（1024x512 水平主题对），并缩小到按钮的 3/4 居中绘制。
 * 互斥单选，默认选中单方块。
 */
public final class ShapeButtonGroup extends AbstractButtonGroup {

    public static final int SINGLE_BLOCK = 0;
    public static final int LINE = 1;
    public static final int WALL = 2;
    public static final int PLANE = 3;
    public static final int SOLID = 4;
    public static final int CIRCLE_FACE = 5;
    public static final int SPHERE = 6;

    /** 激活模式：建造。 */
    public static final int MODE_CONSTRUCTION = 0;
    /** 激活模式：破坏。 */
    public static final int MODE_DESTRUCTION = 1;

    private static final int BUTTON_COUNT = 7;

    /** 图案相对按钮的缩放比例（缩小到 3/4）。 */
    private static final float ICON_SCALE = 0.75f;

    /** 保持满尺寸的按钮：圆面、球（圆形图案不缩小）。 */
    private static boolean isFullSizeIcon(int index) {
        return index == CIRCLE_FACE || index == SPHERE;
    }

    private static final ResourceLocation SINGLE_TEX = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/right_button/single.png");
    private static final ResourceLocation LINE_TEX = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/right_button/line.png");
    private static final ResourceLocation WALL_TEX = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/right_button/wall.png");
    private static final ResourceLocation PLANE_TEX = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/right_button/noodle.png");
    private static final ResourceLocation SOLID_TEX = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/right_button/body.png");
    private static final ResourceLocation CIRCLE_FACE_TEX = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/right_button/round.png");
    private static final ResourceLocation SPHERE_TEX = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/left/right_button/ball.png");

    private static final String[] KEY_NAMES = {
            "single_block", "line", "wall", "plane", "solid", "circle_face", "sphere"
    };

    /** 自行构建的贴图 region（与 {@link AbstractButtonGroup} 的 hasBg 模式一致），供缩小居中绘制。 */
    private final SpriteRegion[] regions = new SpriteRegion[BUTTON_COUNT];

    private final TooltipController[] tooltips = new TooltipController[BUTTON_COUNT];

    /** 是否显示：仅建造模式且启用建造/破坏时显示。 */
    private boolean show = false;

    /** 建造/破坏各自的形状选择，索引见 {@link #MODE_CONSTRUCTION}/{@link #MODE_DESTRUCTION}。 */
    private final int[] modeShapes = {SINGLE_BLOCK, SINGLE_BLOCK};

    /** 当前激活的模式（建造或破坏）。 */
    private int activeMode = MODE_CONSTRUCTION;

    public ShapeButtonGroup() {
        super(Direction.VERTICAL, DEFAULT_BTN_SIZE, DEFAULT_INNER_GAP, true,
                null, null, null,
                SINGLE_TEX, LINE_TEX, WALL_TEX, PLANE_TEX, SOLID_TEX, CIRCLE_FACE_TEX, SPHERE_TEX);
        selected[SINGLE_BLOCK] = true;
        for (int i = 0; i < BUTTON_COUNT; i++) {
            if (patternTextures[i] == null) continue;
            var info = new TextureInfo(patternTextures[i], TEX_W, ICON_TEX_H,
                    TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);
            regions[i] = new SpriteRegion(info, 0, 0, HALF_W, ICON_TEX_H);
        }
        for (int i = 0; i < BUTTON_COUNT; i++) {
            tooltips[i] = TooltipController.builder()
                    .direction(TooltipController.Direction.LEFT).build();
        }
    }

    /**
     * 设置显示状态。隐藏时清空选中态；重新显示时同步到当前激活模式的形状。
     */
    public void setShow(boolean show) {
        if (this.show == show) return;
        this.show = show;
        if (!show) {
            java.util.Arrays.fill(selected, false);
        } else {
            syncSelectionToActiveMode();
        }
    }

    public boolean isShow() {
        return show;
    }

    /**
     * 切换激活模式（建造/破坏）。启用当前模式时，另一模式（未激活）强制回到默认单方块。
     */
    public void setActiveMode(int mode) {
        if (mode != MODE_CONSTRUCTION && mode != MODE_DESTRUCTION) return;
        if (mode == activeMode) return;
        activeMode = mode;
        modeShapes[otherMode(mode)] = SINGLE_BLOCK;
        syncSelectionToActiveMode();
    }

    public int getActiveMode() {
        return activeMode;
    }

    private static int otherMode(int mode) {
        return mode == MODE_CONSTRUCTION ? MODE_DESTRUCTION : MODE_CONSTRUCTION;
    }

    private void syncSelectionToActiveMode() {
        java.util.Arrays.fill(selected, false);
        selected[modeShapes[activeMode]] = true;
    }

    /** 当前激活模式下的选中形状索引，见 {@link #SINGLE_BLOCK} 等常量。 */
    public int getSelectedShape() {
        return modeShapes[activeMode];
    }

    /** 建造模式（{@link #MODE_CONSTRUCTION}）下选中的形状索引，见 {@link #SINGLE_BLOCK} 等常量。 */
    public int getConstructionSelectedShape() {
        return modeShapes[MODE_CONSTRUCTION];
    }

    /** 建造模式（{@link #MODE_CONSTRUCTION}）下是否选中单方块形状。 */
    public boolean isSingleBlockConstructionSelected() {
        return modeShapes[MODE_CONSTRUCTION] == SINGLE_BLOCK;
    }

    /** 破坏模式（{@link #MODE_DESTRUCTION}）下选中的形状索引，见 {@link #SINGLE_BLOCK} 等常量。 */
    public int getDestructionSelectedShape() {
        return modeShapes[MODE_DESTRUCTION];
    }

    /** 破坏模式（{@link #MODE_DESTRUCTION}）下是否选中单方块形状。 */
    public boolean isSingleBlockDestructionSelected() {
        return modeShapes[MODE_DESTRUCTION] == SINGLE_BLOCK;
    }

    @Override
    protected void onButtonClick(int index) {
        super.onButtonClick(index);
        modeShapes[activeMode] = index;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, int originX, int originY) {
        if (!show) return;
        int n = patternTextures.length;
        for (int i = 0; i < n; i++) {
            int by = originY + i * (buttonSize + innerGap);
            if (hasBg) {
                renderSingleBg(g, mouseX, mouseY, i, originX, by);
            }
        }
        for (int i = 0; i < n; i++) {
            int by = originY + i * (buttonSize + innerGap);
            drawScaledPattern(g, i, originX, by);
        }
    }

    /** 图案绘制：圆面/球满尺寸，其余缩小到 3/4 并居中。 */
    private void drawScaledPattern(GuiGraphics g, int index, int bx, int by) {
        if (regions[index] == null) return;
        if (isFullSizeIcon(index)) {
            SpriteRenderer.drawSprite(g, regions[index].withTheme(), bx, by, buttonSize, buttonSize);
            return;
        }
        int w = Math.max(1, Math.round(buttonSize * ICON_SCALE));
        int h = Math.max(1, Math.round(buttonSize * ICON_SCALE));
        int dx = bx + (buttonSize - w) / 2;
        int dy = by + (buttonSize - h) / 2;
        SpriteRenderer.drawSprite(g, regions[index].withTheme(), dx, dy, w, h);
    }

    // ── Tooltip ──────────────────────────────────────────────────────

    public void tickTooltips(int mouseX, int mouseY, int originX, int originY) {
        if (!show) {
            for (TooltipController t : tooltips) {
                t.update(false, false);
            }
            return;
        }
        for (int i = 0; i < BUTTON_COUNT; i++) {
            int by = originY + i * (buttonSize + innerGap);
            boolean hover = mouseX >= originX && mouseX < originX + buttonSize
                    && mouseY >= by && mouseY < by + buttonSize;
            tooltips[i].update(hover, false);
        }
    }

    public void renderTooltipOverlay(GuiGraphics g, int originX, int originY,
                                     int screenW, int screenH) {
        if (!show) return;
        int textColor = ThemeManager.getTextColor();
        int shortcutColor = ColorAnimation.scale(textColor, 0.6f);
        for (int i = 0; i < BUTTON_COUNT; i++) {
            int by = originY + i * (buttonSize + innerGap);
            if (tooltips[i].shouldRender()) {
                String text = Component.translatable("tooltip.rtsbuilding.left.shape." + KEY_NAMES[i]).getString() + "\n"
                        + Component.translatable("tooltip.rtsbuilding.left.shape." + KEY_NAMES[i] + ".desc").getString();
                tooltips[i].render(new TooltipController.RenderContext(
                        g, originX, by, buttonSize, buttonSize,
                        text, textColor, shortcutColor, screenW, screenH));
            }
        }
    }
}
