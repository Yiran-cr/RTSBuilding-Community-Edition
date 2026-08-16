package com.rtsbuilding.rtsbuilding.network.storage;

/**
 * 存储页面排序方式。
 *
 * <p><b>协议/存档编码约定</b>：按显式 {@code id} 编解码（见 {@link #id()} 与 {@link #fromId(int)}），
 * 不依赖 ordinal。该值跨网络包与玩家存档 NBT 持久化，新增/删除值不得改变既有 id。
 * 修改后运行 <code>RtsStorageSortProtocolTest</code>。</p>
 */
public enum RtsStorageSort {
    QUANTITY(0),
    MOD(1),
    NAME(2);

    private final int id;

    RtsStorageSort(int id) {
        this.id = id;
    }

    /** 协议/存档编码值。稳定：与历史 ordinal 一致。 */
    public int id() {
        return id;
    }

    /**
     * 按协议 id 反解；未知/越界 id 返回 {@link #QUANTITY}（默认排序，保持向后兼容）。
     */
    public static RtsStorageSort fromId(int id) {
        return switch (id) {
            case 0 -> QUANTITY;
            case 1 -> MOD;
            case 2 -> NAME;
            default -> QUANTITY;
        };
    }
}
