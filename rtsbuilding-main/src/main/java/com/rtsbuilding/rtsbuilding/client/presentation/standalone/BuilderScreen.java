package com.rtsbuilding.rtsbuilding.client.presentation.standalone;

import com.rtsbuilding.uifw.render.UiPalette;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.application.service.ScreenCoordinator;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.util.TinyFileDialogSupport;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.input.layer.CameraInputLayer;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.event.dispatcher.EventDispatcher;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.*;
import com.rtsbuilding.rtsbuilding.client.presentation.layout.PanelRegistry;
import com.rtsbuilding.rtsbuilding.client.presentation.layout.RenderLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.background.ScreenBackgroundPanel;
import com.rtsbuilding.uifw.window.window.FloatingWindowLayer;
import com.rtsbuilding.uifw.window.window.UiPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.blueprint.BlueprintImportPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.blueprint.BlueprintLibraryPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.blueprint.BlueprintPreviewPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.blueprint.BlueprintSavePanel;
import com.rtsbuilding.uifw.component.color.ColorPickerPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.DownSidebarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.DownSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.gear.GearMenuPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.handler.*;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction.SelectionHighlight;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.LeftSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.rightbar.RightSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.TopBarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.render.ViewCaptureService;
import com.rtsbuilding.rtsbuilding.client.rtsbuild.shape.BuildShape;
import com.rtsbuilding.uifw.render.GuiItemRenderer;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.window.api.UiPanelHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class BuilderScreen extends Screen implements UiPanelHost {

    private final RtsClientKernel kernel;
    
    private final ScreenBackgroundPanel screenBackgroundPanel;
    private final FloatingWindowLayer floatingWindowLayer;
    private final TopBarPanel topBarPanel;
    private final ColorPickerPanel colorPickerPanel;
    private final GearMenuPanel gearMenuPanel;
    private final RightSidebarPanel rightSidebarPanel;
    private final DownSidebarPanel downSidebarPanel;
    private final LeftSidebarPanel leftSidebarPanel;

    /** 蓝图保存对话框：蓝图模式框选完成后按回车弹出，输入名称保存本地蓝图。 */
    private final BlueprintSavePanel blueprintSavePanel;

    /** 蓝图文件管理面板：顶栏「文件」→「蓝图文件」打开。 */
    private final BlueprintLibraryPanel blueprintLibraryPanel;

    /** 蓝图导入面板：顶栏「文件」→「导入」打开（网页式上传区选择文件转换导入）。 */
    private final BlueprintImportPanel blueprintImportPanel;

    /** 蓝图结构预览面板：蓝图库面板单击选中文件时打开（3D 结构缩略图）。 */
    private final BlueprintPreviewPanel blueprintPreviewPanel;

    // ── 蓝图放置模式（蓝图库面板「使用」按钮进入） ──────────────────
    /** 当前正在放置的蓝图（服务端将以准星目标为锚点逐格建造）。 */
    private com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint activeBlueprintPlacement;
    /** 放置模式是否激活（激活后左键点击世界确认放置，Esc 取消）。 */
    private boolean blueprintPlacementActive;
    /** 放置 Y 轴旋转步数（0-3，每步 90°；暂固定 0，可后续加 R 键）。 */
    private int placementYSteps;
    /** 当前准星瞄准的锚点方块（渲染时更新，供幽灵预览 pass 与确认使用）。 */
    private net.minecraft.core.BlockPos placementAnchor;

    /** 待处理的拖放文件路径（drop 回调缓存，渲染阶段判定落点后消费）。 */
    private List<Path> pendingDropPaths;

    
    private final PanelRegistry panelRegistry = new PanelRegistry();

    private final ScreenCoordinator screenCoordinator;

    

    
    private final BuilderScreenScaleManager scaleManager;

    private final CursorStyleManager cursorStyleManager;
    private final CursorWrapHandler cursorWrapHandler;
    
    private final BuilderScreenMovementHandler movementHandler;
    
    private final BindModeMouseHandler bindModeHandler;
    
    private final SelectionHighlight selectionHighlight;
    
    private final EntityInteractionHandler entityInteractionHandler;
    
    private final BuildInteractionHandler buildInteractionHandler;
    
    private final EventDispatcher eventDispatcher = new EventDispatcher();
    
    private final BuilderScreenEventRouter eventRouter;

    public BuilderScreen() {
        super(Component.literal("RTS Builder"));
        long t0 = System.nanoTime();
        this.kernel = RtsClientKernel.get();
        this.screenBackgroundPanel = new ScreenBackgroundPanel();
        this.colorPickerPanel = new ColorPickerPanel();
        this.gearMenuPanel = new GearMenuPanel();
        this.rightSidebarPanel = new RightSidebarPanel();
        this.downSidebarPanel = new DownSidebarPanel();
        this.leftSidebarPanel = new LeftSidebarPanel();
        this.topBarPanel = new TopBarPanel();
        this.blueprintSavePanel = new BlueprintSavePanel();
        this.blueprintLibraryPanel = new BlueprintLibraryPanel();
        this.blueprintImportPanel = new BlueprintImportPanel();
        this.blueprintPreviewPanel = new BlueprintPreviewPanel();
        long t1 = System.nanoTime();
        panelRegistry.register(topBarPanel, RenderLayer.CONTENT_PANELS);
        panelRegistry.register(leftSidebarPanel, RenderLayer.CONTENT_PANELS);
        panelRegistry.register(rightSidebarPanel, RenderLayer.CONTENT_PANELS);
        panelRegistry.register(downSidebarPanel, RenderLayer.CONTENT_PANELS);
        this.floatingWindowLayer = new FloatingWindowLayer();
        this.topBarPanel.setOnGearMenuToggle(() -> {
            gearMenuPanel.toggleOpen();
            topBarPanel.setGearMenuOpen(gearMenuPanel.isOpen());
        });
        // 「文件」→「蓝图文件」：打开蓝图文件管理面板
        this.topBarPanel.setOnOpenBlueprintLibrary(() -> blueprintLibraryPanel.open());
        // 「文件」→「导入」：打开蓝图导入面板（网页式上传区选择文件导入）
        this.topBarPanel.setOnImportBlueprint(() -> blueprintImportPanel.open());
        long t2 = System.nanoTime();

        this.selectionHighlight = new SelectionHighlight();
        this.movementHandler = new BuilderScreenMovementHandler();
        this.bindModeHandler = new BindModeMouseHandler();
        this.entityInteractionHandler = new EntityInteractionHandler();
        CameraInputLayer cameraInputLayer = kernel.inputPipeline().findLayer(CameraInputLayer.class);
        this.buildInteractionHandler = new BuildInteractionHandler(kernel, cameraInputLayer);
        long t3 = System.nanoTime();
        this.cursorStyleManager = new CursorStyleManager((mx, my) -> {
            var fwCursor = floatingWindowLayer.resizeCursorAt(mx, my);
            if (fwCursor != UiPanel.ResizeCursor.DEFAULT) return fwCursor;
            if (floatingWindowLayer.isMouseOverWindowOrResizableBorder(mx, my)) {
                return UiPanel.ResizeCursor.DEFAULT;
            }
            if (rightSidebarPanel.isMouseOverOverlayDivider(mx, my)) return UiPanel.ResizeCursor.RESIZE_NS;
            if (downSidebarPanel.isMouseOverOverlayDivider(mx, my)) return UiPanel.ResizeCursor.RESIZE_EW;
            if (rightSidebarPanel.isMouseOverLeftEdge(mx, my)) return UiPanel.ResizeCursor.RESIZE_EW;
            if (downSidebarPanel.isMouseOverTopEdge(mx, my)) return UiPanel.ResizeCursor.RESIZE_NS;
            return UiPanel.ResizeCursor.DEFAULT;
        });
        this.cursorWrapHandler = new CursorWrapHandler();
        this.scaleManager = new BuilderScreenScaleManager();
        this.screenCoordinator = new ScreenCoordinator();
        long t4 = System.nanoTime();
        this.eventRouter = new BuilderScreenEventRouter(new BuilderScreenEventRouter.SuperScreen() {
            @Override public boolean mouseClicked(double x, double y, int b) { return BuilderScreen.super.mouseClicked(x, y, b); }
            @Override public boolean mouseReleased(double x, double y, int b) { return BuilderScreen.super.mouseReleased(x, y, b); }
            @Override public boolean mouseDragged(double x, double y, int b, double dx, double dy) { return BuilderScreen.super.mouseDragged(x, y, b, dx, dy); }
            @Override public boolean mouseScrolled(double x, double y, double sx, double sy) { return BuilderScreen.super.mouseScrolled(x, y, sx, sy); }
            @Override public boolean keyPressed(int kc, int sc, int mod) { return BuilderScreen.super.keyPressed(kc, sc, mod); }
            @Override public boolean charTyped(char cp, int mod) { return BuilderScreen.super.charTyped(cp, mod); }
            @Override public void mouseMoved(double x, double y) { BuilderScreen.super.mouseMoved(x, y); }
        });
        eventRouter.registerAll(eventDispatcher, panelRegistry, this, kernel,
                floatingWindowLayer, topBarPanel, leftSidebarPanel, gearMenuPanel,
                movementHandler, bindModeHandler, entityInteractionHandler,
                buildInteractionHandler);
        long t5 = System.nanoTime();

        com.rtsbuilding.rtsbuilding.RtsbuildingMod.LOGGER.info(
                "RTS-PERF: BuilderScreen constructor panels={} ms, registry/floating={} ms, handlers={} ms, cursor/scale/coordinator={} ms, eventRouter={} ms, total={} ms",
                (t1 - t0) / 1_000_000L, (t2 - t1) / 1_000_000L, (t3 - t2) / 1_000_000L,
                (t4 - t3) / 1_000_000L, (t5 - t4) / 1_000_000L, (t5 - t0) / 1_000_000L);
    }

    /**
     * 预热 BuilderScreen 及其全部 UI 依赖：在启动阶段完整执行一次构造 + init，
     * 触发相关类的类加载与 JIT 编译，避免首次进入 RTS 模式时（进入游戏后的第一次
     * 打开）的“卡一下”。实例随后被 GC 回收。init 依赖的屏幕尺寸用当前窗口值填充。
     */
    public static void warmUp() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return;
        }
        BuilderScreen screen = new BuilderScreen();
        screen.width = mc.getWindow().getGuiScaledWidth();
        screen.height = mc.getWindow().getGuiScaledHeight();
        screen.init();
    }

    @Override
    protected void init() {
        long t0 = System.nanoTime();
        super.init();
        
        this.screenBackgroundPanel.init(this);
        this.colorPickerPanel.init(this);
        addFloatingWindowIfAbsent(this.colorPickerPanel);
        long t1 = System.nanoTime();
        this.gearMenuPanel.init(this);
        addFloatingWindowIfAbsent(this.gearMenuPanel);
        this.blueprintSavePanel.init(this);
        addFloatingWindowIfAbsent(this.blueprintSavePanel);
        this.blueprintLibraryPanel.init(this);
        addFloatingWindowIfAbsent(this.blueprintLibraryPanel);
        this.blueprintImportPanel.init(this);
        addFloatingWindowIfAbsent(this.blueprintImportPanel);
        this.blueprintPreviewPanel.init(this);
        addFloatingWindowIfAbsent(this.blueprintPreviewPanel);
        long t2 = System.nanoTime();
        panelRegistry.initAll(this);
        long t3 = System.nanoTime();
        
        var eshp = kernel.renderPipeline().entitySelectHighlightPass;
        if (eshp != null) {
            eshp.setHighlightSource(this.selectionHighlight);
        }
        
        var ip = screenCoordinator.getInteractionPanel();
        if (ip != null && ip.isOpen()) {
            ip.init(this);
        }
        long t4 = System.nanoTime();

        long perfCostMs = (t4 - t0) / 1_000_000L;
        if (perfCostMs >= 30L) {
            com.rtsbuilding.rtsbuilding.RtsbuildingMod.LOGGER.info(
                    "RTS-PERF: BuilderScreen.init background/color={} ms, gear={} ms, panelRegistry.initAll={} ms, eshp/interaction={} ms, total={} ms",
                    (t1 - t0) / 1_000_000L, (t2 - t1) / 1_000_000L, (t3 - t2) / 1_000_000L,
                    (t4 - t3) / 1_000_000L, perfCostMs);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        com.rtsbuilding.rtsbuilding.RtsbuildingMod.LOGGER.debug(
                "RTS: BuilderScreen.onClose() called");
        screenCoordinator.closeContainerScreen();
        // 退出 RTS 模式：清空建造选材（选材仅在拿起物品期间有效，退出即失效）
        downSidebarPanel.getRightLayer().cancelSelection();
        // 释放 XYZ 轴调节器可能隐藏的光标（拖拽中退出时防止光标残留隐藏）
        downSidebarPanel.releaseAxisGizmoCursor();
        this.topBarPanel.onRtsExited();
        super.onClose();
        this.cursorStyleManager.restoreDefault();
        // 释放蓝图预览场景的 GPU 资源（FBO/VBO）并恢复可能隐藏的预览拖拽光标，避免跨会话积累
        this.blueprintPreviewPanel.releaseGpuResources();
        this.blueprintPreviewPanel.releaseDragCursor();
        CameraModule cam = kernel.module(CameraModule.class);
        if (cam != null) {
            cam.disableCamera();
        }
    }

    public FloatingWindowLayer getFloatingWindowLayer() {
        return this.floatingWindowLayer;
    }

    /**
     * 将浮动窗口面板加入渲染列表（窗口 resize 会重复触发 init()，需防止同一面板被重复添加）。
     */
    private void addFloatingWindowIfAbsent(UiPanel panel) {
        if (!this.floatingWindowLayer.frontToBackWindows().contains(panel)) {
            this.floatingWindowLayer.frontToBackWindows().add(panel);
        }
    }

    /** {@link UiPanelHost}：宿主屏幕宽度（逻辑像素）。 */
    @Override
    public int getUiWidth() {
        return this.width;
    }

    /** {@link UiPanelHost}：宿主屏幕高度（逻辑像素）。 */
    @Override
    public int getUiHeight() {
        return this.height;
    }

    
    public ColorPickerPanel getColorPickerPanel() {
        return this.colorPickerPanel;
    }

    /** 蓝图保存对话框（蓝图模式框选完成按回车触发）。 */
    public BlueprintSavePanel getBlueprintSavePanel() {
        return this.blueprintSavePanel;
    }

    /** 蓝图文件管理面板（顶栏「文件」→「蓝图文件」）。 */
    public BlueprintLibraryPanel getBlueprintLibraryPanel() {
        return this.blueprintLibraryPanel;
    }

    /** 蓝图导入面板（顶栏「文件」→「导入」）。 */
    public BlueprintImportPanel getBlueprintImportPanel() {
        return this.blueprintImportPanel;
    }

    /** 蓝图结构预览面板（蓝图库面板单击选中文件时打开）。 */
    public BlueprintPreviewPanel getBlueprintPreviewPanel() {
        return this.blueprintPreviewPanel;
    }

    // ── 蓝图放置模式 ────────────────────────────────────────────────

    /** 是否处于蓝图放置模式（等待玩家在世界中点击确认锚点）。 */
    public boolean isBlueprintPlacementActive() {
        return this.blueprintPlacementActive;
    }

    /** 当前正在放置的蓝图（放置模式未激活时返回 null）。 */
    public com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint getActiveBlueprintPlacement() {
        return this.activeBlueprintPlacement;
    }

    /** 当前准星瞄准的锚点方块（放置模式未激活或未命中时为 null）。 */
    public net.minecraft.core.BlockPos getPlacementAnchor() {
        return this.placementAnchor;
    }

    /** 进入蓝图放置模式：加载蓝图，等待玩家在世界中点击确认放置。 */
    public void startBlueprintPlacement(com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint blueprint) {
        if (blueprint == null) return;
        this.activeBlueprintPlacement = blueprint;
        this.blueprintPlacementActive = true;
        this.placementYSteps = 0;
        this.placementAnchor = null;
    }

    /** 取消蓝图放置模式（Esc / 再次点击「使用」）。 */
    public void cancelBlueprintPlacement() {
        this.blueprintPlacementActive = false;
        this.activeBlueprintPlacement = null;
        this.placementAnchor = null;
    }

    /** 确认放置：把蓝图 + 锚点发给服务端启动 BLUEPRINT_BUILD 工作流，并退出放置模式。 */
    public void confirmBlueprintPlacement(net.minecraft.core.BlockPos anchor) {
        if (anchor == null || !this.blueprintPlacementActive || this.activeBlueprintPlacement == null) return;
        com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway
                .sendPlaceBlueprint(this.activeBlueprintPlacement, anchor, this.placementYSteps);
        cancelBlueprintPlacement();
    }

    /**
     * 系统文件拖放回调：导入面板打开时缓存拖入的文件路径，落点判定延迟到渲染阶段
     * （见 {@link #handlePendingFileDrop}），只有落在上传区圆角框内才触发导入。
     * Minecraft 由 GLFW drop 回调经 {@code MouseHandler} 触发本方法。
     */
    @Override
    public void onFilesDrop(List<Path> paths) {
        if (blueprintImportPanel == null || !blueprintImportPanel.isOpen()
                || paths == null || paths.isEmpty()) {
            return;
        }
        // drop 回调阶段 RTS 缩放坐标系不稳定（screen.width 可能处于临时改写窗口期），
        // 先缓存路径，等下一帧渲染时用稳定坐标系判定落点
        this.pendingDropPaths = paths;
    }

    /**
     * 当前是否处于 RTS 虚拟坐标层：
     * <ul>
     *   <li>固定缩放渲染层内（{@code fixedRtsScaleRenderPass}）——{@code this.width} 已临时改为虚拟宽；</li>
     *   <li>或 RTS 缩放 == 原版 GUI 缩放（虚拟坐标系即 GUI 坐标系，不会进入固定缩放层）。</li>
     * </ul>
     * 两层中 {@code mouseX/mouseY} 与 {@link UiPanel} 的 bounds 均为同一基准，可直接用于命中判断。
     */
    private boolean isVirtualCoordinateLayer() {
        if (scaleManager.isInRenderPass()) {
            return true;
        }
        var window = Minecraft.getInstance().getWindow();
        return window != null
                && Math.abs(scaleManager.getRtsGuiScale() - window.getGuiScale()) < 0.001;
    }

    /**
     * 渲染阶段处理缓存的拖放文件：此时 RTS 虚拟坐标系稳定、与面板渲染/命中同基准，
     * 把鼠标落点换算到虚拟坐标后，只有落在导入面板上传区圆角框内才触发导入。
     */
    private void handlePendingFileDrop(int mouseX, int mouseY) {
        if (pendingDropPaths == null) {
            return;
        }
        List<Path> paths = pendingDropPaths;
        pendingDropPaths = null;
        if (blueprintImportPanel == null || !blueprintImportPanel.isOpen()
                || Minecraft.getInstance().getWindow() == null) {
            return;
        }
        // 优先用实时光标（拖放落点），失败回退 gui scaled → RTS 虚拟坐标换算
        double[] live = BlueprintImportPanel.liveCursorVirtual(this);
        if (live != null) {
            if (blueprintImportPanel.isInsideDropZone(live[0], live[1])) {
                blueprintImportPanel.onFilesDropped(paths);
            }
            return;
        }
        double currentScale = Minecraft.getInstance().getWindow().getScreenWidth()
                / (double) Math.max(1, this.width);
        double renderScale = scaleManager.getRtsGuiScale() / currentScale;
        double vx = mouseX / renderScale;
        double vy = mouseY / renderScale;
        if (blueprintImportPanel.isInsideDropZone(vx, vy)) {
            blueprintImportPanel.onFilesDropped(paths);
        }
    }

    /**
     * 弹出系统文件选择对话框（支持多选），返回用户选择的蓝图文件路径列表；取消返回空列表。
     * <p>优先使用 LWJGL 内置 TinyFD 原生文件对话框（不经 AWT，不受 {@code java.awt.headless}
     * 限制，支持 Windows/macOS/Linux 桌面图形后端，多选结果以 {@code '|'} 分隔），
     * 失败时回退 AWT FileDialog（单选）。</p>
     */
    public static List<Path> chooseBlueprintFiles() {
        Minecraft mc = Minecraft.getInstance();
        // 优先使用 LWJGL 内置 TinyFD 原生文件对话框：不经 AWT，不受 java.awt.headless 限制，
        // 支持 Windows/macOS/Linux 桌面图形后端（与客户端本身同平台）。
        if (TinyFileDialogSupport.canOpenFileDialog()) {
            String selected;
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                org.lwjgl.PointerBuffer filters = stack.mallocPointer(5);
                filters.put(stack.UTF8("*.nbt"));
                filters.put(stack.UTF8("*.schem"));
                filters.put(stack.UTF8("*.schematic"));
                filters.put(stack.UTF8("*.litematic"));
                filters.put(stack.UTF8("*.json"));
                filters.flip();
                selected = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                        Component.translatable("screen.rtsbuilding.blueprint.import.title").getString(),
                        null, filters, "Blueprint files", true);
            }
            if (selected == null || selected.isBlank()) {
                return List.of();
            }
            // tinyfd 多选结果用 '|' 分隔
            List<Path> paths = new ArrayList<>();
            for (String part : selected.split("\\|")) {
                String s = part == null ? "" : part.trim();
                if (!s.isEmpty()) {
                    paths.add(java.nio.file.Path.of(s));
                }
            }
            return paths;
        }

        // AWT 兜底：仅当 TinyFD 探测不到图形后端时使用。
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            com.rtsbuilding.rtsbuilding.RtsbuildingMod.LOGGER.warn("当前环境为 headless，无法打开文件选择对话框");
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable("message.rtsbuilding.blueprint.import.failed",
                                "headless environment"), true);
            }
            return List.of();
        }

        final Path[] result = {null};
        final Throwable[] failure = {null};
        try {
            java.awt.EventQueue.invokeAndWait(() -> {
                try {
                    // 使用 AWT 原生文件对话框：支持 Windows/Linux 桌面环境（单选）
                    java.awt.Frame dummy = new java.awt.Frame();
                    java.awt.FileDialog dialog = new java.awt.FileDialog(dummy,
                            Component.translatable("screen.rtsbuilding.blueprint.import.title").getString(),
                            java.awt.FileDialog.LOAD);
                    dialog.setFilenameFilter((dir, name) -> {
                        String lower = name.toLowerCase(java.util.Locale.ROOT);
                        return lower.endsWith(".nbt") || lower.endsWith(".schem")
                                || lower.endsWith(".schematic") || lower.endsWith(".litematic")
                                || lower.endsWith(".json");
                    });
                    dialog.setVisible(true);
                    String file = dialog.getFile();
                    String dir = dialog.getDirectory();
                    dialog.dispose();
                    dummy.dispose();
                    if (file != null) {
                        result[0] = java.nio.file.Path.of(dir == null ? file : dir + file);
                    }
                } catch (Throwable t) {
                    failure[0] = t;
                }
            });
        } catch (Exception ex) {
            failure[0] = ex;
        }

        if (failure[0] != null) {
            com.rtsbuilding.rtsbuilding.RtsbuildingMod.LOGGER.warn("打开文件选择对话框失败", failure[0]);
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable("message.rtsbuilding.blueprint.import.failed",
                                failure[0].getMessage() == null
                                        ? failure[0].getClass().getSimpleName()
                                        : failure[0].getMessage()), true);
            }
            return List.of();
        }
        return result[0] == null ? List.of() : List.of(result[0]);
    }

    public int getRightSidebarWidth() {
        return this.rightSidebarPanel.getCurrentWidth();
    }

    
    public int getDownSidebarHeight() {
        return this.downSidebarPanel.getCurrentHeight();
    }

    
    public int getLeftSidebarWidth() {
        return this.leftSidebarPanel.getCurrentWidth();
    }

    public boolean isMouseOverUI(double mouseX, double mouseY) {
        return screenCoordinator.isMouseOverUI(mouseX, mouseY, floatingWindowLayer, topBarPanel);
    }

    
    public void unfocusGridSearch() {
        if (downSidebarPanel != null) {
            downSidebarPanel.getRightLayer().unfocusSearch();
        }
    }

    public boolean isMouseOverUiPanelApi(double mouseX, double mouseY) {
        
        if (floatingWindowLayer != null
                && floatingWindowLayer.isMouseOverWindowOrResizableBorder(mouseX, mouseY)) {
            return true;
        }
        
        if (topBarPanel != null && topBarPanel.isMouseOverAnyPopup((int) mouseX, (int) mouseY)) {
            return true;
        }
        
        if (mouseY < TopBarLayoutHelper.TOP_BAR_HEIGHT) {
            return true;
        }
        
        int leftW = getLeftSidebarWidth();
        if (leftW > 0 && mouseX < leftW) {
            return true;
        }
        
        int rightW = getRightSidebarWidth();
        if (rightW > 0 && mouseX >= getRtsVirtualWidth() - rightW) {
            return true;
        }
        
        int downH = getDownSidebarHeight();
        if (downH > 0 && mouseY >= getRtsVirtualHeight() - downH) {
            return true;
        }

        // XYZ 轴视角调节器悬浮于下面板右上角：下板被压缩到很小时它可能越过下板矩形顶部，
        // 需要单独识别，避免点击被当成世界区域操作
        if (downSidebarPanel != null && downSidebarPanel.isMouseOverAxisGizmo((int) mouseX, (int) mouseY)) {
            return true;
        }
        return false;
    }

    /**
     * 查询 XYZ 轴视角调节器是否正在拖拽旋转。
     * <p>拖拽期间应跳过点击模式/框选模式等世界交互渲染，避免光标隐藏后误判目标。</p>
     */
    public boolean isAxisGizmoDragging() {
        return downSidebarPanel != null && downSidebarPanel.isAxisGizmoDragging();
    }

    /**
     * 查询蓝图预览面板是否正在拖拽旋转。
     */
    public boolean isBlueprintPreviewDragging() {
        return blueprintPreviewPanel != null && blueprintPreviewPanel.isPreviewDragging();
    }

    /**
     * 查询是否有任何「光标已隐藏/锁定的拖拽旋转」进行中（XYZ 轴调节器或蓝图预览）。
     * <p>此期间光标 DISABLED 锁定，鼠标坐标与视觉不一致，世界内线框 pass 渲染与
     * 悬浮判定应全部跳过，避免 pass/悬浮高亮渲染在错误位置造成视觉错乱。</p>
     */
    public boolean isAnyDragActive() {
        return isAxisGizmoDragging() || isBlueprintPreviewDragging();
    }

    /**
     * RTS 虚拟画布宽度（物理像素 ÷ RTS GUI 缩放基准）。
     * <p>鼠标事件经 {@code BuilderScreenScaleManager} 统一换算到该虚拟坐标系，
     * 渲染也在同一坐标系下完成（GUI 缩放 ≠ RTS 基准时按基准缩放显示），
     * 因此面板/世界区域判定统一用虚拟尺寸，避免坐标错位。</p>
     */
    public int getRtsVirtualWidth() {
        return Math.max(1, (int) Math.round(
                Minecraft.getInstance().getWindow().getScreenWidth() / getRtsGuiScale()));
    }

    /**
     * RTS 虚拟画布高度（物理像素 ÷ RTS GUI 缩放基准），与 {@link #getRtsVirtualWidth()} 同坐标系。
     */
    public int getRtsVirtualHeight() {
        return Math.max(1, (int) Math.round(
                Minecraft.getInstance().getWindow().getScreenHeight() / getRtsGuiScale()));
    }

    
    public boolean isClickButtonSelected() {
        return leftSidebarPanel != null && leftSidebarPanel.isClickButtonSelected();
    }

    
    public boolean isItemPickupActive() {
        return leftSidebarPanel != null && leftSidebarPanel.isItemPickupActive();
    }

    // 相机是否激活（与服务端 RtsCameraManager 的漏斗/操作校验保持一致）
    public boolean isCameraActive() {
        CameraModule cam = kernel.module(CameraModule.class);
        return cam != null && cam.isCameraEnabled();
    }

    
    public boolean isInteractiveMode() {
        return topBarPanel != null
                && topBarPanel.getCurrentMode() == com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.ModeSwitcher.Mode.INTERACTIVE;
    }

    
    public boolean isBlueprintMode() {
        return topBarPanel != null
                && topBarPanel.getCurrentMode() == com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.ModeSwitcher.Mode.BLUEPRINT;
    }

    
    public boolean isBuildMode() {
        return topBarPanel != null
                && topBarPanel.getCurrentMode() == com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.ModeSwitcher.Mode.BUILD;
    }

    
    public boolean isUltimineActive() {
        return leftSidebarPanel != null && leftSidebarPanel.isUltimineActive();
    }

    
    public boolean isConstructionSelected() {
        return leftSidebarPanel != null && leftSidebarPanel.isConstructionSelected();
    }

    
    public boolean isDestructionSelected() {
        return leftSidebarPanel != null && leftSidebarPanel.isDestructionSelected();
    }

    /**
     * 当前激活的建造/破坏形状（单方块等返回 null），供下嵌层形状模式调节器与 X 键使用。
     * 建造/破坏两侧共用同一套调节 UI，各自记忆形状模式。
     */
    @Nullable
    public BuildShape getActiveBuildShape() {
        if (leftSidebarPanel == null) return null;
        if (leftSidebarPanel.isConstructionSelected()) return leftSidebarPanel.getBuildShape();
        if (leftSidebarPanel.isDestructionSelected()) return leftSidebarPanel.getBreakShape();
        return null;
    }

    /**
     * 是否显示形状/单方块调节框（下嵌层）：仅顶栏处于「建造」模式且左栏建造或破坏侧激活时显示，
     * 交互/蓝图模式不显示。建造/破坏两侧共用同一套调节 UI（替换开关 + 形状模式分段控件）。
     */
    public boolean isShapeAdjusterActive() {
        return isBuildMode() && leftSidebarPanel != null
                && (leftSidebarPanel.isConstructionSelected() || leftSidebarPanel.isDestructionSelected());
    }

    
    public boolean isBindModeActive() {
        return leftSidebarPanel != null && leftSidebarPanel.isBindModeActive();
    }

    
    public void clearBoxSelection() {
        kernel.renderPipeline().boxSelector.reset();
        var bsp = kernel.renderPipeline().boxSelectionPass;
        if (bsp != null) bsp.clearCache();
    }

    /**
     * 尝试打开蓝图保存对话框：仅蓝图模式 + 框选完成 + 非点击模式下生效。
     *
     * @return 是否已打开对话框（供调用方消费事件）
     */
    public boolean tryOpenBlueprintSave() {
        if (!isBlueprintMode()) return false;
        if (!com.rtsbuilding.rtsbuilding.Config.areBlueprintsEnabled()) return false;
        if (isClickButtonSelected()) return false;
        var sel = kernel.renderPipeline().boxSelector;
        if (sel == null || sel.getPhase() != com.rtsbuilding.rtsbuilding.client.render.pass.BoxSelector.Phase.COMPLETE) {
            return false;
        }
        if (blueprintSavePanel.isOpen()) return false;
        blueprintSavePanel.openForSelection(sel.getMinCorner(), sel.getMaxCorner());
        return true;
    }

    
    
    

    public void showContainerScreen(Screen screen) {
        screenCoordinator.showContainerScreen(screen, floatingWindowLayer, this);
    }

    
    public com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction.InteractionPanel getInteractionPanel() {
        return screenCoordinator.getInteractionPanel();
    }

    
    public com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction.InteractionPanel getOrCreateInteractionPanel() {
        return screenCoordinator.getOrCreateInteractionPanel(this);
    }

    public boolean hasContainerScreen() {
        return screenCoordinator.hasContainerScreen();
    }

    public void closeContainerScreen() {
        screenCoordinator.closeContainerScreen();
    }

    /**
     * 容器槽位放下物品后的回调：若放下的正是当前启用选材则取消选材
     *（启用仅在“拿起”期间有效，放入容器即失效）。由 InteractionPanel 调用。
     */
    public void cancelGridSelectionIf(ItemStack carried) {
        downSidebarPanel.getRightLayer().cancelSelectionIf(carried);
    }

    
    public double getRtsGuiScale() {
        return scaleManager.getRtsGuiScale();
    }

    
    public String rtsGuiScaleLabel() {
        return scaleManager.rtsGuiScaleLabel();
    }

    
    public void adjustRtsGuiScale(double delta) {
        scaleManager.adjustRtsGuiScale(delta);
    }

    
    public void setRtsGuiScale(double scale) {
        scaleManager.setRtsGuiScale(scale);
    }

    /** 重置 RTS GUI 缩放为自动跟随原版（窗口变化时 UI 与原版一致缩放）。 */
    public void resetRtsGuiScale() {
        scaleManager.resetToAutoRtsGuiScale();
    }

    
    public void enableUiScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        scaleManager.enableUiScissor(g, x1, y1, x2, y2);
    }

    
    private boolean renderWithFixedRtsGuiScale(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        return scaleManager.renderWithFixedRtsGuiScale(this, g, mouseX, mouseY, partialTick);
    }

    private RtsUiScaleFrame enterFixedRtsGuiScale() {
        return scaleManager.enterFixedRtsGuiScale(this);
    }

    @javax.annotation.Nullable
    private Boolean scaleMouseEvent(double mouseX, double mouseY,
            java.util.function.BiFunction<Double, Double, Boolean> handler) {
        return scaleManager.scaleMouseEvent(this, mouseX, mouseY, handler);
    }

    private boolean scaleMouseEventVoid(double mouseX, double mouseY,
            java.util.function.BiConsumer<Double, Double> handler) {
        return scaleManager.scaleMouseEventVoid(this, mouseX, mouseY, handler);
    }

    
    
    

    @Override
    public void tick() {
        super.tick();
        cursorWrapHandler.tick(kernel.module(CameraModule.class), scaleManager.getRtsGuiScale(),
                getRightSidebarWidth(), getDownSidebarHeight());
        screenCoordinator.tickContainerScreen();
        // 物品拾取（漏斗）自动触发：点击模式跟随指针持续拾取，框选模式确认即拾取
        buildInteractionHandler.handleTick(this, leftSidebarPanel);
    }

    
    
    

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 仅在 RTS 虚拟坐标层处理缓存的拖放文件（此时坐标系与面板渲染/命中一致，
        // 避免在外层 gui scaled 层换算导致落点命中失败）
        if (isVirtualCoordinateLayer()) {
            handlePendingFileDrop(mouseX, mouseY);
        }
        
        if (!scaleManager.isInRenderPass() && renderWithFixedRtsGuiScale(guiGraphics, mouseX, mouseY, partialTick)) {
            
            renderPostScaleTooltip(guiGraphics, mouseX, mouseY);
            return;
        }

        
        
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF000000);

        
        
        int rightW = getRightSidebarWidth();
        int downH = getDownSidebarHeight();
        if (screenBackgroundPanel != null && ViewCaptureService.hasValidFrame()) {
            int contentX = 0;
            int contentY = ScreenBackgroundPanel.BACKGROUND_TOP_Y
                + (DownSidebarLayoutHelper.DOWN_BAR_HEIGHT - downH) / 2;
            int contentW = this.width - rightW;
            
            int refContentH = this.height - ScreenBackgroundPanel.BACKGROUND_TOP_Y - DownSidebarLayoutHelper.DOWN_BAR_HEIGHT;
            if (contentW > 0 && refContentH > 0) {
                screenBackgroundPanel.renderCapturedFrameAt(guiGraphics,
                        contentX, contentY, contentW, refContentH);
            }
        }

        
        
        
        boolean mouseOverFloating = floatingWindowLayer != null
                && floatingWindowLayer.isMouseOverWindowOrResizableBorder(mouseX, mouseY);
        panelRegistry.renderContentPanels(guiGraphics, mouseX, mouseY, partialTick, mouseOverFloating);

        
        if (screenBackgroundPanel != null) {
            screenBackgroundPanel.renderOverlays(guiGraphics, mouseX, mouseY);
        }
        if (rightSidebarPanel != null) {
            rightSidebarPanel.renderOverlays(guiGraphics, mouseX, mouseY);
        }
        if (downSidebarPanel != null) {
            downSidebarPanel.renderOverlays(guiGraphics, mouseX, mouseY);
        }

        
        if (topBarPanel != null) {
            topBarPanel.renderOverlays(guiGraphics, mouseX, mouseY);
        }
        if (leftSidebarPanel != null) {
            leftSidebarPanel.renderOverlays(guiGraphics, mouseX, mouseY);
        }

        
        
        RenderSystem.clear(256, false); 
        if (floatingWindowLayer != null) {
            floatingWindowLayer.renderFloatingWindows(guiGraphics, mouseX, mouseY);
        }

        
        if (entityInteractionHandler != null) {
            entityInteractionHandler.validatePanel(this);
        }

        
        if (leftSidebarPanel != null && !leftSidebarPanel.isClickButtonSelected()
                && !isAnyDragActive()
                && mouseX >= getLeftSidebarWidth() && mouseX < this.width - rightW
                && mouseY >= ScreenBackgroundPanel.BACKGROUND_TOP_Y
                && mouseY < this.height - downH
                && !isMouseOverUI(mouseX, mouseY)) {
            var bs = kernel.renderPipeline().boxSelector;
            bs.updateHoverFromScreen(Minecraft.getInstance(), this, RtsKeyMappings.isPlaceOffsetDown());
        }

        // 蓝图放置模式：实时更新准星瞄准的锚点（供幽灵预览 pass 渲染与点击确认使用）
        if (isBlueprintPlacementActive() && !isAnyDragActive()) {
            var ray = com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster
                    .computeCursorRay(Minecraft.getInstance(), this);
            this.placementAnchor = null;
            if (ray != null) {
                var hit = ray.raycastBlock(Minecraft.getInstance());
                if (hit != null) {
                    // 按住 Ctrl：往命中面外侧偏移一格（与建造放置偏移 boxSelector 一致）
                    this.placementAnchor = RtsKeyMappings.isPlaceOffsetDown()
                            ? hit.getBlockPos().relative(hit.getDirection())
                            : hit.getBlockPos();
                }
            }
        }

        cursorStyleManager.update(mouseX, mouseY);
        cursorWrapHandler.applyWrapIfPending();

        // 容器→网络：carried 物品最上层跟随鼠标（离开容器面板后容器 screen 不再渲染它，
        // 这里补渲染保证拖回网格/存入网络的全程可见）
        var carried = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.containerMenu.getCarried()
                : net.minecraft.world.item.ItemStack.EMPTY;
        if (!carried.isEmpty()) {
            GuiItemRenderer.drawItem(guiGraphics, carried, mouseX - 8, mouseY - 8, 300);
        }

        renderLineBrushHint(guiGraphics);
        renderOverlayMessages(guiGraphics, mouseX, mouseY, partialTick);

        if (Minecraft.getInstance().gui.getDebugOverlay().showDebugScreen()) {
            Minecraft.getInstance().gui.getDebugOverlay().render(guiGraphics);
        }
    }

    /**
     * 在 RTS 下面板之上渲染悬浮文字（actionbar 消息）。
     * <p>RTS 是覆盖式 Screen，原版 HUD（含 actionbar）在 Screen 打开时不渲染，
     * 这里补渲染，且位置动态跟随下面板顶部（支持任意窗口尺寸 / RTS GUI 缩放 / 下面板拖拽高度）。</p>
     */
    private void renderOverlayMessages(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null || mc.level == null) return;
        // 下面板顶部（虚拟坐标，随 RTS 缩放 / 下面板拖拽高度动态变化）
        int downBarTopY = this.height - getDownSidebarHeight();
        int downBarW = this.width - getRightSidebarWidth();

        // ── actionbar 悬浮消息（displayClientMessage(comp, true)），以下面板 x 轴中心为基准 ──
        if (mc.gui instanceof com.rtsbuilding.rtsbuilding.client.hud.IRtsOverlayAccess ov) {
            Component overlay = ov.rtsbuilding$getOverlayMessage();
            if (overlay != null && ov.rtsbuilding$getOverlayMessageTime() > 0) {
                String text = overlay.getString();
                if (!text.isEmpty()) {
                    int textW = mc.font.width(text);
                    int x = Math.max(8, downBarW / 2 - textW / 2);
                    int y = downBarTopY - 34;
                    g.fill(x - 8, y, x + textW + 8, y + 14, UiPalette.get("overlay_bg"));
                    g.drawString(mc.font, text, x, y + 2, UiPalette.get("tooltip_text"));
                }
            }
        }
    }

    /** 线/墙/面画笔进行中时，在下面板上方正中央绘制当前步骤与操作提示。 */
    private void renderLineBrushHint(GuiGraphics g) {
        var lineBrush = kernel.renderPipeline().lineBrush;
        // 提示文案由形状自行提供（BuildShape.hint），状态机不再感知具体形状
        String hint = lineBrush.currentHint();
        if (hint == null) return;
        // 下面板范围：x=0 到 (width - 右栏宽)，顶部 y = height - 下面板高
        int downBarW = this.width - getRightSidebarWidth();
        int downBarY = this.height - getDownSidebarHeight();
        int textW = Minecraft.getInstance().font.width(hint);
        int x = downBarW / 2 - textW / 2;
        int y = downBarY - 18;
        g.fill(x - 8, y, x + textW + 8, y + 14, UiPalette.get("overlay_bg"));
        g.drawString(Minecraft.getInstance().font, hint, x, y + 2, UiPalette.get("tooltip_text"));
    }

    
    private void renderPostScaleTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (downSidebarPanel == null) return;
        var stack = downSidebarPanel.getRightLayer().getHoveredSlotStack();
        if (stack.isEmpty()) return;
        g.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
    }

    
    
    
    
    

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Boolean scaled = scaleMouseEvent(mouseX, mouseY, (x, y) -> mouseClicked(x, y, button));
        if (scaled != null) return scaled;
        return eventDispatcher.dispatch(new MouseClickEvent(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        Boolean scaled = scaleMouseEvent(mouseX, mouseY, (x, y) -> mouseReleased(x, y, button));
        if (scaled != null) return scaled;
        return eventDispatcher.dispatch(new MouseReleaseEvent(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scaleManager.scaleMouseEventQuad(this, mouseX, mouseY, button, dragX, dragY,
                (x, y, btn, dx, dy) -> mouseDragged(x, y, btn, dx, dy))) {
            return true;
        }
        return eventDispatcher.dispatch(new MouseDragEvent(mouseX, mouseY, button, dragX, dragY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Boolean scaled = scaleMouseEvent(mouseX, mouseY, (x, y) -> mouseScrolled(x, y, scrollX, scrollY));
        if (scaled != null) return scaled;
        return eventDispatcher.dispatch(new MouseScrollEvent(mouseX, mouseY, scrollX, scrollY));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (scaleMouseEventVoid(mouseX, mouseY, (x, y) -> mouseMoved(x, y))) return;
        eventDispatcher.dispatch(new MouseMoveEvent(mouseX, mouseY));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return eventDispatcher.dispatch(new KeyPressEvent(keyCode, scanCode, modifiers));
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return eventDispatcher.dispatch(new CharEvent(codePoint, modifiers));
    }

}
