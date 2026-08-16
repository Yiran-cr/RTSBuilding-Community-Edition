package com.rtsbuilding.uifw.theme;

@FunctionalInterface
public interface ThemeListener {
    
    void onThemeChanged(boolean lightMode);
}
