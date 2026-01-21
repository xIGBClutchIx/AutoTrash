package me.clutchy.hytale.autotrash;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.clutchy.hytale.autotrash.command.TrashCommand;
import me.clutchy.hytale.autotrash.settings.AutoTrashPlayerSettings;
import me.clutchy.hytale.autotrash.system.AutoTrashSystem;

/**
 * Entrypoint plugin that wires the auto-trash inventory listener.
 */
@SuppressWarnings("unused")
public class AutoTrashPlugin extends JavaPlugin {

    /** Logger for plugin lifecycle events. */
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Component type used for player settings. */
    private ComponentType<EntityStore, AutoTrashPlayerSettings> settingsComponentType;

    /**
     * Creates the plugin and logs its initialization.
     *
     * @param init plugin initialization context
     */
    public AutoTrashPlugin(@NonNullDecl JavaPluginInit init) {
        super(init);
    }

    /** Registers the auto-trash inventory listener. */
    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin: AutoTrash");
        this.settingsComponentType = this.getEntityStoreRegistry().registerComponent(AutoTrashPlayerSettings.class, "AutoTrash", AutoTrashPlayerSettings.CODEC);
        AutoTrashSystem.setSettingsComponentType(settingsComponentType);
        getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, AutoTrashSystem::handleInventoryChange);
        getCommandRegistry().registerCommand(new TrashCommand(settingsComponentType));
    }
}
