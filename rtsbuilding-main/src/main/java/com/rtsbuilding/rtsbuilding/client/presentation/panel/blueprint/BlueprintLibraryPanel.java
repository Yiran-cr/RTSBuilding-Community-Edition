package com.rtsbuilding.rtsbuilding.client.presentation.panel.blueprint;

import com.rtsbuilding.rtsbuilding.client.blueprint.BlueprintLocalStore;
import com.rtsbuilding.uifw.component.DeleteButton;
import com.rtsbuilding.uifw.layout.FlexLayout;
import com.rtsbuilding.uifw.layout.UiBox;
import com.rtsbuilding.uifw.layout.UiRect;
import com.rtsbuilding.uifw.window.component.ScrollBar;
import com.rtsbuilding.uifw.window.window.UiPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import static com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreenConstants.TOP_H;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.theme.ThemeManager;
import com.rtsbuilding.rtsbuilding.util.RtsPinyinSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    /** 底部提示消息（删除失败等）。 */
    private Component statusMessage;

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
        this.scrollBar.setScroll(0);
        this.statusMessage = null;
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

    /** 提交重命名（Enter）：调用本地存储重命名，成功后刷新列表。 */
    private void commitRename() {
        if (renamingFile != null) {
            String newName = renameBuffer.toString().trim();
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

        // 搜索框（与物品网格 GridRenderer 同款外观）
        int searchX = cx + 8;
        int searchW = cw - 16;
        boolean searchHovered = !searchFocused
                && mouseX >= searchX && mouseX < searchX + searchW
                && mouseY >= cy && mouseY < cy + SEARCH_H;
        SdfRenderer.drawInputBox(g, searchX, cy, searchW, SEARCH_H,
                searchFocused ? 1f : 0f, searchHovered ? 1f : 0f, 4);

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
                boolean hovering = mouseX >= cx && mouseX < cx + listW
                        && mouseY >= y && mouseY < y + ROW_H;

                // 条目常驻背景：奇偶行交替 + 悬停高亮
                int rowBg = (index % 2 == 0) ? UiPalette.get("list_row_even") : UiPalette.get("list_row_odd");
                if (hovering) {
                    rowBg = UiPalette.get("list_row_hover");
                }
                g.fill(cx, y, cx + listW, y + ROW_H, rowBg);

                // 行内排布：[内容区 fill, 删除按钮 fixed] 右对齐
                List<UiRect> rowRects = computeRowRects(cx, y, listW);
                UiRect contentRect = rowRects.get(0);
                UiRect delRect = rowRects.get(1);

                if (file.equals(renamingFile)) {
                    // 重命名编辑态：绘制输入框 + 缓冲文本 + 光标
                    int editX = contentRect.x() + 4;
                    int editW = contentRect.w() - 8;
                    SdfRenderer.drawInputBox(g, editX, y + 1, editW, ROW_H - 2,
                            1f, 0f, 3);
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

                // 删除按钮（悬停行时显示，需二次点击确认）
                int delX = delRect.x();
                int delY = delRect.y();
                boolean delHover = deleteButton.hit(mouseX, mouseY, delX, delY);
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
     * 行内排布：[内容区 fill, 删除按钮 fixed] 右对齐。渲染与点击命中共用同一布局，
     * 保证两者坐标一致。
     */
    private static List<UiRect> computeRowRects(int cx, int y, int listW) {
        return FlexLayout.layout(FlexLayout.Direction.ROW, FlexLayout.Justify.START,
                FlexLayout.Align.CENTER, 3, cx, y, listW, ROW_H,
                List.of(UiBox.fill(1f), UiBox.fixed(DeleteButton.SIZE, DeleteButton.SIZE)));
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
                UiRect delRect = computeRowRects(cx, y, listW).get(1);
                boolean onDelete = deleteButton.hit(mouseX, mouseY, delRect.x(), delRect.y());
                if (onDelete) {
                    // 重命名编辑态下不响应删除
                    if (renamingFile == null) {
                        // 二次点击确认删除
                        if (pendingDelete == file) {
                            deleteFile(file);
                        } else {
                            pendingDelete = file;
                        }
                    }
                    return;
                }
                // 点击条目名字区域：进入重命名（点击重命名中的行则保持编辑态）
                if (renamingFile == null || !renamingFile.equals(file)) {
                    if (renamingFile != null) {
                        commitRename();
                    }
                    startRename(file);
                    pendingDelete = null;
                }
                return;
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
        this.renamingFile = null;
        this.renameBuffer.setLength(0);
        this.renameCursorPos = 0;
        this.statusMessage = null;
        super.onClose();
    }
}
