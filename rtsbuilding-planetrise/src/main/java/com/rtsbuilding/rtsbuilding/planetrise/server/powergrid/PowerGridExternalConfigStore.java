package com.rtsbuilding.rtsbuilding.planetrise.server.powergrid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.rtsbuilding.rtsbuilding.api.powergrid.ExternalMachineConfig;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsMachineType;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务端<b>外部模组机器类型配置</b>存储（需求文档 4）。
 * <p>
 * 玩家为<b>非本模组注册的机器</b>手动指定类型，仅允许 {@link RtsMachineType#GENERATOR} /
 * {@link RtsMachineType#CONSUMER} 二选一（约束 #5）；纯传输类型（Substation）为模组内部
 * 硬编码、不在玩家配置选项（约束 #6）。配置持久化到 {@code config/rts_building/powergrid_external.json}。
 * <p>
 * key 为 {@code "mod_id:machine_id"}；powerValue / linkRange 由模组提供默认值，玩家可微调（可选）。
 * 服务端单线程访问；保存操作用临时文件原子替换。
 */
public final class PowerGridExternalConfigStore {

    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("rts_building/powergrid_external.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 机器类型键签名序列化用：直接存枚举名。 */
    private static final Map<String, ExternalMachineConfig> CONFIGS = new LinkedHashMap<>();

    private PowerGridExternalConfigStore() {
    }

    static {
        load();
    }

    /** 加载持久化配置（首次访问时执行）。 */
    private static void load() {
        if (!Files.exists(PATH)) {
            return;
        }
        try (var reader = Files.newBufferedReader(PATH)) {
            Map<String, StoredConfig> data = GSON.fromJson(reader, new TypeToken<Map<String, StoredConfig>>() {
            }.getType());
            if (data == null) {
                return;
            }
            for (Map.Entry<String, StoredConfig> e : data.entrySet()) {
                String type = e.getValue().type;
                RtsMachineType mt = parseType(type);
                if (mt != null) {
                    CONFIGS.put(e.getKey(), new ExternalMachineConfig(e.getKey(), mt,
                            e.getValue().powerValue, e.getValue().linkRange, e.getValue().priority));
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static RtsMachineType parseType(String s) {
        try {
            return RtsMachineType.valueOf(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** 全部外部机器配置。 */
    public static Collection<ExternalMachineConfig> all() {
        return CONFIGS.values();
    }

    /** 获取某机器配置；未配置时返回 null。 */
    public static ExternalMachineConfig get(String machineId) {
        return CONFIGS.get(machineId);
    }

    /** 设置（或更新）某机器配置；随后持久化。 */
    public static void set(ExternalMachineConfig config) {
        CONFIGS.put(config.machineId(), config);
        save();
    }

    /** 清除某机器的配置。 */
    public static void clear(String machineId) {
        if (CONFIGS.remove(machineId) != null) {
            save();
        }
    }

    /** 原子写入持久化文件。 */
    private static void save() {
        Map<String, StoredConfig> data = new LinkedHashMap<>();
        for (ExternalMachineConfig c : CONFIGS.values()) {
            data.put(c.machineId(), new StoredConfig(c.type().name(),
                    c.powerValue(), c.linkRange(), c.priority()));
        }
        Path tmp = PATH.resolveSibling("powergrid_external.json.tmp");
        try {
            Files.createDirectories(PATH.getParent());
            try (var writer = Files.newBufferedWriter(tmp)) {
                GSON.toJson(data, writer);
            }
            Files.move(tmp, PATH, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    /** JSON 持久化结构。 */
    private static final class StoredConfig {
        String type;
        double powerValue;
        double linkRange;
        int priority;

        StoredConfig() {
        }

        StoredConfig(String type, double powerValue, double linkRange, int priority) {
            this.type = type;
            this.powerValue = powerValue;
            this.linkRange = linkRange;
            this.priority = priority;
        }
    }
}
