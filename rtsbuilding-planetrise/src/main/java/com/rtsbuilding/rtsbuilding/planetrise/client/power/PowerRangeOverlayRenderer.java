package com.rtsbuilding.rtsbuilding.planetrise.client.power;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rtsbuilding.rtsbuilding.planetrise.power.IPowerGridNode;
import com.rtsbuilding.rtsbuilding.planetrise.power.PowerRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Collection;

/**
 * 能量节点<b>范围圈覆盖渲染器</b>（客户端，参照 power_system_design.md 第六节视觉反馈）。
 * <p>
 * 挂在 {@link RenderLevelStageEvent} 的 {@code AFTER_TRANSLUCENT_BLOCKS} 阶段，独立于 RTS
 * 客户端内核执行；当 {@link PowerRangeVisualStore} 的全局显示开关开启时，为每个已注册的
 * 能量节点绘制<b>水平圆环</b>：
 * <ul>
 *   <li><b>链路范围圈</b>（蓝色）——发电机 / 输电塔均绘制，表示组网距离；</li>
 *   <li><b>供电范围圈</b>（黄色）——仅输电塔绘制，表示广播供电的圆形区域。</li>
 * </ul>
 * 圆圈为地面水平线环（XZ 平面），以节点中心为圆心；靠近玩家的节点会被优先绘制。
 */
public final class PowerRangeOverlayRenderer {

    /** 圆环分段数（约 3.75° 步长，视觉圆滑）。 */
    private static final int SEGMENTS = 96;

    /** 渲染缓冲区容量。 */
    private static final int BUFFER_CAPACITY = 1024 * 256;

    /** 链路范围圈颜色（蓝）。 */
    private static final float[] LINK_COLOR = {0.25F, 0.65F, 1.0F};

    /** 供电范围圈颜色（黄）。 */
    private static final float[] POWER_COLOR = {1.0F, 0.85F, 0.10F};

    /** 圆环背景填充 alpha。 */
    private static final float RING_ALPHA = 0.85F;

    /** 圆环渲染类型：深色半透明线、无剔除（世界坐标绘制）。 */
    private static final RenderType RING_TYPE = RenderType.create(
            "rtsbuilding_power_range",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.DEBUG_LINES, 2048, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    private PowerRangeOverlayRenderer() {
    }

    /** 渲染回调入口：全局显示开启时绘制所有已注册节点的范围圈。 */
    public static void render(RenderLevelStageEvent event) {
        PowerRangeVisualStore store = PowerRangeVisualStore.INSTANCE;
        if (!store.isGlobalShow()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCameraEntity() == null) {
            return;
        }
        Collection<IPowerGridNode> nodes = store.nodes();
        if (nodes.isEmpty()) {
            return;
        }

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        PoseStack.Pose pose = poseStack.last();

        ByteBufferBuilder backing = new ByteBufferBuilder(BUFFER_CAPACITY);
        BufferBuilder builder = new BufferBuilder(backing, VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (IPowerGridNode node : nodes) {
            if (!(node instanceof net.minecraft.world.level.block.entity.BlockEntity be)) {
                continue;
            }
            BlockPos pos = be.getBlockPos();
            double distSq = camPos.distanceToSqr(Vec3.atCenterOf(pos));
            if (distSq > MAX_DRAW_DISTANCE_SQ) {
                continue;
            }
            // 链路范围圈（蓝）：所有节点均有。
            drawCircle(builder, pose, pos, node.linkRange(), LINK_COLOR[0], LINK_COLOR[1], LINK_COLOR[2]);
            // 供电范围圈（黄）：仅输电塔。
            if (node.role() == PowerRole.TOWER && node.powerRange() > 0) {
                drawCircle(builder, pose, pos, node.powerRange(), POWER_COLOR[0], POWER_COLOR[1], POWER_COLOR[2]);
            }
        }

        MeshData mesh = builder.build();
        if (mesh != null) {
            RING_TYPE.draw(mesh);
            mesh.close();
        }
        backing.close();
        poseStack.popPose();
    }

    /** 绘制某个节点为中心、给定半径的地面水平圆环（XZ 平面）。 */
    private static void drawCircle(BufferBuilder builder, PoseStack.Pose pose,
            BlockPos pos, double radius, float r, float g, float b) {
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY();          // 以节点底部所在格的地面为圆面
        double cz = pos.getZ() + 0.5D;
        double step = Math.PI * 2 / SEGMENTS;
        for (int s = 0; s < SEGMENTS; s++) {
            double t0 = s * step;
            double t1 = t0 + step;
            double x0 = cx + radius * Math.cos(t0);
            double z0 = cz + radius * Math.sin(t0);
            double x1 = cx + radius * Math.cos(t1);
            double z1 = cz + radius * Math.sin(t1);
            addLine(builder, pose, x0, cy, z0, x1, cy, z1, r, g, b);
        }
    }

    /** 追加一条线段顶点（LINES 格式，POSITION_COLOR）。 */
    private static void addLine(BufferBuilder builder, PoseStack.Pose pose,
            double x1, double y1, double z1, double x2, double y2, double z2,
            float r, float g, float b) {
        builder.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, RING_ALPHA);
        builder.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(r, g, b, RING_ALPHA);
    }

    /** 绘图距离剔除：超过该距离的节点不画圈（避免远处小圈被忽略、近处大圈爆量）。 */
    private static final double MAX_DRAW_DISTANCE_SQ = 256.0 * 256.0;
}
