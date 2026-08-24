package com.rtsbuilding.rtsbuilding.planetrise.dimension;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public abstract class PlanetDimension {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlanetDimension.class);
    
    // 1. 维度实例的唯一标识符 (RegistryKey<Level>)
    private final ResourceKey<Level> dimensionKey;
    // 2. 维度类型标识符 (RegistryKey<DimensionType>)，用于引用类型
    private final ResourceKey<DimensionType> dimensionTypeKey;
    // 3. 配置对象，由子类在初始化时加载
    private PlanetDimensionType planetDimensionType;

    public PlanetDimension(ResourceLocation dimensionId, ResourceLocation dimensionTypeId) {
        // 创建 RegistryKey<Level> [reference:4]
        this.dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
        // 创建 RegistryKey<DimensionType> [reference:6]
        this.dimensionTypeKey = ResourceKey.create(Registries.DIMENSION_TYPE, dimensionTypeId);
    }

    // --- 配置加载 ---

    /**
     * 从 data/modid/planet_config/ 加载 JSON 配置文件
     * @param resourceManager 游戏资源管理器
     * @param planetName 配置文件名 (不含 .json)
     */
    protected void loadConfig(ResourceManager resourceManager, String planetName) {
        // 构建配置文件的 ResourceLocation
        // 假设你的 MOD_ID 是 "yourmodid"
        ResourceLocation configId = ResourceLocation.fromNamespaceAndPath("rtsbuilding_planetrise", 
                "dimension/" + planetName);
        
        try {
            // 1. 获取资源文件
            var resource = resourceManager.getResource(configId)
                    .orElseThrow(() -> new RuntimeException("Config not found: " + configId));
            
            // 2. 使用 JsonOps 和 Codec 解析 JSON [reference:7][reference:9]
            try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                // 将 JSON 解析为 JsonElement (使用 Gson)
                var jsonElement = com.google.gson.JsonParser.parseReader(reader);
                
                // 使用 Codec 将 JsonElement 解码为 PlanetConfig 对象 [reference:10]
                var result = PlanetDimensionType.CODEC.parse(JsonOps.INSTANCE, jsonElement);
                
                // 获取解析结果，失败则抛出异常 [reference:11]
                this.planetDimensionType = result.getOrThrow();
                
                LOGGER.info("Successfully loaded planet config: {}", configId);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load planet config: " + configId, e);
        }
    }

    // --- 辅助方法 ---

    /**
     * 返回该维度的 RegistryKey<Level>
     */
    public ResourceKey<Level> getDimensionKey() {
        return dimensionKey;
    }

    /**
     * 返回该维度类型的 RegistryKey<DimensionType>
     */
    public ResourceKey<DimensionType> getDimensionTypeKey() {
        return dimensionTypeKey;
    }

    /**
     * 获取已加载的配置对象
     */
    public Optional<PlanetDimensionType> getConfig() {
        return Optional.ofNullable(planetDimensionType);
    }
}