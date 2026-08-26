package com.rtsbuilding.rtsbuilding.client.presentation.panel.powergrid;

import com.rtsbuilding.rtsbuilding.api.powergrid.ExternalMachineConfig;
import com.rtsbuilding.rtsbuilding.api.powergrid.GridMember;
import com.rtsbuilding.rtsbuilding.api.powergrid.PowerDevice;
import com.rtsbuilding.rtsbuilding.api.powergrid.PowerGridSnapshot;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsAccessLevel;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsDeviceRole;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsPowerGrid;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsPowerStatus;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.render.GuiItemRenderer;
import com.rtsbuilding.uifw.theme.ThemeManager;
import com.rtsbuilding.uifw.window.window.UiPanel;
import com.rtsbuilding.uifw.window.component.CollapsibleSection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreenConstants.TOP_H;

/**
 * 电网多人管理面板（uifw 浮动窗口，RTS 模式内按 K 开关）——参照 Flux-Networks 布局重构。
 * <p>
 * 布局：顶部<b>电网信息区</b>（总发电/总耗电 + 自己权限色 + 操作结果）→ <b>横向 Tab</b>（成员 /
 * 设备 / 外部机器）→ 内容区<b>分页网格</b>（每页若干行，底部翻页导航）。
 * 成员 Tab 等同 Flux-Networks：列表中除组成员外还会显示<b>在线未加入的「陌生人」行
 * （{@link RtsAccessLevel#BLOCKED}）</b>，点击任意行打开 {@link PopupMemberEdit}，对陌生人
 * 点「设为用户」即完成邀请——无需输入玩家名。
 */
public final class PowerGridManagerPanel extends UiPanel {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 252;
    private static final int ROW_H = 17;
    private static final int TAB_H = 18;
    private static final int NAV_H = 14;
    /** 内容区左右内边距（对齐面板边框 4px）。 */
    private static final int CONTENT_PAD = 4;

    private static final int COLOR_OK = 0xFF66BB6A;
    private static final int COLOR_OFFLINE = 0xFF888888;
    private static final int COLOR_WARN = 0xFFFFB74D;
    private static final int COLOR_ERR = 0xFFFF5252;
    private static final int COLOR_OWNER = 0xFFFFAA00;
    private static final int COLOR_ADMIN = 0xFF66CC00;
    private static final int COLOR_USER = 0xFF6699FF;
    private static final int COLOR_BLOCKED = 0xFFA9A9A9; // 陌生人灰（参照 Flux AccessLevel.BLOCKED）
    private static final int COLOR_TOWER = 0xFF4FC3F7;   // 输电蓝
    private static final int COLOR_GEN = 0xFF66BB6A;     // 发电绿

    private static final int TAB_MEMBER = 0;
    private static final int TAB_DEVICE = 1;

    /** 单例（每 BuilderScreen 一个）。 */
    private static PowerGridManagerPanel PANEL;

    private int currentTab = TAB_MEMBER;
    private int page;

    /** 设备 Tab：按机器类型（itemId）缓存的折叠条（参照设置面板 CollapsibleSection；默认折叠显示聚合）。 */
    private final java.util.Map<String, CollapsibleSection> deviceSections = new java.util.LinkedHashMap<>();

    private final List<int[]> hitRects = new ArrayList<>();
    private final List<Integer> hitActions = new ArrayList<>();
    private final List<Object> hitArg = new ArrayList<>();
    private final List<int[]> tabRects = new ArrayList<>();
    private final List<Integer> tabIndex = new ArrayList<>();

    private PowerGridManagerPanel() {
        this.draggable = true;
        this.closable = true;
    }

    /** 打开/关闭电网管理面板（全局 K 键调用）。 */
    public static void toggle(BuilderScreen screen) {
        PowerGridManagerPanel panel = PANEL;
        if (panel == null || panel.getScreen() != screen) {
            if (panel != null && panel.getScreen() != screen) {
                panel.setOpen(false);
            }
            panel = new PowerGridManagerPanel();
            panel.init(screen);
            screen.getFloatingWindowLayer().frontToBackWindows().add(panel);
            PANEL = panel;
        }
        if (!panel.isOpen()) {
            panel.setOpen(true);
            panel.computeDefaultPosition();
            RtsPowerGrid.get().requestRefresh();
        } else {
            panel.setOpen(false);
        }
        screen.getFloatingWindowLayer().markSortDirty();
    }

