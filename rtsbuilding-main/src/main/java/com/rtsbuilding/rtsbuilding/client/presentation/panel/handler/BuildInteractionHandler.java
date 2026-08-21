package com.rtsbuilding.rtsbuilding.client.presentation.panel.handler;

import com.rtsbuilding.rtsbuilding.client.rtsbuild.shape.BuildShape;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.mining.MiningModule;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
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
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster.CursorRay;
import com.rtsbuilding.rtsbuilding.client.state.FeatureAdjusterState;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.network.NetworkConstants;
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
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.CONSUMED;
import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.PASS;

public final class BuildInteractionHandler {

    
    private static final byte ULTIMINE_MODE = 0;

    private final RtsClientKernel kernel;
    private final CameraInputLayer cameraInputLayer;

    private boolean miningActive;

    // ── 单方块模式长按持续操作（放置/破坏） ──────────────────────────

    /** 持续放置间隔（tick）：长按右键期间每隔该值放置一格。 */
    private static final int HOLD_PLACE_INTERVAL = 2;

    /** 持续破坏切换间隔（tick）：当前方块挖完后等待该值再挖下一个。 */
    private static final int HOLD_MINE_INTERVAL = 10;

    /** 长按右键持续放置进行中。 */
    private boolean rightHoldActive;

    /** 长按左键持续破坏进行中。 */
    private boolean leftHoldActive;

    /** 持续放置倒计时。 */
    private int holdPlaceCooldown;

    /** 持续破坏切换倒计时。 */
    private int holdMineCooldown;

    /** 当前按压周期内是否已放置过（区分"单击"与"长按"）。 */
    private boolean holdPlaceFired;

