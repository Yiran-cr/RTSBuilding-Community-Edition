package com.rtsbuilding.uifw.component;

import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.UiPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/**
 * 通用缩放/数值滑块组件：轨道 + 可拖动的拇指。
 *
 * <p>支持三种交互：</p>
 * <ul>
 *   <li>点击拇指启动拖动（{@link #handleClick} → {@link #handleDrag}）。</li>
 *   <li>点击轨道空白处直接跳到该位置对应值并启动拖动。</li>
 *   <li>悬停滚轮微调（{@link #handleScroll}），按住 <b>Shift</b> 时步进 ×10 快速粗调。</li>
 * </ul>
 */
public class ScaleSliderComponent {

    /** 轨道高度。 */
    private static final int TRACK_H = 9;
    /** 拇指尺寸。 */
    private static final int THUMB_H = 9;
    private static final int THUMB_W = 9;
    /** 轨道点击判定向四周的留白。 */
    private static final int TRACK_CLICK_PADDING = 3;

    /** 平滑插值基础速度。 */
    private static final double LERP_BASE = 0.12;
    /** 平滑插值距离加速系数。 */
    private static final double LERP_DISTANCE_BOOST = 3.0;

    /** 是否正在拖动。 */
    private boolean dragging = false;
    /** 拖动开始时的鼠标 X。 */
    private double clickMouseX = 0;
    /** 拖动开始时的值。 */
    private double clickValue = 0;
    /** 每像素对应的值变化量。 */
    private double valuePerPixel = 0;
    /** 上次渲染的拇指 X（供命中判定）。 */
    private int renderedThumbX = 0;

    /** 平滑插值后的值（渲染用）。 */
    private double smoothValue = 0;
    /** 外部期望值（真实值）。 */
    private double externalValue = 0;
    private boolean initialized;

    /**
     * 渲染滑块轨道与拇指，并驱动平滑插值动画。
     *
     * @param value 外部期望值（真实值），拖动时直接跟随。
     */
    public void render(GuiGraphics g, int mouseX, int mouseY,
                       int trackX, int trackY, int trackW,
                       double min, double max, double value) {
        if (trackW <= 0) return;

        this.externalValue = value;

        // 首次渲染以当前值为平滑初值
        if (!initialized) {
            initialized = true;
            smoothValue = value;
        }

        // 拖动时直接跟随外部值，否则按距离自适应速度插值
        if (dragging) {
            smoothValue = value;
        } else {
            double diff = value - smoothValue;
            double speed = Mth.clamp(LERP_BASE + Math.abs(diff) * LERP_DISTANCE_BOOST, 0.0, 1.0);
            smoothValue += diff * speed;
        }

        boolean active = this.dragging;

        SdfRenderer.drawPill(g, trackX, trackY, trackW, TRACK_H, UiPalette.get("slider_track"));

        int thumbX = trackX + (int) Math.round((smoothValue - min) / (max - min) * (trackW - THUMB_W));
        this.renderedThumbX = thumbX;
        int thumbY = trackY + (TRACK_H - THUMB_H) / 2;
        int thumbColor = active ? UiPalette.get("slider_thumb_active") : UiPalette.get("slider_thumb");
        SdfRenderer.drawPill(g, thumbX, thumbY, THUMB_W, THUMB_H, thumbColor);
    }

    /**
     * 点击命中处理：点击拇指启动拖动（返回 null 不立即改值），
     * 点击轨道空白处跳到对应值并启动拖动（返回新值）。
     *
     * @return 轨道跳转产生的新值；未命中或仅启动拖动时返回 {@code null}。
     */
    @Nullable
    public Double handleClick(double mouseX, double mouseY,
                              int trackX, int trackY, int trackW,
                              double min, double max) {
        if (trackW <= 0) return null;

        // 命中检测（含轨道四周留白），命中后以点击位置为锚启动拖动
        if (mouseY >= trackY - TRACK_CLICK_PADDING
                && mouseY < trackY + TRACK_H + TRACK_CLICK_PADDING
                && mouseX >= trackX - TRACK_CLICK_PADDING
                && mouseX < trackX + trackW + TRACK_CLICK_PADDING) {
            double pixelRange = Math.max(1, trackW - THUMB_W);
            // 点击拇指：以当前平滑值为锚启动拖动
            if (mouseX >= renderedThumbX && mouseX < renderedThumbX + THUMB_W) {
                clickMouseX = mouseX;
                clickValue = smoothValue;
                valuePerPixel = (max - min) / pixelRange;
                dragging = true;
                return null;
            }
            // 点击轨道空白处：跳到点击位置对应值并启动拖动
            double ratio = Mth.clamp((mouseX - trackX) / pixelRange, 0.0, 1.0);
            double newValue = Math.round((min + ratio * (max - min)) * 10.0) / 10.0;
            clickMouseX = mouseX;
            clickValue = newValue;
            valuePerPixel = (max - min) / pixelRange;
            dragging = true;
            return newValue;
        }
        return null;
    }

    /**
     * 滚轮悬停微调：每次滚动步长 0.1，返回新值（未命中返回 {@code null}）。
     */
    @Nullable
    public Double handleScroll(double mouseX, double mouseY, double scrollY,
                               int trackX, int trackY, int trackW,
                               double min, double max) {
        return handleScroll(mouseX, mouseY, scrollY, trackX, trackY, trackW, min, max, 0.1);
    }

    /**
     * 滚轮悬停微调：每次滚动步长 {@code step}，返回新值（未命中返回 {@code null}）。
     * 供需要整数步进（如连锁挖掘数量 1 个 1 个）的调用方使用。
     * 按住 <b>Shift</b> 时步长 ×10（如 1 → 10，0.1 → 1），便于快速粗调。
     */
    @Nullable
    public Double handleScroll(double mouseX, double mouseY, double scrollY,
                               int trackX, int trackY, int trackW,
                               double min, double max, double step) {
        if (trackW <= 0) return null;

        // 按住 Shift：步进 ×10 快速粗调
        if (Screen.hasShiftDown()) {
            step *= 10.0;
        }

        // 命中轨道区域才消费滚轮
        if (mouseY >= trackY - TRACK_CLICK_PADDING
                && mouseY < trackY + TRACK_H + TRACK_CLICK_PADDING
                && mouseX >= trackX && mouseX < trackX + trackW) {

            double newValue = smoothValue + (scrollY > 0 ? step : -step);
            newValue = Mth.clamp(newValue, min, max);
            newValue = Math.round(newValue * 100.0) / 100.0;
            return newValue;
        }
        return null;
    }

    /**
     * 拖动中根据鼠标 X 计算新值（步长 0.1）。未在拖动时返回 {@code min}。
     */
    public double handleDrag(double mouseX, int trackX, int trackW,
                             double min, double max) {
        if (!dragging || trackW <= 0) return min;

        double dx = mouseX - clickMouseX;
        double newValue = clickValue + dx * valuePerPixel;
        newValue = Mth.clamp(newValue, min, max);
        newValue = Math.round(newValue * 10.0) / 10.0;
        return newValue;
    }

    public boolean isDragging() {
        return dragging;
    }

    public void endDrag() {
        this.dragging = false;
    }

    public double getSmoothValue() {
        return smoothValue;
    }
}
