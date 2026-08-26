package com.rtsbuilding.rtsbuilding.planetrise.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.planetrise.EnergyBlockEntities;
import com.rtsbuilding.rtsbuilding.planetrise.EnergyBlocks;
import com.rtsbuilding.rtsbuilding.planetrise.EnergyItems;
import com.rtsbuilding.rtsbuilding.planetrise.EnergyMod;
import com.rtsbuilding.rtsbuilding.planetrise.client.model.ModelPowerTower;
import com.rtsbuilding.rtsbuilding.planetrise.client.model.ModelWindGenerator;
import com.rtsbuilding.rtsbuilding.planetrise.client.power.PowerRangeOverlayRenderer;
import com.rtsbuilding.rtsbuilding.planetrise.client.power.PowerRangeVisualStore;
import com.rtsbuilding.rtsbuilding.planetrise.client.render.RenderPowerTower;
import com.rtsbuilding.rtsbuilding.planetrise.client.render.RenderPowerTowerItem;
import com.rtsbuilding.rtsbuilding.planetrise.client.render.RenderWindGenerator;
import com.rtsbuilding.rtsbuilding.planetrise.client.render.RenderWindGeneratorItem;
import com.rtsbuilding.rtsbuilding.api.powergrid.RtsPowerGrid;
import com.rtsbuilding.rtsbuilding.planetrise.network.PowerGridApiImpl;
import com.rtsbuilding.rtsbuilding.planetrise.network.PowerGridPackets;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side registration for the built-in energy addon.
 * <p>
 * This class is picked up by {@code @EventBusSubscriber} unconditionally, so it
 * must guard against the addon being disabled via config — in that case the
 * energy blocks were never registered and their holders are unbound.
 */
@EventBusSubscriber(modid = EnergyMod.MODID, value = Dist.CLIENT)
public final class EnergyClient {

    /** 全局显示所有能量节点范围圈的快捷键（按住显示、松开隐藏；可在按键设置中绑定）。 */
    public static final KeyMapping POWER_RANGE_OVERLAY_KEY = new KeyMapping(
            "key.rtsbuilding_planetrise.power_range",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            "key.categories.rtsbuilding_planetrise");

    private EnergyClient() {
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        if (!Config.isTechnologizedEnabled()) {
            return;
        }
        // Custom break particles: consolidate multi-element collision shapes into
        // a single bounding-box particle burst (see BlockRenderProperties).
        event.registerBlock(BlockRenderProperties.INSTANCE, EnergyBlocks.THERMAL_GENERATOR.get());
        event.registerBlock(BlockRenderProperties.INSTANCE, EnergyBlocks.POWER_TOWER.get());
        event.registerBlock(BlockRenderProperties.INSTANCE, EnergyBlocks.ENERGY_CELL.get());
        event.registerBlock(BlockRenderProperties.INSTANCE, EnergyBlocks.WIND_GENERATOR.get());
        event.registerBlock(BlockRenderProperties.INSTANCE, EnergyBlocks.BOUNDING_BLOCK.get());

        // 风力发电机物品：用 BEWLR 渲染完整塔模型（物品栏/手持均显示塔身 + 旋转风叶）。
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return RenderWindGeneratorItem.RENDERER;
            }
        }, EnergyItems.WIND_GENERATOR.get());

        // 无线输电塔物品：用 BEWLR 渲染完整塔模型（物品栏/手持均显示整座塔）。
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return RenderPowerTowerItem.RENDERER;
            }
        }, EnergyItems.POWER_TOWER.get());
    }

    /** 资源重载时重建物品渲染器内缓存的塔模型。 */
    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        if (!Config.isTechnologizedEnabled()) {
            return;
        }
        event.registerReloadListener(RenderWindGeneratorItem.RENDERER);
        event.registerReloadListener(RenderPowerTowerItem.RENDERER);
    }

    /** 注册风力发电机/无线输电塔方块实体渲染器（Java 模型平移绘制整座塔）。 */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        if (!Config.isTechnologizedEnabled()) {
            return;
        }
        event.registerBlockEntityRenderer(EnergyBlockEntities.WIND_GENERATOR.get(), RenderWindGenerator::new);
        event.registerBlockEntityRenderer(EnergyBlockEntities.POWER_TOWER.get(), RenderPowerTower::new);
    }

    /** 注册风力发电机/无线输电塔 Java 模型的图层定义。 */
    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        if (!Config.isTechnologizedEnabled()) {
            return;
        }
        event.registerLayerDefinition(ModelWindGenerator.LAYER, ModelWindGenerator::createLayerDefinition);
        event.registerLayerDefinition(ModelPowerTower.LAYER, ModelPowerTower::createLayerDefinition);
    }

    /** 注册范围圈全局显示快捷键。 */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(POWER_RANGE_OVERLAY_KEY);
    }

    /** 客户端构造完成：注入电网多人系统 API 实现（纯内存赋值，无网络）。 */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (!Config.isTechnologizedEnabled()) {
            return;
        }
        RtsPowerGrid.setImplementation(PowerGridApiImpl.INSTANCE);
    }

    /** 客户端加入服务器后：请求一次电网信息刷新（此时网络连接已就绪，可安全发 C2S）。 */
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!Config.isTechnologizedEnabled()) {
            return;
        }
        PacketDistributor.sendToServer(new PowerGridPackets.C2SPowerGridRefresh());
    }

    /**
     * 渲染阶段：按住快捷键时显示所有已加载能量节点的范围圈（链路蓝圈 + 供电黄圈）。
     * <p>
     * 每帧读取按键状态写入 {@link PowerRangeVisualStore#setGlobalShow(boolean)}——
     * 按住即显示、松开即隐藏（满足设计文档「按住快捷键可全局显示所有节点的半径圈」）。
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!Config.isTechnologizedEnabled()) {
            return;
        }
        PowerRangeVisualStore.INSTANCE.setGlobalShow(POWER_RANGE_OVERLAY_KEY.isDown());
        PowerRangeOverlayRenderer.render(event);
    }
}

