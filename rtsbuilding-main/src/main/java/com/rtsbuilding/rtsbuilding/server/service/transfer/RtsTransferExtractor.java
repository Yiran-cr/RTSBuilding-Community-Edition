package com.rtsbuilding.rtsbuilding.server.service.transfer;

import com.rtsbuilding.rtsbuilding.api.compat.DirectExtractHandler;
import com.rtsbuilding.rtsbuilding.server.service.page.RtsPageSharedHelpers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * Item extraction utility class, handling core logic for extracting items from various sources.
 *
 * <p>This class provides a comprehensive set of methods for extracting items from linked storage handlers,
 * player inventory, player hotbar, and network combinations. All methods are {@code static},
 * the class itself is a non-instantiable utility class.
 *
 * <p><b>Extraction levels (low to high):</b>
 * <ul>
 *   <li><b>Single handler extraction:</b> {@link #extractOne(IItemHandler, Item)}、
 *       {@link #extractMatching(IItemHandler, Item, int)} — Extracts from a single IItemHandler</li>
 *   <li><b>Linked storage extraction:</b> {@link #extractOneFromLinked(List, Item)}、
 *       {@link #extractMatchingFromLinked(List, Item, int)} — Iterates over multiple handlers</li>
 *   <li><b>Player inventory extraction:</b> {@link #extractOneFromPlayerMainInventory(ServerPlayer, Item)}、
 *       {@link #extractMatchingFromPlayerMainInventory(ServerPlayer, Item, int)} — Extracts from main inventory</li>
 *   <li><b>Player hotbar extraction:</b> {@link #extractMatchingFromPlayerHotbarForQuickDrop(ServerPlayer, Item, int)} —
 *       Prioritizes selected slot, then iterates over other hotbar slots</li>
 *   <li><b>Network combined extraction:</b> {@link #extractOneFromNetwork(List, ServerPlayer, Item)}、
 *       {@link #extractMatchingFromNetwork(List, ServerPlayer, Item, int)} —
 *       Linked storage first, then player inventory</li>
 *   <li><b>Quick drop sources:</b> {@link #extractMatchingFromQuickDropSources(List, ServerPlayer, Item, int)} —
 *       Linked storage first, then hotbar, then main inventory</li>
 *   <li><b>Prototype matching extraction:</b> {@link #extractOneMatchingPrototypeFromLinked(List, ItemStack)}、
 *       {@link #extractOneMatchingPrototypeCombined(List, ServerPlayer, ItemStack)} —
 *       Strictly matches components by ItemStack prototype, used by crafting system</li>
 * </ul>
 *
 * <p><b>Helper methods:</b>
 * <ul>
 *   <li>{@link #mergeExtractedStacks(ItemStack, ItemStack)} — Merges two extracted stacks of the same type</li>
 *   <li>{@link #snapshotPlayerMatchingCounts(ServerPlayer, ItemStack)} —
 *       Snapshots the count of prototype-matching items in each player inventory slot</li>
 *   <li>{@link #drainPlayerInventoryDelta(ServerPlayer, ItemStack, int[])} —
 *       Calculates and extracts delta changes of prototype-matching items in player inventory</li>
 * </ul>
 *
 * <p><b>Design features:</b>
 * <ul>
 *   <li>Integrates with {@link DirectExtractHandler} for BD warehouse direct extraction optimization</li>
 *   <li>Extraction methods maintain component consistency (checked via {@code ItemStack.isSameItemSameComponents})</li>
 *   <li>Non-matching stacks are returned via {@link RtsTransferInserter#insertToHandlerPreferExisting}</li>
 * </ul>
 */
public final class RtsTransferExtractor {

    private RtsTransferExtractor() {
    }

    // ---- single-item extraction --------------------------------------------------

    public static ItemStack extractOne(IItemHandler handler, Item targetItem) {
        if (handler instanceof DirectExtractHandler de) {
            return de.tryExtractItem(targetItem, 1, false);
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getItem() != targetItem) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, 1, false);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack extractMatching(IItemHandler handler, Item targetItem, int limit) {
        if (handler instanceof DirectExtractHandler de) {
            return de.tryExtractItem(targetItem, limit, false);
        }
        return extractMatching(handler, targetItem, ItemStack.EMPTY, limit);
    }

    public static ItemStack extractMatching(IItemHandler handler, Item targetItem, ItemStack preferred, int limit) {
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack out = ItemStack.EMPTY;
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getItem() != targetItem) {
                continue;
            }
            ItemStack expected = out.isEmpty() ? preferred : out;
            if (!expected.isEmpty() && !ItemStack.isSameItemSameComponents(stack, expected)) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, remaining, false);
            if (extracted.isEmpty()) {
                continue;
            }
        if (out.isEmpty()) {
            if (!preferred.isEmpty() && !ItemStack.isSameItemSameComponents(extracted, preferred)) {
                // 变体不匹配：优先插回原槽位；插回失败则随结果返回，杜绝物品丢失（B6 修复）
                if (!refundExtractedToSlot(handler, slot, extracted)) {
                    return extracted;
                }
                continue;
            }
            out = extracted;
        } else if (ItemStack.isSameItemSameComponents(out, extracted)) {
            out.grow(extracted.getCount());
        } else {
            // 变体不匹配：优先插回原槽位（该槽位刚被清空，插回几乎必然成功）；
            // 插回失败时保留已提取的输出，避免部分丢失
            if (!refundExtractedToSlot(handler, slot, extracted)) {
                return out;
            }
            continue;
        }
        remaining -= extracted.getCount();
        }
        return out;
    }

    // ---- from linked handlers ---------------------------------------------------

    public static ItemStack extractOneFromLinked(List<IItemHandler> handlers, Item targetItem) {
        for (IItemHandler handler : handlers) {
            ItemStack extracted = extractOne(handler, targetItem);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack extractOneFromPlayerMainInventory(ServerPlayer player, Item targetItem) {
        if (player == null || targetItem == null) {
            return ItemStack.EMPTY;
        }
        int start = RtsPageSharedHelpers.getPlayerMainInventoryStart(player);
        int end = RtsPageSharedHelpers.getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (current.isEmpty() || current.getItem() != targetItem) {
                continue;
            }
            ItemStack extracted = current.split(1);
            if (current.isEmpty()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(slot, current);
            }
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack extractOneFromNetwork(List<IItemHandler> handlers, ServerPlayer player, Item targetItem) {
        ItemStack extracted = extractOneFromLinked(handlers, targetItem);
        if (!extracted.isEmpty()) {
            return extracted;
        }
        return extractOneFromPlayerMainInventory(player, targetItem);
    }

    // ---- multi-item extraction --------------------------------------------------

    public static ItemStack extractMatchingFromLinked(List<IItemHandler> handlers, Item targetItem, int limit) {
        return extractMatchingFromLinked(handlers, targetItem, ItemStack.EMPTY, limit);
    }

    public static ItemStack extractMatchingFromLinked(List<IItemHandler> handlers, Item targetItem, ItemStack preferred, int limit) {
        int remaining = Math.max(0, limit);
        ItemStack out = ItemStack.EMPTY;
        for (IItemHandler handler : handlers) {
            if (remaining <= 0) {
                break;
            }
            ItemStack part = extractMatching(handler, targetItem, out.isEmpty() ? preferred : out, remaining);
            if (part.isEmpty()) {
                continue;
            }
            if (out.isEmpty()) {
                out = part;
            } else if (ItemStack.isSameItemSameComponents(out, part)) {
                out.grow(part.getCount());
            }
            remaining -= part.getCount();
        }
        return out;
    }

    // ---- from player inventory ---------------------------------------------------

    public static ItemStack extractMatchingFromPlayerMainInventory(ServerPlayer player, Item targetItem, int limit) {
        return extractMatchingFromPlayerMainInventory(player, targetItem, ItemStack.EMPTY, limit);
    }

    public static ItemStack extractMatchingFromPlayerMainInventory(
            ServerPlayer player, Item targetItem, ItemStack preferred, int limit) {
        if (player == null || targetItem == null) {
            return ItemStack.EMPTY;
        }
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack out = ItemStack.EMPTY;
        int start = RtsPageSharedHelpers.getPlayerMainInventoryStart(player);
        int end = RtsPageSharedHelpers.getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end && remaining > 0; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (current.isEmpty() || current.getItem() != targetItem) {
                continue;
            }
            ItemStack expected = out.isEmpty() ? preferred : out;
            if (!expected.isEmpty() && !ItemStack.isSameItemSameComponents(current, expected)) {
                continue;
            }
            int take = Math.min(remaining, current.getCount());
            ItemStack extracted = current.split(take);
            if (current.isEmpty()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(slot, current);
            }
            if (extracted.isEmpty()) {
                continue;
            }
            if (out.isEmpty()) {
                if (!preferred.isEmpty() && !ItemStack.isSameItemSameComponents(extracted, preferred)) {
                    player.getInventory().add(extracted);
                    continue;
                }
                out = extracted;
            } else if (ItemStack.isSameItemSameComponents(out, extracted)) {
                out.grow(extracted.getCount());
            } else {
                player.getInventory().add(extracted);
                continue;
            }
            remaining -= extracted.getCount();
        }
        return out;
    }

    // ---- from player hotbar -----------------------------------------------------

    public static ItemStack extractMatchingFromPlayerHotbarForQuickDrop(
            ServerPlayer player, Item targetItem, int limit) {
        return extractMatchingFromPlayerHotbarForQuickDrop(player, targetItem, ItemStack.EMPTY, limit);
    }

    public static ItemStack extractMatchingFromPlayerHotbarForQuickDrop(
            ServerPlayer player, Item targetItem, ItemStack preferred, int limit) {
        if (player == null || targetItem == null) {
            return ItemStack.EMPTY;
        }
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack out = ItemStack.EMPTY;
        int selected = RtsTransferUtils.clampHotbarSlot(player.getInventory().selected);
        ItemStack selectedPart = extractMatchingFromPlayerSlot(player, targetItem, preferred, selected, remaining);
        out = mergeExtractedStacks(out, selectedPart);
        remaining -= selectedPart.getCount();

        for (int slot = 0; slot < RtsTransferUtils.PLAYER_HOTBAR_SLOT_COUNT && remaining > 0; slot++) {
            if (slot == selected) {
                continue;
            }
            ItemStack part = extractMatchingFromPlayerSlot(
                    player, targetItem, out.isEmpty() ? preferred : out, slot, remaining);
            out = mergeExtractedStacks(out, part);
            remaining -= part.getCount();
        }
        return out;
    }

    public static ItemStack extractMatchingFromPlayerSlot(
            ServerPlayer player, Item targetItem, ItemStack preferred, int slot, int limit) {
        if (player == null || targetItem == null || slot < 0 || limit <= 0) {
            return ItemStack.EMPTY;
        }
        if (slot >= player.getInventory().getContainerSize()) {
            return ItemStack.EMPTY;
        }
        ItemStack current = player.getInventory().getItem(slot);
        if (current.isEmpty() || current.getItem() != targetItem) {
            return ItemStack.EMPTY;
        }
        if (!preferred.isEmpty() && !ItemStack.isSameItemSameComponents(current, preferred)) {
            return ItemStack.EMPTY;
        }
        int take = Math.min(limit, current.getCount());
        ItemStack extracted = current.split(take);
        if (current.isEmpty()) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        } else {
            player.getInventory().setItem(slot, current);
        }
        return extracted.isEmpty() ? ItemStack.EMPTY : extracted;
    }

    // ---- combined network extraction -------------------------------------------

    public static ItemStack extractMatchingFromNetwork(
            List<IItemHandler> handlers, ServerPlayer player, Item targetItem, int limit) {
        return extractMatchingFromNetwork(handlers, player, targetItem, ItemStack.EMPTY, limit);
    }

    public static ItemStack extractMatchingFromNetwork(
            List<IItemHandler> handlers, ServerPlayer player, Item targetItem,
            ItemStack preferred, int limit) {
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack out = extractMatchingFromLinked(handlers, targetItem, preferred, remaining);
        remaining -= out.getCount();
        if (remaining <= 0) {
            return out;
        }
        ItemStack fromPlayer = extractMatchingFromPlayerMainInventory(
                player, targetItem, out.isEmpty() ? preferred : out, remaining);
        if (fromPlayer.isEmpty()) {
            return out;
        }
        if (out.isEmpty()) {
            return fromPlayer;
        }
        if (ItemStack.isSameItemSameComponents(out, fromPlayer)) {
            out.grow(fromPlayer.getCount());
        }
        return out;
    }

    public static ItemStack extractMatchingFromQuickDropSources(
            List<IItemHandler> handlers, ServerPlayer player, Item targetItem, int limit) {
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack out = extractMatchingFromLinked(handlers, targetItem, remaining);
        remaining -= out.getCount();
        if (remaining <= 0) {
            return out;
        }
        ItemStack fromHotbar = extractMatchingFromPlayerHotbarForQuickDrop(player, targetItem, out, remaining);
        out = mergeExtractedStacks(out, fromHotbar);
        remaining -= fromHotbar.getCount();
        if (remaining <= 0) {
            return out;
        }
        ItemStack fromMainInventory = extractMatchingFromPlayerMainInventory(player, targetItem, out, remaining);
        out = mergeExtractedStacks(out, fromMainInventory);
        return out;
    }

    // ---- prototype-based extraction (used by crafting) -------------------------

    public static ItemStack extractOneMatchingPrototypeCombined(
            List<IItemHandler> handlers, ServerPlayer player, ItemStack prototype) {
        ItemStack fromLinked = extractOneMatchingPrototypeFromLinked(handlers, prototype);
        if (!fromLinked.isEmpty()) {
            return fromLinked;
        }
        return extractOneMatchingPrototypeFromPlayer(player, prototype);
    }

    public static ItemStack extractOneMatchingPrototypeFromLinked(List<IItemHandler> handlers, ItemStack prototype) {
        if (prototype == null || prototype.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, prototype)) {
                    continue;
                }
                ItemStack extracted = handler.extractItem(slot, 1, false);
                if (!extracted.isEmpty() && ItemStack.isSameItemSameComponents(extracted, prototype)) {
                    return extracted;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack extractOneMatchingPrototypeFromPlayer(ServerPlayer player, ItemStack prototype) {
        if (player == null || prototype == null || prototype.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int start = RtsPageSharedHelpers.getPlayerMainInventoryStart(player);
        int end = RtsPageSharedHelpers.getPlayerMainInventoryEndExclusive(player);
        for (int i = start; i < end; i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, prototype)) {
                continue;
            }
            ItemStack extracted = current.split(1);
            if (current.isEmpty()) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(i, current);
            }
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    // ---- helpers ----------------------------------------------------------------

    /**
     * 变体不匹配时把已提取的物品插回原槽位；原槽失败时再尝试全局插入，
     * 尽量保证物品不丢失。
     *
     * @return true 表示物品已成功归还（调用方可继续），false 表示归还失败
     */
    private static boolean refundExtractedToSlot(IItemHandler handler, int slot, ItemStack extracted) {
        if (handler == null || extracted == null || extracted.isEmpty()) {
            return true;
        }
        ItemStack remain = handler.insertItem(slot, extracted, false);
        if (remain.isEmpty()) {
            return true;
        }
        return RtsTransferInserter.insertToHandlerPreferExisting(handler, remain).isEmpty();
    }

    public static ItemStack mergeExtractedStacks(ItemStack into, ItemStack addition) {
        if (addition == null || addition.isEmpty()) {
            return into;
        }
        if (into == null || into.isEmpty()) {
            return addition;
        }
        if (ItemStack.isSameItemSameComponents(into, addition)) {
            into.grow(addition.getCount());
        }
        return into;
    }

    public static int[] snapshotPlayerMatchingCounts(ServerPlayer player, ItemStack prototype) {
        int size = player.getInventory().getContainerSize();
        int[] counts = new int[size];
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, prototype)) {
                counts[i] = stack.getCount();
            }
        }
        return counts;
    }

    public static ItemStack drainPlayerInventoryDelta(ServerPlayer player, ItemStack prototype, int[] before) {
        ItemStack out = ItemStack.EMPTY;
        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (!ItemStack.isSameItemSameComponents(current, prototype)) {
                continue;
            }
            int previous = (before != null && i < before.length) ? before[i] : 0;
            int gained = Math.max(0, current.getCount() - previous);
            if (gained <= 0) {
                continue;
            }
            int take = Math.min(gained, current.getCount());
            ItemStack part = current.split(take);
            if (current.isEmpty()) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(i, current);
            }
            if (out.isEmpty()) {
                out = part;
            } else if (ItemStack.isSameItemSameComponents(out, part)) {
                out.grow(part.getCount());
            } else {
                player.getInventory().add(part);
            }
        }
        return out;
    }
}
