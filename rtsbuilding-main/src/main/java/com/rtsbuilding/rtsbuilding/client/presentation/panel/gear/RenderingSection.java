package com.rtsbuilding.rtsbuilding.client.presentation.panel.gear;

import com.rtsbuilding.rtsbuilding.client.render.pass.BoundaryPass;
import com.rtsbuilding.rtsbuilding.client.render.pass.BoxSelectionPass;
import com.rtsbuilding.rtsbuilding.client.render.pass.InteractionTargetPass;
import com.rtsbuilding.rtsbuilding.client.render.pass.LineBrushRenderPass;
import com.rtsbuilding.rtsbuilding.client.render.pass.LinkedStoragePass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.uifw.animate.AnimFloat;
import com.rtsbuilding.uifw.component.ColorPickerButton;
import com.rtsbuilding.uifw.component.ResetButton;
import com.rtsbuilding.uifw.component.ScaleSliderComponent;
import com.rtsbuilding.uifw.component.ToggleSwitch;
import com.rtsbuilding.uifw.component.color.ColorPickerPanel;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.state.TooltipController;
import com.rtsbuilding.uifw.window.component.SettingsSection;
import com.rtsbuilding.uifw.window.window.UiPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 齿轮设置面板中的「渲染」分区：动画/深度开关、透明度滑块与多组世界渲染颜色配置。
 * 世界渲染色（边界墙/目标/框选/线刷）为业务配置色，保留硬编码由设置面板写入渲染 pass。
 */
public class RenderingSection extends SettingsSection {

    private static final double ALPHA_MIN = 0.02;
    private static final double ALPHA_MAX = 0.8;
    private static final int COLOR_BLOCK_GAP = 4;
    private static final int RESET_BTN_GAP = 4;

    private static final int ROW_FLOW = 0;
    private static final int ROW_SMOOTH = 1;
    private static final int ROW_UI_SMOOTH = 2;
    private static final int ROW_DEPTH = 3;
    private static final int ROW_BARRIER_COLOR = 5;
    private static final int ROW_TARGET_COLOR = 6;
    private static final int ROW_SELECTION_COLOR = 7;
    private static final int ROW_LINKED_STORAGE_COLOR = 8;
    private static final int ROW_LINE_BRUSH_COLOR = 9;

    private static final int ALWAYS_VISIBLE_ROW_COUNT = 4;
    private static final int COLOR_ROW_COUNT = 5;
    private static final int ROW_HEIGHT = 20;
    private static final int MIN_CONTENT_H = 186;
    private static final int EXTRA_ROWS_H = 20;

    private final ToggleSwitch depthToggle = new ToggleSwitch();
    private final ToggleSwitch flowToggle = new ToggleSwitch();
    private final ToggleSwitch smoothToggle = new ToggleSwitch();
    private final ToggleSwitch uiSmoothToggle = new ToggleSwitch();
    private final ScaleSliderComponent alphaSlider = new ScaleSliderComponent();
    private final SliderTrack alphaTrack = new SliderTrack();

    private final ColorPickerButton colorPickerButton = new ColorPickerButton();
    private final ColorPickerPanel.ColorGroup barrierColorGroup = ColorPickerPanel.ColorGroup.single(
            tStatic("screen.rtsbuilding.settings.rendering"), tStatic("screen.rtsbuilding.settings.barrier_color"),
            new ColorPickerPanel.ColorSource() {
                @Override public int getColor() { return BoundaryPass.barrierColor; }
                @Override public void setColor(int color) { BoundaryPass.barrierColor = color; }
            });
    private final ColorPickerPanel.ColorBlockComponent colorBlock = new ColorPickerPanel.ColorBlockComponent();
    private int barrierBlockX;
    private int barrierBlockY;
    private final TooltipController barrierTooltip = TooltipController.builder().build();

