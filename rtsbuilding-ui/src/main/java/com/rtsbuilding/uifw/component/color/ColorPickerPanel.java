package com.rtsbuilding.uifw.component.color;

import com.rtsbuilding.uifw.window.window.UiPanel;
import com.rtsbuilding.uifw.component.CircleColorSwatch;
import com.rtsbuilding.uifw.component.HexInputComponent;
import com.rtsbuilding.uifw.component.ScaleSliderComponent;
import com.rtsbuilding.uifw.layout.FlexLayout;
import com.rtsbuilding.uifw.layout.UiBox;
import com.rtsbuilding.uifw.layout.UiRect;
import com.rtsbuilding.uifw.window.component.ScrollBar;
import com.rtsbuilding.uifw.window.api.UiPanelHost;
import com.rtsbuilding.uifw.animate.AnimFloat;
import com.rtsbuilding.uifw.render.BlendScope;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

public class ColorPickerPanel extends UiPanel {

    private static final int SECTION_GAP = 8;
    private static final int CONTENT_PAD_X = 6;
    private static final int CARD_RADIUS = 6;
    /** 滑块条目行高（条目背景高度）。 */
    private static final int SLIDER_ROW_H = 20;
    /** 颜色编码（预览+输入）卡片高度：比色相条目高 4px。 */
    private static final int INPUT_CARD_H = SLIDER_ROW_H + 4;
    private static final int SLIDER_INNER_GAP = 4;
    private static final int SLIDER_CLICK_PAD = 3;
    /** 滑块轨道实际高度（与 {@link ScaleSliderComponent} 内部 TRACK_H 保持一致）。 */
    private static final int SLIDER_TRACK_H = 9;
    /** 滑块条目文字左边缘留白（靠左 +2px）。 */
    private static final int SLIDER_LABEL_LEFT_PAD = 2;
    /** 滑块轨道右边缘留白（靠右 -2px）。 */
    private static final int SLIDER_TRACK_RIGHT_PAD = 2;
    /** 滑块行左右分半区：左半区放文字、右半区放轨道。 */
    private static final float SLIDER_HALF_RATIO = 0.5f;

    private final HexInputComponent hexInput = new HexInputComponent();
    private final ColorWheelComponent wheelComponent = new ColorWheelComponent();
    private final GrayscaleBarComponent grayscaleComponent = new GrayscaleBarComponent();
    private final ColorPreviewComponent colorPreview = new ColorPreviewComponent();
    private final SwatchSelectorComponent swatchSelector = new SwatchSelectorComponent();

    @javax.annotation.Nullable
    private ColorGroup colorGroup;
    private int activeSlotIndex;

    public void setColorGroup(@javax.annotation.Nullable ColorGroup group) {
        this.colorGroup = group;
        this.activeSlotIndex = 0;
        if (group != null && group.size() > 0) {
            int color = group.slot(0).source().getColor();
            this.initialColor = color;
            syncToColor(color);
        }
        if (isOpen()) {
            int neededW = Math.max(getMinWindowWidth(), computeContentWidth() + 2);
            int neededH = Math.max(getMinWindowHeight(), computeContentHeight() + getTitleBarHeight() + 8);
            if (getWindowWidth() < neededW || getWindowHeight() < neededH) {
                setSize(neededW, neededH);
            }
        }
    }

    public void setColorSource(@javax.annotation.Nullable ColorSource source) {
        if (source != null) {
            setColorGroup(ColorGroup.single("", "\u989C\u8272", source));
        } else {
            this.colorGroup = null;
        }
    }

    public void setActiveSlot(int index) {
        switchToSlot(index);
    }

    @javax.annotation.Nullable
    private ColorSource activeColorSource() {
        if (colorGroup == null || activeSlotIndex < 0 || activeSlotIndex >= colorGroup.size()) {
            return null;
        }
        return colorGroup.slot(activeSlotIndex).source();
    }

    private boolean hasSwatchSelector() {
        return colorGroup != null && colorGroup.size() > 1;
    }

    private void syncToColor(int color) {
        this.wheelBaseColor = color;
        float[] hsv = ColorMath.rgbToHsv(color);
        this.hueValue = hsv[0];
        this.saturationValue = hsv[1];
        ColorWheelComponent.IndicatorPos pos = wheelComponent.syncIndicatorToColor(color);
        this.indicatorRelX = pos.relX;
        this.indicatorRelY = pos.relY;
        float valueOnly = hsv[2];
        this.grayscaleIndicatorRelY = Math.max(0.0f, Math.min(1.0f, 1.0f - valueOnly));
    }

    private void applyToSource() {
        ColorSource source = activeColorSource();
        if (source != null) {
            source.setColor(getCurrentColor());
        }
    }

    private void switchToSlot(int index) {
        if (colorGroup == null || index < 0 || index >= colorGroup.size() || index == activeSlotIndex) return;
        applyToSource();
        this.activeSlotIndex = index;
        int color = colorGroup.slot(index).source().getColor();
        this.initialColor = color;
        syncToColor(color);
    }

    private int getCurrentColor() {
        return ColorMath.blendGrayscale(this.wheelBaseColor, this.grayscaleIndicatorRelY);
    }

