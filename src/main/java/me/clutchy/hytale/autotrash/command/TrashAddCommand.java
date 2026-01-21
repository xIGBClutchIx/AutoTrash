package me.clutchy.hytale.autotrash.command;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.clutchy.hytale.autotrash.settings.AutoTrashPlayerSettings;

/**
 * Adds an item to the active auto-trash profile.
 */
public final class TrashAddCommand extends CommandBase {

    /** Component type used for player settings. */
    private final ComponentType<EntityStore, AutoTrashPlayerSettings> settingsComponentType;
    /** Required item id argument. */
    private final RequiredArg<String> itemArg;

    /**
     * Creates the command instance.
     *
     * @param settingsComponentType component type for player settings
     */
    public TrashAddCommand(@NonNullDecl ComponentType<EntityStore, AutoTrashPlayerSettings> settingsComponentType) {
        super("add", "Add an item to the auto-trash list.");
        this.setPermissionGroup(GameMode.Adventure);
        this.settingsComponentType = settingsComponentType;
        this.itemArg = withRequiredArg("item", "Item id", ArgTypes.STRING);
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

        String itemId = context.get(itemArg);
        if (itemId == null || itemId.isBlank()) {
            context.sendMessage(Message.raw("Provide an item id to add."));
            return;
        }
        if (!ItemModule.exists(itemId)) {
            context.sendMessage(Message.raw("Unknown item id: " + itemId));
            return;
        }

        Player player = context.senderAs(Player.class);
        AutoTrashPlayerSettings settings = AutoTrashPlayerSettings.get(player, settingsComponentType);
        if (settings == null) {
            context.sendMessage(Message.raw("Unable to update AutoTrash right now."));
            return;
        }
        if (!settings.addExactItem(itemId)) {
            context.sendMessage(Message.raw("That item is already in your auto-trash list."));
            return;
        }
        context.sendMessage(Message.raw("Added to auto-trash: " + itemId));
    }
}
