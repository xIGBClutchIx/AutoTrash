package me.clutchy.hytale.autotrash;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;

/**
 * Command to open the auto-trash configuration GUI.
 */
public final class TrashCommand extends CommandBase {

    /** Component type used for player settings. */
    private final ComponentType<EntityStore, AutoTrashPlayerSettings> settingsComponentType;

    /**
     * Creates the command instance.
     *
     * @param settingsComponentType component type for player settings
     */
    public TrashCommand(@NonNullDecl ComponentType<EntityStore, AutoTrashPlayerSettings> settingsComponentType) {
        super("trash", "autotrash.command.trash.desc");
        this.settingsComponentType = settingsComponentType;
    }

    /**
     * Executes the command synchronously on the server thread.
     *
     * @param context command context
     */
    @Override
    protected void executeSync(@NonNullDecl CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("Only players can open the AutoTrash UI."));
            return;
        }

        Player player = context.senderAs(Player.class);
        if (player.getReference() == null) {
            context.sendMessage(Message.raw("Unable to open the AutoTrash UI right now."));
            return;
        }

        player.getReference().getStore().getExternalData().getWorld().execute(() -> {
            PlayerRef playerRef = AutoTrashSystem.resolvePlayerRef(player);
            if (playerRef == null) {
                context.sendMessage(Message.raw("Unable to open the AutoTrash UI right now."));
                return;
            }

            player.getPageManager().openCustomPage(player.getReference(), player.getReference().getStore(), new AutoTrashConfigPage(playerRef));
        });
    }

    /**
     * Converts an array to a mutable list.
     *
     * @param values array to convert
     * @return mutable list of values
     */
    private static List<String> toMutableList(String[] values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    /**
     * UI page for configuring auto-trash settings.
     */
    private final class AutoTrashConfigPage extends InteractiveCustomUIPage<AutoTrashConfigPage.PageEventData> {

        /** Cached settings component for the player. */
        private AutoTrashPlayerSettings playerSettings;

        /**
         * Creates the configuration page.
         *
         * @param playerRef player reference
         */
        private AutoTrashConfigPage(@NonNullDecl PlayerRef playerRef) {
            super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageEventData.CODEC);
        }

        /**
         * Builds the UI and binds initial values.
         *
         * @param ref entity reference
         * @param commandBuilder UI command builder
         * @param eventBuilder UI event builder
         * @param store entity store
         */
        @Override
        public void build(@NonNullDecl com.hypixel.hytale.component.Ref<EntityStore> ref, @NonNullDecl UICommandBuilder commandBuilder, @NonNullDecl UIEventBuilder eventBuilder,
                @NonNullDecl com.hypixel.hytale.component.Store<EntityStore> store) {
            commandBuilder.append("Pages/AutoTrashConfigPage.ui");

            // Store the settings reference directly using an SDK pattern.
            this.playerSettings = store.ensureAndGetComponent(ref, settingsComponentType);

            commandBuilder.set("#EnabledRow #CheckBox.Value", this.playerSettings.isEnabled());
            commandBuilder.set("#NotifyRow #CheckBox.Value", this.playerSettings.isNotify());
            buildFilterList(commandBuilder, this.playerSettings.getExactItems());

            eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#EnabledRow #CheckBox",
                    EventData.of(PageEventData.KEY_ACTION, PageEventData.ACTION_TOGGLE_ENABLED).append(PageEventData.KEY_VALUE, "#EnabledRow #CheckBox.Value"), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#NotifyRow #CheckBox",
                    EventData.of(PageEventData.KEY_ACTION, PageEventData.ACTION_TOGGLE_NOTIFY).append(PageEventData.KEY_VALUE, "#NotifyRow #CheckBox.Value"), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ItemAddButton", EventData.of(PageEventData.KEY_ACTION, PageEventData.ACTION_ADD_EXACT));

            rebuildListBindings(eventBuilder, this.playerSettings);
        }

        /**
         * Handles UI events and applies settings changes.
         *
         * @param ref entity reference
         * @param store entity store
         * @param data event data
         */
        @Override
        public void handleDataEvent(@NonNullDecl com.hypixel.hytale.component.Ref<EntityStore> ref, @NonNullDecl com.hypixel.hytale.component.Store<EntityStore> store,
                @NonNullDecl PageEventData data) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null || this.playerSettings == null) {
                return;
            }

            boolean changed = false;
            if (data.action == null) {
                return;
            }
            switch (data.action) {
                case PageEventData.ACTION_TOGGLE_ENABLED -> {
                    if (data.value == null) {
                        return;
                    }
                    this.playerSettings.setEnabled(data.value);
                    changed = true;
                }
                case PageEventData.ACTION_TOGGLE_NOTIFY -> {
                    if (data.value == null) {
                        return;
                    }
                    this.playerSettings.setNotify(data.value);
                    changed = true;
                }
                case PageEventData.ACTION_ADD_EXACT -> {
                    ItemStack held = player.getInventory().getItemInHand();
                    if (ItemStack.isEmpty(held)) {
                        player.sendMessage(Message.raw("Hold an item to add it to the auto-trash list."));
                        return;
                    }
                    String itemId = held.getItemId();
                    String[] current = this.playerSettings.getExactItems();
                    String[] updated = updateArray(current, true, itemId);
                    if (updated != current) {
                        this.playerSettings.setExactItems(updated);
                        changed = true;
                    }
                    // Remove the held item and send trash notification.
                    player.getInventory().getHotbar().removeItemStackFromSlot(player.getInventory().getActiveHotbarSlot());
                    if (this.playerSettings.isNotify()) {
                        sendTrashNotification(player, held);
                    }
                }
                case PageEventData.ACTION_REMOVE_EXACT -> {
                    String itemId = data.itemId;
                    if (itemId == null || itemId.isBlank()) {
                        player.sendMessage(Message.raw("Click a row to remove it from the auto-trash list."));
                        return;
                    }
                    String[] current = this.playerSettings.getExactItems();
                    String[] updated = updateArray(current, false, itemId);
                    if (updated != current) {
                        this.playerSettings.setExactItems(updated);
                        changed = true;
                    }
                }
                default -> {
                }
            }

            if (changed) {
                rebuild();
            }
        }

        /**
         * Builds the list of configured item ids in the UI.
         *
         * @param commandBuilder UI command builder
         * @param items          item ids to list
         */
        private void buildFilterList(@NonNullDecl UICommandBuilder commandBuilder, @NonNullDecl String[] items) {
            int index = 0;
            for (String itemId : items) {
                if (itemId == null || itemId.isBlank()) {
                    continue;
                }
                String rowSelector = "#ItemList" + "[" + index + "]";
                commandBuilder.append("#ItemList", "Pages/BasicTextButton.ui");
                commandBuilder.set(rowSelector + ".Text", getItemDisplayName(itemId));
                index++;
            }
        }

        /**
         * Sends a trash notification for the provided item stack.
         *
         * @param player player to notify
         * @param itemStack item stack to display
         */
        private void sendTrashNotification(@NonNullDecl Player player, @NonNullDecl ItemStack itemStack) {
            PlayerRef playerRef = AutoTrashSystem.resolvePlayerRef(player);
            if (playerRef == null) {
                return;
            }
            Message itemName = Message.translation(itemStack.getItem().getTranslationKey()).color("#b93333");
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), itemName, null, itemStack.toPacket(), NotificationStyle.Default);
        }

        /**
         * Resolves a display name for an item id.
         *
         * @param itemId item id
         * @return display name for UI
         */
        private String getItemDisplayName(@NonNullDecl String itemId) {
            Item item = Item.getAssetMap().getAsset(itemId);
            if (item == null) {
                return itemId;
            }
            return Message.translation(item.getTranslationKey()).getAnsiMessage();
        }

        /**
         * Updates an array of item ids by adding or removing a target.
         *
         * @param values current values
         * @param add true to add, false to remove
         * @param itemId item id to update
         * @return updated array
         */
        private String[] updateArray(@NonNullDecl String[] values, boolean add, @NonNullDecl String itemId) {
            List<String> items = toMutableList(values);
            if (add) {
                if (!items.contains(itemId)) {
                    items.add(itemId);
                }
            } else {
                items.remove(itemId);
            }
            return items.toArray(String[]::new);
        }

        /**
         * Rebuilds UI event bindings for the list of configured items.
         *
         * @param eventBuilder UI event builder
         * @param settings player settings
         */
        private void rebuildListBindings(@NonNullDecl UIEventBuilder eventBuilder, @NonNullDecl AutoTrashPlayerSettings settings) {
            int index = 0;
            for (String itemId : settings.getExactItems()) {
                if (itemId == null || itemId.isBlank()) {
                    continue;
                }
                String rowSelector = "#ItemList[" + index + "]";
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, rowSelector,
                        EventData.of(PageEventData.KEY_ACTION, PageEventData.ACTION_REMOVE_EXACT).append(PageEventData.KEY_ITEM, itemId), false);
                index++;
            }
        }

        /**
         * Event payload for the UI page.
         */
        public static class PageEventData {
            public static final String KEY_ACTION = "Action";
            public static final String KEY_VALUE = "@Value";
            public static final String KEY_ITEM = "Item";
            public static final String ACTION_TOGGLE_ENABLED = "ToggleEnabled";
            public static final String ACTION_TOGGLE_NOTIFY = "ToggleNotify";
            public static final String ACTION_ADD_EXACT = "AddExact";
            public static final String ACTION_REMOVE_EXACT = "RemoveExact";

            public static final BuilderCodec<PageEventData> CODEC = BuilderCodec.builder(PageEventData.class, PageEventData::new).append(new KeyedCodec<>(KEY_ACTION,
                    Codec.STRING), (data, value) -> data.action = value, data -> data.action).add().append(new KeyedCodec<>(KEY_VALUE,
                            Codec.BOOLEAN), (data, value) -> data.value = value, data -> data.value)
                    .add().append(new KeyedCodec<>(KEY_ITEM,
                            Codec.STRING), (data, value) -> data.itemId = value, data -> data.itemId)
                    .add().append(new KeyedCodec<>(KEY_ITEM.toUpperCase(),
                            Codec.STRING), (data, value) -> {
                                if (data.itemId == null || data.itemId.isBlank()) {
                                    data.itemId = value;
                                }
                            }, _ -> null)
                    .add().build();

            public String action;
            public Boolean value;
            public String itemId;
        }
    }
}
