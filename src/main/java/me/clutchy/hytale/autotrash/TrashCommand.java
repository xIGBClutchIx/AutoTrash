package me.clutchy.hytale.autotrash;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.util.Config;

/**
 * Command collection for managing auto-trash rules.
 */
public final class TrashCommand extends AbstractCommandCollection {

    private final Config<AutoTrashPlugin.AutoTrashRules> rulesConfig;

    public TrashCommand(@Nonnull Config<AutoTrashPlugin.AutoTrashRules> rulesConfig) {
        super("trash", "autotrash.command.trash.desc");
        this.rulesConfig = rulesConfig;
        addSubCommand(new TrashAddCommand());
        addSubCommand(new TrashRemoveCommand());
        addSubCommand(new TrashListCommand());
        addSubCommand(new TrashContainsCommand());
    }

    private AutoTrashPlugin.AutoTrashRules getRules() {
        return rulesConfig.get();
    }

    private void saveRules(@Nonnull AutoTrashPlugin.AutoTrashRules rules) {
        rulesConfig.save().join();
        AutoTrashSystem.updateRules(rules.getExactItems(), rules.getContainsItems());
    }

    private static List<String> toMutableList(String[] values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static String normalizeValue(String value) {
        return value == null ? "" : value.trim();
    }

    private final class TrashAddCommand extends CommandBase {
        private final RequiredArg<String> itemArg = withRequiredArg("item", "autotrash.command.trash.add.item", ArgTypes.STRING);

        private TrashAddCommand() {
            super("add", "autotrash.command.trash.add.desc");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String item = normalizeValue(itemArg.get(context));
            if (item.isEmpty()) {
                context.sendMessage(Message.raw("Item id cannot be empty."));
                return;
            }

            AutoTrashPlugin.AutoTrashRules rules = getRules();
            List<String> items = toMutableList(rules.getExactItems());
            if (items.contains(item)) {
                context.sendMessage(Message.raw("Already in exact list: " + item));
                return;
            }

            items.add(item);
            rules.setExactItems(items.toArray(String[]::new));
            saveRules(rules);
            context.sendMessage(Message.raw("Added exact item: " + item));
        }
    }

    private final class TrashRemoveCommand extends CommandBase {
        private final RequiredArg<String> itemArg = withRequiredArg("item", "autotrash.command.trash.remove.item", ArgTypes.STRING);

        private TrashRemoveCommand() {
            super("remove", "autotrash.command.trash.remove.desc");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String item = normalizeValue(itemArg.get(context));
            AutoTrashPlugin.AutoTrashRules rules = getRules();
            List<String> items = toMutableList(rules.getExactItems());
            if (!items.remove(item)) {
                context.sendMessage(Message.raw("Not in exact list: " + item));
                return;
            }

            rules.setExactItems(items.toArray(String[]::new));
            saveRules(rules);
            context.sendMessage(Message.raw("Removed exact item: " + item));
        }
    }

    private final class TrashListCommand extends CommandBase {
        private TrashListCommand() {
            super("list", "autotrash.command.trash.list.desc");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            AutoTrashPlugin.AutoTrashRules rules = getRules();
            String exact = rules.getExactItems().length == 0 ? "(none)" : String.join(", ", rules.getExactItems());
            String contains = rules.getContainsItems().length == 0 ? "(none)" : String.join(", ", rules.getContainsItems());
            context.sendMessage(Message.raw("Exact items: " + exact));
            context.sendMessage(Message.raw("Contains rules: " + contains));
        }
    }

    private final class TrashContainsCommand extends AbstractCommandCollection {
        private TrashContainsCommand() {
            super("contains", "autotrash.command.trash.contains.desc");
            addSubCommand(new TrashContainsAddCommand());
            addSubCommand(new TrashContainsRemoveCommand());
            addSubCommand(new TrashContainsListCommand());
        }
    }

    private final class TrashContainsAddCommand extends CommandBase {
        private final RequiredArg<String> tokenArg = withRequiredArg("token", "autotrash.command.trash.contains.add.token", ArgTypes.STRING);

        private TrashContainsAddCommand() {
            super("add", "autotrash.command.trash.contains.add.desc");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String token = normalizeValue(tokenArg.get(context));
            if (token.isEmpty()) {
                context.sendMessage(Message.raw("Contains token cannot be empty."));
                return;
            }

            AutoTrashPlugin.AutoTrashRules rules = getRules();
            List<String> items = toMutableList(rules.getContainsItems());
            if (items.contains(token)) {
                context.sendMessage(Message.raw("Already in contains list: " + token));
                return;
            }

            items.add(token);
            rules.setContainsItems(items.toArray(String[]::new));
            saveRules(rules);
            context.sendMessage(Message.raw("Added contains rule: " + token));
        }
    }

    private final class TrashContainsRemoveCommand extends CommandBase {
        private final RequiredArg<String> tokenArg = withRequiredArg("token", "autotrash.command.trash.contains.remove.token", ArgTypes.STRING);

        private TrashContainsRemoveCommand() {
            super("remove", "autotrash.command.trash.contains.remove.desc");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String token = normalizeValue(tokenArg.get(context));
            AutoTrashPlugin.AutoTrashRules rules = getRules();
            List<String> items = toMutableList(rules.getContainsItems());
            if (!items.remove(token)) {
                context.sendMessage(Message.raw("Not in contains list: " + token));
                return;
            }

            rules.setContainsItems(items.toArray(String[]::new));
            saveRules(rules);
            context.sendMessage(Message.raw("Removed contains rule: " + token));
        }
    }

    private final class TrashContainsListCommand extends CommandBase {
        private TrashContainsListCommand() {
            super("list", "autotrash.command.trash.contains.list.desc");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            AutoTrashPlugin.AutoTrashRules rules = getRules();
            String contains = rules.getContainsItems().length == 0 ? "(none)" : String.join(", ", rules.getContainsItems());
            context.sendMessage(Message.raw("Contains rules: " + contains));
        }
    }
}
