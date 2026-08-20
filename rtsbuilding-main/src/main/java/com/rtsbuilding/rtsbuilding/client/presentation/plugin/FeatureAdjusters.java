package com.rtsbuilding.rtsbuilding.client.presentation.plugin;

import com.rtsbuilding.rtsbuilding.client.culling.RtsRayCylinderCullingState;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.uifw.window.overlay.OverlayContext;
import com.rtsbuilding.uifw.component.NumericInputBox;
import com.rtsbuilding.uifw.component.ScaleSliderComponent;
import com.rtsbuilding.uifw.component.ToggleSwitch;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.rtsbuild.shape.BuildShape;
import com.rtsbuilding.rtsbuilding.client.rtsbuild.shape.FillMode;
import com.rtsbuilding.uifw.animate.AnimFloat;
import com.rtsbuilding.uifw.animate.ColorAnimation;
import com.rtsbuilding.uifw.animate.Easing;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.state.FeatureAdjusterState;
import com.rtsbuilding.uifw.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/**
 * 右面板下嵌层插件：功能调节器（滑块 + 数值输入框 + 形状模式）。
 *
 * <p>与 {@link ContainerBinding} / {@link ItemGrid} / {@link WorkflowProgress} 同构：
 * 插件持有 {@link OverlayContext}（宿主嵌层），自行实现渲染与交互，宿主只做事件转发。</p>
 *
 * <p>根据当前启用的功能显示对应调节器，每个调节器由独立的圆角背景框包裹
 * （标题与控件都在框内），用于区分不同的调节器：</p>
 * <ul>
 *   <li><b>非单方块形状激活</b> → “形状模式：xxx”框：方块替换开关 +
 *       分段控件（体/圆柱/球=实心/空心/框架，墙/面=实心/框架，线=连接/断点）。</li>
 *   <li><b>漏斗（物品拾取）启用</b> → “漏斗吸取范围”框（1~5 格），
 *       调节后经 {@link FeatureAdjusterState} 同步到服务端（球心吸取实际半径）。</li>
 *   <li><b>连锁挖掘（Ultimine）启用</b> → “连锁挖掘数量”框（16~256），
 *       调节后作为连锁挖掘上限参与客户端预览与服务端启动请求。</li>
 * </ul>
 */
public final class FeatureAdjusters {

    /** 内容区左右内边距。 */
    private static final int PAD = 6;
    /** 调节器背景框之间的纵向间距。 */
    private static final int ROW_GAP = 4;
    /** 背景框圆角半径。 */
    private static final float BOX_RADIUS = 6.0f;
    /** 背景框内部水平内边距。 */
    private static final int BOX_PAD_H = 6;
    /** 背景框内部垂直内边距。 */
    private static final int BOX_PAD_V = 4;
    /** 标签行到按钮/滑块行的纵向间距。 */
    private static final int LABEL_TO_TRACK_GAP = 6;
    /** 滑块轨道高度（与 ScaleSliderComponent.TRACK_H 一致）。 */
    private static final int TRACK_H = 9;
    /** 数值输入框宽度。 */
    private static final int INPUT_W = 46;
    /** 形状模式分段控件高度。 */
    private static final int SEG_H = 14;
    /** 分段控件容器底色（半透明深蓝灰）。 */
    private static final int SEG_BG_COLOR = 0x552E3B4C;
    /** 选中高亮段相对段宽的内缩（胶囊留白）。 */
    private static final int SEG_INSET = 2;

    private final OverlayContext context;

    private final ScaleSliderComponent funnelSlider = new ScaleSliderComponent();
    private final ScaleSliderComponent ultimineSlider = new ScaleSliderComponent();
    /** 射线圆柱剔除距离滑块（右面板下嵌层，剔除开启时显示）。 */
    private final ScaleSliderComponent cullingDistanceSlider = new ScaleSliderComponent();
    /** 射线圆柱剔除半径滑块（右面板下嵌层，剔除开启时显示）。 */
    private final ScaleSliderComponent cullingRadiusSlider = new ScaleSliderComponent();
    /** 漏斗半径值输入框（字段持例，编辑状态跨帧保留）。 */
    private final NumericInputBox funnelInput = createFunnelInput();
    /** 连锁数量值输入框（字段持例，编辑状态跨帧保留）。 */
    private final NumericInputBox ultimineInput = createUltimineInput();
    /** 剔除距离输入框（字段持例，编辑状态跨帧保留）。 */
    private final NumericInputBox cullingDistanceInput = createCullingDistanceInput();
    /** 剔除半径输入框（字段持例，编辑状态跨帧保留）。 */
    private final NumericInputBox cullingRadiusInput = createCullingRadiusInput();

    /** 当前可见的调节器行（渲染时重建，供鼠标/键盘事件复用命中位置）。 */
    private final List<Row> rows = new ArrayList<>(4);

