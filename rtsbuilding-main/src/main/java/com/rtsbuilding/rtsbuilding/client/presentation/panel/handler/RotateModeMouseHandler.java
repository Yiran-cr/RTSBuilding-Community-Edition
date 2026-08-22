package com.rtsbuilding.rtsbuilding.client.presentation.panel.handler;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.MouseClickEvent;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.LeftSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.pass.BoxSelector;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import com.rtsbuilding.rtsbuilding.client.state.FeatureAdjusterState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.CONSUMED;
import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.PASS;

/**
 * 方向旋转模式的右键旋转处理器（左栏「方向旋转」按钮启用时生效）。
 *
 * <p>旋转功能仅在「建造」模式可用（旋转按钮只在建造模式下显示），本处理器在
 * {@code P_BIND_LOGIC} 优先级注册（先于框选选择器与建造交互处理器），并遵循：</p>
 * <ul>
 *   <li><b>直接右键</b> = 水平旋转（绕竖直 Y 轴，改变方块水平朝向）；</li>
 *   <li><b>Shift + 右键</b> = 上下翻转（绕水平轴，轴随相机朝向选择 X/Z）；</li>
 *   <li><b>点击模式</b>：旋转鼠标指向的单个方块；</li>
 *   <li><b>框选模式</b>：框选完成后框内右键旋转整个框选区域（框保留，可连续旋转多次）。</li>
 * </ul>
 *
 * <p>Alt+右键为移动玩家手势（{@code MOVE_PLAYER_KEY}），不在此拦截；
 * Ctrl 在旋转模式下不参与旋转（保持原有「命中面外侧偏移」等行为）。</p>
 */
public final class RotateModeMouseHandler {

    private final RtsClientKernel kernel;

    public RotateModeMouseHandler(RtsClientKernel kernel) {
        this.kernel = kernel;
    }

    /**
     * 处理鼠标点击。仅当旋转模式激活且命中方块时消费事件；否则放行交给
     * 框选选择器 / 建造交互等后续处理器。
     */
    public EventResult handleMouseClick(MouseClickEvent event, BuilderScreen screen,
                                        LeftSidebarPanel leftSidebarPanel) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return PASS;
        if (screen == null || !screen.isBuildMode() || !screen.isDirectionRotateActive()) return PASS;
        if (screen.isMouseOverUiPanelApi(event.x(), event.y())) return PASS;
        if (!BuildInteractionHandler.isWorldArea(event.x(), event.y(), screen)) return PASS;
        // Alt+右键 = 移动玩家手势，不拦截；Shift+右键 = 上下翻转（pitch），直接右键 = 水平旋转（yaw）
        if (isAltDown()) return PASS;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return PASS;
        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray == null) return PASS;
        BlockHitResult hit = ray.raycastBlock(mc);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return PASS;

        // Shift+右键 = 上下翻转（pitch）；直接右键 = 水平旋转（yaw）
        boolean pitch = isShiftDown();

        if (screen.isClickButtonSelected()) {
            // 点击模式：旋转鼠标指向的单个方块
            RtsClientPacketGateway.sendRotateBlock(
                    hit.getBlockPos(), FeatureAdjusterState.getRotateDegrees(), pitch);
            return CONSUMED;
        }

        // 框选模式：框选完成后，框内右键旋转整个框选区域（框保留，可连续旋转）
        BoxSelector sel = kernel.renderPipeline().boxSelector;
        if (sel.getPhase() == BoxSelector.Phase.COMPLETE) {
            BlockPos min = sel.getMinCorner();
            BlockPos max = sel.getMaxCorner();
            if (min != null && max != null && isInBox(hit.getBlockPos(), min, max)) {
                RtsClientPacketGateway.sendRotateArea(
                        min, max, FeatureAdjusterState.getRotateDegrees(), pitch);
                return CONSUMED;
            }
        }
        // 框选未完成或点击框外：放行给框选选择器推进选点 / 重置
        return PASS;
    }

    /** 判断位置是否落在框选区域 [min, max) 内（与 {@code BuildInteractionHandler#isPosInSelection} 语义一致）。 */
    private static boolean isInBox(BlockPos pos, BlockPos min, BlockPos max) {
        if (pos == null) return false;
        return pos.getX() >= min.getX() && pos.getX() < max.getX()
                && pos.getY() >= min.getY() && pos.getY() < max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() < max.getZ();
    }

    private static boolean isAltDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return window != 0L && (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS);
    }

    private static boolean isShiftDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return window != 0L && (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);
    }
}
