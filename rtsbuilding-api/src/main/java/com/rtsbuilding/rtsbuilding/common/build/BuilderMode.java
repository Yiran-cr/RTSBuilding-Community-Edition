package com.rtsbuilding.rtsbuilding.common.build;

/**
 * 建造者模式枚举，定义 RTS 模式下玩家的操作模式。
 *
 * <p>枚举值序列化持久化到玩家 NBT（{@code valueOf(name)}），新增值时不得
 * 重命名既有值。BUILD / BLUEPRINT 为当前界面使用的模式；旧值保留兼容。
 *
 * <p>网络跨端编解码按显式 {@code id}（见 {@link #id()} 与 {@link #fromId(int)}），
 * 不依赖 ordinal——新建值分配 {@code id = 当前最大 id + 1}，禁止改变既有 id。
 * 修改后运行 <code>BuilderModeProtocolTest</code>。
 *
 * <p>放置于 api 模块（包名保持 common.build）：{@link RtsSessionQueryAPI}
 * 引用本类型，而 {@code rtsbuilding-common} 已依赖 api —— 避免模块循环依赖。
 */
public enum BuilderMode {
    /** 关闭模式 —— 不执行任何 RTS 操作，恢复原版交互行为 */
    OFF(0),
    /** 平移选择模式 —— 用于移动相机视口或选择区域 */
    SELECT_PAN(1),
    /** 链接存储模式 —— 将容器方块链接到远程存储网络 */
    LINK_STORAGE(2),
    /** 漏斗模式 —— 配置物品输入/输出通道 */
    FUNNEL(3),
    /** 互动模式 —— 远程交互（如打开容器 GUI） */
    INTERACT(4),
    /** 旋转模式 —— 旋转已放置的方块或蓝图 */
    ROTATE(5),
    /** 建造模式 —— 方块放置/挖掘等建造操作 */
    BUILD(6),
    /** 蓝图模式 —— 蓝图放置/扫描 */
    BLUEPRINT(7);

    private final int id;

    BuilderMode(int id) {
        this.id = id;
    }

    /** 网络协议编码值。稳定：与历史 ordinal 一致。 */
    public int id() {
        return id;
    }

    /** 按协议 id 反解；未知 id 返回 {@code null}（解码越界防恶意包）。 */
    public static BuilderMode fromId(int id) {
        return switch (id) {
            case 0 -> OFF;
            case 1 -> SELECT_PAN;
            case 2 -> LINK_STORAGE;
            case 3 -> FUNNEL;
            case 4 -> INTERACT;
            case 5 -> ROTATE;
            case 6 -> BUILD;
            case 7 -> BLUEPRINT;
            default -> null;
        };
    }
}
