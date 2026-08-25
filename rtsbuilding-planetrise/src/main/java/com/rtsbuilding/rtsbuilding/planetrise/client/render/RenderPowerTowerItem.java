package com.rtsbuilding.rtsbuilding.planetrise.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.planetrise.client.model.ModelPowerTower;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 无线输电塔物品渲染器（参考 Mekanism {@code RenderWindGeneratorItem}）。
 * <p>
 * 物品栏图标与手持物品均通过 {@link ModelPowerTower} 渲染完整塔模型（而非方块模型
 * JSON）。顶部天线的旋转/缩放动画在进入世界后由游戏时间驱动；物品栏（无世界）用
 * 固定相位。物品模型 JSON 使用 {@code builtin/entity}，显示视角由该 JSON 的
 * {@code display} 变换决定。
 */
public class RenderPowerTowerItem extends BlockEntityWithoutLevelRenderer {

    public static final RenderPowerTowerItem RENDERER = new RenderPowerTowerItem();

    private ModelPowerTower model;

    private RenderPowerTowerItem() {
        // 注意：不能在构造器里 bakeLayer——静态单例在 RegisterClientReloadListenersEvent 时
        // 就被初始化，而 RegisterLayerDefinitions 事件尚未触发，EntityModelSet 里还没有
        // 该 layer 定义，会抛 "No model for layer"。烘焙推迟到 onResourceManagerReload
        // （首次资源重载时 layer 已注册），与 Mekanism 的 MekanismISTER 做法一致。
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    private ModelPowerTower bakeModel() {
        return new ModelPowerTower(Minecraft.getInstance().getEntityModels().bakeLayer(ModelPowerTower.LAYER));
    }

    @Override
    public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {
        this.model = bakeModel();
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext displayContext, @NotNull PoseStack poseStack,
          @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (model == null) {
            this.model = bakeModel();
        }
        Minecraft mc = Minecraft.getInstance();
        // 物品栏（无世界）用固定相位；进入世界后动画随时间流动。
        float timeSeconds = 0.0F;
        if (mc.level != null && mc.level.getGameTime() > 0) {
            timeSeconds = mc.level.getGameTime() / 20.0F;
        }
        poseStack.pushPose();
        // 模型原点对准物品体素中心（与方块实体渲染器的底部中心不同）。
        poseStack.translate(0.5, 0.5, 0.5);
        model.render(poseStack, bufferSource, timeSeconds, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
