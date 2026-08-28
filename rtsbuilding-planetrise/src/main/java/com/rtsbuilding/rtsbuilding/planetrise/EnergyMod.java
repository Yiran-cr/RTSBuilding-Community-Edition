package com.rtsbuilding.rtsbuilding.planetrise;

import com.mojang.logging.LogUtils;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.RtsGridOwnerBinding;
import com.rtsbuilding.rtsbuilding.planetrise.block.entity.AbstractEnergyMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Entry point of the built-in addon mod {@code rtsbuilding_planetrise} —
 * the energy &amp; power system (thermal generators, wireless power towers and
 * the terminal energy).
 * <p>
 * This mod is a separate project that gets packaged together with the main
 * {@code rtsbuilding} mod in the same JAR. It registers its own
 * blocks/items/capabilities under the {@code rtsbuilding_planetrise}
 * namespace and shows up as a separate mod in the mods list.
 * <p>
 * The whole energy system can be switched off through the main mod's
 * {@code enableTechnologized} config option. The config file is read manually
 * here at construction time (NeoForge only loads configs after mod
 * construction), so when disabled nothing of this addon is registered or
 * loaded at all.
 */
@Mod(EnergyMod.MODID)
public final class EnergyMod {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Unique mod identifier of the built-in energy addon. */
    public static final String MODID = "rtsbuilding_planetrise";

    /** Config file written by the main mod. */
    private static final String CONFIG_FILE = "rts_building/rtsbuilding-common.toml";
    /** Config key controlling this addon. */
    private static final String CONFIG_KEY = "enableTechnologized";

    public EnergyMod(IEventBus modEventBus, ModContainer modContainer) {
        if (!isEnabledByConfig()) {
            LOGGER.info("rtsbuilding-planetrise disabled by config — nothing will be registered");
            return;
        }
        EnergyBlocks.register(modEventBus);
        EnergyItems.register(modEventBus);
        EnergyBlockEntities.register(modEventBus);
        EnergyCapabilities.register(modEventBus);
        EnergyCreativeTabs.register(modEventBus);
        TerminalEnergyImpl.register(modEventBus);
        modEventBus.addListener(EnergyMod::commonSetup);
        // 电网「新设备即时响应」：监听方块放置/破坏，唤醒覆盖该位置的输电塔立即重扫
        //（配合退避调度：稳态省成本、变化即刻响应）。
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(PowerTowerWakeup.class);
    }

    /**
     * Reads the {@code enableTechnologized} flag straight from the main mod's
     * config file. NeoForge loads configs only after all mods are constructed,
     * so {@link Config#isTechnologizedEnabled()} isn't reliable here yet; parsing
     * the small TOML boolean manually gives us the user's value at construction.
     */
    private static boolean isEnabledByConfig() {
        try {
            Path configPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
            if (Files.isRegularFile(configPath)) {
                for (String line : Files.readAllLines(configPath)) {
                    String s = line.trim();
                    if (s.startsWith(CONFIG_KEY + " =") || s.startsWith(CONFIG_KEY + "=")) {
                        String value = s.substring(s.indexOf('=') + 1).trim().toLowerCase();
                        return !value.startsWith("false");
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not read {} — defaulting to enabled", CONFIG_FILE, e);
        }
        return true;
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (!Config.isTechnologizedEnabled()) {
                LOGGER.info("rtsbuilding-planetrise disabled by config — energy system inactive");
                return;
            }
            // Make the main mod's terminal energy-powered again.
            TerminalEnergyImpl.installProvider();
            // 电网节点归属桥：主模组放置方块后，若该位置是能量节点则绑定到放置者电网组。
            RtsGridOwnerBinding.install((ServerLevel level, BlockPos pos, UUID playerUuid) -> {
                if (level.getBlockEntity(pos) instanceof AbstractEnergyMachineBlockEntity be) {
                    be.setGridOwner(playerUuid);
                }
            });
        });
    }
}
