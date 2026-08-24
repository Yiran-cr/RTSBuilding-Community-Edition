package com.rtsbuilding.rtsbuilding.planetrise;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Creative tab for the built-in energy addon.
 */
public final class EnergyCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EnergyMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ENERGY_TAB = CREATIVE_TABS.register(
            "energy",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.rtsbuilding_planetrise"))
                    .icon(() -> new ItemStack(EnergyItems.THERMAL_GENERATOR.get()))
                    .displayItems((parameters, output) -> {
                        for (var holder : EnergyItems.getCreativeTabItems()) {
                            output.accept(holder.get());
                        }
                        for (var holder : EnergyBlocks.getCreativeTabBlocks()) {
                            output.accept(holder.get());
                        }
                    })
                    .build());

    /** Registers all creative tabs on the energy mod's event bus. */
    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }

    private EnergyCreativeTabs() {
    }
}
