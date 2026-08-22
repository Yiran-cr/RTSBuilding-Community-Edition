package com.rtsbuilding.rtsbuilding.client.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.Difficulty;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 蓝图预览虚拟世界 —— 仅承载蓝图方块状态，供 {@link RtsSceneRenderer} 渲染 3D 结构缩略图。
 * <p>
 * 参考 LDLib2 的 {@code DummyWorld}（同为 NeoForge 1.21.1），但去掉粒子 / 实体 / 光照等
 * 无关能力：方块直接存于内部 {@link Map}，光照固定 15 级，碰撞 / 刻调度均为空实现。
 * 只服务于「把一组方块渲染成 GUI 场景」这一单一用途。
 */
public class RtsDummyLevel extends Level {

    /** 虚拟世界维度 id（仅用于占位，不与真实存档交互）。 */
    private static final ResourceKey<Level> LEVEL_ID = ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("rtsbuilding", "blueprint_preview"));

    private final RegistryAccess registryAccess;
    private final ChunkSource chunkSource;
    private final TransientEntitySectionManager<Entity> entityStorage;
    private final Holder<Biome> biome;
    private final TickRateManager tickRateManager;

    /** 蓝图方块状态表（相对坐标 → 方块状态），不包含空气 / 缺失方块。 */
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    /** 已填充方块的最小包围盒（含角点），用于相机取景。 */
    private AABB bounds = new AABB(0, 0, 0, 1, 1, 1);

    public RtsDummyLevel(RegistryAccess registryAccess) {
        super(new net.minecraft.client.multiplayer.ClientLevel.ClientLevelData(Difficulty.PEACEFUL, false, false),
                LEVEL_ID, registryAccess,
                registryAccess.registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD),
                () -> InactiveProfiler.INSTANCE, true, false, 0L, 1000000);
        this.registryAccess = registryAccess;
        this.chunkSource = new RtsDummyChunkSource(this);
        this.entityStorage = new TransientEntitySectionManager<>(Entity.class, new EmptyEntityCallbacks());
        this.biome = registryAccess.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        this.tickRateManager = new TickRateManager();
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.registryAccess;
    }

    // ── 方块装载（预览用） ─────────────────────────────────────────

    /**
     * 清空并装载一组蓝图方块状态。空气 / 空状态会被跳过。
     *
     * @param blockStates 相对坐标 → 方块状态映射
     */
    public void setBlocks(Map<BlockPos, BlockState> blockStates) {
        this.blocks.clear();
        if (blockStates != null) {
            for (Map.Entry<BlockPos, BlockState> e : blockStates.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue().isAir()) {
                    continue;
                }
                this.blocks.put(e.getKey().immutable(), e.getValue());
            }
        }
        this.bounds = computeBounds();
    }

    /** 当前装载的方块数。 */
    public int getFilledBlockCount() {
        return this.blocks.size();
    }

    /** 已填充方块的最小包围盒（含角点）。 */
    public AABB getFilledBounds() {
        return this.bounds;
    }

    /** 包围盒中心点（用于相机对准）。 */
    public Vec3 getBoundsCenter() {
        return this.bounds.getCenter();
    }

    private AABB computeBounds() {
        if (this.blocks.isEmpty()) {
            return new AABB(0, 0, 0, 1, 1, 1);
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : this.blocks.keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX() + 1);
            maxY = Math.max(maxY, pos.getY() + 1);
            maxZ = Math.max(maxZ, pos.getZ() + 1);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // ── 方块查询（渲染路径使用） ───────────────────────────────────

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState state = this.blocks.get(pos);
        return state != null ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public net.neoforged.neoforge.client.model.data.ModelData getModelData(BlockPos pos) {
        return net.neoforged.neoforge.client.model.data.ModelData.EMPTY;
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
        return true;
    }

    // ── 光照 / 生物群系（固定值，避免依赖区块光照引擎） ─────────────

    @Override
    public int getBrightness(LightLayer lightType, BlockPos blockPos) {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int ambientDarkness) {
        return 15;
    }

    @Override
    public boolean canSeeSky(BlockPos pos) {
        return true;
    }

    @Override
    public float getShade(net.minecraft.core.Direction direction, boolean shade) {
        if (!shade) {
            return 1.0F;
        }
        return switch (direction) {
            case DOWN, UP -> 0.9F;
            case NORTH, SOUTH -> 0.8F;
            case WEST, EAST -> 0.6F;
        };
    }

    @Override
    public Holder<Biome> getBiome(BlockPos pos) {
        return this.biome;
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return this.biome;
    }

    // ── 空实现区域（Level 抽象方法） ───────────────────────────────

    @Override
    public ChunkSource getChunkSource() {
        return this.chunkSource;
    }

    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
    }

    @Override
    public void playSound(@Nullable net.minecraft.world.entity.player.Player player, double x, double y, double z,
                          net.minecraft.sounds.SoundEvent sound, net.minecraft.sounds.SoundSource source,
                          float volume, float pitch) {
    }

    @Override
    public void playSound(@Nullable net.minecraft.world.entity.player.Player player, Entity entity,
                          net.minecraft.sounds.SoundEvent sound, net.minecraft.sounds.SoundSource source,
                          float volume, float pitch) {
    }

    @Override
    public void playSeededSound(@Nullable net.minecraft.world.entity.player.Player player, double x, double y, double z,
                                Holder<net.minecraft.sounds.SoundEvent> sound, net.minecraft.sounds.SoundSource source,
                                float volume, float pitch, long seed) {
    }

    @Override
    public void playSeededSound(@Nullable net.minecraft.world.entity.player.Player player, double x, double y, double z,
                                net.minecraft.sounds.SoundEvent sound, net.minecraft.sounds.SoundSource source,
                                float volume, float pitch, long seed) {
    }

    @Override
    public void playSeededSound(@Nullable net.minecraft.world.entity.player.Player player, Entity entity,
                                Holder<net.minecraft.sounds.SoundEvent> sound, net.minecraft.sounds.SoundSource source,
                                float volume, float pitch, long seed) {
    }

    @Override
    public String gatherChunkSourceStats() {
        return "";
    }

    @Override
    public void addParticle(net.minecraft.core.particles.ParticleOptions data, double x, double y, double z,
                            double xSpeed, double ySpeed, double zSpeed) {
    }

    @Override
    public void addParticle(net.minecraft.core.particles.ParticleOptions data, boolean forceAlwaysRender,
                            double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
    }

    @Override
    public void addAlwaysVisibleParticle(net.minecraft.core.particles.ParticleOptions data, double x, double y, double z,
                                         double xSpeed, double ySpeed, double zSpeed) {
    }

    @Override
    public void addAlwaysVisibleParticle(net.minecraft.core.particles.ParticleOptions data, boolean ignoreRange,
                                         double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
    }

    @Override
    public void levelEvent(@Nullable net.minecraft.world.entity.player.Player player, int type, BlockPos pos, int data) {
    }

    @Override
    public void gameEvent(Holder<GameEvent> gameEvent, Vec3 pos, GameEvent.Context context) {
    }

    @Override
    public java.util.List<? extends net.minecraft.world.entity.player.Player> players() {
        return Collections.emptyList();
    }

    @Override
    public PotionBrewing potionBrewing() {
        return null;
    }

    @Override
    public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() {
        return FeatureFlags.DEFAULT_FLAGS;
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public RecipeManager getRecipeManager() {
        return net.minecraft.client.Minecraft.getInstance().level.getRecipeManager();
    }

    @Override
    public void setDayTimePerTick(float dayTimePerTick) {
    }

    @Override
    public float getDayTimePerTick() {
        return 1.0f;
    }

    @Override
    public float getDayTimeFraction() {
        return 0.0f;
    }

    @Override
    public void setDayTimeFraction(float dayTimeFraction) {
    }

    @Override
    public MapId getFreeMapId() {
        return new MapId(1);
    }

    @Override
    public Scoreboard getScoreboard() {
        return new Scoreboard();
    }

    @Override
    public TickRateManager tickRateManager() {
        return this.tickRateManager;
    }

    @Override
    protected LevelEntityGetter<Entity> getEntities() {
        return this.entityStorage.getEntityGetter();
    }

    @Override
    public @Nullable Entity getEntity(int id) {
        return this.getEntities().get(id);
    }

    @Override
    public @Nullable MapItemSavedData getMapData(MapId mapId) {
        return null;
    }

    @Override
    public void setMapData(MapId mapId, MapItemSavedData data) {
    }

    @Override
    public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {
    }

    /** 最小区块源：不实际提供区块，仅满足 Level 接口。方块查询走 {@link #getBlockState}。 */
    private static final class RtsDummyChunkSource extends ChunkSource {
        private final RtsDummyLevel level;
        private final LevelLightEngine lightEngine;

        RtsDummyChunkSource(RtsDummyLevel level) {
            this.level = level;
            this.lightEngine = new LevelLightEngine(this, true, true);
        }

        @Override
        public void tick(BooleanSupplier hasTimeLeft, boolean tickChunks) {
        }

        @Override
        public @Nullable ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus requiredStatus, boolean load) {
            return null;
        }

        @Override
        public BlockGetter getLevel() {
            return this.level;
        }

        @Override
        public String gatherStats() {
            return "RtsDummy";
        }

        @Override
        public int getLoadedChunksCount() {
            return 0;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return this.lightEngine;
        }
    }

    /** 空实体回调（预览场景无实体）。 */
    private static final class EmptyEntityCallbacks implements LevelCallback<Entity> {
        @Override
        public void onCreated(Entity entity) {
        }

        @Override
        public void onDestroyed(Entity entity) {
        }

        @Override
        public void onTickingStart(Entity entity) {
        }

        @Override
        public void onTickingEnd(Entity entity) {
        }

        @Override
        public void onTrackingStart(Entity entity) {
        }

        @Override
        public void onTrackingEnd(Entity entity) {
        }

        @Override
        public void onSectionChange(Entity entity) {
        }
    }
}
