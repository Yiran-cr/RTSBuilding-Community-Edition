package com.rtsbuilding.rtsbuilding.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public final class RtsKeyMappings {

    private RtsKeyMappings() {}

    
    public static final String CATEGORY_FUNCTION = "key.categories.rtsbuilding.function";

    
    public static final String CATEGORY_CAMERA = "key.categories.rtsbuilding.camera";

    public static final KeyMapping OPEN_GEAR_MENU_KEY = new KeyMapping(
            "key.rtsbuilding.open_gear_menu",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_COMMA,
            CATEGORY_FUNCTION
    );

    
    public static final KeyMapping TOGGLE_DEBUG_OVERLAY_KEY = new KeyMapping(
            "key.rtsbuilding.toggle_debug_overlay",
            KeyConflictContext.GUI,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY_FUNCTION
    );

    
    public static final KeyMapping TOGGLE_CAMERA_MODE_KEY = new KeyMapping(
            "key.rtsbuilding.toggle_camera_mode",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY_FUNCTION
    );

    
    public static final KeyMapping CAMERA_ROTATE_KEY = new KeyMapping(
            "key.rtsbuilding.camera_rotate",
            KeyConflictContext.GUI,
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            CATEGORY_CAMERA
    );

    
    public static final KeyMapping CAMERA_PAN_KEY = new KeyMapping(
            "key.rtsbuilding.camera_pan",
            KeyConflictContext.GUI,
            KeyModifier.SHIFT,
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            CATEGORY_CAMERA
    );

    
    public static final KeyMapping MOVE_PLAYER_KEY = new KeyMapping(
            "key.rtsbuilding.move_player",
            KeyConflictContext.GUI,
            KeyModifier.ALT,
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            CATEGORY_FUNCTION
    );

    
    public static final KeyMapping TOGGLE_SELECT_MODE_KEY = new KeyMapping(
            "key.rtsbuilding.toggle_select_mode",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_T,
            CATEGORY_FUNCTION
    );

    
    public static final KeyMapping TOGGLE_BIND_MODE_KEY = new KeyMapping(
            "key.rtsbuilding.toggle_bind_mode",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY_FUNCTION
    );

    
    public static final KeyMapping TOGGLE_DIRECTION_ROTATE_MODE_KEY = new KeyMapping(
            "key.rtsbuilding.toggle_direction_rotate_mode",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY_FUNCTION
    );

    
    public static final KeyMapping TOGGLE_ITEM_PICKUP_MODE_KEY = new KeyMapping(
            "key.rtsbuilding.toggle_item_pickup_mode",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            CATEGORY_FUNCTION
    );

    
    public static final KeyMapping UNDO_KEY = new KeyMapping(
            "key.rtsbuilding.undo",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY_FUNCTION
    );

    
    public static final KeyMapping CYCLE_MODE_KEY = new KeyMapping(
            "key.rtsbuilding.cycle_mode",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB,
            CATEGORY_FUNCTION
    );

    /**
     * 按住时：点击模式将选中位置偏移到射线命中面外侧一格（默认左 Ctrl，可配置）。
     * 框选模式选点同样遵循该按键。
     */
    public static final KeyMapping PLACE_OFFSET_KEY = new KeyMapping(
            "key.rtsbuilding.place_offset",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            CATEGORY_FUNCTION
    );

    /**
     * 按住时：线模式画线强制平直（忽略高度差，线段保持水平，不产生斜度）。
     */
    public static final KeyMapping LINE_FLAT_KEY = new KeyMapping(
            "key.rtsbuilding.line_flat",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY_FUNCTION
    );

    /**
     * 循环切换形状填充模式（实心 → 空心 → 框架 → 实心），仅对体/圆柱/球生效。
     */
    public static final KeyMapping CYCLE_FILL_MODE_KEY = new KeyMapping(
            "key.rtsbuilding.cycle_fill_mode",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            CATEGORY_FUNCTION
    );

    /**
     * 按住时启用连锁挖掘（Ultimine），松开即停用。默认 `` ` ``（~）键。
     * 启用期间破坏侧形状画笔被禁用，左键直接触发连锁挖掘。
     */
    public static final KeyMapping ULTIMINE_KEY = new KeyMapping(
            "key.rtsbuilding.ultimine",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            CATEGORY_FUNCTION
    );

    /**
     * 检查 {@link #LINE_FLAT_KEY} 绑定的按键当前是否按下。
     * <p>直接读取绑定键的 GLFW 状态（键盘或鼠标），不依赖每 tick 的
     * {@code KeyMappingState} 更新（后者在自定义 Screen 中不可靠），
     * 同时跟随玩家在设置中的自定义绑定。</p>
     */
    public static boolean isLineFlatDown() {
        InputConstants.Key bound = LINE_FLAT_KEY.getKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (window == 0L) return false;
        if (bound.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, bound.getValue()) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(window, bound.getValue()) == GLFW.GLFW_PRESS;
    }

    /**
     * 检查 {@link #PLACE_OFFSET_KEY} 绑定的按键当前是否按下。
     * <p>直接读取绑定键的 GLFW 状态（键盘或鼠标），不依赖每 tick 的
     * {@code KeyMappingState} 更新（后者在自定义 Screen 中不可靠），
     * 同时跟随玩家在设置中的自定义绑定。</p>
     */
    public static boolean isPlaceOffsetDown() {
        InputConstants.Key bound = PLACE_OFFSET_KEY.getKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (window == 0L) return false;
        if (bound.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, bound.getValue()) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(window, bound.getValue()) == GLFW.GLFW_PRESS;
    }

    /**
     * 检查 {@link #ULTIMINE_KEY} 绑定的按键当前是否按住。
     * <p>直接读取绑定键的 GLFW 状态，不依赖每 tick 的 {@code KeyMappingState} 更新
     * （后者在自定义 Screen 中不可靠），同时跟随玩家在设置中的自定义绑定。</p>
     */
    public static boolean isUltimineDown() {
        InputConstants.Key bound = ULTIMINE_KEY.getKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (window == 0L) return false;
        if (bound.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, bound.getValue()) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(window, bound.getValue()) == GLFW.GLFW_PRESS;
    }
}