    /** 三个形状模式段的悬停动画。 */
    private final AnimFloat[] fillBtnHover = {
            AnimFloat.hover(), AnimFloat.hover(), AnimFloat.hover()
    };

    /** 分段控件选中高亮的滑动动画（0 ~ 段数-1，切换时平滑滑过）。 */
    private final AnimFloat fillModeSlide = AnimFloat.of(0f, 180L, Easing.EASE_OUT_QUAD);

    /** 三个形状模式段的命中矩形（渲染时更新；未显示时为 0 宽）。 */
    private final int[][] fillBtnRects = new int[3][4];

    /** 方块替换开关（设置面板 ToggleSwitch 风格，所有形状共享）。 */
    private final ToggleSwitch replaceToggle = new ToggleSwitch();

    /** 方块替换开关按钮的命中矩形（渲染时更新；未显示时为 0 宽）。 */
    private final int[] replaceBtnRect = new int[4];

    /** 蓝图放置覆盖开关（右面板蓝图调节器，独立于建造形状的替换开关）。 */
    private final ToggleSwitch blueprintOverwriteToggle = new ToggleSwitch();

    /** 蓝图放置覆盖开关的命中矩形（渲染时更新；未显示时为 0 宽）。 */
    private final int[] blueprintOverwriteRect = new int[4];

    /** 蓝图 X/Y/Z 三轴偏移输入框（字段持例，编辑状态跨帧保留）。 */
    private final NumericInputBox[] blueprintAxisInputs = {
            createBlueprintAxisInput(0), createBlueprintAxisInput(1), createBlueprintAxisInput(2)
    };
    /** 三轴输入框的非编辑态显示文本（读取屏幕当前偏移）。 */
    private final List<Supplier<String>> blueprintAxisTexts = new ArrayList<>(3);
    /** 三轴输入框的渲染位置（渲染时更新；未显示时宽为 0）。 */
    private final int[][] blueprintAxisRects = new int[3][4];
    /** 蓝图放置调节器的命中区域（渲染时更新）；用作轴输入框/覆盖开关的后备。 */
    private boolean blueprintBoxVisible;

    public FeatureAdjusters(OverlayContext context) {
        this.context = context;
    }

