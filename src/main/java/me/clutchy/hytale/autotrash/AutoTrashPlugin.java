package me.clutchy.hytale.autotrash;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

/**
 * Entrypoint plugin that wires the auto-trash inventory listener.
 */
public class AutoTrashPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Codec for the auto-trash rules stored in config.json.
     */
    private static final BuilderCodec<AutoTrashRules> RULES_CODEC = BuilderCodec.builder(AutoTrashRules.class, AutoTrashRules::new)
            .documentation("Auto-trash item matching rules.")
            .append(new KeyedCodec<>("ExactItems", Codec.STRING_ARRAY), (rules, items) -> rules.exactItems = items, rules -> rules.exactItems)
            .documentation("Exact item ids to delete on pickup.")
            .add()
            .append(new KeyedCodec<>("ContainsItems", Codec.STRING_ARRAY), (rules, items) -> rules.containsItems = items, rules -> rules.containsItems)
            .documentation("Item id substrings to delete on pickup.")
            .add()
            .build();

    /**
     * Loaded config containing auto-trash rules.
     */
    private final Config<AutoTrashRules> rulesConfig;

    /**
     * Creates the plugin and logs its initialization.
     *
     * @param init plugin initialization context
     */
    public AutoTrashPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Initializing plugin " + this.getName());
        this.rulesConfig = withConfig(RULES_CODEC);
    }

    /**
     * Registers the auto-trash inventory listener.
     */
    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin " + this.getName());
        AutoTrashRules rules = normalizeRules(this.rulesConfig.get());
        AutoTrashSystem.updateRules(rules.getExactItems(), rules.getContainsItems());
        getEventRegistry().registerGlobal(
                LivingEntityInventoryChangeEvent.class,
                AutoTrashSystem::handleInventoryChange
        );
        getCommandRegistry().registerCommand(new TrashCommand(rulesConfig));
    }

    /**
     * Normalizes and guards against null config lists.
     *
     * @param rules raw config rules
     * @return normalized rules
     */
    private static AutoTrashRules normalizeRules(@Nonnull AutoTrashRules rules) {
        rules.setExactItems(normalizeList(rules.getExactItems()));
        rules.setContainsItems(normalizeList(rules.getContainsItems()));
        return rules;
    }

    /**
     * Trims any null/blank config values.
     *
     * @param values raw config values
     * @return normalized array
     */
    private static String[] normalizeList(String[] values) {
        if (values == null) {
            return new String[0];
        }
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            if (value != null) {
                values[i] = value.trim();
            }
        }
        return values;
    }

    /**
     * Configuration model for auto-trash rules.
     */
    static final class AutoTrashRules {
        private String[] exactItems = new String[] { "Food_Wildmeat_Raw" };
        private String[] containsItems = new String[0];

        String[] getExactItems() {
            return exactItems;
        }

        void setExactItems(String[] exactItems) {
            this.exactItems = exactItems;
        }

        String[] getContainsItems() {
            return containsItems;
        }

        void setContainsItems(String[] containsItems) {
            this.containsItems = containsItems;
        }
    }
}