    private float indicatorRelX = 0.5f;
    private float indicatorRelY = 0.5f;
    private int wheelBaseColor;
    private boolean wheelDragging;
    private boolean grayscaleDragging;
    private float grayscaleIndicatorRelY;
    private int initialColor;

    private final ScaleSliderComponent hueSlider = new ScaleSliderComponent();
    private final ScaleSliderComponent satSlider = new ScaleSliderComponent();
    private float hueValue;
    private float saturationValue;
    private int sliderDraggingIndex = -1;
    private double sliderDragStartX;
    private double sliderDragStartVal;

    private final AnimFloat indicatorStateAnim = AnimFloat.hover();
    private final AnimFloat grayscaleIndicatorStateAnim = AnimFloat.hover();

    public ColorPickerPanel() {
    }

    @Override
    public void init(UiPanelHost screen) {
        super.init(screen);
        this.resizable = false;
        this.draggable = true;
        this.closable = true;
        this.hexInput.setOnColorParsed(color -> {
            syncToColor(color);
            applyToSource();
        });
        // 模式按钮左移对齐预览区初始色圆的左边缘
        this.hexInput.setModeBtnRightOffset(ColorPreviewComponent.previewLeftPad());
    }

    /**
     * 内容区布局结果（三区块卡片式）：[色轮卡片, 滑块卡片, 预览+输入卡片, 色块卡片(可选)]。
     * 所有区块矩形均由 FlexLayout 统一计算，渲染/点击/拖拽/滚动复用同一份坐标。
     */
    private static final class PickerLayout {
        final int wheelCardX, wheelCardY, wheelCardW, wheelCardH;
        final int wheelImgX, wheelImgY;
        final int grayBarX, grayBarY;
        final int sliderCardX, sliderCardY;
        final int sliderLabelX;
        final int hueTrackX, hueTrackY, hueTrackW;
        final int satTrackX, satTrackY, satTrackW;
        final int inputCardY, inputCardH;
        final int previewX, previewY, previewW;
        final int hexInputX, hexInputW, hexInputY;
        final int swatchTop;

        PickerLayout(int wheelCardX, int wheelCardY, int wheelCardW, int wheelCardH,
                     int wheelImgX, int wheelImgY, int grayBarX, int grayBarY,
                     int sliderCardX, int sliderCardY, int sliderLabelX,
                     int hueTrackX, int hueTrackY, int hueTrackW,
                     int satTrackX, int satTrackY, int satTrackW,
                     int inputCardY, int inputCardH,
                     int previewX, int previewY, int previewW,
                     int hexInputX, int hexInputW, int hexInputY,
                     int swatchTop) {
            this.wheelCardX = wheelCardX; this.wheelCardY = wheelCardY;
            this.wheelCardW = wheelCardW; this.wheelCardH = wheelCardH;
            this.wheelImgX = wheelImgX; this.wheelImgY = wheelImgY;
            this.grayBarX = grayBarX; this.grayBarY = grayBarY;
            this.sliderCardX = sliderCardX; this.sliderCardY = sliderCardY;
            this.sliderLabelX = sliderLabelX;
            this.hueTrackX = hueTrackX; this.hueTrackY = hueTrackY; this.hueTrackW = hueTrackW;
            this.satTrackX = satTrackX; this.satTrackY = satTrackY; this.satTrackW = satTrackW;
            this.inputCardY = inputCardY; this.inputCardH = inputCardH;
            this.previewX = previewX; this.previewY = previewY; this.previewW = previewW;
            this.hexInputX = hexInputX; this.hexInputW = hexInputW; this.hexInputY = hexInputY;
            this.swatchTop = swatchTop;
        }
    }

    private static String hueLabel() {
        return Component.translatable("screen.uifw.color_picker.hue").getString();
    }

    private static String satLabel() {
        return Component.translatable("screen.uifw.color_picker.saturation").getString();
    }