    /** lang 查询辅助（带参数）。 */
    private static String t(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    // ==================== 渲染 ====================

    public void renderContent(GuiGraphics g) {
        if (context.getWidth() <= 0 || context.getHeight() <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        BuilderScreen screen = mc.screen instanceof BuilderScreen bs ? bs : null;

        boolean showFunnel = screen != null && screen.isItemPickupActive();
        boolean showUltimine = screen != null && screen.isUltimineActive();
        boolean showFillMode = screen != null && screen.isShapeAdjusterActive();
        // 蓝图放置模式激活时显示「蓝图放置」调节器（覆盖开关 + XYZ 三轴偏移）
        boolean showBlueprint = screen != null && screen.isBlueprintPlacementActive();

        int cx = context.getX() + PAD;
        int cw = context.getWidth() - PAD * 2;
        if (cw <= 0) return;
        int cy = context.getY() + PAD;
        int textColor = ThemeManager.getTextColor();
        int mx = context.getLastMouseX();
        int my = context.getLastMouseY();
        int lineHeight = mc.font != null ? mc.font.lineHeight : 9;
        // 背景框行高：框内垂直内边距 ×2 + 标题行 + 标签→滑块间距 + 轨道高
        int rowH = BOX_PAD_V * 2 + lineHeight + LABEL_TO_TRACK_GAP + TRACK_H;
        // 形状/单方块调节框行高：标题行 +（替换开关行，仅建造侧）+（分段控件行，仅非单方块形状）
        boolean isConstruction = screen != null && screen.isConstructionSelected();
        int fillRowH = BOX_PAD_V * 2 + lineHeight;
        if (isConstruction) {
            fillRowH += ROW_GAP + SEG_H;
        }
        if (screen != null && screen.getActiveBuildShape() != null) {
            fillRowH += ROW_GAP + SEG_H;
        }
        // 蓝图调节框行高：标题行 + 覆盖开关行 + 三轴输入框行
        int blueprintRowH = BOX_PAD_V * 2 + lineHeight + ROW_GAP + SEG_H + ROW_GAP + TRACK_H;

        rows.clear();
        resetFillBtnRects();
        resetBlueprintRects();

        // 形状模式（非单方块形状激活时显示）
        if (showFillMode) {
            renderFillRowBox(g, cx, cy, cw, fillRowH, mx, my, textColor, lineHeight);
            cy += fillRowH + ROW_GAP;
        }

        // 蓝图放置调节器（蓝图放置模式激活时显示）
        if (showBlueprint) {
            renderBlueprintRowBox(g, cx, cy, cw, blueprintRowH, mx, my, textColor, lineHeight);
            cy += blueprintRowH + ROW_GAP;
        }

        if (showFunnel) {
            Row row = new Row(funnelSlider,
                    FeatureAdjusterState.MIN_FUNNEL_RADIUS, FeatureAdjusterState.MAX_FUNNEL_RADIUS,
                    0.1,
                    FeatureAdjusterState::setFunnelRadius,
                    funnelInput,
                    () -> String.format(Locale.ROOT, "%.1f", FeatureAdjusterState.getFunnelRadius()));
            renderRowBox(g, cx, cy, cw, rowH, row, t("ui.rtsbuilding.adjuster.funnel_radius"),
                    FeatureAdjusterState.getFunnelRadius(), mx, my, textColor, lineHeight);
            rows.add(row);
            cy += rowH + ROW_GAP;
        }

        if (showUltimine) {
            Row row = new Row(ultimineSlider,
                    FeatureAdjusterState.MIN_ULTIMINE_LIMIT, FeatureAdjusterState.MAX_ULTIMINE_LIMIT,
                    1.0,
                    value -> FeatureAdjusterState.setUltimineLimit((int) value),
                    ultimineInput,
                    () -> String.valueOf(FeatureAdjusterState.getUltimineLimit()));
            renderRowBox(g, cx, cy, cw, rowH, row, t("ui.rtsbuilding.adjuster.ultimine_limit"),
                    FeatureAdjusterState.getUltimineLimit(), mx, my, textColor, lineHeight);
            rows.add(row);
            cy += rowH + ROW_GAP;
        }

        // 射线圆柱剔除开启时显示调节器：一个复合框内并排距离 + 圆柱半径两个滑块
        //（纯客户端视觉参数）
        boolean showCulling = screen != null && screen.isCameraActive()
                && RtsRayCylinderCullingState.isEnabled();
        if (showCulling) {
            Row distanceRow = new Row(cullingDistanceSlider,
                    RtsRayCylinderCullingState.MIN_DISTANCE,
                    RtsRayCylinderCullingState.MAX_DISTANCE,
                    1.0,
                    RtsRayCylinderCullingState::setDistance,
                    cullingDistanceInput,
                    () -> String.valueOf((int) RtsRayCylinderCullingState.getDistance()));
            Row radiusRow = new Row(cullingRadiusSlider,
                    RtsRayCylinderCullingState.MIN_RADIUS,
                    RtsRayCylinderCullingState.MAX_RADIUS,
                    1.0,
                    RtsRayCylinderCullingState::setRadius,
                    cullingRadiusInput,
                    () -> String.valueOf((int) RtsRayCylinderCullingState.getRadius()));
            int cullLineH = Math.max(lineHeight, TRACK_H);
            int cullingRowH = BOX_PAD_V * 2 + lineHeight + ROW_GAP + cullLineH + ROW_GAP + cullLineH;
            renderCullingRowBox(g, cx, cy, cw, cullingRowH, distanceRow, radiusRow,
                    t("ui.rtsbuilding.adjuster.culling"),
                    t("ui.rtsbuilding.adjuster.culling_distance"),
                    t("ui.rtsbuilding.adjuster.culling_radius"),
                    mx, my, textColor, lineHeight);
            rows.add(distanceRow);
            rows.add(radiusRow);
            cy += cullingRowH + ROW_GAP;
        }

        // 无任何调节器（形状调节器 + 漏斗 + 连锁均无）时：半透明占位提示
        //（与 GridRenderer 搜索框 placeholder 风格一致，居中显示在内容区）
        if (!showFillMode && rows.isEmpty()) {
            String placeholder = t("ui.rtsbuilding.adjuster.none");
            int placeholderColor = (textColor & 0xFFFFFF) | 0x60000000;
            int pw = mc.font.width(placeholder);
            int availH = context.getHeight() - PAD * 2;
            TextRenderer.draw(g, placeholder,
                    cx + (cw - pw) / 2,
                    cy + Math.max(0, (availH - mc.font.lineHeight) / 2),
                    placeholderColor);
        }
    }

    /**
     * 渲染“方块剔除”复合调节框：单个背景框内自上而下为
     * <ol>
     *   <li><b>标题行</b>：左对齐“方块剔除”。</li>
     *   <li><b>距离行</b>：小标签「剔除距离」+ 滑块 + 数值输入框同排。</li>
     *   <li><b>半径行</b>：小标签「圆柱半径」+ 滑块 + 数值输入框同排。</li>
     * </ol>
     * 距离/半径滑块各用一个 {@link Row} 承载命中矩形与回调，交互逻辑复用
     * {@link #mouseClicked} 的 rows 遍历。
     */
    private void renderCullingRowBox(GuiGraphics g, int boxX, int boxY, int boxW, int boxH,
                                     Row distRow, Row radiusRow, String title,
                                     String distLabel, String radiusLabel,
                                     int mx, int my, int textColor, int lineHeight) {
        Minecraft mc = Minecraft.getInstance();
        // 背景框（dark.png 的 p6 色填充 + 边框）
        SdfRenderer.drawBorderedRoundedRect(g, boxX, boxY, boxW, boxH, BOX_RADIUS,
                UiPalette.border(), UiPalette.p6(), 1);
        // 标题行（左对齐）
        TextRenderer.draw(g, title, boxX + BOX_PAD_H, boxY + BOX_PAD_V, textColor);
        int lineH = Math.max(lineHeight, TRACK_H);
        int contentY = boxY + BOX_PAD_V + lineHeight + ROW_GAP;
        renderCullingRowLine(g, distLabel, distRow, boxX, boxW, contentY,
                RtsRayCylinderCullingState.getDistance(), mx, my, textColor, lineHeight);
        renderCullingRowLine(g, radiusLabel, radiusRow, boxX, boxW, contentY + lineH + ROW_GAP,
                RtsRayCylinderCullingState.getRadius(), mx, my, textColor, lineHeight);
    }

    /** 剔除复合框内的一行内容：小标签（左）+ 滑块（中）+ 数值输入框（右），垂直居中。 */
    private void renderCullingRowLine(GuiGraphics g, String label, Row row,
                                      int boxX, int boxW, int y, double value,
                                      int mx, int my, int textColor, int lineHeight) {
        Minecraft mc = Minecraft.getInstance();
        int lineH = Math.max(lineHeight, TRACK_H);
        // 数值输入框（右侧，行内垂直居中）
        row.inputW = INPUT_W;
        row.inputX = boxX + boxW - BOX_PAD_H - INPUT_W;
        row.inputY = y + (lineH - NumericInputBox.INPUT_H) / 2;
        row.inputBox.render(g, mx, my, row.inputX, row.inputY, row.inputW, row.displayText.get());
        // 小标签（左）+ 滑块轨道（标签与输入框之间）
        int labelW = mc.font.width(label);
        row.trackX = boxX + BOX_PAD_H + labelW + 6;
        row.trackW = Math.max(1, row.inputX - row.trackX - 8);
        row.trackY = y + (lineH - TRACK_H) / 2;
        TextRenderer.draw(g, label, boxX + BOX_PAD_H, y + (lineH - lineHeight) / 2,
                (textColor & 0xFFFFFF) | 0x60000000);
        row.slider.render(g, mx, my, row.trackX, row.trackY, row.trackW,
                row.min, row.max, value);
    }

    /** 漏斗半径输入框：提交解析 double 并收敛到合法范围。 */
    private static NumericInputBox createFunnelInput() {
        NumericInputBox box = new NumericInputBox();
        box.setOnCommit(text -> {
            try {
                FeatureAdjusterState.setFunnelRadius(Double.parseDouble(text));
            } catch (NumberFormatException ignored) {
                // 无效输入忽略，保持原值
            }
        });
        return box;
    }

    /** 连锁数量输入框：提交解析 int 并收敛到合法范围。 */
    private static NumericInputBox createUltimineInput() {
        NumericInputBox box = new NumericInputBox();
        box.setOnCommit(text -> {
            try {
                FeatureAdjusterState.setUltimineLimit(Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                // 无效输入忽略，保持原值
            }
        });
        return box;
    }

    /** 剔除距离输入框：提交解析 double 并收敛到合法范围。 */
    private static NumericInputBox createCullingDistanceInput() {
        NumericInputBox box = new NumericInputBox();
        box.setOnCommit(text -> {
            try {
                RtsRayCylinderCullingState
                        .setDistance(Double.parseDouble(text));
            } catch (NumberFormatException ignored) {
                // 无效输入忽略，保持原值
            }
        });
        return box;
    }

    /** 剔除半径输入框：提交解析 double 并收敛到合法范围。 */
    private static NumericInputBox createCullingRadiusInput() {
        NumericInputBox box = new NumericInputBox();
        box.setOnCommit(text -> {
            try {
                RtsRayCylinderCullingState
                        .setRadius(Double.parseDouble(text));
            } catch (NumberFormatException ignored) {
                // 无效输入忽略，保持原值
            }
        });
        return box;
    }

    /**
     * 渲染单个调节器背景框：圆角半透明底 + 边框，标题（左侧）、数值输入框（右侧）
     * 与滑块（下方）都在框内。同时把命中位置记录到 {@link Row} 供交互复用。
     */
    private void renderRowBox(GuiGraphics g, int boxX, int boxY, int boxW, int boxH,
                              Row row, String label, double value, int mx, int my,
                              int textColor, int lineHeight) {
        Minecraft mc = Minecraft.getInstance();
        // 背景框（dark.png 的 p6 色填充 + 边框）
        SdfRenderer.drawBorderedRoundedRect(g, boxX, boxY, boxW, boxH, BOX_RADIUS,
                UiPalette.border(), UiPalette.p6(), 1);
        // 数值输入框（右侧，与标题行垂直居中）
        row.inputW = INPUT_W;
        row.inputX = boxX + boxW - BOX_PAD_H - INPUT_W;
        row.inputY = boxY + BOX_PAD_V + (lineHeight - NumericInputBox.INPUT_H) / 2;
        row.inputBox.render(g, mx, my, row.inputX, row.inputY, row.inputW, row.displayText.get());
        // 标题（左侧，超宽时裁剪避免与输入框重叠）
        int labelMaxW = row.inputX - (boxX + BOX_PAD_H) - 4;
        String clipped = TextRenderer.trimToWidth(mc.font, label, Math.max(8, labelMaxW));
        TextRenderer.draw(g, clipped, boxX + BOX_PAD_H, boxY + BOX_PAD_V, textColor);
        // 滑块轨道（框内标题下方）
        row.trackX = boxX + BOX_PAD_H;
        row.trackY = boxY + BOX_PAD_V + lineHeight + LABEL_TO_TRACK_GAP;
        row.trackW = boxW - BOX_PAD_H * 2;
        row.slider.render(g, mx, my, row.trackX, row.trackY, row.trackW,
                row.min, row.max, value);
    }

    /**
     * 渲染形状/单方块调节框：背景框内留足空间——
     * <ol>
     *   <li><b>标题行</b>：左对齐“形状模式：线 / 单方块”等。</li>
     *   <li><b>替换开关行</b>（仅建造侧）：左对齐「替换」标签 + {@link ToggleSwitch} 开关
     *       （所有建造形状 + 单方块共享；破坏模式不需要替换，不显示）。</li>
     *   <li><b>分段控件行</b>（仅非单方块形状）：全宽圆角胶囊内等宽分段（选中段高亮 + 滑动动画）。</li>
     * </ol>
     * 命中矩形记录到 {@link #fillBtnRects} / {@link #replaceBtnRect} 供点击复用。
     */
    private void renderFillRowBox(GuiGraphics g, int boxX, int boxY, int boxW, int boxH,
                                  int mx, int my, int textColor, int lineHeight) {
        Minecraft mc = Minecraft.getInstance();
        // 背景框（dark.png 的 p6 色填充 + 边框）
        SdfRenderer.drawBorderedRoundedRect(g, boxX, boxY, boxW, boxH, BOX_RADIUS,
                UiPalette.border(), UiPalette.p6(), 1);

        BuilderScreen screen = mc.screen instanceof BuilderScreen bs ? bs : null;
        BuildShape shape = screen != null ? screen.getActiveBuildShape() : null;
        boolean isConstruction = screen != null && screen.isConstructionSelected();

        RtsClientKernel kernel = RtsClientKernel.get();
        if (kernel == null || kernel.renderPipeline() == null) return;
        var lineBrush = kernel.renderPipeline().lineBrush;

        // 行1：标题（左对齐）
        int titleY = boxY + BOX_PAD_V;
        String title = shape != null ? t("ui.rtsbuilding.adjuster.shape_mode") + shape.label() : t("ui.rtsbuilding.adjuster.shape_mode") + t("ui.rtsbuilding.adjuster.single_block");
        TextRenderer.draw(g, title, boxX + BOX_PAD_H, titleY, textColor);
        int nextY = titleY + lineHeight;

        // 行2：替换开关（仅建造侧；破坏模式不提供替换）
        if (isConstruction) {
            int replaceRowY = nextY + ROW_GAP;
            boolean replaceOn = lineBrush.isReplaceEnabled();
            String replaceLabel = t("ui.rtsbuilding.adjuster.replace");
            TextRenderer.draw(g, replaceLabel,
                    boxX + BOX_PAD_H,
                    replaceRowY + (SEG_H - mc.font.lineHeight) / 2,
                    (textColor & 0xFFFFFF) | 0x60000000);
            int replaceBtnW = replaceToggle.getWidth();
            int replaceBtnH = replaceToggle.getHeight();
            int replaceBtnX = boxX + BOX_PAD_H + mc.font.width(replaceLabel) + 6;
            int replaceBtnY = replaceRowY + (SEG_H - replaceBtnH) / 2;
            replaceToggle.render(g, replaceBtnX, replaceBtnY, replaceOn);
            replaceBtnRect[0] = replaceBtnX;
            replaceBtnRect[1] = replaceBtnY;
            replaceBtnRect[2] = replaceBtnW;
            replaceBtnRect[3] = replaceBtnH;
            nextY = replaceRowY + SEG_H;
        }

        // 行3：分段控件（仅非单方块形状）
        if (shape == null) {
            return;
        }
        FillMode current = lineBrush.getFillModeFor(shape);
        FillMode[] modes = FillMode.modesFor(shape);
        int n = modes.length;

        int segX = boxX + BOX_PAD_H;
        int segW = boxW - BOX_PAD_H * 2;
        int segY = nextY + ROW_GAP;
        int segH = SEG_H;
        int perSeg = Math.max(1, segW / n);

        // 选中索引 → 滑动动画
        int selIdx = 0;
        for (int i = 0; i < n; i++) {
            if (modes[i] == current) {
                selIdx = i;
                break;
            }
        }
        fillModeSlide.target(selIdx);
        float slide = Mth.clamp(fillModeSlide.get(), 0f, n - 1f);

        // 容器底（圆角胶囊）
        SdfRenderer.drawPill(g, segX, segY, segW, segH, SEG_BG_COLOR);

        // 选中高亮段（圆角胶囊，滑动动画跟随）
        int inset = SEG_INSET;
        int selW = Math.max(1, perSeg - inset * 2);
        int selH = segH - inset * 2;
        int selX = segX + inset + Math.round(slide * perSeg);
        SdfRenderer.drawPill(g, selX, segY + inset, selW, selH, UiPalette.toggleOn());

        // 各段文字 + 悬停渐亮 + 命中矩形
        int hoverText = ThemeManager.getHoverTextColor();
        int baseText = ThemeManager.getTextColor();
        for (int i = 0; i < n; i++) {
            int tx = segX + i * perSeg;
            boolean selected = i == selIdx;
            boolean hovering = mx >= tx && mx < tx + perSeg && my >= segY && my < segY + segH;
            float t = fillBtnHover[i].track(hovering);
            int labelColor = selected ? hoverText
                    : ColorAnimation.lerpRGB(baseText, hoverText, t);
            String label = modes[i].label();
            int textW = mc.font.width(label);
            TextRenderer.draw(g, label, tx + (perSeg - textW) / 2,
                    segY + (segH - mc.font.lineHeight) / 2, labelColor);
            fillBtnRects[i][0] = tx;
            fillBtnRects[i][1] = segY;
            fillBtnRects[i][2] = perSeg;
            fillBtnRects[i][3] = segH;
        }
    }

    /** 重置形状模式/替换按钮命中矩形（未显示时点击无效）。 */
    private void resetFillBtnRects() {
        for (int[] rect : fillBtnRects) {
            rect[2] = 0;
            rect[3] = 0;
        }
        replaceBtnRect[2] = 0;
        replaceBtnRect[3] = 0;
    }

    /** 重置蓝图调节器命中矩形（未显示时点击无效）。 */
    private void resetBlueprintRects() {
        blueprintOverwriteRect[2] = 0;
        blueprintOverwriteRect[3] = 0;
        for (int[] rect : blueprintAxisRects) {
            rect[2] = 0;
            rect[3] = 0;
        }
        blueprintBoxVisible = false;
    }

    /**
     * 渲染蓝图放置调节框：
     * <ol>
     *   <li><b>标题行</b>：左对齐"蓝图放置"。</li>
     *   <li><b>覆盖开关行</b>：左对齐「覆盖」标签 + {@link ToggleSwitch}
     *       （开启后允许替换目标位置已有方块，发送服务端参与放置判定）。</li>
     *   <li><b>三轴偏移行</b>：X / Y / Z 三个 {@link NumericInputBox} 并排，
     *       提交后经 {@link BuilderScreen#setBlueprintPlacementOffset} 写回当前偏移（±64 格钳位）。</li>
     * </ol>
     * 命中矩形记录到 {@link #blueprintOverwriteRect} / {@link #blueprintAxisRects} 供点击复用。
     */
    private void renderBlueprintRowBox(GuiGraphics g, int boxX, int boxY, int boxW, int boxH,
                                       int mx, int my, int textColor, int lineHeight) {
        Minecraft mc = Minecraft.getInstance();
        BuilderScreen screen = mc.screen instanceof BuilderScreen bs ? bs : null;
        if (screen == null) return;
        // 背景框
        SdfRenderer.drawBorderedRoundedRect(g, boxX, boxY, boxW, boxH, BOX_RADIUS,
                UiPalette.border(), UiPalette.p6(), 1);

        // 行1：标题（左对齐）
        int titleY = boxY + BOX_PAD_V;
        TextRenderer.draw(g, t("ui.rtsbuilding.adjuster.blueprint"), boxX + BOX_PAD_H, titleY, textColor);
        int nextY = titleY + lineHeight;

        // 行2：覆盖开关（左对齐标签 + ToggleSwitch）
        int overwriteRowY = nextY + ROW_GAP;
        boolean overwriteOn = screen.isBlueprintOverwriteEnabled();
        String overwriteLabel = t("ui.rtsbuilding.adjuster.blueprint_overwrite");
        TextRenderer.draw(g, overwriteLabel,
                boxX + BOX_PAD_H,
                overwriteRowY + (SEG_H - mc.font.lineHeight) / 2,
                (textColor & 0xFFFFFF) | 0x60000000);
        int overwriteW = blueprintOverwriteToggle.getWidth();
        int overwriteH = blueprintOverwriteToggle.getHeight();
        int overwriteX = boxX + BOX_PAD_H + mc.font.width(overwriteLabel) + 6;
        int overwriteY = overwriteRowY + (SEG_H - overwriteH) / 2;
        blueprintOverwriteToggle.render(g, overwriteX, overwriteY, overwriteOn);
        blueprintOverwriteRect[0] = overwriteX;
        blueprintOverwriteRect[1] = overwriteY;
        blueprintOverwriteRect[2] = overwriteW;
        blueprintOverwriteRect[3] = overwriteH;

        // 行3：三轴偏移输入框（X / Y / Z 并排，底部对齐轨道行）
        int axisRowY = overwriteRowY + SEG_H + ROW_GAP;
        String[] axisLabels = {
                t("ui.rtsbuilding.adjuster.blueprint_axis_x"),
                t("ui.rtsbuilding.adjuster.blueprint_axis_y"),
                t("ui.rtsbuilding.adjuster.blueprint_axis_z")
        };
        int axisGap = 8;
        int axisInputW = 40;
        int axisY = axisRowY + (TRACK_H - NumericInputBox.INPUT_H) / 2;
        int axX = boxX + BOX_PAD_H;
        blueprintAxisTexts.clear();
        for (int i = 0; i < 3; i++) {
            TextRenderer.draw(g, axisLabels[i], axX, axisY + (NumericInputBox.INPUT_H - mc.font.lineHeight) / 2,
                    (textColor & 0xFFFFFF) | 0x60000000);
            int inputX = axX + mc.font.width(axisLabels[i]) + 2;
            blueprintAxisTexts.add(axisTextSupplier(screen, i));
            blueprintAxisInputs[i].render(g, mx, my, inputX, axisY, axisInputW, blueprintAxisTexts.get(i).get());
            blueprintAxisRects[i][0] = inputX;
            blueprintAxisRects[i][1] = axisY;
            blueprintAxisRects[i][2] = axisInputW;
            blueprintAxisRects[i][3] = NumericInputBox.INPUT_H;
            axX = inputX + axisInputW + axisGap;
        }
        blueprintBoxVisible = true;
    }

    /** 蓝图某轴偏移的非编辑态显示文本（每帧读取屏幕当前偏移）。 */
    private static Supplier<String> axisTextSupplier(BuilderScreen screen, int axis) {
        switch (axis) {
            case 0:
                return () -> String.valueOf(screen.getBlueprintPlacementOffset().getX());
            case 1:
                return () -> String.valueOf(screen.getBlueprintPlacementOffset().getY());
            default:
                return () -> String.valueOf(screen.getBlueprintPlacementOffset().getZ());
        }
    }

    /** 蓝图轴坐标输入框：提交解析 int，覆盖当前偏移对应轴后写回屏幕。 */
    private static NumericInputBox createBlueprintAxisInput(int axis) {
        NumericInputBox box = new NumericInputBox();
        box.setOnCommit(text -> {
            BuilderScreen activeScreen = Minecraft.getInstance().screen instanceof BuilderScreen bs ? bs : null;
            if (activeScreen == null) return;
            try {
                int value = Integer.parseInt(text);
                net.minecraft.core.BlockPos off = activeScreen.getBlueprintPlacementOffset();
                int c = net.minecraft.util.Mth.clamp(value, -64, 64);
                switch (axis) {
                    case 0:
                        activeScreen.setBlueprintPlacementOffset(c, off.getY(), off.getZ());
                        break;
                    case 1:
                        activeScreen.setBlueprintPlacementOffset(off.getX(), c, off.getZ());
                        break;
                    default:
                        activeScreen.setBlueprintPlacementOffset(off.getX(), off.getY(), c);
                        break;
                }
            } catch (NumberFormatException ignored) {
                // 无效输入忽略，保持原值
            }
        });
        return box;
    }

    // ==================== 交互 ====================

    /** 提交所有常规调节器行（漏斗/连锁）正在编辑的输入框。 */
    private void commitEditingRows() {
        for (Row row : rows) {
            row.inputBox.applyIfEditing();
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        // 蓝图调节器：先尝试三轴输入框（命中则进入编辑态，提交其他正在编辑的输入框）
        for (int i = 0; i < 3; i++) {
            int[] r = blueprintAxisRects[i];
            NumericInputBox box = blueprintAxisInputs[i];
            if (r[2] > 0 && box.handleClick(mouseX, mouseY, r[0], r[1], r[2],
                    blueprintAxisTexts.get(i).get())) {
                for (int j = 0; j < 3; j++) {
                    if (j != i) {
                        blueprintAxisInputs[j].applyIfEditing();
                    }
                }
                commitEditingRows();
                return true;
            }
        }
        // 未命中蓝图输入框：提交所有编辑中的输入框（点击输入框外 = 确认）
        for (int i = 0; i < 3; i++) {
            blueprintAxisInputs[i].applyIfEditing();
        }
        // 蓝图覆盖开关
        if (blueprintOverwriteRect[2] > 0
                && mouseX >= blueprintOverwriteRect[0] && mouseX < blueprintOverwriteRect[0] + blueprintOverwriteRect[2]
                && mouseY >= blueprintOverwriteRect[1] && mouseY < blueprintOverwriteRect[1] + blueprintOverwriteRect[3]) {
            BuilderScreen activeScreen = Minecraft.getInstance().screen instanceof BuilderScreen bs ? bs : null;
            if (activeScreen != null) {
                activeScreen.setBlueprintOverwriteEnabled(!activeScreen.isBlueprintOverwriteEnabled());
            }
            return true;
        }
        // 先尝试值输入框：命中则进入编辑态（提交其他正在编辑的输入框）
        for (Row row : rows) {
            if (row.inputBox.handleClick(mouseX, mouseY, row.inputX, row.inputY, row.inputW,
                    row.displayText.get())) {
                for (Row other : rows) {
                    if (other != row) {
                        other.inputBox.applyIfEditing();
                    }
                }
                return true;
            }
        }
        // 未命中输入框：提交所有编辑中的输入框（点击输入框外 = 确认）
        for (Row row : rows) {
            row.inputBox.applyIfEditing();
        }
        // 方块替换开关（分段控件上方，所有形状共享）
        if (replaceBtnRect[2] > 0 && mouseX >= replaceBtnRect[0] && mouseX < replaceBtnRect[0] + replaceBtnRect[2]
                && mouseY >= replaceBtnRect[1] && mouseY < replaceBtnRect[1] + replaceBtnRect[3]) {
            RtsClientKernel.get().renderPipeline().lineBrush.toggleReplaceBlocks();
            return true;
        }
        // 形状模式按钮：按当前形状支持的模式子集直接选择（写入该形状的模式）
        BuilderScreen activeScreen = Minecraft.getInstance().screen instanceof BuilderScreen bs ? bs : null;
        BuildShape activeShape = activeScreen != null ? activeScreen.getActiveBuildShape() : null;
        if (activeShape != null) {
            FillMode[] modes = FillMode.modesFor(activeShape);
            for (int i = 0; i < modes.length && i < fillBtnRects.length; i++) {
                int[] r = fillBtnRects[i];
                if (r[2] > 0 && mouseX >= r[0] && mouseX < r[0] + r[2]
                        && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                    RtsClientKernel.get().renderPipeline().lineBrush.setFillModeFor(activeShape, modes[i]);
                    return true;
                }
            }
        }
        // 再处理滑块
        for (Row row : rows) {
            Double newVal = row.slider.handleClick(mouseX, mouseY,
                    row.trackX, row.trackY, row.trackW, row.min, row.max);
            if (newVal != null) {
                row.apply.accept(newVal);
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) return false;
        for (Row row : rows) {
            if (row.slider.isDragging() && row.trackW > 0) {
                double val = row.slider.handleDrag(mouseX, row.trackX, row.trackW, row.min, row.max);
                row.apply.accept(val);
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean any = false;
        for (Row row : rows) {
            if (row.slider.isDragging()) {
                row.slider.endDrag();
                any = true;
            }
        }
        return any;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (Row row : rows) {
            Double newVal = row.slider.handleScroll(mouseX, mouseY, scrollY,
                    row.trackX, row.trackY, row.trackW, row.min, row.max, row.scrollStep);
            if (newVal != null) {
                row.apply.accept(newVal);
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 蓝图轴输入框优先（编辑态跨帧保留）
        for (NumericInputBox box : blueprintAxisInputs) {
            if (box.handleKeyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        for (Row row : rows) {
            if (row.inputBox.handleKeyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        // 蓝图轴输入框优先
        for (NumericInputBox box : blueprintAxisInputs) {
            if (box.handleCharTyped(codePoint, modifiers)) {
                return true;
            }
        }
        for (Row row : rows) {
            if (row.inputBox.handleCharTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return false;
    }

    // ==================== 内部结构 ====================

    /**
     * 单个调节器行的渲染状态与取值/提交回调。
     */
    private static final class Row {
        final ScaleSliderComponent slider;
        final double min;
        final double max;
        /** 滚轮步长：小数调节（漏斗 0.1）与整数调节（连锁 1）共用同一组件。 */
        final double scrollStep;
        final DoubleConsumer apply;
        final NumericInputBox inputBox;
        /** 非编辑态在输入框中显示的数值文本（动态生成）。 */
        final Supplier<String> displayText;
        int trackX;
        int trackY;
        int trackW;
        int inputX;
        int inputY;
        int inputW;

        Row(ScaleSliderComponent slider, double min, double max, double scrollStep,
            DoubleConsumer apply, NumericInputBox inputBox, Supplier<String> displayText) {
            this.slider = slider;
            this.min = min;
            this.max = max;
            this.scrollStep = scrollStep;
            this.apply = apply;
            this.inputBox = inputBox;
            this.displayText = displayText;
        }
    }
}
