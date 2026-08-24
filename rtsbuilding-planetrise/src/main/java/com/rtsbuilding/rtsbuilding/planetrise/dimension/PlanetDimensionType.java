package com.rtsbuilding.rtsbuilding.planetrise.dimension;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.Optional;

public record PlanetDimensionType(
        Optional<Long> fixedTime,
        boolean hasSkyLight,
        boolean hasCeiling,
        boolean ultraWarm,
        boolean natural,
        double coordinateScale,
        boolean bedWorks,
        boolean respawnAnchorWorks,
        int minY,
        int height,
        int logicalHeight,
        TagKey<Block> infiniburn,
        ResourceLocation effectsLocation,
        float ambientLight,
        DimensionType.MonsterSettings monsterSettings
) {
    public static final Codec<PlanetDimensionType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("fixed_time").forGetter(PlanetDimensionType::fixedTime),//json不填此项时间正常流逝，填数锁定daytime 没有就返回null
            Codec.BOOL.fieldOf("has_skylight").forGetter(PlanetDimensionType::hasSkyLight),
            Codec.BOOL.fieldOf("has_ceiling").forGetter(PlanetDimensionType::hasCeiling),
            Codec.BOOL.fieldOf("ultrawarm").forGetter(PlanetDimensionType::ultraWarm),
            Codec.BOOL.fieldOf("natural").forGetter(PlanetDimensionType::natural),
            Codec.DOUBLE.fieldOf("coordinate_scale").forGetter(PlanetDimensionType::coordinateScale),
            Codec.BOOL.fieldOf("bed_works").forGetter(PlanetDimensionType::bedWorks),
            Codec.BOOL.fieldOf("respawn_anchor_works").forGetter(PlanetDimensionType::respawnAnchorWorks),
            Codec.INT.fieldOf("min_y").forGetter(PlanetDimensionType::minY),
            Codec.INT.fieldOf("height").forGetter(PlanetDimensionType::height),
            Codec.INT.fieldOf("logical_height").forGetter(PlanetDimensionType::logicalHeight),
            TagKey.codec(Registries.BLOCK).fieldOf("infiniburn").forGetter(PlanetDimensionType::infiniburn),
            ResourceLocation.CODEC.fieldOf("effects").forGetter(PlanetDimensionType::effectsLocation),
            Codec.FLOAT.fieldOf("ambient_light").forGetter(PlanetDimensionType::ambientLight),
            DimensionType.MonsterSettings.CODEC.fieldOf("monster_spawn_light_level").forGetter(PlanetDimensionType::monsterSettings)
    ).apply(instance, PlanetDimensionType::new));
}