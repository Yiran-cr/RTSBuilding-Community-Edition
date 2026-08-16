package com.rtsbuilding.uifw.component;

import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * 通用文本输入框组件：点击进入编辑态后可输入任意字符（含中文等），
 * 回车提交 / Esc 取消，支持退格、删除、方向键、Home/End、Ctrl+V 粘贴、
 * Ctrl+A 全选与 Ctrl+C 复制。
 *
 * <p>与 {@link NumericInputBox} 的区别在于不限制输入字符集：
 * 可打印字符统一经 {@link #handleCharTyped} 录入，特殊键在
 * {@link #handleKeyPressed} 中处理，避免依赖 keyCode→字符的映射。
 * 供蓝图命名等需要自由文本输入的场景使用。</p>
 */
public final class TextInputBox {

    /** 输入框高度。 */
    public static final int INPUT_H = 12;
    /** 输入框内容左右内边距。 */
    private static final int INPUT_PAD = 4;
    /** 光标闪烁周期（毫秒）。 */
    private static final long CURSOR_BLINK_MS = 500;
    /** 输入长度上限，防止超长文本撑爆布局与文件名。 */
    private static final int MAX_LENGTH = 128;

    private boolean editing;
    private final StringBuilder buffer = new StringBuilder();
    private int cursorPos;

    @Nullable
    private Consumer<String> onCommit;

    public void setOnCommit(@Nullable Consumer<String> callback) {
        this.onCommit = callback;
    }

    public boolean isEditing() {
        return editing;
    }

    /** 当前缓冲文本（编辑中或已提交均可读取）。 */
    public String getBufferText() {
        return buffer.toString();
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
                g.fill(cursorX, textY, cursorX + 1, textY + font.lineHeight, UiPalette.get("input_cursor"));
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
     * 键盘处理：仅在编辑态消费。回车提交、Esc 取消，其余编辑键处理后吞掉其他键，
     * 防止触发宿主快捷键。可打印字符不在此处理，统一走 {@link #handleCharTyped}。
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
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_C) {
            Minecraft.getInstance().keyboardHandler.setClipboard(buffer.toString());
            return true;
        }
        return true; // 编辑态吞掉其他键，避免误触宿主快捷键
    }

    /**
     * 字符输入：插入任意可打印字符（控制字符除外）。
     */
    public boolean handleCharTyped(char codePoint, int modifiers) {
        if (!editing) return false;
        if (codePoint < 0x20 || codePoint == 0x7F) {
            return true;
        }
        insertChar(codePoint);
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
    }

    /** 以指定初始文本开始编辑（自动聚焦）。 */
    public void beginEdit(String initialText) {
        buffer.setLength(0);
        buffer.append(initialText == null ? "" : initialText);
        cursorPos = buffer.length();
        editing = true;
    }

    private void insertChar(char ch) {
        if (buffer.length() >= MAX_LENGTH) {
            return;
        }
        buffer.insert(cursorPos, ch);
        cursorPos++;
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
            if (ch >= 0x20 && ch != 0x7F) {
                insertChar(ch);
            }
        }
    }

    private void applyEdit() {
        if (editing && onCommit != null) {
            onCommit.accept(buffer.toString().trim());
        }
        editing = false;
    }
}
