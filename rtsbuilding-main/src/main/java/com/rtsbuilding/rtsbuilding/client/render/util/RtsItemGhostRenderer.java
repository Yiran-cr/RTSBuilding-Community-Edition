package com.rtsbuilding.rtsbuilding.client.render.util;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * 物品<b>Java 模型虚影渲染器</b>（客户端工具）。
 * <p>
 * 用于 RTS 建造成型 / 手动放置时，为「以 Java 模型（BEWLR）呈现的设备」（如输电塔、风力发电机）
 * 渲染<b>完整塔模型虚影</b>，而非常规方块模型 JSON 的占位几何（如 {@code wind_generator.json}
 * 的 {@code "elements": []} 空模型）。
 * <p>
 * <b>模块解耦</b>：虚影 Pass 位于主模组（{@code rtsbuilding-main}），而塔/风机的 BEWLR 实现位于
 * 内置插件（{@code rtsbuilding-planetrise}）。主模组<b>禁止</b>编译期引用插件模块，因此这里通过
 * NeoForge 通用 API {@link IClientItemExtensions#of(ItemStack)} 取到物品注册的自定义渲染器
 * （{@link BlockEntityWithoutLevelRenderer}）再调用其 {@code renderByItem}，对具体插件类零依赖。任何
 * 注册了自定义渲染器（{@link IClientItemExtensions#getCustomRenderer()} 非空）的物品均可复用本工具。
 * <p>
 * <b>坐标补偿</b>：塔/风机物品渲染器把模型原点对准<b>物品体素中心</b>（{@code translate(0.5, 0.5, 0.5)}），
 * 而其方块实体渲染器把模型原点对准<b>主方块底部中心</b>（{@code translate(0.5, 0, 0.5)}）。为让虚影
 * 以「放置后」的姿态落在目标方块底部中心，调用 {@link BlockEntityWithoutLevelRenderer#renderByItem}
 * 前需先把 {@link PoseStack} 平移到目标方块原点并<b>向下补偿 0.5 格</b>，抵消物品中心偏移。
 */
public final class RtsItemGhostRenderer {

    private RtsItemGhostRenderer() {
    }

    /**
     * 判断物品是否具备「自定义 BEWLR 渲染器」（即应渲染完整模型虚影而非方块模型占位）。
     * <p>
     * 塔/风机在客户端通过 {@code RegisterClientExtensionsEvent#registerItem} 注册了
     * {@link IClientItemExtensions#getCustomRenderer()}，据此判断。
     *
     * @param stack 待判断的物品。
     * @return {@code true} 表示该物品以 Java 模型渲染，虚影应走完整模型路径。
     */
    public static boolean hasModelGhost(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && IClientItemExtensions.of(stack).getCustomRenderer() != null;
    }

    /**
     * 在目标方块位置渲染物品的<b>完整 Java 模型虚影</b>（不透明实体模型，塔底落在目标方块底部）。
     * <p>
     * 通过标准 {@code renderBuffers().bufferSource()}（按实体纹理渲染，非图集）绘制，调用后立即
     * {@code endBatch()} 冲刷挂起批次，避免与后续 GUI / 世界批次混淆（参照
     * {@link com.rtsbuilding.rtsbuilding.client.scene.RtsSceneRenderer} 的冲刷方式）。
     *
     * @param mc      客户端。
     * @param poseStack 当前世界渲染 PoseStack（已按相机平移，调用方负责 push/pop 生命周期外的 offset）。
     * @param stack    待渲染的物品（塔/风机）。
     * @param pos      目标方块位置（塔的主方块）。
     * @param partialTick 帧间插值（本仅作占位，BEWLR 内部自取游戏时间驱动动画）。
     */
    public static void renderModelGhost(Minecraft mc, PoseStack poseStack, ItemStack stack,
                                        BlockPos pos, float partialTick) {
        BlockEntityWithoutLevelRenderer bewlr = IClientItemExtensions.of(stack).getCustomRenderer();
        if (bewlr == null || mc.level == null) {
            return;
        }
        poseStack.pushPose();
        // 目标方块原点 + 向下补偿 0.5：配合物品渲染器内部的 translate(0.5, 0.5, 0.5)，
        // 使模型原点最终落在目标方块的底部中心 (pos.x + 0.5, pos.y, pos.z + 0.5)，
        // 与方块实体渲染器「主方块底部中心」一致，塔身跨上方占位格向上延伸。
        poseStack.translate(pos.getX(), pos.getY() - 0.5, pos.getZ());
        int light = LevelRenderer.getLightColor(mc.level, pos);
        bewlr.renderByItem(stack, ItemDisplayContext.NONE, poseStack,
                mc.renderBuffers().bufferSource(), light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        mc.renderBuffers().bufferSource().endBatch();
    }
}
