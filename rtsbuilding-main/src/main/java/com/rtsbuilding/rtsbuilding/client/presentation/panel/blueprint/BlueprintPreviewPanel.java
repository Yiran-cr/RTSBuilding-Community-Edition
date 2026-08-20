package com.rtsbuilding.rtsbuilding.client.presentation.panel.blueprint;

import com.rtsbuilding.rtsbuilding.client.scene.RtsDummyLevel;
import com.rtsbuilding.rtsbuilding.client.scene.RtsSceneRenderer;
import com.rtsbuilding.rtsbuilding.common.blueprint.io.BlueprintReaders;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import com.rtsbuilding.uifw.render.TextRenderer;
import com.rtsbuilding.uifw.render.UiPalette;
import com.rtsbuilding.uifw.theme.ThemeManager;
import com.rtsbuilding.uifw.window.api.UiPanelHost;
import com.rtsbuilding.uifw.window.window.UiPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreenConstants.TOP_H;

/**
 * 蓝图结构预览面板 —— 展示本地蓝图文件的 3D 结构缩略图（真实方块模型渲染）。
 * <p>
 * 参考 LDLib2 的结构预览（{@code WorldSceneRenderer} + FBO 场景渲染）：
 * 后台线程异步解析蓝图文件 → 把方块状态装入 {@link RtsDummyLevel} 虚拟世界 →
 * 由 {@link RtsSceneRenderer} 渲染到 FBO 并绘制到面板预览区。支持鼠标拖拽旋转、
 * 滚轮缩放，面板底部展示结构尺寸与方块数。
 * <p>
 * 由 {@link BlueprintLibraryPanel} 单击选中蓝图文件时调用 {@link #show(Path)} 打开。
 */
public final class BlueprintPreviewPanel extends UiPanel {

    private static final Logger LOG = LoggerFactory.getLogger("RTS-BlueprintPreview");

    /** 面板默认尺寸。 */
    private static final int DEFAULT_W = 280;
    private static final int DEFAULT_H = 300;
    /** 预览区与面板内容区之间的边距。 */
    private static final int PREVIEW_MARGIN = 6;
    /** 底部信息栏高度（操作提示 + 尺寸/方块数）。 */
    private static final int INFO_AREA_H = 30;

    /** 加载状态机。 */
    private enum LoadState {
        /** 未加载任何蓝图。 */
        IDLE,
        /** 后台解析中。 */
        LOADING,
        /** 解析完成且可渲染。 */
        READY,
        /** 解析失败。 */
        FAILED,
        /** 解析成功但没有可渲染的方块。 */
        EMPTY
    }

    private LoadState state = LoadState.IDLE;
    /** 当前展示的蓝图文件（用于幂等：重复选中同一文件不重新加载）。 */
    private Path currentFile;
    /** 加载失败原因提示。 */
    private Component errorMessage;

    /** 异步加载序号，防止旧任务覆盖新任务结果。 */
    private final AtomicLong loadSeq = new AtomicLong();

    /** 虚拟世界与场景渲染器（打开期间复用，show 时更新方块数据）。 */
    private RtsDummyLevel dummyLevel;
    private RtsSceneRenderer sceneRenderer;

    // ── 拖拽旋转状态（参考 XYZ 轴调节器 AxisGizmoInputHandler） ─────
    /** 是否正处于预览区拖拽旋转中。 */
    private boolean previewDragging;
    /** 拖拽期间系统光标是否已隐藏/锁定（GLFW_CURSOR_DISABLED）。 */
    private boolean cursorHidden;
    /** 进入拖拽时系统光标的物理像素位置（释放时移回该处）。 */
    private double dragStartCursorX;
    private double dragStartCursorY;
    /** 拖拽旋转增量计算的上次光标位置（物理像素）。 */
    private double lastDragCursorX;
    private double lastDragCursorY;

    @Override
    public void init(UiPanelHost screen) {
        super.init(screen);
        this.resizable = false;
    }

    /**
     * 打开预览面板并异步加载指定蓝图文件。
     *
     * @param file 本地蓝图文件路径（.nbt）
     */
    public void show(Path file) {
        if (file == null || !Files.exists(file)) {
            this.state = LoadState.FAILED;
            this.errorMessage = Component.translatable("screen.rtsbuilding.blueprint.preview.failed",
                    Component.translatable("message.rtsbuilding.blueprint.open_file_failed").getString());
            setOpen(true);
            markBroughtToFront();
            return;
        }
        // 幂等：同一文件已在展示且加载成功则不重复加载（保留用户当前旋转视角）
        if (this.currentFile != null && this.currentFile.equals(file)
                && isOpen() && this.state == LoadState.READY) {
            markBroughtToFront();
            return;
        }
        this.currentFile = file;
        this.state = LoadState.LOADING;
        this.errorMessage = null;
        setOpen(true);
        markBroughtToFront();
        loadAsync(file);
    }

