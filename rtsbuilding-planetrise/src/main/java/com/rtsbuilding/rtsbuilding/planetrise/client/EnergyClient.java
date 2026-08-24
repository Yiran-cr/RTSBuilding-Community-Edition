package com.rtsbuilding.rtsbuilding.planetrise.client;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.planetrise.EnergyBlocks;
import com.rtsbuilding.rtsbuilding.planetrise.EnergyMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Client-side registration for the built-in energy addon.
 * <p>
 * This class is picked up by {@code @EventBusSubscriber} unconditionally, so it
 * must guard against the addon being disabled via config — in that case the
 * energy blocks were never registered and their holders are unbound.
 */
@EventBusSubscriber(modid = EnergyMod.MODID, value = Dist.CLIENT)
public final class EnergyClient {

    private EnergyClient() {
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        if (!Config.isTechnologizedEnabled()) {
            return;
        }
        // Custom break particles: consolidate multi-element collision shapes into
        // a single bounding-box particle burst (see BlockRenderProperties).
        event.registerBlock(BlockRenderProperties.INSTANCE, EnergyBlocks.THERMAL_GENERATOR.get());
        event.registerBlock(BlockRenderProperties.INSTANCE, EnergyBlocks.POWER_TOWER.get());
        event.registerBlock(BlockRenderProperties.INSTANCE, EnergyBlocks.ENERGY_CELL.get());
    }
}
