package me.clutchy.hytale.autotrash.command;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.clutchy.hytale.autotrash.settings.AutoTrashPlayerSettings;

/**
 * Toggles auto-trash enable state.
 */
public final class TrashEnableCommand extends CommandBase {

    /** Component type used for player settings. */
    private final ComponentType<EntityStore, AutoTrashPlayerSettings> settingsComponentType;
    /** Optional enabled flag argument. */
    private final OptionalArg<Boolean> enabledArg;

    /**
     * Creates the command instance.
     *
     * @param settingsComponentType component type for player settings
     */
    public TrashEnableCommand(@NonNullDecl ComponentType<EntityStore, AutoTrashPlayerSettings> settingsComponentType) {
        super("enable", "Enable or disable auto-trash.");
        this.setPermissionGroup(GameMode.Adventure);
        this.settingsComponentType = settingsComponentType;
        this.enabledArg = withOptionalArg("enabled", "true/false", ArgTypes.BOOLEAN);
    }

    /**
     * Executes the command synchronously on the server thread.
     *
     * @param context command context
     */
    @Override
    protected void executeSync(@NonNullDecl CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("Only players can update AutoTrash."));
            return;
        }

        Player player = context.senderAs(Player.class);
        AutoTrashPlayerSettings settings = AutoTrashPlayerSettings.get(player, settingsComponentType);
        if (settings == null) {
            context.sendMessage(Message.raw("Unable to update AutoTrash right now."));
            return;
        }

        if (!context.provided(enabledArg)) {
            context.sendMessage(Message.raw("AutoTrash is " + (settings.isEnabled() ? "enabled" : "disabled") + "."));
            return;
        }

        boolean enabled = context.get(enabledArg);
        settings.setEnabled(enabled);
        context.sendMessage(Message.raw("AutoTrash is now " + (enabled ? "enabled" : "disabled") + "."));
    }
}
