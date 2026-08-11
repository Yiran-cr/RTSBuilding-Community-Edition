package com.rtsbuilding.rtsbuilding.client.util.render;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Shader pack (光影) detection utility.
 *
 * <p>Detects whether an external shader pack is currently active (Iris / OptiFine)
 * via NeoForge {@link ModList} plus reflection plus config-file inspection, so the
 * module has no hard dependency on either loader.
 * Shader packs take over the world rendering pipeline and turn the mod's
 * unlit overlay draws (boundary walls, wireframes, filled faces) into lit,
 * shadow-sampled geometry, which makes them look like dark/shadow artifacts.
 * Rendering passes can query this state to adapt their behaviour.</p>
 *
 * <p>Detection is <b>conservative</b>: whenever a shader loader (Iris / OptiFine)
 * is present but its exact enablement cannot be proven "off" (API changed,
 * config file unreadable, cross-module reflection failed), {@link #isShaderPackActive()}
 * returns {@code true} so the mod refuses to draw translucent overlay geometry
 * instead of letting it become a dark shadow band under the shader pipeline.</p>
 */
public final class ShaderState {

    private ShaderState() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Cache period before re-checking the shader state (ms). */
    private static final long CACHE_MS = 1000;

    private static long lastCheck;
    private static boolean cachedActive;
    private static boolean cacheValid;

    /**
     * @return {@code true} if an external shader pack (Iris or OptiFine) is active,
     *         or if its state cannot be proven inactive (conservative fallback).
     */
    public static boolean isShaderPackActive() {
        long now = System.currentTimeMillis();
        if (!cacheValid || now - lastCheck > CACHE_MS) {
            boolean active = detect();
            if (active != cachedActive) {
                LOGGER.info("[RTS-Building] Shader pack detection: {} (ModList-iris: {}, Iris API: {}, iris.properties: {}, OptiFine: {}, conservative-present: {})",
                    active ? "ACTIVE" : "inactive",
                    modListIrisLoaded(), irisApiActive(), irisPropertiesEnabled(), optifineActive(),
                    isIrisPresent() || isOptifinePresent());
            }
            cachedActive = active;
            lastCheck = now;
            cacheValid = true;
        }
        return cachedActive;
    }

    /**
     * @return {@code true} if Iris is present and a shader pack is in use.
     */
    public static boolean isIrisActive() {
        if (!isShaderPackActive()) return false;
        return isIrisPresent();
    }

    private static boolean irisPresent;

    /**
     * NeoForge 原生 ModList 检测：不依赖跨模块 {@code Class.forName}（NeoForge 各 mod 独立
     * module，默认 classloader 无法访问 Iris 模块类），是最可靠的 Iris 存在性判定。
     */
    private static boolean modListIrisLoaded() {
        try {
            ModList modList = ModList.get();
            return modList != null && modList.isLoaded("iris");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 检测 Iris 是否存在于 classpath。覆盖新旧版本的不同包名：
     * <ul>
     *   <li><code>net.irisshaders.iris.api.v1.IrisApi</code>（当前版本 API）</li>
     *   <li><code>net.irisshaders.iris.Iris</code>（主类）</li>
     *   <li><code>net.coderbot.iris.api.v1.IrisApi</code>（旧 coderbot 包名）</li>
     * </ul>
     */
    private static boolean isIrisPresent() {
        if (irisPresent) return true;
        if (modListIrisLoaded()) {
            irisPresent = true;
            return true;
        }
        for (String candidate : new String[] {
                "net.irisshaders.iris.api.v1.IrisApi",
                "net.irisshaders.iris.Iris",
                "net.coderbot.iris.api.v1.IrisApi"
        }) {
            try {
                Class.forName(candidate);
                irisPresent = true;
                return true;
            } catch (Throwable ignored) {
                // 尝试下一个候选类
            }
        }
        irisPresent = false;
        return false;
    }

    private static boolean optifinePresent;

    private static boolean isOptifinePresent() {
        if (optifinePresent) return true;
        try {
            Class.forName("optifine.Config");
            optifinePresent = true;
        } catch (Throwable ignored) {
            optifinePresent = false;
        }
        return optifinePresent;
    }

    /**
     * 综合判定：先看是否"明确启用"；再看是否"明确关闭"；
     * 若 shader loader 存在但状态无法证明关闭，则保守返回 {@code true}。
     */
    private static boolean detect() {
        if (modListIrisLoaded() || irisApiActive() || irisPropertiesEnabled() || optifineActive()) {
            return true;
        }
        if (shaderPackDefinitivelyOff()) {
            return false;
        }
        // 检测盲区：loader 存在但无法证明关闭 → 保守视为光影激活，
        // 避免半透明屏障墙在光影管线中被渲染成暗色阴影条带。
        return isIrisPresent() || isOptifinePresent();
    }

    /**
     * Iris runtime API: net.irisshaders.iris.api.v1.IrisApi.getInstance().isShaderPackInUse()
     */
    private static boolean irisApiActive() {
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v1.IrisApi");
            Object instance = apiClass.getMethod("getInstance").invoke(null);
            Object active = apiClass.getMethod("isShaderPackInUse").invoke(instance);
            return Boolean.TRUE.equals(active);
        } catch (Throwable ignored) {
            // Iris not present or API changed
            return false;
        }
    }

    /**
     * 解析 iris.properties：返回 {@code enableShaders} 与所选光影包名。
     * <p>兼容新旧两种键名：新版 Iris 写 <code>shaderPack=xxx.zip</code>，
     * 旧版写 <code>shaderPackName=xxx.zip</code>。</p>
     */
    private static IrisProperties readIrisProperties() {
        try {
            Path props = irisPropertiesPath();
            if (!Files.isRegularFile(props)) return null;
            List<String> lines = Files.readAllLines(props);
            boolean enableShaders = true;
            boolean foundEnable = false;
            String packName = null;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("enableShaders=")) {
                    enableShaders = Boolean.parseBoolean(trimmed.substring("enableShaders=".length()));
                    foundEnable = true;
                } else if (trimmed.startsWith("shaderPackName=")) {
                    packName = trimmed.substring("shaderPackName=".length());
                } else if (trimmed.startsWith("shaderPack=")) {
                    packName = trimmed.substring("shaderPack=".length());
                }
            }
            return new IrisProperties(enableShaders, foundEnable, packName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** iris.properties 解析结果。 */
    private record IrisProperties(boolean enableShaders, boolean foundEnable, String packName) {
        /** 是否选择了有效光影包（非空、非 (internal)、非 off）。 */
        boolean hasValidPack() {
            return packName != null
                && !packName.isEmpty()
                && !"(internal)".equals(packName)
                && !"off".equalsIgnoreCase(packName);
        }
    }

    /**
     * Iris config file: config/iris.properties — 明确启用判定。
     *
     * <p>Iris writes this file on every launch:</p>
     * <pre>
     * enableShaders=true
     * shaderPack=ComplementaryReimagined_r5.8.1.zip
     * </pre>
     * An empty pack name or {@code (internal)} means "no pack selected".
     */
    private static boolean irisPropertiesEnabled() {
        IrisProperties props = readIrisProperties();
        return props != null && props.enableShaders() && props.hasValidPack();
    }

    /**
     * 配置文件明确判定"关闭"：文件存在且 enableShaders=false 或 pack 为空/(internal)/off。
     * 仅当能完整解析时才返回 true，否则（文件缺失/解析失败）返回 false 交给保守兜底。
     */
    private static boolean irisPropertiesDefinitivelyOff() {
        IrisProperties props = readIrisProperties();
        if (props == null || !props.foundEnable()) return false;
        if (!props.enableShaders()) return true;
        return !props.hasValidPack();
    }

    private static Path irisPropertiesPath() {
        Path configDir = Minecraft.getInstance().gameDirectory.toPath().resolve("config");
        return configDir.resolve("iris.properties");
    }

    /**
     * 明确判定"未启用光影"：Iris API 可调用且返回 false、或配置文件明确关闭、或 OptiFine 明确关闭。
     * 无法证明时返回 false（交给保守兜底）。
     */
    private static boolean shaderPackDefinitivelyOff() {
        try {
            if (isIrisPresent()) {
                try {
                    Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v1.IrisApi");
                    Object instance = apiClass.getMethod("getInstance").invoke(null);
                    Object active = apiClass.getMethod("isShaderPackInUse").invoke(instance);
                    // API 可正常调用 → 状态明确
                    return !Boolean.TRUE.equals(active);
                } catch (Throwable ignored) {
                    // API 反射失败 → 尝试配置文件判定
                    return irisPropertiesDefinitivelyOff();
                }
            }
        } catch (Throwable ignored) {
            // fallthrough
        }
        if (isOptifinePresent()) {
            try {
                Class<?> configClass = Class.forName("optifine.Config");
                Object active = configClass.getMethod("isShaders").invoke(null);
                return !Boolean.TRUE.equals(active);
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    /**
     * OptiFine: optifine.Config.isShaders()
     */
    private static boolean optifineActive() {
        try {
            Class<?> configClass = Class.forName("optifine.Config");
            Object active = configClass.getMethod("isShaders").invoke(null);
            return Boolean.TRUE.equals(active);
        } catch (Throwable ignored) {
            // OptiFine not present
            return false;
        }
    }
}
