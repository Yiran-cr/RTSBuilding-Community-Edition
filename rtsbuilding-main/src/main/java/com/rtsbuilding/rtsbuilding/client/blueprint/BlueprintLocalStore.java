package com.rtsbuilding.rtsbuilding.client.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.io.BlueprintReaders;
import com.rtsbuilding.rtsbuilding.common.blueprint.io.BlueprintWriters;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintParseException;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 本地蓝图存储 —— 管理 RTS 蓝图在客户端本地文件系统中的保存、导入与目录扫描。
 * <p>
 * 蓝图以原版结构 NBT 文件（.nbt）形式存放在
 * {@code config/rts_building/blueprints} 目录下。保存时自动清洗非法字符、
 * 同名追加序号避免覆盖；导入时经 {@link BlueprintReaders} 将其他模组蓝图
 * （Sponge 结构 / Litematica / Building Gadgets 模板 / 原版结构）统一转换为
 * 本模组的原版结构 NBT 形式。
 */
public final class BlueprintLocalStore {

    /** 蓝图本地根目录：config/rts_building/blueprints。 */
    private static final Path BLUEPRINT_DIR = FMLPaths.CONFIGDIR.get()
            .resolve("rts_building")
            .resolve("blueprints");

    private BlueprintLocalStore() {
    }

    /** 获取蓝图本地目录（不存在时自动创建）。 */
    public static Path blueprintDir() {
        try {
            Files.createDirectories(BLUEPRINT_DIR);
        } catch (IOException ignored) {
        }
        return BLUEPRINT_DIR;
    }

    /** 列出本地蓝图目录下的所有 .nbt 蓝图文件，按名称排序。 */
    public static List<Path> listBlueprints() {
        try (Stream<Path> stream = Files.list(blueprintDir())) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".nbt"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** 删除本地蓝图文件。 */
    public static void delete(Path file) throws IOException {
        Files.deleteIfExists(file);
    }

    /**
     * 重命名本地蓝图文件（仅改文件基名，保留 .nbt 扩展名）。
     * <p>自动清洗非法字符；若新名称与现有文件重名，自动追加 _1、_2 … 序号避免覆盖。</p>
     *
     * @param file    原蓝图文件路径
     * @param newName 新蓝图名称（不含扩展名）
     * @return 重命名后的文件路径
     * @throws IOException 文件不存在或移动失败
     */
    public static Path rename(Path file, String newName) throws IOException {
        if (file == null || !Files.exists(file)) {
            throw new IOException("蓝图文件不存在");
        }
        String clean = sanitizeFileName(newName == null ? "" : newName);
        if (clean.isBlank()) {
            throw new IOException("蓝图名称不能为空");
        }
        Path target = uniquePath(file.getParent().resolve(clean + ".nbt"));
        if (target.equals(file)) {
            return file;
        }
        Files.move(file, target);
        return target;
    }

    /**
     * 捕获框选区域的世界方块并保存为本地蓝图文件。
     *
     * @param level 客户端世界
     * @param min   区域最小角点（含）
     * @param max   区域最大角点（含）
     * @param name  蓝图名称（自动清洗非法字符）
     * @return 保存后的蓝图文件路径
     * @throws IOException              写入失败
     * @throws IllegalArgumentException 捕获方块数超过上限
     */
    public static Path save(Level level, BlockPos min, BlockPos max, String name) throws IOException {
        if (level == null || min == null || max == null) {
            throw new IOException("框选区域或世界不可用");
        }
        String clean = sanitizeFileName(name == null ? "" : name);
        if (clean.isBlank()) {
            clean = "blueprint_" + System.currentTimeMillis();
        }
        RtsBlueprint blueprint = BlueprintWriters.capture(level, min, max, clean, clean + ".nbt", false);
        Path file = uniquePath(blueprintDir().resolve(clean + ".nbt"));
        BlueprintWriters.writeVanillaStructure(blueprint, file);
        return file;
    }

    /**
     * 导入外部蓝图文件（Sponge 结构 / Litematica / Building Gadgets 模板 / 原版结构），
     * 自动转换为本模组的原版结构 NBT 形式并存入本地蓝图目录。
     *
     * @param source         外部蓝图文件路径
     * @param registryAccess 注册表访问（用于解析方块状态）
     * @return 转换并保存后的本地蓝图文件路径
     * @throws IOException              读取/写入失败
     * @throws BlueprintParseException  蓝图格式解析失败
     * @throws IllegalArgumentException 导入方块数超过上限
     */
    public static Path importFile(Path source, RegistryAccess registryAccess)
            throws IOException, BlueprintParseException {
        return importFile(source, registryAccess, null);
    }

    /**
     * 导入外部蓝图文件，可回调导入阶段供 UI 展示进度。
     *
     * @param source         外部蓝图文件路径
     * @param registryAccess 注册表访问（用于解析方块状态）
     * @param stage          阶段回调（读取/解析/写出），可为 null
     * @return 转换并保存后的本地蓝图文件路径
     * @throws IOException              读取/写入失败
     * @throws BlueprintParseException  蓝图格式解析失败
     * @throws IllegalArgumentException 导入方块数超过上限
     */
    public static Path importFile(Path source, RegistryAccess registryAccess,
                                  java.util.function.Consumer<ImportStage> stage)
            throws IOException, BlueprintParseException {
        if (source == null) {
            throw new IOException("蓝图文件路径为空");
        }
        if (stage != null) stage.accept(ImportStage.READING);
        byte[] data = Files.readAllBytes(source);
        if (stage != null) stage.accept(ImportStage.PARSING);
        RtsBlueprint blueprint = BlueprintReaders.parse(data, source.getFileName().toString(), registryAccess);
        if (blueprint.blockCount() > BlueprintWriters.maxCaptureBlocks()) {
            throw new IllegalArgumentException(
                    "蓝图包含超过 " + BlueprintWriters.maxCaptureBlocks() + " 个方块");
        }
        String baseName = blueprint.name() == null || blueprint.name().isBlank()
                ? stripExtension(source.getFileName().toString())
                : blueprint.name();
        String clean = sanitizeFileName(baseName);
        if (clean.isBlank()) {
            clean = "blueprint_" + System.currentTimeMillis();
        }
        Path out = uniquePath(blueprintDir().resolve(clean + ".nbt"));
        if (stage != null) stage.accept(ImportStage.WRITING);
        BlueprintWriters.writeVanillaStructure(blueprint, out);
        return out;
    }

    /** 导入阶段（供进度条等 UI 反馈）。 */
    public enum ImportStage {
        /** 读取源文件字节。 */
        READING,
        /** 解析为统一的 RtsBlueprint 对象。 */
        PARSING,
        /** 写出为本模组蓝图文件。 */
        WRITING
    }

    /** 去掉文件名扩展名。 */
    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /** 清洗文件名中的非法字符（Windows 保留字符与空白），空名返回空串。 */
    private static String sanitizeFileName(String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_").trim();
        return cleaned;
    }

    /** 若目标文件已存在，自动追加 _1、_2 … 序号避免覆盖已有蓝图。 */
    private static Path uniquePath(Path target) {
        if (!Files.exists(target)) {
            return target;
        }
        String fileName = target.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        int i = 1;
        Path candidate;
        do {
            candidate = target.getParent().resolve(base + "_" + i + ext);
            i++;
        } while (Files.exists(candidate));
        return candidate;
    }
}
