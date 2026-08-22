package com.rtsbuilding.rtsbuilding.client.presentation.panel.resume;

import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.uifw.layout.FlexLayout;
import com.rtsbuilding.uifw.layout.UiBox;
import com.rtsbuilding.uifw.layout.UiRect;
import com.rtsbuilding.uifw.window.component.ScrollBar;
import com.rtsbuilding.uifw.window.window.UiPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.render.GuiItemRenderer;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import java.util.Locale;

import static com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreenConstants.TOP_H;

/**
 * 工作流恢复面板（浮动窗口，每个工作流独立一个实例）：
 * <ul>
 *   <li>显示恢复需要多少方块（材料清单）。</li>
 *   <li>无冲突且材料足够 → 「开始」；材料不足 → 按钮灰色化。</li>
 *   <li>有冲突 → 「跳过」（跳过冲突继续放置）与「覆盖」（原生破坏冲突方块后放置）。</li>
 * </ul>
 * <p>材料清单支持滚动条：缺失物品较多时可在清单区滚轮 / 拖拽滚动条查看全部材料。</p>
 */
public final class ResumeWorkflowPanel extends UiPanel {

    private static final int PANEL_W = 288;
    private static final int PANEL_H = 260;
    private static final int PAD = 8;
    private static final int ROW_H = 20;
    private static final int BTN_H = 16;
    private static final int BTN_W = 56;
    /** 滚动条宽度（内容区右侧预留）。 */
    private static final int SCROLL_BAR_W = 8;
    /** 清单区底部与按钮区之间的留白。 */
    private static final int LIST_BOTTOM_GAP = 6;

    /** 材料充足文字色。 */
    private static final int COLOR_OK = 0xFF66BB6A;
    /** 材料不足文字色。 */
    private static final int COLOR_MISSING = 0xFFFF5252;
    /** 冲突提示色。 */
    private static final int COLOR_CONFLICT = 0xFFFFB74D;
    /** 材料充足进度条渐变（深 → 亮）。 */
    private static final int PROGRESS_OK_START = 0xFF2E7D32;
    private static final int PROGRESS_OK_END = 0xFF66BB6A;
    /** 材料不足进度条渐变（深 → 亮）。 */
    private static final int PROGRESS_MISSING_START = 0xFFC62828;
    private static final int PROGRESS_MISSING_END = 0xFFFF5252;

    private static final int BAR_TEXT_COLOR = UiPalette.get("text_hover");

    /** 材料行图标尺寸。 */
    private static final int MAT_ICON_SIZE = 16;
    /** 材料行图标与进度条间距。 */
    private static final int MAT_ICON_GAP = 4;
    /** 材料行进度条高度。 */
    private static final int MAT_BAR_H = 12;

    /** 每个工作流条目一个面板实例（entryId → panel）。 */
    private static final java.util.Map<Integer, ResumeWorkflowPanel> PANELS = new java.util.HashMap<>();

    private final int[] startRect = new int[4];
    private final int[] skipRect = new int[4];
    private final int[] overwriteRect = new int[4];

    /** 材料清单滚动条：缺失物品较多时逐行滚动查看（滚轮 / 拖拽滑块）。 */
    private final ScrollBar scrollBar = new ScrollBar().withScrollStep(1).withScrollBottomPad(0);
    /** 清单区命中矩形（渲染时更新，供滚轮滚动与拖拽命中判断）。 */
    private final int[] listRect = new int[4];
    /** 滚动条渲染/命中位置（渲染时更新）。 */
    private int scrollBarX;
    private int scrollBarY;
    private int scrollBarLen;

    private int entryId;
    private boolean blueprint;
    private java.util.List<String> matIds = java.util.List.of();
    private java.util.List<String> matLabels = java.util.List.of();
    private java.util.List<Integer> matReq = java.util.List.of();
    private java.util.List<Long> matAvail = java.util.List.of();
    private String itemLabel = "";
    private long itemAvail;
    private int neededItems;
    private long missingItems;
    private int conflictCount;

    private ResumeWorkflowPanel() {
        this.draggable = true;
        this.closable = true;
    }