    /** 全局 K 键处理：命中电网管理按键时切换面板（由 BuilderScreen.keyPressed 调用）。 */
    public static boolean handleGlobalKey(int keyCode, int scanCode, BuilderScreen screen) {
        if (keyCode == RtsKeyMappings.POWER_GRID_MENU_KEY.getKey().getValue()) {
            toggle(screen);
            return true;
        }
        return false;
    }

    /** 当前玩家在电网组中的访问等级（供 PopupMemberEdit 权限判断）。 */
    public RtsAccessLevel myAccess() {
        PowerGridSnapshot snap = RtsPowerGrid.get() == null ? null : RtsPowerGrid.get().currentGrid();
        if (snap == null || localUuid() == null) {
            return RtsAccessLevel.USER;
        }
        for (GridMember m : snap.members()) {
            if (localUuid().equals(m.id())) {
                return m.access() == null ? RtsAccessLevel.USER : m.access();
            }
        }
        return RtsAccessLevel.USER;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hitRects.clear();
        hitActions.clear();
        hitArg.clear();
        tabRects.clear();
        tabIndex.clear();

        int x = contentX() + CONTENT_PAD;
        int w = contentWidth() - CONTENT_PAD * 2;
        int cTop = contentY();
        int cBottom = cTop + contentHeight();
        int cy = cTop + 2;

        PowerGridSnapshot snap = RtsPowerGrid.get() == null ? null : RtsPowerGrid.get().currentGrid();
        if (snap == null) {
            TextRenderer.draw(g, t("screen.rtsbuilding.powergrid.no_grid"), x, cy, UiPalette.border());
            return;
        }

        // 信息区（总发电靠左绿色 / 总耗电靠右 +4px 靠右对齐；下一行 result 提示；固定 30 高避免 tab 跳动）
        TextRenderer.draw(g, t("screen.rtsbuilding.powergrid.gen", snap.totalGeneration()), x, cy, COLOR_GEN);
        String demandText = t("screen.rtsbuilding.powergrid.demand", snap.totalDemand());
        TextRenderer.draw(g, demandText, x + w - 4 - Minecraft.getInstance().font.width(demandText), cy,
                ThemeManager.getTextColor());
        int lastResult = RtsPowerGrid.get().lastActionResult();
        if (lastResult != 0) {
            TextRenderer.draw(g, t("screen.rtsbuilding.powergrid.result_" + lastResult), x, cy + 11,
                    lastResult == 3 ? COLOR_WARN : COLOR_ERR);
        }

        // Tab 栏（固定于信息区下）
        int tabY = cTop + 30;
        renderTabBar(g, x, tabY, w, snap, mouseX, mouseY);
        int tabBottom = tabY + TAB_H;
        int gridTop = tabBottom + 4;

        if (currentTab == TAB_MEMBER) {
            // 成员 Tab：分页（成员 + 可邀请的在线陌生人一起列出）+ 底部翻页导航。
            int navY = cBottom - NAV_H - 2;
            int gridBottom = navY - 4;
            int gridHeight = Math.max(ROW_H, gridBottom - gridTop);
            int perPage = Math.max(1, (gridHeight - ROW_H) / ROW_H);  // 标题占一行，条目从下一行开始
            renderMembers(g, x, w, gridTop, gridHeight, perPage, snap, mouseX, mouseY);
            renderNav(g, x, w, navY, perPage);
        } else {
            // 设备 Tab：参照设置面板折叠条——按机器类型纵向堆叠折叠条（不分页、无翻页/操作行）。
            renderDeviceFolders(g, x, w, gridTop, cBottom - 2, snap, mouseX, mouseY);
        }
    }