    private final ColorPickerButton targetColorPickerButton = new ColorPickerButton();
    private final ColorPickerPanel.ColorGroup targetColorGroup = new ColorPickerPanel.ColorGroup(
            tStatic("screen.rtsbuilding.settings.rendering"),
            List.of(
                    new ColorPickerPanel.ColorSlot(tStatic("screen.rtsbuilding.settings.block_target_color"), new ColorPickerPanel.ColorSource() {
                        @Override public int getColor() { return InteractionTargetPass.blockTargetColor; }
                        @Override public void setColor(int color) { InteractionTargetPass.blockTargetColor = color; }
                    }),
                    new ColorPickerPanel.ColorSlot(tStatic("screen.rtsbuilding.settings.entity_target_color"), new ColorPickerPanel.ColorSource() {
                        @Override public int getColor() { return InteractionTargetPass.entityTargetColor; }
                        @Override public void setColor(int color) { InteractionTargetPass.entityTargetColor = color; }
                    })));
    private final ColorPickerPanel.ColorBlockComponent targetColorBlock = new ColorPickerPanel.ColorBlockComponent();
    private int targetBlockX;
    private int entityBlockX;
    private int targetBlockY;
    private final TooltipController blockTargetTooltip = TooltipController.builder().build();
    private final TooltipController entityTargetTooltip = TooltipController.builder().build();

    private final ColorPickerButton selectionColorPickerButton = new ColorPickerButton();
    private final ResetButton flowResetBtn = new ResetButton();
    private final ResetButton smoothResetBtn = new ResetButton();
    private final ResetButton uiSmoothResetBtn = new ResetButton();
    private final ResetButton depthResetBtn = new ResetButton();
    private final ResetButton alphaResetBtn = new ResetButton();
    private final ResetButton barrierResetBtn = new ResetButton();
    private final ResetButton targetResetBtn = new ResetButton();
    private final ResetButton selectionResetBtn = new ResetButton();
    private final ResetButton linkedResetBtn = new ResetButton();
    private final ResetButton lineBrushResetBtn = new ResetButton();

    private final ColorPickerButton lineBrushColorPickerButton = new ColorPickerButton();
    private final ColorPickerPanel.ColorGroup lineBrushColorGroup = new ColorPickerPanel.ColorGroup(
            tStatic("screen.rtsbuilding.settings.rendering"),
            List.of(
                    new ColorPickerPanel.ColorSlot(tStatic("screen.rtsbuilding.settings.line_brush_color"), new ColorPickerPanel.ColorSource() {
                        @Override public int getColor() { return LineBrushRenderPass.lineBrushColor; }
                        @Override public void setColor(int color) { LineBrushRenderPass.lineBrushColor = color; }
                    }),
                    new ColorPickerPanel.ColorSlot(tStatic("screen.rtsbuilding.settings.overlap_color"), new ColorPickerPanel.ColorSource() {
                        @Override public int getColor() { return LineBrushRenderPass.lineBrushOverlapColor; }
                        @Override public void setColor(int color) { LineBrushRenderPass.lineBrushOverlapColor = color; }
                    })));
    private final ColorPickerPanel.ColorBlockComponent lineBrushColorBlock = new ColorPickerPanel.ColorBlockComponent();
    private int lineBlockX;
    private int lineOverlapBlockX;
    private int lineBlockY;
    private final TooltipController lineBrushTooltip = TooltipController.builder().build();
    private final TooltipController lineBrushOverlapTooltip = TooltipController.builder().build();

