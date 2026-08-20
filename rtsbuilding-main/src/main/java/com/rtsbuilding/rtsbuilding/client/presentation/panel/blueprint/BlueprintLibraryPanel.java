package com.rtsbuilding.rtsbuilding.client.presentation.panel.blueprint;

import com.rtsbuilding.rtsbuilding.client.blueprint.BlueprintLocalStore;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.blueprint.io.BlueprintReaders;
import com.rtsbuilding.uifw.animate.AnimFloat;
import com.rtsbuilding.uifw.animate.ColorAnimation;
import com.rtsbuilding.uifw.animate.Easing;
import com.rtsbuilding.uifw.component.DeleteButton;
import com.rtsbuilding.uifw.layout.FlexLayout;
import com.rtsbuilding.uifw.layout.UiBox;
import com.rtsbuilding.uifw.layout.UiRect;
import com.rtsbuilding.uifw.state.HoverSuppression;
import com.rtsbuilding.uifw.window.component.ScrollBar;
import com.rtsbuilding.uifw.window.window.UiPanel;
import static com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreenConstants.TOP_H;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.theme.ThemeManager;
import com.rtsbuilding.rtsbuilding.util.RtsPinyinSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.Util;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.Locale;
import java.util.Map;

/**
 * 蓝图文件管理面板 —— 列出本地蓝图目录（config/rts_building/blueprints）中的
 * 全部 .nbt 蓝图文件，支持搜索过滤、滚动浏览、删除文件。
 * <p>
 * 顶部搜索框参考物品网格界面（{@code client.presentation.plugin.grid}）的实现：
 * 独立维护 {@code searchFocused} 焦点状态与 {@code searchBuffer} 缓冲，输入即实时过滤，
 * Enter/Esc/点击外部失焦。匹配逻辑：文件名包含（不区分大小写）+ 拼音搜索
 * （复用 {@link RtsPinyinSearch}，支持中文/全拼/首字母模糊匹配）。
 */
public final class BlueprintLibraryPanel extends UiPanel {

    private static final Logger LOG = LoggerFactory.getLogger("RTS-BlueprintLibrary");

    /** 面板默认尺寸。 */
    private static final int DEFAULT_W = 300;
    private static final int DEFAULT_H = 340;
    /** 搜索框高度（与物品网格 GridRenderer.SEARCH_INPUT_H 一致）。 */
    private static final int SEARCH_H = 18;
    /** 搜索框内容左右内边距。 */
    private static final int SEARCH_PAD = 4;
    /** 搜索框与列表之间的垂直间距。 */
    private static final int SEARCH_LIST_GAP = 6;
    /** 光标闪烁周期（毫秒）。 */
    private static final long CURSOR_BLINK_MS = 500;
    /** 文件列表行高。 */
    private static final int ROW_H = 20;

    private final ScrollBar scrollBar = new ScrollBar();
    /** 行内删除按钮（矢量，复用同一实例渲染/命中，坐标随行变化）。 */
    private final DeleteButton deleteButton = new DeleteButton();
    /** 本地全部蓝图文件（打开时扫描，搜索时保持不丢失全量）。 */
    private final List<Path> allFiles = new ArrayList<>();
    /** 过滤后显示的蓝图文件。 */
    private final List<Path> files = new ArrayList<>();
    /** 正在删除的文件（待确认状态），为空表示无待确认项。 */
    private Path pendingDelete;

    /** 当前选中的蓝图文件（单击选中，联动打开结构预览面板）。 */
    private Path selectedFile;
    /** 上次点击的文件与其时间戳（用于双击进入重命名）。 */
    private Path lastClickedFile;
    private long lastClickedMs;
    /** 单击与双击的判定间隔（毫秒）。 */
    private static final long DOUBLE_CLICK_MS = 500;

    /** 底部提示消息（删除失败等）。 */
    private Component statusMessage;

