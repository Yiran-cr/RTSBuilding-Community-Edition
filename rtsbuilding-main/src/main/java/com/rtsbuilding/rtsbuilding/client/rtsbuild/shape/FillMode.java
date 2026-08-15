package com.rtsbuilding.rtsbuilding.client.rtsbuild.shape;

/**
 * 形状模式枚举：描述各立体/线性形状的建造范围。
 *
 * <ul>
 *   <li>{@link #SOLID} 实心：填充整个体积。</li>
 *   <li>{@link #HOLLOW} 空心：只建外壳一层薄壳（外表面）。</li>
 *   <li>{@link #FRAME} 框架：只建边缘骨架——体为 12 条棱边，圆柱为侧壁薄壳，
 *       球为赤道大圆环 + 两条子午线环，墙/面为矩形四边框。</li>
 *   <li>{@link #CONNECTED} 连接（线）：路径方块以共享面直角连接，不斜向相连。</li>
 *   <li>{@link #SEPARATED} 断点（线）：路径方块按 3D DDA 对角跳跃（原行为）。</li>
 * </ul>
 *
 * <p>不同形状支持的模式子集见 {@link #modesFor(BuildShape)}。</p>
 */
public enum FillMode {

    /** 实心：全填充。 */
    SOLID,
    /** 空心：外壳一层薄壳（体/球/圆柱）。 */
    HOLLOW,
    /** 框架：边缘骨架。 */
    FRAME,
    /** 连接：线路径直角连接（不允许斜向方块）。 */
    CONNECTED,
    /** 断点：线路径对角跳跃（原 3D DDA 行为）。 */
    SEPARATED;

    /**
     * 各形状支持的模式子集：
     * <ul>
     *   <li>体/圆柱/球：实心 / 空心 / 框架。</li>
     *   <li>墙/面：实心 / 框架。</li>
     *   <li>线：连接 / 断点。</li>
     *   <li>其他形状：仅实心。</li>
     * </ul>
     */
    public static FillMode[] modesFor(BuildShape shape) {
        return switch (shape) {
            case SOLID, CIRCLE, SPHERE -> new FillMode[]{SOLID, HOLLOW, FRAME};
            case WALL, FACE -> new FillMode[]{SOLID, FRAME};
            case LINE -> new FillMode[]{SEPARATED, CONNECTED};
            default -> new FillMode[]{SOLID};
        };
    }

    /** 各形状的默认模式：线默认断点，其余默认实心。 */
    public static FillMode defaultFor(BuildShape shape) {
        return shape == BuildShape.LINE ? SEPARATED : SOLID;
    }

    /** 在给定形状支持的模式子集中循环到下一个模式。 */
    public static FillMode nextFor(FillMode current, BuildShape shape) {
        FillMode[] modes = modesFor(shape);
        int idx = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == current) {
                idx = i;
                break;
            }
        }
        return modes[(idx + 1) % modes.length];
    }

    /** 中文显示名（下嵌层按钮与提示用），经 lang 语言文件本地化。 */
    public String label() {
        return net.minecraft.network.chat.Component.translatable(
                "ui.rtsbuilding.mode." + name().toLowerCase(java.util.Locale.ROOT)).getString();
    }
}
