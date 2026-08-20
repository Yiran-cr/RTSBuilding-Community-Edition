package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.blueprint.BlueprintImportPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.group_button.ActionButtonGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.group_button.BuildDestroyButtonGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.group_button.SelectButtonGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.group_button.ShapeButtonGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.group_button.UltimineButtonGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.group_button.CameraModeGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.group_button.RayCullingButtonGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.group_button.UtilityButtonGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.popup.LogoMenuPopup;
import com.rtsbuilding.uifw.render.MipmapTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/**
 * 客户端 mipmap 图标纹理注册与资源重载钩子。
 *
 * <p>左/顶面板按钮图标与 LOGO 源图多为 512x512 画到 17~24px（约 20~30 倍缩小），vanilla
 * {@code SimpleTexture} 不生成 mipmap 会严重混叠，需用 {@link MipmapTexture} 预生成 mipmap 链平滑缩放。</p>
 *
 * <p>Minecraft 的 {@code TextureManager} 在资源重载时会清空已注册纹理，之后按需用无 mipmap 的
 * {@code SimpleTexture} 重建。本类通过 {@link RegisterClientReloadListenersEvent} 挂入客户端重载链，
 * 在 {@code TextureManager} 之后重新 register（NeoForge 附加的监听器按注册序执行），保证
 * F3+T / 资源包切换后 mipmap 不失效。启动时的首次注册由 {@link RtsClientBootstrap} 调用
 * {@link #registerAll()} 完成。</p>
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class RtsMipmapTextures {

    private RtsMipmapTextures() {}

    /** 资源重载应用阶段回调：重新注册 mipmap 纹理（幂等）。 */
    private static final PreparableReloadListener RELOAD_LISTENER = new SimplePreparableReloadListener<Void>() {
        @Override
        protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
            return null;
        }

        @Override
        protected void apply(Void data, ResourceManager manager, ProfilerFiller profiler) {
            registerAll();
        }
    };

    /** 把全部需要 mipmap 平滑的界面图标纹理注册为 {@link MipmapTexture}（幂等，可重复调用）。 */
    public static void registerAll() {
        register(
                ShapeButtonGroup.ALL_ICON_TEX,
                new ResourceLocation[] {
                        UltimineButtonGroup.ULTIMINE_BTN,
                        SelectButtonGroup.BTN_TEXTURE,
                        SelectButtonGroup.SELECT_BTN,
                        BuildDestroyButtonGroup.CONSTRUCTION_BTN,
                        BuildDestroyButtonGroup.DESTRUCTION_BTN,
                        ActionButtonGroup.BIND_BTN,
                        ActionButtonGroup.DIRECTION_ROTATE_BTN,
                        ActionButtonGroup.ITEM_PICKUP_BTN
                },
                new ResourceLocation[] {
                        TopBarPanel.LOGO_TEXTURE,
                        LogoMenuPopup.SETTING_TEXTURE,
                        CameraModeGroup.FREE_MODE,
                        CameraModeGroup.SURROUND_MODE,
                        UtilityButtonGroup.CHUNK_DISPLAY,
                        RayCullingButtonGroup.RAY_CULLING
                },
                new ResourceLocation[] {
                        BlueprintImportPanel.UPDATE_TEXTURE
                });
    }

    private static void register(ResourceLocation[]... groups) {
        var textureManager = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation[] group : groups) {
            for (ResourceLocation loc : group) {
                textureManager.register(loc, new MipmapTexture(loc));
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(RELOAD_LISTENER);
    }
}