    /** 打开（或刷新）指定工作流的恢复面板；每个工作流独立实例。 */
    public static void open(BuilderScreen screen, com.rtsbuilding.rtsbuilding.network.resume.S2CResumeScanPayload p) {
        ResumeWorkflowPanel panel = PANELS.get(p.entryId());
        if (panel == null || panel.getScreen() != screen) {
            if (panel != null) {
                PANELS.remove(p.entryId());
            }
            panel = new ResumeWorkflowPanel();
            panel.init(screen);
            screen.getFloatingWindowLayer().frontToBackWindows().add(panel);
            PANELS.put(p.entryId(), panel);
        }
        panel.entryId = p.entryId();
        panel.blueprint = p.blueprint();
        panel.matIds = p.materialIds();
        panel.matLabels = p.materialLabels();
        panel.matReq = p.materialRequired();
        panel.matAvail = p.materialAvailable();
        panel.itemLabel = p.itemLabel();
        panel.itemAvail = p.materialAvailable().isEmpty() ? 0 : p.materialAvailable().get(0);
        panel.neededItems = p.neededItems();
        panel.missingItems = p.missingItems();
        panel.conflictCount = p.conflictCount();
        panel.setOpen(true);
        panel.computeDefaultPosition();
        screen.getFloatingWindowLayer().markSortDirty();
    }

