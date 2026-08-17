package com.rtsbuilding.rtsbuilding.client.presentation.panel.blueprint;

import com.rtsbuilding.rtsbuilding.client.blueprint.BlueprintLocalStore;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.uifw.animate.AnimFloat;
import com.rtsbuilding.uifw.animate.ColorAnimation;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.theme.ThemeManager;
import com.rtsbuilding.uifw.window.window.UiPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import static com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreenConstants.TOP_H;

/**
 * 蓝图导入面板 —— 网页式「文件转换」风格的上传区。
 * <p>
 * 内容区中央为一个大加号图标 + 提示文字的虚线圆角框（上传区），点击后弹出
 * 系统文件选择对话框；选择外部蓝图文件（Sponge Schematic / Litematica /
 * Building Gadgets 模板 / 原版结构）后经 {@link BlueprintLocalStore#importFile}
 * 转换为本模组蓝图存入本地蓝图目录，并刷新蓝图文件管理面板。导入结果（成功
 * 文件名 / 失败原因）在面板内提示。
 */
public final class BlueprintImportPanel extends UiPanel {

    private static final Logger LOG = LoggerFactory.getLogger("RTS-BlueprintImport");

    /** 面板默认尺寸。 */
    private static final int DEFAULT_W = 320;
    private static final int DEFAULT_H = 220;
    /** 上传区左右外边距。 */
    private static final int ZONE_MARGIN = 14;
    /** 上传区最小高度。 */
    private static final int ZONE_MIN_H = 120;
    /** 加号图标边长（px）。 */
    private static final int PLUS_SIZE = 34;
    /** 上传区圆角半径。 */
    private static final int ZONE_RADIUS = 6;

    /** 上传区悬停动画。 */
    private final AnimFloat zoneHover = AnimFloat.hover();
    /** 导入结果提示（成功文件名/失败原因），面板关闭时清除。 */
    private Component statusMessage;
    private boolean statusIsError;

    @Override
    public void init(com.rtsbuilding.uifw.window.api.UiPanelHost screen) {
        super.init(screen);
        this.resizable = false;
    }

    /** 打开面板并清除上次导入状态。 */
    public void open() {
        this.statusMessage = null;
        this.statusIsError = false;
        setOpen(true);
        markBroughtToFront();
    }

    // ── UiPanel 布局与渲染 ─────────────────────────────────────────

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.blueprint.import_panel.title");
    }

    @Override
    protected int getDefaultWidth() {
        return DEFAULT_W;
    }

    @Override
    protected int getDefaultHeight() {
        return DEFAULT_H;
    }

    @Override
    protected void computeDefaultPosition() {
        if (this.screen != null) {
            positionCentered(TOP_H + 6, 8);
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();
        int ch = contentHeight();
        var font = Minecraft.getInstance().font;
        int textColor = ThemeManager.getTextColor();

        UploadZone zone = computeZone(cx, cy, cw, ch);
        boolean hovering = isInZone(mouseX, mouseY, zone);
        float t = zoneHover.track(hovering);

        // 上传区：hover 高亮填充 + 圆角描边（描边色随悬停过渡到强调色）
        if (t > 0.01f) {
            SdfRenderer.drawRoundedRect(g, zone.x(), zone.y(), zone.w(), zone.h(), ZONE_RADIUS,
                    ColorAnimation.lerpRGB(0x00000000, UiPalette.get("list_row_hover"), t));
        }
        int borderColor = ColorAnimation.lerpRGB(UiPalette.border(), UiPalette.accent(), t);
        SdfRenderer.drawRoundedOutline(g, zone.x(), zone.y(), zone.w(), zone.h(), ZONE_RADIUS, borderColor, 1);

        // 大加号图标（上传区中心偏上）
        int plusColor = ColorAnimation.lerpRGB(UiPalette.get("tooltip_text"), UiPalette.accent(), t);
        SdfRenderer.drawPlusIcon(g, zone.cx(), zone.cy() - 14, PLUS_SIZE, plusColor);

        // 提示文字（加号下方）
        String click = Component.translatable("screen.rtsbuilding.blueprint.import_panel.click").getString();
        TextRenderer.drawCentered(g, font, click, zone.cx(), zone.cy() + PLUS_SIZE / 2 + 4, textColor);
        String supported = Component.translatable("screen.rtsbuilding.blueprint.import_panel.supported").getString();
        TextRenderer.drawCentered(g, font, supported, zone.cx(), zone.cy() + PLUS_SIZE / 2 + 16,
                (textColor & 0xFFFFFF) | 0x60000000);

        // 导入结果提示
        if (statusMessage != null) {
            int statusColor = statusIsError ? UiPalette.get("status_error") : UiPalette.get("status_success");
            TextRenderer.drawCentered(g, font, statusMessage, cx + cw / 2, cy + ch - font.lineHeight - 6, statusColor);
        }
    }

    /** 上传区矩形（渲染与点击命中共用同一布局）。 */
    private record UploadZone(int x, int y, int w, int h) {
        int cx() { return x + w / 2; }
        int cy() { return y + h / 2; }
    }

    private static UploadZone computeZone(int cx, int cy, int cw, int ch) {
        int x = cx + ZONE_MARGIN;
        int y = cy + 8;
        int w = cw - ZONE_MARGIN * 2;
        int h = Math.max(ZONE_MIN_H, ch - 8 - ZONE_MARGIN);
        return new UploadZone(x, y, w, h);
    }

    private static boolean isInZone(double mx, double my, UploadZone zone) {
        return mx >= zone.x() && mx < zone.x() + zone.w()
                && my >= zone.y() && my < zone.y() + zone.h();
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();
        int ch = contentHeight();
        UploadZone zone = computeZone(cx, cy, cw, ch);
        if (!isInZone(mouseX, mouseY, zone)) return;

        if (!(screen instanceof BuilderScreen builder)) return;
        try {
            Path saved = builder.importBlueprintFile();
            if (saved == null) {
                return; // 用户取消选择
            }
            this.statusMessage = Component.translatable("screen.rtsbuilding.blueprint.import_panel.success",
                    saved.getFileName().toString());
            this.statusIsError = false;
        } catch (Exception ex) {
            LOG.warn("导入蓝图失败", ex);
            this.statusMessage = Component.translatable("screen.rtsbuilding.blueprint.import_panel.failed",
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            this.statusIsError = true;
        }
    }

    @Override
    protected void onClose() {
        this.statusMessage = null;
        this.statusIsError = false;
        this.zoneHover.snapTo(0f);
        super.onClose();
    }
}
