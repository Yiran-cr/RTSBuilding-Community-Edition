package com.rtsbuilding.rtsbuilding.planetrise.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.planetrise.client.model.ModelWindGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 风力发电机物品渲染器（参考 Mekanism {@code RenderWindGeneratorItem}）。
 * <p>
 * 物品栏图标与手持物品均通过 {@link ModelWindGenerator} 渲染完整塔模型（而非方块
 * 模型 JSON），风叶在进入世界后随游戏时间持续旋转。物品模型 JSON 使用
 * {@code builtin/entity}，显示视角由该 JSON 的 {@code display} 变换决定。
 */
public class RenderWindGeneratorItem extends BlockEntityWithoutLevelRenderer {

    public static final RenderWindGeneratorItem RENDERER = new RenderWindGeneratorItem();

    private static final float BLADE_SPEED = 2.0F;

    private ModelWindGenerator model;

    private RenderWindGeneratorItem() {
        // 注意：不能在构造器里 bakeLayer——静态单例在 RegisterClientReloadListenersEvent 时
        // 就被初始化，而 RegisterLayerDefinitions 事件尚未触发，EntityModelSet 里还没有
        // 该 layer 定义，会抛 "No model for layer"。烘焙推迟到 onResourceManagerReload
        // （首次资源重载时 layer 已注册），与 Mekanism 的 MekanismISTER 做法一致。
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    private ModelWindGenerator bakeModel() {
        return new ModelWindGenerator(Minecraft.getInstance().getEntityModels().bakeLayer(ModelWindGenerator.LAYER));
    }

    @Override
    public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {
        // 资源重载后重新烘焙模型，保证与方块实体渲染器使用同一份图层定义。
        this.model = bakeModel();
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext displayContext, @NotNull PoseStack poseStack,
          @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (model == null) {
            // 防御性懒加载：理论上首次进入世界前一定已完成资源重载。
            this.model = bakeModel();
        }
        Minecraft mc = Minecraft.getInstance();
        // 物品栏（无世界）用固定角度；进入世界后风叶随时间旋转。
        float angle = 20.0F;
        if (mc.level != null) {
            angle = (mc.level.getGameTime() * BLADE_SPEED) % 360.0F;
        }
        poseStack.pushPose();
        // 模型原点对准物品体素中心（与方块实体渲染器的底部中心不同）。
        poseStack.translate(0.5, 0.5, 0.5);
        model.render(poseStack, bufferSource, angle, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
