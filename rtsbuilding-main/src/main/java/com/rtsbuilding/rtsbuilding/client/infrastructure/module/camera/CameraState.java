package com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera;

public final class CameraState {

    
    boolean enabled;
    boolean localReady;

    
    double anchorX, anchorY, anchorZ;
    double maxRadius;

    
    double localX, localY, localZ;
    double localHeightOffset;
    float localYaw, localPitch;

    
    double prevX, prevY, prevZ;
    float prevYaw, prevPitch;

    
    float rotateSensitivity = 5.00F;
    int inputSensitivityIndex = 2;
    
    float inputSensitivity = 1.0F;
    boolean invertPanX, invertPanY;

    
    float pendingPanX, pendingPanY;
    float pendingScroll;
    int pendingRotateSteps;
    float pendingRawRotateX, pendingRawRotateY;

    
    
    

    
    boolean orbitMode;

    
    double orbitAngle;

    
    double orbitPitch;

    
    double orbitRadius;

    
    double orbitTargetX;

    
    double orbitTargetY;

    
    double orbitTargetZ;

    
    double prevOrbitAngle, prevOrbitPitch, prevOrbitRadius;

    
    
    

    
    boolean savedBlockOrbitMode;
    double savedOrbitTargetX, savedOrbitTargetY, savedOrbitTargetZ;
    double savedOrbitAngle, savedOrbitPitch, savedOrbitRadius;

    
    
    

    public boolean isEnabled() { return this.enabled; }
    public boolean isLocalReady() { return this.localReady; }

    public double getLocalX() { return localX; }
    public double getLocalY() { return localY; }
    public double getLocalZ() { return localZ; }
    public double getHeightOffset() { return localHeightOffset; }
    public float getYaw() { return localYaw; }
    public float getPitch() { return localPitch; }

    public double getAnchorX() { return anchorX; }
    public double getAnchorY() { return anchorY; }
    public double getAnchorZ() { return anchorZ; }
    public double getMaxRadius() { return maxRadius; }

    public boolean isInvertPanX() { return invertPanX; }
    public boolean isInvertPanY() { return invertPanY; }
    public void setInvertPanX(boolean v) { this.invertPanX = v; }
    public void setInvertPanY(boolean v) { this.invertPanY = v; }
    public void toggleInvertPanX() { this.invertPanX = !this.invertPanX; }
    public void toggleInvertPanY() { this.invertPanY = !this.invertPanY; }

    
    public boolean isOrbitMode() { return orbitMode; }

    
    public double getOrbitTargetX() { return orbitTargetX; }
    
    public void setOrbitTargetX(double v) { this.orbitTargetX = v; }
    
    public double getOrbitTargetY() { return orbitTargetY; }
    
    public void setOrbitTargetY(double v) { this.orbitTargetY = v; }
    
    public double getOrbitTargetZ() { return orbitTargetZ; }
    
    public void setOrbitTargetZ(double v) { this.orbitTargetZ = v; }

    
    boolean playerOrbitMode;

    public boolean isPlayerOrbitMode() { return playerOrbitMode; }

    /**
     * 玩家实体环绕模式的镜头自动回正状态：
     * 用户拖拽旋转相机后松开，镜头平滑转回"玩家实体当前朝向的反方向"（玩家背后）。
     */
    boolean playerOrbitAutoReturn;

    /**
     * 自动回正的目标水平角（弧度），即玩家背后对应的 orbitAngle。
     */
    double playerOrbitReturnTarget;

    /**
     * 自动回正插值的起始水平角（弧度）。
     */
    double playerOrbitReturnFrom;

    /**
     * 自动回正动画开始时间戳（毫秒）。
     */
    long playerOrbitReturnStartMs;

    void setBounds(double x, double y, double z, double r) {
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
        this.maxRadius = r;
    }
}
