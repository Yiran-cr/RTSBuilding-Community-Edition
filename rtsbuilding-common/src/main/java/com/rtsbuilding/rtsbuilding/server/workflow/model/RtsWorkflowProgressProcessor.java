package com.rtsbuilding.rtsbuilding.server.workflow.model;

/**
 * 工作流进度展示的纯逻辑处理器。
 *
 * <p>只做<b>纯逻辑/纯数据</b>计算（进度条宽度、进度文本、lang key 推导），
 * 不触碰 {@code net.minecraft.network.chat.Component}——UI 文案解析与拼接由
 * client 层完成（见阶段三 3.1 边界净化，common 不依赖 UI 类）。
 */
public final class RtsWorkflowProgressProcessor {

    private RtsWorkflowProgressProcessor() {}

    /**
     * 工作流类型 lang key；空闲（null/非激活）返回空串。
     *
     * @return 完整 lang key；非激活返回空串
     */
    public static String typeLabelKey(RtsWorkflowStatus status) {
        if (status == null || !status.isActive()) return "";
        return status.typeLabelKey();
    }

    /** 进度文本（"已完成/总数"），纯数字无 UI 文案。 */
    public static String formatProgressText(RtsWorkflowStatus status) {
        if (status == null) return "";
        return status.progressText();
    }

    /** 进度条填充宽度（px），纯计算。 */
    public static int computeFillWidth(RtsWorkflowStatus status, int barWidth) {
        if (status == null || !status.isActive()) return 0;
        return Math.round(status.progress() * barWidth);
    }
}
