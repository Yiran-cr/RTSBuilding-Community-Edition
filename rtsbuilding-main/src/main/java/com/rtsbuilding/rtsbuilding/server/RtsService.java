package com.rtsbuilding.rtsbuilding.server;

import java.util.List;

/**
 * Marker interface for all RTS server-side services.
 *
 * <p>Service classes implementing this interface are automatically discovered and registered
 * via {@link java.util.ServiceLoader}, without needing to be manually registered one by one
 * in {@link RtsServer#init()}.
 *
 * <p>Service classes must provide a public no-argument constructor.
 *
 * <p><b>生命周期</b>（阶段四 4.1）：服务可覆写 {@link #init(RtsServer)} / {@link #shutdown()} 参与
 * 装配后的初始化和服务端停止时的清理；依赖关系通过 {@link #dependencies()} 声明，由
 * {@link RtsServer} 拓扑排序保证装配顺序（未声明的服务维持 ServiceLoader 发现顺序）。
 */
public interface RtsService {

    /**
     * 装配完成后调用：服务在此完成对 {@link RtsServer} 的依赖注入。
     * 默认空实现，服务按需覆写。
     */
    default void init(RtsServer server) {
    }

    /**
     * 服务端停止时调用：服务在此释放资源。默认空实现，服务按需覆写。
     */
    default void shutdown() {
    }

    /**
     * 本服务依赖的其他服务类型（用于拓扑排序装配）。
     * 默认无依赖；返回空列表维持 ServiceLoader 发现顺序。
     */
    default List<Class<? extends RtsService>> dependencies() {
        return List.of();
    }
}
