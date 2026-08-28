package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.RenderPipeline;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.render.util.RtsAlphaVertexConsumer;
import com.rtsbuilding.rtsbuilding.client.render.util.RtsItemGhostRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.HashMap;
import java.util.Map;

/**
 * RTS 模式单方块放置幽灵预览 Pass —— 在建造模式下，当玩家选中一个可放置方块时，
 * 在光标指向的目标位置渲染该方块的半透明虚影（ghost），帮助玩家精确定位。
 * <p>
 * 对于多格结构（如输电塔、风力发电机），额外渲染上方占位方块的半透明色块，
 * 示意完整结构高度。
 * </p>
 * <p>
 * 虚影渲染使用 {@link BlockRenderDispatcher#renderSingleBlock} +
 * {@link RtsAlphaVertexConsumer}，与 {@link BlueprintPlacementPreviewPass} 保持一致。
 * </p>
 */
public final class BlockPlacementPreviewPass implements RenderPass {

    /** 虚影透明度。 */
    private static final float GHOST_ALPHA = 0.45F;

    /** 占位方块虚影透明度（比主方块略淡，减少视觉干扰）。 */
    private static final float BOUNDING_GHOST_ALPHA = 0.25F;

    /** 占位块虚影颜色（浅蓝，与主方块纹理区分）。 */
    private static final float BOUNDING_R = 0.5F, BOUNDING_G = 0.7F, BOUNDING_B = 1.0F;

    /** 多格结构的高度映射（占位方块数量，不含主方块自身）。 */
    private static final Map<String, Integer> MULTI_BLOCK_HEIGHTS = new HashMap<>();

    static {
        MULTI_BLOCK_HEIGHTS.put("rtsbuilding_planetrise:power_tower", 4);
        MULTI_BLOCK_HEIGHTS.put("rtsbuilding_planetrise:wind_generator", 2);
    }

    @Override
    public boolean shouldRender(Minecraft mc) {
        if (!(mc.screen instanceof BuilderScreen screen)) return false;
        if (!screen.isCameraActive()) return false;
        BuildingModule buildingModule = RtsClientKernel.get().module(BuildingModule.class);
        if (buildingModule == null) return false;
        String itemId = buildingModule.getSelectedItemId();
        if (itemId == null || itemId.isBlank()) return false;
        // 检查选中的物品是否是方块物品
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
        return item instanceof BlockItem;
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex) {
        if (mc.level == null || mc.getCameraEntity() == null) return;

        var ray = alloc.cursorRay();
        if (ray == null) return;
        BlockHitResult hit = ray.raycastBlock(mc);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos targetPos = hit.getBlockPos().relative(hit.getDirection());

        // 获取选中的方块物品
        BuildingModule buildingModule = RtsClientKernel.get().module(BuildingModule.class);
        if (buildingModule == null) return;
        String itemId = buildingModule.getSelectedItemId();
        if (itemId == null || itemId.isBlank()) return;

        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
        if (!(item instanceof BlockItem blockItem)) return;

        // Java 模型设备（输电塔/风力发电机等，以 BEWLR Java 模型呈现）：虚影直接渲染完整整塔模型，
        // 而非方块模型 JSON 的占位几何；模型已涵盖全部占位格高度，不再额外画占位色块。
        ItemStack previewStack = buildingModule.getSelectedItemPreview();
        if (RtsItemGhostRenderer.hasModelGhost(previewStack)) {
            RtsItemGhostRenderer.renderModelGhost(mc, poseStack, previewStack, targetPos, partialTick);
            return;
        }

        BlockState state = blockItem.getBlock().defaultBlockState();

        // 渲染主方块幽灵
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        renderGhostBlock(mc.level, targetPos, state, GHOST_ALPHA, dispatcher, poseStack, alloc);

        // 多格结构：渲染上方占位方块虚影
        Integer height = MULTI_BLOCK_HEIGHTS.get(itemId);
        if (height != null) {
            for (int i = 1; i <= height; i++) {
                BlockPos abovePos = targetPos.above(i);
                renderBoundingGhost(alloc, poseStack, abovePos, BOUNDING_GHOST_ALPHA);
            }
        }
    }

    /**
     * 渲染单个方块的真实模型半透明虚影；非完整模型方块回退为半透明色块。
     * 与 {@link BlueprintPlacementPreviewPass#renderGhostBlock} 实现一致。
     */
    private static void renderGhostBlock(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                                          float alpha, BlockRenderDispatcher dispatcher,
                                          PoseStack poseStack, BufferAllocator alloc) {
        if (state.getRenderShape() != RenderShape.MODEL) {
            // 非模型方块回退为半透明色块
            int color = 0xFFCCCCCC;
            var mapColor = state.getMapColor(level, pos);
            if (mapColor != null) color = 0xFF000000 | mapColor.col;
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            double x = pos.getX(), y = pos.getY(), z = pos.getZ();
            CornerBracketRenderer.renderFilledFaces(alloc.brackets(), poseStack,
                    x, y, z, x + 1, y + 1, z + 1, r, g, b, alpha);
            return;
        }
        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        int light = LevelRenderer.getLightColor(level, pos);
        MultiBufferSource src = rt -> new RtsAlphaVertexConsumer(alloc.blockSource().getBuffer(rt), alpha);
        dispatcher.renderSingleBlock(state, poseStack, src, light, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, RenderPipeline.BLOCK_ANIMATION);
        poseStack.popPose();
    }

    /**
     * 渲染占位方块位置的半透明色块，示意多格结构的高度。
     */
    private static void renderBoundingGhost(BufferAllocator alloc, PoseStack poseStack,
                                             BlockPos pos, float alpha) {
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        CornerBracketRenderer.renderFilledFaces(alloc.brackets(), poseStack,
                x, y, z, x + 1, y + 1, z + 1, BOUNDING_R, BOUNDING_G, BOUNDING_B, alpha);
    }

    @Override
    public int requiredBuffers() {
        return 4; // brackets（用于非模型方块回退 + 占位块虚影）
    }
}