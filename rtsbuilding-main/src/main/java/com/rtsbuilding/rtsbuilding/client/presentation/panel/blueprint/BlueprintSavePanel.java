package com.rtsbuilding.rtsbuilding.client.presentation.panel.blueprint;

import com.rtsbuilding.rtsbuilding.client.blueprint.BlueprintLocalStore;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;
import com.rtsbuilding.uifw.layout.FlexLayout;
import com.rtsbuilding.uifw.layout.UiBox;
import com.rtsbuilding.uifw.layout.UiRect;
import com.rtsbuilding.uifw.window.window.UiPanel;
import com.rtsbuilding.uifw.component.TextInputBox;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.nio.file.Path;

import static com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreenConstants.TOP_H;

/**
 * 蓝图保存对话框 —— 蓝图模式框选完成后按回车弹出，
 * 输入蓝图名称并将框选区域捕获保存为本地 .nbt 蓝图文件。
 * <p>
 * 流程：框选 COMPLETE → 按回车打开 → 输入名称 → 确认 →
 * {@link BlueprintLocalStore#save} 捕获世界方块写入本地蓝图目录，
 * 成功后关闭并清空框选，派发 {@link StateEvent.BlueprintCaptureComplete}。
 */
public final class BlueprintSavePanel extends UiPanel {

    private static final Logger LOG = LoggerFactory.getLogger("RTS-BlueprintSave");

    /** 面板默认尺寸。 */
    private static final int DEFAULT_W = 260;
    private static final int DEFAULT_H = 118;
    /** 按钮尺寸与间距。 */
    private static final int BTN_W = 70;
    private static final int BTN_H = 16;
    private static final int BTN_GAP = 10;

    /** 蓝图名称输入框。 */
    private final TextInputBox nameInput = new TextInputBox();
    /** 框选区域两个对角点（面板打开时固定，避免关闭期间框选状态变化影响保存范围）。 */
    private BlockPos boxMin;
    private BlockPos boxMax;

    /** 保存结果提示（成功/失败），面板关闭时清除。 */
    private Component statusMessage;
    private boolean statusIsError;

    @Override
    public void init(com.rtsbuilding.uifw.window.api.UiPanelHost screen) {
        super.init(screen);
        this.resizable = false;
        // 输入框回车提交 → 直接保存蓝图
        this.nameInput.setOnCommit(text -> confirmSave());
    }

    /**
     * 打开保存对话框并预填默认名称、固定框选区域。
     *
     * @param min 框选区域最小角点（开区间 [min, max)，含 min）
     * @param max 框选区域最大角点（开区间 [min, max)，不含 max）
     */
    public void openForSelection(BlockPos min, BlockPos max) {
        this.boxMin = min;
        // BoxSelector 的 max 为开区间边界，转换为闭区间端点供捕获使用
        this.boxMax = max == null ? null : max.offset(-1, -1, -1);
        this.statusMessage = null;
        this.statusIsError = false;
        this.nameInput.beginEdit(defaultName(min, max));
        setOpen(true);
        markBroughtToFront();
    }

    /** 依据框选最小角点生成默认名称：blueprint_<x>_<y>_<z>。 */
    private static String defaultName(BlockPos min, BlockPos max) {
        if (min == null) {
            return "blueprint_" + System.currentTimeMillis();
        }
        return "blueprint_" + min.getX() + "_" + min.getY() + "_" + min.getZ();
    }