    private void renderTabBar(GuiGraphics g, int x, int y, int w, PowerGridSnapshot snap, int mouseX, int mouseY) {
        int tabW = (w - 2) / 2;
        String[] labels = {t("screen.rtsbuilding.powergrid.tab_member"),
                t("screen.rtsbuilding.powergrid.tab_device")};
        for (int i = 0; i < 2; i++) {
            int tx = x + i * (tabW + 2);
            boolean active = i == currentTab;
            boolean hovered = !active && mouseX >= tx && mouseX < tx + tabW && mouseY >= y && mouseY < y + TAB_H;
            int fill = active ? UiPalette.accent() : hovered ? 0x99364A5E : 0x882E3B4C;
            SdfRenderer.drawBorderedRoundedRect(g, tx, y, tabW, TAB_H, 4, UiPalette.border(), fill, 1);
            int lw = Minecraft.getInstance().font.width(labels[i]);
            TextRenderer.draw(g, labels[i], tx + (tabW - lw) / 2, y + (TAB_H - Minecraft.getInstance().font.lineHeight) / 2 + 1,
                    active ? ThemeManager.getHoverTextColor() : ThemeManager.getTextColor());
            tabRects.add(new int[]{tx, y, tabW, TAB_H});
            tabIndex.add(i);
        }
    }

    // ---- 成员 Tab ----

    private void renderMembers(GuiGraphics g, int x, int w, int gridTop, int gridHeight, int perPage,
                               PowerGridSnapshot snap, int mouseX, int mouseY) {
        List<GridMember> list = snap.members();
        int total = Math.max(1, list.size());
        page = Math.min(page, (total - 1) / perPage);
        int start = page * perPage;
        // 标题显示「成员 N · 陌生人 M」（参照 Flux-Networks：陌生人=在线未加入，可点击邀请）。
        long realCount = list.stream().filter(m -> m.access() != RtsAccessLevel.BLOCKED).count();
        TextRenderer.draw(g, t("screen.rtsbuilding.powergrid.member_count", realCount, list.size() - realCount),
                x, rowTextY(gridTop), ThemeManager.getHoverTextColor());
        for (int i = 0; i < perPage; i++) {
            int idx = start + i;
            if (idx >= list.size()) {
                break;
            }
            int y = gridTop + ROW_H + i * ROW_H;
            renderMemberRow(g, x, w, y, list.get(idx), mouseX, mouseY);
        }
    }

