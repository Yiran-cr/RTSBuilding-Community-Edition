package com.rtsbuilding.rtsbuilding.client.presentation.panel.handler;

import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreenConstants;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.RtsUiScaleFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public final class BuilderScreenScaleManager {

    

    
    /** 是否自动跟随原版 GUI 缩放（默认）：窗口尺寸变化时 UI 与原版一致地缩放重排。 */
    private boolean autoRtsGuiScale = true;

    private double fixedRtsGuiScale = BuilderScreenConstants.DEFAULT_RTS_GUI_SCALE;

    
    private boolean fixedRtsScaleRenderPass = false;
    
    private boolean fixedRtsScaleInputPass = false;
    
    private double activeRtsGuiRenderScale = 1.0D;

    

    public double getRtsGuiScale() {
        // 自动模式：跟随原版当前 GUI 缩放（Minecraft 窗口 resize 时自动更新）
        if (this.autoRtsGuiScale) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getWindow() != null && mc.getWindow().getGuiScale() > 0.0D) {
                return mc.getWindow().getGuiScale();
            }
            return BuilderScreenConstants.DEFAULT_RTS_GUI_SCALE;
        }
        return this.fixedRtsGuiScale;
    }

    /** 是否处于自动跟随原版 GUI 缩放状态。 */
    public boolean isAutoRtsGuiScale() {
        return this.autoRtsGuiScale;
    }

    /** 恢复到自动跟随原版 GUI 缩放。 */
    public void resetToAutoRtsGuiScale() {
        this.autoRtsGuiScale = true;
    }

    public String rtsGuiScaleLabel() {
        if (this.autoRtsGuiScale) {
            return net.minecraft.network.chat.Component
                    .translatable("screen.rtsbuilding.settings.ui_scale.auto").getString();
        }
        double scale = sanitizeRtsGuiScale(this.fixedRtsGuiScale);
        if (Math.abs(scale - Math.rint(scale)) < 0.001D) {
            return String.format(Locale.ROOT, "%.0fx", scale);
        }
        return String.format(Locale.ROOT, "%.1fx", scale);
    }

    /** 手动调节缩放：退出自动模式，使用固定缩放。 */
    public void adjustRtsGuiScale(double delta) {
        this.autoRtsGuiScale = false;
        this.fixedRtsGuiScale = sanitizeRtsGuiScale(this.fixedRtsGuiScale + delta);
    }

    /** 手动设置缩放：退出自动模式，使用固定缩放。 */
    public void setRtsGuiScale(double scale) {
        this.autoRtsGuiScale = false;
        this.fixedRtsGuiScale = sanitizeRtsGuiScale(scale);
    }

    
    public boolean isInRenderPass() {
        return this.fixedRtsScaleRenderPass;
    }

    

    
    public void enableUiScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        double scale = this.fixedRtsScaleRenderPass ? this.activeRtsGuiRenderScale : 1.0D;
        if (scale > 0.0D && Double.isFinite(scale) && Math.abs(scale - 1.0D) >= 0.001D) {
            g.enableScissor(
                    (int) Math.floor(x1 * scale),
                    (int) Math.floor(y1 * scale),
                    (int) Math.ceil(x2 * scale),
                    (int) Math.ceil(y2 * scale));
            return;
        }
        g.enableScissor(x1, y1, x2, y2);
    }

    
    public boolean renderWithFixedRtsGuiScale(BuilderScreen screen, GuiGraphics g,
                                               int mouseX, int mouseY, float partialTick) {
        RtsUiScaleFrame frame = enterFixedRtsGuiScale(screen);
        if (frame == null || Math.abs(frame.scale() - 1.0D) < 0.001D) {
            if (frame != null) frame.close();
            return false;
        }
        this.fixedRtsScaleRenderPass = true;
        double previousActiveRenderScale = this.activeRtsGuiRenderScale;
        this.activeRtsGuiRenderScale = frame.scale();
        g.pose().pushPose();
        g.pose().scale((float) frame.scale(), (float) frame.scale(), 1.0F);
        try {
            screen.render(g,
                    (int) Math.round(mouseX / frame.scale()),
                    (int) Math.round(mouseY / frame.scale()),
                    partialTick);
        } finally {
            g.pose().popPose();
            this.activeRtsGuiRenderScale = previousActiveRenderScale;
            this.fixedRtsScaleRenderPass = false;
            frame.close();
        }
        return true;
    }

    
    public RtsUiScaleFrame enterFixedRtsGuiScale(BuilderScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (screen == null || mc == null || mc.getWindow() == null
                || screen.width <= 0 || screen.height <= 0) {
            return null;
        }
        double currentScale = mc.getWindow().getScreenWidth()
                / (double) Math.max(1, screen.width);
        if (currentScale <= 0.0D || !Double.isFinite(currentScale)) {
            return null;
        }
        double renderScale = this.fixedRtsGuiScale / currentScale;
        if (renderScale <= 0.0D || !Double.isFinite(renderScale)) {
            return null;
        }
        int oldW = screen.width;
        int oldH = screen.height;
        int virtualW = Math.max(1, (int) Math.round(oldW / renderScale));
        int virtualH = Math.max(1, (int) Math.round(oldH / renderScale));
        screen.width = virtualW;
        screen.height = virtualH;
        return new RtsUiScaleFrame(oldW, oldH, renderScale, () -> {
            screen.width = oldW;
            screen.height = oldH;
        });
    }

    

    
    @javax.annotation.Nullable
    public Boolean scaleMouseEvent(BuilderScreen screen, double mouseX, double mouseY,
                                    BiFunction<Double, Double, Boolean> handler) {
        if (this.fixedRtsScaleInputPass) return null;
        RtsUiScaleFrame frame = enterFixedRtsGuiScale(screen);
        if (frame == null) return false;
        if (Math.abs(frame.scale() - 1.0D) >= 0.001D) {
            this.fixedRtsScaleInputPass = true;
            try {
                return handler.apply(mouseX / frame.scale(), mouseY / frame.scale());
            } finally {
                this.fixedRtsScaleInputPass = false;
                frame.close();
            }
        }
        frame.close();
        return null;
    }

    
    public boolean scaleMouseEventVoid(BuilderScreen screen, double mouseX, double mouseY,
                                        BiConsumer<Double, Double> handler) {
        Boolean result = scaleMouseEvent(screen, mouseX, mouseY, (x, y) -> {
            handler.accept(x, y);
            return true;
        });
        return result != null;
    }

    
    public boolean scaleMouseEventQuad(BuilderScreen screen, double mouseX, double mouseY,
                                        int button, double dragX, double dragY,
                                        QuadHandler handler) {
        if (this.fixedRtsScaleInputPass) return false;
        RtsUiScaleFrame frame = enterFixedRtsGuiScale(screen);
        if (frame == null) return true;
        if (Math.abs(frame.scale() - 1.0D) >= 0.001D) {
            this.fixedRtsScaleInputPass = true;
            try {
                double s = frame.scale();
                return handler.apply(mouseX / s, mouseY / s, button, dragX / s, dragY / s);
            } finally {
                this.fixedRtsScaleInputPass = false;
                frame.close();
            }
        }
        frame.close();
        return false;
    }

    
    @FunctionalInterface
    public interface QuadHandler {
        boolean apply(double mouseX, double mouseY, int button, double dragX, double dragY);
    }

    

    
    private static double sanitizeRtsGuiScale(double scale) {
        if (!Double.isFinite(scale)) {
            return BuilderScreenConstants.DEFAULT_RTS_GUI_SCALE;
        }
        double snapped = Math.round(scale / BuilderScreenConstants.RTS_GUI_SCALE_STEP)
                * BuilderScreenConstants.RTS_GUI_SCALE_STEP;
        return Math.max(BuilderScreenConstants.MIN_RTS_GUI_SCALE,
                Math.min(BuilderScreenConstants.MAX_RTS_GUI_SCALE, snapped));
    }
}
