package com.rtsbuilding.rtsbuilding.client.presentation.plugin.binding;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class RowLayout {
    int y;
    int arrowBtnX;
    int priorityX;
    int priorityW;
    int unbindX;
    int toggleX;
    int unbindW;
    int toggleW;
    int locateBtnX;
    int locateBtnW;
    int originalIndex;

    record ButtonBar(int unbindW, int toggleW, int locateW, int btnAreaRight) {

        private static final int BTN_PAD_H = 4;
        private static final int BTN_GAP = 2;
        private static final int LEFT_PAD = 5;
        private static final int SCROLLBAR_W = 7;
        private static final int RIGHT_MARGIN = 4;

        int toggleX()  { return btnAreaRight - toggleW; }

        int unbindX()  { return toggleX() - BTN_GAP - unbindW; }

        int locateX()  { return unbindX() - BTN_GAP - locateW; }

        ButtonBar(Minecraft mc, boolean scrollBarVisible, int parentX, int parentW) {
            this(
                    mc.font.width(tr("ui.rtsbuilding.binding.unbind")) + BTN_PAD_H * 2,
                    Math.max(mc.font.width(tr("ui.rtsbuilding.binding.bidirectional")), mc.font.width(tr("ui.rtsbuilding.binding.extract_only"))) + BTN_PAD_H * 2,
                    Math.max(mc.font.width(tr("ui.rtsbuilding.binding.show_location")), mc.font.width(tr("ui.rtsbuilding.binding.hide_location"))) + BTN_PAD_H * 2,
                    parentX + LEFT_PAD + (parentW - LEFT_PAD - SCROLLBAR_W - RIGHT_MARGIN)
                            - (scrollBarVisible ? 2 : 0) - 1
            );
        }

        /** 翻译 lang key（UI 文案统一走 lang，见 AGENTS.md 语言约定）。 */
        private static String tr(String key) {
            return Component.translatable(key).getString();
        }
    }
}
