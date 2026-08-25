package com.rtsbuilding.rtsbuilding.planetrise;

import com.rtsbuilding.rtsbuilding.planetrise.block.entity.BoundingBlockEntity;
import com.rtsbuilding.rtsbuilding.planetrise.block.entity.ContainerEnergyStorage;
import com.rtsbuilding.rtsbuilding.planetrise.block.entity.EnergyCellBlockEntity;
import com.rtsbuilding.rtsbuilding.planetrise.block.entity.IBoundingBlock;
import com.rtsbuilding.rtsbuilding.planetrise.block.entity.PowerTowerBlockEntity;
import com.rtsbuilding.rtsbuilding.planetrise.block.entity.ThermalGeneratorBlockEntity;
import com.rtsbuilding.rtsbuilding.planetrise.block.entity.WindGeneratorBlockEntity;
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
public final class EnergyCapabilities {

    private EnergyCapabilities() {
    }

    /** Registers all block capabilities on the energy mod's event bus. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(EnergyCapabilities::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // When the addon is disabled by config, expose no capabilities at all.
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, blockEntity, side) -> !com.rtsbuilding.rtsbuilding.Config.isTechnologizedEnabled() ? null
                        : blockEntity instanceof ThermalGeneratorBlockEntity generator
                                ? new ContainerEnergyStorage(generator.getBuffer(), false, true)
                                : null,
                EnergyBlocks.THERMAL_GENERATOR.get());

        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> !com.rtsbuilding.rtsbuilding.Config.isTechnologizedEnabled() ? null
                        : blockEntity instanceof ThermalGeneratorBlockEntity generator
                                ? generator.getTank()
                                : null,
                EnergyBlocks.THERMAL_GENERATOR.get());

        // 输电塔：缓冲可被外部充能/抽能，也可被范围内其他塔/机器搬运。
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, blockEntity, side) -> !com.rtsbuilding.rtsbuilding.Config.isTechnologizedEnabled() ? null
                        : blockEntity instanceof PowerTowerBlockEntity tower
                                ? new ContainerEnergyStorage(tower.getBuffer(), true, true)
                                : null,
                EnergyBlocks.POWER_TOWER.get());

        // 储能单元：双向缓冲，可被管道充能/抽能，也可被输电塔当源/目标搬运。
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, blockEntity, side) -> !com.rtsbuilding.rtsbuilding.Config.isTechnologizedEnabled() ? null
                        : blockEntity instanceof EnergyCellBlockEntity cell
                                ? new ContainerEnergyStorage(cell.getBuffer(), true, true)
                                : null,
                EnergyBlocks.ENERGY_CELL.get());

        // 风力发电机：提取侧缓冲（产出的 FE 只能被抽走，供热电塔当源吸取）。
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, blockEntity, side) -> !com.rtsbuilding.rtsbuilding.Config.isTechnologizedEnabled() ? null
                        : blockEntity instanceof WindGeneratorBlockEntity wind
                                ? new ContainerEnergyStorage(wind.getBuffer(), false, true)
                                : null,
                EnergyBlocks.WIND_GENERATOR.get());

        // 占位方块：能量能力代理到主方块（参考 Mekanism proxyCapability），
        // 使风力发电机塔身格子也可被取能量（管道/输电塔从塔身吸取）。
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, blockEntity, side) -> {
                    if (!com.rtsbuilding.rtsbuilding.Config.isTechnologizedEnabled()) {
                        return null;
                    }
                    if (blockEntity instanceof BoundingBlockEntity bounding) {
                        IBoundingBlock main = bounding.getMain();
                        if (main != null) {
                            return main.getBoundingEnergyStorage(side, pos.subtract(bounding.getMainPos()));
                        }
                    }
                    return null;
                },
                EnergyBlocks.BOUNDING_BLOCK.get());
    }
}

