package com.rtsbuilding.rtsbuilding.client.culling;

import com.rtsbuilding.rtsbuilding.client.render.RtsCullingRenderInvalidator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * 直线圆柱剔除的客户端全局状态（纯客户端视觉，不动服务端方块）。
 *
 * <p>当启用时，以「摄像机朝向（屏幕中心视线）」的直线为轴、以相机位置为轴中点、
 * 单侧长度={@link #getDistance()}（向前/向后各该距离）、半径={@link #getRadius()}
 * 的圆柱体内的方块在渲染时被隐藏（由 mixin 把对应位置表现为空气，
 * 见 {@code RenderChunkRegionMixin} 等）。圆柱随相机朝向实时变化，
 * 因此对区块网格的失效重建做 {@link #UPDATE_INTERVAL_MS} 节流：变化期间每 250ms
 * 最多重建一次受影响区域。</p>
 *
 * <p>全部字段 {@code volatile} 安全发布：Sodium/Embeddium 的后台网格线程可能并发
 * 读取 {@link #shouldCull} 与 {@link #snapshot()}。网格失效只在渲染帧（主线程）内
 * 触发 {@link RtsCullingRenderInvalidator}。</p>
 */
public final class RtsRayCylinderCullingState {

    // ==================== 范围常量 ====================

    /** 默认剔除距离（格）。 */
    public static final double DEFAULT_DISTANCE = 5.0D;
    /** 最小剔除距离（格）。 */
    public static final double MIN_DISTANCE = 3.0D;
    /** 最大剔除距离（格，与 CursorRaycaster.MAX_REACH 一致）。 */
    public static final double MAX_DISTANCE = 128.0D;

    /** 默认圆柱半径（格）。 */
    public static final double DEFAULT_RADIUS = 3.0D;
    /** 最小圆柱半径（格）。 */
    public static final double MIN_RADIUS = 1.0D;
    /** 最大圆柱半径（格）。 */
    public static final double MAX_RADIUS = 32.0D;

    /** 射线变化时的网格失效节流间隔（毫秒）：相机/准星每帧变化，不能每帧重建区块。 */
    private static final long UPDATE_INTERVAL_MS = 250L;

    /**
     * 触发网格重建的轴向位移阈值（格）：包围盒任一方向端面移动达到该值才重建。
     * 相机插值/滚轮微调会产生亚格位移，无阈值时转动视角每 250ms 全量重建一个
     * 大圆柱区域（默认约 7×7 区块），导致持续卡顿。
     */
    private static final double REBUILD_SHIFT_THRESHOLD = 1.0D;

    // ==================== 状态 ====================

    private static volatile boolean enabled;
    private static volatile double distance = DEFAULT_DISTANCE;
    private static volatile double radius = DEFAULT_RADIUS;

    /** 当前生效的圆柱快照（射线 + 参数），为 null 表示尚未建立（未开启/世界刚切换）。 */
    private static volatile Snapshot snapshot;

    /** 上次已失效重建的区域（节流期间不变则跳过重建）。 */
    private static volatile AABB lastInvalidated;

    /** 本次开启期间所有失效过的区域（关闭时逐一恢复，防止相机移动经过的中间位置残留隐藏）。 */
    private static final List<AABB> EVER_INVALIDATED = new ArrayList<>();

    /** 上次失效时刻（毫秒）。 */
    private static volatile long lastUpdateMs;

    /** 快照所属的客户端世界（弱引用，世界切换时自动失效，防跨世界泄漏）。 */
    private static volatile WeakReference<ClientLevel> snapshotLevel;

    private RtsRayCylinderCullingState() {
    }

    // ==================== 只读查询 ====================

    public static boolean isEnabled() {
        return enabled;
    }

    public static double getDistance() {
        return distance;
    }

    public static double getRadius() {
        return radius;
    }

    /** 当前快照（供渲染 pass 绘制圆柱预览；无快照返回 null）。 */
    public static Snapshot snapshot() {
        return snapshot;
    }

    /**
     * 渲染剔除查询（供 mixin / Flywheel 适配在任意线程调用）。
     *
     * @param pos 待判定方块位置
     * @return 位于当前射线圆柱体内且剔除已启用返回 true
     */
    public static boolean shouldCull(BlockPos pos) {
        if (!enabled || pos == null) {
            return false;
        }
        Snapshot snap = snapshot;
        return snap != null && snap.contains(pos);
    }

    // ==================== 修改 ====================

    /** 切换剔除开关（顶栏按钮 / Y 键共用入口）。 */
    public static void toggle() {
        setEnabled(!enabled);
    }

    /**
     * 设置剔除开关。关闭时恢复开启期间全部被失效过的区域（含中间移动位置，
     * 避免网格残留隐藏）；开启后由下一帧 {@link #updateFromRay} 建立快照并初始化重建。
     */
    public static void setEnabled(boolean value) {
        if (enabled == value) {
            return;
        }
        enabled = value;
        AABB old = lastInvalidated;
        lastInvalidated = null;
        if (!value) {
            Snapshot snap = snapshot;
            snapshot = null;
            invalidate(old);
            if (snap != null) {
                invalidate(snap.bounds());
            }
            for (AABB area : EVER_INVALIDATED) {
                if (!area.equals(old) && !area.equals(snap != null ? snap.bounds() : null)) {
                    invalidate(area);
                }
            }
            EVER_INVALIDATED.clear();
        }
    }

    /**
     * 设置剔除距离（格，单侧长度，向前/向后各该距离），
     * 范围收敛到 {@link #MIN_DISTANCE} ~ {@link #MAX_DISTANCE}。
     * 变化会使当前快照立即重建（新旧区域一并失效）。
     */
    public static void setDistance(double value) {
        double clamped = Mth.clamp(value, MIN_DISTANCE, MAX_DISTANCE);
        if (Double.compare(distance, clamped) == 0) {
            return;
        }
        distance = clamped;
        applyParameterChange();
    }

    /**
     * 设置圆柱半径（格），范围收敛到 {@link #MIN_RADIUS} ~ {@link #MAX_RADIUS}。
     * 变化会使当前快照立即重建（新旧区域一并失效）。
     */
    public static void setRadius(double value) {
        double clamped = Mth.clamp(value, MIN_RADIUS, MAX_RADIUS);
        if (Double.compare(radius, clamped) == 0) {
            return;
        }
        radius = clamped;
        applyParameterChange();
    }

    /**
     * 渲染帧内以当前摄像机朝向射线更新圆柱快照（由 {@code CylinderCullingPreviewPass} 调用）。
     *
     * <p>世界切换（快照所属 level 与当前不一致）或射线变化超过节流间隔时，
     * 失效旧区域并重建新区域；节流窗口内的微小移动只更新快照、不重建网格。</p>
     *
     * @param origin    射线起点（相机位置）
     * @param direction 射线方向（摄像机视线方向，已归一化）
     */
    public static void updateFromRay(Vec3 origin, Vec3 direction) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || origin == null || direction == null) {
            return;
        }
        // 世界切换检测：快照属于旧世界时整体失效（旧世界正在卸载，不再重建旧区域）
        ClientLevel level = mc.level;
        WeakReference<ClientLevel> previousLevel = snapshotLevel;
        if (previousLevel == null || previousLevel.get() != level) {
            snapshotLevel = new WeakReference<>(level);
            lastInvalidated = null;
            snapshot = null;
            EVER_INVALIDATED.clear();
            if (!enabled) {
                return;
            }
        } else if (!enabled) {
            return;
        }

        Vec3 normalized = direction.normalize();
        AABB nextAabb = RtsRayCylinder.axisAlignedBounds(origin, normalized, distance, radius);
        if (nextAabb == null) {
            return;
        }
        AABB previous = lastInvalidated;
        Snapshot next = new Snapshot(origin, normalized, distance, radius, nextAabb);
        long now = System.currentTimeMillis();
        boolean initial = previous == null;
        // 亚格位移（相机插值/微调）不触发重建，须任一端面移动达到阈值
        boolean moved = previous == null || movedBeyondThreshold(nextAabb, previous);
        boolean throttled = now - lastUpdateMs < UPDATE_INTERVAL_MS;

        snapshot = next;
        if (initial || moved && !throttled) {
            // 失效新区域（首帧）或新旧区域（位移/参数变化达到节流窗口）
            lastUpdateMs = now;
            lastInvalidated = nextAabb;
            invalidate(previous);
            invalidate(nextAabb);
        }
    }

    // ==================== 内部 ====================

    /** 距离/半径变化：立即重建当前快照区域（不等待节流窗口）。 */
    private static void applyParameterChange() {
        Snapshot snap = snapshot;
        AABB previous = lastInvalidated;
        if (snap == null) {
            lastInvalidated = null;
            return;
        }
        AABB nextAabb = RtsRayCylinder.axisAlignedBounds(snap.origin(), snap.direction(), distance, radius);
        if (nextAabb == null) {
            return;
        }
        snapshot = new Snapshot(snap.origin(), snap.direction(), distance, radius, nextAabb);
        lastUpdateMs = 0L; // 强制下一帧 updateFromRay 通过节流检查
        lastInvalidated = nextAabb;
        invalidate(previous);
        invalidate(nextAabb);
    }

    /** 线程安全的网格失效：内部兜底判空并适配 Sodium/Embeddium 重建入口。 */
    private static void invalidate(AABB aabb) {
        if (aabb == null) {
            return;
        }
        // 记录本次开启期间失效过的区域，关闭时统一恢复（去重）
        boolean recorded = false;
        for (AABB area : EVER_INVALIDATED) {
            if (area.equals(aabb)) {
                recorded = true;
                break;
            }
        }
        if (!recorded) {
            EVER_INVALIDATED.add(aabb);
        }
        BlockPos min = new BlockPos((int) Math.floor(aabb.minX), (int) Math.floor(aabb.minY), (int) Math.floor(aabb.minZ));
        BlockPos max = new BlockPos((int) Math.ceil(aabb.maxX), (int) Math.ceil(aabb.maxY), (int) Math.ceil(aabb.maxZ));
        RtsCullingRenderInvalidator.markBlocksDirty(min, max);
        // Flywheel 方块实体 Visual 同步（已存在的机械 Visual 需要主动队列移除/恢复）
        Minecraft mc = Minecraft.getInstance();
        if (mc.level instanceof ClientLevel clientLevel) {
            RtsFlywheelCullingCompat.syncBounds(clientLevel, min, max);
        }
    }

    /**
     * 包围盒是否发生达到阈值的实质位移：任一方向端面坐标差 ≥ {@link #REBUILD_SHIFT_THRESHOLD}。
     * 相机亚格移动（插值/滚轮微调）不视为移动，避免频繁全区域重建。
     */
    static boolean movedBeyondThreshold(AABB a, AABB b) {
        if (a == null || b == null) {
            return true;
        }
        double t = REBUILD_SHIFT_THRESHOLD;
        return Math.abs(a.minX - b.minX) >= t || Math.abs(a.minY - b.minY) >= t
                || Math.abs(a.minZ - b.minZ) >= t
                || Math.abs(a.maxX - b.maxX) >= t || Math.abs(a.maxY - b.maxY) >= t
                || Math.abs(a.maxZ - b.maxZ) >= t;
    }

    /**
     * 圆柱快照：射线 + 参数 + 预计算包围盒的不可变记录。
     * 渲染与剔除查询共用同一份，避免两处各自重复计算。
     */
    public record Snapshot(Vec3 origin, Vec3 direction, double distance, double radius, AABB bounds) {
        /** 方块中心是否在圆柱体内（快照已预计算包围盒快速排除）。 */
        boolean contains(BlockPos pos) {
            if (pos == null) {
                return false;
            }
            AABB b = bounds;
            double px = pos.getX() + 0.5D;
            double py = pos.getY() + 0.5D;
            double pz = pos.getZ() + 0.5D;
            if (px < b.minX || px > b.maxX || py < b.minY || py > b.maxY || pz < b.minZ || pz > b.maxZ) {
                return false;
            }
            return RtsRayCylinder.contains(origin, direction, distance, radius, pos);
        }
    }
}