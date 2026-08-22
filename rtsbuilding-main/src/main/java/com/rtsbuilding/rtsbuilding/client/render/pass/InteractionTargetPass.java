package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.PerformanceConfig;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.background.ScreenBackgroundPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class InteractionTargetPass implements RenderPass {

    private static final double LINE_OFFSET = 0.01D;

    private static final CornerBracketRenderer.SmoothTarget smoothTarget = new CornerBracketRenderer.SmoothTarget();

    

    private static final CornerBracketRenderer.Rgb blockColor = new CornerBracketRenderer.Rgb();
    private static final CornerBracketRenderer.Rgb entityColor = new CornerBracketRenderer.Rgb();

    

    
    public static int blockTargetColor = 0xFFF69C31;
    
    public static int entityTargetColor = 0xFF4D99FF;

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.screen instanceof BuilderScreen
            && isConfigSafe() && PerformanceConfig.shouldRenderInteractionHighlights();
    }
    
    private boolean isConfigSafe() {
        try {
            PerformanceConfig.shouldRenderInteractionHighlights();
            return true;
        } catch (IllegalStateException e) {
            
            return true; 
        }
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex) {
        if (mc.level == null || mc.getCameraEntity() == null) return;
        if (!(mc.screen instanceof BuilderScreen screen)) return;

        // XYZ 轴调节器拖拽期间跳过：光标隐藏后不应基于其位置渲染点击目标高亮
        if (screen.isAnyDragActive()) return;

        if (!screen.isClickButtonSelected()) return;

        
        if (!isMouseInContentArea(mc, screen)) return;
        if (screen.isMouseOverUI(guiMouseX(mc, screen), guiMouseY(mc, screen))) return;

        
        var ray = alloc.cursorRay();
        if (ray == null) return;

        var hit = ray.raycastNearest(mc);

        
        boolean isBlock;
        double tMinX, tMinY, tMinZ, tMaxX, tMaxY, tMaxZ;

        if (hit.hasEntity()) {
            
            if (hit.entityHit() == null) return;
            isBlock = false;
            var entity = hit.entityHit().getEntity();
            var bounds = entity.getBoundingBox().inflate(0.03D);
            tMinX = bounds.minX; tMinY = bounds.minY; tMinZ = bounds.minZ;
            tMaxX = bounds.maxX; tMaxY = bounds.maxY; tMaxZ = bounds.maxZ;
        } else if (hit.hasBlock()) {
            if (hit.blockHit() == null) return;
            isBlock = true;
            BlockPos pos = hit.blockHit().getBlockPos();
            // 点击模式按住 Ctrl：与 BuildInteractionHandler.resolveBuildHit 一致，
            // 目标选中位置偏移到命中面外侧一格，高亮同步跟随。
            if (isCtrlDown()) {
                pos = pos.relative(hit.blockHit().getDirection());
            }
            double off = LINE_OFFSET;
            tMinX = pos.getX() - off; tMinY = pos.getY() - off; tMinZ = pos.getZ() - off;
            tMaxX = pos.getX() + 1 + off; tMaxY = pos.getY() + 1 + off; tMaxZ = pos.getZ() + 1 + off;
        } else {
            return;
        }

        
        smoothTarget.update(tMinX, tMinY, tMinZ, tMaxX, tMaxY, tMaxZ);

        
        double distance = smoothTarget.centerDistanceTo(ray.origin());
        
        
        try {
            if (PerformanceConfig.shouldEnableRenderDistanceCulling() &&
                distance > PerformanceConfig.getMaxRenderDistance()) {
                return;
            }
        } catch (IllegalStateException e) {
            
        }

        
        blockColor.update(blockTargetColor);
        entityColor.update(entityTargetColor);
        float r = isBlock ? blockColor.r : entityColor.r;
        float g = isBlock ? blockColor.g : entityColor.g;
        float b = isBlock ? blockColor.b : entityColor.b;

        
        CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.brackets(),
                smoothTarget.minX(), smoothTarget.minY(), smoothTarget.minZ(),
                smoothTarget.maxX(), smoothTarget.maxY(), smoothTarget.maxZ(),
                r, g, b, 1.0f, distance);
        
        if (BoxSelectionPass.depthTestEnabled) {
            CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.noDepth(),
                    smoothTarget.minX(), smoothTarget.minY(), smoothTarget.minZ(),
                    smoothTarget.maxX(), smoothTarget.maxY(), smoothTarget.maxZ(),
                    r, g, b, CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA, distance);
        }
    }

    private static boolean isCtrlDown() {
        return RtsKeyMappings.isPlaceOffsetDown();
    }

    @Override
    public int requiredBuffers() {
        return 4 | 8; 
    }

    

    private static double guiMouseX(Minecraft mc, BuilderScreen screen) {
        return mc.mouseHandler.xpos() / screen.getRtsGuiScale();
    }

    private static double guiMouseY(Minecraft mc, BuilderScreen screen) {
        return mc.mouseHandler.ypos() / screen.getRtsGuiScale();
    }

    private static boolean isMouseInContentArea(Minecraft mc, BuilderScreen screen) {
        double rtsScale = screen.getRtsGuiScale();
        var win = mc.getWindow();
        int virtualW = (int) Math.round(win.getScreenWidth() / rtsScale);
        int virtualH = (int) Math.round(win.getScreenHeight() / rtsScale);
        double mx = guiMouseX(mc, screen);
        double my = guiMouseY(mc, screen);
        int left = screen.getLeftSidebarWidth();
        int top = ScreenBackgroundPanel.BACKGROUND_TOP_Y;
        int right = virtualW - screen.getRightSidebarWidth();
        int bottom = virtualH - screen.getDownSidebarHeight();
        return mx >= left && mx < right && my >= top && my < bottom;
    }
}
