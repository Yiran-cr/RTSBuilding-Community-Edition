package com.rtsbuilding.uifw.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * uifw 主题调色板（全库颜色唯一来源，支持宿主 mod 自定义换肤）。
 *
 * <p><b>加载机制</b>：启动后从资源栈扫描两类主题 JSON 并按 {@code priority} 合并
 * （后加载的覆盖同 key）：
 * <ol>
 *   <li>默认主题：{@code assets/uifw/theme/default.json}（uifw 自带，兜底）</li>
 *   <li>宿主主题：<b>{@code assets/&lt;任意命名空间&gt;/theme/uifw.json}</b>——
 *       使用 uifw 作为前置的 mod 只需在自己的资源目录放这个文件即可整套换肤，零代码。</li>
 * </ol>
 *
 * <p><b>JSON 结构</b>：
 * <pre>{@code
 * {
 *   "priority": 0,                 // 合并优先级，大的后加载（覆盖）；默认 0
 *   "colors": { "key": "#RRGGBB", ... },        // dark 基线色（#AARRGGBB 亦支持）
 *   "light_overrides": { "key": "#RRGGBB", ... } // 亮色模式下的覆盖色（可选）
 * }
 * }</pre>
 *
 * <p><b>明暗双模式</b>：{@link #setLightMode(boolean)} 切换；亮色模式下取
 * {@code light_overrides} 覆盖同 key，其余仍用 {@code colors}。
 * 组件取色统一走 {@link #get(String)}（key 见默认主题），不再硬编码。
 */
public final class UiPalette {

    /** uifw 默认主题（兜底，仅当所有主题缺失时 UI 才能接受完全回退）。 */
    private static final ResourceLocation DEFAULT_THEME = ResourceLocation.fromNamespaceAndPath(
            "uifw", "theme/default.json");

    /** 宿主主题约定文件名：{@code assets/<ns>/theme/uifw.json}（自动发现，无需代码注册）。 */
    private static final String HOST_THEME_FILE = "uifw.json";

    /** 内置兜底色（与 default.json 一致），JSON 加载失败时保证 UI 不崩。 */
    private static final Map<String, Integer> FALLBACK = Map.ofEntries(
            // 基础
            Map.entry("bg", 0xFF2B2B2B), Map.entry("accent", 0xFF636363),
            Map.entry("border", 0xFF0F0F0F), Map.entry("black", 0xFF000000),
            Map.entry("hover_border", 0xFF384565), Map.entry("toggle_on", 0xFF4772B3),
            Map.entry("p1", 0xFF636363), Map.entry("p6", 0xFF252525), Map.entry("p7", 0xFF2F2F2F),
            // 文本
            Map.entry("text", 0xFFCCCCCC), Map.entry("text_hover", 0xFFE8E8E8),
            Map.entry("text_muted", 0xFF888888), Map.entry("text_disabled", 0xFF556677),
            Map.entry("divider", 0xFFC3C2D0),
            // 面板/窗口
            Map.entry("panel_hover_bg", 0xFF384565), Map.entry("panel_border", 0xFF636363),
            Map.entry("titlebar_bg", 0xFF0F0F0F), Map.entry("window_shadow", 0x40000000),
            Map.entry("window_shadow_soft", 0x20000000),
            // 按钮
            Map.entry("button_bg", 0xDD1A232E), Map.entry("button_hover_bg", 0xDD2A3442),
            Map.entry("button_border_light", 0xFF647B92), Map.entry("button_border_dark", 0xFF0D1117),
            Map.entry("button_text", 0xFFD8E3EE), Map.entry("button_text_hover", 0xFFE8F0FA),
            Map.entry("button_text_disabled", 0xFF556677),
            // 滚动条
            Map.entry("scroll_track", 0x662E3B4C), Map.entry("scroll_thumb", 0xFF586A80),
            Map.entry("scroll_thumb_hover", 0xFF6A7E96),
            // 滑块
            Map.entry("slider_track", 0x662E3B4C), Map.entry("slider_thumb", 0xFF586A80),
            Map.entry("slider_thumb_active", 0xFF6A7E96),
            // 开关
            Map.entry("toggle_track_off", 0xFF555555), Map.entry("toggle_thumb", 0xFFFFFFFF),
            // 输入框
            Map.entry("input_cursor", 0xFFFFFFFF),
            // 折叠/设置区
            Map.entry("section_bg", 0xFF636363), Map.entry("section_border", 0xFF000000),
            Map.entry("section_border_hover", 0xFF384565), Map.entry("section_hover_bg", 0x22334455),
            Map.entry("settings_separator", 0xFFFFFFFF),
            // 弹窗
            Map.entry("popup_bg", 0xFF2B2B2B), Map.entry("popup_border", 0xFF0F0F0F),
            Map.entry("popup_item_hover", 0x442A3442),
            // 列表
            Map.entry("list_row_even", 0x141B2430), Map.entry("list_row_odd", 0x1018202A),
            Map.entry("list_row_hover", 0x222A3442), Map.entry("list_separator", 0xFF2E3B4C),
            Map.entry("list_btn", 0xFF3A4A5C), Map.entry("list_btn_hover", 0xFF6A3A3A),
            Map.entry("list_delete", 0xFFB04040),
            // 遮罩/提示
            Map.entry("overlay_bg", 0xAA000000), Map.entry("tooltip_bg", 0xFF2B2B2B),
            Map.entry("tooltip_border", 0xFF0F0F0F), Map.entry("tooltip_text", 0xFFFFFFFF),
            Map.entry("tooltip_shortcut", 0xFF888888),
            // 状态/图标
            Map.entry("status_error", 0xFFE05A5A), Map.entry("status_success", 0xFF7BC58A),
            Map.entry("icon_close", 0xFFFF4444),
            // 页签栏
            Map.entry("tab_bar_bg", 0x55000000), Map.entry("tab_close_hover", 0x3DFFFFFF),
            // 取色器
            Map.entry("picker_swatch_border", 0xFF666666),
            Map.entry("picker_swatch_inactive", 0xFF444444),
            Map.entry("picker_swatch_selected", 0xFFFFFFFF), Map.entry("picker_indicator", 0xFFFFFFFF),
            // 物品角标
            Map.entry("item_count_bg", 0xB0000000),
            // 进度条
            Map.entry("progress_track", 0x662E3B4C), Map.entry("progress_fill", 0xFF4772B3)
    );

    /** 当前模式下生效的颜色（light 时已并入 light_overrides）。 */
    private static final Map<String, Integer> colors = new LinkedHashMap<>();
    /** dark 基线（colors 合并结果）。 */
    private static final Map<String, Integer> darkColors = new HashMap<>();
    /** 亮色模式覆盖（light_overrides 合并结果）。 */
    private static final Map<String, Integer> lightColors = new HashMap<>();

    private static boolean loaded;
    private static boolean lightMode;

    private UiPalette() {}

    /** 惰性加载：首次取色时扫描并合并主题（幂等，重载用 {@link #reload()}）。 */
    public static void load() {
        if (!loaded) reload();
    }

    /**
     * 扫描资源栈中的所有主题 JSON 并合并。资源包 / 宿主 mod 更新主题文件后调用可热重载。
     */
    public static void reload() {
        darkColors.clear();
        lightColors.clear();
        var rm = Minecraft.getInstance().getResourceManager();

        List<ThemeSource> sources = new ArrayList<>();
        rm.getResource(DEFAULT_THEME).ifPresent(r -> sources.add(new ThemeSource(r, -1000)));
        for (Map.Entry<ResourceLocation, Resource> entry
                : rm.listResources("theme", p -> p.getPath().equals(HOST_THEME_FILE)).entrySet()) {
            ResourceLocation loc = entry.getKey();
            if ("uifw".equals(loc.getNamespace())) continue; // 跳过 uifw 自身误放的约定文件
            sources.add(new ThemeSource(entry.getValue(), ThemeSource.priorityOf(entry.getValue())));
        }
        sources.sort(Comparator.comparingInt(ThemeSource::priority));

        for (ThemeSource src : sources) merge(src);
        loaded = true;
        applyMode();
    }

    /** 解析单个主题文件并合并到 dark 基线 / light 覆盖。 */
    private static void merge(ThemeSource src) {
        JsonObject root = readJson(src.resource);
        if (root == null) return;
        JsonObject colorsObj = root.getAsJsonObject("colors");
        if (colorsObj != null) {
            for (Map.Entry<String, JsonElement> e : colorsObj.entrySet()) {
                if (isMetadataKey(e.getKey())) continue;
                int v = parseColor(e.getValue(), FALLBACK.getOrDefault(e.getKey(), 0xFFFFFFFF));
                darkColors.put(e.getKey(), v);
            }
        }
        JsonObject lightObj = root.getAsJsonObject("light_overrides");
        if (lightObj != null) {
            for (Map.Entry<String, JsonElement> e : lightObj.entrySet()) {
                if (isMetadataKey(e.getKey())) continue;
                int v = parseColor(e.getValue(), FALLBACK.getOrDefault(e.getKey(), 0xFFFFFFFF));
                lightColors.put(e.getKey(), v);
            }
        }
    }

    /** 下划线开头（如 {@code _readme}）视为元数据/注释 key，不参与取色。 */
    private static boolean isMetadataKey(String key) {
        return key.startsWith("_");
    }

    /** 按当前明暗模式重算生效颜色表。 */
    private static void applyMode() {
        colors.clear();
        colors.putAll(darkColors);
        if (lightMode) colors.putAll(lightColors);
    }

    /** 解析主题文件为 JsonObject，失败返回 null。 */
    private static JsonObject readJson(Resource resource) {
        try (var reader = new BufferedReader(new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | IllegalStateException e) {
            return null;
        }
    }

    /** 解析单色值：支持 {@code "#RRGGBB"} 与 {@code "#AARRGGBB"}；非法回退 fallback。 */
    private static int parseColor(JsonElement el, int fallback) {
        if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            return fallback;
        }
        String hex = el.getAsString().trim();
        if (!hex.startsWith("#")) return fallback;
        try {
            long v = Long.parseLong(hex.substring(1), 16);
            if (hex.length() - 1 == 8) return (int) v;           // AARRGGBB
            if (hex.length() - 1 == 6) return 0xFF000000 | (int) v; // RRGGBB -> A=FF
            return fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ── 取色 API ──

    /** 按主题 key 取色（当前明暗模式下生效值）。key 缺失时回退内置默认。 */
    public static int get(String key) {
        load();
        return colors.getOrDefault(key, FALLBACK.getOrDefault(key, 0xFFFFFFFF));
    }

    /** 按主题 key 取色，key 缺失回退给定值。 */
    public static int get(String key, int fallback) {
        load();
        return colors.getOrDefault(key, fallback);
    }

    // ── 明暗模式（ThemeManager 驱动） ──

    public static void setLightMode(boolean light) {
        if (lightMode != light) {
            lightMode = light;
            applyMode();
        }
    }

    public static boolean isLightMode() {
        return lightMode;
    }

    // ── 便捷方法（兼容历史语义，key 见默认主题） ──

    public static int bg() { return get("bg"); }
    public static int accent() { return get("accent"); }
    public static int border() { return get("border"); }
    public static int black() { return get("black"); }
    public static int hoverBorder() { return get("hover_border"); }
    public static int toggleOn() { return get("toggle_on"); }
    public static int p1() { return get("p1"); }
    public static int p6() { return get("p6"); }
    public static int p7() { return get("p7"); }

    public static boolean isLoaded() { return loaded; }

    /** 待合并的主题来源（资源 + 优先级）。 */
    private record ThemeSource(Resource resource, int priority) {
        /** 读取 JSON 中的 priority 字段（默认 0）。 */
        static int priorityOf(Resource r) {
            JsonObject root = readJson(r);
            if (root != null && root.has("priority") && root.get("priority").isJsonPrimitive()) {
                try {
                    return root.get("priority").getAsInt();
                } catch (NumberFormatException ignored) {
                }
            }
            return 0;
        }
    }
}
