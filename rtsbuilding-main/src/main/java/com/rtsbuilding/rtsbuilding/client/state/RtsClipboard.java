package com.rtsbuilding.rtsbuilding.client.state;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;

/**
 * 客户端框选剪贴板（建造模式框选范围 Ctrl+C / Ctrl+X / Ctrl+V）。
 *
 * <p>持有一份 {@link RtsBlueprint}（由 {@code BlueprintWriters.capture} 捕获框选区域的
 * 方块相对坐标 + 状态 + 方块实体 NBT + 材料），供粘贴时经 {@code PLACE_BLUEPRINT}
 * 工作流逐格建造。剪贴板为纯客户端内存状态：新复制覆盖旧内容，退出 RTS 界面不清空
 * （与原版剪贴板语义一致，跨会话保留直至再次复制）。</p>
 */
public final class RtsClipboard {

    /** 当前剪贴板内容；未复制过为 {@code null}。 */
    private static RtsBlueprint clipboard;

    private RtsClipboard() {
    }

    /** 覆盖写入剪贴板。 */
    public static void set(RtsBlueprint blueprint) {
        clipboard = blueprint;
    }

    /** 读取剪贴板内容；未复制过返回 {@code null}。 */
    public static RtsBlueprint get() {
        return clipboard;
    }

    /** 剪贴板是否包含可粘贴的方块。 */
    public static boolean hasContent() {
        return clipboard != null && !clipboard.blocks().isEmpty();
    }

    /** 清空剪贴板。 */
    public static void clear() {
        clipboard = null;
    }
}
