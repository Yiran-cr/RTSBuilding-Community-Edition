package com.rtsbuilding.rtsbuilding.api.compat;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApiStatus.NonExtendable
public final class RtsCompatRegistry {

    private static final List<RtsStorageNetworkProvider> storageProviders = new ArrayList<>();
    private static final List<RtsFluidNetworkProvider> fluidProviders = new ArrayList<>();
    private static final List<RtsBackpackProvider> backpackProviders = new ArrayList<>();
    private static final List<RtsIconResolver> iconResolvers = new ArrayList<>();
    private static final List<RtsIntegration> integrations = new ArrayList<>();

    private RtsCompatRegistry() {}

    /** 注册宿主 mod 集成（统一生命周期抽象，见 {@link RtsIntegration}）。 */
    public static void registerIntegration(RtsIntegration integration) {
        if (integration != null) {
            integrations.add(integration);
        }
    }

    public static List<RtsIntegration> getIntegrations() {
        return Collections.unmodifiableList(integrations);
    }

    public static void register(RtsStorageNetworkProvider provider) {
        storageProviders.add(provider);
    }

    public static void register(RtsFluidNetworkProvider provider) {
        fluidProviders.add(provider);
    }

    public static void register(RtsBackpackProvider provider) {
        backpackProviders.add(provider);
    }

    public static void register(RtsIconResolver resolver) {
        iconResolvers.add(resolver);
    }

    public static List<RtsStorageNetworkProvider> getStorageProviders() {
        return Collections.unmodifiableList(storageProviders);
    }

    public static List<RtsFluidNetworkProvider> getFluidProviders() {
        return Collections.unmodifiableList(fluidProviders);
    }

    public static List<RtsBackpackProvider> getBackpackProviders() {
        return Collections.unmodifiableList(backpackProviders);
    }

    public static List<RtsIconResolver> getIconResolvers() {
        return Collections.unmodifiableList(iconResolvers);
    }
}
