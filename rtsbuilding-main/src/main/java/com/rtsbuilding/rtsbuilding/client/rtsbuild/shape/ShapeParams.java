package com.rtsbuilding.rtsbuilding.client.rtsbuild.shape;

/**
 * 形状的可变几何参数容器。
 *
 * <p>把各形状共用的扩展量参数（墙高/面宽/球半径/两端高度偏移）集中到单一可变对象，
 * 由画笔状态机（{@code LineBrushSelector}）持有，并通过 {@link #adjust(AdjustKind, int)}
 * 统一执行参数增减。参数语义与具体形状解耦，新增形状只需按字段含义自行解读。</p>
 */
public final class ShapeParams {

    /** 向上墙高（格），默认 1。 */
    private int wallHeight = 1;
    /** 向下墙高（格），默认 0。 */
    private int wallDown;

    /** 面宽度（沿垂直水平方向一侧），默认 1。 */
    private int faceWidth = 1;
    /** 面另一侧宽度，默认 0。 */
    private int faceDown;

    /** 球半径（格），默认 1。 */
    private int sphereRadius = 1;

    /** 起始点高度偏移（格）。 */
    private int startDy;
    /** 终止点高度偏移（格）。 */
    private int endDy;

    /** 重置为默认值（选新起点时调用）。 */
    public void reset() {
        this.wallHeight = 1;
        this.wallDown = 0;
        this.faceWidth = 1;
        this.faceDown = 0;
        this.sphereRadius = 1;
        this.startDy = 0;
        this.endDy = 0;
    }

    /**
     * 按调整类型增减对应参数。宽度/高度使用对称逻辑：先回收反侧再延伸正侧。
     *
     * @param delta 方向（+1/-1）
     */
    public void adjust(AdjustKind kind, int delta) {
        switch (kind) {
            case START_HEIGHT -> this.startDy += delta;
            case END_HEIGHT -> this.endDy += delta;
            case HEIGHT_EXTEND -> adjustHeight(delta);
            case WIDTH_EXTEND -> adjustWidth(delta);
            case FACE_BOTH_SIDES -> adjustBothSides(delta);
            case SPHERE_RADIUS -> this.sphereRadius = Math.max(1, Math.min(BuildShape.MAX_RADIUS, sphereRadius + delta));
            default -> { }
        }
    }

    /** 高度扩展量对称调整：先回收反侧再延伸正侧。 */
    private void adjustHeight(int delta) {
        if (delta > 0) {
            if (wallDown > 0) {
                this.wallDown--;
            } else if (wallHeight < BuildShape.MAX_EXTEND) {
                this.wallHeight++;
            }
        } else {
            if (wallHeight > 1) {
                this.wallHeight--;
            } else if (wallDown < BuildShape.MAX_EXTEND) {
                this.wallDown++;
            }
        }
    }

    /** 宽度扩展量对称调整：先回收反侧再延伸正侧。 */
    private void adjustWidth(int delta) {
        if (delta > 0) {
            if (faceDown > 0) {
                this.faceDown--;
            } else if (faceWidth < BuildShape.MAX_EXTEND) {
                this.faceWidth++;
            }
        } else {
            if (faceWidth > 1) {
                this.faceWidth--;
            } else if (faceDown < BuildShape.MAX_EXTEND) {
                this.faceDown++;
            }
        }
    }

    /** 两侧同时延展：左右对称加宽/收窄，走向线始终保持居中。 */
    private void adjustBothSides(int delta) {
        if (delta > 0) {
            if (faceWidth < BuildShape.MAX_EXTEND) this.faceWidth++;
            if (faceDown < BuildShape.MAX_EXTEND) this.faceDown++;
        } else {
            if (faceDown > 0) this.faceDown--;
            if (faceWidth > 1) this.faceWidth--;
        }
    }

    public int getWallHeight() {
        return wallHeight;
    }

    public int getWallDown() {
        return wallDown;
    }

    public int getFaceWidth() {
        return faceWidth;
    }

    public int getFaceDown() {
        return faceDown;
    }

    public int getSphereRadius() {
        return Math.max(1, sphereRadius);
    }

    public int getStartDy() {
        return startDy;
    }

    public int getEndDy() {
        return endDy;
    }
}
