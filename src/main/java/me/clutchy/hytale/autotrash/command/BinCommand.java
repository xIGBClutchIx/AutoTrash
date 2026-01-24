package me.clutchy.hytale.autotrash.command;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Opens a temporary one-slot trash bin window.
 */
public final class BinCommand extends CommandBase {

    private static final short BIN_CAPACITY = 1;

    /**
     * Creates the command instance.
     */
    public BinCommand() {
        super("bin", "Open a temporary trash bin slot.");
        this.setPermissionGroup(GameMode.Adventure);
    }

    /**
     * Executes the command synchronously on the server thread.
     *
     * @param context command context
     */
    @Override
    protected void executeSync(@NonNullDecl CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("Only players can open the trash bin."));
            return;
        }

        Player player = context.senderAs(Player.class);
        if (player.getReference() == null) {
            context.sendMessage(Message.raw("Unable to open the trash bin right now."));
            return;
        }

        Ref<EntityStore> ref = player.getReference();
        Store<EntityStore> store = ref.getStore();

        player.getWorld().execute(() -> {
            ItemContainer container = new AutoClearingContainer(BIN_CAPACITY, ref, store);
            ContainerWindow window = new ContainerWindow(container);
            window.registerCloseEvent(event -> container.clear());

            boolean opened = player.getPageManager().setPageWithWindows(ref, store, Page.Inventory, true, window);
            if (!opened) {
                context.sendMessage(Message.raw("Unable to open the trash bin right now."));
                return;
            }
            context.sendMessage(Message.raw("Items dropped in the Trash Can are destroyed immediately."));
        });
    }

    private static final class AutoClearingContainer extends SimpleItemContainer {

        private static final String SOUND_EVENT_PLAYER_DROP_ITEM = "SFX_Player_Drop_Item";

        private final Ref<EntityStore> ref;
        private final Store<EntityStore> store;
        private boolean isClearing;

        private AutoClearingContainer(short capacity, @NonNullDecl Ref<EntityStore> ref, @NonNullDecl Store<EntityStore> store) {
            super(capacity);
            this.ref = ref;
            this.store = store;
        }

        @Override
        protected ItemStack internal_setSlot(short slot, ItemStack stack) {
            ItemStack previous = super.internal_setSlot(slot, stack);
            if (this.isClearing || stack == null || ItemStack.isEmpty(stack)) {
                return previous;
            }
            this.isClearing = true;
            int soundEventIndex = resolveSoundEventIndex(SOUND_EVENT_PLAYER_DROP_ITEM);
            SoundUtil.playSoundEvent2d(this.ref, soundEventIndex, SoundCategory.UI, this.store);
            clear();
            this.isClearing = false;
            return previous;
        }

        private static int resolveSoundEventIndex(@NonNullDecl String soundEventId) {
            int soundEventIndex = SoundEvent.getAssetMap().getIndex(soundEventId);
            return soundEventIndex == Integer.MIN_VALUE ? 0 : soundEventIndex;
        }
    }
}
