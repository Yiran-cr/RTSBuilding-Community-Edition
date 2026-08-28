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
import com.rtsbuilding.uifw.animate.AnimFloat;
import com.rtsbuilding.uifw.animate.ColorAnimation;
import com.rtsbuilding.uifw.animate.Easing;
import com.rtsbuilding.rtsbuilding.util.RtsPinyinSearch;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.render.GuiItemRenderer;
import com.rtsbuilding.uifw.theme.ThemeManager;
import com.rtsbuilding.uifw.window.window.UiPanel;
import com.rtsbuilding.uifw.window.component.CollapsibleSection;
import com.rtsbuilding.uifw.window.component.ScrollBar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    /** 切到「电网总览」仪表盘 Tab 时的面板高度（嵌入饼图+柱状图）。 */
    private static final int PANEL_H_OVERVIEW = 380;
    private static final int ROW_H = 17;
    private static final int TAB_H = 18;
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
    /** 仪表盘饼图/柱状图统一配色：发电蓝 / 耗电橙（参照需求文档配色规范 #1890FF / #FA8C16）。 */
    private static final int COLOR_OVERVIEW_GEN = 0xFF1890FF;
    private static final int COLOR_OVERVIEW_DEMAND = 0xFFFA8C16;

    // ── 搜索框常量（参照 BlueprintLibraryPanel 搜索框实现） ──────────
    /** 搜索框高度。 */
    private static final int SEARCH_H = 18;
    /** 搜索框内容左右内边距。 */
    private static final int SEARCH_PAD = 4;
    /** 搜索框与列表之间的垂直间距。 */
    private static final int SEARCH_LIST_GAP = 6;
    /** 搜索框光标闪烁周期（毫秒）。 */
    private static final long CURSOR_BLINK_MS = 500;

    private static final int TAB_MEMBER = 0;
    private static final int TAB_DEVICE = 1;
    private static final int TAB_OVERVIEW = 2;

    // ── 设备筛选工具栏 / 显隐管理 常量 ──────────────────────────────
    /** 筛选/显隐 按钮单行高度。 */
    private static final int TOOLBAR_H = 16;
    /** 筛选栏与显隐栏之间的垂直间距。 */
    private static final int TOOLBAR_GAP = 3;
    /** 工具栏与下方搜索框之间的垂直间距。 */
    private static final int TOOLBAR_LIST_GAP = 6;
    /** 设备筛选维度：全部。 */
    private static final int FILTER_ALL = 0;
    /** 设备筛选维度：发电。 */
    private static final int FILTER_GEN = 1;
    /** 设备筛选维度：用电。 */
    private static final int FILTER_CONSUMER = 2;
    /** 设备显隐视图模式：全部显示（隐藏与未隐藏均显示）。 */
    private static final int VIS_ALL = 0;
    /** 设备显隐视图模式：仅显示已隐藏条目。 */
    private static final int VIS_HIDDEN_ONLY = 1;
    /** 设备显隐视图模式：仅显示未隐藏条目。 */
    private static final int VIS_VISIBLE_ONLY = 2;
    /** 设备筛选维度<b>轮换顺序</b>：全部 → 发电 → 用电 → 循环。 */
    private static final int[] FILTER_CYCLE = {FILTER_ALL, FILTER_GEN, FILTER_CONSUMER};
    /** 设备显隐模式<b>轮换顺序</b>：仅显示 → 全部 → 仅隐藏 → 循环。 */
    private static final int[] VIS_CYCLE = {VIS_VISIBLE_ONLY, VIS_ALL, VIS_HIDDEN_ONLY};
    /** 设备行内显隐开关按钮宽度。 */
    private static final int EYE_BTN_W = 20;
    /** 设备行内显隐开关按钮高度。 */
    private static final int EYE_BTN_H = 14;
    /** 折叠条组头「一键修改」按钮宽度（与单台设备行内的「◀用电/发电▶」切换按钮一致）。 */
    private static final int QUICK_TOGGLE_BTN_W = 40;

    /** 单例（每 BuilderScreen 一个）。 */
    private static PowerGridManagerPanel PANEL;

    private int currentTab = TAB_MEMBER;

    /** 成员 Tab 滚动条（替代翻页按钮）。 */
    private final ScrollBar memberScrollBar = new ScrollBar().withScrollStep(1).withScrollBottomPad(0);
    /** 设备 Tab 滚动条（设备较多时滚动查看）。 */
    private final ScrollBar deviceScrollBar = new ScrollBar().withScrollStep(1).withScrollBottomPad(0);

    /** 仪表盘柱状图当前选中的时间粒度（0=5秒, 1=1分钟, 2=1小时）。 */
    private int overviewGranularity = 0;
    /** 仪表盘饼图当前悬浮区域（0=无, 1=发电, 2=耗电），用于悬浮提示框渲染。 */
    private int overviewHoverSeg = 0;
    /** 上次自动请求历史时序的时间戳（毫秒），用于在 OVERVIEW Tab 内每 5 秒刷新一次柱状图。 */
    private long overviewLastHistoryRequestMs;
    /** 首次进入电网总览 Tab 标记，用于触发初始动画。 */
    private boolean overviewFirstEnter = true;

    // ---- 动画状态 ----

    /** 饼图发电扇区角度动画（EASE_OUT_BACK 200ms，数值变化时平滑过渡）。 */
    private final AnimFloat pieGenAnim = AnimFloat.slide();
    /** 饼图耗电扇区角度动画。 */
    private final AnimFloat pieDemAnim = AnimFloat.slide();
    /** 中心百分比数值动画（EASE_OUT_QUART 300ms）。 */
    private final AnimFloat pctAnim = AnimFloat.expand();
    /** 柱状图整体揭晓进度（0→1，每次数据更新时重播）。 */
    private final AnimFloat barRevealAnim = AnimFloat.expand();
    /** 粒度切换按钮淡入动画（首次进入或切换粒度时触发）。 */
    private final AnimFloat granuleFadeAnim = AnimFloat.fade();
    /** 粒度切换按钮的悬浮动画（每按钮一个，120ms SMOOTHSTEP）。 */
    private final List<AnimFloat> granHoverAnims = new ArrayList<>(3);
    /** Tab 栏按钮的悬浮动画（每按钮一个，120ms SMOOTHSTEP）。 */
    private final List<AnimFloat> tabHoverAnims = new ArrayList<>(3);
    /** 上一次柱状图的数据点数量，用于检测新增数据时触发揭晓动画。 */
    private int lastBarDataSize;

    /** 设备 Tab：按机器类型（itemId）缓存的折叠条（参照设置面板 CollapsibleSection；默认折叠显示聚合）。 */
    private final java.util.Map<String, CollapsibleSection> deviceSections = new java.util.LinkedHashMap<>();

    /** 成员行悬浮动画（每成员一个，按 UUID 索引）。 */
    private final java.util.Map<UUID, AnimFloat> memberRowHoverAnims = new java.util.HashMap<>();
    /** 设备行悬浮动画（每设备一个，按位置坐标字符串 "x_y_z" 索引）。 */
    private final java.util.Map<String, AnimFloat> deviceRowHoverAnims = new java.util.HashMap<>();

    // ── 成员搜索框状态（参照 BlueprintLibraryPanel 搜索框实现） ──────
    /** 成员搜索框是否聚焦。 */
    private boolean memberSearchFocused;
    /** 成员搜索输入缓冲。 */
    private final StringBuilder memberSearchBuffer = new StringBuilder();
    /** 成员搜索光标位置。 */
    private int memberSearchCursorPos;
    /** 成员搜索光标闪烁时间戳。 */
    private long memberSearchCursorBlink;
    /** 上次成员搜索框焦点状态（变化时驱动焦点动画）。 */
    private boolean prevMemberSearchFocused;
    /** 成员搜索框焦点高亮动画。 */
    private final AnimFloat memberSearchFocusAnim = AnimFloat.of(0f, 100L, Easing.EASE_OUT_QUAD);
    /** 成员搜索框悬停高亮动画。 */
    private final AnimFloat memberSearchHoverAnim = AnimFloat.hover();
    /** 过滤后的成员列表（按搜索缓冲实时过滤）。 */
    private List<GridMember> filteredMembers;

    // ── 设备搜索框状态（参照 BlueprintLibraryPanel 搜索框实现） ──────
    /** 设备搜索框是否聚焦。 */
    private boolean deviceSearchFocused;
    /** 设备搜索输入缓冲。 */
    private final StringBuilder deviceSearchBuffer = new StringBuilder();
    /** 设备搜索光标位置。 */
    private int deviceSearchCursorPos;
    /** 设备搜索光标闪烁时间戳。 */
    private long deviceSearchCursorBlink;
    /** 上次设备搜索框焦点状态（变化时驱动焦点动画）。 */
    private boolean prevDeviceSearchFocused;
    /** 设备搜索框焦点高亮动画。 */
    private final AnimFloat deviceSearchFocusAnim = AnimFloat.of(0f, 100L, Easing.EASE_OUT_QUAD);
    /** 设备搜索框悬停高亮动画。 */
    private final AnimFloat deviceSearchHoverAnim = AnimFloat.hover();
    /** 过滤后的设备列表（按搜索缓冲实时过滤）。 */
    private List<PowerDevice> filteredDevices;

    // ── 设备筛选 / 显隐管理 状态 ──────────────────────────────
    /** 当前设备筛选维度（FILTER_ALL / FILTER_GEN / FILTER_CONSUMER）。 */
    private int deviceFilter = FILTER_ALL;
    /** 当前设备显隐视图模式（VIS_ALL / VIS_HIDDEN_ONLY / VIS_VISIBLE_ONLY）。 */
    private int deviceVisibilityMode = VIS_VISIBLE_ONLY;
    /** 被用户隐藏的设备坐标 key（"x_y_z" → 隐藏）。显隐管理使用，仅客户端本地状态。 */
    private final java.util.Set<String> hiddenDeviceKeys = new java.util.HashSet<>();
    /** 设备筛选维度<b>轮换按钮</b>命中矩形（null=未渲染）。 */
    private int[] filterCycleRect;
    /** 设备显隐模式<b>轮换按钮</b>命中矩形（null=未渲染）。 */
    private int[] visibilityCycleRect;
    /** 设备筛选维度轮换按钮悬浮动画。 */
    private final AnimFloat filterCycleHover = AnimFloat.hover();
    /** 设备显隐模式轮换按钮悬浮动画。 */
    private final AnimFloat visibilityCycleHover = AnimFloat.hover();

    private final List<int[]> hitRects = new ArrayList<>();
    private final List<Integer> hitActions = new ArrayList<>();
    private final List<Object> hitArg = new ArrayList<>();
    private final List<int[]> tabRects = new ArrayList<>();
    private final List<Integer> tabIndex = new ArrayList<>();
    private final List<int[]> granularityBtnRects = new ArrayList<>();

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
        granularityBtnRects.clear();
        filterCycleRect = null;
        visibilityCycleRect = null;
        overviewHoverSeg = 0;

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

        // 信息区：仅显示成员操作结果提示（按需求文档「移除顶部总发电/总耗电文字」）。
        // 高度固定 16（单行），Tab 栏紧随其后避免刷新时跳动。
        int lastResult = RtsPowerGrid.get().lastActionResult();
        if (lastResult != 0) {
            TextRenderer.draw(g, t("screen.rtsbuilding.powergrid.result_" + lastResult), x, cy,
                    lastResult == 3 ? COLOR_WARN : COLOR_ERR);
        }

        // Tab 栏（成员 / 设备 / 电网总览 三选一）。
        int tabY = cTop + 16;
        renderTabBar(g, x, tabY, w, snap, mouseX, mouseY);
        int tabBottom = tabY + TAB_H;
        int gridTop = tabBottom + 4;

        if (currentTab == TAB_MEMBER) {
            // 成员 Tab：滚动条替代翻页，所有成员行纵向排列，超出可视区域时滚动查看。
            renderMembers(g, x, w, gridTop, cBottom - 2, snap, mouseX, mouseY);
        } else if (currentTab == TAB_DEVICE) {
            // 设备 Tab：按机器类型纵向堆叠折叠条，超出可视区域时滚动查看。
            renderDeviceFolders(g, x, w, gridTop, cBottom - 2, snap, mouseX, mouseY);
        } else {
            // 电网总览 Tab：嵌入式仪表盘子区（上方圆环饼图 + 下方柱状图）。
            renderOverviewDashboard(g, x, w, gridTop, cBottom - 2, snap, mouseX, mouseY);
        }
    }

    private void renderTabBar(GuiGraphics g, int x, int y, int w, PowerGridSnapshot snap, int mouseX, int mouseY) {
        int tabCount = 3;
        // 懒初始化 tabHoverAnims
        while (tabHoverAnims.size() < tabCount) {
            tabHoverAnims.add(AnimFloat.hover());
        }
        int tabW = (w - (tabCount - 1) * 2) / tabCount;
        String[] labels = {t("screen.rtsbuilding.powergrid.tab_member"),
                t("screen.rtsbuilding.powergrid.tab_device"),
                t("screen.rtsbuilding.powergrid.tab_overview")};
        for (int i = 0; i < tabCount; i++) {
            int tx = x + i * (tabW + 2);
            boolean active = i == currentTab;
            boolean hovered = !active && mouseX >= tx && mouseX < tx + tabW && mouseY >= y && mouseY < y + TAB_H;
            float hoverT = tabHoverAnims.get(i).track(hovered);
            int fill = active ? UiPalette.accent() : lerpColor(0x882E3B4C, 0x99364A5E, hoverT);
            SdfRenderer.drawBorderedRoundedRect(g, tx, y, tabW, TAB_H, 4, UiPalette.border(), fill, 1);
            int lw = Minecraft.getInstance().font.width(labels[i]);
            TextRenderer.draw(g, labels[i], tx + (tabW - lw) / 2, y + (TAB_H - Minecraft.getInstance().font.lineHeight) / 2 + 1,
                    active ? ThemeManager.getHoverTextColor() : ThemeManager.getTextColor());
            tabRects.add(new int[]{tx, y, tabW, TAB_H});
            tabIndex.add(i);
        }
    }

    // ---- 电网总览仪表盘 Tab ----

    /**
     * 嵌入式电网总览仪表盘（按需求文档 3.1/3.2/3.3）：
     * <ul>
     *   <li>上方：<b>圆环饼状图</b>（发电蓝/耗电橙，中心镂空）+ 中心占比百分比 + 鼠标悬浮分区提示。</li>
     *   <li>中部：<b>粒度切换按钮组</b>（5 秒 / 1 分钟 / 1 小时 三选一，高亮当前选中）。</li>
     *   <li>下方：<b>上下对比柱状图</b>——时间刻度上，上方发电柱（蓝色，向上）、下方耗电柱（橙色，向下），
     *       数据来自服务端历史时序（{@link RtsPowerGrid#history5s()} / {@code 1m} / {@code 1h}）。</li>
     * </ul>
     * 中心百分比按需求公式 {@code (1 - 较小值/较大值) × 100%}；两者都为 0 时显示 "--"。
     */
    private void renderOverviewDashboard(GuiGraphics g, int x, int w, int gridTop, int gridBottom,
                                          PowerGridSnapshot snap, int mouseX, int mouseY) {
        long gen = snap.totalGeneration();
        long demand = snap.totalDemand();
        Font font = Minecraft.getInstance().font;

        // 首次进入电网总览 Tab：触发所有入场动画。
        if (overviewFirstEnter) {
            overviewFirstEnter = false;
            granuleFadeAnim.snapTo(0f);
            granuleFadeAnim.target(1f);
            barRevealAnim.snapTo(0f);
            barRevealAnim.target(1f);
        }

        // 每 5 秒自动请求一次历史时序（与 5 秒采样档同步），让柱状图持续刷新；
        // 节流到 5 秒一次避免每帧发包。首次进入时立即请求。
        if (RtsPowerGrid.get() != null) {
            long now = net.minecraft.Util.getMillis();
            if (now - overviewLastHistoryRequestMs >= 5000L) {
                overviewLastHistoryRequestMs = now;
                RtsPowerGrid.get().requestHistory();
            }
        }

        // ===== 上方：圆环饼图区域（高度 110，饼图中心在区域高度中心）=====
        int pieAreaTop = gridTop;
        int pieAreaH = 110;
        int pieCx = x + w / 2;
        int pieCy = pieAreaTop + pieAreaH / 2;  // 饼图中心在区域高度中心
        int outerR = 40;
        int innerR = 26;

        // 角度分配：发电占 0° → genAngle，耗电占 genAngle → 360°（顺时针）。
        float genAngle;
        if (gen + demand == 0) {
            genAngle = 0f;
        } else {
            genAngle = (float) (gen * 360.0 / (gen + demand));
        }
        float demandAngle = 360f - genAngle;

        // 动画插值：饼图扇区角度平滑过渡 + 百分比数值跟随变化。
        pieGenAnim.target(genAngle);
        pieDemAnim.target(demandAngle);
        float animGenAngle = pieGenAnim.get();
        float animDemAngle = pieDemAnim.get();

        // 先画一圈灰色底环（防数据为 0 时空白），再画两扇形覆盖。
        SdfRenderer.drawRingSector(g, pieCx, pieCy, outerR, innerR, 0, 360, 0x55223344);
        // 发电扇形（蓝色）。耗电扇形（橙色）。两者拼成完整圆环。
        if (animGenAngle > 0) {
            SdfRenderer.drawRingSector(g, pieCx, pieCy, outerR, innerR, 0, animGenAngle, COLOR_OVERVIEW_GEN);
        }
        if (animDemAngle > 0) {
            SdfRenderer.drawRingSector(g, pieCx, pieCy, outerR, innerR, animGenAngle, 360, COLOR_OVERVIEW_DEMAND);
        }

        // 中心百分比文字（按需求公式，使用动画角度计算插值百分比）。
        String centerText;
        if (gen == 0 && demand == 0) {
            centerText = "--";
        } else {
            // 用动画角度推算插值百分比（genAngle / 360 × 100），
            // 这样饼图旋转时中心数字也和角度同步过渡。
            float pctFromAngles = animGenAngle / 360f * 100f;
            int pct = Math.round(pctFromAngles);
            centerText = pct + "%";
        }
        int ctW = font.width(centerText);
        TextRenderer.draw(g, centerText, pieCx - ctW / 2, pieCy - font.lineHeight / 2,
                ThemeManager.getHoverTextColor());

        // 图例：饼图区域右上角，发电与耗电换行显示（发电在上，耗电在下）。
        // 只保留文字作为颜色标记，不显示数值。
        int legendRight = x + w - 4;
        int legendTop = pieAreaTop + 4;
        String genLabel = t("screen.rtsbuilding.powergrid.overview_gen");
        String demandLabel = t("screen.rtsbuilding.powergrid.overview_demand");
        // 发电行（第一行，上方）
        // 色块（8px 高）与文字垂直居中：色块中心对齐该行文字行中线（textY + lineHeight/2）。
        int gLw = font.width(genLabel);
        int gPillX = legendRight - gLw - 12; // 右对齐：色块 + 2px 间距 + 文字
        int gPillY = legendTop + (font.lineHeight - 8) / 2;
        SdfRenderer.drawPill(g, gPillX, gPillY, 10, 8, COLOR_OVERVIEW_GEN);
        TextRenderer.draw(g, genLabel, gPillX + 12, legendTop, ThemeManager.getTextColor());
        // 耗电行（第二行，下方，行间距 2px）
        int dLw = font.width(demandLabel);
        int dPillX = legendRight - dLw - 12;
        int dPillY = legendTop + 11 + (font.lineHeight - 8) / 2;
        SdfRenderer.drawPill(g, dPillX, dPillY, 10, 8, COLOR_OVERVIEW_DEMAND);
        TextRenderer.draw(g, demandLabel, dPillX + 12, legendTop + 11, ThemeManager.getTextColor());

        // 饼图悬浮提示：鼠标落在发电扇形或耗电扇形上时弹出小浮窗显示数值。
        int dx = mouseX - pieCx;
        int dy = mouseY - pieCy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist >= innerR && dist <= outerR && mouseY >= pieAreaTop && mouseY < pieAreaTop + pieAreaH) {
            // 计算角度（顺时针，0° 在右侧）。
            double ang = Math.toDegrees(Math.atan2(dy, dx));
            if (ang < 0) ang += 360;
            int seg = (ang >= genAngle || genAngle == 0 && demandAngle > 0) ? 2 : 1;
            // genAngle==0 时全部为耗电扇形，归为耗电。
            if (genAngle == 0 && demandAngle > 0) seg = 2;
            if (genAngle == 360) seg = 1;
            overviewHoverSeg = seg;
            String tip = seg == 1
                    ? t("screen.rtsbuilding.powergrid.overview_tip_gen", formatNumber(gen))
                    : t("screen.rtsbuilding.powergrid.overview_tip_demand", formatNumber(demand));
            drawHoverTooltip(g, mouseX, mouseY, tip);
        }

        // ===== 中部：分隔线 + 粒度切换按钮组（高度 28，淡入动画）=====
        // 浅色分隔线，将饼图区与柱状图区自然分开
        int sepY = pieAreaTop + pieAreaH + 2;
        g.fill(x + 4, sepY, x + w - 4, sepY + 1, 0x22334455);
        int granY = sepY + 6;
        int granH = 16;
        String[] granLabels = {
                t("screen.rtsbuilding.powergrid.overview_gran_5s"),
                t("screen.rtsbuilding.powergrid.overview_gran_1m"),
                t("screen.rtsbuilding.powergrid.overview_gran_1h")
        };
        int granBtnW = 56;
        int granGap = 4;
        int granTotal = granBtnW * 3 + granGap * 2;
        int granXStart = x + (w - granTotal) / 2;
        float granAlpha = granuleFadeAnim.get();
        // 懒初始化 granHoverAnims
        while (granHoverAnims.size() < 3) {
            granHoverAnims.add(AnimFloat.hover());
        }
        for (int i = 0; i < 3; i++) {
            int bx = granXStart + i * (granBtnW + granGap);
            boolean active = i == overviewGranularity;
            boolean hovered = !active && mouseX >= bx && mouseX < bx + granBtnW && mouseY >= granY && mouseY < granY + granH;
            float hoverT = granHoverAnims.get(i).track(hovered);
            int fill = active ? UiPalette.accent() : lerpColor(0x882E3B4C, 0x99364A5E, hoverT);
            // 淡入：用 granAlpha 调制 fill 的 alpha 通道
            if (granAlpha < 0.999f) {
                int a = (fill >> 24) & 0xFF;
                a = (int) (a * granAlpha);
                fill = (fill & 0x00FFFFFF) | (a << 24);
            }
            SdfRenderer.drawBorderedRoundedRect(g, bx, granY, granBtnW, granH, 3, UiPalette.border(), fill, 1);
            int lw = font.width(granLabels[i]);
            // 文字颜色也用 granAlpha 调制
            int textColor = active ? ThemeManager.getHoverTextColor() : ThemeManager.getTextColor();
            if (granAlpha < 0.999f) {
                int a = (textColor >> 24) & 0xFF;
                a = (int) (a * granAlpha);
                textColor = (textColor & 0x00FFFFFF) | (a << 24);
            }
            TextRenderer.draw(g, granLabels[i], bx + (granBtnW - lw) / 2,
                    granY + (granH - font.lineHeight) / 2 + 1, textColor);
            granularityBtnRects.add(new int[]{bx, granY, granBtnW, granH});
        }

        // ===== 下方：柱状图区域 =====
        int chartTop = granY + granH + 8;
        int chartBottom = gridBottom;
        renderOverviewBars(g, x, w, chartTop, chartBottom, mouseX, mouseY, gen, demand);
    }

    /**
     * 渲染仪表盘下方柱状图——按当前粒度取历史时序数据点，上下对比布局：
     * 中线之上画发电柱（蓝色，向上生长），中线之下画耗电柱（橙色，向下生长）。
     * 柱宽按数据点数自适应；高度按当前数据集 max 值归一化。
     * 支持最多 20 根柱子，超过时自动降采样。
     * <p>
     * 左侧预留 Y 轴刻度标签区，底部预留 X 轴时间标签区，形成完整的坐标轴柱状图。
     */
    private static final int MAX_BARS = 20;
    private static final int MIN_BAR_WIDTH = 3;
    private static final int MAX_BAR_WIDTH = 18;
    private static final int BAR_GAP = 2;

    private void renderOverviewBars(GuiGraphics g, int x, int w, int top, int bottom, int mouseX, int mouseY,
                                  long currentGen, long currentDem) {
        Font font = Minecraft.getInstance().font;
        int fontH = font.lineHeight;

        // Y 轴标签区（左侧预留 36px），柱状图背景扩大至包含 Y 轴标签区
        // 坐标轴标记数字也画在柱状图背景框内
        int yAxisW = 36;
        int chartW = w - 8;                    // 总宽减左右内边距（8px），背景扩大涵盖 Y 轴标签
        int chartX = x + (w - chartW) / 2;     // 图表背景在面板 X 轴居中

        // X 轴标签区（底部预留）
        int xAxisH = fontH + 4;
        int chartTop = top;
        int chartBottom = bottom - xAxisH;
        int chartH = chartBottom - chartTop;
        int midY = chartTop + chartH / 2;
        int halfH = chartH / 2;
        int barMaxH = Math.max(1, halfH - 8);

        // 柱子绘制区域（Y 轴标签右侧）
        int barAreaX = chartX + yAxisW;
        int barAreaW = chartW - yAxisW;

        // 取当前粒度的历史数据
        List<long[]> pts = switch (overviewGranularity) {
            case 1 -> RtsPowerGrid.get().history1m();
            case 2 -> RtsPowerGrid.get().history1h();
            default -> RtsPowerGrid.get().history5s();
        };

        // 柱状图揭晓进度：检测数据点数量变化（新增数据时触发 0→1 动画）。
        // 首次进入和切换粒度时由 overviewFirstEnter / 粒度点击处理器已有逻辑触发。
        if (pts.size() != lastBarDataSize) {
            lastBarDataSize = pts.size();
            barRevealAnim.snapTo(0f);
            barRevealAnim.target(1f);
        }
        float barReveal = barRevealAnim.get();

        // 柱状图外框 + 中线（背景扩大至涵盖 Y 轴标签区）
        SdfRenderer.drawBorderedRoundedRect(g, chartX, chartTop, chartW, chartH, 3, UiPalette.border(), 0x33223344, 1);
        g.fill(barAreaX + 1, midY, chartX + chartW - 1, midY + 1, UiPalette.border());

        if (pts.isEmpty()) {
            String empty = t("screen.rtsbuilding.powergrid.overview_no_data");
            int ew = font.width(empty);
            TextRenderer.draw(g, empty, barAreaX + (barAreaW - ew) / 2, midY - fontH / 2, UiPalette.border());
            return;
        }

        // Y 轴最大值以当前实时发/耗电量为基准，但不低于历史数据峰值，
        // 保证所有柱子都能完整显示在图表范围内。取两者最大值，向上取整到规整刻度。
        long maxVal = Math.max(1, Math.max(currentGen, currentDem));
        for (long[] pt : pts) {
            if (pt[0] > maxVal) maxVal = pt[0];
            if (pt[1] > maxVal) maxVal = pt[1];
        }
        maxVal = roundToNiceMax(maxVal);
        if (maxVal < 1) maxVal = 1;
        final long fMaxVal = maxVal;

        // 降采样
        List<long[]> displayPts = pts;
        if (pts.size() > MAX_BARS) {
            displayPts = downsampleData(pts, MAX_BARS);
        }

        int n = displayPts.size();
        int totalGapW = (n - 1) * BAR_GAP;
        int availableBarW = barAreaW - 12 - totalGapW; // 柱子左右各留 6px 边距，使柱子在柱子区域中居中
        int barW = Math.min(MAX_BAR_WIDTH, Math.max(MIN_BAR_WIDTH, availableBarW / n));
        int startX = barAreaX + 6;               // 左 6px 边距，与右 6px 对称

        // ==================== Y 轴（左侧，在柱状图背景框内）====================
        // 标签区在柱状图背景框左侧，紧贴背景左边缘
        int yLabelX = chartX;
        long[] yTicks = {0, fMaxVal / 2, fMaxVal};
        for (long tickVal : yTicks) {
            float ratio = (float) tickVal / fMaxVal;
            int yTop = midY - Math.round(ratio * barMaxH);   // 发电侧（向上）
            int yBot = midY + Math.round(ratio * barMaxH);   // 耗电侧（向下）

            // 网格线（半透明，精细 1px，在柱子区域内绘制）
            if (tickVal > 0) {
                g.fill(barAreaX + 1, yTop, chartX + chartW - 1, yTop + 1, 0x22334455);
                g.fill(barAreaX + 1, yBot, chartX + chartW - 1, yBot + 1, 0x22334455);
            }

            // 数字标签（右对齐到 Y 轴标签区右边缘，即柱子区域左边缘）
            String label = formatNumber(tickVal);
            int lw = font.width(label);
            TextRenderer.draw(g, label, yLabelX + (yAxisW - lw - 4), yTop - fontH / 2, ThemeManager.getTextColor());
            if (tickVal > 0) {
                TextRenderer.draw(g, label, yLabelX + (yAxisW - lw - 4), yBot - fontH / 2, ThemeManager.getTextColor());
            }
        }

        // ==================== 柱子（揭晓动画）====================
        for (int i = 0; i < n; i++) {
            long[] pt = displayPts.get(i);
            int bx = startX + i * (barW + BAR_GAP);
            int genH = Math.min((int) (pt[0] * barMaxH / fMaxVal), barMaxH);
            int demH = Math.min((int) (pt[1] * barMaxH / fMaxVal), barMaxH);
            // 揭晓动画：柱高乘 barReveal（0→1 过渡），底部生长。
            genH = Math.round(genH * barReveal);
            demH = Math.round(demH * barReveal);

            if (genH > 0) {
                g.fill(bx, midY - genH, bx + barW, midY, COLOR_OVERVIEW_GEN);
            }
            if (demH > 0) {
                g.fill(bx, midY + 1, bx + barW, midY + 1 + demH, COLOR_OVERVIEW_DEMAND);
            }
        }

        // ==================== X 轴（底部）====================
        // 每 labelInterval 根柱子显示一个时间标签，保证约 4-6 个标签
        int labelInterval = Math.max(1, n / 5);
        int timeStep = switch (overviewGranularity) {
            case 1 -> 60;     // 1 分钟
            case 2 -> 3600;   // 1 小时
            default -> 5;     // 5 秒
        };
        String unit = switch (overviewGranularity) {
            case 1 -> "m";
            case 2 -> "h";
            default -> "s";
        };

        int xAxisY = bottom - xAxisH + 2;
        for (int i = 0; i < n; i++) {
            int bx = startX + i * (barW + BAR_GAP);
            int tickX = bx + barW / 2;

            if (i % labelInterval == 0 || i == n - 1) {
                // 从最后一个柱子往前算相对时间
                int offset = (n - 1 - i) * timeStep;
                String timeLabel;
                if (offset == 0) {
                    timeLabel = t("screen.rtsbuilding.powergrid.overview_time_now");
                } else if (offset >= 3600) {
                    timeLabel = "-" + (offset / 3600) + "h";
                } else if (offset >= 60) {
                    timeLabel = "-" + (offset / 60) + "m";
                } else {
                    timeLabel = "-" + offset + unit;
                }

                // 刻度短线
                g.fill(tickX, chartBottom, tickX + 1, chartBottom + 3, UiPalette.border());
                // 标签文字
                int tlw = font.width(timeLabel);
                TextRenderer.draw(g, timeLabel, tickX - tlw / 2, xAxisY, UiPalette.border());
            }
        }
    }

    /**
     * 降采样数据到指定点数，使用平均值聚合。
     */
    private java.util.List<long[]> downsampleData(java.util.List<long[]> data, int targetCount) {
        int sourceSize = data.size();
        java.util.List<long[]> result = new java.util.ArrayList<>(targetCount);
        int blockSize = sourceSize / targetCount;
        int remainder = sourceSize % targetCount;

        int index = 0;
        for (int i = 0; i < targetCount; i++) {
            int currentBlockSize = blockSize + (i < remainder ? 1 : 0);
            long sumGen = 0, sumDem = 0;
            for (int j = 0; j < currentBlockSize; j++) {
                long[] pt = data.get(index + j);
                sumGen += pt[0];
                sumDem += pt[1];
            }
            result.add(new long[]{sumGen / currentBlockSize, sumDem / currentBlockSize});
            index += currentBlockSize;
        }
        return result;
    }

    // ---- 辅助方法 ----

    /** 格式化数字：最多 2 位有效数字 + k/M/B 单位。如 1500→1.5k, 12300→12k, 2.1B。 */
    private static String formatNumber(long v) {
        if (v >= 1_000_000_000) {
            long scaled = (v * 10) / 1_000_000_000;
            long whole = scaled / 10;
            long frac = scaled % 10;
            if (whole >= 10 || frac == 0) return whole + "B";
            return whole + "." + frac + "B";
        }
        if (v >= 1_000_000) {
            long scaled = (v * 10) / 1_000_000;
            long whole = scaled / 10;
            long frac = scaled % 10;
            if (whole >= 10 || frac == 0) return whole + "M";
            return whole + "." + frac + "M";
        }
        if (v >= 1_000) {
            long scaled = (v * 10) / 1_000;
            long whole = scaled / 10;
            long frac = scaled % 10;
            if (whole >= 10 || frac == 0) return whole + "k";
            return whole + "." + frac + "k";
        }
        return String.valueOf(v);
    }

    /** ARGB 颜色线性插值（t=0 返回 from，t=1 返回 to）。 */
    private static int lerpColor(int from, int to, float t) {
        if (t <= 0) return from;
        if (t >= 1) return to;
        int ia = (from >> 24) & 0xFF, oa = (to >> 24) & 0xFF;
        int ir = (from >> 16) & 0xFF, or = (to >> 16) & 0xFF;
        int ig = (from >> 8) & 0xFF, og = (to >> 8) & 0xFF;
        int ib = from & 0xFF, ob = to & 0xFF;
        return ((int) (ia + (oa - ia) * t) << 24) |
               ((int) (ir + (or - ir) * t) << 16) |
               ((int) (ig + (og - ig) * t) << 8) |
               (int) (ib + (ob - ib) * t);
    }
    private static long roundToNiceMax(long v) {
        if (v <= 0) return 1;
        long magnitude = (long) Math.pow(10, Math.floor(Math.log10(v)));
        long normalized = (v + magnitude - 1) / magnitude; // 向上取整除法
        if (normalized <= 1) return magnitude;
        if (normalized <= 2) return 2 * magnitude;
        if (normalized <= 5) return 5 * magnitude;
        return 10 * magnitude;
    }

    /** 在鼠标位置旁画一个简版悬浮提示框（圆角背景 + 文字），用于饼图分区悬浮提示。 */
    private void drawHoverTooltip(GuiGraphics g, int mouseX, int mouseY, String text) {
        var font = Minecraft.getInstance().font;
        int pad = 4;
        int tw = font.width(text);
        int th = font.lineHeight;
        int boxW = tw + pad * 2;
        int boxH = th + pad * 2;
        // 默认放在鼠标右下方；若超出右侧则放左侧；若超出底部则放上方。
        int bx = mouseX + 10;
        int by = mouseY + 10;
        if (bx + boxW > g.guiWidth()) bx = mouseX - boxW - 10;
        if (by + boxH > g.guiHeight()) by = mouseY - boxH - 10;
        SdfRenderer.drawBorderedRoundedRect(g, bx, by, boxW, boxH, 3, UiPalette.accent(), 0xEE1F2A3A, 1);
        TextRenderer.draw(g, text, bx + pad, by + pad, ThemeManager.getHoverTextColor());
    }

    // ---- 成员 Tab ----

    private void renderMembers(GuiGraphics g, int x, int w, int gridTop, int gridBottom,
                               PowerGridSnapshot snap, int mouseX, int mouseY) {
        List<GridMember> allMembers = snap.members();

        // ── 搜索框（参照 BlueprintLibraryPanel 搜索框实现） ──────────────
        // 搜索框位于 Tab 栏下方，成员列表上方
        int searchY = gridTop;
        if (memberSearchFocused != prevMemberSearchFocused) {
            memberSearchFocusAnim.target(memberSearchFocused ? 1f : 0f);
            prevMemberSearchFocused = memberSearchFocused;
        }
        boolean searchHovered = !memberSearchFocused
                && mouseX >= x && mouseX < x + w
                && mouseY >= searchY && mouseY < searchY + SEARCH_H;
        SdfRenderer.drawInputBox(g, x, searchY, w, SEARCH_H,
                memberSearchFocusAnim.get(), memberSearchHoverAnim.track(searchHovered), 4);

        Font font = Minecraft.getInstance().font;
        String searchText = memberSearchBuffer.toString();
        int textX = x + SEARCH_PAD;
        int textY = searchY + (SEARCH_H - font.lineHeight) / 2;
        int contentAreaW = w - SEARCH_PAD * 2;

        if (memberSearchFocused) {
            String displayText = TextRenderer.trimToWidth(font, searchText, contentAreaW);
            g.drawString(font, displayText, textX, textY, ThemeManager.getTextColor(), false);
            if ((System.currentTimeMillis() / CURSOR_BLINK_MS) % 2 == 0) {
                int cursorX = textX + font.width(displayText);
                g.fill(cursorX, textY, cursorX + 1, textY + font.lineHeight, UiPalette.get("input_cursor"));
            }
        } else {
            String placeholder = searchText.isEmpty()
                    ? t("screen.rtsbuilding.powergrid.search_member")
                    : searchText;
            String displayText = TextRenderer.trimToWidth(font, placeholder, contentAreaW);
            int placeholderColor = searchText.isEmpty() ? (ThemeManager.getTextColor() & 0xFFFFFF) | 0x60000000 : ThemeManager.getTextColor();
            g.drawString(font, displayText, textX, textY, placeholderColor, false);
        }

        // 搜索框与列表之间的分界线
        int dividerY = searchY + SEARCH_H + SEARCH_LIST_GAP - 3;
        g.fill(x, dividerY, x + w, dividerY + 1, UiPalette.get("list_separator"));

        // 标题行：显示「成员 N · 陌生人 M」
        int titleY = searchY + SEARCH_H + SEARCH_LIST_GAP;
        long realCount = allMembers.stream().filter(m -> m.access() != RtsAccessLevel.BLOCKED).count();
        TextRenderer.draw(g, t("screen.rtsbuilding.powergrid.member_count", realCount, allMembers.size() - realCount),
                x, titleY + 2, ThemeManager.getHoverTextColor());

        // 列表区域（搜索框 + 标题下方，可滚动）
        int titleH = ROW_H;
        int listTop = titleY + titleH;
        int listH = gridBottom - listTop;
        if (listH <= 0) return;

        // 使用过滤后的成员列表
        List<GridMember> list = (filteredMembers != null) ? filteredMembers : allMembers;
        int totalRows = list.size();
        int totalContentH = totalRows * ROW_H;
        int visibleH = Math.max(1, listH);
        memberScrollBar.setContent(totalContentH, visibleH);
        int scroll = memberScrollBar.getScroll();
        // 滚动条可见时，行内容宽度扣除滚动条宽度（8px），避免重叠
        int rowW = memberScrollBar.isVisible() ? w - 8 : w;

        // 裁剪：滚动内容限定在 listTop 到 gridBottom 之间，防止与 Tab 栏/搜索框重叠
        g.flush();
        if (this.screen != null) {
            this.screen.enableUiScissor(g, x, listTop, x + w, gridBottom);
        } else {
            g.enableScissor(x, listTop, x + w, gridBottom);
        }

        // 根据滚动偏移渲染所有成员行（由 scissor 纯裁剪，不移除条目）。
        int firstMemberIdx = scroll / ROW_H;
        int drawOffset = scroll % ROW_H;
        int rowY = listTop - drawOffset;
        for (int i = firstMemberIdx; i < totalRows; i++) {
            renderMemberRow(g, x, rowW, rowY, list.get(i), i, mouseX, mouseY);
            rowY += ROW_H;
        }

        g.flush();
        g.disableScissor();

        // 渲染滚动条（右侧）
        if (memberScrollBar.isVisible()) {
            int barX = x + rowW;
            memberScrollBar.render(g, barX, listTop, visibleH);
        }
    }

    private void renderMemberRow(GuiGraphics g, int x, int w, int y, GridMember m, int rowIndex, int mouseX, int mouseY) {
        // 奇偶行背景（参照下面板左嵌层样式）
        int base = (rowIndex % 2 == 0) ? UiPalette.get("list_row_even") : UiPalette.get("list_row_odd");
        AnimFloat hoverAnim = memberRowHoverAnims.computeIfAbsent(m.id(), k -> AnimFloat.hover());
        boolean hovering = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H;
        float hoverT = hoverAnim.track(hovering);
        int rowBg = ColorAnimation.lerpRGB(base, UiPalette.get("list_row_hover"), hoverT);
        g.fill(x, y, x + w, y + ROW_H, rowBg);

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
        if (hovering) {
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
     * <p>设备较多超出可视区域时，右侧出现滚动条支持滚动查看。</p>
     */
    private void renderDeviceFolders(GuiGraphics g, int x, int w, int gridTop, int gridBottom,
                                     PowerGridSnapshot snap, int mouseX, int mouseY) {
        List<PowerDevice> allDevices = snap.devices();

        // ── 筛选工具栏 + 显隐切换控件（搜索框上方） ──────────────
        // 依据当前筛选维度 / 显隐模式过滤设备列表（本地状态，不入服务端）。
        refreshDeviceFilteredList(snap);

        // ── 搜索框（参照 BlueprintLibraryPanel 搜索框实现） ──────────────
        // 搜索框位于工具栏下方，设备列表上方
        int searchY = deviceSearchTop(gridTop);
        if (deviceSearchFocused != prevDeviceSearchFocused) {
            deviceSearchFocusAnim.target(deviceSearchFocused ? 1f : 0f);
            prevDeviceSearchFocused = deviceSearchFocused;
        }
        boolean searchHovered = !deviceSearchFocused
                && mouseX >= x && mouseX < x + w
                && mouseY >= searchY && mouseY < searchY + SEARCH_H;
        SdfRenderer.drawInputBox(g, x, searchY, w, SEARCH_H,
                deviceSearchFocusAnim.get(), deviceSearchHoverAnim.track(searchHovered), 4);

        Font font = Minecraft.getInstance().font;
        String searchText = deviceSearchBuffer.toString();
        int textX = x + SEARCH_PAD;
        int textY = searchY + (SEARCH_H - font.lineHeight) / 2;
        int contentAreaW = w - SEARCH_PAD * 2;

        if (deviceSearchFocused) {
            String displayText = TextRenderer.trimToWidth(font, searchText, contentAreaW);
            g.drawString(font, displayText, textX, textY, ThemeManager.getTextColor(), false);
            if ((System.currentTimeMillis() / CURSOR_BLINK_MS) % 2 == 0) {
                int cursorX = textX + font.width(displayText);
                g.fill(cursorX, textY, cursorX + 1, textY + font.lineHeight, UiPalette.get("input_cursor"));
            }
        } else {
            String placeholder = searchText.isEmpty()
                    ? t("screen.rtsbuilding.powergrid.search_device")
                    : searchText;
            String displayText = TextRenderer.trimToWidth(font, placeholder, contentAreaW);
            int placeholderColor = searchText.isEmpty() ? (ThemeManager.getTextColor() & 0xFFFFFF) | 0x60000000 : ThemeManager.getTextColor();
            g.drawString(font, displayText, textX, textY, placeholderColor, false);
        }

        // 搜索框与列表之间的分界线
        int dividerY = searchY + SEARCH_H + SEARCH_LIST_GAP - 3;
        g.fill(x, dividerY, x + w, dividerY + 1, UiPalette.get("list_separator"));

        // 工具栏（筛选维度 + 显隐模式）位于搜索框上方，需在列表画布之前绘制。
        // 由于工具栏在搜索框上方，且在折叠条滚动裁剪区之外，因此直接以屏幕坐标绘制。
        renderDeviceToolbar(g, x, w, gridTop, mouseX, mouseY);

        // 列表区域（搜索框下方，可滚动）
        int listTop = searchY + SEARCH_H + SEARCH_LIST_GAP;
        int listH = gridBottom - listTop;
        if (listH <= 0) return;

        // 使用过滤后的设备列表
        List<PowerDevice> devices = (filteredDevices != null) ? filteredDevices : allDevices;

        // 按机器类型（itemId；缺省回落 label 翻译键）分组，保持出现顺序。
        java.util.LinkedHashMap<String, java.util.List<PowerDevice>> byType = new java.util.LinkedHashMap<>();
        for (PowerDevice d : devices) {
            String key = (d.itemId() == null || d.itemId().isEmpty())
                    ? (d.label() == null ? "" : d.label()) : d.itemId();
            byType.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }

        // 先计算所有设备组的总高度，用来设置滚动条范围
        int totalContentH = 0;
        for (var e : byType.entrySet()) {
            java.util.List<PowerDevice> group = e.getValue();
            if (group.size() == 1) {
                totalContentH += ROW_H + 2;
            } else {
                CollapsibleSection section = deviceSections.computeIfAbsent(e.getKey(),
                        k -> new CollapsibleSection(""));
                int contentH = group.size() * ROW_H;
                totalContentH += CollapsibleSection.headerHeight();
                totalContentH += (int) (contentH * section.getContentProgress());
            }
        }

        int visibleH = Math.max(1, listH);
        deviceScrollBar.setContent(totalContentH, visibleH);
        int scroll = deviceScrollBar.getScroll();
        // 滚动条可见时，内容宽度扣除滚动条宽度（8px），避免重叠
        int rowW = deviceScrollBar.isVisible() ? w - 8 : w;

        // 裁剪：设备内容限定在 listTop 到 gridBottom 之间，防止与 Tab 栏/搜索框重叠
        // x/listTop/gridBottom 已是屏幕全局坐标，使用 screen.enableUiScissor 适配 RTS GUI 缩放。
        g.flush();
        if (this.screen != null) {
            this.screen.enableUiScissor(g, x, listTop, x + w, gridBottom);
        } else {
            g.enableScissor(x, listTop, x + w, gridBottom);
        }

        // 根据滚动偏移渲染所有设备组（由 scissor 纯裁剪，不移除条目）。
        int y = listTop + 2 - scroll;
        int deviceIdx = 0;  // 全局设备索引，用于奇偶行背景
        for (var e : byType.entrySet()) {
            java.util.List<PowerDevice> group = e.getValue();
            if (group.size() == 1) {
                // 只有一台：不堆叠，直接显示单台设备行。
                renderDeviceRow(g, x, rowW, y, group.get(0), deviceIdx, mouseX, mouseY);
                y += ROW_H + 2;
                deviceIdx++;
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
            section.drawHeader(g, mouseX, mouseY, x, y, rowW, contentH);
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
            // 组内可切换角色的设备数（>0 时显示「一键修改」按钮，批量切换发电↔用电）。
            long toggleableCount = group.stream().filter(PowerDevice::canToggleRole).count();
            int quickToggleW = toggleableCount > 0 ? QUICK_TOGGLE_BTN_W : 0;
            // 标题空间：右侧为聚合 + 「一键修改」按钮预留。
            int titleMax = rowW - 42 - 66 - 12 - (quickToggleW + 6);
            TextRenderer.draw(g, TextRenderer.trimToWidth(Minecraft.getInstance().font, headTitle,
                            Math.max(20, titleMax)),
                    headTitleX, y + 7, ThemeManager.getTextColor());
            // 右侧聚合：发电/输电/用电 X/tick（对齐设置面板折叠条右侧）。
            String agg = switch (role) {
                case GENERATOR -> "发电 " + formatNumber(sum) + "/tick";
                case TOWER -> "输电 " + formatNumber(sum) + "/tick";
                default -> "用电 " + formatNumber(sum) + "/tick";
            };
            int groupColor = switch (role) {
                case GENERATOR -> COLOR_GEN;
                case TOWER -> COLOR_TOWER;
                default -> COLOR_WARN;
            };
            int aggRight = x + rowW - 4 - (quickToggleW + 6);
            TextRenderer.draw(g, agg, aggRight - Minecraft.getInstance().font.width(agg), y + 7, groupColor);
            // 「一键修改」按钮（批量切换本组全部可切换设备，发电↔用电）——样式/文案/颜色与单台
            // 设备行内的「◀用电 / 发电▶」切换按钮完全一致：以组内第一台可切换设备的<b>相反角色</b>
            // 为目标角色，按钮据此显示目标方向（绿=当前发电、橙=当前用电）。点击后整组统一切换
            // （见 {@link #handleContentClick} action 17 → {@link #batchToggleGroup}）。
            if (toggleableCount > 0) {
                // 组内第一台可切换设备的角色（决定按钮颜色与「◀/▶」目标方向）。
                RtsDeviceRole toggleBase = RtsDeviceRole.CONSUMER;
                for (PowerDevice d : group) {
                    if (d.canToggleRole()) {
                        toggleBase = d.role();
                        break;
                    }
                }
                int btnX = aggRight + 4;
                int btnY = y + (CollapsibleSection.headerHeight() - 14) / 2;
                int btnW = QUICK_TOGGLE_BTN_W;
                int btnH = 14;
                boolean hovering = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
                int btnColor = toggleBase == RtsDeviceRole.GENERATOR ? 0x884CAF50 : 0x88FF7043;
                if (hovering) {
                    btnColor = (btnColor & 0x00FFFFFF) | 0xBB000000;  // 悬浮加深
                }
                String btnLabel = toggleBase == RtsDeviceRole.GENERATOR ? "◀用电" : "发电▶";
                SdfRenderer.drawPill(g, btnX, btnY, btnW, btnH, btnColor);
                int labelColor = hovering ? 0xFFFFFFFF : 0xCCFFFFFF;
                TextRenderer.draw(g, btnLabel,
                        btnX + btnW / 2 - Minecraft.getInstance().font.width(btnLabel) / 2,
                        btnY + (btnH - Minecraft.getInstance().font.lineHeight) / 2 + 1, labelColor);
                // 命中检测（arg = 该组在 byType 中的 key）
                hitRects.add(new int[]{btnX, btnY, btnW, btnH});
                hitActions.add(17);
                hitArg.add(e.getKey());
            }
            // 组头命中：折叠/展开折叠条。
            if (section.isHeaderClicked(mouseX, mouseY, x, y, rowW)) {
                hitRects.add(new int[]{x, y, rowW, CollapsibleSection.headerHeight()});
                hitActions.add(13);
                hitArg.add(e.getKey());
            }
            int headerH = CollapsibleSection.headerHeight();
            y += headerH;
            // 展开：全部设备行都渲染，由 scissor 裁剪显示到内容动画高度对应进度区域。
            if (section.isExpanded()) {
                int animH = (int) (contentH * section.getContentProgress());
                if (animH > 0) {
                    g.flush();
                    if (this.screen != null) {
                        this.screen.enableUiScissor(g, x, y, x + rowW, y + animH);
                    } else {
                        g.enableScissor(x, y, x + rowW, y + animH);
                    }
                    int dy = y;
                    for (PowerDevice d : group) {
                        renderDeviceRow(g, x, rowW, dy, d, deviceIdx, mouseX, mouseY);
                        dy += ROW_H;
                        deviceIdx++;
                    }
                    g.flush();
                    g.disableScissor();
                }
            }
            // 折叠条当前动画状态下的占位高度
            y += (int) (contentH * section.getContentProgress());
        }

        g.flush();
        g.disableScissor();

        // 渲染设备滚动条（右侧）
        if (deviceScrollBar.isVisible()) {
            int barX = x + rowW;
            deviceScrollBar.render(g, barX, listTop, visibleH);
        }
    }

    /** 设备 Tab 搜索框顶部 y：位于筛选工具栏 + 显隐切换控件的下方。 */
    /** 设备 Tab 搜索框顶部 y：位于一排（两个轮换按钮）工具栏的下方。 */
    private int deviceSearchTop(int gridTop) {
        return gridTop + TOOLBAR_H + TOOLBAR_LIST_GAP;
    }

    /**
     * 渲染设备 Tab 上方的过滤/显隐工具栏（<b>一排两个轮换按钮</b>）：
     * <ul>
     *   <li>左：<b>筛选维度轮换按钮</b>「全部 / 发电 / 用电」，点击按 {@link #FILTER_CYCLE} 轮换
     *       显示当前维度（{@link #deviceFilter}）。</li>
     *   <li>右：<b>显隐模式轮换按钮</b>「仅显示 / 全部 / 仅隐藏」，点击按 {@link #VIS_CYCLE} 轮换
     *       显示当前视图模式（{@link #deviceVisibilityMode}）。</li>
     * </ul>
     * 两按钮等分宽度，点击由 {@link #handleContentClick} 经命中矩形处理。
     */
    private void renderDeviceToolbar(GuiGraphics g, int x, int w, int toolbarTop, int mouseX, int mouseY) {
        int btnW = (w - 2) / 2;               // 两按钮等分，中间 2px 间距
        int btnH = TOOLBAR_H;
        int fy = toolbarTop;
        // 左：筛选维度轮换按钮（显示当前维度标签）
        filterCycleRect = drawCycleButton(g, x, fy, btnW, btnH, mouseX, mouseY,
                filterCycleLabel(), filterCycleHover);
        // 右：显隐模式轮换按钮（显示当前视图模式标签）
        visibilityCycleRect = drawCycleButton(g, x + btnW + 2, fy, btnW, btnH, mouseX, mouseY,
                visibilityCycleLabel(), visibilityCycleHover);
    }

    /** 绘制一个<b>轮换按钮</b>（圆角矩形 + 当前标签文本，悬浮加深），返回命中矩形。 */
    private int[] drawCycleButton(GuiGraphics g, int x, int y, int w, int h,
                                  int mouseX, int mouseY, String label, AnimFloat hoverAnim) {
        Font font = Minecraft.getInstance().font;
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        float hoverT = hoverAnim.track(hovered);
        int fill = lerpColor(0x882E3B4C, 0x99364A5E, hoverT);
        SdfRenderer.drawBorderedRoundedRect(g, x, y, w, h, 3, UiPalette.border(), fill, 1);
        int lw = font.width(label);
        TextRenderer.draw(g, label, x + (w - lw) / 2, y + (h - font.lineHeight) / 2 + 1,
                ThemeManager.getTextColor());
        return new int[]{x, y, w, h};
    }

    /** 当前筛选维度轮换按钮标签（全部 / 发电 / 用电）。 */
    private String filterCycleLabel() {
        return switch (deviceFilter) {
            case FILTER_GEN -> t("screen.rtsbuilding.powergrid.filter_gen");
            case FILTER_CONSUMER -> t("screen.rtsbuilding.powergrid.filter_cons");
            default -> t("screen.rtsbuilding.powergrid.filter_all");
        };
    }

    /** 当前显隐模式轮换按钮标签（仅显示 / 全部 / 仅隐藏）。 */
    private String visibilityCycleLabel() {
        return switch (deviceVisibilityMode) {
            case VIS_ALL -> t("screen.rtsbuilding.powergrid.vis_all");
            case VIS_HIDDEN_ONLY -> t("screen.rtsbuilding.powergrid.vis_hidden_only");
            default -> t("screen.rtsbuilding.powergrid.vis_visible_only");
        };
    }

    /** 在给定轮换序列中取<b>下一个</b>状态（循环）；当前值不在序列时回到首项。 */
    private static int nextInCycle(int current, int[] cycle) {
        for (int i = 0; i < cycle.length; i++) {
            if (cycle[i] == current) {
                return cycle[(i + 1) % cycle.length];
            }
        }
        return cycle[0];
    }

    /**
     * 依据「搜索缓冲 + 筛选维度 + 显隐模式」三者合成设备过滤列表，写入 {@link #filteredDevices}。
     * 任一条件未激活时也保持「未过滤」语义（置 null）。
     * <p>筛选/显隐均为客户端本地状态，隐藏集合 {@link #hiddenDeviceKeys} 不随搜索计数，仅影响可见性。</p>
     */
    private void refreshDeviceFilteredList(PowerGridSnapshot snap) {
        if (snap == null) {
            filteredDevices = null;
            return;
        }
        List<PowerDevice> all = snap.devices();
        boolean hasFilter = !deviceSearchBuffer.isEmpty()
                || deviceFilter != FILTER_ALL
                || deviceVisibilityMode != VIS_VISIBLE_ONLY;
        if (!hasFilter) {
            filteredDevices = null;
            return;
        }
        String query = deviceSearchBuffer.toString().toLowerCase(Locale.ROOT);
        List<PowerDevice> out = new ArrayList<>();
        for (PowerDevice d : all) {
            // 显隐模式过滤
            String key = d.x() + "_" + d.y() + "_" + d.z();
            boolean hidden = hiddenDeviceKeys.contains(key);
            if (deviceVisibilityMode == VIS_HIDDEN_ONLY && !hidden) continue;
            if (deviceVisibilityMode == VIS_VISIBLE_ONLY && hidden) continue;
            // 筛选维度过滤
            if (deviceFilter == FILTER_GEN && d.role() != RtsDeviceRole.GENERATOR) continue;
            if (deviceFilter == FILTER_CONSUMER && d.role() != RtsDeviceRole.CONSUMER) continue;
            // 搜索过滤（沿用 applyDeviceSearch 的字段匹配 + 拼音）
            if (!query.isEmpty() && !matchesDeviceSearch(d, query)) continue;
            out.add(d);
        }
        filteredDevices = out;
    }

    /** 单台设备是否匹配搜索关键词（翻译后显示名 / label / itemId 字段 + 拼音）。 */
    private static boolean matchesDeviceSearch(PowerDevice d, String query) {
        String label = d.label() == null ? "" : d.label().toLowerCase(Locale.ROOT);
        String itemId = d.itemId() == null ? "" : d.itemId().toLowerCase(Locale.ROOT);
        String displayName = d.label() == null || d.label().isEmpty()
                ? "" : Component.translatable(d.label()).getString().toLowerCase(Locale.ROOT);
        return label.contains(query) || itemId.contains(query)
                || displayName.contains(query)
                || RtsPinyinSearch.contains(label, query)
                || RtsPinyinSearch.contains(itemId, query)
                || RtsPinyinSearch.contains(displayName, query);
    }

    // ── 搜索过滤（参照 BlueprintLibraryPanel.applySearch 实现） ──────────

    /** 按成员搜索缓冲实时过滤（大小写不敏感 + 拼音搜索）。 */
    private void applyMemberSearch(PowerGridSnapshot snap) {
        String query = memberSearchBuffer.toString().toLowerCase(Locale.ROOT);
        List<GridMember> all = snap.members();
        if (query.isEmpty()) {
            filteredMembers = null;
            return;
        }
        filteredMembers = new ArrayList<>();
        for (GridMember m : all) {
            String name = m.name().toLowerCase(Locale.ROOT);
            if (name.contains(query) || RtsPinyinSearch.contains(name, query)) {
                filteredMembers.add(m);
            }
        }
        // 过滤后重置滚动位置
        memberScrollBar.setScroll(0);
    }

    /** 按设备搜索缓冲实时过滤（大小写不敏感 + 拼音搜索，匹配翻译后显示名、label 和 itemId）。
     * 与筛选维度、显隐视图模式合成后统一写入 {@link #filteredDevices}。 */
    private void applyDeviceSearch(PowerGridSnapshot snap) {
        if (snap == null) {
            filteredDevices = null;
            return;
        }
        refreshDeviceFilteredList(snap);
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

    private void renderDeviceRow(GuiGraphics g, int x, int w, int y, PowerDevice d, int deviceIdx, int mouseX, int mouseY) {
        // 奇偶行背景（参照下面板左嵌层样式）
        int base = (deviceIdx % 2 == 0) ? UiPalette.get("list_row_even") : UiPalette.get("list_row_odd");
        String devKey = d.x() + "_" + d.y() + "_" + d.z();
        AnimFloat hoverAnim = deviceRowHoverAnims.computeIfAbsent(devKey, k -> AnimFloat.hover());
        boolean hovering = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H;
        float hoverT = hoverAnim.track(hovering);
        int rowBg = ColorAnimation.lerpRGB(base, UiPalette.get("list_row_hover"), hoverT);
        g.fill(x, y, x + w, y + ROW_H, rowBg);

        int textY = rowTextY(y);
        RtsDeviceRole role = d.role();
        // 物品图标固定「图标列 x+22」、名称固定「文字列 x+42」——与上方折叠条组头同列（X 轴对齐）。
        final int iconX = x + 22;
        final int textX = x + 42;
        ItemStack stack = (d.itemId() == null || d.itemId().isEmpty())
                ? ItemStack.EMPTY : GuiItemRenderer.resolveItemStack(d.itemId());
        if (!stack.isEmpty()) {
            GuiItemRenderer.drawItem(g, stack, iconX, y + (ROW_H - 16) / 2 + 1);
        }
        // 物品名称（翻译键 → 实际名字）
        String name = d.label() == null || d.label().isEmpty()
                ? "" : Component.translatable(d.label()).getString();
        // 量值（右对齐）：（发电/输电/用电）X/tick —— 发电绿 / 输电蓝 / 用电橙
        String metric = switch (role) {
            case GENERATOR -> "发电 " + formatNumber(d.metric()) + "/tick";
            case TOWER -> "输电 " + formatNumber(d.metric()) + "/tick";
            case CONSUMER -> "用电 " + formatNumber(d.metric()) + "/tick";
        };
        int metricColor = switch (role) {
            case GENERATOR -> COLOR_GEN;
            case TOWER -> COLOR_TOWER;
            default -> COLOR_WARN;   // 用电橙
        };
        // 可切换角色时，在量值左侧预留切换按钮空间（40px，含按钮+间距）；输电塔预留刷新按钮空间（按文案自适应）。
        final int toggleBtnW = 40;
        // 刷新按钮文案（lang 管理，避免硬编码）；按钮宽 = 文案宽 + 8px 内边距。
        String refreshLabel = t("screen.rtsbuilding.powergrid.refresh_tower");
        int refreshBtnW = Minecraft.getInstance().font.width(refreshLabel) + 8;
        // 右侧按钮区（从右到左）：[显隐开关(eyeBtnW)] + [切换/刷新按钮] + [量值文本]。
        // 显隐开关始终显示在最右端；切换/刷新按设备类型各占其一（互斥）。
        int eyeAlloc = EYE_BTN_W + 4;
        int rightReserved = eyeAlloc + (d.canToggleRole() ? toggleBtnW
                : (role == RtsDeviceRole.TOWER ? refreshBtnW : 0));
        int metricRightEdge = x + w - 4 - rightReserved;
        int metricLeft = metricRightEdge - Minecraft.getInstance().font.width(metric);
        TextRenderer.draw(g, metric, metricLeft, textY, metricColor);
        // 显隐开关按钮（最右端）：方框图标——未隐藏→框内绿色实心矩形（当前显示中）；
        // 已隐藏→空心框（当前被隐藏，框内无绿矩）。点击切换隐藏/恢复显示。
        {
            int btnX = x + w - 4 - EYE_BTN_W;
            int btnY = y + (ROW_H - EYE_BTN_H) / 2 + 1;
            int btnW = EYE_BTN_W;
            int btnH = EYE_BTN_H;
            boolean hidden = hiddenDeviceKeys.contains(devKey);
            boolean hover = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            // 外框：未隐藏用中性描边、已隐藏用暖色描边；悬浮时提亮。
            int frameColor = hover ? 0xBBFFFFFF : (hidden ? 0xBB7B68EE : 0xBB8AA0B5);
            SdfRenderer.drawRoundedOutline(g, btnX, btnY, btnW, btnH, 3, frameColor, 1);
            // 未隐藏（显示中）：框内绿色实心矩形；已隐藏：空心（不画内矩形）。
            if (!hidden) {
                int pad = 4;
                SdfRenderer.drawRoundedRect(g, btnX + pad, btnY + pad,
                        btnW - pad * 2, btnH - pad * 2, 2, 0xFF66BB6A);
            }
            // 命中检测
            hitRects.add(new int[]{btnX, btnY, btnW, btnH});
            hitActions.add(16);
            hitArg.add(devKey);
        }
        // 可切换角色：绘制一个小型切换按钮（用电 ↔ 发电）
        if (d.canToggleRole()) {
            int btnX = x + w - 4 - EYE_BTN_W - 4 - toggleBtnW;
            int btnY = y + (ROW_H - 14) / 2 + 1;
            int btnW = toggleBtnW;
            int btnH = 14;
            boolean hover = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            int btnColor = role == RtsDeviceRole.GENERATOR ? 0x884CAF50 : 0x88FF7043;
            if (hover) {
                btnColor = (btnColor & 0x00FFFFFF) | 0xBB000000;  // 悬浮加深
            }
            String btnLabel = role == RtsDeviceRole.GENERATOR ? "◀用电" : "发电▶";
            SdfRenderer.drawPill(g, btnX, btnY, btnW, btnH, btnColor);
            int labelColor = hover ? 0xFFFFFFFF : 0xCCFFFFFF;
            TextRenderer.draw(g, btnLabel,
                    btnX + btnW / 2 - Minecraft.getInstance().font.width(btnLabel) / 2,
                    btnY + (btnH - Minecraft.getInstance().font.lineHeight) / 2 + 1, labelColor);
            // 命中检测
            hitRects.add(new int[]{btnX, btnY, btnW, btnH});
            hitActions.add(14);
            hitArg.add(new long[]{d.x(), d.y(), d.z(),
                    role == RtsDeviceRole.GENERATOR ? (byte) RtsDeviceRole.CONSUMER.ordinal()
                            : (byte) RtsDeviceRole.GENERATOR.ordinal()});
        }
        // 输电塔：绘制刷新按钮（立即重扫供电范围覆盖，绕过自适应退避）。
        if (role == RtsDeviceRole.TOWER) {
            int btnX = x + w - 4 - EYE_BTN_W - 4 - refreshBtnW;
            int btnY = y + (ROW_H - 14) / 2 + 1;
            int btnW = refreshBtnW;
            int btnH = 14;
            boolean hover = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            int btnColor = 0x8845A1C9;
            if (hover) {
                btnColor = (btnColor & 0x00FFFFFF) | 0xBB000000;  // 悬浮加深
            }
            SdfRenderer.drawPill(g, btnX, btnY, btnW, btnH, btnColor);
            // 「刷新」文字（level 垂直居中）。
            int labelColor = hover ? 0xFFFFFFFF : 0xE8FFFFFF;
            TextRenderer.draw(g, refreshLabel,
                    btnX + btnW / 2 - Minecraft.getInstance().font.width(refreshLabel) / 2,
                    btnY + (btnH - Minecraft.getInstance().font.lineHeight) / 2 + 1, labelColor);
            // 命中检测
            hitRects.add(new int[]{btnX, btnY, btnW, btnH});
            hitActions.add(15);
            hitArg.add(new long[]{d.x(), d.y(), d.z()});
        }
        int nameMax = metricLeft - textX - 6;
        TextRenderer.draw(g, TextRenderer.trimToWidth(Minecraft.getInstance().font, name, Math.max(8, nameMax)),
                textX, textY, ThemeManager.getTextColor());
        // 点击行 → 定位设备
        if (hovering) {
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

    // ---- 滚动条交互 ----

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 0) {
            int cy = contentY();
            int ch = contentHeight();
            int cw = contentWidth();
            // 计算列表区域位置（与渲染和 handleContentClick 一致）
            int x = contentX() + CONTENT_PAD;
            int w = cw - CONTENT_PAD * 2;
            int cTop = cy;
            int tabY = cTop + 16;
            int gridTop = tabY + TAB_H + 4;

            if (memberScrollBar.isDragging()) {
                int searchY = gridTop;
                int titleY = searchY + SEARCH_H + SEARCH_LIST_GAP;
                int listTop = titleY + ROW_H;
                int listH = cy + ch - listTop;
                memberScrollBar.handleDrag(my, listTop, listH);
                return true;
            }
            if (deviceScrollBar.isDragging()) {
                int listTop = deviceSearchTop(gridTop) + SEARCH_H + SEARCH_LIST_GAP;
                int listH = cy + ch - listTop;
                deviceScrollBar.handleDrag(my, listTop, listH);
                return true;
            }
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        memberScrollBar.endDrag();
        deviceScrollBar.endDrag();
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    protected boolean handleContentScroll(double mx, double my, double sx, double sy) {
        if (currentTab == TAB_MEMBER) {
            return memberScrollBar.handleScroll(sy);
        }
        if (currentTab == TAB_DEVICE) {
            return deviceScrollBar.handleScroll(sy);
        }
        return false;
    }

    // ── 搜索框键盘输入（参照 BlueprintLibraryPanel.handleWindowKeyPressed 实现） ──

    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        boolean searchActive = memberSearchFocused || deviceSearchFocused;
        if (!searchActive) return false;

        StringBuilder buf = memberSearchFocused ? memberSearchBuffer : deviceSearchBuffer;
        int[] cursorPos = memberSearchFocused ? new int[]{memberSearchCursorPos} : new int[]{deviceSearchCursorPos};
        Runnable updateCursor = () -> {
            if (memberSearchFocused) memberSearchCursorBlink = System.currentTimeMillis();
            else deviceSearchCursorBlink = System.currentTimeMillis();
        };
        Runnable applyFilter = () -> {
            PowerGridSnapshot snap = RtsPowerGrid.get() == null ? null : RtsPowerGrid.get().currentGrid();
            if (snap == null) return;
            if (memberSearchFocused) applyMemberSearch(snap);
            else applyDeviceSearch(snap);
        };

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (memberSearchFocused) memberSearchFocused = false;
            if (deviceSearchFocused) deviceSearchFocused = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (memberSearchFocused) memberSearchFocused = false;
            if (deviceSearchFocused) deviceSearchFocused = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            int pos = cursorPos[0];
            if (pos > 0 && buf.length() > 0) {
                buf.deleteCharAt(pos - 1);
                if (memberSearchFocused) memberSearchCursorPos--;
                else deviceSearchCursorPos--;
                updateCursor.run();
                applyFilter.run();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            int pos = cursorPos[0];
            if (pos < buf.length()) {
                buf.deleteCharAt(pos);
                updateCursor.run();
                applyFilter.run();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if (memberSearchFocused) memberSearchCursorPos = Math.max(0, memberSearchCursorPos - 1);
            else deviceSearchCursorPos = Math.max(0, deviceSearchCursorPos - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (memberSearchFocused) memberSearchCursorPos = Math.min(memberSearchBuffer.length(), memberSearchCursorPos + 1);
            else deviceSearchCursorPos = Math.min(deviceSearchBuffer.length(), deviceSearchCursorPos + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            if (memberSearchFocused) memberSearchCursorPos = 0;
            else deviceSearchCursorPos = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            if (memberSearchFocused) memberSearchCursorPos = memberSearchBuffer.length();
            else deviceSearchCursorPos = deviceSearchBuffer.length();
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                int pos = cursorPos[0];
                buf.insert(pos, clip);
                if (memberSearchFocused) memberSearchCursorPos += clip.length();
                else deviceSearchCursorPos += clip.length();
                updateCursor.run();
                applyFilter.run();
            }
            return true;
        }
        return true;
    }

    /** 字符输入：搜索框聚焦时录入可打印字符。 */
    @Override
    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        if (!memberSearchFocused && !deviceSearchFocused) return false;
        if (codePoint < 32 || Character.isISOControl(codePoint)) return false;

        StringBuilder buf = memberSearchFocused ? memberSearchBuffer : deviceSearchBuffer;
        int pos = memberSearchFocused ? memberSearchCursorPos : deviceSearchCursorPos;
        buf.insert(pos, codePoint);
        if (memberSearchFocused) {
            memberSearchCursorPos++;
            memberSearchCursorBlink = System.currentTimeMillis();
        } else {
            deviceSearchCursorPos++;
            deviceSearchCursorBlink = System.currentTimeMillis();
        }
        PowerGridSnapshot snap = RtsPowerGrid.get() == null ? null : RtsPowerGrid.get().currentGrid();
        if (snap != null) {
            if (memberSearchFocused) applyMemberSearch(snap);
            else applyDeviceSearch(snap);
        }
        return true;
    }

    @Override
    protected void onClose() {
        this.memberSearchFocused = false;
        this.memberSearchBuffer.setLength(0);
        this.memberSearchCursorPos = 0;
        this.filteredMembers = null;
        this.prevMemberSearchFocused = false;
        this.memberSearchFocusAnim.snapTo(0f);
        this.memberSearchHoverAnim.snapTo(0f);

        this.deviceSearchFocused = false;
        this.deviceSearchBuffer.setLength(0);
        this.deviceSearchCursorPos = 0;
        this.filteredDevices = null;
        this.prevDeviceSearchFocused = false;
        this.deviceSearchFocusAnim.snapTo(0f);
        this.deviceSearchHoverAnim.snapTo(0f);

        this.deviceSections.clear();
        this.memberRowHoverAnims.clear();
        this.deviceRowHoverAnims.clear();
        // 重置设备筛选 / 显隐视图状态（隐藏集合保留供下次打开继续生效）。
        this.deviceFilter = FILTER_ALL;
        this.deviceVisibilityMode = VIS_VISIBLE_ONLY;
        super.onClose();
    }

    // ---- 交互 ----

    @Override
    protected void handleContentClick(double mx, double my, int button) {
        if (button != 0) {
            return;
        }
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();
        int ch = contentHeight();

        // 计算当前 Tab 内容区起始坐标（与渲染一致）
        int x = cx + CONTENT_PAD;
        int w = cw - CONTENT_PAD * 2;
        int cTop = cy;
        int tabY = cTop + 16;
        int gridTop = tabY + TAB_H + 4;

        // ── 搜索框点击（与渲染坐标一致） ──────────────────────────────
        int memberSearchY = gridTop;
        int deviceSearchY = deviceSearchTop(gridTop);
        boolean onMemberSearch = currentTab == TAB_MEMBER
                && mx >= x && mx < x + w
                && my >= memberSearchY && my < memberSearchY + SEARCH_H;
        boolean onDeviceSearch = currentTab == TAB_DEVICE
                && mx >= x && mx < x + w
                && my >= deviceSearchY && my < deviceSearchY + SEARCH_H;

        if (onMemberSearch) {
            memberSearchFocused = true;
            memberSearchCursorBlink = System.currentTimeMillis();
            // 失焦设备搜索
            if (deviceSearchFocused) {
                deviceSearchFocused = false;
            }
            return;
        }
        if (onDeviceSearch) {
            deviceSearchFocused = true;
            deviceSearchCursorBlink = System.currentTimeMillis();
            if (memberSearchFocused) {
                memberSearchFocused = false;
            }
            return;
        }
        // 点击搜索框外：失焦并应用当前搜索
        if (memberSearchFocused) {
            memberSearchFocused = false;
        }
        if (deviceSearchFocused) {
            deviceSearchFocused = false;
        }

        // 成员 Tab 滚动条点击
        if (currentTab == TAB_MEMBER && memberScrollBar.isVisible()) {
            int barX = cx + cw - 12;
            int titleY = memberSearchY + SEARCH_H + SEARCH_LIST_GAP;
            int listTop = titleY + ROW_H;
            int listH = cy + ch - listTop;
            if (memberScrollBar.handleClick(mx, my, barX, listTop, listH)) {
                return;
            }
        }
        // 设备 Tab 滚动条点击
        if (currentTab == TAB_DEVICE && deviceScrollBar.isVisible()) {
            int barX = cx + cw - 12;
            int listTop = deviceSearchY + SEARCH_H + SEARCH_LIST_GAP;
            int listH = cy + ch - listTop;
            if (deviceScrollBar.handleClick(mx, my, barX, listTop, listH)) {
                return;
            }
        }

        // 设备筛选维度 & 显隐视图模式：均改为单个「轮换按钮」（仅设备 Tab）。
        // 左按钮按 FILTER_CYCLE 轮换（全部→发电→用电），右按钮按 VIS_CYCLE 轮换（仅显示→全部→仅隐藏）。
        if (currentTab == TAB_DEVICE) {
            if (filterCycleRect != null && mx >= filterCycleRect[0] && mx < filterCycleRect[0] + filterCycleRect[2]
                    && my >= filterCycleRect[1] && my < filterCycleRect[1] + filterCycleRect[3]) {
                int next = nextInCycle(deviceFilter, FILTER_CYCLE);
                if (next != deviceFilter) {
                    deviceFilter = next;
                    PowerGridSnapshot snap = RtsPowerGrid.get() == null ? null : RtsPowerGrid.get().currentGrid();
                    if (snap != null) refreshDeviceFilteredList(snap);
                }
                return;
            }
            if (visibilityCycleRect != null && mx >= visibilityCycleRect[0] && mx < visibilityCycleRect[0] + visibilityCycleRect[2]
                    && my >= visibilityCycleRect[1] && my < visibilityCycleRect[1] + visibilityCycleRect[3]) {
                int next = nextInCycle(deviceVisibilityMode, VIS_CYCLE);
                if (next != deviceVisibilityMode) {
                    deviceVisibilityMode = next;
                    PowerGridSnapshot snap = RtsPowerGrid.get() == null ? null : RtsPowerGrid.get().currentGrid();
                    if (snap != null) refreshDeviceFilteredList(snap);
                }
                return;
            }
        }

        // Tab 切换：切到电网总览时拉高面板；切回其他 Tab 时恢复面板高度。
        // 切到电网总览后的历史请求由 renderOverviewDashboard 内的 5 秒节流自动处理（首次立即请求）。
        for (int i = 0; i < tabIndex.size(); i++) {
            int[] r = tabRects.get(i);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                int target = tabIndex.get(i);
                if (currentTab != target) {
                    currentTab = target;
                    // 切换 Tab 时重置滚动条位置
                    memberScrollBar.setScroll(0);
                    deviceScrollBar.setScroll(0);
                    applyTabLayout();
                }
                return;
            }
        }
        // 粒度切换按钮（电网总览 Tab 内）
        for (int i = 0; i < granularityBtnRects.size(); i++) {
            int[] r = granularityBtnRects.get(i);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                if (overviewGranularity != i) {
                    overviewGranularity = i;
                    // 切换粒度时重置柱状图揭晓动画 + 粒度按钮淡入动画
                    barRevealAnim.snapTo(0f);
                    barRevealAnim.target(1f);
                    granuleFadeAnim.snapTo(0f);
                    granuleFadeAnim.target(1f);
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

    /** 按 currentTab 切换面板高度：电网总览 Tab 用更大的高度容纳仪表盘，其他 Tab 用标准高度。 */
    private void applyTabLayout() {
        int targetH = currentTab == TAB_OVERVIEW ? PANEL_H_OVERVIEW : PANEL_H;
        setWindowHeight(targetH);
        // 高度变化后重新对齐到屏幕内（避免溢出底部）
        clampWindowToScreen();
    }

    private void onRowAction(int action, Object arg) {
        switch (action) {
            case 0 -> {                 // 定位设备
                if (arg instanceof long[] p) {
                    RtsPowerGrid.get().locateDevice(p[0], p[1], p[2]);
                }
            }
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
            case 14 -> {                // 切换设备角色（用电 ↔ 发电）
                if (arg instanceof long[] p && p.length >= 4) {
                    RtsDeviceRole newRole = RtsDeviceRole.values()[(int) p[3]];
                    RtsPowerGrid.get().toggleDeviceRole(p[0], p[1], p[2], newRole);
                }
            }
            case 15 -> {                // 刷新输电塔（立即重扫供电范围覆盖）
                if (arg instanceof long[] p && p.length >= 3) {
                    RtsPowerGrid.get().refreshTower(p[0], p[1], p[2]);
                }
            }
            case 16 -> {                // 单台设备显隐开关（隐藏 ↔ 恢复显示）
                if (arg instanceof String key) {
                    if (!hiddenDeviceKeys.add(key)) {
                        hiddenDeviceKeys.remove(key);   // 已隐藏 → 恢复
                    }
                    // 显隐变化后刷新过滤列表（若当前处于显隐过滤模式）。
                    PowerGridSnapshot snap = RtsPowerGrid.get() == null ? null : RtsPowerGrid.get().currentGrid();
                    if (snap != null) refreshDeviceFilteredList(snap);
                }
            }
            case 17 -> {                // 折叠条「一键修改」：批量切换本组全部可切换设备（发电 ↔ 用电）
                if (arg instanceof String itemId && !itemId.isEmpty()) {
                    batchToggleGroup(itemId);
                }
            }
            default -> { }
        }
    }

    /**
     * 批量切换某个机器类型分组下所有<b>可切换角色</b>的设备（发电 ↔ 用电）。
     * <p>目标角色以组内第一台可切换设备的相反角色为准；组内全部可切换设备统一切到该目标角色，
     * 避免组内角色混杂导致方向不明确。</p>
     */
    private void batchToggleGroup(String itemId) {
        PowerGridSnapshot snap = RtsPowerGrid.get() == null ? null : RtsPowerGrid.get().currentGrid();
        if (snap == null) return;
        List<PowerDevice> group = new ArrayList<>();
        for (PowerDevice d : snap.devices()) {
            String key = (d.itemId() == null || d.itemId().isEmpty())
                    ? (d.label() == null ? "" : d.label()) : d.itemId();
            if (key.equals(itemId) && d.canToggleRole()) {
                group.add(d);
            }
        }
        if (group.isEmpty()) return;
        // 目标角色：第一台可切换设备的相反角色。
        RtsDeviceRole target = group.get(0).role() == RtsDeviceRole.GENERATOR
                ? RtsDeviceRole.CONSUMER : RtsDeviceRole.GENERATOR;
        for (PowerDevice d : group) {
            RtsPowerGrid.get().toggleDeviceRole(d.x(), d.y(), d.z(), target);
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
