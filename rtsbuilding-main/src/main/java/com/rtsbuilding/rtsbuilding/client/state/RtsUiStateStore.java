package com.rtsbuilding.rtsbuilding.client.state;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RTS 界面状态持久化基建——面板位置/尺寸/开关/数值的统一 JSON 存储。
 *
 * <p>解决「RTS 面板位置、按钮开启状态重启后丢失」的问题：把用户在 RTS 中调整的
 * 界面布局（浮动面板位置与尺寸、侧边栏宽高）与各类开关状态（调试浮层、射线剔除、
 * 相机反转平移、灵敏度等）持久化到 {@code config/rts_building/ui_state.json}，
 * 下次进入 RTS 时自动恢复。</p>
 *
 * <p>读写模式与 {@link com.rtsbuilding.rtsbuilding.client.input.RtsKeybinds} 一致：
 * Gson 序列化 + 临时文件 {@code ui_state.json.tmp} 原子替换，避免写入中途崩溃损坏文件。
 * 本类只负责 JSON 的读写与内存缓存，具体「哪个面板/开关参与持久化」由业务层
 * （{@code BuilderScreen}）决定并调用对应 getter/setter。</p>
 *
 * <p>JSON 结构：
 * <pre>
 * {
 *   "panels": {
 *     "gear":             { "x":120, "y":80, "w":253, "h":284, "open":true },
 *     "blueprint_library":{ "x":50,  "y":60, "w":400, "h":300, "open":false }
 *   },
 *   "sidebar": { "rightWidth":260, "downHeight":180, "rightUpperHeight":120, "downLeftWidth":360 },
 *   "toggles": { "debug_overlay":true, "ray_culling":false, "invert_pan_x":false, "invert_pan_y":false },
 *   "numbers": { "camera_sensitivity":1.0 }
 * }
 * </pre></p>
 */
public final class RtsUiStateStore {

    /** 持久化文件路径：config/rts_building/ui_state.json。 */
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("rts_building/ui_state.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 单个浮动面板的状态快照（位置 + 尺寸 + 是否开启）。 */
    public static final class PanelState {
        public int x;
        public int y;
        public int w;
        public int h;
        public boolean open;

        public PanelState() {
        }

        public PanelState(int x, int y, int w, int h, boolean open) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.open = open;
        }
    }

    /**
     * 侧边栏尺寸快照（左/右栏宽、下面板高、右栏上下分区高度、下面板左右分区宽度）。
     */
    public static final class SidebarState {
        public int leftWidth;
        public int rightWidth;
        public int downHeight;
        public int rightUpperHeight;
        public int downLeftWidth;

        public SidebarState() {
        }

        public SidebarState(int leftWidth, int rightWidth, int downHeight, int rightUpperHeight, int downLeftWidth) {
            this.leftWidth = leftWidth;
            this.rightWidth = rightWidth;
            this.downHeight = downHeight;
            this.rightUpperHeight = rightUpperHeight;
            this.downLeftWidth = downLeftWidth;
        }
    }

    /** JSON 根节点。 */
    static final class Root {
        Map<String, PanelState> panels = new LinkedHashMap<>();
        SidebarState sidebar = new SidebarState();
        Map<String, Boolean> toggles = new LinkedHashMap<>();
        Map<String, Double> numbers = new LinkedHashMap<>();
    }

    /** 内存缓存（首次访问时懒加载）。 */
    private static Root data;

    private RtsUiStateStore() {
    }

    // ==================================================================
    //  公开 API
    // ==================================================================

    /**
     * 读取指定浮动面板的持久化状态；不存在时返回 null。
     *
     * @param id 面板唯一标识（如 {@code "gear"}、{@code "blueprint_library"}）
     */
    public static PanelState getPanel(String id) {
        return ensureLoaded().panels.get(id);
    }

    /**
     * 记录指定浮动面板的当前状态，下次 {@link #save()} 时落盘。
     */
    public static void setPanel(String id, PanelState state) {
        ensureLoaded().panels.put(id, state);
    }

    /**
     * 读取侧边栏尺寸持久化状态；从未保存过时返回 null。
     */
    public static SidebarState getSidebar() {
        return ensureLoaded().sidebar;
    }

    /**
     * 记录侧边栏尺寸，下次 {@link #save()} 时落盘。
     */
    public static void setSidebar(SidebarState state) {
        ensureLoaded().sidebar = state == null ? new SidebarState() : state;
    }

    /**
     * 读取开关状态；未保存时返回 {@code def}。
     */
    public static boolean getToggle(String id, boolean def) {
        Boolean value = ensureLoaded().toggles.get(id);
        return value == null ? def : value;
    }

    /**
     * 记录开关状态，下次 {@link #save()} 时落盘。
     */
    public static void setToggle(String id, boolean value) {
        ensureLoaded().toggles.put(id, value);
    }

    /**
     * 读取数值型设置；未保存时返回 {@code def}。
     */
    public static double getNumber(String id, double def) {
        Double value = ensureLoaded().numbers.get(id);
        return value == null ? def : value;
    }

    /**
     * 记录数值型设置，下次 {@link #save()} 时落盘。
     */
    public static void setNumber(String id, double value) {
        ensureLoaded().numbers.put(id, value);
    }

    /**
     * 返回当前内存缓存中的面板条目数（用于诊断日志）。
     */
    public static int componentCount() {
        return ensureLoaded().panels.size();
    }

    // ==================================================================
    //  文件读写
    // ==================================================================

    /**
     * 从文件加载持久化数据到内存缓存。
     * 文件不存在或解析失败时使用空数据（等价于默认布局）。
     */
    public static void load() {
        if (!Files.isRegularFile(PATH)) {
            data = new Root();
            return;
        }
        try (var reader = Files.newBufferedReader(PATH)) {
            Root root = GSON.fromJson(reader, Root.class);
            data = root != null ? root : new Root();
        } catch (IOException | RuntimeException ex) {
            RtsbuildingMod.LOGGER.error("读取 UI 状态文件 {} 失败: {}", PATH, ex.getMessage());
            data = new Root();
        }
    }

    /**
     * 把内存缓存写入持久化文件。
     * <p>先写临时文件再 {@link StandardCopyOption#ATOMIC_MOVE} 原子替换，
     * 文件系统不支持原子移动时回退普通替换。</p>
     */
    public static void save() {
        try {
            ensureLoaded();
            Files.createDirectories(PATH.getParent());
            Path tmp = PATH.resolveSibling("ui_state.json.tmp");
            try (var writer = Files.newBufferedWriter(tmp)) {
                GSON.toJson(data, writer);
            }
            try {
                Files.move(tmp, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                Files.move(tmp, PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException ex) {
            RtsbuildingMod.LOGGER.error("写入 UI 状态文件 {} 失败: {}", PATH, ex.getMessage());
        }
    }

    /** 懒加载：首次访问任何数据时从文件读取。 */
    private static Root ensureLoaded() {
        if (data == null) {
            load();
        }
        return data;
    }
}
