package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.culling.RtsRayCylinderCullingState;
import com.rtsbuilding.rtsbuilding.client.culling.RtsRayCylinderCullingState.Snapshot;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * 直线圆柱剔除的范围预览 pass。
 *
 * <p>当剔除开启且 RTS 界面打开时，每帧以「摄像机朝向（屏幕中心视线）」的直线更新
 * 圆柱快照（含节流网格失效），并绘制圆柱线框：两端圆环 + 侧壁竖线 + 轴线。
 * 圆柱以相机位置为轴中点、沿视线方向<b>双向</b>延伸（前方与后方各一份剔除距离），
 * 轴与鼠标位置无关，始终沿摄像机视线方向；关闭或非 RTS 状态下不渲染、不更新。</p>
 *
 * <p>渲染结构参考 {@link FunnelRangePass} 的 LINES 细线模式（POSITION_COLOR_NORMAL）。</p>
 */
public final class CylinderCullingPreviewPass implements RenderPass {

    /** 圆环分段数（11.25° 步长，弦长约 0.2 格，视觉上足够圆滑）。 */
    private static final int SEGMENTS = 32;

    /** 线框 alpha。 */
    private static final float LINE_ALPHA = 0.85f;

    /** 圆柱线框颜色（天蓝，区别于漏斗绿 / 框选橙）。 */
    public static int cullingColor = 0xFF42A5F5;

    /** 兜底世界竖直轴（用于构造圆柱正交基的默认上方向）。 */
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Vec3 WORLD_X = new Vec3(1.0D, 0.0D, 0.0D);

    @Override
    public boolean shouldRender(Minecraft mc) {
        if (mc.screen instanceof BuilderScreen screen) {
            // 剔除功能 + RTS 相机激活（与 FunnelRangePass 同样的相机激活判断）
            if (screen.isCameraActive() && RtsRayCylinderCullingState.isEnabled()) {
                return true;
            }
        }
        // 非 RTS 界面 / 相机停用：自动关闭剔除并恢复被隐藏的方块（幂等）。
        // 剔除是 RTS 相机会话内的纯视觉功能，退出界面后不应继续隐藏世界。
        if (RtsRayCylinderCullingState.isEnabled()) {
            RtsRayCylinderCullingState.setEnabled(false);
        }
        return false;
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex) {
        if (mc.level == null) return;
        // 摄像机朝向射线（屏幕中心视线），与鼠标位置无关
        var ray = CursorRaycaster.computeCameraCenterRay(mc);
        if (ray == null) return;

        // 以当前射线更新圆柱快照（内部节流后失效网格并同步 Flywheel）
        RtsRayCylinderCullingState.updateFromRay(ray.origin(), ray.direction());
        Snapshot snapshot = RtsRayCylinderCullingState.snapshot();
        if (snapshot == null) {
            return;
        }

        float r = ((cullingColor >> 16) & 0xFF) / 255.0f;
        float g = ((cullingColor >> 8) & 0xFF) / 255.0f;
        float b = (cullingColor & 0xFF) / 255.0f;

        Vec3 dir = snapshot.direction();
        Vec3 right = dir.cross(WORLD_UP);
        if (right.lengthSqr() < 1.0E-6D) {
            // 射线接近竖直：换基准轴构造正交基
            right = dir.cross(WORLD_X);
        }
        right = right.normalize();
        Vec3 up = right.cross(dir).normalize();

        // 双向轴线段：以相机位置为轴中点，向前/向后各剔除距离（快照 distance = 单侧长度）
        Vec3 start = snapshot.origin().subtract(dir.scale(snapshot.distance()));
        Vec3 end = snapshot.origin().add(dir.scale(snapshot.distance()));
        double radius = snapshot.radius();

        // 两端圆环
        drawCircle(alloc.lines(), poseStack, start, right, up, radius, r, g, b);
        drawCircle(alloc.lines(), poseStack, end, right, up, radius, r, g, b);
        // 侧壁竖线 + 轴线
        double step = Math.PI * 2 / SEGMENTS;
        for (int s = 0; s < SEGMENTS; s++) {
            double cos = Math.cos(s * step);
            double sin = Math.sin(s * step);
            double ax = start.x + right.x * cos * radius + up.x * sin * radius;
            double ay = start.y + right.y * cos * radius + up.y * sin * radius;
            double az = start.z + right.z * cos * radius + up.z * sin * radius;
            double bx = end.x + right.x * cos * radius + up.x * sin * radius;
            double by = end.y + right.y * cos * radius + up.y * sin * radius;
            double bz = end.z + right.z * cos * radius + up.z * sin * radius;
            lineSegment(alloc.lines(), poseStack, ax, ay, az, bx, by, bz, r, g, b);
        }
        // 轴线（标出剔除的直线方向，跨整个双向圆柱）
        lineSegment(alloc.lines(), poseStack,
                start.x, start.y, start.z, end.x, end.y, end.z, r, g, b, 0.55f);
    }

    @Override
    public int requiredBuffers() {
        return 1; // lines
    }

    private static void drawCircle(VertexConsumer consumer, PoseStack poseStack,
            Vec3 center, Vec3 right, Vec3 up, double radius,
            float red, float green, float blue) {
        double step = Math.PI * 2 / SEGMENTS;
        for (int s = 0; s < SEGMENTS; s++) {
            double t0 = s * step;
            double t1 = t0 + step;
            double x0 = center.x + right.x * Math.cos(t0) * radius + up.x * Math.sin(t0) * radius;
            double y0 = center.y + right.y * Math.cos(t0) * radius + up.y * Math.sin(t0) * radius;
            double z0 = center.z + right.z * Math.cos(t0) * radius + up.z * Math.sin(t0) * radius;
            double x1 = center.x + right.x * Math.cos(t1) * radius + up.x * Math.sin(t1) * radius;
            double y1 = center.y + right.y * Math.cos(t1) * radius + up.y * Math.sin(t1) * radius;
            double z1 = center.z + right.z * Math.cos(t1) * radius + up.z * Math.sin(t1) * radius;
            lineSegment(consumer, poseStack, x0, y0, z0, x1, y1, z1, red, green, blue);
        }
    }

    /** 绘制一条线段（LINES 格式，POSITION_COLOR_NORMAL），默认 alpha。 */
    private static void lineSegment(VertexConsumer consumer, PoseStack poseStack,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            float r, float g, float b) {
        lineSegment(consumer, poseStack, x1, y1, z1, x2, y2, z2, r, g, b, LINE_ALPHA);
    }

    /** 绘制一条线段（LINES 格式，POSITION_COLOR_NORMAL）。 */
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