    /** 后台线程读取并解析蓝图，成功后回主线程装载方块数据。 */
    private void loadAsync(Path file) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            fail(Component.translatable("screen.rtsbuilding.blueprint.preview.failed", "level unavailable"));
            return;
        }
        long seq = loadSeq.incrementAndGet();
        RegistryAccess registryAccess = mc.level.registryAccess();
        CompletableFuture.supplyAsync(() -> {
            try {
                byte[] data = Files.readAllBytes(file);
                return BlueprintReaders.parse(data, file.getFileName().toString(), registryAccess);
            } catch (java.io.IOException | com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintParseException e) {
                throw new RuntimeException(e);
            }
        }).thenAcceptAsync(blueprint -> {
            // 期间被更新的加载任务或面板已关闭，丢弃过期结果
            if (seq != loadSeq.get() || !isOpen()) return;
            applyBlueprint(blueprint);
        }, mc::execute).exceptionally(ex -> {
            LOG.warn("加载蓝图预览失败: {}", file, ex);
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            fail(Component.translatable("screen.rtsbuilding.blueprint.preview.failed", msg));
            return null;
        });
    }

    /** 把解析出的蓝图装入虚拟世界并触发场景渲染器重编译。 */
    private void applyBlueprint(RtsBlueprint blueprint) {
        Map<BlockPos, BlockState> blockStates = new HashMap<>();
        for (RtsBlueprintBlock block : blueprint.blocks()) {
            if (block.isMissingBlock()) continue;
            BlockState s = block.state();
            if (s == null || s.isAir()) continue;
            blockStates.put(block.relativePos(), s);
        }
        if (blockStates.isEmpty()) {
            this.state = LoadState.EMPTY;
            return;
        }
        ensureSceneResources();
        this.dummyLevel.setBlocks(blockStates);
        this.sceneRenderer.setRenderedBlocks(blockStates.keySet());
        var bounds = this.dummyLevel.getFilledBounds();
        double maxSide = Math.max(Math.max(bounds.getXsize(), bounds.getYsize()), bounds.getZsize());
        this.sceneRenderer.frameStructure(bounds.getCenter(), maxSide);
        this.state = LoadState.READY;
    }

    /** 懒创建虚拟世界与场景渲染器（需要客户端注册表）。 */
    private void ensureSceneResources() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            fail(Component.translatable("screen.rtsbuilding.blueprint.preview.failed", "level unavailable"));
            return;
        }
        if (this.dummyLevel == null) {
            this.dummyLevel = new RtsDummyLevel(mc.level.registryAccess());
        }
        if (this.sceneRenderer == null) {
            this.sceneRenderer = new RtsSceneRenderer(this.dummyLevel);
        }
    }

    private void fail(Component message) {
        this.state = LoadState.FAILED;
        this.errorMessage = message;
    }

    // ── UiPanel 布局与渲染 ─────────────────────────────────────────

    @Override
    protected Component getTitle() {
        // 标题后附上当前预览的蓝图文件名，便于识别预览内容
        if (this.currentFile != null && this.currentFile.getFileName() != null) {
            return Component.translatable("screen.rtsbuilding.blueprint.preview.title_with_name",
                    this.currentFile.getFileName().toString());
        }
        return Component.translatable("screen.rtsbuilding.blueprint.preview.title");
    }

    @Override
    protected int getDefaultWidth() {
        return DEFAULT_W;
    }

    @Override
    protected int getDefaultHeight() {
        return DEFAULT_H;
    }

    @Override
    protected void computeDefaultPosition() {
        if (this.screen != null) {
            // 统一基准：与其它浮窗面板一致——尺寸自适应（不超过屏幕留 8px 边距），
            // 位置水平居中 + 垂直居中，顶部避开顶栏（TOP_H+6）、底部留 8px 边距
            int margin = 8;
            int availableW = this.screen.getUiWidth() - margin * 2;
            int w = Math.min(getDefaultWidth(), availableW);
            int h = Math.min(getDefaultHeight(),
                    Math.max(getMinWindowHeight(), this.screen.getUiHeight() - TOP_H - 6 - margin));
            setSize(w, h);
            positionCentered(TOP_H + 6, margin);
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();
        int ch = contentHeight();
        int textColor = ThemeManager.getTextColor();
        var font = Minecraft.getInstance().font;

        PreviewLayout layout = computeLayout(cx, cy, cw, ch);

        // 场景渲染（真实方块模型 3D 预览）
        if (this.state == LoadState.READY && this.sceneRenderer != null) {
            this.sceneRenderer.render(g, layout.previewX(), layout.previewY(), layout.previewW(), layout.previewH());
        } else {
            // 非就绪态：占位背景
            g.fill(layout.previewX(), layout.previewY(),
                    layout.previewX() + layout.previewW(), layout.previewY() + layout.previewH(), 0xAA101820);
        }

        // 中央状态文字（加载中 / 失败 / 空）
        String status = null;
        if (this.state == LoadState.LOADING) {
            status = Component.translatable("screen.rtsbuilding.blueprint.preview.loading").getString();
        } else if (this.state == LoadState.FAILED) {
            status = errorMessage == null ? "" : errorMessage.getString();
        } else if (this.state == LoadState.EMPTY) {
            status = Component.translatable("screen.rtsbuilding.blueprint.preview.empty").getString();
        }
        if (status != null && !status.isEmpty()) {
            int color = this.state == LoadState.FAILED ? UiPalette.get("status_error") : textColor;
            TextRenderer.drawCentered(g, font, status,
                    layout.previewX() + layout.previewW() / 2,
                    layout.previewY() + (layout.previewH() - font.lineHeight) / 2, color);
        }

        // 底部信息栏：操作提示 + 结构尺寸/方块数
        if (this.state == LoadState.READY) {
            String hint = Component.translatable("screen.rtsbuilding.blueprint.preview.hint").getString();
            TextRenderer.drawCentered(g, font, hint, layout.infoX() + layout.infoW() / 2, layout.infoY(), textColor);
            String sizeText = Component.translatable("screen.rtsbuilding.blueprint.preview.info",
                    layoutSizeX(), layoutSizeY(), layoutSizeZ(), layoutBlockCount()).getString();
            TextRenderer.drawCentered(g, font, sizeText,
                    layout.infoX() + layout.infoW() / 2, layout.infoY() + font.lineHeight + 2,
                    (textColor & 0xFFFFFF) | 0x80000000);
        }
    }

    /** 预览区与底部信息栏布局（渲染与命中检测共用）。 */
    private record PreviewLayout(int previewX, int previewY, int previewW, int previewH,
                                 int infoX, int infoY, int infoW, int infoH) {
    }

    private PreviewLayout computeLayout(int cx, int cy, int cw, int ch) {
        int infoY = cy + ch - INFO_AREA_H;
        int previewY = cy + PREVIEW_MARGIN;
        int previewH = Math.max(1, infoY - previewY - PREVIEW_MARGIN);
        return new PreviewLayout(cx + PREVIEW_MARGIN, previewY,
                cw - PREVIEW_MARGIN * 2, previewH,
                cx + PREVIEW_MARGIN, infoY, cw - PREVIEW_MARGIN * 2, INFO_AREA_H);
    }

    /** 当前蓝图结构尺寸（读取虚拟世界包围盒）与方块数，供底部信息栏展示。 */
    private int layoutSizeX() {
        if (this.dummyLevel == null) return 0;
        var b = this.dummyLevel.getFilledBounds();
        return (int) Math.ceil(b.getXsize());
    }

    private int layoutSizeY() {
        if (this.dummyLevel == null) return 0;
        var b = this.dummyLevel.getFilledBounds();
        return (int) Math.ceil(b.getYsize());
    }

    private int layoutSizeZ() {
        if (this.dummyLevel == null) return 0;
        var b = this.dummyLevel.getFilledBounds();
        return (int) Math.ceil(b.getZsize());
    }

    private int layoutBlockCount() {
        return this.dummyLevel == null ? 0 : this.dummyLevel.getFilledBlockCount();
    }

    // ── 交互：拖拽旋转（隐藏光标 + 无限旋转）/ 滚轮缩放 ──────────────

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        // 左键在预览区按下：开始拖拽旋转（隐藏系统光标）
        if (button == 0 && isInPreviewArea(mouseX, mouseY)) {
            startPreviewDrag();
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 拖拽开始后不再校验鼠标位置，只用增量旋转 → 离开预览区也能持续旋转
        if (this.state == LoadState.READY && this.sceneRenderer != null
                && this.previewDragging && button == 0) {
            rotateByCursorDelta();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /**
     * 基于光标绝对位置计算旋转增量。
     * <p>拖拽期间光标处于 {@code GLFW_CURSOR_DISABLED}（锁定+隐藏）：GLFW 只上报相对位移
     * （虚拟位置），不会产生钳制中心的绝对位置跳变，因此差分始终等于用户真实拖动位移，
     * 无合成事件干扰，无抖动、无轴向偏移。
     * 增量经灵敏度缩放并过滤亚像素死区，旋转更丝滑。</p>
     */
    private void rotateByCursorDelta() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        long win = mc.getWindow().getWindow();
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(win, x, y);
        double dx = x[0] - this.lastDragCursorX;
        double dy = y[0] - this.lastDragCursorY;
        // 灵敏度（旋转度/像素）与亚像素死区：过低噪声忽略，避免微小抖动造成卡顿感
        final double SENSITIVITY = 0.3;
        final double DEADZONE = 0.2;
        if (Math.abs(dx) < DEADZONE) dx = 0;
        if (Math.abs(dy) < DEADZONE) dy = 0;
        // 旋转方向反转：向左拖 → yaw 增（结构绕 Y 轴正向转动），向上拖 → pitch 增
        this.sceneRenderer.rotate((float) (-dx * SENSITIVITY), (float) (-dy * SENSITIVITY));
        this.lastDragCursorX = x[0];
        this.lastDragCursorY = y[0];
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.previewDragging) {
            endPreviewDrag();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ── 光标隐藏 / 钳制 / 恢复（参考 AxisGizmoInputHandler） ─────────

    /** 开始拖拽旋转：锁定并隐藏系统光标（GLFW_CURSOR_DISABLED，只上报相对位移），记录起始位置。 */
    private void startPreviewDrag() {
        if (this.previewDragging) return;
        this.previewDragging = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        long win = mc.getWindow().getWindow();
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(win, x, y);
        this.dragStartCursorX = x[0];
        this.dragStartCursorY = y[0];
        // DISABLED 模式：光标锁定+隐藏，glfwGetCursorPos 返回相对位移（虚拟位置），
        // 不产生绝对位置跳变 → 无限旋转且无合成事件干扰
        GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        this.cursorHidden = true;
        // 切换到 DISABLED 后读取一次虚拟位置作为差分基点
        GLFW.glfwGetCursorPos(win, x, y);
        this.lastDragCursorX = x[0];
        this.lastDragCursorY = y[0];
    }

    /** 结束拖拽旋转：恢复系统光标并移回进入拖拽时的位置。 */
    private void endPreviewDrag() {
        this.previewDragging = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || !this.cursorHidden) return;
        long win = mc.getWindow().getWindow();
        GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        GLFW.glfwSetCursorPos(win, this.dragStartCursorX, this.dragStartCursorY);
        this.cursorHidden = false;
    }

    /** 强制结束拖拽并恢复光标（面板关闭 / 退出 RTS 模式时调用，防光标残留隐藏）。 */
    public void releaseDragCursor() {
        endPreviewDrag();
    }

    /** 查询是否正处于预览区拖拽旋转中（光标已锁定隐藏），供渲染 pass 跳过世界交互渲染。 */
    public boolean isPreviewDragging() {
        return this.previewDragging;
    }

    /** 命中检测（与渲染坐标一致）：点是否落在预览区内。 */
    private boolean isInPreviewArea(double mouseX, double mouseY) {
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();
        int ch = contentHeight();
        PreviewLayout layout = computeLayout(cx, cy, cw, ch);
        return mouseX >= layout.previewX() && mouseX < layout.previewX() + layout.previewW()
                && mouseY >= layout.previewY() && mouseY < layout.previewY() + layout.previewH();
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.state != LoadState.READY || this.sceneRenderer == null) return false;
        if (!isInPreviewArea(mouseX, mouseY)) return false;
        this.sceneRenderer.zoom(scrollY > 0 ? 0.9f : 1.1f);
        return true;
    }

    @Override
    protected void onClose() {
        // 关闭时丢弃未完成加载结果，避免下次打开被旧结果污染
        this.loadSeq.incrementAndGet();
        // 拖拽旋转中关闭：强制恢复光标，防止残留隐藏
        releaseDragCursor();
        this.state = LoadState.IDLE;
        this.currentFile = null;
        this.errorMessage = null;
        super.onClose();
    }

    /** 释放 GPU 资源（FBO / VBO），退出 RTS 模式时调用，避免跨会话积累。 */
    public void releaseGpuResources() {
        if (this.sceneRenderer != null) {
            this.sceneRenderer.releaseResource();
            this.sceneRenderer = null;
        }
        this.dummyLevel = null;
    }
}
