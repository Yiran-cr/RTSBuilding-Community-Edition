package com.rtsbuilding.rtsbuilding.client.presentation.plugin.binding;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rtsbuilding.rtsbuilding.client.domain.state.LinkedStorageEntry;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.uifw.animate.AnimFloat;
import com.rtsbuilding.uifw.animate.ColorAnimation;
import com.rtsbuilding.uifw.render.GuiItemRenderer;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.theme.ThemeManager;
import com.rtsbuilding.uifw.window.component.ScrollBar;
import com.rtsbuilding.uifw.window.overlay.OverlayContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 绑定条目渲染：链接存储条目列表（优先级/图标/名称/操作按钮）。
 * 操作按钮的语义色（解绑/双向/仅提取/定位）为业务状态色，保留硬编码。
 */
public final class BindingRenderer {

    private final OverlayContext ctx;
    private final ScrollBar scrollBar;
    private final List<RowLayout> rowLayouts;
    private final PriorityEditController editController;
    private final EntryAnimationController animController;

    private final Map<Integer, AnimFloat> arrowHoverAnims = new HashMap<>();
    private final Map<Integer, AnimFloat> locateHoverAnims = new HashMap<>();
    private final Map<Integer, AnimFloat> unbindHoverAnims = new HashMap<>();
    private final Map<Integer, AnimFloat> toggleHoverAnims = new HashMap<>();
    private final Map<Integer, AnimFloat> priorityHoverAnims = new HashMap<>();

    private static final int ROW_H = 20;
    private static final int ICON_SIZE = 12;
    private static final int PRIORITY_PAD_H = 4;
    private static final int PRIORITY_ICON_GAP = 2;
    private static final int ICON_TEXT_GAP = 4;
    private static final int BTN_HEIGHT = 14;
    private static final int BTN_PAD_H = 4;
    private static final int BTN_GAP = 2;
    private static final int ARROW_BTN_SIZE = 14;
    private static final int ARROW_DRAW_SIZE = 6;
    private static final int SCROLLBAR_W = 7;
    private static final int RIGHT_MARGIN = 4;
    private static final int LEFT_PAD = 5;
    private static final int TOP_PAD = 2;
    private static final int EDIT_INPUT_W = 40;
    private static final int EDIT_INPUT_H = 14;
    private static final long CURSOR_BLINK_MS = 600;

    // ── 业务状态色（绑定操作按钮语义，不入主题） ──
    private static final int UNBIND_COLOR = 0xFFE06060;
    private static final int UNBIND_HOVER_COLOR = 0xFFFF8080;
    private static final int MODE_BI_COLOR = 0xFF60C060;
    private static final int MODE_EXTRACT_COLOR = 0xFFE0A040;
    private static final int BTN_HOVER_FG = 0xFFFFFFFF;
    private static final int LOCATE_BTN_COLOR = 0xFF8080E0;
    private static final int LOCATE_BTN_HOVER_COLOR = 0xFFA0A0FF;

    public BindingRenderer(OverlayContext ctx, ScrollBar scrollBar, List<RowLayout> rowLayouts,
                           PriorityEditController editController, EntryAnimationController animController) {
        this.ctx = ctx;
        this.scrollBar = scrollBar;
        this.rowLayouts = rowLayouts;
        this.editController = editController;
        this.animController = animController;
    }

