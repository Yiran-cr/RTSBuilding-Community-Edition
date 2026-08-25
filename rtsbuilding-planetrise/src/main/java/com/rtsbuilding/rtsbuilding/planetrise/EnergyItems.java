package com.rtsbuilding.rtsbuilding.planetrise;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Item registry for the built-in energy addon ({@code rtsbuilding_planetrise}).
 */
public final class EnergyItems {

    /** Unified item registry instance for the energy namespace */
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, EnergyMod.MODID);

    private static final Set<DeferredHolder<Item, ? extends Item>> CREATIVE_TAB_ITEMS = new LinkedHashSet<>();

    /** Thermal generator block item */
    public static final DeferredHolder<Item, BlockItem> THERMAL_GENERATOR = blockItem(
            "thermal_generator", EnergyBlocks.THERMAL_GENERATOR, true);

    /** Wireless power tower block item */
    public static final DeferredHolder<Item, BlockItem> POWER_TOWER = blockItem(
            "power_tower", EnergyBlocks.POWER_TOWER, true);

    /** Energy cell block item */
    public static final DeferredHolder<Item, BlockItem> ENERGY_CELL = blockItem(
            "energy_cell", EnergyBlocks.ENERGY_CELL, true);

    /** Wind generator block item (the tower occupies 1 + 4 bounding blocks above) */
    public static final DeferredHolder<Item, BlockItem> WIND_GENERATOR = blockItem(
            "wind_generator", EnergyBlocks.WIND_GENERATOR, true);

    public static DeferredHolder<Item, BlockItem> blockItem(String id,
            DeferredHolder<Block, ? extends Block> block, boolean creative) {
        DeferredHolder<Item, BlockItem> holder = ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        if (creative) {
            CREATIVE_TAB_ITEMS.add(holder);
        }
        return holder;
    }

    /** Registers all items on the energy mod's event bus. */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    public static Set<DeferredHolder<Item, ? extends Item>> getCreativeTabItems() {
        return Collections.unmodifiableSet(CREATIVE_TAB_ITEMS);
    }

    private EnergyItems() {
    }
}
