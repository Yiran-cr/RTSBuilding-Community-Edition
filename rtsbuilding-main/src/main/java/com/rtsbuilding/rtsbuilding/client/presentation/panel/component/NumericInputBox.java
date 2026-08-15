package com.rtsbuilding.rtsbuilding.client.presentation.panel.component;

import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * 数值文本输入框组件：点击进入编辑态后可输入数字（含小数点），
 * 回车提交 / Esc 取消，支持退格、删除、方向键、Ctrl+V 粘贴与 Ctrl+A 全选。
 *
 * <p>文本解析与格式化由宿主（调用方）通过 {@link #setOnCommit(Consumer)} 提供，
 * 组件本身不关心数值语义。供右面板下嵌层调节器的“xxx：值”文本输入使用。</p>
 */
public final class NumericInputBox {

    /** 输入框高度。 */
    public static final int INPUT_H = 12;
    /** 输入框内容左右内边距。 */
    private static final int INPUT_PAD = 4;
    /** 光标闪烁周期（毫秒）。 */
    private static final long CURSOR_BLINK_MS = 500;

    private boolean editing;
    private final StringBuilder buffer = new StringBuilder();
    private int cursorPos;
    /** keyPressed 已处理可打印字符时置位，charTyped 据此跳过，避免重复输入。 */
    private boolean keyProcessed;

    @Nullable
    private Consumer<String> onCommit;

    public void setOnCommit(@Nullable Consumer<String> callback) {
        this.onCommit = callback;
    }

    public boolean isEditing() {
        return editing;
    }

    /**
     * 渲染输入框：非编辑态居中显示 {@code displayText}，编辑态显示缓冲文本与闪烁光标。
     */
    public void render(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, String displayText) {
        Font font = Minecraft.getInstance().font;
        boolean hovered = !editing
                && mouseX >= x && mouseX < x + w
                && mouseY >= y && mouseY < y + INPUT_H;
        float focus = editing ? 1f : 0f;
        float hover = hovered ? 1f : 0f;
        SdfRenderer.drawInputBox(g, x, y, w, INPUT_H, focus, hover, 4);

        int contentW = w - INPUT_PAD * 2;
        int textBaselineX = x + INPUT_PAD;
        int textY = y + (INPUT_H - font.lineHeight) / 2;

        if (editing) {
            String text = buffer.toString();
            String visible = TextRenderer.trimToWidth(font, text, Math.max(8, contentW));
            g.drawString(font, visible, textBaselineX, textY, ThemeManager.getTextColor(), false);
            // 光标：仅当光标在可见区内绘制闪烁竖线
            int before = font.width(text.substring(0, Math.min(cursorPos, text.length())));
            int cursorX = textBaselineX + Math.min(before, contentW - 1);
            if (cursorX >= textBaselineX && (System.currentTimeMillis() / CURSOR_BLINK_MS) % 2 == 0) {
                g.fill(cursorX, textY, cursorX + 1, textY + font.lineHeight, 0xFFFFFFFF);
            }
        } else {
            String visible = TextRenderer.trimToWidth(font, displayText, Math.max(8, contentW));
            int textX = textBaselineX + (contentW - font.width(visible)) / 2;
            g.drawString(font, visible, textX, textY, ThemeManager.getTextColor(), false);
        }
    }

    /**
     * 点击命中：进入编辑态（以 {@code initialText} 为初始缓冲）。
     *
     * @return 是否命中输入框。
     */
    public boolean handleClick(double mouseX, double mouseY, int x, int y, int w, String initialText) {
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + INPUT_H) {
            if (!editing) {
                beginEdit(initialText);
            }
            return true;
        }
        return false;
    }

    /**
     * 键盘处理：仅在编辑态消费。回车提交、Esc 取消，其余编辑键处理后在编辑态吞掉其他键，
     * 防止触发 RTS 快捷键。
     */
    public boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!editing) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            applyEdit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelEdit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (cursorPos > 0 && buffer.length() > 0) {
                buffer.deleteCharAt(cursorPos - 1);
                cursorPos--;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (cursorPos < buffer.length()) {
                buffer.deleteCharAt(cursorPos);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT
                || keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END) {
            handleCursorKey(keyCode, modifiers);
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V) {
            pasteFromClipboard();
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_A) {
            cursorPos = buffer.length();
            return true;
        }
        char ch = keyCodeToInputChar(keyCode, modifiers);
        if (ch != '\0') {
            insertChar(ch);
            keyProcessed = true;
            return true;
        }
        return true; // 编辑态吞掉其他键，避免误触 RTS 快捷键
    }

    /**
     * 字符输入：keyPressed 已处理可打印字符时跳过（防重复），否则插入数字/小数点。
     */
    public boolean handleCharTyped(char codePoint, int modifiers) {
        if (!editing) return false;
        if (keyProcessed) {
            keyProcessed = false;
            return true;
        }
        if ((codePoint >= '0' && codePoint <= '9') || codePoint == '.') {
            insertChar(codePoint);
        }
        return true;
    }

    /** 提交当前编辑（供宿主在点击输入框外部等时机调用）。 */
    public void applyIfEditing() {
        if (editing) {
            applyEdit();
        }
    }

    /** 强制取消编辑（不提交）。 */
    public void cancelEdit() {
        editing = false;
        keyProcessed = false;
    }

    private void beginEdit(String initialText) {
        buffer.setLength(0);
        buffer.append(initialText == null ? "" : initialText);
        cursorPos = buffer.length();
        editing = true;
        keyProcessed = false;
    }

    private void insertChar(char ch) {
        buffer.insert(cursorPos, ch);
        cursorPos++;
    }

    private static char keyCodeToInputChar(int keyCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) return '\0';
            return (char) ('0' + (keyCode - GLFW.GLFW_KEY_0));
        }
        if (keyCode == GLFW.GLFW_KEY_PERIOD && (modifiers & GLFW.GLFW_MOD_SHIFT) == 0) {
            return '.';
        }
        return '\0';
    }

    private void handleCursorKey(int keyCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> cursorPos = ctrl ? 0 : Math.max(0, cursorPos - 1);
            case GLFW.GLFW_KEY_RIGHT -> cursorPos = ctrl ? buffer.length() : Math.min(buffer.length(), cursorPos + 1);
            case GLFW.GLFW_KEY_HOME -> cursorPos = 0;
            case GLFW.GLFW_KEY_END -> cursorPos = buffer.length();
        }
    }

    private void pasteFromClipboard() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clip == null) return;
        for (int i = 0; i < clip.length(); i++) {
            char ch = clip.charAt(i);
            if ((ch >= '0' && ch <= '9') || ch == '.') {
                insertChar(ch);
            }
        }
    }

    private void applyEdit() {
        if (editing && onCommit != null) {
            onCommit.accept(buffer.toString().trim());
        }
        editing = false;
        keyProcessed = false;
    }
}
