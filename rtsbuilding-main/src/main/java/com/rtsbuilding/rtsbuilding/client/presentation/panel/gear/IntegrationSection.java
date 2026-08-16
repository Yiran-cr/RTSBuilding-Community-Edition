package com.rtsbuilding.rtsbuilding.client.presentation.panel.gear;

import com.rtsbuilding.rtsbuilding.api.compat.RtsCompatRegistry;
import com.rtsbuilding.rtsbuilding.api.compat.RtsIntegration;
import com.rtsbuilding.uifw.window.component.SettingsSection;
import com.rtsbuilding.uifw.render.TextRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 宿主 mod 集成（AE2 / RS / BD / Sophisticated Backpacks）健康状态展示区。
 *
 * <p>数据来自 {@link RtsCompatRegistry#getIntegrations()} 的 {@link RtsIntegration#selfCheck()}，
 * 每行显示：集成名 + 状态（✓ 正常 / ✕ 不可用 + 诊断）。供玩家/维护者在设置面板快速判断
 * 某个宿主集成是否真正生效（反射绑定失败会在启动日志 WARN 并在此标注）。
 */
public class IntegrationSection extends SettingsSection {

    /** 正常（绿） */
    private static final int COLOR_OK = 0xFF16A34A;
    /** 不可用（黄）——宿主未加载或绑定缺失 */
    private static final int COLOR_UNAVAILABLE = 0xFFB45309;

    /** 状态列与集成名的间距（px）。 */
    private static final int STATUS_COL_GAP = 12;

    /** 状态文本最大宽度，超出截断防止溢出面板。 */
    private static final int MAX_STATUS_WIDTH = 90;

    public IntegrationSection() {
        super("screen.rtsbuilding.settings.category.integrations");
    }

    /** 数据源：注册的所有宿主集成。 */
    private List<RtsIntegration> integrations() {
        return RtsCompatRegistry.getIntegrations();
    }

    /** 是否有任何宿主集成（无集成时设置面板整块隐藏该分区）。 */
    public boolean hasIntegrations() {
        return !integrations().isEmpty();
    }

    @Override
    protected int getContentRowCount() {
        int size = integrations().size();
        return size == 0 ? 1 : size; // 无集成时占一行显示提示
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int lineCount) {
        List<RtsIntegration> list = integrations();
        if (list.isEmpty()) {
            TextRenderer.draw(g, net.minecraft.network.chat.Component
                    .translatable("ui.rtsbuilding.integration.none").getString(),
                    x + LEFT_PAD, rowY(y, 0), getTextColor());
            return;
        }
        for (int i = 0; i < lineCount && i < list.size(); i++) {
            RtsIntegration it = list.get(i);
            int lineY = rowY(y, i);
            String problem = safeSelfCheck(it);
            boolean available = safeAvailable(it);

            String name = displayName(it.integrationId());
            TextRenderer.draw(g, name, x + LEFT_PAD, lineY, getTextColor());

            int statusX = x + LEFT_PAD + Minecraft.getInstance().font.width(name) + STATUS_COL_GAP;
            String statusText;
            int color;
            if (problem != null && !problem.isEmpty()) {
                color = COLOR_UNAVAILABLE;
                statusText = "✕ " + problem;
            } else if (available) {
                color = COLOR_OK;
                statusText = "✓";
            } else {
                color = COLOR_UNAVAILABLE;
                statusText = "✕ " + net.minecraft.network.chat.Component
                        .translatable("ui.rtsbuilding.integration.unavailable").getString();
            }
            if (statusText.length() > MAX_STATUS_WIDTH) {
                statusText = statusText.substring(0, MAX_STATUS_WIDTH) + "…";
            }
            TextRenderer.draw(g, statusText, statusX, lineY, color);
        }
    }

    /** 集成名 -> 展示 label（内部用英文 id 直显，避免新增 lang key 同步成本）。 */
    private static String displayName(String id) {
        return switch (id) {
            case "ae2" -> "AE2";
            case "refinedstorage" -> "Refined Storage";
            case "beyonddimensions" -> "Beyond Dimensions";
            case "sophisticatedbackpacks" -> "Sophisticated Backpacks";
            default -> id;
        };
    }

    private static boolean safeAvailable(RtsIntegration it) {
        try {
            return it.available();
        } catch (Throwable e) {
            return false;
        }
    }

    @Nullable
    private static String safeSelfCheck(RtsIntegration it) {
        try {
            return it.selfCheck();
        } catch (Throwable e) {
            return "selfCheck threw: " + e.getMessage();
        }
    }
}

