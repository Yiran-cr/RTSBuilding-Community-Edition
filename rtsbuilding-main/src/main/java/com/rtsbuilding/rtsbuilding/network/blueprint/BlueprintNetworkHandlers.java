package com.rtsbuilding.rtsbuilding.network.blueprint;

import com.rtsbuilding.rtsbuilding.platform.Platform;
import net.minecraft.server.level.ServerPlayer;

public final class BlueprintNetworkHandlers {
    private BlueprintNetworkHandlers() {}

    public static void send(ServerPlayer player, byte status, String messageKey, String detail) {
        Platform.sendPacket(player, new S2CBlueprintStatusPayload(status, messageKey, detail));
    }
}
