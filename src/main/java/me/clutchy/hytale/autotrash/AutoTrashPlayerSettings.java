package me.clutchy.hytale.autotrash;

import java.util.Arrays;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
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

    /** Codec used to serialize and validate auto-trash settings. */
    public static final BuilderCodec<AutoTrashPlayerSettings> CODEC = BuilderCodec.builder(AutoTrashPlayerSettings.class, AutoTrashPlayerSettings::new)
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN), AutoTrashPlayerSettings::setEnabled, AutoTrashPlayerSettings::isEnabled)
            .addValidator(Validators.nonNull()).documentation("Controls whether auto-trash is enabled for this player.").add()
            .append(new KeyedCodec<>("Notify", Codec.BOOLEAN), AutoTrashPlayerSettings::setNotify, AutoTrashPlayerSettings::isNotify)
            .addValidator(Validators.nonNull()).documentation("Controls whether trash notifications are shown.").add()
            .append(new KeyedCodec<>("ExactItems", Codec.STRING_ARRAY), AutoTrashPlayerSettings::setExactItems, AutoTrashPlayerSettings::getExactItems)
            .documentation("Exact item ids to delete on pickup for this player.").add()
            .build();

    private boolean enabled = true;
    private boolean notify = true;
    private String[] exactItems = new String[0];

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
     * Returns whether auto-trash is enabled.
     *
     * @return {@code true} when auto-trash is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Updates whether auto-trash is enabled.
     *
     * @param enabled {@code true} to enable auto-trash
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns whether notifications are enabled.
     *
     * @return {@code true} when notifications are enabled
     */
    public boolean isNotify() {
        return notify;
    }

    /**
     * Updates whether notifications are enabled.
     *
     * @param notify {@code true} to show trash notifications
     */
    public void setNotify(boolean notify) {
        this.notify = notify;
    }

    /**
     * Returns the configured exact item ids.
     *
     * @return configured exact item ids
     */
    public String[] getExactItems() {
        return exactItems;
    }

    /**
     * Replaces the exact item ids, copying the array defensively.
     *
     * @param exactItems exact item ids to auto-trash, or {@code null} for none
     */
    public void setExactItems(String[] exactItems) {
        this.exactItems = exactItems == null ? new String[0] : Arrays.copyOf(exactItems, exactItems.length);
    }

    /**
     * Creates a deep copy of the settings.
     *
     * @return a cloned copy of these settings
     */
    @NonNullDecl
    public AutoTrashPlayerSettings copy() {
        AutoTrashPlayerSettings settings = new AutoTrashPlayerSettings();
        settings.enabled = this.enabled;
        settings.notify = this.notify;
        settings.exactItems = Arrays.copyOf(this.exactItems, this.exactItems.length);
        return settings;
    }

    /** Creates a deep copy of the settings via {@code Cloneable}. */
    @NonNullDecl
    @Override
    public AutoTrashPlayerSettings clone() {
        try {
            AutoTrashPlayerSettings settings = (AutoTrashPlayerSettings) super.clone();
            settings.exactItems = Arrays.copyOf(this.exactItems, this.exactItems.length);
            return settings;
        } catch (CloneNotSupportedException ex) {
            return this.copy();
        }
    }
}
