package com.rtsbuilding.rtsbuilding.planetrise;

import com.rtsbuilding.rtsbuilding.planetrise.block.BoundingBlock;
import com.rtsbuilding.rtsbuilding.planetrise.block.EnergyCellBlock;
import com.rtsbuilding.rtsbuilding.planetrise.block.PowerTowerBlock;
import com.rtsbuilding.rtsbuilding.planetrise.block.ThermalGeneratorBlock;
import com.rtsbuilding.rtsbuilding.planetrise.block.WindGeneratorBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Block registry for the built-in energy addon ({@code rtsbuilding_planetrise}).
 */
public final class EnergyBlocks {

    /** Unified block registry instance for the energy namespace */
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, EnergyMod.MODID);

    private static final Set<DeferredHolder<Block, ? extends Block>> CREATIVE_TAB_BLOCKS = new LinkedHashSet<>();

    /** Thermal generator — burns lava to produce FE for nearby power towers. */
    public static final DeferredHolder<Block, ThermalGeneratorBlock> THERMAL_GENERATOR = registerBlock(
            "thermal_generator",
            () -> new ThermalGeneratorBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(ThermalGeneratorBlock.LIT) ? 14 : 0)
                    .requiresCorrectToolForDrops()),
            true);

    /** Wireless power tower — buffers FE and redistributes it wirelessly within its coverage area. */
    public static final DeferredHolder<Block, PowerTowerBlock> POWER_TOWER = registerBlock(
            "power_tower",
            () -> new PowerTowerBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()),
            true);

    /** Energy cell — large FE buffer, chargeable/dischargeable by pipes or power towers. */
    public static final DeferredHolder<Block, EnergyCellBlock> ENERGY_CELL = registerBlock(
            "energy_cell",
            () -> new EnergyCellBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()),
            true);

    /** Wind generator — a multi-place tower (1 main block + 4 invisible bounding blocks above). */
    public static final DeferredHolder<Block, WindGeneratorBlock> WIND_GENERATOR = registerBlock(
            "wind_generator",
            () -> new WindGeneratorBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()),
            true);

    /** Invisible shared bounding block used by multi-place blocks (wind generator tower). No item, not in creative tab. */
    public static final DeferredHolder<Block, BoundingBlock> BOUNDING_BLOCK = registerBlock(
            "bounding_block",
            () -> new BoundingBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F, 4.8F)
                    .requiresCorrectToolForDrops()),
            false);

    public static <T extends Block> DeferredHolder<Block, T> registerBlock(String id,
            java.util.function.Supplier<? extends T> factory, boolean creative) {
        DeferredHolder<Block, T> holder = BLOCKS.register(id, factory);
        if (creative) {
            CREATIVE_TAB_BLOCKS.add(holder);
        }
        return holder;
    }

    /** Registers all blocks on the energy mod's event bus. */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    public static Set<DeferredHolder<Block, ? extends Block>> getCreativeTabBlocks() {
        return Collections.unmodifiableSet(CREATIVE_TAB_BLOCKS);
    }

    private EnergyBlocks() {
    }
}