    // ── 动画状态（与物品网格 GridRenderer / 工作流 WorkflowRenderer 同款） ──
    /** 搜索框焦点高亮动画（焦点变化平滑过渡，与物品网格搜索框一致）。 */
    private final AnimFloat searchFocusAnim = AnimFloat.of(0f, 100L, Easing.EASE_OUT_QUAD);
    /** 搜索框悬停高亮动画。 */
    private final AnimFloat searchHoverAnim = AnimFloat.hover();
    /** 重命名编辑框焦点高亮动画。 */
    private final AnimFloat renameFocusAnim = AnimFloat.of(0f, 100L, Easing.EASE_OUT_QUAD);
    /** 上次渲染时的搜索框焦点状态（变化时驱动焦点动画）。 */
    private boolean prevSearchFocused;
    /** 上次渲染时是否处于重命名编辑态（变化时驱动编辑框焦点动画）。 */
    private boolean prevRenaming;
    /** 文件行悬停动画（按文件关联，各行独立淡入淡出）。 */
    private final Map<Path, AnimFloat> rowHoverAnims = new HashMap<>();
    /** 行内「打开文件」按钮悬停动画（按文件关联）。 */
    private final Map<Path, AnimFloat> openFileBtnHovers = new HashMap<>();
    /** 行内「使用」按钮悬停动画（按文件关联）。 */
    private final Map<Path, AnimFloat> useBtnHovers = new HashMap<>();
    /** 行内「预览」按钮悬停动画（按文件关联）。 */
    private final Map<Path, AnimFloat> previewBtnHovers = new HashMap<>();

    // ── 搜索框状态（与物品网格 GridInputHandler 同款） ──────────────
    /** 搜索框是否聚焦。 */
    private boolean searchFocused;
    /** 搜索输入缓冲。 */
    private final StringBuilder searchBuffer = new StringBuilder();
    /** 搜索光标位置。 */
    private int searchCursorPos;
    /** 光标闪烁时间戳。 */
    private long searchCursorBlink;

    // ── 重命名状态 ─────────────────────────────────────────────────
    /** 正在重命名的蓝图文件（null 表示未处于重命名编辑态）。 */
    private Path renamingFile;
    /** 重命名输入缓冲（不含扩展名）。 */
    private final StringBuilder renameBuffer = new StringBuilder();
    /** 重命名光标位置。 */
    private int renameCursorPos;
    /** 重命名光标闪烁时间戳。 */
    private long renameCursorBlink;

    @Override
    public void init(com.rtsbuilding.uifw.window.api.UiPanelHost screen) {
        super.init(screen);
        this.resizable = true;
    }

    /** 打开面板并刷新本地蓝图文件列表（保留当前搜索词）。 */
    public void open() {
        refreshFiles();
        setOpen(true);
        markBroughtToFront();
    }

    /** 重新扫描本地蓝图目录并刷新列表。 */
    public void refreshFiles() {
        this.allFiles.clear();
        this.allFiles.addAll(BlueprintLocalStore.listBlueprints());
        this.pendingDelete = null;
        this.selectedFile = null;
        this.lastClickedFile = null;
        this.scrollBar.setScroll(0);
        this.statusMessage = null;
        // 文件集合变化后旧的行 hover 动画缓存不再命中，整体清理避免无界增长
        this.rowHoverAnims.clear();
        this.openFileBtnHovers.clear();
        this.useBtnHovers.clear();
        this.previewBtnHovers.clear();
        applySearch();
    }

