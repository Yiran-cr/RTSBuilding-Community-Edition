package com.rtsbuilding.rtsbuilding.client.domain.state;

import net.minecraft.world.item.ItemStack;

public record StorageEntry(
        ItemStack stack,
        String itemId,
        long count,
        String namespace,
        String path,
        byte linkedMode
) {
    
    public static final byte MODE_BIDIRECTIONAL = 0;
    
    public static final byte MODE_EXTRACT_ONLY = 1;

    /**
     * @deprecated 玩家背包与存储条目已在服务端合并显示（合并条目数量加总），
     * 页面载荷中不再下发独立的背包条目；保留常量与判断方法仅作兼容。
     */
    @Deprecated
    public static final byte MODE_PLAYER_INVENTORY = 2;

    
    public boolean isBidirectional() {
        return linkedMode == MODE_BIDIRECTIONAL;
    }

    
    public boolean isExtractOnly() {
        return linkedMode == MODE_EXTRACT_ONLY;
    }

    
    public boolean isPlayerInventory() {
        return linkedMode == MODE_PLAYER_INVENTORY;
    }
}
