package com.rtsbuilding.uifw.window.api;

import com.rtsbuilding.uifw.window.window.FloatingWindowLayer;
import com.rtsbuilding.uifw.window.window.UiPanel;
import net.minecraft.client.gui.GuiGraphics;

/**
 * uifw 宿主屏幕抽象（UI 框架与宿主 mod 的解耦点）。
 *
 * <p>UI 面板框架（{@link UiPanel} 等）只依赖本接口，不感知具体业务屏幕。
 * 宿主侧的任意 {@code Screen} 实现本接口即可接入面板框架。
 */
public interface UiPanelHost {

    /** 宿主屏幕宽度（逻辑像素）。 */
    int getUiWidth();

    /** 宿主屏幕高度（逻辑像素）。 */
    int getUiHeight();

    /** 浮窗层管理器（面板叠放/排序）。 */
    FloatingWindowLayer getFloatingWindowLayer();

    /** 开启 uifw 缩放裁剪区（面板裁剪用）。 */
    void enableUiScissor(GuiGraphics g, int x1, int y1, int x2, int y2);
}