    private void renderMemberRow(GuiGraphics g, int x, int w, int y, GridMember m, int mouseX, int mouseY) {
        RtsAccessLevel access = m.access() == null ? RtsAccessLevel.USER : m.access();
        int accColor = accessColor(access);
        int textY = rowTextY(y);
        // 权限色条 + 在线点：色条与整行文字垂直居中对齐（以文字中线为基准）
        int barH = ROW_H - 4;
        SdfRenderer.drawPill(g, x, textY + (Minecraft.getInstance().font.lineHeight - barH) / 2, 2, barH, accColor);
        TextRenderer.draw(g, m.online() ? "●" : "○", x + 6, textY, m.online() ? COLOR_OK : COLOR_OFFLINE);
        boolean isMe = localUuid() != null && localUuid().equals(m.id());
        String name = m.name();
        int nameMax = w - 8 - 4 - 60;
        TextRenderer.draw(g, TextRenderer.trimToWidth(Minecraft.getInstance().font, name, Math.max(16, nameMax)),
                x + 16, textY, isMe ? ThemeManager.getHoverTextColor() : ThemeManager.getTextColor());
        // 右侧权限名
        String an = accessName(access);
        TextRenderer.draw(g, an, x + w - Minecraft.getInstance().font.width(an), textY, accColor);
        // 点击行 → 弹窗编辑
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H) {
            SdfRenderer.drawPill(g, x, y + ROW_H, w, 1, 0x66FFFFFF);
            hitRects.add(new int[]{x, y, w, ROW_H});
            hitActions.add(12);
            hitArg.add(m);
        }
    }

    // ---- 设备 Tab ----

    /** 设备 Tab（参照设置面板折叠条）：按机器类型纵向堆叠折叠条，不翻页。
     * <p>每组一个 {@link CollapsibleSection}：圆角背景 + chevron 旋转动画 + hover；组头标题为
     * 「机器类型名 ×N」、右侧显示该组聚合（发电/输电/用电 X/tick）。点击组头折叠/展开，展开时
     * 按折叠动画进度在组头下摊开显示单台设备行（scissor 裁剪）。同一机器类型只有 1 台时
     * <b>不堆叠</b>，直接显示单台设备行。</p>
     */
    private void renderDeviceFolders(GuiGraphics g, int x, int w, int gridTop, int gridBottom,
                                     PowerGridSnapshot snap, int mouseX, int mouseY) {
        // 按机器类型（itemId；缺省回落 label 翻译键）分组，保持出现顺序。
        java.util.LinkedHashMap<String, java.util.List<PowerDevice>> byType = new java.util.LinkedHashMap<>();
        for (PowerDevice d : snap.devices()) {
            String key = (d.itemId() == null || d.itemId().isEmpty())
                    ? (d.label() == null ? "" : d.label()) : d.itemId();
            byType.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }
        int y = gridTop + 2;
        for (var e : byType.entrySet()) {
            java.util.List<PowerDevice> group = e.getValue();
            if (group.size() == 1) {
                // 只有一台：不堆叠，直接显示单台设备行。
                if (y + ROW_H <= gridBottom) {
                    renderDeviceRow(g, x, w, y, group.get(0), mouseX, mouseY);
                    y += ROW_H + 2;
                }
                continue;
            }
            // 多台：折叠条（参照设置面板折叠条）。
            CollapsibleSection section = deviceSections.computeIfAbsent(e.getKey(),
                    k -> new CollapsibleSection(""));
            long sum = sumMetric(group);
            RtsDeviceRole role = group.get(0).role();
            int contentH = group.size() * ROW_H;
            // 折叠条组头：标题为空交给 drawHeader（只画背景/chevron/hover/动画），标题由下方自绘。
            section.setTitleText("");
            section.drawHeader(g, mouseX, mouseY, x, y, w, contentH);
            // 组头左侧：物品图标（组内第一台设备）固定在「图标列 x+22」；
            // 标题（机器名 ×N）固定在「文字列 x+42」——与单台设备行名称同列（X 轴对齐）。
            ItemStack headStack = (group.get(0).itemId() == null || group.get(0).itemId().isEmpty())
                    ? ItemStack.EMPTY : GuiItemRenderer.resolveItemStack(group.get(0).itemId());
            if (!headStack.isEmpty()) {
                GuiItemRenderer.drawItem(g, headStack, x + 22, y + (CollapsibleSection.headerHeight() - 16) / 2);
            }
            String machineName = group.get(0).label() == null || group.get(0).label().isEmpty()
                    ? e.getKey() : Component.translatable(group.get(0).label()).getString();
            String headTitle = machineName + " \u00D7" + group.size();
            int headTitleX = x + 42;                          // 文字 X 轴统一列
            // 标题空间：右侧为聚合预留 66px。
            int titleMax = w - 42 - 66 - 12;
            TextRenderer.draw(g, TextRenderer.trimToWidth(Minecraft.getInstance().font, headTitle,
                            Math.max(20, titleMax)),
                    headTitleX, y + 7, ThemeManager.getTextColor());
            // 右侧聚合：发电/输电/用电 X/tick（对齐设置面板折叠条右侧）。
            String agg = switch (role) {
                case GENERATOR -> "发电 " + sum + "/tick";
                case TOWER -> "输电 " + sum + "/tick";
                default -> "用电 " + sum + "/tick";
            };
            int groupColor = switch (role) {
                case GENERATOR -> COLOR_GEN;
                case TOWER -> COLOR_TOWER;
                default -> COLOR_WARN;
            };
            TextRenderer.draw(g, agg, x + w - 4 - Minecraft.getInstance().font.width(agg), y + 7, groupColor);
            // 组头命中：折叠/展开折叠条。
            if (section.isHeaderClicked(mouseX, mouseY, x, y, w)) {
                hitRects.add(new int[]{x, y, w, CollapsibleSection.headerHeight()});
                hitActions.add(13);
                hitArg.add(e.getKey());
            }
            y += CollapsibleSection.headerHeight();
            // 展开：与设置面板折叠条一致——<b>全部设备行都渲染</b>，由 scissor 裁剪显示到内容动画高度
            // 对应进度区域（未到达的部分被裁剪而非移除渲染，避免展开动画期间条目被省略）。
            if (section.isExpanded()) {
                int animH = (int) (contentH * section.getContentProgress());
                if (animH > 0) {
                    g.flush();
                    if (this.screen != null) {
                        this.screen.enableUiScissor(g, x, y, x + w, y + animH);
                    } else {
                        g.enableScissor(x, y, x + w, y + animH);
                    }
                    int dy = y;
                    for (PowerDevice d : group) {
                        // 所有行都绘制（位于裁剪区外部分不可见），与 SettingsSection 行为一致。
                        renderDeviceRow(g, x, w, dy, d, mouseX, mouseY);
                        dy += ROW_H;
                    }
                    g.flush();
                    g.disableScissor();
                }
            }
            // 折叠条当前动画状态下的占位高度：收回/展开动画期间下方条目跟随进度同步移动
            //（而非瞬间上移/下移），避免与正在缩小/扩展的背景重叠。
            y += (int) (contentH * section.getContentProgress());
        }
    }

    /** 该组设备 metric 合计（折叠聚合用）。 */
    private static long sumMetric(java.util.List<PowerDevice> group) {
        long sum = 0L;
        for (PowerDevice d : group) {
            sum = safeAdd(sum, d.metric());
        }
        return sum;
    }

    /** 饱和加法（分组聚合用）。 */
    private static long safeAdd(long a, long b) {
        long sum = a + b;
        return sum < a ? Long.MAX_VALUE : sum;
    }

    private void renderDeviceRow(GuiGraphics g, int x, int w, int y, PowerDevice d, int mouseX, int mouseY) {
        int textY = rowTextY(y);
        RtsDeviceRole role = d.role();
        // 物品图标固定「图标列 x+22」、名称固定「文字列 x+42」——与上方折叠条组头同列（X 轴对齐）。
        final int iconX = x + 22;
        final int textX = x + 42;
        ItemStack stack = (d.itemId() == null || d.itemId().isEmpty())
                ? ItemStack.EMPTY : GuiItemRenderer.resolveItemStack(d.itemId());
        if (!stack.isEmpty()) {
            GuiItemRenderer.drawItem(g, stack, iconX, y + (ROW_H - 16) / 2);
        }
        // 物品名称（翻译键 → 实际名字）
        String name = d.label() == null || d.label().isEmpty()
                ? "" : Component.translatable(d.label()).getString();
        // 量值（右对齐）：（发电/输电/用电）X/tick —— 发电绿 / 输电蓝 / 用电橙
        String metric = switch (role) {
            case GENERATOR -> "发电 " + d.metric() + "/tick";
            case TOWER -> "输电 " + d.metric() + "/tick";
            case CONSUMER -> "用电 " + d.metric() + "/tick";
        };
        int metricColor = switch (role) {
            case GENERATOR -> COLOR_GEN;
            case TOWER -> COLOR_TOWER;
            default -> COLOR_WARN;   // 用电橙
        };
        int metricLeft = x + w - 4 - Minecraft.getInstance().font.width(metric);
        TextRenderer.draw(g, metric, metricLeft, textY, metricColor);
        int nameMax = metricLeft - textX - 6;
        TextRenderer.draw(g, TextRenderer.trimToWidth(Minecraft.getInstance().font, name, Math.max(8, nameMax)),
                textX, textY, ThemeManager.getTextColor());
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H) {
            SdfRenderer.drawPill(g, x, y + ROW_H, w, 1, 0x66FFFFFF);
            hitRects.add(new int[]{x, y, w, ROW_H});
            hitActions.add(0);
            hitArg.add(new long[]{d.x(), d.y(), d.z()});
        }
    }

    // ---- 外部机器 Tab ----

    /** 行内文字垂直居中 y。 */
    private int rowTextY(int y) {
        return y + (ROW_H - Minecraft.getInstance().font.lineHeight) / 2 + 1;
    }

    // ---- 翻页导航 + 操作行 ----

    private void renderNav(GuiGraphics g, int x, int w, int y, int perPage) {
        renderButton(g, x, y, "◀", 18, NAV_H, 0, 0);
        hitRects.add(new int[]{x, y, 18, NAV_H});
        hitActions.add(10);
        hitArg.add(null);
        String ptext = (page + 1) + "/" + Math.max(1, pageCount(perPage));
        TextRenderer.draw(g, ptext, x + w / 2 - Minecraft.getInstance().font.width(ptext) / 2, y,
                ThemeManager.getTextColor());
        renderButton(g, x + w - 18, y, "▶", 18, NAV_H, 0, 0);
        hitRects.add(new int[]{x + w - 18, y, 18, NAV_H});
        hitActions.add(11);
        hitArg.add(null);
    }

    private int pageCount(int perPage) {
        PowerGridSnapshot snap = RtsPowerGrid.get() == null ? null : RtsPowerGrid.get().currentGrid();
        // 仅成员 Tab 分页；设备 Tab 为折叠条纵向堆叠、不翻页（default 保险返回 1）。
        int n = snap == null ? 0 : snap.members().size();
        if (currentTab != TAB_MEMBER) {
            n = 0;
        }
        return Math.max(1, (int) Math.ceil(n / (double) perPage));
    }

    private void renderButton(GuiGraphics g, int x, int y, String label, int w, int h, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int fill = hovered ? UiPalette.accent() : 0x882E3B4C;
        SdfRenderer.drawBorderedRoundedRect(g, x, y, w, h, 3, UiPalette.border(), fill, 1);
        int lw = Minecraft.getInstance().font.width(label);
        TextRenderer.draw(g, label, x + (w - lw) / 2, y + (h - Minecraft.getInstance().font.lineHeight) / 2 + 1,
                hovered ? ThemeManager.getHoverTextColor() : ThemeManager.getTextColor());
    }

    // ---- 交互 ----

    @Override
    protected void handleContentClick(double mx, double my, int button) {
        if (button != 0) {
            return;
        }
        // Tab 切换
        for (int i = 0; i < tabIndex.size(); i++) {
            int[] r = tabRects.get(i);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                if (currentTab != tabIndex.get(i)) {
                    currentTab = tabIndex.get(i);
                    page = 0;
                }
                return;
            }
        }
        // 内容区命中
        for (int i = 0; i < hitActions.size(); i++) {
            int[] r = hitRects.get(i);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                onRowAction(hitActions.get(i), hitArg.get(i));
                return;
            }
        }
    }

    private void onRowAction(int action, Object arg) {
        switch (action) {
            case 0 -> {                 // 定位设备
                if (arg instanceof long[] p) {
                    RtsPowerGrid.get().locateDevice(p[0], p[1], p[2]);
                }
            }
            case 10 -> page = Math.max(0, page - 1);
            case 11 -> page++;
            case 12 -> {                // 打开成员编辑弹窗（独立浮窗）
                if (arg instanceof GridMember m) {
                    PopupMemberEdit popup = new PopupMemberEdit(this, m);
                    popup.init(this.screen);
                    this.screen.getFloatingWindowLayer().frontToBackWindows().add(popup);
                    popup.computeDefaultPosition();
                    popup.setOpen(true);
                    this.screen.getFloatingWindowLayer().markSortDirty();
                }
            }
            case 13 -> {                // 设备分组（机器类型）：折叠条展开/折叠
                if (arg instanceof String itemId && !itemId.isEmpty()) {
                    CollapsibleSection section = deviceSections.get(itemId);
                    if (section != null) {
                        section.toggle();       // 参照设置面板折叠条动画
                    }
                }
            }
            default -> { }
        }
    }

    private UUID localUuid() {
        return Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
    }

    // ---- 布局参数 ----

    static String t(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static int accessColor(RtsAccessLevel a) {
        return switch (a) {
            case OWNER -> COLOR_OWNER;
            case ADMIN -> COLOR_ADMIN;
            case BLOCKED -> COLOR_BLOCKED;
            default -> COLOR_USER;
        };
    }

    private static String accessName(RtsAccessLevel a) {
        return switch (a) {
            case OWNER -> t("screen.rtsbuilding.powergrid.access_owner");
            case ADMIN -> t("screen.rtsbuilding.powergrid.access_admin");
            case BLOCKED -> t("screen.rtsbuilding.powergrid.access_blocked");
            default -> t("screen.rtsbuilding.powergrid.access_user");
        };
    }

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.powergrid.title");
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
        if (screen == null) {
            return;
        }
        positionCentered(TOP_H + 6, 8);
    }
}