    /** 关闭指定工作流的恢复面板（幂等）。 */
    public static void closePanel(int entryId) {
        ResumeWorkflowPanel panel = PANELS.remove(entryId);
        if (panel != null) {
            panel.setOpen(false);
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = bounds.getX() + PAD;
        int y = bounds.getY() + PAD;
        int w = getWindowWidth() - PAD * 2;
        int textColor = ThemeManager.getTextColor();
        Minecraft mc = Minecraft.getInstance();

        // 标题（面板标题栏下方）
        TextRenderer.draw(g, t("screen.rtsbuilding.resume.title"), x, y, ThemeManager.getHoverTextColor());
        int cy = y + 16;

        // 统计行：剩余 / 冲突 / 缺口
        StringBuilder stat = new StringBuilder(t("screen.rtsbuilding.resume.remaining", neededItems));
        if (conflictCount > 0) {
            stat.append(t("screen.rtsbuilding.resume.conflict", conflictCount));
        }
        int statColor = textColor;
        if (missingItems > 0) {
            stat.append(t("screen.rtsbuilding.resume.missing", missingItems));
            statColor = COLOR_MISSING;
        } else if (conflictCount > 0) {
            statColor = COLOR_CONFLICT;
        }
        TextRenderer.draw(g, stat.toString(), x, cy, statColor);
        cy += STAT_GAP;

        // 分隔线
        SdfRenderer.drawPill(g, x, cy, w, 1, 0x33888888);
        cy += 6;

        // 材料清单（滚动区）：清单区 = 分隔线下方 ～ 按钮区上方，右侧预留滚动条。
        // 可见行数由面板高度动态计算，超出部分经滚动条逐行滚动查看。
        int btnY = bounds.getY() + getWindowHeight() - BTN_H - PAD;
        int listTop = cy;
        int listHeight = Math.max(ROW_H, btnY - LIST_BOTTOM_GAP - listTop);
        int visibleRows = matLabels.isEmpty() ? 0 : Math.max(1, listHeight / ROW_H);
        int rowW = w - SCROLL_BAR_W - 4;
        scrollBarX = x + w - SCROLL_BAR_W;
        scrollBarY = listTop + 2;
        scrollBarLen = Math.max(1, listHeight - 4);
        listRect[0] = x;
        listRect[1] = listTop;
        listRect[2] = w;
        listRect[3] = listHeight;

        scrollBar.setContent(Math.max(1, matLabels.size()), Math.max(1, visibleRows));
        int scroll = scrollBar.getScroll();
        for (int i = 0; i < visibleRows; i++) {
            int idx = scroll + i;
            if (idx >= matLabels.size()) break;
            renderMaterialRow(g, x, listTop + i * ROW_H, rowW,
                    matIds.get(idx), matLabels.get(idx), matReq.get(idx), matAvail.get(idx));
        }
        if (matLabels.isEmpty()) {
            TextRenderer.draw(g, t("screen.rtsbuilding.resume.no_materials"), x, listTop, UiPalette.border());
        }
        if (scrollBar.isVisible()) {
            scrollBar.render(g, scrollBarX, scrollBarY, scrollBarLen);
        }

        // 按钮区：FlexLayout 居中（冲突时两个按钮，否则一个）
        boolean enough = missingItems <= 0;
        boolean conflict = conflictCount > 0;
        int gap = 8;
        List<UiBox> btnBoxes = conflict
                ? List.of(UiBox.fixed(BTN_W, BTN_H), UiBox.fixed(BTN_W, BTN_H))
                : List.of(UiBox.fixed(BTN_W, BTN_H));
        List<UiRect> btnRects = FlexLayout.layout(FlexLayout.Direction.ROW, FlexLayout.Justify.CENTER,
                FlexLayout.Align.CENTER, gap, bounds.getX(), btnY, getWindowWidth(), BTN_H, btnBoxes);

        if (conflict) {
            renderButton(g, btnRects.get(0).x(), btnY, t("screen.rtsbuilding.resume.skip"), !enough, skipRect, mouseX, mouseY);
            renderButton(g, btnRects.get(1).x(), btnY, t("screen.rtsbuilding.resume.overwrite"), !enough, overwriteRect, mouseX, mouseY);
        } else {
            renderButton(g, btnRects.get(0).x(), btnY, t("screen.rtsbuilding.resume.start"), !enough, startRect, mouseX, mouseY);
        }
    }

    /** lang 查询辅助（带参数）。 */
    private static String t(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /** 统计行与分隔线间距。 */
    private static final int STAT_GAP = 21;

    /** 渲染单行材料：物品图标 + 进度条（条内直接叠加「名称 + 有/缺」文字）。 */
    private void renderMaterialRow(GuiGraphics g, int x, int y, int rowW,
                                   String itemId, String label, int req, long avail) {
        Minecraft mc = Minecraft.getInstance();

        // 行内排布：[图标 fixed, 进度条区 fill]（FlexLayout，渲染与进度条定位一致）
        List<UiRect> rowRects = FlexLayout.layout(FlexLayout.Direction.ROW, FlexLayout.Justify.START,
                FlexLayout.Align.CENTER, MAT_ICON_GAP, x, y, rowW, ROW_H,
                List.of(UiBox.fixed(MAT_ICON_SIZE, MAT_ICON_SIZE), UiBox.fill(1f)));
        UiRect iconRect = rowRects.get(0);
        UiRect barRect = rowRects.get(1);

        // 左侧绘制材料物品图标（统一走 GuiItemRenderer，防止穿透面板）
        ItemStack stack = GuiItemRenderer.resolveItemStack(itemId);
        if (!stack.isEmpty()) {
            GuiItemRenderer.drawItem(g, stack, iconRect.x(), iconRect.y());
        }

        int barX = barRect.x();
        int barW = barRect.w();
        int barY = y + (ROW_H - MAT_BAR_H) / 2;
        boolean creative = mc.player != null && mc.player.isCreative();

        // 进度条填充比例 = 拥有 / 需求（创造模式恒满格）
        float ratio = creative ? 1f : (req <= 0 ? 0f : Math.max(0f, Math.min(1f, (float) avail / req)));
        boolean enough = creative || avail >= req;
        int fillStart = enough ? PROGRESS_OK_START : PROGRESS_MISSING_START;
        int fillEnd = enough ? PROGRESS_OK_END : PROGRESS_MISSING_END;
        SdfRenderer.drawProgressBar(g, barX, barY, barW, MAT_BAR_H, ratio,
                UiPalette.accent(), fillStart, fillEnd, UiPalette.hoverBorder());

        // 进度条内文字（垂直居中）：左侧名称，右侧「有 M / 需 R」，不足时左侧再追加「缺 X」
        int textCenterY = barY + (MAT_BAR_H - mc.font.lineHeight) / 2 + 1;
        String numText = t("screen.rtsbuilding.resume.material_avail_req",
                creative ? "∞" : avail, req);
        int nw = mc.font.width(numText);

        // 不足时右侧整体 = 「缺 X」 + 「有 M / 需 R」，红色突出
        String missText = enough ? "" : t("screen.rtsbuilding.resume.missing_item", req - avail);
        int missW = enough ? 0 : mc.font.width(missText);
        int rightW = nw + (missW > 0 ? missW + 4 : 0);
        int rightX = barX + barW - rightW - 2;

        TextRenderer.draw(g, numText, barX + barW - nw - 2, textCenterY,
                enough ? COLOR_OK : COLOR_MISSING);
        if (!enough) {
            TextRenderer.draw(g, missText, rightX, textCenterY, COLOR_MISSING);
        }

        int nameMaxW = barW - rightW - 8;
        TextRenderer.draw(g, TextRenderer.trimToWidth(mc.font, label, Math.max(8, nameMaxW)),
                barX + 4, textCenterY, BAR_TEXT_COLOR);
    }

    private void renderButton(GuiGraphics g, int x, int y, String label, boolean disabled,
                              int[] rect, int mouseX, int mouseY) {
        boolean hovered = !disabled && mouseX >= x && mouseX < x + BTN_W && mouseY >= y && mouseY < y + BTN_H;
        int fill = disabled ? 0x55333333 : hovered ? UiPalette.accent() : 0x882E3B4C;
        SdfRenderer.drawBorderedRoundedRect(g, x, y, BTN_W, BTN_H, 5, UiPalette.border(), fill, 1);
        int labelW = Minecraft.getInstance().font.width(label);
        int textColor = disabled ? UiPalette.border()
                : hovered ? ThemeManager.getHoverTextColor() : ThemeManager.getTextColor();
        TextRenderer.draw(g, label, x + (BTN_W - labelW) / 2,
                y + (BTN_H - Minecraft.getInstance().font.lineHeight) / 2, textColor);
        rect[0] = x;
        rect[1] = y;
        rect[2] = BTN_W;
        rect[3] = BTN_H;
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        // 滚动条优先：点击轨道/滑块（材料较多时）进入拖拽或翻页，不再处理按钮
        if (scrollBar.handleClick(mouseX, mouseY, scrollBarX, scrollBarY, scrollBarLen)) {
            return;
        }
        // 材料不足时所有恢复按钮（开始/跳过/覆盖）均不可点击
        if (missingItems > 0) return;
        boolean conflict = conflictCount > 0;
        if (conflict) {
            if (hit(skipRect, mouseX, mouseY)) {
                RtsClientPacketGateway.sendResumeAction(entryId, (byte) 1);
                setOpen(false);
            } else if (hit(overwriteRect, mouseX, mouseY)) {
                RtsClientPacketGateway.sendResumeAction(entryId, (byte) 2);
                setOpen(false);
            }
        } else if (hit(startRect, mouseX, mouseY)) {
            RtsClientPacketGateway.sendResumeAction(entryId, (byte) 0);
            setOpen(false);
        }
    }

    private static boolean hit(int[] r, double mx, double my) {
        return r[2] > 0 && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    /**
     * 滚轮：鼠标悬浮于材料清单区时滚动清单（材料较多时），否则交由面板默认行为（吞掉滚轮）。
     */
    @Override
    protected boolean handleContentScroll(double mx, double my, double sx, double sy) {
        if (scrollBar.isVisible()
                && mx >= listRect[0] && mx < listRect[0] + listRect[2]
                && my >= listRect[1] && my < listRect[1] + listRect[3]) {
            scrollBar.handleScroll(sy);
        }
        return true;
    }

    /**
     * 拖拽：滚动条滑块拖动优先于面板拖动/缩放（滑块仅在清单区，面板拖动只发生在标题栏）。
     */
    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 0 && scrollBar.isDragging() && scrollBarLen > 0) {
            scrollBar.handleDrag(my, scrollBarY, scrollBarLen);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0) {
            scrollBar.endDrag();
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    protected void onClose() {
        super.onClose();
        // 关闭本工作流面板：移除其线框预览并释放对应实例
        PANELS.remove(this.entryId);
        com.rtsbuilding.rtsbuilding.client.presentation.plugin.resume.ResumeWorkflowState.remove(this.entryId);
    }

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.resume.title");
    }

    @Override
    protected int getDefaultWidth() {
        return PANEL_W;
    }

    @Override
    protected int getDefaultHeight() {
        return PANEL_H;
    }

    @Override
    protected void computeDefaultPosition() {
        if (screen == null) return;
        // 统一基准：水平居中 + 垂直居中，顶部避开顶栏（TOP_H+6）、底部留 8px 边距
        positionCentered(TOP_H + 6, 8);
    }
}
