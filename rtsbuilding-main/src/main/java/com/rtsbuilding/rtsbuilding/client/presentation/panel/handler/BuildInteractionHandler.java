package com.rtsbuilding.rtsbuilding.client.presentation.panel.handler;

import com.rtsbuilding.rtsbuilding.client.infrastructure.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.mining.MiningModule;
import com.rtsbuilding.rtsbuilding.client.input.layer.CameraInputLayer;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.MouseClickEvent;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.MouseReleaseEvent;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.background.ScreenBackgroundPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.LeftSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.ModeSwitcher;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.pass.BoxSelector;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.network.NetworkConstants;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.CONSUMED;
import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.PASS;

public final class BuildInteractionHandler {

    
    private static final byte ULTIMINE_MODE = 0;

    private final RtsClientKernel kernel;
    private final CameraInputLayer cameraInputLayer;

    private boolean miningActive;
    private int miningMouseButton = -1;

    /** 上次记录的框选阶段，用于检测框选确认（COMPLETE）沿以自动触发拾取。 */
    private BoxSelector.Phase lastBoxPhase = BoxSelector.Phase.IDLE;

    /** 框选持续吸收的重新扫描间隔（tick）。COMPLETE 期间周期性重扫，
     *  解决“框选确认时掉落物尚未生成/未落定导致一次性触发漏吸”的问题。 */
    private static final int BOX_PICKUP_INTERVAL = 20;

    /** 框选持续吸收的重新扫描倒计时。 */
    private int boxPickupCooldown;

    public BuildInteractionHandler(RtsClientKernel kernel, CameraInputLayer cameraInputLayer) {
        this.kernel = kernel;
        this.cameraInputLayer = cameraInputLayer;
    }

    
    public EventResult handleMouseClick(MouseClickEvent event, BuilderScreen screen,
                                         LeftSidebarPanel leftSidebarPanel, TopBarPanel topBarPanel) {
        int button = event.button();

        if (!isInBuildOrInteractiveMode(topBarPanel)) return PASS;
        if (screen.isMouseOverRtsPanelApi(event.x(), event.y())) return PASS;
        if (!isWorldArea(event.x(), event.y(), screen)) return PASS;
        if (leftSidebarPanel != null && leftSidebarPanel.isClickButtonSelected()
                && screen.isInteractiveMode()) return PASS;

        // 左键：挖掘
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !isAltDown()) {
            return handleLeftClick(screen, leftSidebarPanel) ? CONSUMED : PASS;
        }

