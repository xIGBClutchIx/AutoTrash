package me.clutchy.hytale.autotrash;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.asset.AssetNotifications;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.NotificationUtil;

/**
 * Handles auto-trashing of configured items based on inventory change events and match rules.
 */
public final class AutoTrashSystem {

    /**
     * Exact item ids to auto-trash.
     */
    private static volatile String[] exactItems = new String[] { "Food_Wildmeat_Raw" };

    /**
     * Substring rules to auto-trash.
     */
    private static volatile String[] containsItems = new String[0];

    /**
     * Utility-only class.
     */
    private AutoTrashSystem() {
    }

    /**
     * Updates the item matching rules for auto-trash.
     *
     * @param exactItems exact item ids to delete
     * @param containsItems substring matches to delete
     */
    public static void updateRules(@Nonnull String[] exactItems, @Nonnull String[] containsItems) {
        AutoTrashSystem.exactItems = exactItems.clone();
        AutoTrashSystem.containsItems = containsItems.clone();
    }

    /**
     * Handles inventory change events and removes trash items from player inventories.
     *
     * @param event the inventory change event
     */
    public static void handleInventoryChange(@Nonnull LivingEntityInventoryChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        ItemContainer container = event.getItemContainer();
        // Only act on containers that belong to the player inventory.
        if (!player.getInventory().getCombinedEverything().containsContainer(container)) {
            return;
        }

        removeTrashItems(player, container, event.getTransaction());
    }

    /**
     * Removes trash items from any slots modified by the transaction.
     *
     * @param container the container being modified
     * @param transaction the inventory transaction driving the change
     */
    private static void removeTrashItems(@Nonnull Player player, @Nonnull ItemContainer container, @Nonnull Transaction transaction) {
        // Collect only the slots touched by this transaction to avoid full scans.
        List<Short> slotsToRemove = new ArrayList<>();
        Map<String, Integer> totalsByItem = new LinkedHashMap<>();
        Map<String, ItemStack> samplesByItem = new LinkedHashMap<>();
        int removedCount = collectModifiedTrashSlots(container, transaction, slotsToRemove, totalsByItem, samplesByItem);
        if (removedCount > 0) {
            for (short slot : slotsToRemove) {
                container.removeItemStackFromSlot(slot);
            }
            sendTrashNotifications(player, totalsByItem, samplesByItem);
        }
    }

    /**
     * Collects slot indices that now contain trash items after applying a transaction.
     *
     * @param container the container being modified
     * @param transaction the transaction to evaluate
     * @param slotsToRemove output list of slot indices to remove
     * @param totalsByItem output totals for each item id
     * @param samplesByItem output samples for each item id
     * @return total quantity removed by the transaction
     */
    private static int collectModifiedTrashSlots(
            @Nonnull ItemContainer container,
            @Nonnull Transaction transaction,
            @Nonnull List<Short> slotsToRemove,
            @Nonnull Map<String, Integer> totalsByItem,
            @Nonnull Map<String, ItemStack> samplesByItem
    ) {
        if (!transaction.succeeded()) {
            return 0;
        }

        // Normalize the common transaction types into per-slot checks.
        if (transaction instanceof ItemStackSlotTransaction slotTransaction) {
            return collectFromSlotTransaction(container, slotTransaction, slotsToRemove, totalsByItem, samplesByItem);
        }

        if (transaction instanceof SlotTransaction slotTransaction) {
            return collectFromSlotTransaction(container, slotTransaction, slotsToRemove, totalsByItem, samplesByItem);
        }

        if (transaction instanceof ItemStackTransaction itemStackTransaction) {
            return collectFromListTransaction(container, itemStackTransaction.getSlotTransactions(), slotsToRemove, totalsByItem, samplesByItem);
        }

        if (transaction instanceof MoveTransaction<?> moveTransaction) {
            int removed = collectFromSlotTransaction(container, moveTransaction.getRemoveTransaction(), slotsToRemove, totalsByItem, samplesByItem);
            Transaction addTransaction = moveTransaction.getAddTransaction();
            if (addTransaction != null) {
                removed += collectModifiedTrashSlots(container, addTransaction, slotsToRemove, totalsByItem, samplesByItem);
            }
            return removed;
        }

        if (transaction instanceof ListTransaction<?> listTransaction) {
            int removed = 0;
            for (Transaction subTransaction : listTransaction.getList()) {
                removed += collectModifiedTrashSlots(container, subTransaction, slotsToRemove, totalsByItem, samplesByItem);
            }
            return removed;
        }

        return 0;
    }

    /**
     * Checks a generic slot transaction for newly added trash items.
     *
     * @param container the container being modified
     * @param transaction the slot transaction to inspect
     * @param slotsToRemove output list of slot indices to remove
     * @param totalsByItem output totals for each item id
     * @param samplesByItem output samples for each item id
     * @return quantity removed when the transaction matches a trash item
     */
    private static int collectFromSlotTransaction(
            @Nonnull ItemContainer container,
            @Nonnull SlotTransaction transaction,
            @Nonnull List<Short> slotsToRemove,
            @Nonnull Map<String, Integer> totalsByItem,
            @Nonnull Map<String, ItemStack> samplesByItem
    ) {
        if (!transaction.succeeded()) {
            return 0;
        }

        // Ignore pure removals; only new/updated items should be evaluated.
        if (transaction.getAction().isRemove()) {
            return 0;
        }

        ItemStack slotAfter = transaction.getSlotAfter();
        if (!matchesRule(slotAfter)) {
            return 0;
        }

        short slot = transaction.getSlot();
        slotsToRemove.add(slot);
        return registerRemovedItem(transaction.getSlotBefore(), slotAfter, totalsByItem, samplesByItem);
    }

