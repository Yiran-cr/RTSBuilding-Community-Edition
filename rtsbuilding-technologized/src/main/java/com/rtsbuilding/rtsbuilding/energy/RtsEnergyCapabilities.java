package com.rtsbuilding.rtsbuilding.energy;

import com.rtsbuilding.rtsbuilding.energy.block.entity.ContainerEnergyStorage;
import com.rtsbuilding.rtsbuilding.energy.block.entity.RtsEnergyCellBlockEntity;
import com.rtsbuilding.rtsbuilding.energy.block.entity.RtsPowerTowerBlockEntity;
import com.rtsbuilding.rtsbuilding.energy.block.entity.RtsThermalGeneratorBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Capability registration for the built-in energy addon.
 * <p>
 * Thermal generators expose an extract-only {@code IEnergyStorage} plus a lava
 * {@code IFluidHandler} so power towers / external pipes can draw FE and supply
 * fuel. Power towers and energy cells expose a fully bidirectional
 * {@code IEnergyStorage} so they can be charged externally and drained too.
 */
public final class RtsEnergyCapabilities {

    private RtsEnergyCapabilities() {
    }

    /** Registers all block capabilities on the energy mod's event bus. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RtsEnergyCapabilities::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // When the addon is disabled by config, expose no capabilities at all.
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, blockEntity, side) -> !com.rtsbuilding.rtsbuilding.Config.isTechnologizedEnabled() ? null
                        : blockEntity instanceof RtsThermalGeneratorBlockEntity generator
                                ? new ContainerEnergyStorage(generator.getBuffer(), false, true)
                                : null,
                RtsEnergyBlocks.THERMAL_GENERATOR.get());

        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> !com.rtsbuilding.rtsbuilding.Config.isTechnologizedEnabled() ? null
                        : blockEntity instanceof RtsThermalGeneratorBlockEntity generator
                                ? generator.getTank()
                                : null,
                RtsEnergyBlocks.THERMAL_GENERATOR.get());

        // 输电塔：缓冲可被外部充能/抽能，也可被范围内其他塔/机器搬运。
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, blockEntity, side) -> !com.rtsbuilding.rtsbuilding.Config.isTechnologizedEnabled() ? null
                        : blockEntity instanceof RtsPowerTowerBlockEntity tower
                                ? new ContainerEnergyStorage(tower.getBuffer(), true, true)
                                : null,
                RtsEnergyBlocks.POWER_TOWER.get());

        // 储能单元：双向缓冲，可被管道充能/抽能，也可被输电塔当源/目标搬运。
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, blockEntity, side) -> !com.rtsbuilding.rtsbuilding.Config.isTechnologizedEnabled() ? null
                        : blockEntity instanceof RtsEnergyCellBlockEntity cell
                                ? new ContainerEnergyStorage(cell.getBuffer(), true, true)
                                : null,
                RtsEnergyBlocks.ENERGY_CELL.get());
    }
}

