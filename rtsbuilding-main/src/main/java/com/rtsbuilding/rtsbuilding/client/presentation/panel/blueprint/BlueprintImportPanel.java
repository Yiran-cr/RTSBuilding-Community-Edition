package com.rtsbuilding.rtsbuilding.client.presentation.panel.blueprint;

import com.rtsbuilding.rtsbuilding.client.blueprint.BlueprintLocalStore;
import com.rtsbuilding.rtsbuilding.client.blueprint.BlueprintLocalStore.ImportStage;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.uifw.animate.AnimFloat;
import com.rtsbuilding.uifw.animate.ColorAnimation;
import com.rtsbuilding.uifw.animate.Easing;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.SpriteRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.render.model.SpriteRegion;
import com.rtsbuilding.uifw.render.model.TextureInfo;
import com.rtsbuilding.uifw.theme.ThemeManager;
import com.rtsbuilding.uifw.window.window.UiPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreenConstants.TOP_H;

/**
 * 蓝图导入面板 —— 网页式「文件转换」风格的上传区。
 * <p>
 * 内容区中央为贴图加号图标（update.png，1024x512 暗亮两半区，mipmap 平滑）+ 提示文字的
 * 圆角框（上传区，仅展示：悬浮时只有边框颜色过渡，背景不填充）；面板底部为
 * 「选择文件」按钮（圆角框内），点击后才弹出系统文件选择对话框；也可直接把文件拖入
 * 上传区导入。导入在后台线程异步执行（大文件不卡渲染线程），面板内以「读取 → 解析 →
 * 写出」三阶段进度条反馈进度。导入结果（成功文件名 / 失败原因）在面板内提示。
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
    /** 「选择文件」按钮高度（px）。 */
    private static final int BTN_H = 18;
    /** 按钮距上传区底部的内边距（px）。 */
    private static final int BTN_INNER_MARGIN = 8;
    /** 状态提示与按钮的间距（px）。 */
    private static final int STATUS_GAP = 5;
    /** 上传区底部到状态提示的间距（px）。 */
    private static final int STATUS_BOTTOM_MARGIN = 6;

    /** 加号图标贴图：1024x512，左暗右亮两半区（暗/亮主题各用半区）。 */
    public static final ResourceLocation UPDATE_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/blueprint/update.png");
    /** 加号图标贴图信息：HQ（linear+mipmap，绘制由 TextureStateShard 强制 setFilter(true,true)）。 */
    private static final TextureInfo UPDATE_TEX_INFO = new TextureInfo(
            UPDATE_TEXTURE, 1024, 512,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.HQ);
    /** 加号图标精灵：占满暗/亮半区（512x512），主题偏移由 {@link SpriteRenderer#getThemeOffset} 决定。 */
    private static final SpriteRegion UPDATE_PLUS = new SpriteRegion(
            UPDATE_TEX_INFO, 0, 0, 512, 512);

    /** 上传区悬停动画（仅驱动边框颜色）。 */
    private final AnimFloat zoneHover = AnimFloat.hover();
    /** 「选择文件」按钮悬停动画。 */
    private final AnimFloat btnHover = AnimFloat.hover();
    /** 「打开蓝图列表」快捷按钮悬停动画。 */
    private final AnimFloat openLibraryHover = AnimFloat.hover();
    /** 进度条平滑动画（阶段切换时过渡到对应目标值）。 */
    private final AnimFloat progressAnim = AnimFloat.of(0f, 250L, Easing.EASE_OUT_QUAD);

    /** 是否正在后台导入。 */
    private volatile boolean importing;
    /** 当前导入阶段（后台线程更新，渲染线程读取）。 */
    private final AtomicReference<ImportStage> importStage = new AtomicReference<>();
    /** 当前正在导入的文件索引（后台线程更新，渲染读取，驱动多文件进度）。 */
    private final AtomicInteger importIndex = new AtomicInteger();
    /** 本次导入的文件总数（多文件进度分母）。 */
    private volatile int importTotal;

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
        this.importing = false;
        this.importStage.set(null);
        this.importIndex.set(0);
        this.importTotal = 0;
        this.progressAnim.snapTo(0f);
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

        ImportLayout layout = computeLayout(cx, cy, cw, ch);
        UploadZone zone = layout.zone();
        ButtonRect button = layout.button();

        // 实时光标位置：拖放期间系统接管鼠标导致 GLFW/MC 光标位置冻结，
        // 用 AWT MouseInfo 取真实光标（失败时回退渲染传入的鼠标坐标）
        double[] liveCursor = liveCursorVirtual(this.screen);
        double hx = liveCursor != null ? liveCursor[0] : mouseX;
        double hy = liveCursor != null ? liveCursor[1] : mouseY;

        // 上传区：仅悬浮时边框颜色过渡到强调色，不做背景填充
        boolean zoneHovered = isInZone(hx, hy, zone);
        float t = zoneHover.track(zoneHovered);
        int borderColor = ColorAnimation.lerpRGB(UiPalette.border(), UiPalette.accent(), t);
        SdfRenderer.drawRoundedOutline(g, zone.x(), zone.y(), zone.w(), zone.h(), ZONE_RADIUS, borderColor, 1);

        // 加号图标（上传区中心偏上）：update.png 贴图，按暗/亮主题取对应半区
        int plusX = zone.cx() - PLUS_SIZE / 2;
        int plusY = zone.cy() - 14 - PLUS_SIZE / 2;
        int themeOffset = SpriteRenderer.getThemeOffset(UPDATE_PLUS);
        SpriteRenderer.drawSprite(g, UPDATE_PLUS, themeOffset, plusX, plusY, PLUS_SIZE, PLUS_SIZE, 1f);

        // 提示文字（加号下方）
        String hint = Component.translatable("screen.rtsbuilding.blueprint.import_panel.hint").getString();
        TextRenderer.drawCentered(g, font, hint, zone.cx(), zone.cy() + PLUS_SIZE / 2 + 4, textColor);
        String supported = Component.translatable("screen.rtsbuilding.blueprint.import_panel.supported").getString();
        TextRenderer.drawCentered(g, font, supported, zone.cx(), zone.cy() + PLUS_SIZE / 2 + 16,
                (textColor & 0xFFFFFF) | 0x60000000);

        // 选择文件按钮（圆角框内底部居中）
        renderButton(g, hx, hy, button);

        // 导入中：进度条 + 阶段文字（位于按钮上方，圆角框内）
        if (importing) {
            ImportStage stage = importStage.get();
            int idx = Math.max(0, importIndex.get());
            int total = Math.max(1, importTotal);
            float stageFrac = stage == null ? 0.08f : stageProgress(stage);
            float target = (idx + stageFrac) / total;
            progressAnim.target(target);
            float p = progressAnim.get();
            int barW = zone.w() - 60;
            int barH = 7;
            int barX = zone.cx() - barW / 2;
            int barY = layout.progressY() - barH - 8;
            SdfRenderer.drawProgressBar(g, barX, barY, barW, barH, p,
                    UiPalette.get("scroll_track"),
                    UiPalette.accent(), UiPalette.accent(),
                    UiPalette.border());
            String stageText = Component.translatable(stageKey(stage)).getString();
            if (total > 1) {
                stageText += Component.translatable("screen.rtsbuilding.blueprint.import_panel.stage.count",
                        Math.min(idx + 1, total), total).getString();
            }
            TextRenderer.drawCentered(g, font, stageText, cx + cw / 2, layout.progressY(), textColor);
        } else if (statusMessage != null) {
            // 导入结果提示（圆角框下方）
            int statusColor = statusIsError ? UiPalette.get("status_error") : UiPalette.get("status_success");
            TextRenderer.drawCentered(g, font, statusMessage, cx + cw / 2, layout.statusY(), statusColor);
            // 导入成功：显示「打开蓝图列表」快捷按钮（状态文字下一行）
            if (!statusIsError) {
                renderTextButton(g, hx, hy, layout.openLibraryBtn(),
                        "button.rtsbuilding.blueprint.open_library", openLibraryHover);
            }
        }
    }

    /** 「选择文件」按钮：本地化文字 + 悬停渐变。 */
    private void renderButton(GuiGraphics g, double mouseX, double mouseY, ButtonRect button) {
        renderTextButton(g, mouseX, mouseY, button, "button.rtsbuilding.blueprint.choose_file", btnHover);
    }

    /** 通用文字按钮：本地化文字 + 悬停渐变。 */
    private void renderTextButton(GuiGraphics g, double mouseX, double mouseY,
                                  ButtonRect button, String textKey, AnimFloat hoverAnim) {
        boolean hovering = isInButton(mouseX, mouseY, button);
        float t = hoverAnim.track(hovering);
        int bg = ColorAnimation.lerpRGB(UiPalette.get("list_btn"), UiPalette.get("list_btn_hover"), t);
        SdfRenderer.drawRoundedRect(g, button.x(), button.y(), button.w(), button.h(), 3, bg);
        String text = Component.translatable(textKey).getString();
        TextRenderer.drawCentered(g, Minecraft.getInstance().font, text,
                button.cx(), button.y() + (button.h() - Minecraft.getInstance().font.lineHeight) / 2 + 1,
                UiPalette.get("tooltip_text"));
    }

    /**
     * 面板内容布局：上传区（上部，占满除底部状态区以外的空间）+ 选择文件按钮（圆角框内
     * 底部居中）+ 进度条/阶段文字（框内按钮上方）+ 导入结果提示（圆角框下方）+
     * 成功时的「打开蓝图列表」快捷按钮（状态文字下一行）。
     * 渲染与点击命中共用同一布局。
     */
    private record ImportLayout(UploadZone zone, ButtonRect button, int progressY, int statusY,
                                ButtonRect openLibraryBtn) {}

    /** 上传区矩形。 */
    private record UploadZone(int x, int y, int w, int h) {
        int cx() { return x + w / 2; }
        int cy() { return y + h / 2; }
    }

    /** 「选择文件」按钮矩形。 */
    private record ButtonRect(int x, int y, int w, int h) {
        int cx() { return x + w / 2; }
    }

    private static ImportLayout computeLayout(int cx, int cy, int cw, int ch) {
        var font = Minecraft.getInstance().font;
        int btnW = font.width(Component.translatable("button.rtsbuilding.blueprint.choose_file").getString()) + 20;
        // 上传区占满内容区，底部预留状态区（导入结果提示 + 成功快捷按钮显示在圆角框下方）
        int libBtnH = 14;
        int statusAreaH = font.lineHeight + 4 + libBtnH + STATUS_BOTTOM_MARGIN;
        int zoneX = cx + ZONE_MARGIN;
        int zoneW = cw - ZONE_MARGIN * 2;
        int zoneH = Math.max(ZONE_MIN_H, ch - 8 - statusAreaH - STATUS_BOTTOM_MARGIN);
        UploadZone zone = new UploadZone(zoneX, cy + 8, zoneW, zoneH);
        int btnX = zone.cx() - btnW / 2;
        int btnY = zone.y() + zone.h() - BTN_H - BTN_INNER_MARGIN;
        int progressY = btnY - font.lineHeight - STATUS_GAP;
        int statusY = zone.y() + zone.h() + STATUS_BOTTOM_MARGIN;
        int libBtnW = font.width(Component.translatable("button.rtsbuilding.blueprint.open_library").getString()) + 20;
        int libBtnX = cx + (cw - libBtnW) / 2;
        int libBtnY = statusY + font.lineHeight + 4;
        return new ImportLayout(zone, new ButtonRect(btnX, btnY, btnW, BTN_H), progressY, statusY,
                new ButtonRect(libBtnX, libBtnY, libBtnW, libBtnH));
    }

    private static boolean isInZone(double mx, double my, UploadZone zone) {
        return mx >= zone.x() && mx < zone.x() + zone.w()
                && my >= zone.y() && my < zone.y() + zone.h();
    }

    private static boolean isInButton(double mx, double my, ButtonRect button) {
        return mx >= button.x() && mx < button.x() + button.w()
                && my >= button.y() && my < button.y() + button.h();
    }

    /** 导入阶段 → 进度条目标值（阶段跳变，进度条平滑过渡）。 */
    private static float stageProgress(ImportStage stage) {
        return switch (stage) {
            case READING -> 0.15f;
            case PARSING -> 0.50f;
            case WRITING -> 0.88f;
        };
    }

    /** 导入阶段 → 阶段文字 lang key（null 表示后台任务尚未进入任一阶段）。 */
    private static String stageKey(ImportStage stage) {
        if (stage == null) {
            return "screen.rtsbuilding.blueprint.import_panel.stage.preparing";
        }
        return switch (stage) {
            case READING -> "screen.rtsbuilding.blueprint.import_panel.stage.reading";
            case PARSING -> "screen.rtsbuilding.blueprint.import_panel.stage.parsing";
            case WRITING -> "screen.rtsbuilding.blueprint.import_panel.stage.writing";
        };
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();
        int ch = contentHeight();
        ImportLayout layout = computeLayout(cx, cy, cw, ch);
        // 导入成功后的「打开蓝图列表」快捷按钮：打开蓝图文件管理面板并关闭本面板
        if (statusMessage != null && !statusIsError
                && isInButton(mouseX, mouseY, layout.openLibraryBtn())) {
            if (screen instanceof BuilderScreen builder) {
                builder.getBlueprintLibraryPanel().open();
            }
            setOpen(false);
            return;
        }
        // 只有点击「选择文件」按钮才打开文件选择对话框，上传区本身不响应点击
        if (!isInButton(mouseX, mouseY, layout.button())) return;

        List<Path> sources = BuilderScreen.chooseBlueprintFiles();
        if (sources == null || sources.isEmpty()) {
            return; // 用户取消选择
        }
        startAsyncImport(sources);
    }

    /**
     * 用系统原生 API 获取真实光标屏幕位置并转换为 RTS 虚拟坐标。
     * <p>文件拖放期间 Windows OLE 接管鼠标消息，GLFW/MC 的光标位置停留在拖放前旧值，
     * 导致悬浮动画与落点判断失效；而系统拖拽光标始终跟随鼠标，原生 {@code GetCursorPos}
     * 仍能取到真实位置。Windows 上用 LWJGL 内置 {@code org.lwjgl.system.windows.User32}
     * （不经 AWT、不受 {@code java.awt.headless} 影响），其他平台回退 AWT MouseInfo。
     * 转换基准与渲染坐标系一致（gui 坐标 × 虚拟宽/高 ÷ gui 宽/高）。失败返回 null。</p>
     */
    public static double[] liveCursorVirtual(com.rtsbuilding.uifw.window.api.UiPanelHost screen) {
        if (screen == null) return null;
        try {
            var mc = Minecraft.getInstance();
            var window = mc.getWindow();
            if (window == null) return null;
            // Windows：LWJGL Win32 GetCursorPos（不依赖 AWT）
            if (net.minecraft.Util.getPlatform() == net.minecraft.Util.OS.WINDOWS) {
                try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                    org.lwjgl.system.windows.POINT pt = org.lwjgl.system.windows.POINT.malloc(stack);
                    if (org.lwjgl.system.windows.User32.GetCursorPos(pt)) {
                        return toVirtual(pt.x(), pt.y(), window, screen);
                    }
                } catch (Throwable ignored) {
                    // 回退 AWT
                }
            }
            java.awt.Point p = java.awt.MouseInfo.getPointerInfo().getLocation();
            return toVirtual(p.x, p.y, window, screen);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 屏幕坐标（物理像素）→ RTS 虚拟坐标，基准与渲染坐标系一致。 */
    private static double[] toVirtual(int screenX, int screenY,
                                      com.mojang.blaze3d.platform.Window window,
                                      com.rtsbuilding.uifw.window.api.UiPanelHost screen) {
        int clientX = screenX - window.getX();
        int clientY = screenY - window.getY();
        int physW = Math.max(1, window.getScreenWidth());
        int physH = Math.max(1, window.getScreenHeight());
        double guiX = clientX * window.getGuiScaledWidth() / (double) physW;
        double guiY = clientY * window.getGuiScaledHeight() / (double) physH;
        int virtualW = screen.getUiWidth();
        int virtualH = screen.getUiHeight();
        return new double[]{
                guiX * virtualW / Math.max(1, window.getGuiScaledWidth()),
                guiY * virtualH / Math.max(1, window.getGuiScaledHeight())};
    }

    /**
     * 判断（RTS 虚拟坐标下的）点是否落在上传区圆角框内，供系统文件拖放判定使用：
     * 只有拖入圆角框范围内才触发导入。
     */
    public boolean isInsideDropZone(double mouseX, double mouseY) {
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();
        int ch = contentHeight();
        return isInZone(mouseX, mouseY, computeLayout(cx, cy, cw, ch).zone());
    }

    /**
     * 系统文件拖放导入（网页式）：面板打开时把拖入的文件直接导入（支持多文件）。
     * 由 {@code BuilderScreen.onFilesDrop} 转发，跳过文件选择对话框。
     */
    public void onFilesDropped(List<Path> paths) {
        List<Path> sources = importableFiles(paths);
        if (sources.isEmpty()) {
            this.statusMessage = Component.translatable("screen.rtsbuilding.blueprint.import_panel.failed",
                    Component.translatable("screen.rtsbuilding.blueprint.import_panel.drop_invalid").getString());
            this.statusIsError = true;
            return;
        }
        startAsyncImport(sources);
    }

    /**
     * 后台异步导入（支持多文件）：不阻塞渲染线程，逐个文件按「读取 → 解析 → 写出」阶段
     * 更新进度，完成/失败后切回 Minecraft 主线程刷新蓝图文件面板并统计提示结果。
     */
    private void startAsyncImport(List<Path> sources) {
        if (importing || sources == null || sources.isEmpty()) {
            return; // 正在导入或无有效文件，忽略重复触发
        }
        if (!(screen instanceof BuilderScreen builder)) return;
        Minecraft mc = Minecraft.getInstance();
        var registryAccess = mc.level != null
                ? mc.level.registryAccess()
                : RegistryAccess.EMPTY;
        this.statusMessage = null;
        this.statusIsError = false;
        this.importStage.set(null);
        this.importIndex.set(0);
        this.importTotal = sources.size();
        this.progressAnim.snapTo(0f);
        this.importing = true;

        CompletableFuture.supplyAsync(() -> {
            int success = 0;
            int failed = 0;
            String lastSavedName = null;
            String firstError = null;
            for (int i = 0; i < sources.size(); i++) {
                this.importIndex.set(i);
                Path source = sources.get(i);
                try {
                    Path saved = BlueprintLocalStore.importFile(source, registryAccess,
                            stage -> importStage.set(stage));
                    success++;
                    lastSavedName = saved.getFileName() == null
                            ? source.getFileName() == null ? source.toString() : source.getFileName().toString()
                            : saved.getFileName().toString();
                } catch (Exception ex) {
                    failed++;
                    LOG.warn("导入蓝图失败: {}", source, ex);
                    if (firstError == null) {
                        firstError = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    }
                }
            }
            return new ImportOutcome(success, failed, lastSavedName, firstError);
        }).thenAcceptAsync(outcome -> {
            this.importing = false;
            if (outcome.success() > 0) {
                builder.getBlueprintLibraryPanel().refreshFiles();
            }
            if (outcome.failed() == 0) {
                if (outcome.success() == 1) {
                    this.statusMessage = Component.translatable("screen.rtsbuilding.blueprint.import_panel.success",
                            outcome.lastSavedName());
                } else {
                    this.statusMessage = Component.translatable(
                            "screen.rtsbuilding.blueprint.import_panel.success_multi", outcome.success());
                }
                this.statusIsError = false;
            } else if (outcome.success() == 0) {
                this.statusMessage = Component.translatable("screen.rtsbuilding.blueprint.import_panel.failed",
                        outcome.firstError());
                this.statusIsError = true;
            } else {
                this.statusMessage = Component.translatable("screen.rtsbuilding.blueprint.import_panel.partial",
                        outcome.success(), outcome.failed());
                this.statusIsError = true;
            }
            this.progressAnim.snapTo(1f);
        }, mc::execute);
    }

    /** 导入结果统计（后台线程计算，主线程展示）。 */
    private record ImportOutcome(int success, int failed, String lastSavedName, String firstError) {}

    /** 从拖入路径中选出所有以受支持扩展名结尾的文件。 */
    private static List<Path> importableFiles(List<Path> paths) {
        List<Path> result = new ArrayList<>();
        if (paths == null) return result;
        for (Path p : paths) {
            if (p == null || p.getFileName() == null) continue;
            String lower = p.getFileName().toString().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".nbt") || lower.endsWith(".schem")
                    || lower.endsWith(".schematic") || lower.endsWith(".litematic")
                    || lower.endsWith(".json")) {
                result.add(p);
            }
        }
        return result;
    }

    @Override
    protected void onClose() {
        this.statusMessage = null;
        this.statusIsError = false;
        this.zoneHover.snapTo(0f);
        this.btnHover.snapTo(0f);
        this.openLibraryHover.snapTo(0f);
        super.onClose();
    }
}