    /**
     * 统一内容区布局（FlexLayout 列，三区块卡片式）：
     * ① 色轮卡片（色轮 + 灰阶条，行内居中）→ ② 滑块卡片（色相/饱和度）→ ③ 预览+输入卡片（初始色圆/当前色圆 + hex 输入同一行）→ ④ 色块卡片（可选，置底）。
     * 滑块行内用 FlexLayout 排布 [标签, 滑块]；预览+输入行内用 FlexLayout 排布 [预览 fixed, hex 输入 fill]。
     */
    private PickerLayout computeContentLayout(int cx, int cy, int cw) {
        Font font = Minecraft.getInstance().font;
        int innerW = cw - CONTENT_PAD_X * 2;

        // 区块高度
        int wheelCardH = ColorWheelComponent.AREA_SIZE;
        int sliderCardH = SLIDER_ROW_H * 2 + SLIDER_INNER_GAP;
        int inputCardH = INPUT_CARD_H;
        boolean swatch = hasSwatchSelector();
        int swatchCardH = swatch ? SwatchSelectorComponent.ROW_H : 0;

        List<UiBox> rows = new java.util.ArrayList<>();
        rows.add(UiBox.fixed(innerW, wheelCardH));
        rows.add(UiBox.fixed(innerW, sliderCardH));
        rows.add(UiBox.fixed(innerW, inputCardH));
        if (swatch) rows.add(UiBox.fixed(innerW, swatchCardH));

        List<UiRect> rects = FlexLayout.layout(FlexLayout.Direction.COLUMN, FlexLayout.Justify.START,
                FlexLayout.Align.STRETCH, SECTION_GAP, cx + CONTENT_PAD_X, cy + 4, innerW, computeContentHeight(), rows);

        // ① 色轮卡片：色轮 + 灰阶条行内居中
        UiRect wheelCard = rects.get(0);
        int panelW = ColorWheelComponent.AREA_SIZE + GrayscaleBarComponent.GAP + GrayscaleBarComponent.BAR_W;
        List<UiRect> wheelRects = FlexLayout.layout(FlexLayout.Direction.ROW, FlexLayout.Justify.CENTER,
                FlexLayout.Align.STRETCH, GrayscaleBarComponent.GAP, wheelCard.x(), wheelCard.y(), innerW, wheelCard.h(),
                List.of(
                        UiBox.fixed(ColorWheelComponent.AREA_SIZE, ColorWheelComponent.AREA_SIZE),
                        UiBox.fixed(GrayscaleBarComponent.BAR_W, ColorWheelComponent.AREA_SIZE)));
        int wheelImgX = wheelRects.get(0).x() + ColorWheelComponent.PAD;
        int wheelImgY = wheelCard.y() + ColorWheelComponent.PAD;
        int grayBarX = wheelRects.get(1).x();
        int grayBarY = wheelImgY;

        // ② 滑块卡片：两行滑块条目，文字左边缘对齐初始色圆左边缘，轨道左移相同偏移
        UiRect sliderCard = rects.get(1);
        int hueRowY = sliderCard.y();
        int satRowY = hueRowY + SLIDER_ROW_H + SLIDER_INNER_GAP;
        // 滑块文字对齐下方初始色圆左边缘；偏移 = 对齐目标 - 原靠左起点
        int sliderLabelX = ColorPreviewComponent.initialSwatchLeft(sliderCard.x());
        int shift = sliderLabelX - sliderLabelXBase(sliderCard.x());
        int[] hueTrack = computeSliderTrack(sliderCard.x(), innerW, shift);
        int[] satTrack = computeSliderTrack(sliderCard.x(), innerW, shift);
        int hueTrackY = hueRowY + (SLIDER_ROW_H - SLIDER_TRACK_H) / 2;
        int satTrackY = satRowY + (SLIDER_ROW_H - SLIDER_TRACK_H) / 2;

        // ③ 预览+输入卡片：行内 [预览 fixed, hex 输入 fill]
        UiRect inputCard = rects.get(2);
        int previewW = ColorPreviewComponent.computePreviewWidth();
        List<UiRect> inputRects = FlexLayout.layout(FlexLayout.Direction.ROW, FlexLayout.Justify.START,
                FlexLayout.Align.CENTER, 8, inputCard.x(), inputCard.y(), innerW, inputCard.h(),
                List.of(UiBox.fixed(previewW, inputCard.h()), UiBox.fill(1f)));
        int previewX = inputRects.get(0).x();
        int previewY = inputCard.y() + (inputCard.h() - ColorPreviewComponent.PREVIEW_BAR_H) / 2;
        int hexInputX = inputRects.get(1).x();
        int hexInputW = inputRects.get(1).w();
        int hexInputY = inputCard.y() + (inputCard.h() - HexInputComponent.INPUT_H) / 2;

        // ④ 色块卡片（可选）
        int swatchTop = -1;
        if (swatch) swatchTop = rects.get(3).y();

        return new PickerLayout(wheelCard.x(), wheelCard.y(), wheelCard.w(), wheelCard.h(),
                wheelImgX, wheelImgY, grayBarX, grayBarY,
                sliderCard.x(), sliderCard.y(), sliderLabelX,
                hueTrack[0], hueTrackY, hueTrack[1],
                satTrack[0], satTrackY, satTrack[1],
                inputCard.y(), inputCard.h(),
                previewX, previewY, previewW,
                hexInputX, hexInputW, hexInputY,
                swatchTop);
    }

    /**
     * 滑块条目行内排布：左右分半区——左半区放文字、右半区放轨道（靠右 -{@link #SLIDER_TRACK_RIGHT_PAD}）。
     * {@code shift} 为整体左移量（文字右移对齐初始色圆后，轨道同步左移）。返回 {轨道 x, 轨道 w}。
     */
    private int[] computeSliderTrack(int rowX, int rowW, int shift) {
        int leftHalfW = Math.round(rowW * SLIDER_HALF_RATIO);
        int rightHalfX = rowX + leftHalfW;
        int rightHalfW = rowW - leftHalfW;
        int trackX = rightHalfX - shift;
        int trackW = rightHalfW - SLIDER_TRACK_RIGHT_PAD;
        return new int[]{trackX, trackW};
    }

