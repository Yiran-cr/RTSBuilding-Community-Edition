package com.rtsbuilding.rtsbuilding.server;

import com.rtsbuilding.rtsbuilding.api.compat.RtsCompatRegistry;
import com.rtsbuilding.rtsbuilding.server.service.ServiceOperationTemplate;
import com.rtsbuilding.rtsbuilding.server.service.impl.*;
import com.rtsbuilding.rtsbuilding.server.util.ServiceTopoSorter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

public final class RtsServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RtsServer.class);
    private static RtsServer INSTANCE;

    private final List<RtsService> services = new ArrayList<>();

    private RtsPathfindingServiceImpl pathfindingService;
    private RtsBindingServiceImpl bindingService;
    private RtsPageServiceImpl pageService;
    private RtsTransferServiceImpl transferService;
    private RtsInteractionServiceImpl interactionService;
    private RtsMiningServiceImpl miningService;
    private RtsPlacementServiceImpl placementService;
    private RtsFluidServiceImpl fluidService;
    private RtsSessionServiceImpl sessionService;
    private RtsBlueprintServiceImpl blueprintService;
    private ServiceOperationTemplate serviceOp;

    private RtsServer() {}

    public static RtsServer init() {
        if (INSTANCE == null) {
            INSTANCE = new RtsServer();
            INSTANCE.discoverServices();
            INSTANCE.checkIntegrations();
        }
        return INSTANCE;
    }

    /**
     * 对已注册的宿主 mod 集成（AE2/RS/BD/SB）做统一健康检查：
     * 反射绑定缺失时打 WARN（而非静默降级），便于玩家/维护者定位失效集成。
     */
    private void checkIntegrations() {
        for (var integration : RtsCompatRegistry.getIntegrations()) {
            try {
                String problem = integration.selfCheck();
                if (problem != null && !problem.isEmpty()) {
                    LOGGER.warn("Host integration [{}] unhealthy: {}", integration.integrationId(), problem);
                } else {
                    LOGGER.info("Host integration [{}] healthy (available={})",
                            integration.integrationId(), integration.available());
                }
            } catch (Throwable e) {
                LOGGER.warn("Host integration [{}] selfCheck threw: {}", integration.integrationId(), e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void discoverServices() {
        Map<Class<?>, ServiceLoader.Provider<RtsService>> providers = ServiceLoader.load(RtsService.class).stream()
                .collect(Collectors.toMap(p -> p.type(), p -> p));

        // ── 构造全部服务（无参构造；构造期可通过 RtsServer.get() 访问已构造的服务）──
        this.pathfindingService = get(providers, RtsPathfindingServiceImpl.class);
        this.bindingService = get(providers, RtsBindingServiceImpl.class);
        this.transferService = get(providers, RtsTransferServiceImpl.class);
        this.interactionService = get(providers, RtsInteractionServiceImpl.class);
        this.miningService = get(providers, RtsMiningServiceImpl.class);
        this.placementService = get(providers, RtsPlacementServiceImpl.class);
        this.fluidService = get(providers, RtsFluidServiceImpl.class);
        this.blueprintService = get(providers, RtsBlueprintServiceImpl.class);
        // pageService before sessionService (RtsSessionServiceImpl accesses server.page() during construction)
        this.pageService = get(providers, RtsPageServiceImpl.class);
        this.sessionService = get(providers, RtsSessionServiceImpl.class);

        this.serviceOp = new ServiceOperationTemplate(this);

        // ── 收集已装配服务，按依赖拓扑排序后调用 init（阶段四 4.1）──
        services.add(pathfindingService);
        services.add(bindingService);
        services.add(pageService);
        services.add(transferService);
        services.add(interactionService);
        services.add(miningService);
        services.add(placementService);
        services.add(fluidService);
        services.add(sessionService);
        services.add(blueprintService);

        for (RtsService s : topoOrder()) {
            try {
                s.init(this);
            } catch (Throwable e) {
                LOGGER.error("Service init failed: {} — {}", s.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * 按 {@link RtsService#dependencies()} 做拓扑排序（委托 {@link ServiceTopoSorter}）。
     */
    private List<RtsService> topoOrder() {
        return ServiceTopoSorter.sort(services);
    }

    /**
     * 服务端停止清理：按 init 逆序调用各服务 {@link RtsService#shutdown()}。
     */
    public void shutdown() {
        for (int i = services.size() - 1; i >= 0; i--) {
            try {
                services.get(i).shutdown();
            } catch (Throwable e) {
                LOGGER.warn("Service shutdown failed: {} — {}", services.get(i).getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T get(Map<Class<?>, ServiceLoader.Provider<RtsService>> providers, Class<T> type) {
        ServiceLoader.Provider<RtsService> provider = providers.get(type);
        if (provider == null) {
            throw new IllegalStateException("Missing RTS service: " + type.getName()
                    + ". Check META-INF/services/" + RtsService.class.getName());
        }
        return (T) provider.get();
    }

    public static RtsServer get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("RtsServer not initialized. Call init() first.");
        }
        return INSTANCE;
    }

    /** 未初始化时返回 null（供停机等非关键路径安全访问）。 */
    public static RtsServer getInstanceOrNull() {
        return INSTANCE;
    }

    // ── Service accessors ──

    public RtsPathfindingServiceImpl pathfinding() { return pathfindingService; }
    public RtsBindingServiceImpl binding() { return bindingService; }
    public RtsPageServiceImpl page() { return pageService; }
    public RtsTransferServiceImpl transfer() { return transferService; }
    public RtsInteractionServiceImpl interaction() { return interactionService; }
    public RtsMiningServiceImpl mining() { return miningService; }
    public RtsPlacementServiceImpl placement() { return placementService; }
    public RtsFluidServiceImpl fluid() { return fluidService; }
    public RtsSessionServiceImpl session() { return sessionService; }
    public RtsBlueprintServiceImpl blueprint() { return blueprintService; }
    public ServiceOperationTemplate serviceOp() { return serviceOp; }
}
