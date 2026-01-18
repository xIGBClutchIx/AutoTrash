package me.clutchy.hytale.autotrash;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Entrypoint plugin that wires the auto-trash inventory listener.
 */
@SuppressWarnings("unused")
public class AutoTrashPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ComponentType<EntityStore, AutoTrashPlayerSettings> settingsComponentType;

    /**
     * Creates the plugin and logs its initialization.
     *
     * @param init plugin initialization context
     */
    public AutoTrashPlugin(@NonNullDecl JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Initializing plugin " + this.getName());
        this.settingsComponentType = this.getEntityStoreRegistry().registerComponent(AutoTrashPlayerSettings.class, "AutoTrash", AutoTrashPlayerSettings.CODEC);
    }

    /** Registers the auto-trash inventory listener. */
    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin " + this.getName());
        AutoTrashSystem.setSettingsComponentType(settingsComponentType);
        getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, AutoTrashSystem::handleInventoryChange);
        getCommandRegistry().registerCommand(new TrashCommand(settingsComponentType));
    }
}