    /** 滑块条目内文字靠左基准起点（未对齐前的原始位置，供计算偏移用）。 */
    private static int sliderLabelXBase(int rowX) {
        return rowX + SLIDER_LABEL_LEFT_PAD;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();
        Font font = Minecraft.getInstance().font;

        PickerLayout layout = computeContentLayout(cx, cy, cw);

        try (BlendScope blend = BlendScope.normal()) {
            // ① 色轮卡片背景 + 色轮 + 灰阶条
            SdfRenderer.drawVectorFloatingPanel(g, layout.wheelCardX, layout.wheelCardY, layout.wheelCardW, layout.wheelCardH, false);
            wheelComponent.renderWheel(g, layout.wheelImgX, layout.wheelImgY);
            wheelComponent.renderIndicator(g, layout.wheelImgX, layout.wheelImgY,
                    indicatorRelX, indicatorRelY, indicatorStateAnim,
                    mouseX, mouseY, wheelDragging);
            grayscaleComponent.renderBar(g, layout.grayBarX, layout.grayBarY, wheelBaseColor);
            grayscaleComponent.renderIndicator(g, layout.grayBarX, layout.grayBarY,
                    grayscaleIndicatorRelY, grayscaleIndicatorStateAnim,
                    mouseX, mouseY, grayscaleDragging);
        }

        // ② 两条滑块条目（条目背景 + 文字靠左 + 轨道靠右）
        int textColor = ThemeManager.getTextColor();
        int labelYOff = (SLIDER_TRACK_H - font.lineHeight) / 2;

        // 色相条目
        SdfRenderer.drawRoundedRect(g, layout.sliderCardX, layout.sliderCardY, layout.wheelCardW, SLIDER_ROW_H,
                CARD_RADIUS, UiPalette.get("slider_row_bg"));
        TextRenderer.draw(g, hueLabel(), layout.sliderLabelX, layout.hueTrackY + labelYOff, textColor);
        hueSlider.render(g, mouseX, mouseY, layout.hueTrackX, layout.hueTrackY, layout.hueTrackW, 0.0, 1.0, hueValue);

        // 饱和度条目
        SdfRenderer.drawRoundedRect(g, layout.sliderCardX, layout.sliderCardY + SLIDER_ROW_H + SLIDER_INNER_GAP,
                layout.wheelCardW, SLIDER_ROW_H, CARD_RADIUS, UiPalette.get("slider_row_bg"));
        TextRenderer.draw(g, satLabel(), layout.sliderLabelX, layout.satTrackY + labelYOff, textColor);
        satSlider.render(g, mouseX, mouseY, layout.satTrackX, layout.satTrackY, layout.satTrackW, 0.0, 1.0, saturationValue);

        // ③ 预览+输入卡片背景 + 预览圆 + hex 输入（同一行）
        SdfRenderer.drawBorderedRoundedRect(g, layout.sliderCardX, layout.inputCardY, layout.wheelCardW, layout.inputCardH,
                CARD_RADIUS, UiPalette.border(), UiPalette.bg(), 1);
        int newColor = getCurrentColor();
        colorPreview.render(g, layout.previewX, layout.previewY, layout.previewW,
                this.initialColor, newColor);
        this.hexInput.render(g, mouseX, mouseY, layout.hexInputX, layout.hexInputW, layout.hexInputY, newColor);

        // ④ 预设色块卡片（可选，置底）
        if (hasSwatchSelector()) {
            SdfRenderer.drawBorderedRoundedRect(g, layout.sliderCardX, layout.swatchTop, layout.wheelCardW, SwatchSelectorComponent.ROW_H,
                    CARD_RADIUS, UiPalette.border(), UiPalette.bg(), 1);
            swatchSelector.render(g, mouseX, mouseY, colorGroup, activeSlotIndex, layout.swatchTop, layout.sliderCardX, layout.wheelCardW);
        }
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();
        PickerLayout layout = computeContentLayout(cx, cy, cw);

        if (mouseX >= layout.wheelImgX && mouseX < layout.wheelImgX + ColorWheelComponent.DRAW_SIZE
                && mouseY >= layout.wheelImgY && mouseY < layout.wheelImgY + ColorWheelComponent.DRAW_SIZE) {
            pickWheelColor(mouseX, mouseY, layout.wheelImgX, layout.wheelImgY);
            this.wheelDragging = true;
            return;
        }

        if (mouseX >= layout.grayBarX && mouseX < layout.grayBarX + GrayscaleBarComponent.BAR_W
                && mouseY >= layout.grayBarY && mouseY < layout.grayBarY + GrayscaleBarComponent.BAR_H) {
            pickGrayscaleColor(mouseY, layout.grayBarY);
            this.grayscaleDragging = true;
            return;
        }

        if (hasSwatchSelector()) {
            // 预设色块横向滚动条：优先响应点击/拖动
            if (swatchSelector.handleClick(mouseX, mouseY, layout.swatchTop)) {
                return;
            }
            int hitIndex = swatchSelector.hitTest(mouseX, mouseY, colorGroup, layout.swatchTop, layout.sliderCardX);
            if (hitIndex >= 0) {
                switchToSlot(hitIndex);
                return;
            }
        }

        if (colorPreview.isClickOnInitialColor(mouseX, mouseY, layout.previewX, layout.previewY)) {
            if (hexInput.isEditMode()) hexInput.cancelEdit();
            syncToColor(this.initialColor);
            applyToSource();
            return;
        }

        if (this.hexInput.handleClick(mouseX, mouseY, layout.hexInputY, layout.hexInputX, layout.hexInputW, getCurrentColor())) return;

        // 色相滑块命中（与渲染同一布局）
        if (mouseY >= layout.hueTrackY - SLIDER_CLICK_PAD && mouseY < layout.hueTrackY + SLIDER_TRACK_H + SLIDER_CLICK_PAD
                && mouseX >= layout.hueTrackX && mouseX < layout.hueTrackX + layout.hueTrackW) {
            double relX = (mouseX - layout.hueTrackX) / (double) layout.hueTrackW;
            this.hueValue = (float) Mth.clamp(relX, 0.0, 1.0);
            this.sliderDraggingIndex = 0;
            this.sliderDragStartX = mouseX;
            this.sliderDragStartVal = this.hueValue;
            hueSlider.handleClick(mouseX, mouseY, layout.hueTrackX, layout.hueTrackY, layout.hueTrackW, 0.0, 1.0);
            updateColorFromSliders();
            return;
        }

        if (mouseY >= layout.satTrackY - SLIDER_CLICK_PAD && mouseY < layout.satTrackY + SLIDER_TRACK_H + SLIDER_CLICK_PAD
                && mouseX >= layout.satTrackX && mouseX < layout.satTrackX + layout.satTrackW) {
            double relX = (mouseX - layout.satTrackX) / (double) layout.satTrackW;
            this.saturationValue = (float) Mth.clamp(relX, 0.0, 1.0);
            this.sliderDraggingIndex = 1;
            this.sliderDragStartX = mouseX;
            this.sliderDragStartVal = this.saturationValue;
            satSlider.handleClick(mouseX, mouseY, layout.satTrackX, layout.satTrackY, layout.satTrackW, 0.0, 1.0);
            updateColorFromSliders();
        }
    }

