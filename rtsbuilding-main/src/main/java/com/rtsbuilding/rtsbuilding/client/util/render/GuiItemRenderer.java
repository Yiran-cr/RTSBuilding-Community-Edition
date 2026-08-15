package com.rtsbuilding.rtsbuilding.client.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 统一物品图标绘制工具。
 *
 * <p>全项目所有 {@code renderItem} 图标绘制必须收敛到本类，统一采用"绘制前关闭深度测试、
 * 绘制后开启、角标绘制完成后再关闭"的成对写法，并在绘制完成后清空深度缓冲。
 * 原因：物品模型经 {@code GuiGraphics.renderItem} 写入的深度值 z 偏移极大（靠相机极近），
 * 若不清理，后续其它面板/UI 元素绘制时会被深度测试丢弃，导致物品图标"穿透"上层面板显示。</p>
 */
public final class GuiItemRenderer {

    /** GL 深度缓冲位掩码（{@code GL_DEPTH_BUFFER_BIT}），用于清除物品写入的深度污染。 */
    private static final int GL_DEPTH_BUFFER_BIT = 256;

    private GuiItemRenderer() {}

    /**
     * 将物品 ID 字符串解析为 ItemStack（按注册表校验，失败返回空栈）。
     * 供各面板按 ID 绘制物品图标统一使用。
     */
    public static ItemStack resolveItemStack(String itemId) {
        if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    /**
     * 以 (x, y) 为左上角绘制 16×16 物品图标及数量角标。
     * 绘制完成后自动清除物品写入的深度缓冲，调用方可直接继续绘制其它 UI。
     */
    public static void drawItem(GuiGraphics g, ItemStack stack, int x, int y) {
        drawItem(g, stack, x, y, 0);
    }

    /**
     * 以 (x, y) 为左上角绘制 16×16 物品图标及数量角标，并指定 z 偏移（供浮层置顶用）。
     * 绘制完成后自动清除物品写入的深度缓冲。
     */
    public static void drawItem(GuiGraphics g, ItemStack stack, int x, int y, int z) {
        drawItemInternal(g, stack, x, y, z, 1.0f, true, true);
    }

    /**
     * 以 (centerX, centerY) 为中心绘制缩放物品图标（不画数量角标），
     * 供标签栏等小尺寸图标使用。绘制完成后自动清除物品写入的深度缓冲。
     *
     * @param scale 缩放比（图标 16px × scale）
     */
    public static void drawItemCentered(GuiGraphics g, ItemStack stack, int centerX, int centerY, float scale) {
        if (stack == null || stack.isEmpty()) return;
        float half = 8.0f * scale;
        drawItemInternal(g, stack, centerX - half, centerY - half, 0, scale, false, true);
    }

    /**
     * 批量绘制物品图标（不逐次清理深度缓冲），供物品网格等高频场景使用；
     * 全部绘制完毕后必须调用 {@link #finishItemBatch(GuiGraphics)} 统一清理深度污染。
     */
    public static void drawItemBatch(GuiGraphics g, ItemStack stack, int x, int y) {
        drawItemInternal(g, stack, x, y, 0, 1.0f, true, false);
    }

    /**
     * 批量物品图标绘制结束：强制提交全部绘制并清空深度缓冲、关闭深度测试，
     * 使后续 UI 绘制不受物品深度污染。
     */
    public static void finishItemBatch(GuiGraphics g) {
        g.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.clear(GL_DEPTH_BUFFER_BIT, false);
    }

    private static void drawItemInternal(GuiGraphics g, ItemStack stack, float x, float y, int z,
                                         float scale, boolean drawDecorations, boolean clearDepth) {
        if (stack == null || stack.isEmpty()) return;

        // 关闭深度测试绘制物品本体，避免图标与面板/其它 GUI 元素相互穿透
        RenderSystem.disableDepthTest();
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(scale, scale, 1.0f);
        g.renderItem(stack, 0, 0);
        pose.popPose();

        // 数量角标在深度开启状态下绘制，保证文本叠加在物品之上
        RenderSystem.enableDepthTest();
        if (drawDecorations) {
            g.renderItemDecorations(Minecraft.getInstance().font, stack, (int) x, (int) y);
        }
        RenderSystem.disableDepthTest();

        // 物品模型经 renderItem 写入的深度值靠相机极近，不清空会导致后续 UI 被深度测试
        // 丢弃（物品透过上层面板显示）。单图标场景立即清理，批量场景由 finishItemBatch 统一清理。
        if (clearDepth) {
            g.flush();
            RenderSystem.clear(GL_DEPTH_BUFFER_BIT, false);
        }
    }
}
