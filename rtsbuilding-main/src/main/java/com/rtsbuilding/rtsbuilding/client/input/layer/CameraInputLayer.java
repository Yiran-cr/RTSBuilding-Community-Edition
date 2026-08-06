package com.rtsbuilding.rtsbuilding.client.input.layer;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.input.InputLayer;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.settings.KeyModifier;

public final class CameraInputLayer implements InputLayer {

    private final RtsClientKernel kernel;

    
    private int pressedButton = -1;

    
    private boolean pressedPan;

    
    private double accumulatedDragDistance = 0.0D;

    
    private boolean draggedPastThreshold = false;

    
    private static final double DRAG_THRESHOLD = 5.0D;

    public CameraInputLayer(RtsClientKernel kernel) {
        this.kernel = kernel;
    }

    @Override
    public boolean isActive() {
        CameraModule cam = kernel.module(CameraModule.class);
        return cam != null && cam.getState().isEnabled();
    }

    
    public boolean wasDragged(int button) {
        return button == this.pressedButton && this.draggedPastThreshold;
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!isActive()) return false;

        if (matchesBind(RtsKeyMappings.CAMERA_ROTATE_KEY, button)) {
            this.pressedButton = button;
            this.pressedPan = false;
            this.accumulatedDragDistance = 0.0D;
            this.draggedPastThreshold = false;
            return true;
        }
        if (matchesBind(RtsKeyMappings.CAMERA_PAN_KEY, button)) {
            this.pressedButton = button;
            this.pressedPan = true;
            this.accumulatedDragDistance = 0.0D;
            this.draggedPastThreshold = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (this.pressedButton == button) {
            boolean wasDragged = this.draggedPastThreshold;
            this.pressedButton = -1;
            this.pressedPan = false;
            this.accumulatedDragDistance = 0.0D;
            this.draggedPastThreshold = false;
            
            return wasDragged;
        }
        return false;
    }

    @Override
    public boolean onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!isActive()) return false;

        if (button != this.pressedButton) return false;

        CameraModule cam = kernel.module(CameraModule.class);
        if (cam == null) return false;

        
        this.accumulatedDragDistance += Math.sqrt(dragX * dragX + dragY * dragY);
        if (this.accumulatedDragDistance >= DRAG_THRESHOLD) {
            this.draggedPastThreshold = true;
        }

        if (this.accumulatedDragDistance < DRAG_THRESHOLD) {
            return true;
        }

        if (this.pressedPan) {
            cam.queueDragMove(dragX, dragY);
        } else {
            cam.queueRotateDrag(dragX, dragY);
        }
        return true;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        CameraModule cam = kernel.module(CameraModule.class);
        if (cam == null) return false;

        cam.queueScroll(scrollY);
        return true;
    }

    /**
     * 判断给定鼠标按钮是否匹配某个相机操作键。
     * <p>鼠标按钮命中后，无修饰键要求的绑定直接放行（拖拽时允许自然按住修饰键），
     * 有修饰键要求的绑定（如平移默认 Shift+右键）做严格修饰校验，
     * 避免与右键点击交互冲突。</p>
     */
    private static boolean matchesBind(KeyMapping mapping, int button) {
        InputConstants.Key bound = mapping.getKey();
        if (bound.getType() != InputConstants.Type.MOUSE) return false;
        if (bound.getValue() != button) return false;
        KeyModifier required = mapping.getKeyModifier();
        if (required == KeyModifier.NONE) return true;
        boolean ctrl = Screen.hasControlDown();
        boolean alt = Screen.hasAltDown();
        boolean shift = Screen.hasShiftDown();
        return switch (required) {
            case SHIFT -> shift && !ctrl && !alt;
            case CONTROL -> ctrl && !alt && !shift;
            case ALT -> alt && !ctrl && !shift;
            default -> false;
        };
    }
}
