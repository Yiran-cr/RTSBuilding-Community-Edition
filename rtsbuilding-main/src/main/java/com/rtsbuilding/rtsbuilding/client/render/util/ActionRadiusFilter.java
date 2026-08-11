package com.rtsbuilding.rtsbuilding.client.render.util;

import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import net.minecraft.core.BlockPos;

/**
 * 动作半径过滤工具：客户端预览与服务端 {@code RtsCameraManager.isWithinActionRange}
 * 保持一致，避免"高亮但点了没反应"。
 *
 * <p>供 {@link com.rtsbuilding.rtsbuilding.client.render.pass.UltiminePreviewPass} 与
 * {@link com.rtsbuilding.rtsbuilding.client.render.pass.LineBrushRenderPass} 共用，
 * 相机未启用时不做过滤（与服务端行为对齐）。</p>
 */
public final class ActionRadiusFilter {

    private ActionRadiusFilter() {
    }

    /** 判断方块是否落在当前相机锚点的动作半径（X/Z 半边长）内。 */
    public static boolean isWithinActionRadius(BlockPos pos) {
        RtsClientKernel kernel = RtsClientKernel.get();
        if (kernel == null) return true;
        CameraModule cam = kernel.module(CameraModule.class);
        if (cam == null || !cam.isCameraEnabled()) return true;
        var state = cam.getState();
        double dx = (pos.getX() + 0.5D) - state.getAnchorX();
        double dz = (pos.getZ() + 0.5D) - state.getAnchorZ();
        double halfExtent = state.getMaxRadius();
        return Math.abs(dx) <= halfExtent && Math.abs(dz) <= halfExtent;
    }
}