    /** 保存并关闭面板（回车提交或点击保存按钮触发）。 */
    private void confirmSave() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || boxMin == null || boxMax == null) {
            showError(Component.translatable("message.rtsbuilding.blueprint.save.invalid"));
            return;
        }
        String name = nameInput.getBufferText().trim();
        try {
            Path file = BlueprintLocalStore.save(mc.level, boxMin, boxMax, name);
            setOpen(false);
            if (screen != null) {
                ((com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen) screen).clearBoxSelection();
            }
            RtsClientKernel kernel = RtsClientKernel.get();
            kernel.dispatch(new StateEvent.BlueprintCaptureComplete(boxMin, boxMax));
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable("message.rtsbuilding.blueprint.saved",
                                file.getFileName().toString()), true);
            }
        } catch (IllegalArgumentException ex) {
            LOG.warn("Blueprint capture failed: too many blocks", ex);
            showError(Component.translatable("message.rtsbuilding.blueprint.save.too_large"));
        } catch (IOException ex) {
            LOG.warn("Blueprint save failed", ex);
            showError(Component.translatable("message.rtsbuilding.blueprint.save.failed", ex.getMessage()));
        }
    }

    /** 显示错误状态提示（面板保持打开，用户可修改名称重试）。 */
    private void showError(Component message) {
        this.statusMessage = message;
        this.statusIsError = true;
    }

    // ── UiPanel 布局与渲染 ─────────────────────────────────────────

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.blueprint.save.title");
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
            // 统一基准：水平居中 + 垂直居中，顶部避开顶栏（TOP_H+6）、底部留 8px 边距
            positionCentered(TOP_H + 6, 8);
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();
        int textColor = ThemeManager.getTextColor();
        var font = Minecraft.getInstance().font;

        SaveLayout layout = computeLayout(cx, cy, cw);

        // 区域尺寸说明
        if (boxMin != null && boxMax != null) {
            int w = boxMax.getX() - boxMin.getX() + 1;
            int h = boxMax.getY() - boxMin.getY() + 1;
            int d = boxMax.getZ() - boxMin.getZ() + 1;
            String region = Component.translatable("screen.rtsbuilding.blueprint.save.region", w, h, d).getString();
            TextRenderer.draw(g, region, layout.labelX(), layout.labelY(), textColor);
        }

        // 名称输入框
        String placeholder = Component.translatable("screen.rtsbuilding.blueprint.save.name_placeholder").getString();
        nameInput.render(g, mouseX, mouseY, layout.inputX(), layout.inputY(), layout.inputW(), placeholder);

        // 保存 / 取消 按钮（FlexLayout 居中）
        renderButton(g, mouseX, mouseY, layout.btnRowX(), layout.btnRowY(),
                Component.translatable("button.rtsbuilding.blueprint.save"));
        renderButton(g, mouseX, mouseY, layout.btnRowX() + BTN_W + BTN_GAP, layout.btnRowY(),
                Component.translatable("button.rtsbuilding.blueprint.cancel"));

        // 状态提示（成功/错误）
        if (statusMessage != null) {
            int statusColor = statusIsError ? UiPalette.get("status_error") : UiPalette.get("status_success");
            TextRenderer.draw(g, statusMessage, layout.statusX(), layout.statusY(), statusColor);
        }
    }

    /**
     * 保存面板内容布局：垂直堆叠（标签 / 输入框 / 按钮行 / 状态），按钮行用 {@link FlexLayout} 居中。
     * 渲染与点击命中共用同一布局。
     */
    private record SaveLayout(int labelX, int labelY, int inputX, int inputY, int inputW,
                              int btnRowX, int btnRowY, int statusX, int statusY) {}

    private static SaveLayout computeLayout(int cx, int cy, int cw) {
        int inputY = cy + 22;
        int inputW = cw - 20;
        int btnRowY = inputY + TextInputBox.INPUT_H + 14;
        List<UiRect> btnRects = FlexLayout.layout(FlexLayout.Direction.ROW, FlexLayout.Justify.CENTER,
                FlexLayout.Align.CENTER, BTN_GAP, cx, btnRowY, cw, BTN_H,
                List.of(UiBox.fixed(BTN_W, BTN_H), UiBox.fixed(BTN_W, BTN_H)));
        return new SaveLayout(
                cx + 10, cy + 6,
                cx + 10, inputY, inputW,
                btnRects.get(0).x(), btnRowY,
                cx + 10, btnRowY + BTN_H + 6);
    }

    private void renderButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, Component label) {
        boolean hovering = mouseX >= x && mouseX < x + BTN_W && mouseY >= y && mouseY < y + BTN_H;
        int color = hovering ? UiPalette.accent() : UiPalette.border();
        SdfRenderer.drawBorderedRoundedRect(g, x, y, BTN_W, BTN_H, 4, color, UiPalette.bg(), 1);
        TextRenderer.drawCentered(g, Minecraft.getInstance().font, label, x + BTN_W / 2,
                y + (BTN_H - Minecraft.getInstance().font.lineHeight) / 2 + 1, ThemeManager.getTextColor());
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();

        SaveLayout layout = computeLayout(cx, cy, cw);
        if (nameInput.handleClick(mouseX, mouseY, layout.inputX(), layout.inputY(), layout.inputW(),
                nameInput.getBufferText())) {
            return;
        }

        if (isInButton(mouseX, mouseY, layout.btnRowX(), layout.btnRowY())) {
            confirmSave();
            return;
        }
        if (isInButton(mouseX, mouseY, layout.btnRowX() + BTN_W + BTN_GAP, layout.btnRowY())) {
            setOpen(false);
        }
    }

    private static boolean isInButton(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + BTN_W && mouseY >= y && mouseY < y + BTN_H;
    }

    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameInput.handleKeyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.handleWindowKeyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        if (nameInput.handleCharTyped(codePoint, modifiers)) {
            return true;
        }
        return super.handleWindowCharTyped(codePoint, modifiers);
    }

    @Override
    protected void onClose() {
        this.boxMin = null;
        this.boxMax = null;
        this.statusMessage = null;
        this.statusIsError = false;
        super.onClose();
    }
}
