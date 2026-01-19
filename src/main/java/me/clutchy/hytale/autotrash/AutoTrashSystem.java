package me.clutchy.hytale.autotrash;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;

/**
 * Handles auto-trashing of configured items based on inventory change events and match rules.
 */
public final class AutoTrashSystem {

    /** Component type used to resolve player settings. */
    private static volatile ComponentType<EntityStore, AutoTrashPlayerSettings> settingsComponentType;

    private AutoTrashSystem() {
    }

    /**
     * Supplies the component type used for settings lookups.
     *
     * @param settingsComponentType settings component type
     */
    public static void setSettingsComponentType(@NonNullDecl ComponentType<EntityStore, AutoTrashPlayerSettings> settingsComponentType) {
        AutoTrashSystem.settingsComponentType = settingsComponentType;
    }

    /**
     * Handles inventory change events and removes trash items from player inventories.
     *
     * @param event the inventory change event
     */
    public static void handleInventoryChange(@NonNullDecl LivingEntityInventoryChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        ItemContainer container = event.getItemContainer();
        if (container == null) {
            return;
        }
        Transaction transaction = event.getTransaction();
        if (transaction == null) {
            return;
        }
        // Only act on containers that belong to the player inventory.
        if (!player.getInventory().getCombinedEverything().containsContainer(container)) {
            return;
        }

        AutoTrashPlayerSettings settings = getSettings(player);
        if (settings == null || !settings.isEnabled()) {
            return;
        }
        AutoTrashPlayerSettings.AutoTrashProfile profile = settings.getActiveProfile();
        if (profile == null) {
            return;
        }

        removeTrashItems(player, container, transaction, profile, settings);
    }

