package com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.popup;

import com.rtsbuilding.uifw.window.popup.BasePopup;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件按钮菜单 —— 点击顶栏「文件」按钮弹出的下拉菜单。
 * <p>
 * 菜单项：
 * <ul>
 *   <li>蓝图文件：打开本地蓝图文件管理面板（列表浏览/删除）；</li>
 *   <li>导入：打开蓝图导入面板（网页式上传区），选择其他模组蓝图文件，
 *       经 {@code BlueprintReaders} 转换为本模组蓝图并存入本地蓝图目录。</li>
 * </ul>
 */
public final class FileMenuPopup extends BasePopup {

    /** 菜单项描述：标签 + 点击动作。 */
    public record MenuItem(Component label, Runnable action) {}

    private final List<MenuItem> items;

    public FileMenuPopup() {
        this.items = new ArrayList<>();
        this.items.add(new MenuItem(
                Component.translatable("screen.rtsbuilding.file_menu.blueprints"),
                () -> {
                    if (onOpenBlueprints != null) onOpenBlueprints.run();
                }));
        this.items.add(new MenuItem(
                Component.translatable("screen.rtsbuilding.file_menu.import"),
                () -> {
                    if (onImport != null) onImport.run();
                }));

        var font = Minecraft.getInstance().font;
        int[] contentWidths = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            contentWidths[i] = font.width(items.get(i).label());
        }
        setItemContentWidths(contentWidths);

        initAnims(items.size());
    }

    /** 「蓝图文件」菜单项动作（打开蓝图文件面板）。 */
    private Runnable onOpenBlueprints;
    /** 「导入」菜单项动作（打开蓝图导入面板）。 */
    private Runnable onImport;

    public void setOnOpenBlueprints(Runnable runnable) {
        this.onOpenBlueprints = runnable;
    }

    public void setOnImport(Runnable runnable) {
        this.onImport = runnable;
    }

    @Override
    protected int getItemCount() {
        return items.size();
    }

    @Override
    protected void renderItem(GuiGraphics g, int index, int itemY, float hoverT) {
        int textColor = hoverT > 0.5f ? ThemeManager.getHoverTextColor() : ThemeManager.getTextColor();
        String label = items.get(index).label().getString();
        int textX = x + getPadH();
        int textY = itemY + (getItemHeight() - Minecraft.getInstance().font.lineHeight) / 2 + 1;
        TextRenderer.draw(g, label, textX, textY, textColor);
    }

    @Override
    protected boolean onItemClick(int index) {
        close();
        if (index >= 0 && index < items.size()) {
            items.get(index).action().run();
        }
        return true;
    }
}
