package com.rtsbuilding.rtsbuilding.server.util;

import com.rtsbuilding.rtsbuilding.server.RtsService;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务拓扑排序工具（阶段四 4.1）。
 *
 * <p>按 {@link RtsService#dependencies()} 对服务做拓扑排序（Kahn 入度法），保证
 * 任何服务的依赖先于它出现。依赖缺失或成环时安全降级为剩余发现顺序，不抛异常。
 */
public final class ServiceTopoSorter {

    private ServiceTopoSorter() {}

    /**
     * 拓扑排序服务列表。
     *
     * @param services 已装配服务（顺序任意）
     * @return 排序后列表：依赖先于被依赖者；依赖缺失/成环时保持剩余顺序
     */
    public static List<RtsService> sort(List<RtsService> services) {
        if (services.isEmpty()) {
            return List.of();
        }

        // 类 → 服务 映射（保证所有服务都被返回，即使依赖缺失）
        Map<Class<?>, RtsService> byClass = new LinkedHashMap<>();
        for (RtsService s : services) {
            byClass.put(s.getClass(), s);
        }

        // 入度：每个服务被多少已存在依赖指向
        Map<Class<?>, Integer> inDegree = new HashMap<>();
        Map<Class<?>, List<RtsService>> dependents = new HashMap<>();
        for (RtsService s : services) {
            inDegree.putIfAbsent(s.getClass(), 0);
            for (Class<? extends RtsService> dep : s.dependencies()) {
                if (dep == s.getClass()) continue;
                RtsService depService = byClass.get(dep);
                if (depService == null) continue; // 依赖缺失：忽略
                inDegree.merge(s.getClass(), 1, Integer::sum);
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(s);
            }
        }

        // Kahn：入度 0 的服务先入队
        Deque<RtsService> ready = new ArrayDeque<>();
        for (RtsService s : services) {
            if (inDegree.getOrDefault(s.getClass(), 0) == 0) {
                ready.add(s);
            }
        }

        List<RtsService> ordered = new ArrayList<>(services.size());
        while (!ready.isEmpty()) {
            RtsService s = ready.poll();
            ordered.add(s);
            for (RtsService dependent : dependents.getOrDefault(s.getClass(), List.of())) {
                Class<?> depClazz = dependent.getClass();
                int deg = inDegree.getOrDefault(depClazz, 0) - 1;
                inDegree.put(depClazz, deg);
                if (deg == 0) {
                    ready.add(dependent);
                }
            }
        }

        // 环导致的剩余服务（入度 > 0）：按原顺序追加，保证不丢
        if (ordered.size() < services.size()) {
            for (RtsService s : services) {
                if (!ordered.contains(s)) {
                    ordered.add(s);
                }
            }
        }
        return ordered;
    }
}