        return PASS;
    }

    
    public EventResult handleMouseRelease(MouseReleaseEvent event, BuilderScreen screen,
                                           TopBarPanel topBarPanel,
                                           LeftSidebarPanel leftSidebarPanel) {
        int button = event.button();

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.miningActive) {
            stopMining();
            return CONSUMED;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !isAltDown() && !isShiftDown()
                && !screen.isMouseOverRtsPanelApi(event.x(), event.y())
                && isWorldArea(event.x(), event.y(), screen)
                && isInBuildOrInteractiveMode(topBarPanel)
                && !shouldSkipRightClickRelease(screen, leftSidebarPanel)) {
            
            if (!cameraInputLayer.wasDragged(button)) {
                return runPrimaryActionAt(screen, leftSidebarPanel);
            }
        }

        
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                && !screen.isMouseOverRtsPanelApi(event.x(), event.y())
                && isWorldArea(event.x(), event.y(), screen)) {
            if (!cameraInputLayer.wasDragged(button)) {
                tryPickHoveredBlockForPlacement(screen);
                return CONSUMED;
            }
        }

        return PASS;
    }

    
    /**
     * 每 tick 驱动：物品拾取（漏斗）自动触发，无需任何点击。<p>当物品拾取按钮启用且处于交互/建造/蓝图模式时：</p>
     * <ul>
     *   <li>点击模式：以鼠标指针指向的方块位置为圆心，直接请求服务端持续吸取周围掉落物（指针移动即更新目标）；</li>
     *   <li>框选模式：框选范围确认（COMPLETE）后立即吸取框内掉落物，
     *       并在 COMPLETE 期间每 {@link #BOX_PICKUP_INTERVAL} tick 周期性重新收集，
     *       直到框选被重置。</li>
     * </ul>
     */
    public void handleTick(BuilderScreen screen, LeftSidebarPanel leftSidebarPanel) {
        // 相机激活检查与服务端 RtsFunnelService.validate（RtsCameraManager.isActive）保持一致：
        // 未开启 RTS 相机时服务端会静默拒绝，这里直接不发包，避免无效请求
        if (leftSidebarPanel == null || !leftSidebarPanel.isItemPickupActive()
                || (!screen.isInteractiveMode() && !screen.isBlueprintMode() && !screen.isBuildMode())
                || !screen.isCameraActive()) {
            this.lastBoxPhase = BoxSelector.Phase.IDLE;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCameraEntity() == null) return;

        // 鼠标必须停留在世界区域，且不在任何 RTS 面板/浮动窗口上
        double rtsScale = screen.getRtsGuiScale();
        double mouseX = mc.mouseHandler.xpos() / rtsScale;
        double mouseY = mc.mouseHandler.ypos() / rtsScale;
        if (screen.isMouseOverRtsPanelApi(mouseX, mouseY)) return;
        if (!isWorldArea(mouseX, mouseY, screen)) return;

        // 点击模式：指针指向的方块为球心，持续拾取周围掉落物（无需点击）
        if (screen.isClickButtonSelected()) {
            var ray = CursorRaycaster.computeCursorRay(mc, screen);
            if (ray == null) return;
            BlockHitResult hit = ray.raycastBlock(mc);
            if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;
            RtsClientPacketGateway.sendFunnelPickup(hit.getBlockPos());
            return;
        }

        // 框选模式：框选确认完成后自动拾取框内掉落物。
        // COMPLETE 期间每 BOX_PICKUP_INTERVAL tick 周期性重新收集，
        // 覆盖“掉落物在框选确认之后才生成/落定”的漏吸场景；沿检测仅用于刚完成时立即拾取一次。
        BoxSelector.Phase phase = kernel.renderPipeline().boxSelector.getPhase();
        if (phase == BoxSelector.Phase.COMPLETE) {
            if (this.lastBoxPhase != BoxSelector.Phase.COMPLETE) {
                // 刚确认完成：立即拾取一次
                funnelPickupBox(screen);
                this.boxPickupCooldown = BOX_PICKUP_INTERVAL;
            } else if (--this.boxPickupCooldown <= 0) {
                // 持续期间：周期性重新拾取，框内无物品时不发包（funnelPickupBox 内部处理）
                funnelPickupBox(screen);
                this.boxPickupCooldown = BOX_PICKUP_INTERVAL;
            }
        } else {
            this.boxPickupCooldown = 0;
        }
        this.lastBoxPhase = phase;
    }

    /**
     * 框选模式：把框选区域内收集到的掉落物实体 ID 同步给服务端一次性吸取。
     */
    private void funnelPickupBox(BuilderScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var sel = kernel.renderPipeline().boxSelector;
        if (sel.getPhase() != BoxSelector.Phase.COMPLETE) return;
        BlockPos min = sel.getMinCorner();
        BlockPos max = sel.getMaxCorner();
        if (min == null || max == null) return;

        AABB box = new AABB(min.getX(), min.getY(), min.getZ(),
                max.getX(), max.getY(), max.getZ());
        List<Entity> items = mc.level.getEntities((Entity) null, box,
                e -> e instanceof ItemEntity ie && ie.isAlive() && !ie.getItem().isEmpty());
        if (items.isEmpty()) return;

        List<Integer> entityIds = new java.util.ArrayList<>(items.size());
        for (Entity entity : items) entityIds.add(entity.getId());
        RtsClientPacketGateway.sendFunnelBoxPickup(entityIds);
    }

    
    private boolean handleLeftClick(BuilderScreen screen, LeftSidebarPanel leftSidebarPanel) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        BuildingModule buildingModule = kernel.module(BuildingModule.class);
        if (buildingModule == null) return false;
        if (buildingModule.getMode() != BuilderMode.BUILD) return false;

        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray == null) return false;

        BlockHitResult hit = ray.raycastBlock(mc);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;

        MiningModule miningModule = kernel.module(MiningModule.class);
        if (miningModule == null) return false;

        String toolItemId = buildingModule.getSelectedItemId();
        int toolSlot = mc.player != null ? mc.player.getInventory().selected : 0;

        // 框选模式（非点击模式）：破坏机制与点击模式完全区分——
        // 仅当框选完成后在框内左键才对整个框选区域批量破坏；
        // 框选进行中或点击框外不执行单点挖掘。
        BoxSelector boxSelector = kernel.renderPipeline().boxSelector;
        if (!screen.isClickButtonSelected()) {
            if (boxSelector.getPhase() == BoxSelector.Phase.COMPLETE
                    && isPosInSelection(hit.getBlockPos(), boxSelector)) {
                RtsClientPacketGateway.sendAreaBoxDestroy(
                        boxSelector.getMinCorner(), boxSelector.getMaxCorner(),
                        toolSlot, toolItemId, false);
            }
            return true;
        }

        // 连锁挖掘按钮启用时：左键挖掘直接触发服务端连锁挖掘（一次点击一批，松开不中止）。
        // 服务端 ULTIMINE 流程（RtsUltimineProcessor）从种子位置 BFS 收集同类型连通方块处理。
        if (leftSidebarPanel != null && leftSidebarPanel.isUltimineActive()) {
            // D3：批次进行中再次点击 = 取消当前批次
            if (miningModule.getActivePos() != null) {
                miningModule.abortMining(toolSlot);
                return true;
            }
            miningModule.startUltimine(hit.getBlockPos(), hit.getDirection().get3DDataValue(),
                    toolSlot, RtsMiningValidator.ULTIMINE_MAX_BLOCKS, ULTIMINE_MODE,
                    toolItemId, false);
            return true;
        }

        // 形状按钮组：仅"破坏"侧"单方块"形状允许单方块挖掘；其余破坏形状（线/墙/平面/体/圆面/球）尚未实现，
        // 选中其他破坏形状时禁止单方块破坏（放置不受影响，走建造侧形状判断）。
        if (leftSidebarPanel != null && !leftSidebarPanel.isSingleBlockBreakShapeSelected()) {
            return true;
        }

        miningModule.startMining(hit.getBlockPos(), hit.getDirection().get3DDataValue(),
                toolSlot, toolItemId, false, false);
        this.miningActive = true;
        this.miningMouseButton = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        return true;
    }

    private void stopMining() {
        MiningModule miningModule = kernel.module(MiningModule.class);
        if (miningModule != null) {
            int toolSlot = Minecraft.getInstance().player != null
                    ? Minecraft.getInstance().player.getInventory().selected : 0;
            miningModule.abortMining(toolSlot);
        }
        this.miningActive = false;
        this.miningMouseButton = -1;
    }

    
    private EventResult runPrimaryActionAt(BuilderScreen screen, LeftSidebarPanel leftSidebarPanel) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return PASS;

        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray == null) return PASS;

        BlockHitResult hit = ray.raycastBlock(mc);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return PASS;

        BuildingModule buildingModule = kernel.module(BuildingModule.class);
        if (buildingModule == null) return PASS;

        boolean shiftDown = isShiftDown();
        boolean isBuildMode = buildingModule.getMode() == BuilderMode.BUILD;

        // 框选模式（非点击模式）：框选完成后，在框内右键 = 对整个框选区域批量放置，
        // 与点击模式的单点放置机制区分开。流体暂不支持批量，仍走单点放置。
        BoxSelector boxSelector = kernel.renderPipeline().boxSelector;
        if (!screen.isClickButtonSelected()
                && boxSelector.getPhase() == BoxSelector.Phase.COMPLETE
                && isPosInSelection(hit.getBlockPos(), boxSelector)) {
            if (!isBuildMode) return PASS;
            if (buildingModule.hasSelectedFluid()) {
                buildingModule.placeFluid(hit, shiftDown, ray.origin(), ray.direction());
                return CONSUMED;
            }
            if (buildingModule.hasSelectedItem()) {
                RtsClientPacketGateway.sendAreaBoxPlace(
                        boxSelector.getMinCorner(), boxSelector.getMaxCorner(),
                        (byte) buildingModule.getPlaceRotateSteps(), shiftDown, true,
                        buildingModule.getSelectedItemId(), ray.origin(), ray.direction());
                return CONSUMED;
            }
            return CONSUMED;
        }

        if (buildingModule.hasSelectedFluid()) {
            if (!isBuildMode) return PASS;
            buildingModule.placeFluid(hit, shiftDown, ray.origin(), ray.direction());
            return CONSUMED;
        }

        
        if (buildingModule.hasSelectedItem()) {
            if (!isBuildMode) return PASS;
            // 形状按钮组：仅"建造"侧"单方块"形状允许单方块放置；其余建造形状（线/墙/平面/体/圆面/球）尚未实现，
            // 选中其他建造形状时禁止单方块放置（破坏不受影响，走破坏侧形状判断）。
            if (leftSidebarPanel != null && !leftSidebarPanel.isSingleBlockBuildShapeSelected()) {
                return CONSUMED;
            }
            buildingModule.placeSelected(hit, shiftDown, ray.origin(), ray.direction());
            return CONSUMED;
        }

        
        if (isBuildMode) return PASS;

        
        if (buildingModule.isEmptyHandSelected()) {
            RtsClientPacketGateway.sendInteractEntityEmptyHand(
                    NetworkConstants.NO_ENTITY,
                    hit.getLocation(), hit, ray.origin(), ray.direction());
            return CONSUMED;
        }

        
        if (mc.player != null) {
            int slot = mc.player.getInventory().selected;
            ItemStack held = mc.player.getInventory().getItem(slot);
            if (!held.isEmpty()) {
                RtsClientPacketGateway.sendInteractEntityEmptyHand(
                        NetworkConstants.NO_ENTITY,
                        hit.getLocation(), hit, ray.origin(), ray.direction());
                return CONSUMED;
            }
        }

        return PASS;
    }

    
    private boolean tryPickHoveredBlockForPlacement(BuilderScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray == null) return false;

        BlockHitResult hit = ray.raycastBlock(mc);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;

        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) return false;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null) return false;

        ItemStack preview = new ItemStack(item);
        if (preview.isEmpty()) return false;

        
        if (mc.player != null) {
            var inventory = mc.player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack candidate = inventory.getItem(i);
                if (!candidate.isEmpty() && candidate.getItem() == preview.getItem()) {
                    inventory.selected = i;
                    
                    BuildingModule buildingModule = kernel.module(BuildingModule.class);
                    if (buildingModule != null) {
                        buildingModule.clearSelection();
                    }
                    return true;
                }
            }
        }

        
        BuildingModule buildingModule = kernel.module(BuildingModule.class);
        if (buildingModule != null) {
            buildingModule.selectItem(itemId.toString(), preview.getHoverName().getString(), preview);
        }
        return true;
    }

    
    
    private boolean shouldSkipRightClickRelease(BuilderScreen screen, LeftSidebarPanel leftSidebarPanel) {
        if (leftSidebarPanel == null) return false;
        // 框选模式（非点击模式）：必须等框选完全完毕后才判定右键放置。
        // 最近一次右键点击若改变了框选状态（正在选点、或点击框外重置），释放一律跳过；
        // 仅当点击前框选已 COMPLETE 且框内点击（未改变状态）时放行，由 runPrimaryActionAt 批量放置。
        if (!screen.isClickButtonSelected()) {
            var sel = kernel.renderPipeline().boxSelector;
            return sel.lastRightClickChangedPhase()
                    || sel.getPhase() != BoxSelector.Phase.COMPLETE;
        }
        if (!screen.isInteractiveMode()) return false;
        if (leftSidebarPanel.isClickButtonSelected()) return true;
        return kernel.renderPipeline().boxSelector.getPhase() == BoxSelector.Phase.COMPLETE;
    }

    /** 判断位置是否落在框选区域 [min, max) 内。 */
    private static boolean isPosInSelection(BlockPos pos, BoxSelector sel) {
        if (pos == null || sel == null) return false;
        BlockPos min = sel.getMinCorner();
        BlockPos max = sel.getMaxCorner();
        if (min == null || max == null) return false;
        return pos.getX() >= min.getX() && pos.getX() < max.getX()
                && pos.getY() >= min.getY() && pos.getY() < max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() < max.getZ();
    }

    private static boolean isWorldArea(double mouseX, double mouseY, BuilderScreen screen) {
        int leftW = screen.getLeftSidebarWidth();
        if (mouseX < leftW) return false;

        int rightW = screen.getRightSidebarWidth();
        if (rightW > 0 && mouseX >= screen.getRtsVirtualWidth() - rightW) return false;

        int downH = screen.getDownSidebarHeight();
        if (downH > 0 && mouseY >= screen.getRtsVirtualHeight() - downH) return false;

        if (mouseY < ScreenBackgroundPanel.BACKGROUND_TOP_Y) return false;

        return true;
    }

    private static boolean isInBuildOrInteractiveMode(TopBarPanel topBarPanel) {
        if (topBarPanel == null) return false;
        ModeSwitcher.Mode mode = topBarPanel.getCurrentMode();
        return mode == ModeSwitcher.Mode.BUILD || mode == ModeSwitcher.Mode.INTERACTIVE;
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
}
