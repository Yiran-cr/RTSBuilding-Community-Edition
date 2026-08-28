package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.PerformanceConfig;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.RenderPipeline;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.render.util.RtsItemGhostRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * 无线输电塔放置预览覆盖范围 Pass —— 玩家在建造成型时选中了输电塔（RTS 模式）或手持输电塔
 * （手动放置）的前提下，在目标放置位置绘制球形范围球体，显示供电范围。
 * <p>
 * 检测逻辑：
 * <ul>
 *   <li><b>RTS 模式</b>（{@link BuilderScreen} 打开）：玩家主手持终端，无法同时手持输电塔，
 *       因此检查建造模式下当前<b>选中的建造物品</b>是否为无线输电塔。注意不能回退为「只要物品栏
 *       里有输电塔就显示」——否则玩家存储了输电塔但未选中建造时也会误渲染范围圈。</li>
 *   <li><b>手动放置</b>：检查玩家是否手持（主手/副手）输电塔。</li>
 * </ul>
 * RTS 模式通过 {@link RenderPipeline} 渲染（由 {@link #render} 方法处理），
 * 使用 {@link CornerBracketRenderer.SmoothTarget} 做平滑跟随动画。
 * 手动放置通过 {@link #renderManual(RenderLevelStageEvent)} 静态方法在
 * {@link RenderLevelStageEvent} 中独立渲染。
 * </p>
 * <p>
 * 球体渲染：三条两两垂直的赤道大圆线框（LINES 细线）
 * + 半透明球壳面片（QUADS，增强体积感）。风格与 {@link FunnelRangePass} 保持一致。
 * </p>
 */
public final class TowerRangePreviewPass implements RenderPass {

    /** 输电塔物品的注册 ID。 */
    private static final ResourceLocation POWER_TOWER_ITEM_ID =
            ResourceLocation.tryParse("rtsbuilding_planetrise:power_tower");

    /** 每条大圆的分段数（7.5° 步长，弦长约 0.26 格，视觉上足够圆滑）。 */
    private static final int SEGMENTS = 48;

    /** 球壳表面经度细分段数。 */
    private static final int SHELL_LON_SEGMENTS = 24;

    /** 球壳表面纬度细分段数。 */
    private static final int SHELL_LAT_SEGMENTS = 12;

    /** 经纬网格线 alpha（LINES，带深度）。 */
    private static final float GRID_ALPHA = 0.9f;

    /** 球壳填充 alpha（QUADS，带深度）。 */
    private static final float SHELL_DEPTH_ALPHA = 0.10f;

    /** 球壳填充 alpha（QUADS，穿透无深度）。 */
    private static final float SHELL_NO_DEPTH_ALPHA = 0.05f;

    /** 供电范围颜色（黄色）。 */
    public static int rangeColor = 0xFFFFD700;

    private static final CornerBracketRenderer.Rgb rangeRgb = new CornerBracketRenderer.Rgb();
    private static final CornerBracketRenderer.SmoothTarget smoothTarget = new CornerBracketRenderer.SmoothTarget();

    /** 手动放置渲染缓冲区容量。 */
    private static final int BUFFER_CAPACITY = 1024 * 64;

    /** 手动放置球壳渲染 RenderType：POSITION_COLOR，半透明，与 RTS 模式的 BRACKET_QUADS 一致。 */
    private static final RenderType MANUAL_SHELL_TYPE = RenderType.create(
            "rtsbuilding_tower_preview_shell",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 2048, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    // ==================== RenderPass 接口（RTS 模式）====================

    @Override
    public boolean shouldRender(Minecraft mc) {
        if (mc.player == null) return false;
        // RTS 模式：仅当建造模式下当前选中的建造物品正是无线输电塔时，才显示放置位置的范围预览。
        // 注意不能改回「只要物品栏里有输电塔就显示」——那会让玩家在物品栏存储了输电塔但并未选中
        // 建造该塔时，也始终渲染范围圈，造成视觉误导。
        if (mc.screen instanceof BuilderScreen screen) {
            if (!screen.isCameraActive()) return false;
            BuildingModule buildingModule = RtsClientKernel.get().module(BuildingModule.class);
            if (buildingModule == null) {
                return false;
            }
            return POWER_TOWER_ITEM_ID.toString().equals(buildingModule.getSelectedItemId());
        }
        // 手动放置不通过此 Pass 渲染，由外部 RenderLevelStageEvent 触发
        return false;
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex) {
        if (mc.level == null || mc.getCameraEntity() == null) return;
        if (!(mc.screen instanceof BuilderScreen)) return;

        var ray = alloc.cursorRay();
        if (ray == null) return;
        BlockHitResult hit = ray.raycastBlock(mc);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        // 获取目标放置位置，球心在方块中心
        BlockPos targetPos = hit.getBlockPos().relative(hit.getDirection());
        double cx = targetPos.getX() + 0.5D;
        double cy = targetPos.getY() + 0.5D;
        double cz = targetPos.getZ() + 0.5D;
        double r = Config.powerTowerPowerRange();
        smoothTarget.update(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r);

        // 平滑后计算实际球心
        cx = (smoothTarget.minX() + smoothTarget.maxX()) / 2;
        cy = (smoothTarget.minY() + smoothTarget.maxY()) / 2;
        cz = (smoothTarget.minZ() + smoothTarget.maxZ()) / 2;
        r = (smoothTarget.maxX() - smoothTarget.minX()) / 2;

        // 距离剔除
        double distance = smoothTarget.centerDistanceTo(ray.origin());
        try {
            if (PerformanceConfig.shouldEnableRenderDistanceCulling()
                    && distance > PerformanceConfig.getMaxRenderDistance()) {
                return;
            }
        } catch (IllegalStateException e) {
            // 配置未加载时跳过剔除
        }

        rangeRgb.update(rangeColor);
        float rr = rangeRgb.r, rg = rangeRgb.g, rb = rangeRgb.b;

        // 1) 半透明球壳（先画，作为体积底衬）
        renderSphereShell(alloc.brackets(), poseStack, cx, cy, cz, r, rr, rg, rb, SHELL_DEPTH_ALPHA);
        if (BoxSelectionPass.depthTestEnabled) {
            renderSphereShell(alloc.noDepth(), poseStack, cx, cy, cz, r,
                    rr, rg, rb, SHELL_NO_DEPTH_ALPHA);
        }

        // 2) 三条两两垂直的赤道大圆线框（细线，保证任意视角轮廓圆润完整）
        renderSphereGrid(alloc.lines(), poseStack, cx, cy, cz, r, rr, rg, rb, GRID_ALPHA);
    }

    @Override
    public int requiredBuffers() {
        return 1 | 4 | 8; // lines + brackets + noDepth
    }

    // ==================== 手动放置渲染（RenderLevelStageEvent）====================

    /**
     * 手动放置时通过 {@link RenderLevelStageEvent} 绘制的入口。
     * 当玩家手持输电塔物品且不在 RTS 模式时，在目标放置位置绘制球形范围，
     * 并渲染输电塔的完整 Java 模型虚影（与 RTS 模式表现保持一致）。
     */
    public static void renderManual(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        // 仅手动放置（不在 RTS 模式）时触发
        if (mc.screen instanceof BuilderScreen) return;
        if (!isHoldingTower(mc.player)) return;

        // 获取目标位置
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;
        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockPos targetPos = hit.getBlockPos().relative(hit.getDirection());

        double cx = targetPos.getX() + 0.5D;
        double cy = targetPos.getY() + 0.5D;
        double cz = targetPos.getZ() + 0.5D;
        double r = Config.powerTowerPowerRange();

        // 距离剔除（基于相机）
        double distance = event.getCamera().getPosition().distanceTo(new Vec3(cx, cy, cz));
        try {
            if (PerformanceConfig.shouldEnableRenderDistanceCulling()
                    && distance > PerformanceConfig.getMaxRenderDistance()) {
                return;
            }
        } catch (IllegalStateException e) {
            // 配置未加载时跳过剔除
        }

        if (r <= 0) return;

        rangeRgb.update(rangeColor);
        float rr = rangeRgb.r, rg = rangeRgb.g, rb = rangeRgb.b;

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        // 1) 球壳（与 RTS 模式 BRACKET_QUADS 相同的 POSITION_COLOR 半透明 QUADS）
        if (r > 0) {
            ByteBufferBuilder shellBacking = new ByteBufferBuilder(BUFFER_CAPACITY);
            BufferBuilder shellBuilder = new BufferBuilder(shellBacking,
                    VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            renderSphereShell(shellBuilder, poseStack, cx, cy, cz, r, rr, rg, rb, SHELL_DEPTH_ALPHA);
            MeshData shellMesh = shellBuilder.build();
            if (shellMesh != null) {
                MANUAL_SHELL_TYPE.draw(shellMesh);
                shellMesh.close();
            }
            shellBacking.close();
        }

        // 2) 网格线框（与 RTS 模式 RenderType.lines() 相同的 POSITION_COLOR_NORMAL 格式，
        //    顶点一致性保证线宽/深度表现与 RTS 完全一致）
        ByteBufferBuilder linesBacking = new ByteBufferBuilder(BUFFER_CAPACITY);
        BufferBuilder linesBuilder = new BufferBuilder(linesBacking,
                RenderType.lines().mode, RenderType.lines().format);
        renderSphereGrid(linesBuilder, poseStack, cx, cy, cz, r, rr, rg, rb, GRID_ALPHA);
        MeshData lineMesh = linesBuilder.build();
        if (lineMesh != null) {
            RenderType.lines().draw(lineMesh);
            lineMesh.close();
        }
        linesBacking.close();

        // 3) 输电塔完整 Java 模型虚影（目标方块底部放置，与 RTS 模式一致）
        RtsItemGhostRenderer.renderModelGhost(mc, poseStack, heldTowerStack(mc.player), targetPos, 0.0F);

        poseStack.popPose();
    }

    /** 返回玩家主手/副手中持有的输电塔物品（用于手动放置模型虚影）。 */
    private static ItemStack heldTowerStack(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && POWER_TOWER_ITEM_ID.equals(BuiltInRegistries.ITEM.getKey(mainHand.getItem()))) {
            return mainHand;
        }
        return player.getOffhandItem();
    }

    // ==================== 工具方法 ====================

    /**
     * 判断玩家是否手持输电塔物品（主手或副手）。
     */
    private static boolean isHoldingTower(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && POWER_TOWER_ITEM_ID.equals(BuiltInRegistries.ITEM.getKey(mainHand.getItem()))) {
            return true;
        }
        ItemStack offHand = player.getOffhandItem();
        return !offHand.isEmpty() && POWER_TOWER_ITEM_ID.equals(BuiltInRegistries.ITEM.getKey(offHand.getItem()));
    }

    /**
     * 绘制三条两两垂直的赤道大圆：水平环（XZ 平面，y = cy）、
     * 垂直环（XY 平面，z = cz）与垂直环（YZ 平面，x = cx），
     * 各自按 {@link #SEGMENTS} 段细分。
     */
    private static void renderSphereGrid(VertexConsumer consumer, PoseStack poseStack,
            double cx, double cy, double cz, double r,
            float red, float green, float blue, float alpha) {
        double step = Math.PI * 2 / SEGMENTS;
        // 第一条：水平赤道（XZ 平面，y = cy），半径 r
        for (int s = 0; s < SEGMENTS; s++) {
            double t0 = s * step;
            double t1 = t0 + step;
            double x0 = cx + r * Math.cos(t0);
            double z0 = cz + r * Math.sin(t0);
            double x1 = cx + r * Math.cos(t1);
            double z1 = cz + r * Math.sin(t1);
            lineSegment(consumer, poseStack, x0, cy, z0, x1, cy, z1, red, green, blue, alpha);
        }
        // 第二条：垂直赤道（XY 平面，z = cz），与水平赤道互相垂直
        for (int s = 0; s < SEGMENTS; s++) {
            double t0 = s * step;
            double t1 = t0 + step;
            double x0 = cx + r * Math.cos(t0);
            double y0 = cy + r * Math.sin(t0);
            double x1 = cx + r * Math.cos(t1);
            double y1 = cy + r * Math.sin(t1);
            lineSegment(consumer, poseStack, x0, y0, cz, x1, y1, cz, red, green, blue, alpha);
        }
        // 第三条：垂直赤道（YZ 平面，x = cx），与前两条两两垂直
        for (int s = 0; s < SEGMENTS; s++) {
            double t0 = s * step;
            double t1 = t0 + step;
            double y0 = cy + r * Math.cos(t0);
            double z0 = cz + r * Math.sin(t0);
            double y1 = cy + r * Math.cos(t1);
            double z1 = cz + r * Math.sin(t1);
            lineSegment(consumer, poseStack, cx, y0, z0, cx, y1, z1, red, green, blue, alpha);
        }
    }

    /**
     * 绘制半透明球壳：按经纬度细分成小四边形面片，营造完整的体积感。
     */
    private static void renderSphereShell(VertexConsumer consumer, PoseStack poseStack,
            double cx, double cy, double cz, double r,
            float red, float green, float blue, float alpha) {
        double lonStep = Math.PI * 2 / SHELL_LON_SEGMENTS;
        double latStep = Math.PI / SHELL_LAT_SEGMENTS;
        for (int lat = 0; lat < SHELL_LAT_SEGMENTS; lat++) {
            double phi0 = -Math.PI / 2 + lat * latStep;
            double phi1 = phi0 + latStep;
            double cos0 = Math.cos(phi0), sin0 = Math.sin(phi0);
            double cos1 = Math.cos(phi1), sin1 = Math.sin(phi1);
            for (int lon = 0; lon < SHELL_LON_SEGMENTS; lon++) {
                double lam0 = lon * lonStep;
                double lam1 = lam0 + lonStep;
                double cosA = Math.cos(lam0), sinA = Math.sin(lam0);
                double cosB = Math.cos(lam1), sinB = Math.sin(lam1);

                double x00 = cx + r * cos0 * cosA, y00 = cy + r * sin0, z00 = cz + r * cos0 * sinA;
                double x10 = cx + r * cos0 * cosB, y10 = cy + r * sin0, z10 = cz + r * cos0 * sinB;
                double x11 = cx + r * cos1 * cosB, y11 = cy + r * sin1, z11 = cz + r * cos1 * sinB;
                double x01 = cx + r * cos1 * cosA, y01 = cy + r * sin1, z01 = cz + r * cos1 * sinA;

                CornerBracketRenderer.quad(consumer, poseStack, x00, y00, z00, x10, y10, z10, x11, y11, z11, x01, y01, z01, red, green, blue, alpha);
            }
        }
    }

    /**
     * 绘制一条线段（LINES 格式，POSITION_COLOR_NORMAL）。
     */
    private static void lineSegment(VertexConsumer consumer, PoseStack poseStack,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            float r, float g, float b, float a) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-6) return;
        float nx = (float) (dx / len);
        float ny = (float) (dy / len);
        float nz = (float) (dz / len);

        var pose = poseStack.last();
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(r, g, b, a)
                .setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(r, g, b, a)
                .setNormal(pose, nx, ny, nz);
    }
}