    @Override
    public void setOpen(boolean open) {
        if (open && !isOpen() && colorGroup != null && colorGroup.size() > 1) {
            int w = Math.max(getMinWindowWidth(), computeContentWidth() + 2);
            int h = Math.max(getMinWindowHeight(), computeContentHeight() + getTitleBarHeight() + 8);
            setBounds(getWindowX(), getWindowY(), w, h);
        }
        super.setOpen(open);
    }

    @Override
    protected void onClose() {
        if (hexInput.isEditMode()) hexInput.applyEdit();
    }

    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        return this.hexInput.handleKeyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        return this.hexInput.handleCharTyped(codePoint, modifiers);
    }

    private void pickWheelColor(double mouseX, double mouseY, int wheelImgX, int wheelImgY) {
        ColorWheelComponent.WheelPickResult result = wheelComponent.pickColor(mouseX, mouseY, wheelImgX, wheelImgY);
        if (result != null) {
            this.wheelBaseColor = result.color;
            this.grayscaleIndicatorRelY = 0.0f;
            this.indicatorRelX = result.relX;
            this.indicatorRelY = result.relY;
            float[] hsv = ColorMath.rgbToHsv(this.wheelBaseColor);
            this.hueValue = hsv[0];
            this.saturationValue = hsv[1];
            applyToSource();
        }
    }

    private void pickGrayscaleColor(double mouseY, int grayBarY) {
        this.grayscaleIndicatorRelY = grayscaleComponent.pickColor(mouseY, grayBarY);
        applyToSource();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();

        if (this.wheelDragging && button == 0) {
            PickerLayout layout = computeContentLayout(cx, cy, cw);
            pickWheelColor(mouseX, mouseY, layout.wheelImgX, layout.wheelImgY);
            return true;
        }

        if (this.grayscaleDragging && button == 0) {
            PickerLayout layout = computeContentLayout(cx, cy, cw);
            pickGrayscaleColor(mouseY, layout.grayBarY);
            return true;
        }

        if (this.sliderDraggingIndex >= 0 && button == 0) {
            PickerLayout layout = computeContentLayout(cx, cy, cw);
            int trackW = this.sliderDraggingIndex == 0 ? layout.hueTrackW : layout.satTrackW;
            double pixelRange = Math.max(1.0, trackW - 8.0);
            double dx = mouseX - this.sliderDragStartX;
            double newVal = Mth.clamp(this.sliderDragStartVal + dx / pixelRange, 0.0, 1.0);
            if (this.sliderDraggingIndex == 0) {
                this.hueValue = (float) newVal;
            } else {
                this.saturationValue = (float) newVal;
            }
            updateColorFromSliders();
            return true;
        }

        // 预设色块横向滚动条拖动
        if (swatchSelector.handleDrag(mouseX)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.wheelDragging = false;
        this.grayscaleDragging = false;
        this.sliderDraggingIndex = -1;
        hueSlider.endDrag();
        satSlider.endDrag();
        swatchSelector.endDrag();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (hasSwatchSelector()) {
            int cw = contentWidth();
            PickerLayout layout = computeContentLayout(contentX(), contentY(), cw);
            if (swatchSelector.handleScroll(mouseX, mouseY, scrollY, layout.swatchTop, layout.sliderCardX, layout.wheelCardW)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Component getTitle() {
        if (colorGroup != null && activeSlotIndex >= 0 && activeSlotIndex < colorGroup.size()) {
            String groupName = colorGroup.groupDisplayName();
            String slotName = colorGroup.slot(activeSlotIndex).displayName();
            if (!groupName.isEmpty()) return Component.literal(groupName + " - " + slotName);
            return Component.literal(slotName);
        }
        return Component.translatable("screen.uifw.color_picker.title");
    }

    @Override
    protected int getDefaultWidth() {
        // 内容宽 + 左右各 15px 外边距（共 +30）
        return Math.max(getMinWindowWidth(), computeContentWidth() + 32);
    }

    @Override
    protected int getDefaultHeight() {
        // 内容高 + 标题栏 + 上 8px / 下 30px（共 +30 相对原布局）
        return Math.max(getMinWindowHeight(), computeContentHeight() + getTitleBarHeight() + 38);
    }

    private int computeContentWidth() {
        int wheelWidth = ColorWheelComponent.AREA_SIZE + GrayscaleBarComponent.GAP + GrayscaleBarComponent.BAR_W + 8;
        int inputLineWidth = computeInputLineWidth();
        int previewRowWidth = ColorPreviewComponent.computePreviewWidth() + 8 + inputLineWidth;
        int maxWidth = Math.max(wheelWidth, previewRowWidth);
        if (colorGroup != null && colorGroup.size() > 1) {
            maxWidth = Math.max(maxWidth, swatchSelector.computeMinWidth(colorGroup));
        }
        return maxWidth;
    }

    private int computeInputLineWidth() {
        return this.hexInput.computeInputLineWidth();
    }

    private int computeContentHeight() {
        int h = 4;
        h += ColorWheelComponent.AREA_SIZE;
        h += SECTION_GAP + (SLIDER_ROW_H * 2 + SLIDER_INNER_GAP);
        h += SECTION_GAP + INPUT_CARD_H;
        if (hasSwatchSelector()) h += SECTION_GAP + SwatchSelectorComponent.ROW_H;
        h += 10;
        return h;
    }

    @Override
    protected void computeDefaultPosition() {
        if (this.screen != null) {
            // 统一基准：水平居中 + 垂直居中，顶部留 60px、底部留 8px 边距
            positionCentered(60, 8);
        }
    }

    private void updateColorFromSliders() {
        this.wheelBaseColor = ColorMath.hsvToRgb(this.hueValue, this.saturationValue, 1.0f);
        ColorWheelComponent.IndicatorPos pos = wheelComponent.calcIndicatorUVFromHS(this.hueValue, this.saturationValue);
        this.indicatorRelX = pos.relX;
        this.indicatorRelY = pos.relY;
        applyToSource();
    }

    // ── Merged inner classes ──

    public interface ColorSource {
        int getColor();
        void setColor(int color);
    }

    public static class ColorSlot {
        private final String displayName;
        private final ColorSource source;
        public ColorSlot(String displayName, ColorSource source) {
            this.displayName = displayName;
            this.source = source;
        }
        public String displayName() { return displayName; }
        public ColorSource source() { return source; }
    }

    public static class ColorGroup {
        private final String groupDisplayName;
        private final java.util.List<ColorSlot> slots;
        public ColorGroup(String groupDisplayName, java.util.List<ColorSlot> slots) {
            if (slots == null || slots.isEmpty()) throw new IllegalArgumentException("ColorGroup needs at least one ColorSlot");
            this.groupDisplayName = groupDisplayName;
            this.slots = java.util.List.copyOf(slots);
        }
        public String groupDisplayName() { return groupDisplayName; }
        public java.util.List<ColorSlot> slots() { return slots; }
        public int size() { return slots.size(); }
        public ColorSlot slot(int index) { return slots.get(index); }
        public static ColorGroup single(String groupDisplayName, String slotDisplayName, ColorSource source) {
            return new ColorGroup(groupDisplayName, java.util.Collections.singletonList(new ColorSlot(slotDisplayName, source)));
        }
    }

    public static final class ColorMath {
        private ColorMath() {}
        public static float[] rgbToHsv(int argb) {
            int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
            float rf = r / 255.0f, gf = g / 255.0f, bf = b / 255.0f;
            float max = Math.max(rf, Math.max(gf, bf)), min = Math.min(rf, Math.min(gf, bf)), delta = max - min;
            float h = 0, s = (max == 0) ? 0 : delta / max, v = max;
            if (delta != 0) {
                if (max == rf) h = ((gf - bf) / delta) % 6.0f;
                else if (max == gf) h = ((bf - rf) / delta) + 2.0f;
                else h = ((rf - gf) / delta) + 4.0f;
                h *= 60.0f;
                if (h < 0) h += 360.0f;
            }
            return new float[]{h / 360.0f, s, v};
        }
        public static int hsvToRgb(float h, float s, float v) {
            float hueDeg = h * 360.0f, c = v * s, x = c * (1.0f - Math.abs((hueDeg / 60.0f) % 2.0f - 1.0f)), m = v - c;
            float rF, gF, bF;
            switch (((int) hueDeg) / 60) {
                case 0: rF = c; gF = x; bF = 0; break;
                case 1: rF = x; gF = c; bF = 0; break;
                case 2: rF = 0; gF = c; bF = x; break;
                case 3: rF = 0; gF = x; bF = c; break;
                case 4: rF = x; gF = 0; bF = c; break;
                default: rF = c; gF = 0; bF = x; break;
            }
            return 0xFF000000 | ((int) ((rF + m) * 255) << 16) | ((int) ((gF + m) * 255) << 8) | (int) ((bF + m) * 255);
        }
        public static boolean isDarkColor(int argb) {
            int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
            return (r * 0.299 + g * 0.587 + b * 0.114) < 128;
        }
        public static int blendGrayscale(int base, float t) {
            t = 1.0f - t;
            return 0xFF000000 | ((int) (((base >> 16) & 0xFF) * t) << 16) | ((int) (((base >> 8) & 0xFF) * t) << 8) | (int) ((base & 0xFF) * t);
        }
    }

    public static final class ColorPreviewComponent {
        public static final int PREVIEW_BAR_H = 16;
        private static final int SWATCH_SIZE = 12;
        private static final int SWATCH_GAP = 8;
        private static final int LEFT_PAD = 4;

        /** 紧凑横向预览宽度（初始色圆 + 当前色圆）。 */
        public static int computePreviewWidth() {
            return LEFT_PAD + SWATCH_SIZE + SWATCH_GAP + SWATCH_SIZE + 4;
        }

        /** 初始色圆左边缘 x（供滑块文字对齐）。 */
        public static int initialSwatchLeft(int previewX) {
            return previewX + LEFT_PAD;
        }

        /** 初始色圆左边缘到预览区左边界的距离（供模式按钮左移对齐）。 */
        public static int previewLeftPad() {
            return LEFT_PAD;
        }

        public void render(GuiGraphics g, int previewX, int previewY, int previewW,
                           int initialColor, int currentColor) {
            int centerY = previewY + PREVIEW_BAR_H / 2;
            int swatchY = centerY - SWATCH_SIZE / 2;
            int borderColor = UiPalette.get("picker_swatch_border");
            CircleColorSwatch swatch = new CircleColorSwatch();

            // 初始色圆（点击可恢复）
            int initX = previewX + LEFT_PAD;
            swatch.render(g, initX, swatchY, SWATCH_SIZE, initialColor, borderColor, 1);

            // 当前色圆
            int curX = initX + SWATCH_SIZE + SWATCH_GAP;
            swatch.render(g, curX, swatchY, SWATCH_SIZE, currentColor, borderColor, 1);
        }

        /** 命中初始色圆（点击恢复初始色）。 */
        public boolean isClickOnInitialColor(double mouseX, double mouseY, int previewX, int previewY) {
            int centerY = previewY + PREVIEW_BAR_H / 2;
            int swatchY = centerY - SWATCH_SIZE / 2;
            int initX = previewX + LEFT_PAD;
            return mouseX >= initX && mouseX < initX + SWATCH_SIZE
                    && mouseY >= swatchY && mouseY < swatchY + SWATCH_SIZE;
        }
    }

    public static final class SwatchSelectorComponent {
        public static final int ROW_H = 20;
        private static final int SWATCH_SIZE = 14, SWATCH_GAP = 12, SWATCH_TEXT_GAP = 3;
        /** 起始条目左边缘与卡片左边的间隔，对齐颜色编码卡片初始色圆的左边缘。 */
        private static final int LEFT_PADDING = ColorPreviewComponent.previewLeftPad();
        /** 起始条目绘制起点在 LEFT_PADDING 基础上再右移 4px。 */
        private static final int START_PAD = LEFT_PADDING + 4;
        private static final int SCROLLBAR_H = 7;

        private final ScrollBar scrollBar = new ScrollBar()
                .withOrientation(ScrollBar.Orientation.HORIZONTAL)
                .withMinThumbSize(24);
        private int scrollX;
        private int lastBarX, lastBarY, lastBarLen;

        public void render(GuiGraphics g, int mouseX, int mouseY, ColorGroup group, int activeSlotIndex,
                           int sectionTop, int contentX, int contentW) {
            if (group == null || group.size() <= 1) return;
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            int textColor = com.rtsbuilding.uifw.theme.ThemeManager.getTextColor();
            int totalW = computeTotalWidth(font, group);
            int usableW = contentW - LEFT_PADDING * 2;
            boolean scrolling = totalW > usableW;
            if (scrolling) {
                scrollBar.setContent(totalW, usableW);
                this.scrollX = -scrollBar.getScroll();
            } else {
                scrollBar.setScroll(0);
                this.scrollX = 0;
            }

            // 条目绘制区裁剪：X 轴左右各内缩 LEFT_PADDING，避免滚动条目画出卡片边界
            boolean scissored = enableSwatchScissor(g, contentX + LEFT_PADDING, sectionTop,
                    contentX + contentW - LEFT_PADDING, sectionTop + ROW_H);
            try {
                int swatchY = sectionTop + (ROW_H - SWATCH_SIZE) / 2;
                int itemX = contentX + START_PAD + this.scrollX;
                CircleColorSwatch swatch = new CircleColorSwatch();
                for (int i = 0; i < group.size(); i++) {
                    String name = group.slot(i).displayName();
                    int slotColor = group.slot(i).source().getColor();
                    int swatchBorder = (i == activeSlotIndex) ? UiPalette.get("picker_swatch_selected") : UiPalette.get("picker_swatch_inactive");
                    swatch.render(g, itemX, swatchY, SWATCH_SIZE, slotColor, swatchBorder, 1);
                    TextRenderer.draw(g, name, itemX + SWATCH_SIZE + SWATCH_TEXT_GAP, sectionTop + (ROW_H - font.lineHeight) / 2 + 1, textColor);
                    itemX += SWATCH_SIZE + SWATCH_TEXT_GAP + font.width(name) + SWATCH_GAP;
                }
            } finally {
                if (scissored) {
                    g.flush();
                    g.disableScissor();
                }
            }

            // 横向滚动条：横跨整个可用宽度（背景内缩后的左右范围，绘制在裁剪区之外）
            if (scrolling) {
                int barX = contentX + LEFT_PADDING;
                int barW = contentW - LEFT_PADDING * 2;
                int barY = sectionTop + ROW_H + 2;
                this.lastBarX = barX;
                this.lastBarY = barY;
                this.lastBarLen = barW;
                scrollBar.render(g, barX, barY, barW);
            } else {
                this.lastBarLen = 0;
            }
        }

        /** 启用条目绘制区的 scissor 裁剪，返回是否成功启用。 */
        private static boolean enableSwatchScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
            if (x2 <= x1 || y2 <= y1) return false;
            net.minecraft.client.gui.screens.Screen screen = net.minecraft.client.Minecraft.getInstance().screen;
            if (screen instanceof UiPanelHost host) {
                host.enableUiScissor(g, x1, y1, x2, y2);
            } else {
                g.enableScissor(x1, y1, x2, y2);
            }
            return true;
        }

        /** 滚轮横向滚动（命中预设行区域时）。X 轴裁剪范围为左右各内缩 {@link #LEFT_PADDING}。 */
        public boolean handleScroll(double mouseX, double mouseY, double scrollY,
                                    int sectionTop, int contentX, int contentW) {
            if (mouseX >= contentX + LEFT_PADDING && mouseX < contentX + contentW - LEFT_PADDING
                    && mouseY >= sectionTop && mouseY < sectionTop + ROW_H + SCROLLBAR_H + 4) {
                return scrollBar.handleScroll(scrollY);
            }
            return false;
        }

        /** 滚动条点击（拖动拇指 / 点击轨道翻页）。 */
        public boolean handleClick(double mouseX, double mouseY, int sectionTop) {
            return lastBarLen > 0
                    && scrollBar.handleClick(mouseX, mouseY, lastBarX, lastBarY, lastBarLen);
        }

        /** 滚动条拖动中。 */
        public boolean handleDrag(double mouseX) {
            return lastBarLen > 0 && scrollBar.handleDrag(mouseX, lastBarX, lastBarLen);
        }

        public void endDrag() {
            scrollBar.endDrag();
        }

        public int hitTest(double mouseX, double mouseY, ColorGroup group, int sectionTop, int contentX) {
            if (group == null || group.size() <= 1) return -1;
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            int swatchY = sectionTop + (ROW_H - SWATCH_SIZE) / 2;
            int itemX = contentX + START_PAD + this.scrollX;
            for (int i = 0; i < group.size(); i++) {
                String name = group.slot(i).displayName();
                int itemW = SWATCH_SIZE + SWATCH_TEXT_GAP + font.width(name);
                if (mouseX >= itemX && mouseX < itemX + itemW && mouseY >= swatchY && mouseY < swatchY + SWATCH_SIZE) return i;
                itemX += itemW + SWATCH_GAP;
            }
            return -1;
        }

        public int computeMinWidth(ColorGroup group) {
            if (group == null || group.size() <= 1) return 0;
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            return LEFT_PADDING + 4 + computeTotalWidth(font, group);
        }

        private static int computeTotalWidth(net.minecraft.client.gui.Font font, ColorGroup group) {
            int total = 0;
            for (int i = 0; i < group.size(); i++) {
                if (i > 0) total += SWATCH_GAP;
                total += SWATCH_SIZE + SWATCH_TEXT_GAP + font.width(group.slot(i).displayName());
            }
            return total;
        }
    }

    public static final class ColorBlockComponent {
        public static final int DEFAULT_SIZE = 8;
        public void render(GuiGraphics g, int x, int y, int size, int color) {
            new CircleColorSwatch().render(g, x, y, size, color);
        }
        public void render(GuiGraphics g, int x, int y, int color) { render(g, x, y, DEFAULT_SIZE, color); }
    }
}
