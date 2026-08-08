package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 线/墙模式建造的画笔状态机（点选式）。
 *
 * <p>右键单击选择起点进入 {@link Phase#PICK_START}，移动鼠标实时预览线段，
 * 再次右键单击选择终点：线模式进入 {@link Phase#ADJUST}（可微调两端高度后确认建造），
 * 墙模式进入 {@link Phase#HEIGHT}（滚轮调整墙高后确认建造）。</p>
 *
 * <p>高度调整：画线/确认阶段滚轮调终止点高度、Ctrl+滚轮调起始点高度；
 * 墙模式阶段二滚轮上下对称调整墙高。ESC 逐级取消：ADJUST/HEIGHT→PICK_START→完全取消。</p>
 */
public final class LineBrushSelector {

    public enum Phase {
        IDLE,
        /** 起点已选择，移动鼠标实时预览，右键选择终点 */
        PICK_START,
        /** 阶段一（线）：终点已选择，滚轮微调两端高度，右键确认建造 */
        ADJUST,
        /** 阶段二（墙）：终点已选择，滚轮调整墙高，右键确认建造 */
        HEIGHT
    }

    /** 墙高上限（格，向上/向下分别限制）。 */
    public static final int MAX_WALL_HEIGHT = 64;

    /** 向上墙高（格），默认 1。 */
    private int wallHeight = 1;

    /** 向下墙高（格），默认 0（墙高阶段滚轮向下滚动时向下延伸）。 */
    private int wallDown;

    /** 起始点高度偏移（Ctrl+滚轮调整，格）。 */
    private int startDy;
    /** 终止点高度偏移（滚轮直接调整，格）。 */
    private int endDy;

    private Phase phase = Phase.IDLE;
    private BlockPos start;
    private BlockPos hover;

    /** 当前是否为墙模式（选择起点时确定，影响后续状态流转与方块生成）。 */
    private boolean wallActive;

    public Phase getPhase() {
        return phase;
    }

    /** 是否处于起点选择/画线预览阶段。 */
    public boolean isPicking() {
        return phase == Phase.PICK_START;
    }

    /** 是否处于阶段一（线：微调两端高度待确认）。 */
    public boolean isAdjusting() {
        return phase == Phase.ADJUST;
    }

    /** 是否处于阶段二（墙：调整墙高待确认）。 */
    public boolean isHeightAdjusting() {
        return phase == Phase.HEIGHT;
    }

    /** 是否为墙模式。 */
    public boolean isWallActive() {
        return wallActive;
    }

    /** 当前向上墙高（格）。 */
    public int getWallHeight() {
        return wallHeight;
    }

    /** 当前向下墙高（格）。 */
    public int getWallDown() {
        return wallDown;
    }

    /** 是否处于点选/任一确认的活跃阶段（用于渲染预览与状态清理）。 */
    public boolean isActive() {
        return phase == Phase.PICK_START || phase == Phase.ADJUST
                || phase == Phase.HEIGHT;
    }

    @Nullable
    public BlockPos getStart() {
        return start;
    }

    @Nullable
    public BlockPos getHover() {
        return hover;
    }

    /** 右键单击选择起点并进入画线预览（wall 为 true 表示墙模式）。 */
    public boolean start(BlockPos pos, boolean wall) {
        if (pos == null) return false;
        this.start = pos.immutable();
        this.wallActive = wall;
        this.wallHeight = 1;
        this.wallDown = 0;
        this.startDy = 0;
        this.endDy = 0;
        this.phase = Phase.PICK_START;
        return true;
    }

    /** 移动鼠标更新预览终点（仅起点选择阶段跟随）。 */
    public void updateHover(@Nullable BlockPos pos) {
        if (phase != Phase.PICK_START) {
            // 已选终点：锁定预览，不随鼠标移动改变
            return;
        }
        this.hover = pos == null ? null : pos.immutable();
    }

    /** 右键单击选择终点：线模式进入微调确认，墙模式进入墙高调整。 */
    public boolean pickEnd() {
        if (phase != Phase.PICK_START || start == null || hover == null) {
            return false;
        }
        phase = wallActive ? Phase.HEIGHT : Phase.ADJUST;
        return true;
    }

    /** 计算起点到当前悬停位置的连续线段方块列表（含两端高度偏移，不含墙高扩展）。 */
    public List<BlockPos> computeLinePositions() {
        if (start == null || hover == null) return List.of();
        // 应用起始点高度偏移（Ctrl+滚轮）
        int sy = start.getY() + startDy;
        BlockPos s = new BlockPos(start.getX(), sy, start.getZ());
        BlockPos e;
        if (RtsKeyMappings.isLineFlatDown()) {
            // 按住 V 键：强制平直，终点高度锁定到起点实际高度
            e = new BlockPos(hover.getX(), sy, hover.getZ());
        } else {
            // 应用终止点高度偏移（滚轮）
            e = new BlockPos(hover.getX(), hover.getY() + endDy, hover.getZ());
        }
        return lineBetween(s, e);
    }

    /**
     * 计算墙体方块列表：走向线上的每个方块，从向下 {@link #wallDown} 格到向上
     * {@link #wallHeight} 格（含上下限），即以走向线为中心可向上/向下双向建造。
     */
    public List<BlockPos> computeWallPositions() {
        List<BlockPos> line = computeLinePositions();
        if (line.isEmpty()) return List.of();
        List<BlockPos> result = new ArrayList<>(line.size() * (wallHeight + wallDown));
        for (BlockPos p : line) {
            for (int dy = -wallDown; dy < wallHeight; dy++) {
                result.add(new BlockPos(p.getX(), p.getY() + dy, p.getZ()));
            }
        }
        return result;
    }

    /** Ctrl+滚轮调整起始点高度（仅画线/微调阶段，方向 +1/-1）。 */
    public void adjustStartHeight(int delta) {
        if (phase != Phase.PICK_START && phase != Phase.ADJUST) return;
        this.startDy += delta;
    }

    /** 滚轮调整终止点高度（仅画线/微调阶段，方向 +1/-1）。 */
    public void adjustEndHeight(int delta) {
        if (phase != Phase.PICK_START && phase != Phase.ADJUST) return;
        this.endDy += delta;
    }

    /**
     * 滚轮调整墙高度（仅阶段二生效），向上/向下使用同一套对称逻辑：
     * 向上滚时优先回收向下的延伸量，回收完再向上延伸；向下滚时优先回收向上的
     * 延伸量，回收完再向下延伸。
     */
    public void adjustWallHeight(int delta) {
        if (phase != Phase.HEIGHT) return;
        if (delta > 0) {
            if (wallDown > 0) {
                this.wallDown--;
            } else if (wallHeight < MAX_WALL_HEIGHT) {
                this.wallHeight++;
            }
        } else {
            if (wallHeight > 1) {
                this.wallHeight--;
            } else if (wallDown < MAX_WALL_HEIGHT) {
                this.wallDown++;
            }
        }
    }

    /**
     * ESC 逐级取消：ADJUST/HEIGHT→PICK_START→完全取消。
     * 每按一次 ESC 只回退一个阶段，不会一次性取消整个流程。
     */
    public void cancelStage() {
        switch (phase) {
            case ADJUST, HEIGHT -> phase = Phase.PICK_START;
            default -> reset();
        }
    }

    public void reset() {
        phase = Phase.IDLE;
        start = null;
        hover = null;
        wallActive = false;
        wallHeight = 1;
        wallDown = 0;
        startDy = 0;
        endDy = 0;
    }

    /** 3D DDA 插值线段：返回从 a 到 b 沿最长轴均匀分布的连续方块（含两端）。 */
    public static List<BlockPos> lineBetween(BlockPos a, BlockPos b) {
        List<BlockPos> result = new ArrayList<>();
        if (a == null || b == null) return result;
        int x0 = a.getX(), y0 = a.getY(), z0 = a.getZ();
        int x1 = b.getX(), y1 = b.getY(), z1 = b.getZ();
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int dz = Math.abs(z1 - z0);
        int steps = Math.max(dx, Math.max(dy, dz));
        if (steps == 0) {
            result.add(a.immutable());
            return result;
        }
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        double stepX = (double) dx / steps * sx;
        double stepY = (double) dy / steps * sy;
        double stepZ = (double) dz / steps * sz;
        double x = x0, y = y0, z = z0;
        result.add(a.immutable());
        for (int i = 1; i <= steps; i++) {
            x += stepX;
            y += stepY;
            z += stepZ;
            result.add(new BlockPos((int) Math.round(x), (int) Math.round(y), (int) Math.round(z)));
        }
        return result;
    }
}
