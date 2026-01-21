package me.clutchy.hytale.autotrash.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Stores per-player auto-trash settings.
 *
 * <p>Backed by the Hytale component system and serialized through {@link #CODEC}.
 */
public class AutoTrashPlayerSettings implements Component<EntityStore> {

    /** Maximum number of profiles a player can store. */
    public static final int MAX_PROFILES = 50;
    /** Default profile name created on first use. */
    public static final String DEFAULT_PROFILE_NAME = "Default";
    private static final int DATA_VERSION = 1;
    private static final MapCodec<AutoTrashProfile, Map<String, AutoTrashProfile>> PROFILES_CODEC =
            new MapCodec<>(AutoTrashProfile.CODEC, LinkedHashMap::new);

    /** Codec used to serialize and validate auto-trash settings. */
    public static final BuilderCodec<AutoTrashPlayerSettings> CODEC = BuilderCodec.builder(AutoTrashPlayerSettings.class, AutoTrashPlayerSettings::new)
            .append(new KeyedCodec<>("Version", Codec.INTEGER), AutoTrashPlayerSettings::setDataVersion, AutoTrashPlayerSettings::getDataVersion)
            .documentation("Schema version for profile data.").add()
            .append(new KeyedCodec<>("Profiles", PROFILES_CODEC), AutoTrashPlayerSettings::setProfiles, AutoTrashPlayerSettings::getProfiles)
            .documentation("Named auto-trash profiles.").add()
            .append(new KeyedCodec<>("ActiveProfile", Codec.STRING), AutoTrashPlayerSettings::setActiveProfileName, AutoTrashPlayerSettings::getActiveProfileName)
            .documentation("Name of the active profile.").add()
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN), AutoTrashPlayerSettings::setEnabled, AutoTrashPlayerSettings::isEnabled)
            .documentation("Global enabled flag for auto-trash.").add()
            .append(new KeyedCodec<>("Notify", Codec.BOOLEAN), AutoTrashPlayerSettings::setNotify, AutoTrashPlayerSettings::isNotify)
            .documentation("Global notify flag for auto-trash.").add()
            .append(new KeyedCodec<>("ExactItems", Codec.STRING_ARRAY), AutoTrashPlayerSettings::setLegacyExactItems, settings -> null)
            .documentation("Legacy exact item ids for migration.").add()
            .build();

    private int dataVersion;
    private Map<String, AutoTrashProfile> profiles = new LinkedHashMap<>();
    private String activeProfileName = DEFAULT_PROFILE_NAME;
    private boolean enabled = true;
    private boolean notify = true;
    private String[] legacyExactItems = new String[0];

    /** Creates a new settings instance with defaults. */
    public AutoTrashPlayerSettings() {
    }

    /**
     * Fetches or creates the settings component for the player.
     *
     * @param player player to retrieve settings for
     * @param type component type for settings
     * @return the settings component for the player
     */
    @NullableDecl
    public static AutoTrashPlayerSettings get(@NonNullDecl Player player, @NonNullDecl ComponentType<EntityStore, AutoTrashPlayerSettings> type) {
        Holder<EntityStore> holder = player.toHolder();
        holder.ensureComponent(type);
        return holder.getComponent(type);
    }

    /**
     * Returns the active profile.
     *
     * @return active auto-trash profile
     */
    @NonNullDecl
    public AutoTrashProfile getActiveProfile() {
        ensureProfiles();
        return profiles.get(activeProfileName);
    }

    /**
     * Returns the active profile name.
     *
     * @return active profile name
     */
    @NonNullDecl
    public String getActiveProfileName() {
        ensureProfiles();
        return activeProfileName;
    }

    /**
     * Switches the active profile name when it exists.
     *
     * @param profileName profile name to activate
     * @return {@code true} when the profile exists and was activated
     */
    public boolean activateProfile(@NonNullDecl String profileName) {
        ensureProfiles();
        if (profileName == null || profileName.isBlank()) {
            return false;
        }
        if (!profiles.containsKey(profileName)) {
            return false;
        }
        this.activeProfileName = profileName;
        return true;
    }

    /**
     * Returns the profile names in insertion order.
     *
     * @return list of profile names
     */
    @NonNullDecl
    public List<String> getProfileNames() {
        ensureProfiles();
        return new ArrayList<>(profiles.keySet());
    }

    /**
     * Attempts to create a new profile.
     *
     * @param profileName profile name
     * @return result of the creation attempt
     */
    @NonNullDecl
    public ProfileActionResult createProfile(@NonNullDecl String profileName) {
        return createProfile(profileName, false);
    }

    /**
     * Attempts to create a new profile, optionally cloning the active one.
     *
     * @param profileName profile name
     * @param duplicateFromActive true to clone the active profile
     * @return result of the creation attempt
     */
    public ProfileActionResult createProfile(@NonNullDecl String profileName, boolean duplicateFromActive) {
        ensureProfiles();
        if (profileName == null || profileName.isBlank()) {
            return ProfileActionResult.NAME_EMPTY;
        }
        String trimmed = profileName.trim();
        if (trimmed.isEmpty()) {
            return ProfileActionResult.NAME_EMPTY;
        }
        if (profiles.containsKey(trimmed)) {
            return ProfileActionResult.NAME_TAKEN;
        }
        if (profiles.size() >= MAX_PROFILES) {
            return ProfileActionResult.LIMIT_REACHED;
        }
        AutoTrashProfile profile = duplicateFromActive ? getActiveProfile().copy() : new AutoTrashProfile();
        profiles.put(trimmed, profile);
        activeProfileName = trimmed;
        dataVersion = DATA_VERSION;
        return duplicateFromActive ? ProfileActionResult.DUPLICATED : ProfileActionResult.CREATED;
    }

    /**
     * Renames a profile, updating the active profile reference when needed.
     *
     * @param currentName profile name to rename
     * @param newName new profile name
     * @return result of the rename attempt
     */
    @NonNullDecl
    public ProfileActionResult renameProfile(@NonNullDecl String currentName, @NonNullDecl String newName) {
        ensureProfiles();
        if (currentName == null || currentName.isBlank()) {
            return ProfileActionResult.NOT_FOUND;
        }
        if (newName == null || newName.isBlank()) {
            return ProfileActionResult.NAME_EMPTY;
        }
        String trimmedNew = newName.trim();
        if (trimmedNew.isEmpty()) {
            return ProfileActionResult.NAME_EMPTY;
        }
        String trimmedCurrent = currentName.trim();
        if (trimmedCurrent.isEmpty()) {
            return ProfileActionResult.NOT_FOUND;
        }
        AutoTrashProfile profile = profiles.get(trimmedCurrent);
        if (profile == null) {
            return ProfileActionResult.NOT_FOUND;
        }
        if (!trimmedCurrent.equals(trimmedNew) && profiles.containsKey(trimmedNew)) {
            return ProfileActionResult.NAME_TAKEN;
        }
        if (trimmedCurrent.equals(trimmedNew)) {
            return ProfileActionResult.RENAMED;
        }
        profiles.remove(trimmedCurrent);
        profiles.put(trimmedNew, profile);
        if (trimmedCurrent.equals(activeProfileName)) {
            activeProfileName = trimmedNew;
        }
        dataVersion = DATA_VERSION;
        return ProfileActionResult.RENAMED;
    }

    /**
     * Deletes the named profile if it exists and preserves a valid active profile.
     *
     * @param profileName profile name to delete
     * @return result of the delete attempt
     */
    @NonNullDecl
    public ProfileActionResult deleteProfile(@NonNullDecl String profileName) {
        ensureProfiles();
        if (profileName == null || profileName.isBlank()) {
            return ProfileActionResult.NOT_FOUND;
        }
        String trimmed = profileName.trim();
        if (trimmed.isEmpty() || !profiles.containsKey(trimmed)) {
            return ProfileActionResult.NOT_FOUND;
        }
        if (profiles.size() <= 1) {
            return ProfileActionResult.LAST_PROFILE;
        }
        profiles.remove(trimmed);
        if (!profiles.containsKey(activeProfileName)) {
            activeProfileName = profiles.keySet().iterator().next();
        }
        dataVersion = DATA_VERSION;
        return ProfileActionResult.DELETED;
    }

    /**
     * Returns the number of profiles available.
     *
     * @return profile count
     */
    public int getProfileCount() {
        ensureProfiles();
        return profiles.size();
    }

    /**
     * Returns whether the profile limit has been reached.
     *
     * @return true when at the profile limit
     */
    public boolean isProfileLimitReached() {
        ensureProfiles();
        return profiles.size() >= MAX_PROFILES;
    }

    /**
     * Updates the profile map, copying values defensively.
     *
     * @param profiles profiles map to apply
     */
    public void setProfiles(Map<String, AutoTrashProfile> profiles) {
        if (profiles == null) {
            this.profiles = new LinkedHashMap<>();
            return;
        }
        Map<String, AutoTrashProfile> copied = new LinkedHashMap<>();
        for (Map.Entry<String, AutoTrashProfile> entry : profiles.entrySet()) {
            AutoTrashProfile profile = entry.getValue();
            copied.put(entry.getKey(), profile == null ? new AutoTrashProfile() : profile.copy());
        }
        this.profiles = copied;
    }

    /**
     * Returns the profiles map, ensuring migration has run.
     *
     * @return profiles map
     */
    @NonNullDecl
    public Map<String, AutoTrashProfile> getProfiles() {
        ensureProfiles();
        return profiles;
    }

    /**
     * Sets the data schema version.
     *
     * @param dataVersion schema version
     */
    private void setDataVersion(int dataVersion) {
        this.dataVersion = dataVersion;
    }

    /**
     * Returns the schema version, running pending migrations if needed.
     *
     * @return schema version
     */
    private int getDataVersion() {
        runMigrations();
        return dataVersion;
    }

    /**
     * Sets the active profile name.
     *
     * @param activeProfileName active profile name
     */
    private void setActiveProfileName(String activeProfileName) {
        this.activeProfileName = activeProfileName;
    }

    /**
     * Returns whether auto-trash is enabled.
     *
     * @return true when enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether auto-trash is enabled.
     *
     * @param enabled enabled state
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns whether auto-trash notifications are enabled.
     *
     * @return true when notifications are enabled
     */
    public boolean isNotify() {
        return notify;
    }

    /**
     * Sets whether auto-trash notifications are enabled.
     *
     * @param notify notification state
     */
    public void setNotify(boolean notify) {
        this.notify = notify;
    }

    /**
     * Adds an item id to the active profile when missing.
     *
     * @param itemId item id to add
     * @return true if added
     */
    public boolean addExactItem(@NonNullDecl String itemId) {
        AutoTrashProfile profile = getActiveProfile();
        String[] existing = profile.getExactItems();
        for (String current : existing) {
            if (itemId.equals(current)) {
                return false;
            }
        }
        String[] updated = Arrays.copyOf(existing, existing.length + 1);
        updated[existing.length] = itemId;
        profile.setExactItems(updated);
        return true;
    }

    /**
     * Removes the provided item id from the active profile.
     *
     * @param itemId item id to remove
     * @return true if removed
     */
    public boolean removeExactItem(@NonNullDecl String itemId) {
        AutoTrashProfile profile = getActiveProfile();
        List<String> updated = new ArrayList<>();
        boolean removed = false;
        for (String current : profile.getExactItems()) {
            if (itemId.equals(current)) {
                removed = true;
                continue;
            }
            updated.add(current);
        }
        if (removed) {
            profile.setExactItems(updated.toArray(new String[0]));
        }
        return removed;
    }

    /**
     * Captures legacy exact items for migration from v0 settings.
     *
     * @param legacyExactItems legacy item ids
     */
    private void setLegacyExactItems(String[] legacyExactItems) {
        this.legacyExactItems = legacyExactItems == null ? new String[0] : Arrays.copyOf(legacyExactItems, legacyExactItems.length);
    }

    /**
     * Ensures profiles exist and migrations are applied.
     */
    private void ensureProfiles() {
        if (profiles == null) {
            profiles = new LinkedHashMap<>();
        }
        runMigrations();
        if (profiles.isEmpty()) {
            profiles.put(DEFAULT_PROFILE_NAME, new AutoTrashProfile());
        }
        if (activeProfileName == null || activeProfileName.isBlank() || !profiles.containsKey(activeProfileName)) {
            activeProfileName = profiles.keySet().iterator().next();
        }
    }

    /**
     * Runs incremental migrations up to the latest schema.
     */
    private void runMigrations() {
        if (dataVersion >= DATA_VERSION) {
            return;
        }
        int version = dataVersion;
        while (version < DATA_VERSION) {
            switch (version) {
                case 0 -> {
                    migrateV0ToV1();
                    version = 1;
                }
                default -> version = DATA_VERSION;
            }
        }
        dataVersion = version;
    }

    /**
     * Migrates legacy v0 data into the v1 profile structure.
     */
    private void migrateV0ToV1() {
        AutoTrashProfile profile = profiles.get(DEFAULT_PROFILE_NAME);
        if (profile == null) {
            profile = new AutoTrashProfile();
            profiles.put(DEFAULT_PROFILE_NAME, profile);
        }
        if (legacyExactItems != null && legacyExactItems.length > 0) {
            profile.setExactItems(legacyExactItems);
        }
        legacyExactItems = new String[0];
        if (activeProfileName == null || activeProfileName.isBlank()) {
            activeProfileName = DEFAULT_PROFILE_NAME;
        }
    }

    /**
     * Creates a deep copy of the settings.
     *
     * @return a cloned copy of these settings
     */
    @NonNullDecl
    public AutoTrashPlayerSettings copy() {
        AutoTrashPlayerSettings settings = new AutoTrashPlayerSettings();
        settings.dataVersion = this.dataVersion;
        settings.activeProfileName = this.activeProfileName;
        settings.enabled = this.enabled;
        settings.notify = this.notify;
        settings.setProfiles(this.profiles);
        return settings;
    }

    /** Creates a deep copy of the settings via {@code Cloneable}. */
    @NonNullDecl
    @Override
    public AutoTrashPlayerSettings clone() {
        return copy();
    }

    /**
     * Outcome of profile management operations.
     */
    /**
     * Outcome of profile management operations.
     */
    public enum ProfileActionResult {
        /** Profile was created. */
        CREATED,
        /** Profile was created by duplicating the active one. */
        DUPLICATED,
        /** Profile was renamed. */
        RENAMED,
        /** Profile was deleted. */
        DELETED,
        /** Profile name was empty or blank. */
        NAME_EMPTY,
        /** Profile name already exists. */
        NAME_TAKEN,
        /** Profile limit has been reached. */
        LIMIT_REACHED,
        /** Profile could not be found. */
        NOT_FOUND,
        /** Last remaining profile cannot be deleted. */
        LAST_PROFILE
    }

    /**
     * Stores per-profile auto-trash settings.
     */
    public static class AutoTrashProfile {
        /** Codec used to serialize profile data. */
        public static final BuilderCodec<AutoTrashProfile> CODEC = BuilderCodec.builder(AutoTrashProfile.class, AutoTrashProfile::new)
                .append(new KeyedCodec<>("ExactItems", Codec.STRING_ARRAY), AutoTrashProfile::setExactItems, AutoTrashProfile::getExactItems)
                .documentation("Exact item ids to delete on pickup for this profile.").add()
                .build();

        private String[] exactItems = new String[0];

        /** Creates a new profile with no filters. */
        public AutoTrashProfile() {
        }

        /**
         * Creates a profile with prepopulated item filters.
         *
         * @param exactItems exact item ids to remove
         */
        public AutoTrashProfile(String[] exactItems) {
            this.exactItems = exactItems == null ? new String[0] : Arrays.copyOf(exactItems, exactItems.length);
        }

        /**
         * Returns the exact item filters.
         *
         * @return exact item ids
         */
        public String[] getExactItems() {
            return exactItems;
        }

        /**
         * Replaces the exact item filters.
         *
         * @param exactItems exact item ids
         */
        public void setExactItems(String[] exactItems) {
            this.exactItems = exactItems == null ? new String[0] : Arrays.copyOf(exactItems, exactItems.length);
        }

        /**
         * Creates a deep copy of the profile.
         *
         * @return profile copy
         */
        @NonNullDecl
        public AutoTrashProfile copy() {
            AutoTrashProfile profile = new AutoTrashProfile();
            profile.exactItems = Arrays.copyOf(this.exactItems, this.exactItems.length);
            return profile;
        }
    }

}