    private final ColorPickerPanel.ColorGroup selectionColorGroup = new ColorPickerPanel.ColorGroup(
            tStatic("screen.rtsbuilding.settings.rendering"),
            List.of(
                    new ColorPickerPanel.ColorSlot(tStatic("screen.rtsbuilding.settings.selection_wireframe_color"), new ColorPickerPanel.ColorSource() {
                        @Override public int getColor() { return BoxSelectionPass.selectionColor; }
                        @Override public void setColor(int color) { BoxSelectionPass.selectionColor = color; }
                    }),
                    new ColorPickerPanel.ColorSlot(tStatic("screen.rtsbuilding.settings.selection_gap_color"), new ColorPickerPanel.ColorSource() {
                        @Override public int getColor() { return BoxSelectionPass.selectionGapColor; }
                        @Override public void setColor(int color) { BoxSelectionPass.selectionGapColor = color; }
                    }),
                    new ColorPickerPanel.ColorSlot(tStatic("screen.rtsbuilding.settings.selection_overlay_color"), new ColorPickerPanel.ColorSource() {
                        @Override public int getColor() { return BoxSelectionPass.previewOverlayColor; }
                        @Override public void setColor(int color) { BoxSelectionPass.previewOverlayColor = color; }
                    }),
                    new ColorPickerPanel.ColorSlot(tStatic("screen.rtsbuilding.settings.selection_entity_color"), new ColorPickerPanel.ColorSource() {
                        @Override public int getColor() { return BoxSelectionPass.entitySelectionColor; }
                        @Override public void setColor(int color) { BoxSelectionPass.entitySelectionColor = color; }
                    })));
    private final ColorPickerPanel.ColorBlockComponent selectionColorBlock = new ColorPickerPanel.ColorBlockComponent();
    private int selBlockX;
    private int selGapBlockX;
    private int selOverlayBlockX;
    private int selEntityBlockX;
    private int selBlockY;
    private final TooltipController selWireframeTooltip = TooltipController.builder().build();
    private final TooltipController selGapTooltip = TooltipController.builder().build();
    private final TooltipController selOverlayTooltip = TooltipController.builder().build();
    private final TooltipController selEntityTooltip = TooltipController.builder().build();

    private final ColorPickerButton linkedColorPickerButton = new ColorPickerButton();
    private final ColorPickerPanel.ColorGroup linkedColorGroup = new ColorPickerPanel.ColorGroup(
            tStatic("screen.rtsbuilding.settings.rendering"),
            List.of(
                    new ColorPickerPanel.ColorSlot(tStatic("screen.rtsbuilding.settings.linked_bi_color"), new ColorPickerPanel.ColorSource() {
                        @Override public int getColor() { return LinkedStoragePass.bidirectionalColor; }
                        @Override public void setColor(int color) { LinkedStoragePass.bidirectionalColor = color; }
                    }),
                    new ColorPickerPanel.ColorSlot(tStatic("screen.rtsbuilding.settings.linked_ext_color"), new ColorPickerPanel.ColorSource() {
                        @Override public int getColor() { return LinkedStoragePass.extractOnlyColor; }
                        @Override public void setColor(int color) { LinkedStoragePass.extractOnlyColor = color; }
                    })));
    private final ColorPickerPanel.ColorBlockComponent linkedColorBlock = new ColorPickerPanel.ColorBlockComponent();
    private int linkedBiBlockX;
    private int linkedExtBlockX;
    private int linkedBlockY;
    private final TooltipController linkedBiTooltip = TooltipController.builder().build();
    private final TooltipController linkedExtTooltip = TooltipController.builder().build();

    private final Map<String, String> translationCache = new HashMap<>();
    private final AnimFloat heightAnim = AnimFloat.expand();
    private boolean lastDepthEnabled;

