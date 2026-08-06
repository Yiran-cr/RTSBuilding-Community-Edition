package com.rtsbuilding.rtsbuilding.server.service.transfer;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Shared constants and helper utility methods for the transfer sub-package.
 *
 * <p>This class provides constants and utility methods shared by multiple classes in the transfer sub-package.
 * Package-private by design, not exposed externally.
 * All methods are {@code static}, the class itself is a non-instantiable utility class.
 *
 * <p><b>Constants:</b>
 * <ul>
 *   <li>{@link #PLAYER_HOTBAR_SLOT_COUNT} = {@value #PLAYER_HOTBAR_SLOT_COUNT} — Number of player hotbar slots</li>
 *   <li>{@link #SHIFT_IMPORT_MAX_CRAFT_ITERATIONS} = {@value #SHIFT_IMPORT_MAX_CRAFT_ITERATIONS} —
 *       Maximum auto-craft iterations per Shift+Import</li>
 * </ul>
 *
 * <p><b>Utility methods:</b>
 * <ul>
 *   <li>{@link #movesLinkedQuickMoveToPlayerInventory(AbstractContainerMenu)} —
 *       Determines if quick move from linked storage should go to player inventory (instead of menu slots);
 *       Returns {@code true} for {@code InventoryMenu} or any {@code CraftingMenu} (incl. RTS craft terminal)</li>
 *   <li>{@link #clampHotbarSlot(int)} — Clamps hotbar slot index to [0, 8] range</li>
 * </ul>
 *
 * <p><b>Player inventory bounds:</b> {@code getPlayerMainInventoryStart/EndExclusive} 与
 * {@code shouldIncludePlayerMainInventoryInStorageView} 的统一实现在
 * {@link com.rtsbuilding.rtsbuilding.server.service.page.RtsPageSharedHelpers}，
 * 转移子包直接复用，避免双份实现（R1 修复）。
 */
final class RtsTransferUtils {
    static final int PLAYER_HOTBAR_SLOT_COUNT = 9;
    static final int SHIFT_IMPORT_MAX_CRAFT_ITERATIONS = 64;

    private RtsTransferUtils() {
    }

    /**
     * Checks whether quick move from linked storage should target the player's main inventory
     * (instead of the currently open menu's slots).
     */
    static boolean movesLinkedQuickMoveToPlayerInventory(AbstractContainerMenu menu) {
        // RTS 合成终端虽是 CraftingMenu 子类，但其输入槽位不是背包也不是可存放容器，
        // 快速转移时一律并入背包目标，避免物品被塞进合成终端槽位
        return menu instanceof InventoryMenu || menu instanceof CraftingMenu;
    }

    static int clampHotbarSlot(int slot) {
        return Math.max(0, Math.min(PLAYER_HOTBAR_SLOT_COUNT - 1, slot));
    }

}
