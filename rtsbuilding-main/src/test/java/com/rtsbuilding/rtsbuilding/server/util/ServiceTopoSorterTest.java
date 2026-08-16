package com.rtsbuilding.rtsbuilding.server.util;

import com.rtsbuilding.rtsbuilding.server.RtsService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ServiceTopoSorter 拓扑排序测试（阶段四 4.1）。
 *
 * <p>验证依赖排序正确性、依赖缺失降级、成环防护与无依赖保持顺序。
 */
class ServiceTopoSorterTest {

    // ── 简单依赖链：A ← B ← C（C 依赖 B，B 依赖 A）──

    private static class ServiceA implements RtsService {}
    private static class ServiceB implements RtsService {
        @Override public List<Class<? extends RtsService>> dependencies() { return List.of(ServiceA.class); }
    }
    private static class ServiceC implements RtsService {
        @Override public List<Class<? extends RtsService>> dependencies() { return List.of(ServiceB.class); }
    }

    @Test
    void dependencyBeforeDependent() {
        List<RtsService> result = ServiceTopoSorter.sort(List.of(new ServiceC(), new ServiceA(), new ServiceB()));
        int idxA = indexOf(result, ServiceA.class);
        int idxB = indexOf(result, ServiceB.class);
        int idxC = indexOf(result, ServiceC.class);
        assertTrue(idxA < idxB, "A 应先于 B");
        assertTrue(idxB < idxC, "B 应先于 C");
    }

    @Test
    void noDependenciesKeepDiscoveryOrder() {
        List<RtsService> input = List.of(new ServiceA(), new ServiceB(), new ServiceC());
        // 直接给无依赖服务：C 声明依赖 B，但输入顺序 A,B,C 已满足 → 顺序不变
        List<RtsService> result = ServiceTopoSorter.sort(input);
        assertEquals(ServiceA.class, result.get(0).getClass());
        assertEquals(ServiceB.class, result.get(1).getClass());
        assertEquals(ServiceC.class, result.get(2).getClass());
    }

    @Test
    void missingDependencyDegrades() {
        // D 依赖不存在的 X → 降级为剩余顺序，不抛异常
        class ServiceD implements RtsService {
            @Override public List<Class<? extends RtsService>> dependencies() { return List.of(RtsService.class); }
        }
        List<RtsService> result = ServiceTopoSorter.sort(List.of(new ServiceA(), new ServiceD()));
        assertEquals(2, result.size());
    }

    @Test
    void cyclicDependencyTerminates() {
        // 成环：不抛异常、不死循环，返回全部服务
        List<RtsService> result = ServiceTopoSorter.sort(List.of(new ServiceX(), new ServiceY()));
        assertEquals(2, result.size());
    }

    private static class ServiceX implements RtsService {
        @Override public List<Class<? extends RtsService>> dependencies() { return List.of(ServiceY.class); }
    }

    private static class ServiceY implements RtsService {
        @Override public List<Class<? extends RtsService>> dependencies() { return List.of(ServiceX.class); }
    }

    @Test
    void emptyInput() {
        assertTrue(ServiceTopoSorter.sort(List.of()).isEmpty());
    }

    private static int indexOf(List<RtsService> list, Class<?> clazz) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getClass() == clazz) return i;
        }
        return -1;
    }
}