    /**
     * Removes trash items from any slots modified by the transaction.
     *
     * @param player the player owning the inventory
     * @param container the container being modified
     * @param transaction the inventory transaction driving the change
     * @param profile active auto-trash profile
     */
    private static void removeTrashItems(@NonNullDecl Player player, @NonNullDecl ItemContainer container, @NonNullDecl Transaction transaction,
            @NonNullDecl AutoTrashPlayerSettings.AutoTrashProfile profile, @NonNullDecl AutoTrashPlayerSettings settings) {
        // Collect only the slots touched by this transaction to avoid full scans.
        List<Short> slotsToRemove = new ArrayList<>();
        Map<String, Integer> totalsByItem = new LinkedHashMap<>();
        Map<String, ItemStack> samplesByItem = new LinkedHashMap<>();
        int removedCount = collectModifiedTrashSlots(container, transaction, slotsToRemove, totalsByItem, samplesByItem, profile);
        if (removedCount > 0) {
            for (short slot : slotsToRemove) {
                container.removeItemStackFromSlot(slot);
            }
            if (settings.isNotify()) {
                sendTrashNotifications(player, totalsByItem, samplesByItem);
            }
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
     * @param profile active auto-trash profile
     * @return total quantity removed by the transaction
     */
    private static int collectModifiedTrashSlots(@NonNullDecl ItemContainer container, @NonNullDecl Transaction transaction, @NonNullDecl List<Short> slotsToRemove,
            @NonNullDecl Map<String, Integer> totalsByItem, @NonNullDecl Map<String, ItemStack> samplesByItem, @NonNullDecl AutoTrashPlayerSettings.AutoTrashProfile profile) {
        if (!transaction.succeeded()) {
            return 0;
        }

        // Normalize the common transaction types into per-slot checks.
        switch (transaction) {
            case ItemStackSlotTransaction slotTransaction -> {
                return collectFromSlotTransaction(container, slotTransaction, slotsToRemove, totalsByItem, samplesByItem, profile);
            }
            case SlotTransaction slotTransaction -> {
                return collectFromSlotTransaction(container, slotTransaction, slotsToRemove, totalsByItem, samplesByItem, profile);
            }
            case ItemStackTransaction itemStackTransaction -> {
                return collectFromListTransaction(container, itemStackTransaction.getSlotTransactions(), slotsToRemove, totalsByItem, samplesByItem, profile);
            }
            case MoveTransaction<?> moveTransaction -> {
                int removed = collectFromSlotTransaction(container, moveTransaction.getRemoveTransaction(), slotsToRemove, totalsByItem, samplesByItem, profile);
                Transaction addTransaction = moveTransaction.getAddTransaction();
                if (addTransaction != null) {
                    removed += collectModifiedTrashSlots(container, addTransaction, slotsToRemove, totalsByItem, samplesByItem, profile);
                }
                return removed;
            }
            case ListTransaction<?> listTransaction -> {
                int removed = 0;
                for (Transaction subTransaction : listTransaction.getList()) {
                    removed += collectModifiedTrashSlots(container, subTransaction, slotsToRemove, totalsByItem, samplesByItem, profile);
                }
                return removed;
            }
            default -> {
            }
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
     * @param profile active auto-trash profile
     * @return quantity removed when the transaction matches a trash item
     */
    private static int collectFromSlotTransaction(@NonNullDecl ItemContainer container, @NonNullDecl SlotTransaction transaction, @NonNullDecl List<Short> slotsToRemove,
            @NonNullDecl Map<String, Integer> totalsByItem, @NonNullDecl Map<String, ItemStack> samplesByItem, @NonNullDecl AutoTrashPlayerSettings.AutoTrashProfile profile) {
        if (!transaction.succeeded()) {
            return 0;
        }

        return collectFromSlotTransaction(container, transaction.getAction().isRemove(), transaction.getSlot(), transaction.getSlotAfter(),
                transaction.getSlotBefore(), slotsToRemove, totalsByItem, samplesByItem, profile);

    }

    /**
     * Checks an item-stack slot transaction for newly added trash items.
     *
     * @param container the container being modified
     * @param transaction the item-stack slot transaction to inspect
     * @param slotsToRemove output list of slot indices to remove
     * @param totalsByItem output totals for each item id
     * @param samplesByItem output samples for each item id
     * @param profile active auto-trash profile
     * @return quantity removed when the transaction matches a trash item
     */
    private static int collectFromSlotTransaction(@NonNullDecl ItemContainer container, @NonNullDecl ItemStackSlotTransaction transaction,
            @NonNullDecl List<Short> slotsToRemove, @NonNullDecl Map<String, Integer> totalsByItem, @NonNullDecl Map<String, ItemStack> samplesByItem,
            @NonNullDecl AutoTrashPlayerSettings.AutoTrashProfile profile) {
        if (!transaction.succeeded()) {
            return 0;
        }

        return collectFromSlotTransaction(container, transaction.getAction().isRemove(), transaction.getSlot(), transaction.getSlotAfter(),
                transaction.getSlotBefore(), slotsToRemove, totalsByItem, samplesByItem, profile);
    }

    /**
     * Handles shared slot transaction checks for trashable items.
     *
     * @param container the container being modified
     * @param isRemoveAction whether the transaction removes items
     * @param slot the slot index affected
     * @param slotAfter stack after the transaction
     * @param slotBefore stack before the transaction
     * @param slotsToRemove output list of slot indices to remove
     * @param totalsByItem output totals for each item id
     * @param samplesByItem output samples for each item id
     * @param profile active auto-trash profile
     * @return quantity removed when the transaction matches a trash item
     */
    private static int collectFromSlotTransaction(@NonNullDecl ItemContainer container, boolean isRemoveAction, short slot, ItemStack slotAfter,
            ItemStack slotBefore, @NonNullDecl List<Short> slotsToRemove, @NonNullDecl Map<String, Integer> totalsByItem, @NonNullDecl Map<String, ItemStack> samplesByItem,
            @NonNullDecl AutoTrashPlayerSettings.AutoTrashProfile profile) {
        if (isRemoveAction) {
            return 0;
        }

        if (slot < 0 || slot >= container.getCapacity()) {
            return 0;
        }

        if (!matchesRule(slotAfter, profile)) {
            return 0;
        }

        slotsToRemove.add(slot);
        return registerRemovedItem(slotBefore, slotAfter, totalsByItem, samplesByItem);
    }

    /**
     * Walks a nested list transaction and collects trash-item slots.
     *
     * @param <T> the transaction type
     * @param container the container being modified
     * @param transactions the list of transactions to inspect
     * @param slotsToRemove output list of slot indices to remove
     * @param totalsByItem output totals for each item id
     * @param samplesByItem output samples for each item id
     * @param profile active auto-trash profile
     * @return total quantity removed across nested transactions
     */
    private static <T extends Transaction> int collectFromListTransaction(@NonNullDecl ItemContainer container, @NonNullDecl List<T> transactions,
            @NonNullDecl List<Short> slotsToRemove,
            @NonNullDecl Map<String, Integer> totalsByItem, @NonNullDecl Map<String, ItemStack> samplesByItem, @NonNullDecl AutoTrashPlayerSettings.AutoTrashProfile profile) {
        int removed = 0;
        for (Transaction transaction : transactions) {
            removed += collectModifiedTrashSlots(container, transaction, slotsToRemove, totalsByItem, samplesByItem, profile);
        }
        return removed;
    }

    /**
     * Checks whether the given stack matches configured auto-trash rules.
     *
     * @param itemStack the stack to check
     * @param profile active auto-trash profile
     * @return true if the stack should be auto-trashed
     */
    private static boolean matchesRule(ItemStack itemStack, @NonNullDecl AutoTrashPlayerSettings.AutoTrashProfile profile) {
        if (itemStack == null || ItemStack.isEmpty(itemStack)) {
            return false;
        }

        String itemId = itemStack.getItemId();
        for (String exactItem : profile.getExactItems()) {
            if (itemId.equals(exactItem)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Registers the removed item quantity and sample for notifications.
     *
     * @param slotBefore stack before the transaction
     * @param slotAfter stack after the transaction
     * @param totalsByItem output totals for each item id
     * @param samplesByItem output samples for each item id
     * @return quantity removed in this transaction
     */
    private static int registerRemovedItem(ItemStack slotBefore, @NonNullDecl ItemStack slotAfter, @NonNullDecl Map<String, Integer> totalsByItem,
            @NonNullDecl Map<String, ItemStack> samplesByItem) {
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

    /**
     * Sends per-item notifications when trash items were removed.
     *
     * @param player the player to notify
     * @param totalsByItem totals of removed items
     * @param samplesByItem sample stacks for item display
     */
    private static void sendTrashNotifications(@NonNullDecl Player player, @NonNullDecl Map<String, Integer> totalsByItem, @NonNullDecl Map<String, ItemStack> samplesByItem) {
        if (totalsByItem.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Integer> entry : totalsByItem.entrySet()) {
            ItemStack sample = samplesByItem.get(entry.getKey());
            if (sample == null) {
                continue;
            }

            sendTrashNotification(player, sample);
        }
    }

    /**
     * Sends a single trash notification for an item stack.
     *
     * @param player player to notify
     * @param itemStack item stack to display
     */
    public static void sendTrashNotification(@NonNullDecl Player player, @NonNullDecl ItemStack itemStack) {
        World world = player.getWorld();
        if (world == null || !world.getGameplayConfig().getShowItemPickupNotifications()) {
            return;
        }

        PlayerRef playerRef = resolvePlayerRef(player);
        if (playerRef == null) {
            return;
        }

        Message itemName = Message.translation(itemStack.getItem().getTranslationKey()).color("#b93333");
        NotificationUtil.sendNotification(playerRef.getPacketHandler(), itemName, null, itemStack.toPacket(), NotificationStyle.Default);
    }

    /**
     * Resolves the player's runtime reference for notifications.
     *
     * @param player player to resolve
     * @return player reference or null when unavailable
     */
    public static PlayerRef resolvePlayerRef(@NonNullDecl Player player) {
        if (player.getReference() == null) {
            return null;
        }

        return player.getReference().getStore().getComponent(player.getReference(), PlayerRef.getComponentType());
    }

    /**
     * Retrieves the settings for the player if the component type is available.
     *
     * @param player the player to query
     * @return settings or null when unavailable
     */
    private static AutoTrashPlayerSettings getSettings(@NonNullDecl Player player) {
        if (settingsComponentType == null) {
            return null;
        }

        return AutoTrashPlayerSettings.get(player, settingsComponentType);
    }
}
