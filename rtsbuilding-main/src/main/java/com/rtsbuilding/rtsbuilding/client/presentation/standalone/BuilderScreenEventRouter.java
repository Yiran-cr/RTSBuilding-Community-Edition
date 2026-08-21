package com.rtsbuilding.rtsbuilding.client.presentation.standalone;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.presentation.event.dispatcher.EventDispatcher;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.KeyPressEvent;
import com.rtsbuilding.rtsbuilding.client.presentation.layout.PanelRegistry;
import com.rtsbuilding.uifw.window.window.FloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.gear.GearMenuPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.handler.BindModeMouseHandler;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.handler.BuildInteractionHandler;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.handler.BuilderScreenMovementHandler;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.handler.EntityInteractionHandler;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.handler.RotateModeMouseHandler;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.LeftSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.CONSUMED;
import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.PASS;

public final class BuilderScreenEventRouter {

    /** Ctrl+Z 最小触发间隔（毫秒）：GLFW 按住按键时以 repeat 形式每 tick 触发，这里限流防止连发 */
    private static final long UNDO_MIN_INTERVAL_MS = 250L;

    private static long lastUndoPressMs = 0L;

    private final SuperScreen superScreen;

    public interface SuperScreen {
        boolean mouseClicked(double x, double y, int button);
        boolean mouseReleased(double x, double y, int button);
        boolean mouseDragged(double x, double y, int button, double dx, double dy);
        boolean mouseScrolled(double x, double y, double scrollX, double scrollY);
        boolean keyPressed(int keyCode, int scanCode, int modifiers);
        boolean charTyped(char codePoint, int modifiers);
        void mouseMoved(double x, double y);
    }

    public BuilderScreenEventRouter(SuperScreen superScreen) {
        this.superScreen = superScreen;
    }

    public void registerAll(EventDispatcher dispatcher, PanelRegistry panelRegistry,
                            BuilderScreen screen, RtsClientKernel kernel,
                            FloatingWindowLayer floatingWindowLayer,
                            TopBarPanel topBarPanel, LeftSidebarPanel leftSidebarPanel,
                            GearMenuPanel gearMenuPanel,
                            BuilderScreenMovementHandler movementHandler,
                            BindModeMouseHandler bindModeHandler,
                            EntityInteractionHandler entityInteractionHandler,
                            BuildInteractionHandler buildInteractionHandler,
                            RotateModeMouseHandler rotateModeHandler) {
        registerMouseClickHandlers(dispatcher, screen, kernel, floatingWindowLayer,
                panelRegistry, leftSidebarPanel, movementHandler, bindModeHandler,
                entityInteractionHandler, buildInteractionHandler, topBarPanel, rotateModeHandler);
        registerMouseReleaseHandlers(dispatcher, panelRegistry, floatingWindowLayer, kernel,
                buildInteractionHandler, screen, topBarPanel, leftSidebarPanel);
        registerMouseDragHandlers(dispatcher, panelRegistry, floatingWindowLayer, kernel);
        registerMouseScrollHandlers(dispatcher, panelRegistry, floatingWindowLayer, kernel,
                leftSidebarPanel, screen);
        registerKeyPressHandlers(dispatcher, floatingWindowLayer, panelRegistry,
                kernel, topBarPanel, leftSidebarPanel, gearMenuPanel,
                movementHandler, bindModeHandler, entityInteractionHandler, screen);
        registerCharHandlers(dispatcher, panelRegistry, floatingWindowLayer, kernel);
        registerMouseMoveHandlers(dispatcher, floatingWindowLayer);
    }

