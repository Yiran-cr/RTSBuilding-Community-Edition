package com.rtsbuilding.rtsbuilding.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rtsbuilding.rtsbuilding.common.entity.RtsDroneEntity;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsDroneBeamPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 无人机建造/破坏激光光束渲染器（客户端，服务端广播给"其他玩家"）。
 *
 * <p>光束以"发射 → 命中 → 收回"的一次性动画呈现，强调发射感：
 * <ul>
 *   <li><b>发射阶段</b>：一个光球（前尖）从起点沿射线快速飞向终点，拖出渐长的激光尾迹
 *       （白芯 + 彩色光晕两层），体现从一端"射出"的动态。</li>
 *   <li><b>命中阶段</b>：光束到达终点后全亮保持，目标端出现命中闪光——此时方块已完成
 *       放置/破坏，光束据此自动收回。</li>
 *   <li><b>收回阶段</b>：整条光束整体渐隐消失。</li>
 * </ul></p>
 *
 * <p>光束两端持续追踪：起点优先用客户端实时追踪到的无人机实体（摄像头位置 = 无人机位置 +
 * {@link RtsDroneEntity#CAMERA_PART_OFFSET}），实体未加载时回退到发包瞬间记录的起点；
 * 终点锁定目标方块中心。建造蓝光从摄像头射向方块，破坏红光从方块射向摄像头。</p>
 *
 * <p>本渲染器挂在 {@link RenderLevelStageEvent} 上独立执行，不依赖 RTS 客户端内核，
 * 因此收到光束包的普通玩家（非 RTS 模式）也能看到。</p>
 */
public final class DroneBeamRenderer {

    public static final DroneBeamRenderer INSTANCE = new DroneBeamRenderer();

    /** 发射阶段时长（毫秒）：光球从起点飞到终点。 */
    private static final long LAUNCH_MS = 280L;
    /** 命中保持时长（毫秒）：全亮并显示命中闪光。 */
    private static final long HOLD_MS = 220L;
    /** 收回（渐隐）时长（毫秒）。 */
    private static final long FADE_MS = 200L;
    /** 光束总存活时长（毫秒）。 */
    private static final long TOTAL_LIFETIME_MS = LAUNCH_MS + HOLD_MS + FADE_MS;

    /** 前端光球半长（格）。 */
    private static final float MUZZLE_HALF = 0.16F;
    /** 目标端命中闪光半长（格）。 */
    private static final float HIT_FLASH_HALF = 0.26F;
    /** 激光侧向偏移（格，形成光束宽度）。 */
    private static final double BEAM_OFFSET = 0.035D;

    /** 光束缓冲区容量（字节）。 */
    private static final int BUFFER_CAPACITY = 1024 * 512;

    /** 白色核心颜色。 */
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};

    /** 建造蓝光颜色（RGB，0-1）。 */
    private static final float[] PLACE_COLOR = {0.30F, 0.70F, 1.0F};

    /** 破坏红光颜色（RGB，0-1）。 */
    private static final float[] BREAK_COLOR = {1.0F, 0.32F, 0.30F};

    /** 光束渲染类型：半透明加法混合、无剔除，模拟发光激光；线条按世界坐标绘制。 */
    private static final RenderType BEAM_TYPE = RenderType.create(
            "rtsbuilding_drone_beam",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.DEBUG_LINES, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /** 活跃光束列表（网络回调与渲染均在客户端主线程执行，无需并发容器）。 */
    private final List<Beam> beams = new ArrayList<>();

    private DroneBeamRenderer() {
    }

    /**
     * 注册一条光束（由网络包处理回调调用）。
     */
    public void addBeam(S2CRtsDroneBeamPayload payload) {
        this.beams.add(new Beam(payload.droneEntityId(), payload.targetPos(), payload.place(),
                new Vec3(payload.originX(), payload.originY(), payload.originZ())));
    }

    /**
     * 在 LevelRenderer 阶段渲染所有光束，并清理播放完成的光束。
     */
    public void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || this.beams.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        Vec3 camPos = event.getCamera().getPosition();

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // 世界坐标渲染：poseStack 平移到世界原点后再画线（与相机相对坐标一致）
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        PoseStack.Pose pose = poseStack.last();

        ByteBufferBuilder backing = new ByteBufferBuilder(BUFFER_CAPACITY);
        BufferBuilder builder = new BufferBuilder(backing, VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        Iterator<Beam> it = this.beams.iterator();
        while (it.hasNext()) {
            Beam beam = it.next();
            long age = now - beam.createdAt;
            if (age >= TOTAL_LIFETIME_MS) {
                it.remove();
                continue;
            }
            Vec3 origin = beam.resolveOrigin(mc.level);
            if (origin == null) {
                continue;
            }
            Vec3 target = Vec3.atCenterOf(beam.targetPos);
            if (beam.place) {
                drawBeam(builder, pose, origin, target, PLACE_COLOR, age);
            } else {
                drawBeam(builder, pose, target, origin, BREAK_COLOR, age);
            }
        }

        // 提交渲染（draw 为即时调用，结束后释放网格与缓冲）
        MeshData mesh = builder.build();
        if (mesh != null) {
            BEAM_TYPE.draw(mesh);
            mesh.close();
        }
        backing.close();

        poseStack.popPose();
    }

    /**
     * 绘制一条处于指定播放时刻的激光：从 {@code from} 射向 {@code to}。
     *
     * @param age   光束已播放时长（毫秒），驱动发射/命中/收回三阶段
     */
    private static void drawBeam(BufferBuilder builder, PoseStack.Pose pose, Vec3 from, Vec3 to,
                                 float[] color, long age) {
        Vec3 dir = to.subtract(from);
        double fullLen = dir.length();
        if (fullLen < 1.0E-3D) {
            return;
        }
        Vec3 unit = dir.scale(1.0D / fullLen);

        // 阶段推进：当前可见的激光长度与整体透明度
        double visibleLen;
        float alpha;
        if (age < LAUNCH_MS) {
            double k = age / (double) LAUNCH_MS;
            visibleLen = fullLen * easeOutCubic(k);
            alpha = 1.0F;
        } else if (age < LAUNCH_MS + HOLD_MS) {
            visibleLen = fullLen;
            alpha = 1.0F;
        } else {
            visibleLen = fullLen;
            alpha = Math.max(0.0F, 1.0F - (float) (age - LAUNCH_MS - HOLD_MS) / (float) FADE_MS);
        }
        if (visibleLen < 0.05D || alpha <= 0.01F) {
            return;
        }

        Vec3 tip = from.add(unit.scale(visibleLen));

        // 激光尾迹：外侧彩晕 + 内侧白色核心
        drawLaserTail(builder, pose, from, tip, unit, color, alpha);

        // 前端光球（发射感的核心：光球拖着尾迹前进）
        drawMuzzleGlow(builder, pose, tip, color, alpha);

        // 命中阶段：目标端出现命中闪光
        if (age >= LAUNCH_MS && age < LAUNCH_MS + HOLD_MS) {
            float flashAlpha = alpha;
            if (age >= LAUNCH_MS + HOLD_MS - 80L) {
                flashAlpha *= (float) (LAUNCH_MS + HOLD_MS - age) / 80.0F;
            }
            drawHitFlash(builder, pose, to, flashAlpha);
        }
    }

    /**
     * 激光尾迹：沿射线方向在起点与前端之间绘制两层线条——外侧半透明彩色光晕、
     * 内侧不透明白色核心，并沿垂直方向略作偏移形成光束宽度。
     */
    private static void drawLaserTail(BufferBuilder builder, PoseStack.Pose pose, Vec3 from, Vec3 tip,
                                      Vec3 unit, float[] color, float alpha) {
        Vec3 side = new Vec3(-unit.z, 0.0D, unit.x);
        double sideLen = side.lengthSqr();
        if (sideLen < 1.0E-6D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            side = side.scale(1.0D / Math.sqrt(sideLen));
        }
        Vec3 up = side.cross(unit);

        Vec3 start = from;
        Vec3 end = tip;
        Vec3 dir = end.subtract(start);
        double len = dir.length();
        if (len < 0.05D) {
            return;
        }
        // 白色核心线（略短，收在光球后）
        Vec3 coreEnd = start.add(dir.scale(0.94D));
        line(builder, pose, start, coreEnd, WHITE, alpha * 0.9F);

        // 彩色光晕线：中心 + 两侧偏移，形成"有宽度"的发光光束
        Vec3 endSide1 = end.add(side.scale(BEAM_OFFSET)).add(up.scale(BEAM_OFFSET));
        Vec3 startSide1 = start.add(side.scale(BEAM_OFFSET)).add(up.scale(BEAM_OFFSET));
        line(builder, pose, startSide1, endSide1, color, alpha * 0.5F);

        Vec3 endSide2 = end.subtract(side.scale(BEAM_OFFSET)).subtract(up.scale(BEAM_OFFSET));
        Vec3 startSide2 = start.subtract(side.scale(BEAM_OFFSET)).subtract(up.scale(BEAM_OFFSET));
        line(builder, pose, startSide2, endSide2, color, alpha * 0.5F);
    }

    /**
     * 前端光球：光束最前端的亮斑（十字光晕 + 白色核心点），随发射动画沿射线前进。
     */
    private static void drawMuzzleGlow(BufferBuilder builder, PoseStack.Pose pose, Vec3 tip,
                                       float[] color, float alpha) {
        // 彩色光晕十字（沿世界坐标轴，垂直方向拉长更醒目）
        float s = MUZZLE_HALF;
        line(builder, pose, tip.add(0, s * 1.5F, 0), tip.add(0, -s * 1.5F, 0), color, alpha * 0.9F);
        line(builder, pose, tip.add(s, 0, 0), tip.add(-s, 0, 0), color, alpha * 0.7F);
        line(builder, pose, tip.add(0, 0, s), tip.add(0, 0, -s), color, alpha * 0.7F);
        // 白色核心亮点
        float s2 = 0.06F;
        line(builder, pose, tip.add(s2, 0, 0), tip.add(-s2, 0, 0), WHITE, alpha);
        line(builder, pose, tip.add(0, s2, 0), tip.add(0, -s2, 0), WHITE, alpha);
    }

    /**
     * 命中闪光：激光命中目标端时出现的十字爆发光晕（白色核心 + 彩色外环）。
     */
    private static void drawHitFlash(BufferBuilder builder, PoseStack.Pose pose, Vec3 center,
                                     float alpha) {
        float s = HIT_FLASH_HALF;
        line(builder, pose, center.add(0, s * 1.5F, 0), center.add(0, -s * 1.5F, 0), WHITE, alpha * 0.9F);
        line(builder, pose, center.add(s, 0, 0), center.add(-s, 0, 0), WHITE, alpha * 0.9F);
        line(builder, pose, center.add(0, 0, s), center.add(0, 0, -s), WHITE, alpha * 0.9F);
        line(builder, pose, center.add(s * 1.8F, 0, 0), center.add(-s * 1.8F, 0, 0), WHITE, alpha * 0.4F);
        line(builder, pose, center.add(0, s * 1.8F, 0), center.add(0, -s * 1.8F, 0), WHITE, alpha * 0.4F);
    }

    private static void line(BufferBuilder builder, PoseStack.Pose pose, Vec3 a, Vec3 b,
                             float[] color, float alpha) {
        vertex(builder, pose, a, color, alpha);
        vertex(builder, pose, b, color, alpha);
    }

    private static void vertex(BufferBuilder builder, PoseStack.Pose pose, Vec3 pos,
                               float[] color, float alpha) {
        VertexConsumer consumer = builder.addVertex(pose, (float) pos.x, (float) pos.y, (float) pos.z);
        consumer.setColor(color[0], color[1], color[2], alpha);
    }

    /** 缓出三次曲线：开头加速、结尾减速，让光球"射出"更自然。 */
    private static double easeOutCubic(double k) {
        double m = 1.0D - k;
        return 1.0D - m * m * m;
    }

    /**
     * 单条光束条目：绑定无人机实体 ID 与目标方块，实时追踪端点并驱动发射动画。
     */
    private static final class Beam {
        final int droneEntityId;
        final BlockPos targetPos;
        final boolean place;
        final Vec3 fallbackOrigin;
        final long createdAt = System.currentTimeMillis();

        Beam(int droneEntityId, BlockPos targetPos, boolean place, Vec3 fallbackOrigin) {
            this.droneEntityId = droneEntityId;
            this.targetPos = targetPos.immutable();
            this.place = place;
            this.fallbackOrigin = fallbackOrigin;
        }

        /**
         * 解析光束起点：优先使用客户端实时追踪到的无人机实体位置（摄像头 = 实体位置 + 偏移），
         * 实体未加载时回退到发包瞬间记录的起点。
         */
        Vec3 resolveOrigin(Level level) {
            Entity drone = level.getEntity(this.droneEntityId);
            if (drone != null && drone.isAlive()) {
                return drone.position().add(RtsDroneEntity.CAMERA_PART_OFFSET);
            }
            return this.fallbackOrigin;
        }
    }
}
