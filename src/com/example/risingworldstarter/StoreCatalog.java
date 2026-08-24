package com.example.risingworldstarter;

import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

final class StoreCatalog {
    private static final String DEFAULT_PRICE = "100.00";
    private static final Set<String> BLOCKED_ITEM_NAMES = Set.of(
            "clothingitem", "oldboot", "missingitem", "constructionitem",
            "objectkit", "objectkitsmall", "plantitem", "blueprint");
    private final List<StoreItem> items;

    private StoreCatalog(List<StoreItem> items) {
        this.items = List.copyOf(items);
    }

    static StoreCatalog load(Path path) {
        Properties properties = new Properties();
        if (Files.exists(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException exception) {
                throw new IllegalStateException("Could not load marketplace settings from " + path, exception);
            }
        }

        boolean changed = false;
        List<StoreItem> enabledItems = new ArrayList<>();
        Items.ItemDefinition[] definitions = Definitions.getAllItemDefinitions();
        if (definitions == null) {
            throw new IllegalStateException("Rising World returned no item-definition catalog");
        }

        int skippedDefinitions = 0;
        int blockedDefinitions = 0;
        for (Items.ItemDefinition definition : definitions) {
            if (definition == null) {
                skippedDefinitions++;
                continue;
            }
            String itemName = definition.name == null || definition.name.isBlank()
                    ? "item-" + definition.id : definition.name;
            String category = definition.category == null ? "Other" : definition.category.name();
            boolean blocked = isBlocked(definition, itemName);
            String prefix = "item." + definition.id + ".";
            changed |= putDefault(properties, prefix + "name", itemName);
            changed |= putDefault(properties, prefix + "category", category);
            if (blocked) {
                changed |= setIfDifferent(properties, prefix + "enabled", "false");
                blockedDefinitions++;
            } else {
                changed |= putDefault(properties, prefix + "enabled", "true");
            }
            changed |= putDefault(properties, prefix + "price", DEFAULT_PRICE);

            if (!blocked && Boolean.parseBoolean(properties.getProperty(prefix + "enabled"))) {
                long price = toMinorUnits(properties.getProperty(prefix + "price"), prefix + "price");
                enabledItems.add(new StoreItem(definition.id, itemName, category, price));
            }
        }

        if (skippedDefinitions > 0) {
            System.out.println("[RisingWorldStarter/DEBUG] Marketplace skipped " + skippedDefinitions
                    + " empty item-definition slot(s) returned by Rising World");
        }
        System.out.println("[RisingWorldStarter/DEBUG] Marketplace blocked " + blockedDefinitions
                + " internal or NPC item definition(s)");

        if (changed || !Files.exists(path)) {
            try {
                Files.createDirectories(path.getParent());
                try (OutputStream output = Files.newOutputStream(path)) {
                    properties.store(output,
                            "Marketplace items. Set enabled=false to hide an item; prices are in dollars.");
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Could not save marketplace settings to " + path, exception);
            }
        }

        enabledItems.sort(Comparator.comparing(StoreItem::category, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(StoreItem::name, String.CASE_INSENSITIVE_ORDER));
        return new StoreCatalog(enabledItems);
    }

    List<StoreItem> items() {
        return items;
    }

    private static boolean putDefault(Properties properties, String key, String value) {
        if (properties.containsKey(key)) {
            return false;
        }
        properties.setProperty(key, value);
        return true;
    }

    private static boolean setIfDifferent(Properties properties, String key, String value) {
        if (value.equals(properties.getProperty(key))) {
            return false;
        }
        properties.setProperty(key, value);
        return true;
    }

    private static boolean isBlocked(Items.ItemDefinition definition, String itemName) {
        String normalizedName = itemName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return BLOCKED_ITEM_NAMES.contains(normalizedName)
                || definition.category == Items.Category.Npc
                || definition.type == Items.Type.Npc;
    }

    private static long toMinorUnits(String value, String settingName) {
        try {
            long amount = new BigDecimal(value).movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            if (amount < 0) {
                throw new IllegalArgumentException(settingName + " must not be negative");
            }
            return amount;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(
                    settingName + " must be a valid amount with at most two decimals", exception);
        }
    }

    record StoreItem(short id, String name, String category, long price) {
    }
}