    /** 本按压周期是否已进入自动持续破坏阶段（区分"单击"与"长按持续破坏"）。
     *  单击后即使第一个方块很快被挖完，首次自动切换也必须等待一个 {@link #HOLD_MINE_INTERVAL}，
     *  防止快速单击被误判为长按而连续破坏两个方块。 */
    private boolean holdMineFired;

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
        // 方向旋转模式：右键由 RotateModeMouseHandler（更高优先级）接管，
        // 本处理器完全让路，避免误触发单方块放置 / 形状画笔。
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && isRotateModeActive(screen, leftSidebarPanel)) return PASS;
        if (screen.isMouseOverUiPanelApi(event.x(), event.y())) return PASS;
        if (!isWorldArea(event.x(), event.y(), screen)) return PASS;
        if (leftSidebarPanel != null && leftSidebarPanel.isClickButtonSelected()
                && screen.isInteractiveMode()) return PASS;

        // 左键：破坏侧形状画笔左键驱动（选起点/选终点/确认破坏）；不满足则按常规挖掘
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !isAltDown()) {
            if (tryStartLineBrush(screen, leftSidebarPanel, true)) return CONSUMED;
            return handleLeftClick(screen, leftSidebarPanel) ? CONSUMED : PASS;
        }

        // 建造侧形状画笔（线/墙/面模式建造）：右键按下处理画笔逐级点选。消费右键按下，阻止相机层记录右键
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !isAltDown() && !isShiftDown()) {
            if (tryStartLineBrush(screen, leftSidebarPanel, false)) return CONSUMED;
            // 破坏侧形状已选中但画笔未启动：右键不驱动画笔（破坏侧改用左键选取），消费右键防相机移动
            if (activeBreakShape(screen, leftSidebarPanel) != null) return CONSUMED;
            // 单方块持续放置：右键按下进入持续放置状态（首次放置由 handleTick 立即执行，
            // 快速单击由 handleMouseRelease 兜底放置一次）
            if (isSingleBlockPlaceActive(screen, leftSidebarPanel)) {
                this.rightHoldActive = true;
                this.holdPlaceCooldown = 0;
                this.holdPlaceFired = false;
                return CONSUMED;
            }
        }

        // 线/墙/面画笔活跃时：任何右键按下（含 Alt+右键）一律消费，
        // 阻挡建造过程中的 Alt+右键移动玩家（P_MOVEMENT 层）等其它操作
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && kernel.renderPipeline().lineBrush.isActive()) {
            return CONSUMED;
        }

        return PASS;
    }

    
    public EventResult handleMouseRelease(MouseReleaseEvent event, BuilderScreen screen,
                                           TopBarPanel topBarPanel,
                                           LeftSidebarPanel leftSidebarPanel) {
        int button = event.button();

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.miningActive) {
            stopMining();
            this.leftHoldActive = false;
            this.holdMineCooldown = 0;
            this.holdMineFired = false;
            return CONSUMED;
        }

        // 破坏侧画笔活跃阶段：松开左键消费（点选交互由左键按下完成），避免释放落到原版操作
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && kernel.renderPipeline().lineBrush.isActive()) {
            return CONSUMED;
        }

        // 线/墙/面画笔活跃阶段：松开右键不触发单点放置（点选交互由右键按下完成）
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && kernel.renderPipeline().lineBrush.isActive()) {
            return CONSUMED;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !isAltDown() && !isShiftDown()
                && !screen.isMouseOverUiPanelApi(event.x(), event.y())
                && isWorldArea(event.x(), event.y(), screen)
                && isInBuildOrInteractiveMode(topBarPanel)
                && !isRotateModeActive(screen, leftSidebarPanel)
                && !shouldSkipRightClickRelease(screen, leftSidebarPanel)) {
            // 单方块持续放置：松开右键结束持续放置。
            // 快速单击（按下后 handleTick 尚未放置）兜底放置一次，避免漏放。
            if (this.rightHoldActive) {
                boolean fired = this.holdPlaceFired;
                this.rightHoldActive = false;
                this.holdPlaceCooldown = 0;
                this.holdPlaceFired = false;
                if (!fired) {
                    placeSelectedAtCursor(screen, leftSidebarPanel);
                }
                return CONSUMED;
            }
            if (!cameraInputLayer.wasDragged(button)) {
                return runPrimaryActionAt(screen, leftSidebarPanel);
            }
        }

        
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                && !screen.isMouseOverUiPanelApi(event.x(), event.y())
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
        // 线模式建造：画线激活时实时更新预览（右键拖拽方向由鼠标位置决定，无需 mouseDragged 事件）；
        // 画线激活状态消失时清除遗留的拖拽状态（切换形状/选择/模式等）
        if (activeShape(screen, leftSidebarPanel) != null) {
            updateLineBrushHover(screen);
        } else if (kernel.renderPipeline().lineBrush.isActive()) {
            kernel.renderPipeline().lineBrush.reset();
        }

        // 单方块持续放置：长按右键按固定间隔在鼠标指向处放置方块
        if (this.rightHoldActive) {
            if (!isSingleBlockPlaceActive(screen, leftSidebarPanel)
                    || !isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
                this.rightHoldActive = false;
                this.holdPlaceCooldown = 0;
            } else if (this.holdPlaceCooldown > 0) {
                this.holdPlaceCooldown--;
            } else {
                placeSelectedAtCursor(screen, leftSidebarPanel);
                this.holdPlaceFired = true;
                this.holdPlaceCooldown = HOLD_PLACE_INTERVAL;
            }
        }

        // 单方块持续破坏：长按左键，当前方块挖完后（activePos 被清空）等待间隔再挖下一个目标。
        // 兜底：若服务端完成信号丢失但目标方块已消失（被挖掉），视为挖完并切换到下一个。
        // 目标转移：长按期间鼠标瞄准的方块发生变化时，立即取消当前挖掘任务并切换到新目标（与原版挖方块一致）。
        if (this.leftHoldActive) {
            if (!isSingleBlockMineActive(screen, leftSidebarPanel)
                    || (leftSidebarPanel != null && leftSidebarPanel.isUltimineActive())
                    || !isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
                this.leftHoldActive = false;
                this.holdMineCooldown = 0;
                this.holdMineFired = false;
            } else {
                MiningModule holdMining = kernel.module(MiningModule.class);
                Minecraft holdMc = Minecraft.getInstance();
                if (holdMining != null && holdMc.level != null && holdMc.player != null) {
                    BlockPos cur = holdMining.getActivePos();
                    if (cur != null && holdMc.level.getBlockState(cur).isAir()) {
                        holdMining.clearActivePos();
                        cur = null;
                    }

                    // 目标转移：鼠标瞄准的方块与当前挖掘目标不一致时，取消旧任务并立即开始挖新目标
                    if (cur != null) {
                        BlockHitResult hover = resolveMineHit(holdMc, screen);
                        if (hover != null && !cur.equals(hover.getBlockPos())) {
                            BuildingModule bm = kernel.module(BuildingModule.class);
                            String toolItemId = bm != null ? bm.getSelectedItemId() : "";
                            int toolSlot = holdMc.player.getInventory().selected;
                            holdMining.abortMining(toolSlot);
                            holdMining.startMining(hover.getBlockPos(), hover.getDirection().get3DDataValue(),
                                    toolSlot, toolItemId, false, false);
                            // 已确认是长按持续破坏，转移后无需再等待单击保护间隔
                            this.holdMineFired = true;
                            this.holdMineCooldown = 0;
                        }
                    }

                    if (cur == null) {
                        // 单击保护：首次点击的方块挖完后，进入自动持续破坏前必须等待一个完整间隔，
                        // 避免快速单击被误判为长按而连续破坏两个方块。
                        if (!this.holdMineFired) {
                            this.holdMineFired = true;
                            this.holdMineCooldown = HOLD_MINE_INTERVAL;
                        } else if (this.holdMineCooldown > 0) {
                            this.holdMineCooldown--;
                        } else {
                            mineAtCursor(screen, leftSidebarPanel);
                            this.holdMineCooldown = HOLD_MINE_INTERVAL;
                        }
                    }
                }
            }
        }

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
        if (screen.isMouseOverUiPanelApi(mouseX, mouseY)) return;
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
        // 线/墙/面画笔活跃阶段：左键不触发挖掘
        if (kernel.renderPipeline().lineBrush.isActive()) return true;

        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray == null) return false;

        BlockHitResult hit = resolveBuildHit(mc, screen, ray);
        if (hit == null) return false;

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
                    toolSlot, FeatureAdjusterState.getUltimineLimit(), ULTIMINE_MODE,
                    toolItemId, false);
            return true;
        }

        // 形状按钮组：仅"破坏"侧"单方块"形状允许左键单方块挖掘；
        // 其余破坏形状（线/墙/平面/体/圆面/球）走右键画笔交互（AREA_DESTROY），左键不触发单方块挖掘。
        if (leftSidebarPanel != null && !leftSidebarPanel.isSingleBlockBreakShapeSelected()) {
            return true;
        }

        miningModule.startMining(hit.getBlockPos(), hit.getDirection().get3DDataValue(),
                toolSlot, toolItemId, false, false);
        this.miningActive = true;
        // 长按左键持续破坏：当前方块挖完后由 handleTick 自动切换到下一个目标
        this.leftHoldActive = true;
        this.holdMineCooldown = 0;
        this.holdMineFired = false;
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
    }

    // ── 线模式建造（按住右键拖拽画线） ───────────────────────────────

    /**
     * 当前生效的画笔建造形状：点击模式 + 建造模式 + 建造侧选中线/墙/面/体/圆面/球 + 已选中方块物品。
     *
     * @return 激活的形状；不满足激活条件或选中的是单方块形状时返回 {@code null}
     */
    private BuildShape activeBuildShape(BuilderScreen screen, LeftSidebarPanel leftSidebarPanel) {
        if (leftSidebarPanel == null || screen == null || !screen.isClickButtonSelected()) return null;
        BuildingModule bm = kernel.module(BuildingModule.class);
        if (bm == null || bm.getMode() != BuilderMode.BUILD || !bm.hasSelectedItem()) return null;
        return leftSidebarPanel.getBuildShape();
    }

    /**
     * 当前生效的画笔破坏形状：点击模式 + 建造模式 + 破坏侧选中线/墙/面/体/圆面/球。
     * 破坏侧无需选中物品（使用工具破坏），只需选中非单方块破坏形状。
     *
     * @return 激活的破坏形状；选中单方块或条件不满足时返回 {@code null}
     */
    private BuildShape activeBreakShape(BuilderScreen screen, LeftSidebarPanel leftSidebarPanel) {
        if (leftSidebarPanel == null || screen == null || !screen.isClickButtonSelected()) return null;
        BuildingModule bm = kernel.module(BuildingModule.class);
        if (bm == null || bm.getMode() != BuilderMode.BUILD) return null;
        // 连锁挖掘启用：破坏侧形状批量破坏被禁用（形状画笔与连锁挖掘互斥，左键必须走连锁挖掘）
        if (leftSidebarPanel.isUltimineActive()) return null;
        return leftSidebarPanel.getBreakShape();
    }

    /** 当前生效的画笔形状：优先建造侧，其次破坏侧。 */
    private BuildShape activeShape(BuilderScreen screen, LeftSidebarPanel leftSidebarPanel) {
        BuildShape build = activeBuildShape(screen, leftSidebarPanel);
        if (build != null) return build;
        return activeBreakShape(screen, leftSidebarPanel);
    }

    /**
     * 左键/右键单击：起点选择/终点选择/确认建造的逐级推进（点选式画线）。
     * 建造侧画笔由<b>右键</b>驱动，破坏侧画笔由<b>左键</b>驱动（左键选取）。
     *
     * @param leftClick 本次点击是否为左键；驱动键不匹配时（建造侧遇左键 / 破坏侧遇右键）
     *                  不推进画笔：画笔未启动则不启动（交由其它左/右键操作），已启动则仅消费事件
     */
    private boolean tryStartLineBrush(BuilderScreen screen, LeftSidebarPanel leftSidebarPanel, boolean leftClick) {
        var lineBrush = kernel.renderPipeline().lineBrush;
        // 连锁挖掘启用：破坏侧形状画笔被禁用，若存在启用前残留的已激活破坏画笔直接重置，
        // 避免左键点击被破坏画笔抢占而无法触发连锁挖掘
        if (leftSidebarPanel != null && leftSidebarPanel.isUltimineActive() && lineBrush.isBreakActive()) {
            lineBrush.reset();
        }
        // 任一确认阶段：由驱动键推进（体从宽度进入高度），进入建造阶段后确认建造
        if (lineBrush.isAdjusting() || lineBrush.isWidthAdjusting() || lineBrush.isHeightAdjusting()
                || lineBrush.isRadiusAdjusting()) {
            // 非驱动键（建造侧画笔遇左键、破坏侧画笔遇右键）：消费但不推进
            if (lineBrush.isBreakActive() != leftClick) return true;
            boolean done = lineBrush.advancePhase();
            if (done) confirmLinePlace(screen, leftSidebarPanel);
            return true;
        }
        // 起点已选：由驱动键选择终点
        if (lineBrush.isPicking()) {
            if (lineBrush.isBreakActive() != leftClick) return true;
            lineBrush.pickEnd();
            return true;
        }
        // 空闲：选择起点。建造侧用右键，破坏侧用左键
        BuildShape build = activeBuildShape(screen, leftSidebarPanel);
        BuildShape shape = build != null ? build : activeBreakShape(screen, leftSidebarPanel);
        if (shape == null) return false;
        boolean breakMode = build == null;
        // 驱动键不匹配（建造侧遇左键 / 破坏侧遇右键）：不启动画笔
        if (breakMode != leftClick) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray == null) return false;
        BlockHitResult hit = resolveBuildHit(mc, screen, ray);
        if (hit == null) return false;
        // 记录画笔归属：建造侧（选中了物品）还是破坏侧，确认阶段据此分发请求
        lineBrush.start(hit.getBlockPos(), shape, breakMode);
        return true;
    }

    /** 每 tick 更新画线悬停位置（起点→当前指针的线段预览）。 */
    private void updateLineBrushHover(BuilderScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray == null) {
            kernel.renderPipeline().lineBrush.updateHover(null);
            return;
        }
        BlockHitResult hit = resolveBuildHit(mc, screen, ray);
        kernel.renderPipeline().lineBrush.updateHover(hit == null ? null : hit.getBlockPos());
    }

    // ── 单方块模式长按持续放置/破坏 ──────────────────────────────────

    /** 单方块持续放置是否激活：点击模式 + 建造模式 + 建造侧单方块形状 + 已选中方块物品。 */
    private boolean isSingleBlockPlaceActive(BuilderScreen screen, LeftSidebarPanel leftSidebarPanel) {
        if (leftSidebarPanel == null || screen == null || !screen.isClickButtonSelected()) return false;
        BuildingModule bm = kernel.module(BuildingModule.class);
        return bm != null && bm.getMode() == BuilderMode.BUILD
                && leftSidebarPanel.isSingleBlockBuildShapeSelected()
                && bm.hasSelectedItem();
    }

    /** 单方块持续破坏是否激活：点击模式 + 建造模式 + 破坏侧单方块形状。 */
    private boolean isSingleBlockMineActive(BuilderScreen screen, LeftSidebarPanel leftSidebarPanel) {
        if (leftSidebarPanel == null || screen == null || !screen.isClickButtonSelected()) return false;
        BuildingModule bm = kernel.module(BuildingModule.class);
        return bm != null && bm.getMode() == BuilderMode.BUILD
                && leftSidebarPanel.isSingleBlockBreakShapeSelected();
    }

    private static boolean isMouseButtonDown(int button) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return window != 0L && GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
    }

    /** 在鼠标指向处放置一次当前选中的方块（含 Ctrl 面偏移）。 */
    private void placeSelectedAtCursor(BuilderScreen screen, LeftSidebarPanel leftSidebarPanel) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray == null) return;
        BlockHitResult hit = resolveBuildHit(mc, screen, ray);
        if (hit == null) return;
        BuildingModule bm = kernel.module(BuildingModule.class);
        if (bm == null) return;
        bm.placeSelected(hit, isReplaceModeActive(), ray.origin(), ray.direction());
    }

    /**
     * 替换模式是否生效：按住 Shift 或开启「方块替换」开关（下嵌层按钮，所有形状共享）。
     */
    private boolean isReplaceModeActive() {
        return isShiftDown() || kernel.renderPipeline().lineBrush.isReplaceEnabled();
    }

    /** 在鼠标指向处触发一次单方块挖掘（含 Ctrl 面偏移）。 */
    private void mineAtCursor(BuilderScreen screen, LeftSidebarPanel leftSidebarPanel) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        BlockHitResult hit = resolveMineHit(mc, screen);
        if (hit == null) return;
        BuildingModule bm = kernel.module(BuildingModule.class);
        String toolItemId = bm != null ? bm.getSelectedItemId() : "";
        int toolSlot = mc.player.getInventory().selected;
        MiningModule mm = kernel.module(MiningModule.class);
        if (mm != null) {
            mm.startMining(hit.getBlockPos(), hit.getDirection().get3DDataValue(),
                    toolSlot, toolItemId, false, false);
        }
    }

    /**
     * 计算当前鼠标瞄准的方块命中结果（点击模式下含 Ctrl 面偏移），未命中方块时返回 {@code null}。
     * 供长按持续破坏的目标转移检测复用，避免与 {@link #mineAtCursor} 重复射线计算。
     */
    @Nullable
    private BlockHitResult resolveMineHit(Minecraft mc, BuilderScreen screen) {
        if (mc.level == null) return null;
        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray == null) return null;
        return resolveBuildHit(mc, screen, ray);
    }

    /** 右键确认：按画笔归属（建造/破坏）批量执行并复位画笔（最终确认阶段）。 */
    private EventResult confirmLinePlace(BuilderScreen screen, LeftSidebarPanel leftSidebarPanel) {
        var lineBrush = kernel.renderPipeline().lineBrush;
        if (lineBrush.isBreakActive()) {
            return confirmLineBreak(screen, leftSidebarPanel);
        }
        BuildingModule bm = kernel.module(BuildingModule.class);
        if (bm == null) return CONSUMED;
        Minecraft mc = Minecraft.getInstance();
        // 形状 × 阶段的统一派发：确认阶段即为当前生效形态
        List<BlockPos> positions = lineBrush.computePositions();
        lineBrush.reset();
        if (positions.isEmpty()) return CONSUMED;
        // 形状达到单包位置上限（BuildShape 在生成时已按上限截断）：明确提示已截断
        if (positions.size() >= NetworkConstants.MAX_POSITIONS && mc.player != null) {
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.rtsbuilding.shape.truncated_place", NetworkConstants.MAX_POSITIONS), true);
        }
        Vec3 rayOrigin = Vec3.ZERO;
        Vec3 rayDir = Vec3.ZERO;
        if (mc.level != null) {
            var ray = CursorRaycaster.computeCursorRay(mc, screen);
            if (ray != null) {
                rayOrigin = ray.origin();
                rayDir = ray.direction();
            }
        }
        RtsClientPacketGateway.sendLinePlace(positions,
                (byte) bm.getPlaceRotateSteps(), isReplaceModeActive(), true,
                bm.getSelectedItemId(), rayOrigin, rayDir);
        return CONSUMED;
    }

    /** 右键确认（破坏侧）：按当前破坏形状批量破坏并复位画笔。 */
    private EventResult confirmLineBreak(BuilderScreen screen, LeftSidebarPanel leftSidebarPanel) {
        Minecraft mc = Minecraft.getInstance();
        var lineBrush = kernel.renderPipeline().lineBrush;
        List<BlockPos> positions = lineBrush.computePositions();
        lineBrush.reset();
        if (positions.isEmpty()) return CONSUMED;
        // 形状达到单包位置上限：明确提示已截断
        if (positions.size() >= NetworkConstants.MAX_POSITIONS && mc.player != null) {
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.rtsbuilding.shape.truncated_break", NetworkConstants.MAX_POSITIONS), true);
        }
        int toolSlot = mc.player != null ? mc.player.getInventory().selected : 0;
        RtsClientPacketGateway.sendShapeAreaDestroy(positions, toolSlot, "", false);
        return CONSUMED;
    }

    
    private EventResult runPrimaryActionAt(BuilderScreen screen, LeftSidebarPanel leftSidebarPanel) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return PASS;

        // 方向旋转模式：右键已由 RotateModeMouseHandler 处理，此处兜底放行
        if (isRotateModeActive(screen, leftSidebarPanel)) return PASS;

        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray == null) return PASS;

        BlockHitResult hit = resolveBuildHit(mc, screen, ray);
        if (hit == null) return PASS;

        BuildingModule buildingModule = kernel.module(BuildingModule.class);
        if (buildingModule == null) return PASS;

        boolean isBuildMode = buildingModule.getMode() == BuilderMode.BUILD;

        // 框选模式（非点击模式）：框选完成后，在框内右键 = 对整个框选区域批量放置，
        // 与点击模式的单点放置机制区分开。流体暂不支持批量，仍走单点放置。
        BoxSelector boxSelector = kernel.renderPipeline().boxSelector;
        if (!screen.isClickButtonSelected()
                && boxSelector.getPhase() == BoxSelector.Phase.COMPLETE
                && isPosInSelection(hit.getBlockPos(), boxSelector)) {
            if (!isBuildMode) return PASS;
            if (buildingModule.hasSelectedFluid()) {
                buildingModule.placeFluid(hit, isReplaceModeActive(), ray.origin(), ray.direction());
                return CONSUMED;
            }
            if (buildingModule.hasSelectedItem()) {
                RtsClientPacketGateway.sendAreaBoxPlace(
                        boxSelector.getMinCorner(), boxSelector.getMaxCorner(),
                        (byte) buildingModule.getPlaceRotateSteps(), isReplaceModeActive(), true,
                        buildingModule.getSelectedItemId(), ray.origin(), ray.direction());
                return CONSUMED;
            }
            return CONSUMED;
        }

        if (buildingModule.hasSelectedFluid()) {
            if (!isBuildMode) return PASS;
            buildingModule.placeFluid(hit, isReplaceModeActive(), ray.origin(), ray.direction());
            return CONSUMED;
        }

        
        if (buildingModule.hasSelectedItem()) {
            if (!isBuildMode) return PASS;
            // 形状按钮组：仅"建造"侧"单方块"形状允许单方块放置；
            // 选中非单方块建造形状时走右键形状画笔批量建造（破坏不受影响，走破坏侧左键画笔判断）。
            if (leftSidebarPanel != null && !leftSidebarPanel.isSingleBlockBuildShapeSelected()) {
                return CONSUMED;
            }
            buildingModule.placeSelected(hit, isReplaceModeActive(), ray.origin(), ray.direction());
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

    /** 方向旋转模式是否激活：建造模式 + 左栏「方向旋转」按钮选中。 */
    private static boolean isRotateModeActive(BuilderScreen screen) {
        return screen != null && screen.isBuildMode() && screen.isDirectionRotateActive();
    }

    /** 方向旋转模式是否激活（兼容保留签名，仅依赖屏幕状态）。 */
    private static boolean isRotateModeActive(BuilderScreen screen, LeftSidebarPanel leftSidebarPanel) {
        return isRotateModeActive(screen);
    }

    static boolean isWorldArea(double mouseX, double mouseY, BuilderScreen screen) {
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

    private static boolean isCtrlDown() {
        return RtsKeyMappings.isPlaceOffsetDown();
    }

    /**
     * 解析鼠标射线命中的方块。点击模式下按住 Ctrl 时，将选中位置偏移到命中面外侧一格
     * （与框选工具 Ctrl 行为一致），射线命中点调整为偏移方块中心。
     * 框选模式下不偏移（由 {@link BoxSelector} 自行处理）。
     */
    @Nullable
    private BlockHitResult resolveBuildHit(Minecraft mc, BuilderScreen screen, CursorRay ray) {
        BlockHitResult hit = ray.raycastBlock(mc);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return null;
        if (screen.isClickButtonSelected() && isCtrlDown()) {
            BlockPos shifted = hit.getBlockPos().relative(hit.getDirection());
            return new BlockHitResult(Vec3.atCenterOf(shifted), hit.getDirection(), shifted, hit.isInside());
        }
        return hit;
    }
}
