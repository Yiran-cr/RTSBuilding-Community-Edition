package com.rtsbuilding.rtsbuilding.client.presentation.plugin.resume;

import com.rtsbuilding.rtsbuilding.network.resume.S2CResumeScanPayload;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端工作流恢复扫描结果的共享状态：网络 handler 写入，恢复面板与
 * 恢复预览线框 pass 读取。按工作流条目 ID 独立保存，支持多个工作流
 * 同时打开各自的恢复面板与线框预览。
 */
public final class ResumeWorkflowState {

    private static final Map<Integer, S2CResumeScanPayload> BY_ENTRY = new HashMap<>();

    private ResumeWorkflowState() {
    }

    public static void put(S2CResumeScanPayload payload) {
        if (payload != null) {
            BY_ENTRY.put(payload.entryId(), payload);
        }
    }

    public static void remove(int entryId) {
        BY_ENTRY.remove(entryId);
    }

    /** 当前所有打开的恢复扫描数据（各工作流独立）。 */
    public static Collection<S2CResumeScanPayload> getAll() {
        return List.copyOf(BY_ENTRY.values());
    }

    /** 指定工作流的恢复扫描数据，无则 {@code null}。 */
    public static S2CResumeScanPayload get(int entryId) {
        return BY_ENTRY.get(entryId);
    }
}
