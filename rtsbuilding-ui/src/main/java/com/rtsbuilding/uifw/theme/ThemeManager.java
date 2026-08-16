package com.rtsbuilding.uifw.theme;

import com.rtsbuilding.uifw.render.UiPalette;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 明暗主题切换管理。
 *
 * <p>颜色统一由 {@link UiPalette} 提供（JSON 主题可配置），本类只负责
 * 明暗模式状态与变更通知。{@code getTextColor} 等便捷方法为兼容 API，委托 {@link UiPalette}。
 */
public final class ThemeManager {
    private static final ThemeManager INSTANCE = new ThemeManager();

    private final List<ThemeListener> listeners = new CopyOnWriteArrayList<>();

    private ThemeManager() {
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    public boolean isLightMode() {
        return UiPalette.isLightMode();
    }

    public void setLightMode(boolean mode) {
        if (UiPalette.isLightMode() != mode) {
            UiPalette.setLightMode(mode);
            notifyListeners();
        }
    }

    public void toggle() {
        setLightMode(!UiPalette.isLightMode());
    }

    public void addListener(ThemeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ThemeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (ThemeListener l : listeners) {
            l.onThemeChanged(UiPalette.isLightMode());
        }
    }

    // ── 兼容取色 API（颜色均来自 UiPalette 主题） ──

    public static int getTextColor() {
        return UiPalette.get("text");
    }

    public static int getHoverTextColor() {
        return UiPalette.get("text_hover");
    }

    public static int getDividerColor() {
        return UiPalette.get("divider");
    }
}
