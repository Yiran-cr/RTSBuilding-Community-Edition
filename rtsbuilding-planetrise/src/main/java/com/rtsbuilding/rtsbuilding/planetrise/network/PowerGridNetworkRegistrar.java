package com.rtsbuilding.rtsbuilding.planetrise.network;

import com.rtsbuilding.rtsbuilding.planetrise.EnergyMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 电网多人系统网络包注册（仅当能量插件启用时执行，EnergyMod 负责调用）。
 */
@EventBusSubscriber(modid = EnergyMod.MODID)
public final class PowerGridNetworkRegistrar {

    private PowerGridNetworkRegistrar() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        PowerGridPackets.register(registrar);
    }
}
