package com.rtsbuilding.rtsbuilding.client.presentation.panel.gear;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeybinds;
import com.rtsbuilding.uifw.window.component.SettingsSection;
import com.rtsbuilding.uifw.component.ResetButton;
import com.rtsbuilding.uifw.animate.AnimFloat;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.theme.ThemeManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;

/**
 * "按键设置"折叠条目：集中配置 RTS 的全部按键。
 * <p>
 * 每行展示一个 RTS 键位的名称、当前绑定按钮与恢复默认按钮。点击绑定按钮进入
 * 捕获态，等待下一次键盘按键或鼠标点击作为新绑定（ESC 取消）。绑定通过
 * {@link RtsKeybinds} 持久化，并实时应用到 {@link KeyMapping} 对象，
 * 因此 RTS 各处的快捷键判断、提示文本都会立即跟随新绑定。
 */
public final class KeybindSection extends SettingsSection {

    private static final int BIND_BTN_W = 84 * 2 / 3 * 3 / 2;
    private static final int BIND_BTN_H = 16;
    private static final int BIND_TEXT_PAD = 3;
    private static final String CAPTURE_PENDING_TEXT = "> ...";

    private final List<RtsKeybinds.Entry> entries = RtsKeybinds.entries();
    private final ResetButton[] resetButtons;
    private final AnimFloat[] bindHover = new AnimFloat[entries.size()];
    private final int[] bindBtnX = new int[entries.size()];
    private final int[] bindBtnY = new int[entries.size()];

    private final String[] cachedLabels;
    private final String[] cachedBinds;

    @Nullable
    private KeyMapping capturing;
    private int capturingRow = -1;

    public KeybindSection() {
        super("screen.rtsbuilding.settings.category.keybinds");
        int n = entries.size();
        this.resetButtons = new ResetButton[n];
        this.cachedLabels = new String[n];
        this.cachedBinds = new String[n];
        for (int i = 0; i < n; i++) {
            this.bindHover[i] = AnimFloat.hover();
            this.resetButtons[i] = new ResetButton();
            int idx = i;
            this.resetButtons[i].setResetAction(() -> {
                RtsKeybinds.reset(entries.get(idx).mapping());
                invalidateTextCache();
            });
        }
    }

    @Override
    protected int getContentRowCount() {
        return entries.size();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int lineCount) {
        for (int i = 0; i < entries.size(); i++) {
            RtsKeybinds.Entry entry = entries.get(i);

            int lineCenterY = textY(y, i) + Minecraft.getInstance().font.lineHeight / 2;
            int btnX = x + w - RIGHT_PAD - ResetButton.BTN_SIZE - 4 - BIND_BTN_W;
            int btnY = lineCenterY - BIND_BTN_H / 2;
            bindBtnX[i] = btnX;
            bindBtnY[i] = btnY;

            int labelMaxW = btnX - (x + LEFT_PAD) - 6;
            String label = TextRenderer.trimToWidth(Minecraft.getInstance().font, labelText(i), Math.max(24, labelMaxW));
            TextRenderer.draw(g, label, x + LEFT_PAD, textY(y, i), getTextColor());

            renderBindButton(g, mouseX, mouseY, btnX, btnY, i);

            int resetX = x + w - RIGHT_PAD - ResetButton.BTN_SIZE;
            int resetY = lineCenterY - ResetButton.BTN_SIZE / 2;
            resetButtons[i].render(g, mouseX, mouseY, resetX, resetY);
        }
    }

    private void renderBindButton(GuiGraphics g, int mx, int my, int x, int y, int row) {
        boolean hovering = mx >= x && mx < x + BIND_BTN_W && my >= y && my < y + BIND_BTN_H;
        boolean active = capturingRow == row;
        float t = bindHover[row].track(hovering || active);
        int fill = lerpColor(UiPalette.bg(), UiPalette.accent(), t);
        SdfRenderer.drawBorderedRoundedRect(g, x, y, BIND_BTN_W, BIND_BTN_H, 3,
                UiPalette.black(), fill, 1);

        String text = active ? CAPTURE_PENDING_TEXT : bindText(row);
        String trimmed = TextRenderer.trimToWidth(Minecraft.getInstance().font, text, BIND_BTN_W - BIND_TEXT_PAD * 2);
        int textY = y + (BIND_BTN_H - Minecraft.getInstance().font.lineHeight) / 2;
        if (active) {
            TextRenderer.draw(g, trimmed, x + BIND_TEXT_PAD, textY, getTextColor());
        } else {
            TextRenderer.draw(g, trimmed, x + (BIND_BTN_W - Minecraft.getInstance().font.width(trimmed)) / 2, textY, getTextColor());
        }
    }

