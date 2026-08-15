package com.rtsbuilding.rtsbuilding.network.resume;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
import com.rtsbuilding.rtsbuilding.server.service.ResumeWorkflowService;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 注册工作流恢复相关的客户端请求与服务端回包。
 */
public final class RtsResumePackets {
    private RtsResumePackets() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                S2CResumeScanPayload.TYPE,
                S2CResumeScanPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchResume);

        registrar.playToServer(
                C2SRequestResumeScanPayload.TYPE,
                C2SRequestResumeScanPayload.STREAM_CODEC,
                (payload, ctx) -> {
                    if (!(ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp)) return;
                    var result = ResumeWorkflowService.scan(sp, payload.workflowEntryId());
                    com.rtsbuilding.rtsbuilding.RtsbuildingMod.LOGGER.info(
                            "[Resume] scan request entryId={} result={}",
                            payload.workflowEntryId(), result != null ? "OK(" + result.totalRemaining() + ")" : "NULL");
                    if (result != null) {
                        com.rtsbuilding.rtsbuilding.platform.Platform.sendPacket(sp, result);
                    }
                });

        registrar.playToServer(
                C2SResumeActionPayload.TYPE,
                C2SResumeActionPayload.STREAM_CODEC,
                (payload, ctx) -> {
                    if (!(ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp)) return;
                    ResumeWorkflowService.apply(sp, payload.workflowEntryId(), payload.strategy());
                });
    }
}