    /**
     * Checks an item-stack slot transaction for newly added trash items.
     *
     * @param container the container being modified
     * @param transaction the item-stack slot transaction to inspect
     * @param slotsToRemove output list of slot indices to remove
     * @param totalsByItem output totals for each item id
     * @param samplesByItem output samples for each item id
     * @return quantity removed when the transaction matches a trash item
     */
    private static int collectFromSlotTransaction(
            @Nonnull ItemContainer container,
            @Nonnull ItemStackSlotTransaction transaction,
            @Nonnull List<Short> slotsToRemove,
            @Nonnull Map<String, Integer> totalsByItem,
            @Nonnull Map<String, ItemStack> samplesByItem
    ) {
        if (!transaction.succeeded()) {
            return 0;
        }

        // Use slot-after to avoid extra container reads for ItemStack-aware transactions.
        if (transaction.getAction().isRemove()) {
            return 0;
        }

        ItemStack slotAfter = transaction.getSlotAfter();
        if (!matchesRule(slotAfter)) {
            return 0;
        }

        slotsToRemove.add(transaction.getSlot());
        return registerRemovedItem(transaction.getSlotBefore(), slotAfter, totalsByItem, samplesByItem);
    }

    /**
     * Walks a nested list transaction and collects trash-item slots.
     *
     * @param container the container being modified
     * @param transactions the list of transactions to inspect
     * @param slotsToRemove output list of slot indices to remove
     * @param totalsByItem output totals for each item id
     * @param samplesByItem output samples for each item id
     * @param <T> the transaction type
     * @return total quantity removed across nested transactions
     */
    private static <T extends Transaction> int collectFromListTransaction(
            @Nonnull ItemContainer container,
            @Nonnull List<T> transactions,
            @Nonnull List<Short> slotsToRemove,
            @Nonnull Map<String, Integer> totalsByItem,
            @Nonnull Map<String, ItemStack> samplesByItem
    ) {
        // Walk nested transaction lists (batch adds, multi-slot moves, etc.).
        int removed = 0;
        for (Transaction transaction : transactions) {
            removed += collectModifiedTrashSlots(container, transaction, slotsToRemove, totalsByItem, samplesByItem);
        }
        return removed;
    }

    /**
     * Checks whether the given stack matches configured auto-trash rules.
     *
     * @param itemStack the stack to check
     * @return true if the stack should be auto-trashed
     */
    private static boolean matchesRule(ItemStack itemStack) {
        if (ItemStack.isEmpty(itemStack)) {
            return false;
        }

        String itemId = itemStack.getItemId();
        for (String exactItem : exactItems) {
            if (itemId.equals(exactItem)) {
                return true;
            }
        }

        for (String containsItem : containsItems) {
            if (itemId.contains(containsItem)) {
                return true;
            }
        }

        return false;
    }

    private static int registerRemovedItem(
            @Nonnull ItemStack slotBefore,
            @Nonnull ItemStack slotAfter,
            @Nonnull Map<String, Integer> totalsByItem,
            @Nonnull Map<String, ItemStack> samplesByItem
    ) {
        String itemId = slotAfter.getItemId();
        int beforeQuantity = ItemStack.isEmpty(slotBefore) ? 0 : slotBefore.getQuantity();
        int delta = slotAfter.getQuantity() - beforeQuantity;
        if (delta <= 0) {
            return 0;
        }

        totalsByItem.put(itemId, totalsByItem.getOrDefault(itemId, 0) + delta);
        samplesByItem.putIfAbsent(itemId, slotAfter);
        return delta;
    }

    private static void sendTrashNotifications(
            @Nonnull Player player,
            @Nonnull Map<String, Integer> totalsByItem,
            @Nonnull Map<String, ItemStack> samplesByItem
    ) {
        if (totalsByItem.isEmpty()) {
            return;
        }

        World world = player.getWorld();
        if (world == null || !world.getGameplayConfig().getShowItemPickupNotifications()) {
            return;
        }

        PlayerRef playerRef = resolvePlayerRef(player);
        if (playerRef == null) {
            return;
        }

        for (Map.Entry<String, Integer> entry : totalsByItem.entrySet()) {
            ItemStack sample = samplesByItem.get(entry.getKey());
            if (sample == null) {
                continue;
            }

            ItemStack displayStack = sample.withQuantity(entry.getValue());
            if (displayStack == null) {
                continue;
            }

            String itemName = Message.translation(displayStack.getItem().getTranslationKey()).getAnsiMessage();
            Message mainLine = Message.raw("-" + entry.getValue() + " " + itemName).color("#b93333");
            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    mainLine,
                    null,
                    AssetNotifications.ASSET_DELETED_ICON,
                    NotificationStyle.Default
            );
        }
    }

    private static PlayerRef resolvePlayerRef(@Nonnull Player player) {
        if (player.getReference() == null) {
            return null;
        }

        return player.getReference().getStore().getComponent(player.getReference(), PlayerRef.getComponentType());
    }
}
