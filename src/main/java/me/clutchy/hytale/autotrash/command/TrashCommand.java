package me.clutchy.hytale.autotrash.command;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.clutchy.hytale.autotrash.settings.AutoTrashPlayerSettings;
import me.clutchy.hytale.autotrash.system.AutoTrashSystem;
import me.clutchy.hytale.autotrash.ui.AutoTrashConfigPage;

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
        super("trash", "Open the auto-trash configuration UI.");
        this.setPermissionGroup(GameMode.Adventure);
        this.settingsComponentType = settingsComponentType;
        addSubCommand(new TrashAddCommand(settingsComponentType));
        addSubCommand(new TrashEnableCommand(settingsComponentType));
        addSubCommand(new TrashNotifyCommand(settingsComponentType));
        addSubCommand(new TrashOnCommand(settingsComponentType));
        addSubCommand(new TrashOffCommand(settingsComponentType));
        addSubCommand(new TrashRemoveCommand(settingsComponentType));
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

            player.getPageManager().openCustomPage(player.getReference(), player.getReference().getStore(),
                    new AutoTrashConfigPage(playerRef, settingsComponentType));
        });
    }
}
