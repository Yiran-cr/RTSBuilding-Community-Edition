package com.rtsbuilding.rtsbuilding.client.presentation.panel.powergrid;

import com.rtsbuilding.rtsbuilding.api.powergrid.GridMember;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsAccessLevel;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsPowerGrid;
import com.rtsbuilding.uifw.render.SdfRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.theme.ThemeManager;
import com.rtsbuilding.uifw.window.window.UiPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 成员编辑弹窗（参照 Flux-Networks PopupMemberEdit）——在电网管理面板内用 uifw 子面板打开。
 * <p>
 * 顶部居中显示：目标成员名 + 权限名 + UUID（0.75 缩放两行）；下方纵向操作按钮：
 * <b>设为用户 / 设为管理员 / 取消成员身份 / 转移所有权</b>——按<b>当前玩家权限</b>与<b>目标权限</b>
 * 决定显示与可点（OWNER 可升 ADMIN/移除 ADMIN/USER/转移；ADMIN 只能移除 USER）。转移所有权
 * 需<b>二次点击确认</b>（防误触）。
 * <p>
 * 目标为<b>陌生人（{@link RtsAccessLevel#BLOCKED}，在线未加入）</b>时显示唯一可点操作
 * 「设为用户」——即<b>邀请加入电网组</b>（参照 Flux：点击成员行 → 设为用户完成邀请）。
 */
public final class PopupMemberEdit extends UiPanel {

    private static final int PW = 176;
    private static final int PH = 150;
    private static final int BTN_W = 120;
    private static final int BTN_H = 13;
    private static final int BTN_GAP = 6;

    private static final int COLOR_OWNER = 0xFFFFAA00;
    private static final int COLOR_ADMIN = 0xFF66CC00;
    private static final int COLOR_USER = 0xFF6699FF;
    private static final int COLOR_BLOCKED = 0xFFA9A9A9; // 陌生人灰
    private static final int COLOR_DANGER = 0xFFFF5555;

    private final PowerGridManagerPanel host;
    private final GridMember target;

    private final List<int[]> btnRects = new ArrayList<>();
    private final List<Byte> btnAction = new ArrayList<>();
    private boolean transferArmed;

    public PopupMemberEdit(PowerGridManagerPanel host, GridMember target) {
        this.host = host;
        this.target = target;
        this.draggable = false;
        this.closable = true;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        btnRects.clear();
        btnAction.clear();

        int x = contentX() + 4;
        int w = contentWidth() - 8;
        int cTop = contentY();
        int cx = x + w / 2;
        Font font = Minecraft.getInstance().font;

        // 顶部：成员名 + 权限名 + UUID
        TextRenderer.draw(g, target.name(), cx - font.width(target.name()) / 2, cTop + 2,
                ThemeManager.getHoverTextColor());
        int accColor = accessColor(target.access());
        TextRenderer.draw(g, accessName(target.access()), cx - font.width(accessName(target.access())) / 2,
                cTop + 13, accColor);

        g.pose().pushPose();
        g.pose().scale(0.75f, 0.75f, 1);
        String uuid = target.id().toString();
        TextRenderer.draw(g, "UUID: " + uuid.substring(0, 16),
                (int) ((x + 14) / 0.75f), (int) ((cTop + 25) / 0.75f), 0xFFAAAAAA);
        TextRenderer.draw(g, uuid.substring(16),
                (int) ((x + 14) / 0.75f), (int) ((cTop + 31) / 0.75f), 0xFFAAAAAA);
        g.pose().popPose();

        // 按钮（参考 Flux：OWNER 全操作；ADMIN 仅移除 USER；目标 OWNER 无可操作）
        RtsAccessLevel my = host.myAccess();
        boolean canEdit = my != null && my.canEdit();
        boolean canTransfer = my != null && my.canTransfer();
        boolean isOwnerTarget = target.access() == RtsAccessLevel.OWNER;

        int by = cTop + 46;
        if (!isOwnerTarget && canEdit) {
            if (target.access() == RtsAccessLevel.BLOCKED) {
                // 陌生人（在线未加入）：参照 Flux PopupMemberEdit——唯一的可点操作是「设为用户」
                // （即邀请加入电网组），无需输入玩家名。
                addButton(g, x, by, "设为用户", COLOR_USER, false, (byte) 1, mouseX, mouseY, w);
                by += BTN_H + BTN_GAP;
            } else if (target.access() == RtsAccessLevel.ADMIN) {
                addButton(g, x, by, "设为用户", COLOR_USER, false, (byte) 1, mouseX, mouseY, w);      // 降级
                by += BTN_H + BTN_GAP;
            } else if (target.access() == RtsAccessLevel.USER && canTransfer) {
                addButton(g, x, by, "设为管理员", COLOR_ADMIN, false, (byte) 2, mouseX, mouseY, w);  // 升级
                by += BTN_H + BTN_GAP;
            }
            boolean canRemove = target.access() == RtsAccessLevel.USER
                    || (target.access() == RtsAccessLevel.ADMIN && canTransfer);
            addButton(g, x, by, "取消成员身份", COLOR_DANGER, !canRemove, (byte) 3, mouseX, mouseY, w);
            by += BTN_H + BTN_GAP;
            if (canTransfer) {
                addButton(g, x, by, transferArmed ? "确认转移!(再点)" : "转移所有权", 0xFFFF00FF,
                        !transferArmed || target.access() == RtsAccessLevel.OWNER, (byte) 4, mouseX, mouseY, w);
            }
        } else {
            SdfRenderer.drawPill(g, x, cTop + 70, w, 1, 0x33888888);
        }
    }

    /** 绘制一个按钮并登记命中。 */
    private void addButton(GuiGraphics g, int x, int y, String label, int color, boolean disabled, byte action,
                           int mouseX, int mouseY, int w) {
        int bx = x + (w - BTN_W) / 2;
        boolean hovered = !disabled && mouseX >= bx && mouseX < bx + BTN_W
                && mouseY >= y && mouseY < y + BTN_H;
        int fill = disabled ? 0x552E2E2E : hovered ? UiPalette.accent() : 0x882E3B4C;
        SdfRenderer.drawBorderedRoundedRect(g, bx, y, BTN_W, BTN_H, 4, UiPalette.border(), fill, 1);
        int lw = Minecraft.getInstance().font.width(label);
        TextRenderer.draw(g, label, bx + (BTN_W - lw) / 2, y + (BTN_H - Minecraft.getInstance().font.lineHeight) / 2 + 1,
                disabled ? UiPalette.border() : color);
        btnRects.add(new int[]{bx, y, BTN_W, BTN_H});
        btnAction.add(action);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        for (int i = 0; i < btnAction.size(); i++) {
            int[] r = btnRects.get(i);
            if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                onAction(btnAction.get(i));
                return;
            }
        }
    }

    private void onAction(byte action) {
        switch (action) {
            case 1 -> RtsPowerGrid.get().setMemberAccess(target.id(), RtsAccessLevel.USER);
            case 2 -> RtsPowerGrid.get().setMemberAccess(target.id(), RtsAccessLevel.ADMIN);
            case 3 -> RtsPowerGrid.get().removeMember(target.id());
            case 4 -> {
                if (transferArmed) {
                    RtsPowerGrid.get().transferOwnership(target.id());
                } else {
                    transferArmed = true;
                    return;                 // 不关闭，等待二次确认
                }
            }
            default -> { }
        }
        setOpen(false);
    }

    private static int accessColor(RtsAccessLevel a) {
        return switch (a) {
            case OWNER -> COLOR_OWNER;
            case ADMIN -> COLOR_ADMIN;
            case BLOCKED -> COLOR_BLOCKED;
            default -> COLOR_USER;
        };
    }

    private static String accessName(RtsAccessLevel a) {
        return switch (a) {
            case OWNER -> t("screen.rtsbuilding.powergrid.access_owner");
            case ADMIN -> t("screen.rtsbuilding.powergrid.access_admin");
            case BLOCKED -> t("screen.rtsbuilding.powergrid.access_blocked");
            default -> t("screen.rtsbuilding.powergrid.access_user");
        };
    }

    private static String t(String key) {
        return Component.translatable(key).getString();
    }

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.powergrid.member_edit");
    }

    @Override
    protected int getDefaultWidth() {
        return PW;
    }

    @Override
    protected int getDefaultHeight() {
        return PH;
    }

    @Override
    protected void computeDefaultPosition() {
        if (screen == null) {
            return;
        }
        // 居中于宿主电网面板内
        setWindowX(Math.max(0, (screen.getUiWidth() - PW) / 2));
        setWindowY(Math.max(0, (screen.getUiHeight() - PH) / 2));
    }
}