    public RenderingSection() {
        super("screen.rtsbuilding.settings.category.rendering");
        this.setExpanded(false);
        this.lastDepthEnabled = BoxSelectionPass.depthTestEnabled;
        this.heightAnim.snapTo(this.lastDepthEnabled ? 1f : 0f);

        this.flowResetBtn.setResetAction(() -> BoxSelectionPass.flowAnimationEnabled = true);
        this.smoothResetBtn.setResetAction(() -> CornerBracketRenderer.SmoothTarget.enabled = true);
        this.uiSmoothResetBtn.setResetAction(() -> AnimFloat.setEnabled(true));
        this.depthResetBtn.setResetAction(() -> BoxSelectionPass.depthTestEnabled = true);
        this.alphaResetBtn.setResetAction(() -> CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA = 0.1f);
        this.barrierResetBtn.setResetAction(() -> BoundaryPass.barrierColor = 0xFFFFCC00);
        this.targetResetBtn.setResetAction(() -> {
            InteractionTargetPass.blockTargetColor = 0xFFF69C31;
            InteractionTargetPass.entityTargetColor = 0xFF4D99FF;
        });
        this.selectionResetBtn.setResetAction(() -> {
            BoxSelectionPass.selectionColor = 0xFFFFFFFF;
            BoxSelectionPass.selectionGapColor = 0xFF000000;
            BoxSelectionPass.previewOverlayColor = 0xFF4D80FF;
            BoxSelectionPass.entitySelectionColor = 0xFF4CAF50;
        });
        this.linkedResetBtn.setResetAction(() -> {
            LinkedStoragePass.bidirectionalColor = 0xFF4CAF50;
            LinkedStoragePass.extractOnlyColor = 0xFFFF4CD1;
        });
        this.lineBrushResetBtn.setResetAction(() -> {
            LineBrushRenderPass.lineBrushColor = 0xFF3388FF;
            LineBrushRenderPass.lineBrushOverlapColor = 0xFFAA00FF;
        });
    }

    @Override
    protected int getContentRowCount() {
        return 10;
    }

    @Override
    protected int getEffectiveContentHeight() {
        return MIN_CONTENT_H + Math.round(EXTRA_ROWS_H * this.heightAnim.get());
    }

    private String t(String key) {
        return this.translationCache.computeIfAbsent(key, k -> Component.translatable(k).getString());
    }

    private static String tStatic(String key) {
        return Component.translatable(key).getString();
    }

    private void renderRowLabel(GuiGraphics g, String text, int x, int lineY) {
        TextRenderer.draw(g, text, x + 6, lineY + 2, this.getTextColor());
    }

    private void renderToggleRow(GuiGraphics g, int mx, int my, int x, int w, int lineY,
                                 String label, ToggleSwitch toggle, boolean state, ResetButton resetBtn) {
        this.renderRowLabel(g, label, x, lineY);
        int textCenterY = lineY + 2 + Minecraft.getInstance().font.lineHeight / 2;
        int toggleX = x + w - 6 - ResetButton.BTN_SIZE - RESET_BTN_GAP - 28;
        toggle.render(g, toggleX, textCenterY - 7, state);
        resetBtn.render(g, mx, my, x + w - 6 - ResetButton.BTN_SIZE, textCenterY - 8);
    }

