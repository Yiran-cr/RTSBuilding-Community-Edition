package com.rtsbuilding.rtsbuilding.core.network;

import com.rtsbuilding.rtsbuilding.api.compat.RtsCompatRegistry;
import com.rtsbuilding.rtsbuilding.api.compat.RtsIntegration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RtsIntegration SPI 注册机制测试（不依赖 Minecraft 运行时）。
 *
 * <p>验证 {@link RtsCompatRegistry#registerIntegration} 的契约：注册后可通过
 * {@link RtsCompatRegistry#getIntegrations()} 遍历，供主 mod 统一做健康检查。
 * 各 addon 的反射绑定正确性需在装有宿主的运行时由 selfCheck() 验证（见 RtsServer.checkIntegrations）。
 */
class RtsCompatRegistryTest {

    @Test
    void registerAndGetIntegrations() {
        var integration = new FakeIntegration("ae2", true, null);
        RtsCompatRegistry.registerIntegration(integration);

        List<RtsIntegration> all = RtsCompatRegistry.getIntegrations();
        assertTrue(all.contains(integration), "注册的 integration 应能被遍历到");
        assertEquals("ae2", integration.integrationId());
        assertTrue(integration.available());
        assertEquals(null, integration.selfCheck());
    }

    @Test
    void registerIgnoresNull() {
        RtsCompatRegistry.registerIntegration(null);
        // 不抛异常即可
        assertTrue(true);
    }

    @Test
    void selfCheckReportsMissingBinding() {
        var broken = new FakeIntegration("refinedstorage", false, "missing: mhInsert,mhExtract");
        RtsCompatRegistry.registerIntegration(broken);
        assertTrue(!broken.selfCheck().isEmpty(), "异常集成应报告诊断串");
    }

    /** 最小可测 integration 实现（真实 addon 的 selfCheck 逻辑需宿主运行时验证）。 */
    private record FakeIntegration(String id, boolean available, String selfCheck)
            implements RtsIntegration {
        @Override public String integrationId() { return id; }
        @Override public boolean available() { return available; }
        @Override public String selfCheck() { return selfCheck; }
        @Override public void register(RtsCompatRegistry registry) {
            // no-op（测试仅验证注册/遍历契约）
        }
    }
}
