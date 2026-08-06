package com.rtsbuilding.rtsbuilding.client.render.pass;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 线模式建造的画笔状态机。
 *
 * <p>按住右键拖拽画线：右键按下记录起点进入 {@link Phase#DRAGGING}，
 * 拖动时通过 {@link #updateHover} 实时更新当前指向方块，
 * 右键松开时用 {@link #computeLinePositions} 取得起点到悬停位置的连续线段方块列表，
 * 交由批量放置（PLACE_BATCH）沿线段建造。</p>
 */
public final class LineBrushSelector {

    public enum Phase {
        IDLE,
        DRAGGING
    }

    private Phase phase = Phase.IDLE;
    private BlockPos start;
    private BlockPos hover;

    public Phase getPhase() {
        return phase;
    }

    public boolean isDragging() {
        return phase == Phase.DRAGGING;
    }

    @Nullable
    public BlockPos getStart() {
        return start;
    }

    @Nullable
    public BlockPos getHover() {
        return hover;
    }

    /** 记录画线起点并进入拖拽状态。 */
    public boolean start(BlockPos pos) {
        if (pos == null) return false;
        this.start = pos.immutable();
        this.phase = Phase.DRAGGING;
        return true;
    }

    public void updateHover(@Nullable BlockPos pos) {
        this.hover = pos == null ? null : pos.immutable();
    }

    /** 计算起点到当前悬停位置的连续线段方块列表（含两端）。 */
    public List<BlockPos> computeLinePositions() {
        if (start == null || hover == null) return List.of();
        return lineBetween(start, hover);
    }

    public void reset() {
        phase = Phase.IDLE;
        start = null;
        hover = null;
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