    private int renderColorRow(GuiGraphics g, int mx, int my, int x, int w, int cursorY, String label,
                               ColorPickerPanel.ColorBlockComponent block, int[] colorValues, int[] colorPosX,
                               ColorPickerButton picker, ResetButton resetBtn) {
        this.renderRowLabel(g, label, x, cursorY);
        int labelW = Minecraft.getInstance().font.width(label);
        int textCenterY = cursorY + 2 + Minecraft.getInstance().font.lineHeight / 2;
        int blockX = x + 6 + labelW + COLOR_BLOCK_GAP;
        for (int i = 0; i < colorValues.length; i++) {
            int bx = blockX + i * 10;
            if (colorPosX != null && i < colorPosX.length) {
                colorPosX[i] = bx;
            }
            block.render(g, bx, textCenterY - 4, colorValues[i]);
        }
        picker.render(g, mx, my, x + w - 6 - ResetButton.BTN_SIZE - RESET_BTN_GAP - 16, textCenterY - 8);
        resetBtn.render(g, mx, my, x + w - 6 - ResetButton.BTN_SIZE, textCenterY - 8);
        return cursorY + this.getLineHeight();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, int x, int y, int w, int lineCount) {
        boolean depthOn = BoxSelectionPass.depthTestEnabled;
        if (depthOn != this.lastDepthEnabled) {
            this.lastDepthEnabled = depthOn;
            this.heightAnim.target(depthOn ? 1f : 0f);
        }

        int cursorY = y + 4;
        int lh = this.getLineHeight();
        this.renderToggleRow(g, mx, my, x, w, cursorY,
                this.t("screen.rtsbuilding.settings.flow_animation"), this.flowToggle,
                BoxSelectionPass.flowAnimationEnabled, this.flowResetBtn);
        this.renderToggleRow(g, mx, my, x, w, cursorY += lh,
                this.t("screen.rtsbuilding.settings.smooth_animation"), this.smoothToggle,
                CornerBracketRenderer.SmoothTarget.enabled, this.smoothResetBtn);
        this.renderToggleRow(g, mx, my, x, w, cursorY += lh,
                this.t("screen.rtsbuilding.settings.ui_smooth_animation"), this.uiSmoothToggle,
                AnimFloat.isEnabled(), this.uiSmoothResetBtn);
        this.renderToggleRow(g, mx, my, x, w, cursorY += lh,
                this.t("screen.rtsbuilding.settings.depth_test"), this.depthToggle,
                BoxSelectionPass.depthTestEnabled, this.depthResetBtn);
        cursorY += lh;

        if (depthOn) {
            String alphaLabel = this.t("screen.rtsbuilding.settings.overlay_alpha")
                    + String.format(Locale.ROOT, "：%.0f%%", CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA * 100f);
            this.renderRowSlider(g, mx, my, x, w, cursorY, alphaLabel,
                    this.alphaSlider, this.alphaTrack, ALPHA_MIN, ALPHA_MAX,
                    CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA, this.alphaResetBtn);
            cursorY += lh;
        }

        this.barrierBlockX = x + 6 + Minecraft.getInstance().font.width(this.t("screen.rtsbuilding.settings.barrier_color")) + COLOR_BLOCK_GAP;
        this.barrierBlockY = cursorY + 2 + Minecraft.getInstance().font.lineHeight / 2 - 4;
        cursorY = this.renderColorRow(g, mx, my, x, w, cursorY,
                this.t("screen.rtsbuilding.settings.barrier_color"), this.colorBlock,
                new int[]{BoundaryPass.barrierColor}, null, this.colorPickerButton, this.barrierResetBtn);

        int targetLabelW = Minecraft.getInstance().font.width(this.t("screen.rtsbuilding.settings.target_color"));
        this.targetBlockX = x + 6 + targetLabelW + COLOR_BLOCK_GAP;
        this.targetBlockY = cursorY + 2 + Minecraft.getInstance().font.lineHeight / 2 - 4;
        int[] targetColors = {InteractionTargetPass.blockTargetColor, InteractionTargetPass.entityTargetColor};
        int[] targetPosX = new int[2];
        cursorY = this.renderColorRow(g, mx, my, x, w, cursorY,
                this.t("screen.rtsbuilding.settings.target_color"), this.targetColorBlock,
                targetColors, targetPosX, this.targetColorPickerButton, this.targetResetBtn);
        this.entityBlockX = targetPosX.length > 1 ? targetPosX[1] : this.targetBlockX;

        int selLabelW = Minecraft.getInstance().font.width(this.t("screen.rtsbuilding.settings.selection_color"));
        this.selBlockX = x + 6 + selLabelW + COLOR_BLOCK_GAP;
        this.selBlockY = cursorY + 2 + Minecraft.getInstance().font.lineHeight / 2 - 4;
        int[] selColors = {
                BoxSelectionPass.selectionColor, BoxSelectionPass.selectionGapColor,
                BoxSelectionPass.previewOverlayColor, BoxSelectionPass.entitySelectionColor};
        int[] selPosX = new int[4];
        cursorY = this.renderColorRow(g, mx, my, x, w, cursorY,
                this.t("screen.rtsbuilding.settings.selection_color"), this.selectionColorBlock,
                selColors, selPosX, this.selectionColorPickerButton, this.selectionResetBtn);
        this.selGapBlockX = selPosX.length > 1 ? selPosX[1] : this.selBlockX;
        this.selOverlayBlockX = selPosX.length > 2 ? selPosX[2] : this.selBlockX;
        this.selEntityBlockX = selPosX.length > 3 ? selPosX[3] : this.selBlockX;

        int linkedLabelW = Minecraft.getInstance().font.width(this.t("screen.rtsbuilding.settings.linked_storage_color"));
        this.linkedBiBlockX = x + 6 + linkedLabelW + COLOR_BLOCK_GAP;
        this.linkedBlockY = cursorY + 2 + Minecraft.getInstance().font.lineHeight / 2 - 4;
        int[] linkedColors = {LinkedStoragePass.bidirectionalColor, LinkedStoragePass.extractOnlyColor};
        int[] linkedPosX = new int[2];
        cursorY = this.renderColorRow(g, mx, my, x, w, cursorY,
                this.t("screen.rtsbuilding.settings.linked_storage_color"), this.linkedColorBlock,
                linkedColors, linkedPosX, this.linkedColorPickerButton, this.linkedResetBtn);
        this.linkedExtBlockX = linkedPosX.length > 1 ? linkedPosX[1] : this.linkedBiBlockX;

        this.lineBlockX = x + 6 + Minecraft.getInstance().font.width(this.t("screen.rtsbuilding.settings.line_brush_color")) + COLOR_BLOCK_GAP;
        this.lineBlockY = cursorY + 2 + Minecraft.getInstance().font.lineHeight / 2 - 4;
        int[] lineColors = {LineBrushRenderPass.lineBrushColor, LineBrushRenderPass.lineBrushOverlapColor};
        int[] linePosX = new int[2];
        this.renderColorRow(g, mx, my, x, w, cursorY,
                this.t("screen.rtsbuilding.settings.line_brush_color"), this.lineBrushColorBlock,
                lineColors, linePosX, this.lineBrushColorPickerButton, this.lineBrushResetBtn);
        this.lineOverlapBlockX = linePosX.length > 1 ? linePosX[1] : this.lineBlockX;
    }

