package com.rtsbuilding.rtsbuilding.client.input;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.settings.KeyModifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RTS 键位注册表与自定义持久化。
 * <p>
 * RTS 的按键不再注册到原版 {@code KeyMapping} 系统（不会出现在
 * "选项 → 控制 → 按键绑定"界面），改由 RTS 设置面板内的"按键设置"折叠条目配置。
 * 绑定写入 {@code config/rts_building/keybinds.json}，启动时加载并应用到
 * {@link RtsKeyMappings} 的各个 {@link KeyMapping} 对象上。运行时逻辑全部通过
 * KeyMapping 对象读取，因此改绑定后立即生效，无需改动其他引用点。
 */
public final class RtsKeybinds {

    private RtsKeybinds() {}

    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("rts_building/keybinds.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Entry(String id, KeyMapping mapping) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private static void add(String id, KeyMapping mapping) {
        ENTRIES.add(new Entry(id, mapping));
    }

    static {
        add("open_gear_menu", RtsKeyMappings.OPEN_GEAR_MENU_KEY);
        add("toggle_debug_overlay", RtsKeyMappings.TOGGLE_DEBUG_OVERLAY_KEY);
        add("toggle_camera_mode", RtsKeyMappings.TOGGLE_CAMERA_MODE_KEY);
        add("camera_rotate", RtsKeyMappings.CAMERA_ROTATE_KEY);
        add("camera_pan", RtsKeyMappings.CAMERA_PAN_KEY);
        add("move_player", RtsKeyMappings.MOVE_PLAYER_KEY);
        add("toggle_select_mode", RtsKeyMappings.TOGGLE_SELECT_MODE_KEY);
        add("toggle_bind_mode", RtsKeyMappings.TOGGLE_BIND_MODE_KEY);
        add("toggle_direction_rotate_mode", RtsKeyMappings.TOGGLE_DIRECTION_ROTATE_MODE_KEY);
        add("toggle_item_pickup_mode", RtsKeyMappings.TOGGLE_ITEM_PICKUP_MODE_KEY);
        add("undo", RtsKeyMappings.UNDO_KEY);
        add("cycle_mode", RtsKeyMappings.CYCLE_MODE_KEY);
        add("place_offset", RtsKeyMappings.PLACE_OFFSET_KEY);
        add("line_flat", RtsKeyMappings.LINE_FLAT_KEY);
        add("ultimine", RtsKeyMappings.ULTIMINE_KEY);
    }

    public static List<Entry> entries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    /** 启动时从配置文件加载绑定并应用到 KeyMapping 对象。 */
    public static void load() {
        if (!Files.exists(PATH)) return;
        Map<String, Binding> data;
        try (var reader = Files.newBufferedReader(PATH)) {
            data = GSON.fromJson(reader, new TypeToken<Map<String, Binding>>() {}.getType());
        } catch (IOException ex) {
            return;
        }
        if (data == null) return;
        for (Entry entry : ENTRIES) {
            Binding binding = data.get(entry.id());
            if (binding == null || binding.key == null || binding.key.isBlank()) continue;
            try {
                InputConstants.Key key = InputConstants.getKey(binding.key);
                KeyModifier modifier = parseModifier(binding.modifier);
                entry.mapping().setKeyModifierAndCode(modifier, key);
            } catch (Exception ignored) {
                // 单个非法绑定忽略，保留默认
            }
        }
    }

    /** 把当前全部绑定写入配置文件。 */
    public static void save() {
        Map<String, Binding> data = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            InputConstants.Key key = entry.mapping().getKey();
            data.put(entry.id(), new Binding(key.getName(), entry.mapping().getKeyModifier().name()));
        }
        Path tmp = PATH.resolveSibling("keybinds.json.tmp");
        try {
            Files.createDirectories(PATH.getParent());
            try (var writer = Files.newBufferedWriter(tmp)) {
                GSON.toJson(data, writer);
            }
            Files.move(tmp, PATH, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    /** 恢复单个键位的默认绑定并保存。 */
    public static void reset(KeyMapping mapping) {
        mapping.setKeyModifierAndCode(mapping.getDefaultKeyModifier(), mapping.getDefaultKey());
        save();
    }

    private static KeyModifier parseModifier(String name) {
        if (name == null) return KeyModifier.NONE;
        try {
            return KeyModifier.valueOf(name);
        } catch (Exception ex) {
            return KeyModifier.NONE;
        }
    }

    /** JSON 数据结构：键名 + 修饰键名。 */
    public static final class Binding {
        public String key;
        public String modifier;

        public Binding() {}

        public Binding(String key, String modifier) {
            this.key = key;
            this.modifier = modifier;
        }
    }
}