    public void renderContent(GuiGraphics g) {
        StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
        if (sm == null) return;

        List<LinkedStorageEntry> entries = sm.getLinkedStorageEntries();
        List<String> names = sm.getLinkedDisplayNames();
        List<String> iconIds = sm.getLinkedIconItemIds();
        List<Integer> priorities = sm.getLinkedPriorities();
        int count = Math.min(entries.size(), Math.min(names.size(), Math.min(iconIds.size(), priorities.size())));
        if (count > 0) {
            this.editController.tick(count);
            this.animController.tick(count);
        }

        int x = this.ctx.getX();
        int y = this.ctx.getY();
        int w = this.ctx.getWidth();
        int h = this.ctx.getHeight();
        int mouseX = this.ctx.getLastMouseX();
        int mouseY = this.ctx.getLastMouseY();
        Minecraft mc = Minecraft.getInstance();

        int visibleH = h - 4;
        this.scrollBar.setContent(count * ROW_H, visibleH);
        int scroll = this.scrollBar.getScroll();

        this.renderBackgroundRows(g, x, y, w, count, scroll, visibleH, mouseX, mouseY);

        if (count > 0) {
            List<Integer> sortedIndices = buildSortedIndices(count, priorities);
            RowLayout.ButtonBar btnBar = new RowLayout.ButtonBar(mc, this.scrollBar.isVisible(), x, w);
            int fontColor = ThemeManager.getTextColor();
            this.rowLayouts.clear();
            int clipY = y + TOP_PAD;
            for (int vi = 0; vi < count; vi++) {
                int origIdx = sortedIndices.get(vi);
                RowLayout rl = new RowLayout();
                rl.originalIndex = origIdx;
                this.rowLayouts.add(rl);
                this.renderSingleRow(g, x, y, scroll, vi, origIdx, rl, entries, names, iconIds, priorities,
                        btnBar, fontColor, mc, mouseX, mouseY, clipY, visibleH);
            }
        } else {
            String hint = Component.translatable("screen.rtsbuilding.binding.no_linked").getString();
            int textColor = (ThemeManager.getTextColor() & 0xFFFFFF) | 0x60000000;
            int lineH = Minecraft.getInstance().font.lineHeight;
            TextRenderer.drawCentered(g, Minecraft.getInstance().font, hint,
                    this.ctx.getX() + this.ctx.getWidth() / 2,
                    this.ctx.getY() + (this.ctx.getHeight() - lineH) / 2, textColor);
        }

        this.renderScrollbar(g, x, y, h);
    }

    private void renderBackgroundRows(GuiGraphics g, int x, int y, int w, int count, int scroll, int visibleH, int mouseX, int mouseY) {
        int firstRow = scroll / ROW_H;
        int totalRows = visibleH / ROW_H + 2;
        for (int i = firstRow; i < firstRow + totalRows; i++) {
            int bgTop = y + TOP_PAD + i * ROW_H - scroll;
            int color = i % 2 == 0 ? UiPalette.p7() : UiPalette.p6();
            g.fill(x, bgTop, x + w, bgTop + ROW_H, color);
        }
    }

    private void renderSingleRow(GuiGraphics g, int x, int y, int scroll, int vi, int origIdx, RowLayout rl,
                                 List<LinkedStorageEntry> entries, List<String> names, List<String> iconIds,
                                 List<Integer> priorities, RowLayout.ButtonBar btnBar, int fontColor,
                                 Minecraft mc, int mouseX, int mouseY, int clipY, int clipH) {
        int lineH = Minecraft.getInstance().font.lineHeight;
        int baseRowY = TOP_PAD + vi * ROW_H;
        float animY = this.animController.updateEntryAnimY(origIdx, baseRowY);
        int contentY;
        int priorityBoxW;
        rl.y = contentY = y + Math.round(animY) - scroll;
        boolean rowVisible = contentY + ROW_H >= clipY && contentY < clipY + clipH;
        boolean isEditingRow = this.editController.isEditingRow(vi);
        boolean actuallyRender = rowVisible || isEditingRow;

        LinkedStorageEntry entry = entries.get(origIdx);
        String name = names.get(origIdx);
        String iconItemId = iconIds.get(origIdx);
        int priority = priorities.get(origIdx);
        boolean dimmed = !entry.worldAvailable();

        int rowCenterY = contentY + ROW_H / 2;
        int cursorX = x + LEFT_PAD;
        int arrowBtnY = rowCenterY - ARROW_BTN_SIZE / 2;
        rl.arrowBtnX = cursorX;
        if (actuallyRender) {
            this.renderArrowButton(g, cursorX, arrowBtnY, vi == 0, mouseX, mouseY);
        }

        rl.priorityX = cursorX += ARROW_BTN_SIZE + PRIORITY_ICON_GAP;
        rl.priorityW = priorityBoxW = mc.font.width(String.valueOf(priority)) + PRIORITY_PAD_H * 2;
        float animW = this.editController.computePriorityBoxWidth(priorityBoxW, isEditingRow, vi);
        if (actuallyRender || isEditingRow) {
            boolean hoverPriority = !this.ctx.isDividerDragging() && !isEditingRow
                    && mouseX >= cursorX && mouseX < cursorX + (int) animW
                    && mouseY >= rowCenterY - BTN_HEIGHT / 2 && mouseY < rowCenterY + BTN_HEIGHT / 2;
            this.renderPriorityBox(g, cursorX, rowCenterY, String.valueOf(priority), isEditingRow, dimmed,
                    (int) animW, vi, this.priorityHoverAnims.computeIfAbsent(vi, k -> AnimFloat.hover()).track(hoverPriority));
        }
        cursorX += (int) animW + PRIORITY_ICON_GAP;

        if (actuallyRender && !iconItemId.isEmpty()) {
            ItemStack stack = resolveItemStack(iconItemId);
            if (!stack.isEmpty()) {
                this.renderItemIcon(g, stack, cursorX + 6, rowCenterY);
            }
        }
        cursorX += ICON_SIZE;

        if (actuallyRender) {
            int maxNameW = Math.max(0, btnBar.locateX() - cursorX - ICON_TEXT_GAP - BTN_GAP);
            String displayName = TextRenderer.trimToWidth(mc.font, name, maxNameW);
            int nameX = cursorX + ICON_TEXT_GAP;
            int nameColor = dimmed ? (fontColor & 0xFFFFFF) | 0x60000000 : fontColor;
            TextRenderer.draw(g, displayName, nameX, rowCenterY - lineH / 2, nameColor);
        }

        if (actuallyRender) {
            this.renderActionButtons(g, entry, rl, btnBar, rowCenterY, mouseX, mouseY);
        }
    }

