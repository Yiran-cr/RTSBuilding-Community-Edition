package com.rtsbuilding.rtsbuilding.planetrise;

import com.rtsbuilding.rtsbuilding.planetrise.block.entity.EnergyCellBlockEntity;
import com.rtsbuilding.rtsbuilding.planetrise.block.entity.PowerTowerBlockEntity;
import com.rtsbuilding.rtsbuilding.planetrise.block.entity.ThermalGeneratorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block entity registry for the built-in energy addon ({@code rtsbuilding_planetrise}).
 */
public final class EnergyBlockEntities {

    /** Unified block entity registry instance for the energy namespace */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EnergyMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThermalGeneratorBlockEntity>> THERMAL_GENERATOR =
            BLOCK_ENTITY_TYPES.register("thermal_generator", () ->
                    BlockEntityType.Builder.of(ThermalGeneratorBlockEntity::new, EnergyBlocks.THERMAL_GENERATOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerTowerBlockEntity>> POWER_TOWER =
            BLOCK_ENTITY_TYPES.register("power_tower", () ->
                    BlockEntityType.Builder.of(PowerTowerBlockEntity::new, EnergyBlocks.POWER_TOWER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyCellBlockEntity>> ENERGY_CELL =
            BLOCK_ENTITY_TYPES.register("energy_cell", () ->
                    BlockEntityType.Builder.of(EnergyCellBlockEntity::new, EnergyBlocks.ENERGY_CELL.get()).build(null));

    /** Registers all block entity types on the energy mod's event bus. */
    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }

    private EnergyBlockEntities() {
    }
}