    @Override
    protected boolean onContentLineClick(int lineIndex, double mouseX, double mouseY,
                                         int contentX, int contentY, int contentW) {
        if (this.smoothToggle.handleClick(mouseX, mouseY)) {
            CornerBracketRenderer.SmoothTarget.enabled = !CornerBracketRenderer.SmoothTarget.enabled;
            return true;
        }
        if (this.flowToggle.handleClick(mouseX, mouseY)) {
            BoxSelectionPass.flowAnimationEnabled = !BoxSelectionPass.flowAnimationEnabled;
            return true;
        }
        if (this.uiSmoothToggle.handleClick(mouseX, mouseY)) {
            AnimFloat.setEnabled(!AnimFloat.isEnabled());
            return true;
        }
        if (this.depthToggle.handleClick(mouseX, mouseY)) {
            BoxSelectionPass.depthTestEnabled = !BoxSelectionPass.depthTestEnabled;
            return true;
        }
        if (this.flowResetBtn.handleClick(mouseX, mouseY) || this.smoothResetBtn.handleClick(mouseX, mouseY)
                || this.uiSmoothResetBtn.handleClick(mouseX, mouseY) || this.depthResetBtn.handleClick(mouseX, mouseY)
                || this.alphaResetBtn.handleClick(mouseX, mouseY) || this.barrierResetBtn.handleClick(mouseX, mouseY)
                || this.targetResetBtn.handleClick(mouseX, mouseY) || this.selectionResetBtn.handleClick(mouseX, mouseY)
                || this.linkedResetBtn.handleClick(mouseX, mouseY) || this.lineBrushResetBtn.handleClick(mouseX, mouseY)) {
            return true;
        }
        if (lineIndex == ROW_BARRIER_COLOR && this.colorPickerButton.handleClick(mouseX, mouseY)) {
            return true;
        }
        if (lineIndex == ROW_TARGET_COLOR && this.targetColorPickerButton.handleClick(mouseX, mouseY)) {
            return true;
        }
        if (lineIndex == ROW_SELECTION_COLOR && this.selectionColorPickerButton.handleClick(mouseX, mouseY)) {
            return true;
        }
        if (lineIndex == ROW_LINKED_STORAGE_COLOR && this.linkedColorPickerButton.handleClick(mouseX, mouseY)) {
            return true;
        }
        if (lineIndex == ROW_LINE_BRUSH_COLOR && this.lineBrushColorPickerButton.handleClick(mouseX, mouseY)) {
            return true;
        }
        if (BoxSelectionPass.depthTestEnabled) {
            Double newVal = this.alphaSlider.handleClick(mouseX, mouseY,
                    this.alphaTrack.trackX, this.alphaTrack.trackY, this.alphaTrack.trackW, ALPHA_MIN, ALPHA_MAX);
            if (newVal != null) {
                CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA = newVal.floatValue();
                return true;
            }
        }
        return false;
    }

