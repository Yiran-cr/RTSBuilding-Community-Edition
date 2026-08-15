package com.rtsbuilding.rtsbuilding.client.presentation.plugin.workflow;

import com.rtsbuilding.rtsbuilding.client.infrastructure.module.workflow.WorkflowModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay.OverlayContext;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class WorkflowInputHandler {

    private final OverlayContext context;
    private final ScrollBar scrollBar;
    private final List<RowLayout> rowLayouts;

    public WorkflowInputHandler(OverlayContext context, ScrollBar scrollBar, List<RowLayout> rowLayouts) {
        this.context = context;
        this.scrollBar = scrollBar;
        this.rowLayouts = rowLayouts;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (!context.contains((int) mouseX, (int) mouseY)) return false;

        int mx = (int) mouseX;
        int my = (int) mouseY;

        WorkflowModule wm = RtsClientKernel.get().module(WorkflowModule.class);
        if (wm == null) return false;

        for (RowLayout rl : rowLayouts) {
            if (rl.containsToggle(mx, my)) {
                // 挂起（holdType=2，等待材料/工具）点击「继续」→ 请求恢复扫描并弹出恢复面板；
                // 手动暂停（holdType=1）或运行中（0）→ 走 PAUSE_WORKFLOW toggle（暂停/解除暂停）
                if (isSuspended(wm, rl.entryId())) {
                    RtsClientPacketGateway.sendRequestResumeScan(rl.entryId());
                } else {
                    RtsClientPacketGateway.sendPauseWorkflow(rl.entryId());
                }
                return true;
            }
            if (rl.containsDelete(mx, my)) {
                RtsClientPacketGateway.sendDeleteWorkflow(rl.entryId());
                return true;
            }
        }

        return false;
    }

    /** 判断指定工作流是否处于挂起（等待材料/工具）状态——区别于手动暂停。 */
    private static boolean isSuspended(WorkflowModule wm, int entryId) {
        var progress = wm.getProgress();
        if (progress == null) return false;
        for (com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus st : progress.statuses()) {
            if (st != null && st.entryId() == entryId) {
                return st.holdType() == 2;
            }
        }
        return false;
    }
}
