package com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

final class PlayerOrbitCameraMode {

    
    
    

    private static final float ROT_INPUT_CLAMP = 20.0F;
    private static final float ROTATE_GAIN_X = 0.24F;
    private static final float ROTATE_GAIN_Y = 0.22F;
    private static final double DOLLY_PER_SCROLL = 2.6D;

    /** 镜头自动回正动画时长（毫秒） */
    private static final long AUTO_RETURN_ANIM_MS = 500L;

    
    
    

    
    void init(CameraState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double tx = mc.player.getX();
        double ty = mc.player.getY() + mc.player.getEyeHeight();
        double tz = mc.player.getZ();
        state.orbitTargetX = tx;
        state.orbitTargetY = ty;
        state.orbitTargetZ = tz;

        double dx = state.localX - tx;
        double dy = state.localY - ty;
        double dz = state.localZ - tz;
        state.orbitRadius = Math.sqrt(dx * dx + dy * dy + dz * dz);
        state.orbitAngle = Math.atan2(dx, dz);
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        state.orbitPitch = Math.atan2(dy, distXZ);
        state.orbitRadius = Math.max(1.0, state.orbitRadius);
    }

    
    
    

    
    void processInput(CameraState state, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 用户主动操作相机（拖拽旋转/平移）时取消自动回正
        if (state.playerOrbitAutoReturn) {
            boolean hasUserInput = state.pendingRawRotateX != 0.0F
                    || state.pendingRawRotateY != 0.0F
                    || state.pendingPanX != 0.0F
                    || state.pendingPanY != 0.0F;
            if (hasUserInput) {
                state.playerOrbitAutoReturn = false;
            } else {
                advanceAutoReturn(state);
            }
        }

        
        double playerX = Mth.lerp(partialTick, mc.player.xo, mc.player.getX());
        double playerY = Mth.lerp(partialTick, mc.player.yo, mc.player.getY()) + mc.player.getEyeHeight();
        double playerZ = Mth.lerp(partialTick, mc.player.zo, mc.player.getZ());
        state.orbitTargetX = playerX;
        state.orbitTargetY = playerY;
        state.orbitTargetZ = playerZ;

        
        float rawX = Mth.clamp(state.pendingRawRotateX, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float rawY = Mth.clamp(state.pendingRawRotateY, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float panX = Mth.clamp(state.pendingPanX, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float panY = Mth.clamp(state.pendingPanY, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float sensScale = state.inputSensitivity;

        state.orbitAngle += (rawX + panX) * state.rotateSensitivity * sensScale * ROTATE_GAIN_X * 0.01;
        state.orbitPitch += (rawY + panY) * state.rotateSensitivity * sensScale * ROTATE_GAIN_Y * 0.01;
        
        if (state.pendingScroll != 0.0F) {
            double scroll = state.pendingScroll * DOLLY_PER_SCROLL;
            state.orbitRadius = Math.max(1.0, state.orbitRadius - scroll);
        }

        
        double sinAngle = Math.sin(state.orbitAngle);
        double cosAngle = Math.cos(state.orbitAngle);
        double cosPitch = Math.cos(state.orbitPitch);
        double sinPitch = Math.sin(state.orbitPitch);

        double tx = state.orbitTargetX;
        double ty = state.orbitTargetY;
        double tz = state.orbitTargetZ;
        double r = state.orbitRadius;

        state.localX = tx + r * sinAngle * cosPitch;
        state.localY = ty + r * sinPitch;
        state.localZ = tz + r * cosAngle * cosPitch;
        state.localHeightOffset = state.localY - state.anchorY;

        
        state.localYaw = Mth.wrapDegrees(180.0f - (float) Math.toDegrees(state.orbitAngle));
        state.localPitch = Mth.wrapDegrees((float) Math.toDegrees(state.orbitPitch));

        
        state.pendingRawRotateX = 0;
        state.pendingRawRotateY = 0;
        state.pendingPanX = 0;
        state.pendingPanY = 0;
        state.pendingScroll = 0;
    }

    /**
     * 启动镜头自动回正动画：以玩家实体当前朝向的反方向（玩家背后）为目标。
     * <p>玩家实体 yaw 决定面朝方向，相机环绕在玩家背后（与面朝相反一侧）观察，
     * 类似第三人称游戏松手后镜头回正。</p>
     */
    void startAutoReturn(CameraState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        state.playerOrbitReturnFrom = state.orbitAngle;
        // 玩家背后 = 玩家面朝反方向。实体 lookAngle 水平分量 = (-sin(yaw), cos(yaw))，
        // 相机相对玩家方向应为面朝反方向，即 (-(-sin), -(cos)) → orbitAngle=atan2(sin(yaw), -cos(yaw))
        float yawDeg = mc.player.getYRot();
        double yawRad = Math.toRadians(yawDeg);
        state.playerOrbitReturnTarget = Math.atan2(Math.sin(yawRad), -Math.cos(yawRad));
        state.playerOrbitReturnStartMs = net.minecraft.Util.getMillis();
        state.playerOrbitAutoReturn = true;
    }

    /**
     * 逐帧推进自动回正插值：水平角走最短弧，缓出曲线，到目标后自动停止。
     */
    private void advanceAutoReturn(CameraState state) {
        long elapsed = net.minecraft.Util.getMillis() - state.playerOrbitReturnStartMs;
        double t = Math.min(1.0D, elapsed / (double) AUTO_RETURN_ANIM_MS);
        double eased = 1.0D - Math.pow(1.0D - t, 3);

        state.orbitAngle = lerpAngleShortest(
                state.playerOrbitReturnFrom, state.playerOrbitReturnTarget, eased);

        if (t >= 1.0D) {
            state.playerOrbitAutoReturn = false;
        }
    }

    /**
     * 角度插值，结果保持在与 from 相差不超过半圈的最短路径上。
     */
    private static double lerpAngleShortest(double from, double to, double t) {
        double delta = Math.atan2(Math.sin(to - from), Math.cos(to - from));
        return from + delta * t;
    }
}
