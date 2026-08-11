package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.PerformanceConfig;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

public final class BoundaryPass implements RenderPass {

    
    private static final double FALLBACK_RADIUS = 250.0;

    
    private static final float TILE_SIZE = 2.0F;

    
    private static final float WHITE = 1.0F;

    
    public static int barrierColor = 0xFFFFCC00;

    /**
     * 屏障墙透明度。原 0.80 过实，相机被限制在墙内活动时大片半透明遮挡感强，
     * 旋转视角时形成"跟随摄像机视角"的深色阴影观感；调低后仅作弱边界提示。
     */
    private static final float BARRIER_ALPHA = 0.35F;

    /**
     * 边界墙相对锚点的最大向上高度（格）。原实现 yMax=最高边界方块+5、yMin=minBuildHeight(-64)，
     * 墙可从地下一直延伸到上百格高空，相机又被限制在墙内活动，任何视角墙都占满视野，
     * 观感即"超长的边界屏障阴影一直跟随摄像机"。限高后墙仅覆盖锚点附近一段，不再遮挡视线。
     */
    private static final float WALL_MAX_HEIGHT_ABOVE = 20.0F;

    /**
     * 边界墙相对锚点的最大向下深度（格）。墙底不再一路延伸到 -64，避免地下部分占据视野下部。
     */
    private static final float WALL_MAX_DEPTH_BELOW = 4.0F;

    
    private static final float SCROLL_SPEED = 0.5F;

    
    private static final long DEFAULT_FALLBACK_RECALC_MS = 500;

    
    private static final float MAX_DELTA_MS = 200.0F;

    
    private static final float SCROLL_MOD = 256.0F;

    

    
    private float scrollOffset;

    
    private long lastFrameMillis = -1;

    

    
    private int cachedMinX = Integer.MIN_VALUE;
    private int cachedMinZ = Integer.MIN_VALUE;
    private int cachedMaxX = Integer.MIN_VALUE;
    private int cachedMaxZ = Integer.MIN_VALUE;

    
    private int cachedHighestY = Integer.MIN_VALUE;

    
    private long fallbackLastRecalc;
    
    
    private long getFallbackRecalcInterval() {
        return PerformanceConfig.getBoundaryScanCacheTimeout();
    }

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.player != null
            && mc.screen instanceof com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen
            && PerformanceConfig.shouldRenderBoundaryWalls();
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex) {
        if (mc.player == null) return;
        RtsClientKernel kernel = RtsClientKernel.get();
        double r, cx, cy, cz;
        boolean useFallback;
        if (kernel.isRegionValid()) {
            cx = kernel.getRegionAnchorX();
            cy = kernel.getRegionAnchorY();
            cz = kernel.getRegionAnchorZ();
            r  = kernel.getRegionMaxRadius();
            useFallback = false;
        } else {
            cx = mc.player.getX();
            cy = mc.player.getY();
            cz = mc.player.getZ();
            r  = FALLBACK_RADIUS;
            useFallback = true;
        }
        
        
        var camera = mc.getCameraEntity();
        if (camera != null) {
            double dx = Math.abs(camera.getX() - cx) - r;
            double dz = Math.abs(camera.getZ() - cz) - r;
            double distanceToBoundary = Math.sqrt(
                Math.max(0, dx) * Math.max(0, dx) +
                Math.max(0, dz) * Math.max(0, dz)
            );
            
            
            try {
                if (PerformanceConfig.shouldEnableRenderDistanceCulling() &&
                    distanceToBoundary > PerformanceConfig.getMaxRenderDistance()) {
                    return;
                }
            } catch (IllegalStateException e) {
                
            }
        }

        
        long now = System.currentTimeMillis();
        if (this.lastFrameMillis < 0) {
            this.lastFrameMillis = now;
        } else {
            float deltaMs = (float) (now - this.lastFrameMillis);
            if (deltaMs > MAX_DELTA_MS) deltaMs = MAX_DELTA_MS; 
            this.scrollOffset = (this.scrollOffset + deltaMs * SCROLL_SPEED / 1000.0F) % SCROLL_MOD;
            this.lastFrameMillis = now;
        }

        // 屏障墙 RenderType 已改为自定义 POSITION_COLOR_TEX 无光照半透明（见
        // RenderPipeline.createBoundaryBarrierType）：不采样光照/阴影，Iris 对 basic
        // 管线通常直通，原版与光影下均为带 barrier.png 贴图的半透明墙。
        renderBarrierWalls(alloc, mc.level, poseStack, cx, cy, cz, r, useFallback, now);
    }

    /**
     * 计算边界框的 Y 范围：{@code yMin}（墙底）、{@code yMax}（墙顶）与 {@code wallH}。
     * 墙顶不超过边界最高方块+5 且不高于锚点上方 {@link #WALL_MAX_HEIGHT_ABOVE}；
     * 墙底不深入地下，最多锚点下方 {@link #WALL_MAX_DEPTH_BELOW}。
     */
    private WallBounds resolveWallY(Level level, float minX, float minZ, float maxX, float maxZ,
                                    float ay, boolean useFallback, long now) {
        int highest = resolveHighestY(level, minX, minZ, maxX, maxZ, useFallback, now);
        float yMax = (highest > Integer.MIN_VALUE)
                ? Math.min(highest + 5.0F, ay + WALL_MAX_HEIGHT_ABOVE)
                : ay + 3.0F;
        float yMin = Math.max((float) level.getMinBuildHeight(), ay - WALL_MAX_DEPTH_BELOW);
        return new WallBounds(yMin, yMax, Math.max(1.0F, yMax - yMin));
    }

    /** 边界框 Y 范围。 */
    private record WallBounds(float yMin, float yMax, float wallH) {}

    
    private void renderBarrierWalls(BufferAllocator alloc, Level level, PoseStack poseStack,
                                     double ax, double ay, double az, double r,
                                     boolean useFallback, long now) {
        
        ensureBarrierColor();
        float minX = (float) (ax - r);
        float minZ = (float) (az - r);
        float maxX = (float) (ax + r);
        float maxZ = (float) (az + r);

        WallBounds b = resolveWallY(level, minX, minZ, maxX, maxZ, (float) ay, useFallback, now);
        float yMax = b.yMax();
        float yMin = b.yMin();
        float wallH = b.wallH();

        var pose = poseStack.last();
        VertexConsumer barrier = alloc.barrier();

        float wallWX = maxX - minX;
        float wallWZ = maxZ - minZ;
        float scroll = this.scrollOffset;

        
        addTexturedQuad(pose, barrier, minX, yMin, minZ, maxX, yMax, minZ,
                wallWX / TILE_SIZE, wallH / TILE_SIZE, scroll);

        
        addTexturedQuad(pose, barrier, maxX, yMin, maxZ, minX, yMax, maxZ,
                wallWX / TILE_SIZE, wallH / TILE_SIZE, scroll);

        
        addTexturedQuad(pose, barrier, minX, yMin, minZ, minX, yMax, maxZ,
                wallWZ / TILE_SIZE, wallH / TILE_SIZE, scroll);

        
        addTexturedQuad(pose, barrier, maxX, yMin, maxZ, maxX, yMax, minZ,
                wallWZ / TILE_SIZE, wallH / TILE_SIZE, scroll);
    }

    
    private int resolveHighestY(Level level, float minX, float minZ, float maxX, float maxZ,
                                boolean useFallback, long now) {
        int iminX = (int) Math.floor(minX);
        int iminZ = (int) Math.floor(minZ);
        int imaxX = (int) Math.floor(maxX);
        int imaxZ = (int) Math.floor(maxZ);

        if (!useFallback) {
            
            if (iminX != cachedMinX || iminZ != cachedMinZ ||
                imaxX != cachedMaxX || imaxZ != cachedMaxZ) {
                cachedHighestY = findHighestBoundaryBlock(level, minX, minZ, maxX, maxZ);
                cachedMinX = iminX;
                cachedMinZ = iminZ;
                cachedMaxX = imaxX;
                cachedMaxZ = imaxZ;
            }
            return cachedHighestY;
        } else {
            
            if (now - fallbackLastRecalc >= getFallbackRecalcInterval()) {
                cachedHighestY = findHighestBoundaryBlock(level, minX, minZ, maxX, maxZ);
                fallbackLastRecalc = now;
            }
            return cachedHighestY;
        }
    }

    
    private static int findHighestBoundaryBlock(Level level, float minX, float minZ, float maxX, float maxZ) {
        int highest = Integer.MIN_VALUE;
        int x1 = (int) Math.floor(minX);
        int x2 = (int) Math.floor(maxX);
        int z1 = (int) Math.floor(minZ);
        int z2 = (int) Math.floor(maxZ);

        
        for (int x = x1; x <= x2; x++) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z1);
            if (h > highest) highest = h;
        }
        
        for (int x = x1; x <= x2; x++) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z2);
            if (h > highest) highest = h;
        }
        
        for (int z = z1 + 1; z < z2; z++) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x1, z);
            if (h > highest) highest = h;
        }
        
        for (int z = z1 + 1; z < z2; z++) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x2, z);
            if (h > highest) highest = h;
        }

        return highest;
    }

    
    

    private static final CornerBracketRenderer.Rgb barrierRgb = new CornerBracketRenderer.Rgb();

    private static void ensureBarrierColor() {
        barrierRgb.update(barrierColor);
    }

    private static void addTexturedQuad(PoseStack.Pose pose, VertexConsumer buffer,
                                         float x1, float yMin, float z1,
                                         float x2, float yMax, float z2,
                                         float tileU, float tileV,
                                         float scroll) {
        // POSITION_COLOR_TEX 顶点格式：位置 + 颜色 + UV，无光照/法线/lightmap。
        // 颜色 alpha 由 BARRIER_ALPHA 控制半透明，纹理为 barrier.png（UV 随 scroll 滚动）。
        buffer.addVertex(pose, x1, yMin, z1)
                .setColor(barrierRgb.r, barrierRgb.g, barrierRgb.b, BARRIER_ALPHA)
                .setUv(scroll, scroll);
        buffer.addVertex(pose, x2, yMin, z2)
                .setColor(barrierRgb.r, barrierRgb.g, barrierRgb.b, BARRIER_ALPHA)
                .setUv(tileU + scroll, scroll);
        buffer.addVertex(pose, x2, yMax, z2)
                .setColor(barrierRgb.r, barrierRgb.g, barrierRgb.b, BARRIER_ALPHA)
                .setUv(tileU + scroll, tileV + scroll);
        buffer.addVertex(pose, x1, yMax, z1)
                .setColor(barrierRgb.r, barrierRgb.g, barrierRgb.b, BARRIER_ALPHA)
                .setUv(scroll, tileV + scroll);
    }

    @Override
    public int requiredBuffers() {
        return 16; 
    }
}
