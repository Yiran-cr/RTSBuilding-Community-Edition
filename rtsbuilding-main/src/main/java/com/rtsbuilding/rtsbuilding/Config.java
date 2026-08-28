package com.rtsbuilding.rtsbuilding;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_BLUEPRINTS = BUILDER
            .comment("Enable the RTS blueprint library tab, local blueprint upload, and server-side blueprint placement.")
            .translation("rtsbuilding.configuration.enableBlueprints")
            .define("enableBlueprints", true);

    public static final ModConfigSpec.IntValue MAX_BLUEPRINT_BLOCKS = BUILDER
            .comment("Maximum non-air blocks allowed in one RTS blueprint import, capture, or placement job.")
            .translation("rtsbuilding.configuration.maxBlueprintBlocks")
            .defineInRange("maxBlueprintBlocks", 20000, 1, 200000);

    // ---- Rendering options ----

    public static final ModConfigSpec.BooleanValue USE_BLOCK_GHOST_PREVIEW = BUILDER
            .comment("Render translucent block ghost models for placement previews before the player confirms placement.")
            .translation("rtsbuilding.configuration.useBlockGhostPreview")
            .define("useBlockGhostPreview", true);

    public static final ModConfigSpec.BooleanValue USE_PLACE_BLOCK_GHOST_ANIMATION = BUILDER
            .comment("Render translucent grow-in block ghosts after server-confirmed block placement.")
            .translation("rtsbuilding.configuration.usePlaceBlockGhostAnimation")
            .define("usePlaceBlockGhostAnimation", true);

    public static final ModConfigSpec.BooleanValue USE_DESTROY_BLOCK_GHOST_ANIMATION = BUILDER
            .comment("Render translucent shrink-out block ghosts after server-confirmed block destruction.")
            .translation("rtsbuilding.configuration.useDestroyBlockGhostAnimation")
            .define("useDestroyBlockGhostAnimation", true);

    public static final ModConfigSpec.BooleanValue USE_WIREFRAME_PREVIEW = BUILDER
            .comment("Render wireframe outlines for placement previews before the player confirms placement.")
            .translation("rtsbuilding.configuration.useWireframePreview")
            .define("useWireframePreview", false);

    public static final ModConfigSpec.BooleanValue USE_PLACE_WIREFRAME_ANIMATION = BUILDER
            .comment("Render grow-in wireframe outlines after server-confirmed block placement.")
            .translation("rtsbuilding.configuration.usePlaceWireframeAnimation")
            .define("usePlaceWireframeAnimation", false);

    public static final ModConfigSpec.BooleanValue USE_DESTROY_WIREFRAME_ANIMATION = BUILDER
            .comment("Render shrink-out wireframe outlines after server-confirmed block destruction.")
            .translation("rtsbuilding.configuration.useDestroyWireframeAnimation")
            .define("useDestroyWireframeAnimation", false);

    public static final ModConfigSpec.BooleanValue USE_RANGE_DESTROY_SKELETON = BUILDER
            .comment("Render merged skeleton borders for non-chain range destroy previews. Chain mining always uses the skeleton style.")
            .translation("rtsbuilding.configuration.useRangeDestroySkeleton")
            .define("useRangeDestroySkeleton", true);

    // ---- Energy system options ----

    public static final ModConfigSpec.BooleanValue ENABLE_TECHNOLOGIZED = BUILDER
            .comment("Enable the built-in rtsbuilding-planetrise addon (energy & power system).",
                    "Set to false to disable the energy addon's functionality (no energy blocks, no energy transfer).")
            .translation("rtsbuilding.configuration.enableTechnologized")
            .define("enableTechnologized", true);

    public static final ModConfigSpec.LongValue POWER_TOWER_CAPACITY = BUILDER
            .comment("FE storage capacity of one power tower's internal buffer (rtsbuilding_planetrise).",
                    "The tower only relays this buffer for external pipes; grid power flow is rate-based and does not store here.")
            .translation("rtsbuilding.configuration.powerTowerCapacity")
            .defineInRange("powerTowerCapacity", 1_000_000L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.LongValue POWER_TOWER_LINK_RANGE = BUILDER
            .comment("Link range (blocks) of one power tower (rtsbuilding_planetrise).",
                    "How far a tower can build grid links to other power nodes (generators/other towers), forming the grid (electric network).")
            .translation("rtsbuilding.configuration.powerTowerLinkRange")
            .defineInRange("powerTowerLinkRange", 32L, 0L, 1024L);

    public static final ModConfigSpec.IntValue POWER_TOWER_POWER_RANGE = BUILDER
            .comment("Power range (blocks, horizontal) of one power tower (rtsbuilding_planetrise).",
                    "Radius of the circular area in which the tower broadcasts power to consumers. Set to 0 to disable supply entirely.")
            .translation("rtsbuilding.configuration.powerTowerPowerRange")
            .defineInRange("powerTowerPowerRange", 12, 0, 128);

    /** 已废弃：自 1.1.4 起供电范围改为<b>标准球体</b>（高度 = 水平半径），此竖直半径配置不再生效。
     * 保留仅为向后兼容旧配置文件。 */
    @Deprecated
    public static final ModConfigSpec.IntValue POWER_TOWER_VERTICAL_RADIUS = BUILDER
            .comment("DEPRECATED — supply area is now a standard sphere (height = horizontal radius); this vertical radius is ignored.")
            .translation("rtsbuilding.configuration.powerTowerVerticalRadius")
            .defineInRange("powerTowerVerticalRadius", 8, 0, 128);

    public static final ModConfigSpec.LongValue POWER_TOWER_THROUGHPUT = BUILDER
            .comment("Maximum FE/t one power tower can broadcast to consumers (rtsbuilding_planetrise).",
                    "A tower receives grid power proportionally to demand but capped by this throughput. Set to 0 to disable supply.")
            .translation("rtsbuilding.configuration.powerTowerThroughput")
            .defineInRange("powerTowerThroughput", (long) Integer.MAX_VALUE, 0L, Long.MAX_VALUE);

    public static final ModConfigSpec.LongValue GENERATOR_LINK_RANGE = BUILDER
            .comment("Link range (blocks) of generators (thermal / wind, rtsbuilding_planetrise).",
                    "How far a generator can build grid links to power towers, forming the grid. Generators never link to each other.")
            .translation("rtsbuilding.configuration.generatorLinkRange")
            .defineInRange("generatorLinkRange", 32L, 0L, 1024L);


    public static final ModConfigSpec.LongValue ENERGY_CELL_CAPACITY = BUILDER
            .comment("FE storage capacity of one energy cell block (rtsbuilding_planetrise).",
                    "The cell buffers energy and can be charged/discharged by pipes or power towers.")
            .translation("rtsbuilding.configuration.energyCellCapacity")
            .defineInRange("energyCellCapacity", 4_000_000L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.LongValue WIND_GENERATOR_CAPACITY = BUILDER
            .comment("FE storage capacity of one wind generator (rtsbuilding_planetrise).",
                    "Produced energy is buffered here until a power tower sucks it away.")
            .translation("rtsbuilding.configuration.windGeneratorCapacity")
            .defineInRange("windGeneratorCapacity", 5_000L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.LongValue WIND_GENERATOR_MIN_GENERATION = BUILDER
            .comment("FE per tick generated by a wind generator at the lowest buildable height (rtsbuilding_planetrise).")
            .translation("rtsbuilding.configuration.windGeneratorMinGeneration")
            .defineInRange("windGeneratorMinGeneration", 8L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.LongValue WIND_GENERATOR_MAX_GENERATION = BUILDER
            .comment("FE per tick generated by a wind generator at the world's max build height (rtsbuilding_planetrise).",
                    "Generation scales linearly with tower top height between min and max.")
            .translation("rtsbuilding.configuration.windGeneratorMaxGeneration")
            .defineInRange("windGeneratorMaxGeneration", 32L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean areBlueprintsEnabled() {
        return ENABLE_BLUEPRINTS.getAsBoolean();
    }

    public static int maxBlueprintBlocks() {
        return MAX_BLUEPRINT_BLOCKS.getAsInt();
    }

    public static void saveGeneralSettings(boolean blueprintsEnabled, int maxBlueprintBlocks,
            boolean placementBlockGhostPreview, boolean placeBlockGhostAnimation,
            boolean destroyBlockGhostAnimation, boolean placementWireframePreview,
            boolean placeWireframeAnimation, boolean destroyWireframeAnimation, boolean rangeDestroySkeleton) {
        ENABLE_BLUEPRINTS.set(blueprintsEnabled);
        MAX_BLUEPRINT_BLOCKS.set(Math.max(1, Math.min(200000, maxBlueprintBlocks)));
        USE_BLOCK_GHOST_PREVIEW.set(placementBlockGhostPreview);
        USE_PLACE_BLOCK_GHOST_ANIMATION.set(placeBlockGhostAnimation);
        USE_DESTROY_BLOCK_GHOST_ANIMATION.set(destroyBlockGhostAnimation);
        USE_WIREFRAME_PREVIEW.set(placementWireframePreview);
        USE_PLACE_WIREFRAME_ANIMATION.set(placeWireframeAnimation);
        USE_DESTROY_WIREFRAME_ANIMATION.set(destroyWireframeAnimation);
        USE_RANGE_DESTROY_SKELETON.set(rangeDestroySkeleton);
        SPEC.save();
    }

    public static boolean isPlacementBlockGhostPreviewEnabled() {
        return USE_BLOCK_GHOST_PREVIEW.getAsBoolean();
    }

    public static void setPlacementBlockGhostPreviewEnabled(boolean enabled) {
        USE_BLOCK_GHOST_PREVIEW.set(enabled);
        SPEC.save();
    }

    public static boolean isPlaceBlockGhostAnimationEnabled() {
        return USE_PLACE_BLOCK_GHOST_ANIMATION.getAsBoolean();
    }

    public static void setPlaceBlockGhostAnimationEnabled(boolean enabled) {
        USE_PLACE_BLOCK_GHOST_ANIMATION.set(enabled);
        SPEC.save();
    }

    public static boolean isDestroyBlockGhostAnimationEnabled() {
        return USE_DESTROY_BLOCK_GHOST_ANIMATION.getAsBoolean();
    }

    public static void setDestroyBlockGhostAnimationEnabled(boolean enabled) {
        USE_DESTROY_BLOCK_GHOST_ANIMATION.set(enabled);
        SPEC.save();
    }

    public static boolean isPlacementWireframePreviewEnabled() {
        return USE_WIREFRAME_PREVIEW.getAsBoolean();
    }

    public static void setPlacementWireframePreviewEnabled(boolean enabled) {
        USE_WIREFRAME_PREVIEW.set(enabled);
        SPEC.save();
    }

    public static boolean isPlaceWireframeAnimationEnabled() {
        return USE_PLACE_WIREFRAME_ANIMATION.getAsBoolean();
    }

    public static void setPlaceWireframeAnimationEnabled(boolean enabled) {
        USE_PLACE_WIREFRAME_ANIMATION.set(enabled);
        SPEC.save();
    }

    public static boolean isDestroyWireframeAnimationEnabled() {
        return USE_DESTROY_WIREFRAME_ANIMATION.getAsBoolean();
    }

    public static void setDestroyWireframeAnimationEnabled(boolean enabled) {
        USE_DESTROY_WIREFRAME_ANIMATION.set(enabled);
        SPEC.save();
    }

    public static boolean isRangeDestroySkeletonEnabled() {
        return USE_RANGE_DESTROY_SKELETON.getAsBoolean();
    }

    public static void setRangeDestroySkeletonEnabled(boolean enabled) {
        USE_RANGE_DESTROY_SKELETON.set(enabled);
        SPEC.save();
    }

    /** Whether the built-in rtsbuilding-planetrise (energy & power) addon is enabled. */
    public static boolean isTechnologizedEnabled() {
        return ENABLE_TECHNOLOGIZED.getAsBoolean();
    }

    /** FE storage capacity of one power tower's internal buffer. */
    public static long powerTowerCapacity() {
        return POWER_TOWER_CAPACITY.getAsLong();
    }

    /** Link range (blocks) of one power tower — how far it can form grid links to other nodes. */
    public static long powerTowerLinkRange() {
        return POWER_TOWER_LINK_RANGE.getAsLong();
    }

    /** Power range (blocks, horizontal) of one power tower's broadcast area. */
    public static int powerTowerPowerRange() {
        return POWER_TOWER_POWER_RANGE.getAsInt();
    }

    /** @deprecated 自 1.1.4 起供电范围为球体（竖直 = 水平半径），此竖直半径取值不再生效。 */
    @Deprecated
    public static int powerTowerVerticalRadius() {
        return POWER_TOWER_VERTICAL_RADIUS.getAsInt();
    }

    /** Maximum FE/t one power tower can broadcast to consumers. */
    public static long powerTowerThroughput() {
        return POWER_TOWER_THROUGHPUT.getAsLong();
    }

    /** Link range (blocks) of generators (thermal / wind). */
    public static long generatorLinkRange() {
        return GENERATOR_LINK_RANGE.getAsLong();
    }

    /** FE storage capacity of one energy cell block. */
    public static long energyCellCapacity() {
        return ENERGY_CELL_CAPACITY.getAsLong();
    }

    /** FE storage capacity of one wind generator. */
    public static long windGeneratorCapacity() {
        return WIND_GENERATOR_CAPACITY.getAsLong();
    }

    /** Minimum FE per tick generated by a wind generator at the lowest buildable height. */
    public static long windGeneratorMinGeneration() {
        return WIND_GENERATOR_MIN_GENERATION.getAsLong();
    }

    /** Maximum FE per tick generated by a wind generator at the world's max build height. */
    public static long windGeneratorMaxGeneration() {
        return WIND_GENERATOR_MAX_GENERATION.getAsLong();
    }

}

