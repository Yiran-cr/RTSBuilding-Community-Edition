package com.rtsbuilding.rtsbuilding.server.workflow.model;

import java.util.List;

public record RtsWorkflowStatus(
        RtsWorkflowType type,
        RtsWorkflowPriority priority,
        int totalBlocks,
        int completedBlocks,
        int failedBlocks,
        int remainingBlocks,
        float progress,
        int holdType,
        boolean isComplete,
        List<String> missingItems,
        String detailMessage,
        int entryId) {

    public static RtsWorkflowStatus fromRaw(
            RtsWorkflowType type, RtsWorkflowPriority priority,
            int totalBlocks, int completedBlocks, int failedBlocks,
            List<String> missingItems, String detailMessage,
            int holdType, int entryId) {
        int remaining = totalBlocks > 0
                ? Math.max(0, totalBlocks - (completedBlocks + failedBlocks))
                : 0;
        float progress = totalBlocks > 0
                ? Math.min(1.0F, (float) (completedBlocks + failedBlocks) / (float) totalBlocks)
                : 0.0F;
        boolean isComplete = totalBlocks > 0
                && (completedBlocks + failedBlocks) >= totalBlocks;
        return new RtsWorkflowStatus(type, priority, totalBlocks, completedBlocks,
                failedBlocks, remaining, progress, holdType, isComplete,
                missingItems == null ? List.of() : List.copyOf(missingItems),
                detailMessage == null ? "" : detailMessage, entryId);
    }

    public static RtsWorkflowStatus idle() {
        return new RtsWorkflowStatus(null, RtsWorkflowPriority.NORMAL,
                0, 0, 0, 0, 0.0F, 0, false,
                List.of(), "", -1);
    }

    public boolean isActive() {
        return type != null;
    }

    /** {@code true} 表示处于搁置状态（挂起或手动暂停）。 */
    public boolean onHold() {
        return holdType != 0;
    }

    /**
     * 搁置类型：0=运行中，1=手动暂停（paused），2=等待材料/工具挂起（suspended）。
     * <p>客户端据此区分「继续」按钮的语义：挂起→弹恢复面板；暂停→直接解除暂停。</p>
     */
    public int holdType() {
        return holdType;
    }

    public boolean hasMissingItems() {
        return !missingItems.isEmpty();
    }

    public boolean hasFailures() {
        return failedBlocks > 0;
    }

    public String progressText() {
        return completedBlocks + "/" + (totalBlocks > 0 ? totalBlocks : 0);
    }

    /**
     * 工作流类型对应的 lang key（如 {@code screen.rtsbuilding.workflow.type.area_mine}）。
     *
     * <p>纯逻辑层只返回 key，不触碰 {@link net.minecraft.network.chat.Component}（UI 文案由
     * client 层统一 {@code Component.translatable(key)} 解析，见阶段三 3.1 边界净化）。
     *
     * @return 完整 lang key；type 为 null（空闲）时返回 idle key
     */
    public String typeLabelKey() {
        if (type == null) return "screen.rtsbuilding.workflow.type.idle";
        String key = switch (type) {
            case MINE_SINGLE  -> "mine_single";
            case ULTIMINE     -> "ultimine";
            case AREA_MINE    -> "area_mine";
            case AREA_DESTROY -> "area_destroy";
            case PLACE_SINGLE -> "place_single";
            case PLACE_BATCH  -> "place_batch";
            case QUICK_BUILD  -> "quick_build";
            case BLUEPRINT_BUILD -> "blueprint_build";
            case STOP_MINING  -> "stop_mining";
        };
        return "screen.rtsbuilding.workflow.type." + key;
    }
}
