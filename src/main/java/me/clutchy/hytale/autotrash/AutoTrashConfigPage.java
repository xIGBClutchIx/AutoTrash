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
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * UI page for configuring auto-trash settings.
 */
public final class AutoTrashConfigPage extends InteractiveCustomUIPage<AutoTrashConfigPage.PageEventData> {

    /** Component type used for player settings. */
    private final ComponentType<EntityStore, AutoTrashPlayerSettings> settingsComponentType;

    /** Cached settings component for the player. */
    private AutoTrashPlayerSettings playerSettings;

    /**
     * Creates the configuration page.
     *
     * @param playerRef player reference
     * @param settingsComponentType component type for player settings
     */
    public AutoTrashConfigPage(@NonNullDecl PlayerRef playerRef, @NonNullDecl ComponentType<EntityStore, AutoTrashPlayerSettings> settingsComponentType) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageEventData.CODEC);
        this.settingsComponentType = settingsComponentType;
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
    public void build(@NonNullDecl com.hypixel.hytale.component.Ref<EntityStore> ref, @NonNullDecl UICommandBuilder commandBuilder,
            @NonNullDecl UIEventBuilder eventBuilder, @NonNullDecl com.hypixel.hytale.component.Store<EntityStore> store) {
        commandBuilder.append("Pages/AutoTrashConfigPage.ui");

        this.playerSettings = store.ensureAndGetComponent(ref, settingsComponentType);
        AutoTrashPlayerSettings.AutoTrashProfile profile = this.playerSettings.getActiveProfile();

        buildProfileDropdown(commandBuilder, this.playerSettings);
        commandBuilder.set("#ProfileNameInput.Value", "");
        commandBuilder.set("#ProfileCount.Text", "Profiles: " + this.playerSettings.getProfileCount() + "/" + AutoTrashPlayerSettings.MAX_PROFILES);
        commandBuilder.set("#ProfileWarning.Visible", this.playerSettings.isProfileLimitReached());
        commandBuilder.set("#EnabledRow #CheckBox.Value", this.playerSettings.isEnabled());
        commandBuilder.set("#NotifyRow #CheckBox.Value", this.playerSettings.isNotify());
        buildFilterList(commandBuilder, profile.getExactItems());

        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ProfileDropdown",
                EventData.of(PageEventData.KEY_ACTION, PageEventData.ACTION_SWITCH_PROFILE).append(PageEventData.KEY_PROFILE, "#ProfileDropdown.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ProfileAddButton",
                EventData.of(PageEventData.KEY_ACTION, PageEventData.ACTION_ADD_PROFILE).append(PageEventData.KEY_PROFILE, "#ProfileNameInput.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ProfileDuplicateButton",
                EventData.of(PageEventData.KEY_ACTION, PageEventData.ACTION_DUPLICATE_PROFILE).append(PageEventData.KEY_PROFILE, "#ProfileNameInput.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ProfileRenameButton",
                EventData.of(PageEventData.KEY_ACTION, PageEventData.ACTION_RENAME_PROFILE).append(PageEventData.KEY_PROFILE, "#ProfileNameInput.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ProfileDeleteButton",
                EventData.of(PageEventData.KEY_ACTION, PageEventData.ACTION_DELETE_PROFILE).append(PageEventData.KEY_PROFILE, "#ProfileDropdown.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ProfileScanButton",
                EventData.of(PageEventData.KEY_ACTION, PageEventData.ACTION_SCAN_INVENTORY), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#EnabledRow #CheckBox",
                EventData.of(PageEventData.KEY_ACTION, PageEventData.ACTION_TOGGLE_ENABLED).append(PageEventData.KEY_VALUE, "#EnabledRow #CheckBox.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#NotifyRow #CheckBox",
                EventData.of(PageEventData.KEY_ACTION, PageEventData.ACTION_TOGGLE_NOTIFY).append(PageEventData.KEY_VALUE, "#NotifyRow #CheckBox.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ItemAddButton", EventData.of(PageEventData.KEY_ACTION, PageEventData.ACTION_ADD_EXACT));

        rebuildListBindings(eventBuilder, profile);
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

        if (data.action == null) {
            return;
        }

        AutoTrashPlayerSettings.AutoTrashProfile profile = this.playerSettings.getActiveProfile();
        if (profile == null) {
            return;
        }

        boolean changed = false;
        switch (data.action) {
            case PageEventData.ACTION_SWITCH_PROFILE -> {
                if (data.profileName == null || data.profileName.isBlank()) {
                    return;
                }
                changed = this.playerSettings.activateProfile(data.profileName);
            }
            case PageEventData.ACTION_ADD_PROFILE -> {
                AutoTrashPlayerSettings.ProfileActionResult result = this.playerSettings.createProfile(data.profileName);
                changed = handleProfileAction(player, result, this.playerSettings.getActiveProfileName());
            }
            case PageEventData.ACTION_DUPLICATE_PROFILE -> {
                AutoTrashPlayerSettings.ProfileActionResult result = this.playerSettings.createProfile(data.profileName, true);
                changed = handleProfileAction(player, result, this.playerSettings.getActiveProfileName());
            }
            case PageEventData.ACTION_RENAME_PROFILE -> {
                String currentName = this.playerSettings.getActiveProfileName();
                AutoTrashPlayerSettings.ProfileActionResult result = this.playerSettings.renameProfile(currentName, data.profileName);
                changed = handleProfileAction(player, result, this.playerSettings.getActiveProfileName());
            }
            case PageEventData.ACTION_DELETE_PROFILE -> {
                String target = data.profileName != null && !data.profileName.isBlank()
                        ? data.profileName
                        : this.playerSettings.getActiveProfileName();
                AutoTrashPlayerSettings.ProfileActionResult result = this.playerSettings.deleteProfile(target);
                changed = handleProfileAction(player, result, target);
            }
            case PageEventData.ACTION_SCAN_INVENTORY -> {
                handleInventoryScan(player, profile);
                changed = true;
            }
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
                if (held == null || ItemStack.isEmpty(held)) {
                    player.sendMessage(Message.raw("Hold an item to add it to the auto-trash list."));
                    rebuild();
                    return;
                }
                String itemId = held.getItemId();
                String[] current = profile.getExactItems();
                String[] updated = updateArray(current, true, itemId);
                if (updated != current) {
                    profile.setExactItems(updated);
                    changed = true;
                }
                if (this.playerSettings.isEnabled()) {
                    player.getInventory().getHotbar().removeItemStackFromSlot(player.getInventory().getActiveHotbarSlot());
                    if (this.playerSettings.isNotify()) {
                        sendTrashNotification(player, held);
                    }
                }
            }
            case PageEventData.ACTION_REMOVE_EXACT -> {
                String itemId = data.itemId;
                if (itemId == null || itemId.isBlank()) {
                    player.sendMessage(Message.raw("Click a row to remove it from the auto-trash list."));
                    rebuild();
                    return;
                }
                String[] current = profile.getExactItems();
                String[] updated = updateArray(current, false, itemId);
                if (updated != current) {
                    profile.setExactItems(updated);
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
     * @param items item ids to list
     */
    private void buildFilterList(@NonNullDecl UICommandBuilder commandBuilder, @NonNullDecl String[] items) {
        int index = 0;
        for (String itemId : items) {
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            String rowSelector = "#ItemGrid" + "[" + index + "]";
            commandBuilder.append("#ItemGrid", "Pages/AutoTrashItemSlotRow.ui");
            commandBuilder.set(rowSelector + " #ItemSlot.ItemId", itemId);
            index++;
        }
    }

    /**
     * Builds the profile dropdown entries and selection.
     *
     * @param commandBuilder UI command builder
     * @param settings player settings
     */
    private void buildProfileDropdown(@NonNullDecl UICommandBuilder commandBuilder, @NonNullDecl AutoTrashPlayerSettings settings) {
        List<DropdownEntryInfo> entries = new ArrayList<>();
        for (String profileName : settings.getProfileNames()) {
            entries.add(new DropdownEntryInfo(LocalizableString.fromString(profileName), profileName));
        }
        commandBuilder.set("#ProfileDropdown.Entries", entries);
        commandBuilder.set("#ProfileDropdown.Value", settings.getActiveProfileName());
    }

    private boolean handleProfileAction(@NonNullDecl Player player, @NonNullDecl AutoTrashPlayerSettings.ProfileActionResult result,
            @NonNullDecl String profileName) {
        String displayName = profileName.isBlank() ? "profile" : "\"" + profileName + "\"";
        switch (result) {
            case AutoTrashPlayerSettings.ProfileActionResult.CREATED -> {
                player.sendMessage(Message.raw("Profile " + displayName + " created."));
                return true;
            }
            case AutoTrashPlayerSettings.ProfileActionResult.DUPLICATED -> {
                player.sendMessage(Message.raw("Profile duplicated."));
                return true;
            }
            case AutoTrashPlayerSettings.ProfileActionResult.RENAMED -> {
                player.sendMessage(Message.raw("Profile renamed."));
                return true;
            }
            case AutoTrashPlayerSettings.ProfileActionResult.DELETED -> {
                player.sendMessage(Message.raw("Profile " + displayName + " deleted."));
                return true;
            }
            case AutoTrashPlayerSettings.ProfileActionResult.NAME_TAKEN -> player.sendMessage(Message.raw("That profile name is already in use."));
            case AutoTrashPlayerSettings.ProfileActionResult.LIMIT_REACHED -> player.sendMessage(Message.raw("You can only have 50 profiles."));
            case AutoTrashPlayerSettings.ProfileActionResult.NAME_EMPTY -> player.sendMessage(Message.raw("Enter a profile name."));
            case AutoTrashPlayerSettings.ProfileActionResult.NOT_FOUND -> player.sendMessage(Message.raw("Profile not found."));
            case AutoTrashPlayerSettings.ProfileActionResult.LAST_PROFILE -> player.sendMessage(Message.raw("You must keep at least one profile."));
            default -> {
            }
        }
        return false;
    }

    private void handleInventoryScan(@NonNullDecl Player player, @NonNullDecl AutoTrashPlayerSettings.AutoTrashProfile profile) {
        List<Short> slotsToRemove = new ArrayList<>();
        player.getInventory().getCombinedEverything().forEach((slot, stack) -> {
            if (stack == null || ItemStack.isEmpty(stack)) {
                return;
            }
            String itemId = stack.getItemId();
            for (String exactItem : profile.getExactItems()) {
                if (itemId.equals(exactItem)) {
                    slotsToRemove.add(slot);
                    break;
                }
            }
        });
        if (slotsToRemove.isEmpty()) {
            player.sendMessage(Message.raw("No inventory items matched this profile."));
            return;
        }
        for (short slot : slotsToRemove) {
            player.getInventory().getCombinedEverything().removeItemStackFromSlot(slot);
        }
        player.sendMessage(Message.raw("Removed " + slotsToRemove.size() + " items from inventory using this profile."));
    }

    /**
     * Sends a trash notification for the provided item stack.
     *
     * @param player player to notify
     * @param itemStack item stack to display
     */
    private void sendTrashNotification(@NonNullDecl Player player, @NonNullDecl ItemStack itemStack) {
        AutoTrashSystem.sendTrashNotification(player, itemStack);
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
        boolean changed = false;
        if (add) {
            if (!items.contains(itemId)) {
                items.add(itemId);
                changed = true;
            }
        } else {
            changed = items.remove(itemId);
        }
        if (!changed) {
            return values;
        }
        return items.toArray(String[]::new);
    }

    /**
     * Rebuilds UI event bindings for the list of configured items.
     *
     * @param eventBuilder UI event builder
     * @param profile auto-trash profile
     */
    private void rebuildListBindings(@NonNullDecl UIEventBuilder eventBuilder, @NonNullDecl AutoTrashPlayerSettings.AutoTrashProfile profile) {
        int index = 0;
        for (String itemId : profile.getExactItems()) {
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            String rowSelector = "#ItemGrid[" + index + "]";
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, rowSelector,
                    EventData.of(PageEventData.KEY_ACTION, PageEventData.ACTION_REMOVE_EXACT).append(PageEventData.KEY_ITEM, itemId), false);
            index++;
        }
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
     * Event payload for the UI page.
     */
    public static class PageEventData {
        /**
         * Creates an empty event payload for decoding.
         */
        public PageEventData() {
        }

        /**
         * Payload key for the action identifier.
         */
        public static final String KEY_ACTION = "Action";
        /**
         * Payload key for boolean toggle values.
         */
        public static final String KEY_VALUE = "@Value";
        /**
         * Payload key for item identifiers.
         */
        public static final String KEY_ITEM = "Item";
        /**
         * Payload key for profile names.
         */
        public static final String KEY_PROFILE = "@Profile";
        /**
         * Action id for toggling the enabled setting.
         */
        public static final String ACTION_TOGGLE_ENABLED = "ToggleEnabled";
        /**
         * Action id for toggling notification setting.
         */
        public static final String ACTION_TOGGLE_NOTIFY = "ToggleNotify";
        /**
         * Action id for adding an exact item.
         */
        public static final String ACTION_ADD_EXACT = "AddExact";
        /**
         * Action id for removing an exact item.
         */
        public static final String ACTION_REMOVE_EXACT = "RemoveExact";
        /**
         * Action id for switching profiles.
         */
        public static final String ACTION_SWITCH_PROFILE = "SwitchProfile";
        /**
         * Action id for creating profiles.
         */
        public static final String ACTION_ADD_PROFILE = "AddProfile";
        /**
         * Action id for duplicating profiles.
         */
        public static final String ACTION_DUPLICATE_PROFILE = "DuplicateProfile";
        /**
         * Action id for renaming profiles.
         */
        public static final String ACTION_RENAME_PROFILE = "RenameProfile";
        /**
         * Action id for deleting profiles.
         */
        public static final String ACTION_DELETE_PROFILE = "DeleteProfile";
        /**
         * Action id for scanning inventory.
         */
        public static final String ACTION_SCAN_INVENTORY = "ScanInventory";

        /**
         * Codec for serializing UI event data payloads.
         */
        public static final BuilderCodec<PageEventData> CODEC = BuilderCodec.builder(PageEventData.class, PageEventData::new)
                .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (data, value) -> data.action = value, data -> data.action)
                .add()
                .append(new KeyedCodec<>(KEY_VALUE, Codec.BOOLEAN), (data, value) -> data.value = value, data -> data.value)
                .add()
                .append(new KeyedCodec<>(KEY_ITEM, Codec.STRING), (data, value) -> data.itemId = value, data -> data.itemId)
                .add()
                .append(new KeyedCodec<>(KEY_PROFILE, Codec.STRING), (data, value) -> data.profileName = value, data -> data.profileName)
                .add()
                .append(new KeyedCodec<>(KEY_ITEM.toUpperCase(), Codec.STRING), (data, value) -> {
                    if (data.itemId == null || data.itemId.isBlank()) {
                        data.itemId = value;
                    }
                }, _ -> null)
                .add()
                .append(new KeyedCodec<>(KEY_PROFILE.toUpperCase(), Codec.STRING), (data, value) -> {
                    if (data.profileName == null || data.profileName.isBlank()) {
                        data.profileName = value;
                    }
                }, _ -> null)
                .add()
                .build();

        /**
         * Incoming action identifier.
         */
        public String action;
        /**
         * Incoming toggle value, if present.
         */
        public Boolean value;
        /**
         * Incoming item identifier, if present.
         */
        public String itemId;
        /**
         * Incoming profile name, if present.
         */
        public String profileName;
    }
}
