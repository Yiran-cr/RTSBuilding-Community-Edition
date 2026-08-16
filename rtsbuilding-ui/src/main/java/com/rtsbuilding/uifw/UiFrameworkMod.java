package com.rtsbuilding.uifw;

import com.rtsbuilding.uifw.render.UiShaders;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * uifw（UI Framework）独立 UI 工具模组入口（modId: {@code uifw}）。
 *
 * <p>提供独立的 SDF 渲染库、动画/主题系统与可复用的窗口/控件框架，
 * 供任何 mod 依赖使用。本模组不包含任何游戏业务逻辑（网络/服务端/建造）。
 *
 * <p>负责注册 UI 渲染所需的 10 个自定义 shader（资源位于
 * {@code assets/uifw/shaders/core}）。宿主 mod 无需自行注册这些 shader，
 * 直接使用 {@link UiShaders} 静态字段即可。
 */
@Mod("uifw")
public class UiFrameworkMod {

    public static final String MODID = "uifw";

    public UiFrameworkMod(IEventBus modEventBus, net.neoforged.fml.ModContainer modContainer) {
        // RegisterShadersEvent 是 IModBusEvent，必须注册到 MOD 总线（modEventBus），
        // 不能挂在 common NeoForge 总线（否则构造失败）。
        modEventBus.addListener(this::registerShaders);
    }

    /** 注册 UI shader（rounded_rect 系列 / chevron / textured / reset_icon）。 */
    private void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    shader(event, "rounded_rect"), shader -> UiShaders.roundedRect = shader);
            event.registerShader(
                    shader(event, "rounded_rect_outline"), shader -> UiShaders.roundedRectOutline = shader);
            event.registerShader(
                    shader(event, "rounded_rect_top"), shader -> UiShaders.roundedRectTop = shader);
            event.registerShader(
                    shader(event, "rounded_rect_bottom"), shader -> UiShaders.roundedRectBottom = shader);
            event.registerShader(
                    shader(event, "rounded_rect_left"), shader -> UiShaders.roundedRectLeft = shader);
            event.registerShader(
                    shader(event, "rounded_rect_right"), shader -> UiShaders.roundedRectRight = shader);
            event.registerShader(
                    shader(event, "chevron"), shader -> UiShaders.chevron = shader);
            event.registerShader(
                    shader(event, "textured"), shader -> UiShaders.textured = shader);
            event.registerShader(
                    shader(event, "reset_icon"), shader -> UiShaders.resetIcon = shader);
            event.registerShader(
                    shader(event, "colorwheel"), shader -> UiShaders.colorwheel = shader);
        } catch (java.io.IOException e) {
            throw new RuntimeException("uifw: failed to register shaders", e);
        }
    }

    private static ShaderInstance shader(RegisterShadersEvent event, String name) throws java.io.IOException {
        return new ShaderInstance(
                event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(MODID, name),
                DefaultVertexFormat.POSITION_TEX_COLOR);
    }
}
