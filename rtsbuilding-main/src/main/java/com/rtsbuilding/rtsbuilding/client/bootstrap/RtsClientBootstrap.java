package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.camera.RtsCameraEntityRenderer;
import com.rtsbuilding.rtsbuilding.client.entity.RtsDroneRenderer;
import com.rtsbuilding.rtsbuilding.client.entity.rts_drone;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.mining.MiningModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.pathfinding.PathfindingModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.remote.RemoteMenuModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.workflow.WorkflowModule;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeybinds;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.render.RtsShaders;
import com.rtsbuilding.rtsbuilding.common.RtsEntities;
import com.rtsbuilding.rtsbuilding.common.RtsItems;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class RtsClientBootstrap {

    private RtsClientBootstrap() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RtsEntities.RTS_CAMERA_ENTITY.get(), RtsCameraEntityRenderer::new);
        event.registerEntityRenderer(RtsEntities.RTS_DRONE.get(), RtsDroneRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(RtsDroneRenderer.LAYER_LOCATION, rts_drone::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "rounded_rect"),
                            DefaultVertexFormat.POSITION_TEX_COLOR
                    ),
                    shader -> RtsShaders.roundedRect = shader
            );
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "rounded_rect_outline"),
                            DefaultVertexFormat.POSITION_TEX_COLOR
                    ),
                    shader -> RtsShaders.roundedRectOutline = shader
            );
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "rounded_rect_top"),
                            DefaultVertexFormat.POSITION_TEX_COLOR
                    ),
                    shader -> RtsShaders.roundedRectTop = shader
            );
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "rounded_rect_bottom"),
                            DefaultVertexFormat.POSITION_TEX_COLOR
                    ),
                    shader -> RtsShaders.roundedRectBottom = shader
            );
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "rounded_rect_left"),
                            DefaultVertexFormat.POSITION_TEX_COLOR
                    ),
                    shader -> RtsShaders.roundedRectLeft = shader
            );
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "rounded_rect_right"),
                            DefaultVertexFormat.POSITION_TEX_COLOR
                    ),
                    shader -> RtsShaders.roundedRectRight = shader
            );
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "chevron"),
                            DefaultVertexFormat.POSITION_TEX_COLOR
                    ),
                    shader -> RtsShaders.chevron = shader
            );
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "textured"),
                            DefaultVertexFormat.POSITION_TEX_COLOR
                    ),
                    shader -> RtsShaders.textured = shader
            );
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "reset_icon"),
                            DefaultVertexFormat.POSITION_TEX_COLOR
                    ),
                    shader -> RtsShaders.resetIcon = shader
            );
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to load shader", e);
        }
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // RTS 按键不再注册到原版"按键绑定"界面，改由 RTS 设置面板内的
            // "按键设置"折叠条目配置；此处加载自定义绑定并应用到 KeyMapping 对象。
            RtsKeybinds.load();

            // 终端点亮模型属性：RTS 模式开启时（TERMINAL_LIT 组件为 true）物品模型
            // 切换为 rts_terminal_lit（由 rts_terminal.json 的 overrides 引用）。
            ItemProperties.register(
                    RtsItems.RTS_TERMINAL.get(),
                    ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "lit"),
                    (stack, level, entity, seed) ->
                            Boolean.TRUE.equals(stack.get(RtsItems.TERMINAL_LIT.get())) ? 1.0F : 0.0F);

            RtsClientKernel kernel = RtsClientKernel.get();

            kernel.register(new CameraModule());
            kernel.register(new StorageModule());
            kernel.register(new BuildingModule());
            kernel.register(new MiningModule());
            kernel.register(new WorkflowModule());
            kernel.register(new RemoteMenuModule());
            kernel.register(new PathfindingModule());

            kernel.initialize();
            RtsbuildingMod.LOGGER.info("RTS client kernel initialized with all modules");

            // 预热 BuilderScreen：首次进入 RTS 模式时，BuilderScreen 及其大量 UI 依赖类
            // 的类加载 + JIT 编译会造成明显卡顿（进入游戏后的第一次打开）。这里在启动阶段
            // 提前完整构造 + init 一次（触发类加载与静态初始化，实例随后被 GC 回收），
            // 把一次性开销从“首次打开 RTS 终端”转移到游戏启动阶段。构造不涉及 GL/渲染。
            try {
                BuilderScreen.warmUp();
            } catch (Throwable t) {
                RtsbuildingMod.LOGGER.warn("RTS: BuilderScreen warmup failed (non-fatal): {}", t.toString());
            }
        });
    }
}
