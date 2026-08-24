package com.rtsbuilding.rtsbuilding.planetrise.dimension;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class PlanetConfig {
    public static PlanetDimensionType loadDimensionType(ResourceManager resourceManager, ResourceLocation id) {
        // 1. 从资源管理器中获取 JSON 文件
        Resource resource = resourceManager.getResource(id).orElseThrow();
        try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            // 2. 将文件内容解析为 JsonElement
            JsonElement jsonElement = JsonParser.parseReader(reader);
            // 3. 使用 Codec 将 JsonElement 解码为 Java 对象
            return PlanetDimensionType.CODEC.parse(JsonOps.INSTANCE, jsonElement).getOrThrow();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load dimension type: " + id, e);
        }
    }
}