    private void renderRowSlider(GuiGraphics g, int mx, int my, int x, int w, int lineY, String label,
                                 ScaleSliderComponent slider, SliderTrack trackPos,
                                 double min, double max, double value, ResetButton resetBtn) {
        TextRenderer.draw(g, label, x + 6, lineY + 2, this.getTextColor());
        int centerY = lineY + 2 + Minecraft.getInstance().font.lineHeight / 2;
        int controlStart = midControlX(x, w);
        trackPos.trackX = controlStart;
        trackPos.trackY = centerY - 2;
        trackPos.trackW = Mth.clamp(x + w - 6 - ResetButton.BTN_SIZE - RESET_BTN_GAP - controlStart, 20, Integer.MAX_VALUE);
        trackPos.slider = slider;
        slider.render(g, mx, my, trackPos.trackX, trackPos.trackY, trackPos.trackW, min, max, value);
        resetBtn.render(g, mx, my, x + w - 6 - ResetButton.BTN_SIZE, centerY - 8);
    }

    /** 渲染各颜色块与色盘按钮的悬停 tooltip。 */
    public void renderColorTooltips(GuiGraphics g, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen == null) return;
        int textColor = this.getTextColor();
        int shortcutColor = UiPalette.get("tooltip_shortcut");
        int bs = 8;
        this.renderTooltipAt(g, mouseX, mouseY, this.barrierBlockX, this.barrierBlockY, bs, this.barrierTooltip,
                tStatic("screen.rtsbuilding.settings.barrier_color.tooltip"), textColor, shortcutColor, screen);
        this.renderTooltipAt(g, mouseX, mouseY, this.targetBlockX, this.targetBlockY, bs, this.blockTargetTooltip,
                tStatic("screen.rtsbuilding.settings.block_target_color.tooltip"), textColor, shortcutColor, screen);
        this.renderTooltipAt(g, mouseX, mouseY, this.entityBlockX, this.targetBlockY, bs, this.entityTargetTooltip,
                tStatic("screen.rtsbuilding.settings.entity_target_color.tooltip"), textColor, shortcutColor, screen);
        this.renderTooltipAt(g, mouseX, mouseY, this.selBlockX, this.selBlockY, bs, this.selWireframeTooltip,
                tStatic("screen.rtsbuilding.settings.selection_wireframe_color.tooltip"), textColor, shortcutColor, screen);
        this.renderTooltipAt(g, mouseX, mouseY, this.selGapBlockX, this.selBlockY, bs, this.selGapTooltip,
                tStatic("screen.rtsbuilding.settings.selection_gap_color.tooltip"), textColor, shortcutColor, screen);
        this.renderTooltipAt(g, mouseX, mouseY, this.selOverlayBlockX, this.selBlockY, bs, this.selOverlayTooltip,
                tStatic("screen.rtsbuilding.settings.selection_overlay_color.tooltip"), textColor, shortcutColor, screen);
        this.renderTooltipAt(g, mouseX, mouseY, this.selEntityBlockX, this.selBlockY, bs, this.selEntityTooltip,
                tStatic("screen.rtsbuilding.settings.selection_entity_color.tooltip"), textColor, shortcutColor, screen);
        this.renderTooltipAt(g, mouseX, mouseY, this.linkedBiBlockX, this.linkedBlockY, bs, this.linkedBiTooltip,
                tStatic("screen.rtsbuilding.settings.linked_bi_color.tooltip"), textColor, shortcutColor, screen);
        this.renderTooltipAt(g, mouseX, mouseY, this.linkedExtBlockX, this.linkedBlockY, bs, this.linkedExtTooltip,
                tStatic("screen.rtsbuilding.settings.linked_ext_color.tooltip"), textColor, shortcutColor, screen);
        this.renderTooltipAt(g, mouseX, mouseY, this.lineBlockX, this.lineBlockY, bs, this.lineBrushTooltip,
                tStatic("screen.rtsbuilding.settings.line_brush_color.tooltip"), textColor, shortcutColor, screen);
        this.renderTooltipAt(g, mouseX, mouseY, this.lineOverlapBlockX, this.lineBlockY, bs, this.lineBrushOverlapTooltip,
                tStatic("screen.rtsbuilding.settings.overlap_color.tooltip"), textColor, shortcutColor, screen);
    }

    private void renderTooltipAt(GuiGraphics g, int mx, int my, int bx, int by, int bs,
                                 TooltipController tooltip, String text, int textColor, int shortcutColor, Screen screen) {
        boolean hovered = mx >= bx && mx < bx + bs && my >= by && my < by + bs;
        tooltip.update(hovered, false);
        if (tooltip.shouldRender()) {
            tooltip.render(g, bx, by, bs, bs, text, textColor, shortcutColor, screen.width, screen.height);
        }
    }

    public void setColorPickerPanel(ColorPickerPanel panel) {
        this.colorPickerButton.setColorPickerPanel(panel);
        this.colorPickerButton.setColorGroup(this.barrierColorGroup);
        this.targetColorPickerButton.setColorPickerPanel(panel);
        this.targetColorPickerButton.setColorGroup(this.targetColorGroup);
        this.selectionColorPickerButton.setColorPickerPanel(panel);
        this.selectionColorPickerButton.setColorGroup(this.selectionColorGroup);
        this.linkedColorPickerButton.setColorPickerPanel(panel);
        this.linkedColorPickerButton.setColorGroup(this.linkedColorGroup);
        this.lineBrushColorPickerButton.setColorPickerPanel(panel);
        this.lineBrushColorPickerButton.setColorGroup(this.lineBrushColorGroup);
    }

    public void setColorPickerButtonParent(UiPanel parent) {
        this.colorPickerButton.setParentPanel(parent);
        this.targetColorPickerButton.setParentPanel(parent);
        this.selectionColorPickerButton.setParentPanel(parent);
        this.linkedColorPickerButton.setParentPanel(parent);
        this.lineBrushColorPickerButton.setParentPanel(parent);
    }

    public boolean isSliderDragging() {
        return BoxSelectionPass.depthTestEnabled && this.alphaSlider.isDragging();
    }

    public void handleSliderDrag(double mouseX) {
        if (this.alphaSlider.isDragging() && this.alphaTrack.trackW > 0) {
            CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA =
                    (float) this.alphaSlider.handleDrag(mouseX, this.alphaTrack.trackX, this.alphaTrack.trackW, ALPHA_MIN, ALPHA_MAX);
        }
    }

    public void endSliderDrag() {
        this.alphaSlider.endDrag();
    }

    public boolean handleSliderScroll(double mouseX, double mouseY, double scrollY) {
        Double newVal = this.alphaSlider.handleScroll(mouseX, mouseY, scrollY,
                this.alphaTrack.trackX, this.alphaTrack.trackY, this.alphaTrack.trackW, ALPHA_MIN, ALPHA_MAX);
        if (newVal != null) {
            CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA = newVal.floatValue();
            return true;
        }
        return false;
    }
}