    @Override
    protected boolean onContentLineClick(int lineIndex, double mouseX, double mouseY,
                                         int contentX, int contentY, int contentW) {
        if (lineIndex < 0 || lineIndex >= entries.size()) return false;
        if (resetButtons[lineIndex].handleClick(mouseX, mouseY)) return true;
        if (isOverBindButton(lineIndex, mouseX, mouseY)) {
            beginCapture(lineIndex);
            return true;
        }
        return false;
    }

    // ── 捕获逻辑 ──

    public boolean isCapturing() {
        return capturing != null;
    }

    public void cancelCapture() {
        this.capturing = null;
        this.capturingRow = -1;
    }

    private void beginCapture(int row) {
        this.capturingRow = row;
        this.capturing = entries.get(row).mapping();
    }

    /**
     * 捕获键盘按键。ESC 取消捕获；Ctrl/Alt/Shift 作为组合键前缀，
     * 若按下的本身就是修饰键（如左 Ctrl），则绑定为无修饰的单键。
     */
    public boolean captureKey(int keyCode, int scanCode, int modifiers) {
        if (capturing == null) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelCapture();
            return true;
        }
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        KeyModifier modifier = keyIsModifier(keyCode)
                ? KeyModifier.NONE
                : modifierFromBits(modifiers);
        applyCapture(modifier, key);
        return true;
    }

    /** 捕获鼠标按钮。捕获时按住 Ctrl/Alt/Shift 可作为组合键前缀（如 Shift+右键）。 */
    public boolean captureMouse(int button) {
        if (capturing == null) return false;
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(button);
        applyCapture(modifierFromGLFW(), key);
        return true;
    }

    private void applyCapture(KeyModifier modifier, InputConstants.Key key) {
        if (capturing == null) return;
        capturing.setKeyModifierAndCode(modifier, key);
        RtsKeybinds.save();
        invalidateTextCache();
        cancelCapture();
    }

    private static boolean keyIsModifier(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL
                || keyCode == GLFW.GLFW_KEY_LEFT_ALT || keyCode == GLFW.GLFW_KEY_RIGHT_ALT
                || keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT;
    }

    private static KeyModifier modifierFromBits(int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean alt = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (ctrl && !alt && !shift) return KeyModifier.CONTROL;
        if (alt && !ctrl && !shift) return KeyModifier.ALT;
        if (shift && !ctrl && !alt) return KeyModifier.SHIFT;
        return KeyModifier.NONE;
    }

    private static KeyModifier modifierFromGLFW() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean ctrl = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean alt = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
        boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        if (ctrl && !alt && !shift) return KeyModifier.CONTROL;
        if (alt && !ctrl && !shift) return KeyModifier.ALT;
        if (shift && !ctrl && !alt) return KeyModifier.SHIFT;
        return KeyModifier.NONE;
    }

    // ── 文本缓存 ──

    private String labelText(int row) {
        if (cachedLabels[row] == null) {
            cachedLabels[row] = Component.translatable(entries.get(row).mapping().getName()).getString();
        }
        return cachedLabels[row];
    }

    private String bindText(int row) {
        if (cachedBinds[row] == null) {
            cachedBinds[row] = entries.get(row).mapping().getTranslatedKeyMessage().getString();
        }
        return cachedBinds[row];
    }

    /** 绑定变更后清空文本缓存，使提示文本立即刷新。 */
    public void invalidateTextCache() {
        for (int i = 0; i < cachedBinds.length; i++) {
            cachedBinds[i] = null;
        }
    }

    private boolean isOverBindButton(int row, double mx, double my) {
        return mx >= bindBtnX[row] && mx < bindBtnX[row] + BIND_BTN_W
                && my >= bindBtnY[row] && my < bindBtnY[row] + BIND_BTN_H;
    }

    private static int lerpColor(int from, int to, float t) {
        if (t <= 0.005f) return from;
        if (t >= 0.995f) return to;
        int a = lerpComp((from >> 24) & 0xFF, (to >> 24) & 0xFF, t);
        int r = lerpComp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerpComp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerpComp(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpComp(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }
}