    private void registerMouseClickHandlers(EventDispatcher d, BuilderScreen screen,
            RtsClientKernel kernel, FloatingWindowLayer fw, PanelRegistry pr,
            LeftSidebarPanel lb, BuilderScreenMovementHandler mh,
            BindModeMouseHandler bmh, EntityInteractionHandler eih,
            BuildInteractionHandler bih, TopBarPanel topBar,
            RotateModeMouseHandler rotateHandler) {
        d.onMouseClick(event -> {
            screen.unfocusGridSearch();
            return PASS;
        }, EventDispatcher.P_FLOATING_WINDOW);

        d.onMouseClick(event -> {
            // 点击面板内部：交给浮动窗口处理并消费；点击外部：放行（不关闭面板、不吞事件），
            // 世界/左栏/顶栏等操作正常生效，面板仅通过 ESC、关闭按钮或容器关闭退出。
            if (fw.mouseClicked(event.x(), event.y(), event.button())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_FLOATING_WINDOW);

        pr.registerContentPanelMouseClick(d);

        d.onMouseClick(event -> {
            // 蓝图放置模式：左键两段式交互 —— 第一次按下定点键把当前瞄准锚点定点，
            // 第二次按下确认建造（Esc 先解除定点再取消模式）；默认左键，可经设置面板改绑；
            // 改绑为键盘键时鼠标不触发（改由按键分支处理），其余鼠标键消费掉防误触。
            if (screen.isBlueprintPlacementActive()
                    && !screen.isMouseOverUI(event.x(), event.y())) {
                if (isBlueprintPlaceButton(event.button())) {
                    if (screen.isBlueprintPlacementPinned()) {
                        var anchor = screen.getPlacementAnchor();
                        if (anchor != null) {
                            screen.confirmBlueprintPlacement(anchor);
                            return CONSUMED;
                        }
                    } else {
                        screen.pinBlueprintPlacement();
                        return CONSUMED;
                    }
                } else {
                    return CONSUMED;
                }
            }
            return PASS;
        }, EventDispatcher.P_BIND_LOGIC);

        d.onMouseClick(event ->
                bmh.handleMouseClick(event, screen, lb),
                EventDispatcher.P_BIND_LOGIC);

        // 方向旋转模式：右键旋转（点击模式单方块 / 框选模式整框），
        // 注册在框选选择器（P_SELECTION）之前，旋转生效时优先于框选选点/重置
        d.onMouseClick(event ->
                rotateHandler.handleMouseClick(event, screen, lb),
                EventDispatcher.P_BIND_LOGIC);

        d.onMouseClick(event -> {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                    && !isAltDown() && !isShiftDown()
                    && !lb.isClickButtonSelected()) {
                var boxSelector = kernel.renderPipeline().boxSelector;
                // 蓝图模式：框选完成后右键框内点击 = 打开蓝图保存对话框（生成蓝图）。
                // 先处理框选状态机（选点推进/框外重置），再判断是否满足触发条件。
                boxSelector.handleRightClickWithHover();
                if (screen.tryOpenBlueprintSave()) {
                    return CONSUMED;
                }
                return CONSUMED;
            }
            return PASS;
        }, EventDispatcher.P_SELECTION);

        d.onMouseClick(event ->
                eih.handleMouseClick(event, screen, lb),
                EventDispatcher.P_ENTITY_INTERACT);

        d.onMouseClick(event ->
                bih.handleMouseClick(event, screen, lb, topBar),
                EventDispatcher.P_ENTITY_INTERACT);

        d.onMouseClick(event -> {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && isAltDown()) {
                if (mh.handleMovePlayerActionAt(screen)) return CONSUMED;
            }
            return PASS;
        }, EventDispatcher.P_MOVEMENT);

        d.onMouseClick(event -> {
            if (screen.isMouseOverUiPanelApi(event.x(), event.y())) return CONSUMED;
            if (kernel.inputPipeline().onMouseClicked(event.x(), event.y(), event.button())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_INPUT_PIPELINE);

        d.onMouseClick(event -> {
            if (superScreen.mouseClicked(event.x(), event.y(), event.button())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_FALLBACK);
    }

    private void registerMouseReleaseHandlers(EventDispatcher d, PanelRegistry pr,
            FloatingWindowLayer fw, RtsClientKernel kernel,
            BuildInteractionHandler bih, BuilderScreen screen, TopBarPanel topBar,
            LeftSidebarPanel leftSidebarPanel) {
        pr.registerContentPanelMouseRelease(d);

        d.onMouseRelease(event ->
                bih.handleMouseRelease(event, screen, topBar, leftSidebarPanel),
                EventDispatcher.P_BUILD_ACTION);

        d.onMouseRelease(event -> {
            if (fw.mouseReleased(event.x(), event.y(), event.button())) return CONSUMED;
            if (kernel.inputPipeline().onMouseReleased(event.x(), event.y(), event.button())) return CONSUMED;
            if (superScreen.mouseReleased(event.x(), event.y(), event.button())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_FALLBACK);
    }

    private void registerMouseDragHandlers(EventDispatcher d, PanelRegistry pr,
            FloatingWindowLayer fw, RtsClientKernel kernel) {
        pr.registerContentPanelMouseDrag(d);

        d.onMouseDrag(event -> {
            double clampedDx = Math.abs(event.dx()) > 200 ? 0 : event.dx();
            double clampedDy = Math.abs(event.dy()) > 200 ? 0 : event.dy();
            if (fw.mouseDragged(event.x(), event.y(), event.button(), event.dx(), event.dy())) return CONSUMED;
            if (kernel.inputPipeline().onMouseDragged(event.x(), event.y(), event.button(), clampedDx, clampedDy)) return CONSUMED;
            if (superScreen.mouseDragged(event.x(), event.y(), event.button(), event.dx(), event.dy())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_FALLBACK);
    }

    private void registerMouseScrollHandlers(EventDispatcher d, PanelRegistry pr,
            FloatingWindowLayer fw, RtsClientKernel kernel,
            LeftSidebarPanel lb, BuilderScreen screen) {
        pr.registerContentPanelMouseScroll(d);

        d.onMouseScroll(event -> {
            if (fw.mouseScrolled(event.x(), event.y(), event.scrollX(), event.scrollY())) return CONSUMED;
            // 蓝图放置模式：Ctrl+滚轮调整锚点高度（Y 偏移 ±1，定点后同样生效），消费掉避免落入相机远近缩放；
            // 裸滚轮不消费，恢复为相机远近缩放
            if (screen.isBlueprintPlacementActive()
                    && isCtrlDown()
                    && !screen.isMouseOverUI(event.x(), event.y())) {
                int dir = event.scrollY() > 0.0D ? 1 : -1;
                screen.adjustBlueprintPlacementHeight(dir);
                return CONSUMED;
            }
            var lineBrush = kernel.renderPipeline().lineBrush;
            boolean shift = isShiftDown();
            boolean alt = isAltDown();
            // 画线/线确认阶段：Shift+滚轮调起点高度，Shift+Alt+滚轮调终点高度（裸滚轮留给相机缩放）
            if (shift && (lineBrush.isPicking() || lineBrush.isAdjusting())) {
                int dir = event.scrollY() > 0.0D ? 1 : -1;
                if (alt) {
                    lineBrush.adjustEndHeight(dir);
                } else {
                    lineBrush.adjustStartHeight(dir);
                }
                return CONSUMED;
            }
            // 宽度阶段（面/体）：Shift+滚轮调宽度，Shift+Alt+滚轮双边延展
            if (shift && lineBrush.isWidthAdjusting()) {
                int dir = event.scrollY() > 0.0D ? 1 : -1;
                if (alt) {
                    lineBrush.adjustFaceBothSides(dir);
                } else {
                    lineBrush.adjustWidthExtend(dir);
                }
                return CONSUMED;
            }
            // 高度阶段（墙/体）：Shift+滚轮调高度（裸滚轮留给相机缩放）
            if (shift && lineBrush.isHeightAdjusting()) {
                int dir = event.scrollY() > 0.0D ? 1 : -1;
                lineBrush.adjustHeightExtend(dir);
                return CONSUMED;
            }
            // 球半径阶段：Shift+滚轮调半径（裸滚轮留给相机缩放）
            if (shift && lineBrush.isRadiusAdjusting()) {
                int dir = event.scrollY() > 0.0D ? 1 : -1;
                lineBrush.adjustSphereRadius(dir);
                return CONSUMED;
            }
            // 裸滚轮：不消费，交由下方框选/相机（inputPipeline）处理相机远近缩放
            if (!lb.isClickButtonSelected()
                    && kernel.renderPipeline().boxSelector.handleScroll(event.scrollY())) return CONSUMED;
            if (screen.isMouseOverUiPanelApi(event.x(), event.y())) return CONSUMED;
            if (kernel.inputPipeline().onMouseScrolled(event.x(), event.y(), event.scrollX(), event.scrollY())) return CONSUMED;
            if (superScreen.mouseScrolled(event.x(), event.y(), event.scrollX(), event.scrollY())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_FALLBACK);
    }

    private void registerKeyPressHandlers(EventDispatcher d,
            FloatingWindowLayer fw, PanelRegistry pr,
            RtsClientKernel kernel, TopBarPanel topBar, LeftSidebarPanel lb,
            GearMenuPanel gearMenu, BuilderScreenMovementHandler mh,
            BindModeMouseHandler bmh, EntityInteractionHandler eih,
            BuilderScreen screen) {
        d.onKeyPress(event -> {
            // 蓝图放置模式：Esc 逐级取消 —— 已定点先解除定点（恢复自由瞄准），再按才退出放置模式
            if (event.keyCode() == GLFW.GLFW_KEY_ESCAPE && screen.isBlueprintPlacementActive()) {
                if (screen.isBlueprintPlacementPinned()) {
                    screen.unpinBlueprintPlacement();
                } else {
                    screen.cancelBlueprintPlacement();
                }
                return CONSUMED;
            }
            // 蓝图放置模式：定点键改为键盘绑定（默认鼠标左键）时，按键触发定点/确认
            if (screen.isBlueprintPlacementActive()
                    && keyMatches(RtsKeyMappings.BLUEPRINT_PLACE_KEY, event)) {
                if (screen.isBlueprintPlacementPinned()) {
                    var anchor = screen.getPlacementAnchor();
                    if (anchor != null) {
                        screen.confirmBlueprintPlacement(anchor);
                        return CONSUMED;
                    }
                } else {
                    screen.pinBlueprintPlacement();
                    return CONSUMED;
                }
            }
            // 蓝图放置模式：旋转键（默认 R，无修饰键，避免与 Ctrl+R 方向旋转模式冲突）
            if (screen.isBlueprintPlacementActive()
                    && keyMatches(RtsKeyMappings.BLUEPRINT_ROTATE_KEY, event)) {
                screen.rotateBlueprintPlacement();
                return CONSUMED;
            }
            // 蓝图放置模式：四向偏移键（默认小键盘方向键，按相机朝向在水平面平移）
            if (screen.isBlueprintPlacementActive()) {
                CameraModule cam = kernel.module(CameraModule.class);
                float yaw = cam != null ? cam.getState().getYaw() : 0f;
                int[] dir = blueprintPlacementOffsetDir(event, yaw);
                if (dir != null) {
                    screen.offsetBlueprintPlacement(dir[0], dir[1]);
                    return CONSUMED;
                }
            }
            if (fw.keyPressed(event.keyCode(), event.scanCode(), event.modifiers())) return CONSUMED;
            if (event.keyCode() == GLFW.GLFW_KEY_ESCAPE && eih.isInteractionPanelOpen()) {
                eih.closeInteractionPanel();
                return CONSUMED;
            }
            // 线/墙/面画笔活跃阶段按 ESC：逐级取消当前阶段，每按一次只回退一个阶段，
            // 不退出 RTS 模式；全部取消后再按 ESC 才走下方 P_FALLBACK 的关闭逻辑退出 RTS。
            if (event.keyCode() == GLFW.GLFW_KEY_ESCAPE
                    && kernel.renderPipeline().lineBrush.isActive()) {
                kernel.renderPipeline().lineBrush.cancelStage();
                return CONSUMED;
            }
            return PASS;
        }, EventDispatcher.P_FLOATING_WINDOW);

        pr.registerContentPanelKeyPress(d);

        d.onKeyPress(event -> handleShortcut(event, kernel, topBar, lb, gearMenu, screen),
                EventDispatcher.P_UI_PANEL);

        d.onKeyPress(event ->
                bmh.handleKeyPress(event, lb),
                EventDispatcher.P_BIND_LOGIC);

        d.onKeyPress(event -> {
            if (kernel.inputPipeline().onKeyPressed(event.keyCode(), event.scanCode(), event.modifiers())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_INPUT_PIPELINE);

        d.onKeyPress(event -> {
            if (superScreen.keyPressed(event.keyCode(), event.scanCode(), event.modifiers())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_FALLBACK);
    }

    /**
     * 蓝图放置偏移方向：四向偏移键 → 相机朝向水平面上的屏幕方向偏移（一格）。
     * 屏幕「上」= 相机前向的水平分量，屏幕「右」= 前向水平分量顺时针旋转 90°；
     * 四舍五入到整格（45° 倍方向时得到对角），兼容自由相机任意朝向。
     *
     * @param event 按键事件（四向偏移键按自定义绑定匹配）
     * @param yaw   相机偏航角（度，CameraState.getYaw()）
     * @return {dx, dz} 水平偏移向量；非偏移键返回 null
     */
    private static int[] blueprintPlacementOffsetDir(KeyPressEvent event, float yaw) {
        double rad = Math.toRadians(yaw);
        int[] fwd = { (int) Math.round(-Math.sin(rad)), (int) Math.round(Math.cos(rad)) };
        int[] right = { (int) Math.round(-Math.cos(rad)), (int) Math.round(-Math.sin(rad)) };
        if (keyMatches(RtsKeyMappings.BLUEPRINT_MOVE_UP_KEY, event)) {
            return new int[] { fwd[0], fwd[1] };
        }
        if (keyMatches(RtsKeyMappings.BLUEPRINT_MOVE_DOWN_KEY, event)) {
            return new int[] { -fwd[0], -fwd[1] };
        }
        if (keyMatches(RtsKeyMappings.BLUEPRINT_MOVE_LEFT_KEY, event)) {
            return new int[] { -right[0], -right[1] };
        }
        if (keyMatches(RtsKeyMappings.BLUEPRINT_MOVE_RIGHT_KEY, event)) {
            return new int[] { right[0], right[1] };
        }
        return null;
    }

    /**
     * 蓝图放置：定点/确认键是否匹配当前鼠标按钮。
     * <p>绑定为鼠标键时按按钮匹配并校验修饰键；绑定为键盘键时返回 false
     * （定点/确认改由按键事件分支处理，避免鼠标误触）。</p>
     */
    private static boolean isBlueprintPlaceButton(int mouseButton) {
        KeyMapping mapping = RtsKeyMappings.BLUEPRINT_PLACE_KEY;
        InputConstants.Key bound = mapping.getKey();
        if (bound.getType() != InputConstants.Type.MOUSE || bound.getValue() != mouseButton) {
            return false;
        }
        return switch (mapping.getKeyModifier()) {
            case SHIFT -> isShiftDown() && !isCtrlDown() && !isAltDown();
            case CONTROL -> isCtrlDown() && !isAltDown() && !isShiftDown();
            case ALT -> isAltDown() && !isCtrlDown() && !isShiftDown();
            case NONE -> !isCtrlDown() && !isAltDown() && !isShiftDown();
        };
    }

    private void registerCharHandlers(EventDispatcher d, PanelRegistry pr,
            FloatingWindowLayer fw, RtsClientKernel kernel) {
        pr.registerContentPanelCharTyped(d);

        d.onChar(event -> {
            if (fw.charTyped(event.codePoint(), event.modifiers())) return CONSUMED;
            if (kernel.inputPipeline().onCharTyped(event.codePoint(), event.modifiers())) return CONSUMED;
            if (superScreen.charTyped(event.codePoint(), event.modifiers())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_FALLBACK);
    }

    private void registerMouseMoveHandlers(EventDispatcher d, FloatingWindowLayer fw) {
        d.onMouseMove(event -> {
            if (fw != null) fw.mouseMoved(event.x(), event.y());
            superScreen.mouseMoved(event.x(), event.y());
            return CONSUMED;
        }, EventDispatcher.P_FALLBACK);
    }

    private EventResult handleShortcut(KeyPressEvent event, RtsClientKernel kernel,
            TopBarPanel topBar, LeftSidebarPanel lb, GearMenuPanel gearMenu,
            BuilderScreen screen) {
        if (keyMatches(RtsKeyMappings.OPEN_GEAR_MENU_KEY, event)) {
            gearMenu.toggleOpen();
            topBar.setGearMenuOpen(gearMenu.isOpen());
            return CONSUMED;
        }
        if (keyMatches(RtsKeyMappings.TOGGLE_DEBUG_OVERLAY_KEY, event)) {
            topBar.toggleDebugOverlay();
            return CONSUMED;
        }
        if (keyMatches(RtsKeyMappings.TOGGLE_CAMERA_MODE_KEY, event)) {
            CameraModule cam = kernel.module(CameraModule.class);
            if (cam != null) cam.togglePlayerOrbitMode();
            return CONSUMED;
        }
        if (keyMatches(RtsKeyMappings.TOGGLE_SELECT_MODE_KEY, event)) {
            lb.toggleSelectMode();
            if (lb.isClickButtonSelected()) screen.clearBoxSelection();
            return CONSUMED;
        }
        if (keyMatches(RtsKeyMappings.TOGGLE_BIND_MODE_KEY, event)) {
            lb.toggleBindMode();
            return CONSUMED;
        }
        if (keyMatches(RtsKeyMappings.TOGGLE_DIRECTION_ROTATE_MODE_KEY, event)) {
            lb.toggleDirectionRotateMode();
            return CONSUMED;
        }
        if (keyMatches(RtsKeyMappings.TOGGLE_ITEM_PICKUP_MODE_KEY, event)) {
            lb.toggleItemPickupMode();
            return CONSUMED;
        }
        if (keyMatches(RtsKeyMappings.TOGGLE_RAY_CULLING_KEY, event)) {
            com.rtsbuilding.rtsbuilding.client.culling.RtsRayCylinderCullingState.toggle();
            return CONSUMED;
        }
        if (keyMatches(RtsKeyMappings.CYCLE_FILL_MODE_KEY, event)) {
            // 只循环当前激活形状（体/球/圆柱）的填充模式，各形状独立记忆
            com.rtsbuilding.rtsbuilding.client.rtsbuild.shape.BuildShape shape = screen.getActiveBuildShape();
            if (shape != null) {
                kernel.renderPipeline().lineBrush.cycleFillModeFor(shape);
            }
            return CONSUMED;
        }
        // 建造模式 + 框选模式：Ctrl+C 复制 / Ctrl+X 剪切 / Ctrl+V 粘贴框选范围方块。
        // 严格校验修饰键（纯 Ctrl，不带 Alt/Shift），输入框编辑态已由前置面板/浮动窗口消费。
        int mods = event.modifiers();
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0 && (mods & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SHIFT)) == 0) {
            if (event.keyCode() == GLFW.GLFW_KEY_C) {
                if (screen.copySelectionToClipboard()) return CONSUMED;
                return PASS;
            }
            if (event.keyCode() == GLFW.GLFW_KEY_X) {
                if (screen.cutSelectionToClipboard()) return CONSUMED;
                return PASS;
            }
            if (event.keyCode() == GLFW.GLFW_KEY_V) {
                if (screen.pasteClipboard()) return CONSUMED;
                return PASS;
            }
        }
        if (keyMatches(RtsKeyMappings.CYCLE_MODE_KEY, event)) {
            topBar.cycleMode();
            return CONSUMED;
        }
        if (keyMatches(RtsKeyMappings.UNDO_KEY, event)) {
            long now = System.currentTimeMillis();
            if (now - lastUndoPressMs >= UNDO_MIN_INTERVAL_MS) {
                lastUndoPressMs = now;
                // 仅建造模式可撤销，避免其他模式下误触回滚
                BuildingModule buildingModule = kernel.module(BuildingModule.class);
                if (buildingModule != null && buildingModule.getMode() == BuilderMode.BUILD) {
                    RtsClientPacketGateway.sendUndo();
                }
            }
            return CONSUMED;
        }
        if (event.keyCode() == GLFW.GLFW_KEY_ENTER || event.keyCode() == GLFW.GLFW_KEY_KP_ENTER) {
            // 蓝图模式框选完成：回车打开蓝图保存对话框（生成蓝图）
            if (screen.tryOpenBlueprintSave()) {
                return CONSUMED;
            }
            return PASS;
        }
        return PASS;
    }

    /**
     * 匹配按键绑定并显式校验修饰键。
     * <p>
     * NeoForge 的 {@link KeyMapping#matches(int, int)} 只匹配键码、不校验修饰键
     * （原版行为，补丁未修改），因此 Alt+Z 与 Ctrl+Z 等同一键码的不同组合键会互相误触。
     * 这里用事件携带的 GLFW 修饰位掩码做精确校验，保证组合键互不干扰。
     */
    private static boolean keyMatches(KeyMapping mapping, KeyPressEvent event) {
        if (!mapping.matches(event.keyCode(), event.scanCode())) return false;
        KeyModifier required = mapping.getKeyModifier();
        int mods = event.modifiers();
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean alt = (mods & GLFW.GLFW_MOD_ALT) != 0;
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        return switch (required) {
            case SHIFT -> shift && !ctrl && !alt;
            case CONTROL -> ctrl && !alt && !shift;
            case ALT -> alt && !ctrl && !shift;
            case NONE -> !ctrl && !alt && !shift;
        };
    }

    private static boolean isAltDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    private static boolean isShiftDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private static boolean isCtrlDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }
}