    /** 根据当前搜索缓冲过滤文件列表（实时过滤，含拼音匹配）。 */
    private void applySearch() {
        String query = searchBuffer.toString().toLowerCase(Locale.ROOT);
        this.files.clear();
        for (Path file : allFiles) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (query.isEmpty()
                    || name.contains(query)
                    || RtsPinyinSearch.contains(name, query)) {
                this.files.add(file);
            }
        }
        // 过滤后滚动位置可能越界，强制复位到顶部
        this.scrollBar.setScroll(0);
    }

    private void deleteFile(Path file) {
        try {
            BlueprintLocalStore.delete(file);
            this.allFiles.remove(file);
            this.files.remove(file);
            this.pendingDelete = null;
            if (this.selectedFile != null && this.selectedFile.equals(file)) {
                this.selectedFile = null;
            }
            if (this.files.isEmpty()) {
                this.scrollBar.setScroll(0);
            }
        } catch (IOException e) {
            LOG.warn("删除蓝图文件失败: {}", file, e);
            this.statusMessage = Component.translatable("message.rtsbuilding.blueprint.delete_failed");
        }
    }

    /** 进入指定蓝图文件的重命名编辑态，预填当前文件名（不含扩展名）。 */
    private void startRename(Path file) {
        this.renamingFile = file;
        String baseName = stripExtension(file.getFileName().toString());
        this.renameBuffer.setLength(0);
        this.renameBuffer.append(baseName);
        this.renameCursorPos = this.renameBuffer.length();
        this.renameCursorBlink = System.currentTimeMillis();
    }

    /**
     * 提交重命名（Enter/点击其他处）：若输入内容与原文件名（不含扩展名）完全一致，
     * 直接退出编辑态、不做任何写入；否则调用本地存储重命名并提示结果。
     */
    private void commitRename() {
        if (renamingFile != null) {
            String newName = renameBuffer.toString().trim();
            String oldName = stripExtension(renamingFile.getFileName().toString());
            if (!newName.equals(oldName)) {
                try {
                    Path renamed = BlueprintLocalStore.rename(renamingFile, newName);
                    this.statusMessage = Component.translatable("message.rtsbuilding.blueprint.renamed",
                            renamed.getFileName().toString());
                } catch (IOException e) {
                    LOG.warn("重命名蓝图文件失败: {} -> {}", renamingFile, newName, e);
                    this.statusMessage = Component.translatable("message.rtsbuilding.blueprint.rename_failed",
                            e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                }
            }
        }
        this.renamingFile = null;
        refreshFiles();
    }

    /** 取消重命名（Esc/点击其他处）。 */
    private void cancelRename() {
        this.renamingFile = null;
        this.renameBuffer.setLength(0);
        this.renameCursorPos = 0;
    }

    /** 去掉文件名的 .nbt 扩展名。 */
    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    // ── UiPanel 布局与渲染 ─────────────────────────────────────────

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.blueprint.library.title");
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
            // 尺寸：保持自适应（不超过屏幕，留边距），位置统一为屏幕居中基准
            int margin = 8;
            int availableW = this.screen.getUiWidth() - margin * 2;
            int w = Math.min(getDefaultWidth(), availableW);
            int h = Math.min(getDefaultHeight(), Math.max(getMinWindowHeight(), this.screen.getUiHeight() - TOP_H - 6 - margin));
            setSize(w, h);
            positionCentered(TOP_H + 6, margin);
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();
        int ch = contentHeight();
        int textColor = ThemeManager.getTextColor();

        // 搜索框（与物品网格 GridRenderer 同款外观，焦点/悬停高亮带平滑动画）
        int searchX = cx + 8;
        int searchW = cw - 16;
        if (searchFocused != prevSearchFocused) {
            searchFocusAnim.target(searchFocused ? 1f : 0f);
            prevSearchFocused = searchFocused;
        }
        boolean searchHovered = !searchFocused
                && mouseX >= searchX && mouseX < searchX + searchW
                && mouseY >= cy && mouseY < cy + SEARCH_H;
        SdfRenderer.drawInputBox(g, searchX, cy, searchW, SEARCH_H,
                searchFocusAnim.get(), searchHoverAnim.track(searchHovered), 4);

        Font font = Minecraft.getInstance().font;
        String searchText = searchBuffer.toString();
        int textX = searchX + SEARCH_PAD;
        int textY = cy + (SEARCH_H - font.lineHeight) / 2;
        int contentAreaW = searchW - SEARCH_PAD * 2;

        if (searchFocused) {
            String displayText = TextRenderer.trimToWidth(font, searchText, contentAreaW);
            g.drawString(font, displayText, textX, textY, textColor, false);
            if ((System.currentTimeMillis() / CURSOR_BLINK_MS) % 2 == 0) {
                int cursorX = textX + font.width(displayText);
                g.fill(cursorX, textY, cursorX + 1, textY + font.lineHeight, UiPalette.get("input_cursor"));
            }
        } else {
            String placeholder = searchText.isEmpty()
                    ? Component.translatable("screen.rtsbuilding.blueprint.library.search").getString()
                    : searchText;
            String displayText = TextRenderer.trimToWidth(font, placeholder, contentAreaW);
            int placeholderColor = searchText.isEmpty() ? (textColor & 0xFFFFFF) | 0x60000000 : textColor;
            g.drawString(font, displayText, textX, textY, placeholderColor, false);
        }

        // 搜索框与列表之间的分界线
        int dividerY = cy + SEARCH_H + SEARCH_LIST_GAP - 3;
        g.fill(cx, dividerY, cx + cw, dividerY + 1, UiPalette.get("list_separator"));

        // 列表区域（搜索框下方）
        int listY = cy + SEARCH_H + SEARCH_LIST_GAP;
        int listH = ch - SEARCH_H - SEARCH_LIST_GAP;

        if (allFiles.isEmpty()) {
            String empty = Component.translatable("screen.rtsbuilding.blueprint.library.empty").getString();
            TextRenderer.drawCentered(g, Minecraft.getInstance().font, empty,
                    cx + cw / 2,
                    listY + (listH - Minecraft.getInstance().font.lineHeight) / 2,
                    textColor);
            return;
        }
        if (files.isEmpty()) {
            String noMatch = Component.translatable("screen.rtsbuilding.blueprint.library.no_match").getString();
            TextRenderer.drawCentered(g, Minecraft.getInstance().font, noMatch,
                    cx + cw / 2,
                    listY + (listH - Minecraft.getInstance().font.lineHeight) / 2,
                    textColor);
            return;
        }

        scrollBar.setContent(files.size() * ROW_H, listH);
        int scroll = scrollBar.getScroll();

        int barX = cx + cw - 11;
        if (scrollBar.isVisible()) {
            scrollBar.render(g, barX, listY, listH);
        }

        int listW = cw - (scrollBar.isVisible() ? 14 : 4);
        int y = listY - scroll;
        int index = 0;
        for (Path file : files) {
            if (y + ROW_H <= listY) {
                y += ROW_H;
                index++;
                continue;
            }
            if (y >= listY + listH) break;
            if (y >= listY) {
                // 上层面板（浮窗/常驻面板）覆盖时抑制本面板的全部悬停判断，
                // 否则行高亮与行内按钮会穿透显示在上层面板之下
                boolean suppressed = HoverSuppression.floatingWindow().isSuppressed();
                boolean hovering = !suppressed && mouseX >= cx && mouseX < cx + listW
                        && mouseY >= y && mouseY < y + ROW_H;

                // 条目常驻背景：奇偶行交替 + 悬停高亮（按文件独立平滑过渡），选中行用强调色
                AnimFloat rowHover = rowHoverAnims.computeIfAbsent(file, k -> AnimFloat.hover());
                float hoverT = rowHover.track(hovering);
                int base = (index % 2 == 0) ? UiPalette.get("list_row_even") : UiPalette.get("list_row_odd");
                int rowBg;
                if (file.equals(selectedFile)) {
                    rowBg = ColorAnimation.lerpRGB(base, UiPalette.accent(), 0.45f);
                } else {
                    rowBg = ColorAnimation.lerpRGB(base, UiPalette.get("list_row_hover"), hoverT);
                }
                g.fill(cx, y, cx + listW, y + ROW_H, rowBg);

                // 行内排布：[内容区 fill, 使用, 预览, 打开文件, 删除] 右对齐
                List<UiRect> rowRects = computeRowRects(cx, y, listW);
                UiRect contentRect = rowRects.get(0);
                UiRect useRect = rowRects.get(1);
                UiRect previewRect = rowRects.get(2);
                UiRect openRect = rowRects.get(3);
                UiRect delRect = rowRects.get(4);

                if (file.equals(renamingFile)) {
                    // 重命名编辑态：绘制输入框 + 缓冲文本 + 光标（焦点高亮平滑淡入）
                    if (renamingFile != null != prevRenaming) {
                        renameFocusAnim.target(renamingFile != null ? 1f : 0f);
                        prevRenaming = renamingFile != null;
                    }
                    int editX = contentRect.x() + 4;
                    int editW = contentRect.w() - 8;
                    SdfRenderer.drawInputBox(g, editX, y + 1, editW, ROW_H - 2,
                            renameFocusAnim.get(), 0f, 3);
                    String editText = renameBuffer.toString();
                    String visible = TextRenderer.trimToWidth(font, editText, editW - 8);
                    g.drawString(font, visible, editX + 3,
                            y + (ROW_H - font.lineHeight) / 2 + 1, textColor, false);
                    if ((System.currentTimeMillis() / CURSOR_BLINK_MS) % 2 == 0) {
                        int cursorX = editX + 3 + font.width(visible);
                        g.fill(cursorX, y + (ROW_H - font.lineHeight) / 2 + 1,
                                cursorX + 1, y + (ROW_H + font.lineHeight) / 2 + 1, UiPalette.get("input_cursor"));
                    }
                } else {
                    String name = file.getFileName().toString();
                    TextRenderer.draw(g, name, contentRect.x() + 6, y + (ROW_H - Minecraft.getInstance().font.lineHeight) / 2 + 1, textColor);
                }

                // 使用 / 预览 / 打开文件按钮（悬停行时显示）
                boolean useHover = !suppressed && hitButton(mouseX, mouseY, useRect);
                if (hovering || useHover) {
                    renderTextButton(g, mouseX, mouseY, useRect, "button.rtsbuilding.blueprint.use", useBtnHovers, file);
                }
                boolean previewHover = !suppressed && hitButton(mouseX, mouseY, previewRect);
                if (hovering || previewHover) {
                    renderTextButton(g, mouseX, mouseY, previewRect, "button.rtsbuilding.blueprint.preview", previewBtnHovers, file);
                }
                boolean openHover = !suppressed && hitButton(mouseX, mouseY, openRect);
                if (hovering || openHover) {
                    renderTextButton(g, mouseX, mouseY, openRect, "button.rtsbuilding.blueprint.open_file", openFileBtnHovers, file);
                }

                // 删除按钮（悬停行时显示，需二次点击确认）
                int delX = delRect.x();
                int delY = delRect.y();
                boolean delHover = !suppressed && deleteButton.hit(mouseX, mouseY, delX, delY);
                if (hovering || delHover) {
                    deleteButton.render(g, mouseX, mouseY, delX, delY, pendingDelete == file);
                }
            }
            y += ROW_H;
            index++;
        }

        if (statusMessage != null) {
            TextRenderer.draw(g, statusMessage, cx + 6, listY + listH - Minecraft.getInstance().font.lineHeight - 2, UiPalette.get("status_error"));
        }
    }

    /**
     * 行内排布：[内容区 fill, 使用, 预览, 打开文件, 删除] 右对齐。
     * 渲染与点击命中共用同一布局，保证两者坐标一致。
     */
    private static List<UiRect> computeRowRects(int cx, int y, int listW) {
        return FlexLayout.layout(FlexLayout.Direction.ROW, FlexLayout.Justify.START,
                FlexLayout.Align.CENTER, 3, cx, y, listW, ROW_H,
                List.of(UiBox.fill(1f),
                        UiBox.fixed(useButtonWidth(), DeleteButton.SIZE),
                        UiBox.fixed(previewButtonWidth(), DeleteButton.SIZE),
                        UiBox.fixed(openLocationWidth(), DeleteButton.SIZE),
                        UiBox.fixed(DeleteButton.width(), DeleteButton.SIZE)));
    }

    /** 「使用」按钮宽度（px）：按当前语言按钮文字渲染宽度自适应（含左右内边距）。 */
    private static int useButtonWidth() {
        return Minecraft.getInstance().font.width(
                Component.translatable("button.rtsbuilding.blueprint.use").getString()) + 12;
    }

    /** 「预览」按钮宽度（px）：按当前语言按钮文字渲染宽度自适应（含左右内边距）。 */
    private static int previewButtonWidth() {
        return Minecraft.getInstance().font.width(
                Component.translatable("button.rtsbuilding.blueprint.preview").getString()) + 12;
    }

    /** 打开文件按钮宽度（px）：按当前语言按钮文字的渲染宽度自适应（含左右内边距）。 */
    private static int openLocationWidth() {
        Font font = Minecraft.getInstance().font;
        return font.width(Component.translatable("button.rtsbuilding.blueprint.open_file").getString()) + 12;
    }

    /** 通用行内文字按钮：本地化文字 + 悬停渐变（与删除按钮同风格）。 */
    private void renderTextButton(GuiGraphics g, int mouseX, int mouseY, UiRect rect,
                                  String textKey, Map<Path, AnimFloat> hoverAnims, Path file) {
        boolean hovering = hitButton(mouseX, mouseY, rect);
        float t = hoverAnims.computeIfAbsent(file, k -> AnimFloat.hover()).track(hovering);
        int bg = ColorAnimation.lerpRGB(UiPalette.get("list_btn"), UiPalette.get("list_btn_hover"), t);
        SdfRenderer.drawRoundedRect(g, rect.x(), rect.y(), rect.w(), rect.h(), 3, bg);
        Font font = Minecraft.getInstance().font;
        String text = Component.translatable(textKey).getString();
        TextRenderer.drawCentered(g, font, text,
                rect.x() + rect.w() / 2, rect.y() + (rect.h() - font.lineHeight) / 2 + 1,
                UiPalette.get("tooltip_text"));
    }

    /** 命中检测（与渲染坐标一致）。 */
    private static boolean hitButton(double mx, double my, UiRect rect) {
        return mx >= rect.x() && mx < rect.x() + rect.w()
                && my >= rect.y() && my < rect.y() + rect.h();
    }

    /** 用系统文件管理器打开该蓝图文件所在文件夹位置（Windows/macOS 上定位选中该文件）。 */
    private void openFileLocation(Path file) {
        try {
            if (file == null || !Files.exists(file)) {
                this.statusMessage = Component.translatable("message.rtsbuilding.blueprint.open_file_failed");
                return;
            }
            Path abs = file.toAbsolutePath();
            if (Util.getPlatform() == Util.OS.WINDOWS) {
                Runtime.getRuntime().exec(new String[]{"explorer", "/select,", abs.toString()});
                this.statusMessage = null;
                return;
            }
            if (Util.getPlatform() == Util.OS.OSX) {
                Runtime.getRuntime().exec(new String[]{"open", "-R", abs.toString()});
                this.statusMessage = null;
                return;
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file.getParent().toFile());
                this.statusMessage = null;
                return;
            }
        } catch (IOException | UnsupportedOperationException e) {
            LOG.warn("打开蓝图文件位置失败: {}", file, e);
        }
        this.statusMessage = Component.translatable("message.rtsbuilding.blueprint.open_file_failed");
    }

    /**
     * 使用该蓝图：后台线程解析蓝图文件 → 主线程进入放置模式（BuilderScreen 激活放置状态，
     * 准星瞄准目标后左键确认，经服务端 BLUEPRINT_BUILD 工作流逐格建造）。
     */
    private void useBlueprint(Path file) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || file == null) return;
        this.selectedFile = file;
        RegistryAccess registryAccess = mc.level.registryAccess();
        CompletableFuture.supplyAsync(() -> {
            try {
                byte[] data = Files.readAllBytes(file);
                return BlueprintReaders.parse(data, file.getFileName().toString(), registryAccess);
            } catch (Exception e) {
                LOG.warn("解析蓝图失败: {}", file, e);
                return null;
            }
        }).thenAcceptAsync(blueprint -> {
            if (blueprint == null) {
                this.statusMessage = Component.translatable(
                        "screen.rtsbuilding.blueprint.import_panel.failed",
                        Component.translatable("screen.rtsbuilding.blueprints.status.parse_failed").getString());
                return;
            }
            if (screen instanceof BuilderScreen builder) {
                builder.startBlueprintPlacement(blueprint);
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.translatable("message.rtsbuilding.blueprint.use_hint"), true);
                }
            }
        }, mc::execute);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();
        int ch = contentHeight();

        // 搜索框点击：聚焦（与渲染保持一致：左右各 8px 外边距）。
        // 若正在重命名则先提交，避免两个编辑态并存。
        int searchX = cx + 8;
        int searchW = cw - 16;
        boolean onSearch = mouseX >= searchX && mouseX < searchX + searchW
                && mouseY >= cy && mouseY < cy + SEARCH_H;
        if (onSearch) {
            if (renamingFile != null) {
                commitRename();
            }
            searchFocused = true;
            searchCursorBlink = System.currentTimeMillis();
            return;
        }
        // 点击搜索框外：失焦并应用当前搜索；若在重命名则提交
        if (searchFocused) {
            searchFocused = false;
            applySearch();
        }
        if (renamingFile != null) {
            commitRename();
        }

        int listY = cy + SEARCH_H + SEARCH_LIST_GAP;
        int listH = ch - SEARCH_H - SEARCH_LIST_GAP;

        if (files.isEmpty()) return;

        scrollBar.setContent(files.size() * ROW_H, listH);
        int scroll = scrollBar.getScroll();
        int barX = cx + cw - 11;
        if (scrollBar.isVisible() && scrollBar.handleClick(mouseX, mouseY, barX, listY, listH)) {
            // 拖动滚动条前先提交未完成的重命名
            if (renamingFile != null) {
                commitRename();
            }
            return;
        }

        int listW = cw - (scrollBar.isVisible() ? 14 : 4);

        int y = listY - scroll;
        for (Path file : files) {
            if (y + ROW_H <= listY) {
                y += ROW_H;
                continue;
            }
            if (y >= listY + listH) break;
            if (mouseY >= y && mouseY < y + ROW_H) {
                List<UiRect> rowRects = computeRowRects(cx, y, listW);
                // 使用按钮：进入蓝图放置模式（重命名编辑态下不响应）
                if (hitButton(mouseX, mouseY, rowRects.get(1))) {
                    if (renamingFile == null) {
                        useBlueprint(file);
                    }
                    return;
                }
                // 预览按钮：打开结构预览面板
                if (hitButton(mouseX, mouseY, rowRects.get(2))) {
                    if (renamingFile == null) {
                        selectedFile = file;
                        if (screen instanceof BuilderScreen builder) {
                            builder.getBlueprintPreviewPanel().show(file);
                        }
                    }
                    return;
                }
                // 打开文件位置按钮
                if (hitButton(mouseX, mouseY, rowRects.get(3))) {
                    if (renamingFile == null) {
                        openFileLocation(file);
                    }
                    return;
                }
                UiRect delRect = rowRects.get(4);
                boolean onDelete = deleteButton.hit(mouseX, mouseY, delRect.x(), delRect.y());
                if (onDelete) {
                    // 重命名编辑态下不响应删除
                    if (renamingFile == null) {
                        // 二次点击确认删除：首次点击进入确认态并闪烁提醒
                        if (pendingDelete == file) {
                            deleteFile(file);
                        } else {
                            pendingDelete = file;
                            deleteButton.triggerFlash();
                        }
                    }
                    return;
                }
                // 点击条目名字区域：双击进入重命名；单击仅选中高亮（打开预览只走「预览」按钮）
                if (renamingFile == null || !renamingFile.equals(file)) {
                    if (renamingFile != null) {
                        commitRename();
                    }
                    long now = System.currentTimeMillis();
                    if (file.equals(lastClickedFile) && now - lastClickedMs < DOUBLE_CLICK_MS) {
                        startRename(file);
                        pendingDelete = null;
                    } else {
                        lastClickedFile = file;
                        lastClickedMs = now;
                        selectedFile = file;
                    }
                    return;
                }
            }
            y += ROW_H;
        }
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (files.isEmpty()) return false;
        int listH = contentHeight() - SEARCH_H - SEARCH_LIST_GAP;
        scrollBar.setContent(files.size() * ROW_H, listH);
        return scrollBar.handleScroll(scrollY);
    }

    /** 键盘输入：重命名编辑态或搜索框聚焦时处理编辑键并吞掉其他键，防止误触 RTS 快捷键。 */
    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        // 重命名编辑态优先
        if (renamingFile != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitRename();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelRename();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (renameCursorPos > 0 && renameBuffer.length() > 0) {
                    renameBuffer.deleteCharAt(renameCursorPos - 1);
                    renameCursorPos--;
                    renameCursorBlink = System.currentTimeMillis();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                if (renameCursorPos < renameBuffer.length()) {
                    renameBuffer.deleteCharAt(renameCursorPos);
                    renameCursorBlink = System.currentTimeMillis();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                renameCursorPos = Math.max(0, renameCursorPos - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                renameCursorPos = Math.min(renameBuffer.length(), renameCursorPos + 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_HOME) {
                renameCursorPos = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_END) {
                renameCursorPos = renameBuffer.length();
                return true;
            }
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    renameBuffer.insert(renameCursorPos, clip);
                    renameCursorPos += clip.length();
                    renameCursorBlink = System.currentTimeMillis();
                }
                return true;
            }
            // 可打印字符交由 charTyped 录入，这里吞掉按键避免误触 RTS 快捷键
            return true;
        }

        if (!searchFocused) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            searchFocused = false;
            applySearch();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            searchFocused = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (searchCursorPos > 0 && searchBuffer.length() > 0) {
                searchBuffer.deleteCharAt(searchCursorPos - 1);
                searchCursorPos--;
                searchCursorBlink = System.currentTimeMillis();
                applySearch();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (searchCursorPos < searchBuffer.length()) {
                searchBuffer.deleteCharAt(searchCursorPos);
                searchCursorBlink = System.currentTimeMillis();
                applySearch();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            searchCursorPos = Math.max(0, searchCursorPos - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            searchCursorPos = Math.min(searchBuffer.length(), searchCursorPos + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            searchCursorPos = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            searchCursorPos = searchBuffer.length();
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                searchBuffer.insert(searchCursorPos, clip);
                searchCursorPos += clip.length();
                searchCursorBlink = System.currentTimeMillis();
                applySearch();
            }
            return true;
        }
        // 可打印字符交由 charTyped 录入，这里吞掉按键避免误触 RTS 快捷键
        return true;
    }

    /** 字符输入：重命名编辑态或搜索框聚焦时录入任意可打印字符。 */
    @Override
    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        if (renamingFile != null) {
            if (codePoint >= 32 && !Character.isISOControl(codePoint)) {
                renameBuffer.insert(renameCursorPos, codePoint);
                renameCursorPos++;
                renameCursorBlink = System.currentTimeMillis();
            }
            return true;
        }
        if (!searchFocused) return false;
        if (codePoint >= 32 && !Character.isISOControl(codePoint)) {
            searchBuffer.insert(searchCursorPos, codePoint);
            searchCursorPos++;
            searchCursorBlink = System.currentTimeMillis();
            applySearch();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && scrollBar.isDragging()) {
            int listY = contentY() + SEARCH_H + SEARCH_LIST_GAP;
            int listH = contentHeight() - SEARCH_H - SEARCH_LIST_GAP;
            scrollBar.handleDrag(mouseY, listY, listH);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollBar.endDrag();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void onClose() {
        this.searchFocused = false;
        this.pendingDelete = null;
        this.selectedFile = null;
        this.lastClickedFile = null;
        this.renamingFile = null;
        this.renameBuffer.setLength(0);
        this.renameCursorPos = 0;
        this.statusMessage = null;
        // 重置动画与焦点状态，保证下次打开从静止态开始
        this.searchFocusAnim.snapTo(0f);
        this.searchHoverAnim.snapTo(0f);
        this.renameFocusAnim.snapTo(0f);
        this.prevSearchFocused = false;
        this.prevRenaming = false;
        this.rowHoverAnims.clear();
        this.openFileBtnHovers.clear();
        this.useBtnHovers.clear();
        this.previewBtnHovers.clear();
        super.onClose();
    }
}