    private void renderArrowButton(GuiGraphics g, int btnX, int btnY, boolean isFirst, int mouseX, int mouseY) {
        boolean hovering = !this.ctx.isDividerDragging() && inRect(mouseX, mouseY, btnX, btnY, ARROW_BTN_SIZE, ARROW_BTN_SIZE);
        int rowIndex = this.rowLayouts.size();
        AnimFloat anim = this.arrowHoverAnims.computeIfAbsent(rowIndex, k -> AnimFloat.hover());
        float t = anim.track(hovering);

        int fillColor = ColorAnimation.lerpRGB(UiPalette.bg(), UiPalette.accent(), t);
        SdfRenderer.drawBorderedRoundedRect(g, btnX, btnY, ARROW_BTN_SIZE, ARROW_BTN_SIZE, 4,
                UiPalette.black(), fillColor, 1);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(btnX + ARROW_BTN_SIZE / 2, btnY + ARROW_BTN_SIZE / 2, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(isFirst ? 90f : -90f));
        int half = ARROW_DRAW_SIZE / 2;
        SdfRenderer.drawChevron(g, -half, -half, ARROW_DRAW_SIZE, ARROW_DRAW_SIZE,
                ThemeManager.getTextColor(), 0.5f);
        pose.popPose();
    }

    private void renderActionButtons(GuiGraphics g, LinkedStorageEntry entry, RowLayout rl,
                                     RowLayout.ButtonBar btnBar, int rowCenterY, int mouseX, int mouseY) {
        int locateBtnX;
        int unbindX;
        int toggleX;
        int btnY = rowCenterY - BTN_HEIGHT / 2;
        int rowIndex = this.rowLayouts.size() - 1;

        String locateText;
        if (entry.worldAvailable()) {
            StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
            boolean showLocate = sm != null && sm.isLocationDisplayActive(entry.pos());
            locateText = showLocate ? tr("ui.rtsbuilding.binding.hide_location") : tr("ui.rtsbuilding.binding.show_location");
        } else {
            locateText = tr("ui.rtsbuilding.binding.show_location");
        }
        int locateBtnW = Minecraft.getInstance().font.width(locateText) + BTN_PAD_H * 2;
        rl.locateBtnX = locateBtnX = btnBar.locateX();
        rl.locateBtnW = locateBtnW;
        boolean hoverLocate = !this.ctx.isDividerDragging() && inRect(mouseX, mouseY, locateBtnX, btnY, locateBtnW, BTN_HEIGHT);
        this.drawTextButton(g, locateBtnX, btnY, locateText,
                hoverLocate ? LOCATE_BTN_HOVER_COLOR : LOCATE_BTN_COLOR,
                this.locateHoverAnims.computeIfAbsent(rowIndex * 3, k -> AnimFloat.hover()).track(hoverLocate));

        String unbindText = tr("ui.rtsbuilding.binding.unbind");
        int unbindBtnW = Minecraft.getInstance().font.width(unbindText) + BTN_PAD_H * 2;
        rl.unbindX = unbindX = btnBar.unbindX();
        rl.unbindW = unbindBtnW;
        boolean hoverUnbind = !this.ctx.isDividerDragging() && inRect(mouseX, mouseY, unbindX, btnY, unbindBtnW, BTN_HEIGHT);
        this.drawTextButton(g, unbindX, btnY, unbindText,
                hoverUnbind ? UNBIND_HOVER_COLOR : UNBIND_COLOR,
                this.unbindHoverAnims.computeIfAbsent(rowIndex * 3 + 1, k -> AnimFloat.hover()).track(hoverUnbind));

        String toggleText = entry.isExtractOnly() ? tr("ui.rtsbuilding.binding.extract_only") : tr("ui.rtsbuilding.binding.bidirectional");
        int toggleBtnW = Minecraft.getInstance().font.width(toggleText) + BTN_PAD_H * 2;
        rl.toggleX = toggleX = btnBar.toggleX();
        rl.toggleW = toggleBtnW;
        boolean hoverToggle = !this.ctx.isDividerDragging() && inRect(mouseX, mouseY, toggleX, btnY, toggleBtnW, BTN_HEIGHT);
        int toggleColor = entry.isExtractOnly() ? MODE_EXTRACT_COLOR : MODE_BI_COLOR;
        this.drawTextButton(g, toggleX, btnY, toggleText, hoverToggle ? BTN_HOVER_FG : toggleColor,
                this.toggleHoverAnims.computeIfAbsent(rowIndex * 3 + 2, k -> AnimFloat.hover()).track(hoverToggle));
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private void drawTextButton(GuiGraphics g, int btnX, int btnY, String text, int textColor, float hoverT) {
        int btnW = Minecraft.getInstance().font.width(text) + BTN_PAD_H * 2;
        int fillColor = ColorAnimation.lerpRGB(UiPalette.bg(), UiPalette.accent(), hoverT);
        SdfRenderer.drawBorderedRoundedRect(g, btnX, btnY, btnW, BTN_HEIGHT, 4, UiPalette.black(), fillColor, 1);
        int lineH = Minecraft.getInstance().font.lineHeight;
        TextRenderer.drawCentered(g, Minecraft.getInstance().font, text,
                btnX + btnW / 2, btnY + (BTN_HEIGHT - lineH) / 2, textColor);
    }

    private void renderPriorityBox(GuiGraphics g, int boxX, int centerY, String priorityStr,
                                   boolean editing, boolean dimmed, int boxW, int rowIndex, float hoverT) {
        int boxY = centerY - BTN_HEIGHT / 2;
        SdfRenderer.drawInputBox(g, boxX, boxY, boxW, BTN_HEIGHT,
                this.editController.getAnimValue(rowIndex), hoverT, 4);
        Minecraft mc = Minecraft.getInstance();
        int fontColor = ThemeManager.getTextColor();
        int textColor = editing ? fontColor : (dimmed ? (fontColor & 0xFFFFFF) | 0x60000000 : fontColor);
        int textX = boxX + 4;
        int textY = boxY + (BTN_HEIGHT - Minecraft.getInstance().font.lineHeight) / 2;

        if (editing) {
            String text = this.editController.getBufferText();
            if (!text.isEmpty()) {
                String visible = TextRenderer.trimToWidth(mc.font, text, boxW - 8);
                TextRenderer.draw(g, visible, textX, textY, textColor);
            }
            if ((System.currentTimeMillis() - this.editController.getStartTime()) / CURSOR_BLINK_MS % 2 == 0) {
                int cursorVisualX = mc.font.width(text.isEmpty() ? "0" : text.substring(0, Math.min(this.editController.getBufferLength(), text.length())));
                int clampedX = Math.min(cursorVisualX, boxW - 8);
                g.fill(textX + clampedX, textY, textX + clampedX + 1, textY + Minecraft.getInstance().font.lineHeight,
                        UiPalette.get("input_cursor"));
            }
        } else if (priorityStr != null && !priorityStr.isEmpty()) {
            int textWidth = mc.font.width(priorityStr);
            int centeredTextX = boxX + (boxW - textWidth) / 2;
            TextRenderer.draw(g, priorityStr, centeredTextX, textY, textColor);
        }
    }

    private void renderItemIcon(GuiGraphics g, ItemStack stack, int centerX, int centerY) {
        if (stack.isEmpty()) return;
        GuiItemRenderer.drawItemCentered(g, stack, centerX, centerY, 0.75f);
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int h) {
        int visibleH = h - 4;
        int barX = x + this.ctx.getWidth() - SCROLLBAR_W - RIGHT_MARGIN;
        this.scrollBar.render(g, barX, y + TOP_PAD + 6, visibleH - 12);
    }

    private static List<Integer> buildSortedIndices(int count, List<Integer> priorities) {
        List<Integer> sorted = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            sorted.add(i);
        }
        sorted.sort(Comparator.comparingInt(priorities::get));
        return sorted;
    }

    private static ItemStack resolveItemStack(String itemId) {
        if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    private static boolean inRect(int px, int py, int rx, int ry, int rw, int rh) {
        return px >= rx && px < rx + rw && py >= ry && py < ry + rh;
    }
}
