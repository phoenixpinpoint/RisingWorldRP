package com.example.risingworldstarter;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class StoreCatalog {
    private static final Set<String> BLOCKED_ITEM_NAMES = Set.of(
            "clothingitem", "oldboot", "missingitem", "constructionitem",
            "objectkit", "objectkitsmall", "plantitem", "blueprint", "branch");
    private static final Pattern JSON_ITEM = Pattern.compile("\\\"(-?\\d+)\\\"\\s*:\\s*\\{(.*?)\\}", Pattern.DOTALL);
    private final List<StoreItem> items;

    private StoreCatalog(List<StoreItem> items) {
        this.items = List.copyOf(items);
    }

    static StoreCatalog empty() {
        return new StoreCatalog(List.of());
    }

    static StoreCatalog load(Path path) {
        Properties properties = new Properties();
        if (Files.exists(path)) {
            loadJson(path, properties);
        } else {
            Path legacyPath = path.resolveSibling("marketplace.properties");
            if (Files.exists(legacyPath)) {
                try (InputStream input = Files.newInputStream(legacyPath)) {
                    properties.load(input);
                    System.out.println("[CivicCore/DEBUG] Migrating legacy marketplace config from "
                            + legacyPath + " to " + path);
                } catch (IOException exception) {
                    throw new IllegalStateException("Could not load legacy marketplace settings from "
                            + legacyPath, exception);
                }
            }
        }

        List<StoreItem> enabledItems = new ArrayList<>();
        int blockedDefinitions = 0;
        List<Integer> configuredIds = properties.stringPropertyNames().stream()
                .map(key -> key.split("\\.", 3))
                .filter(parts -> parts.length == 3 && parts[0].equals("item"))
                .map(parts -> Integer.parseInt(parts[1]))
                .distinct().sorted().toList();
        for (int configuredId : configuredIds) {
            String prefix = "item." + configuredId + ".";
            String itemName = properties.getProperty(prefix + "name", "item-" + configuredId);
            String category = properties.getProperty(prefix + "category", "Other");
            boolean blocked = isBlocked(itemName, category);
            if (blocked) blockedDefinitions++;
            String configuredPrice = properties.getProperty(prefix + "price");
            if (!blocked && Boolean.parseBoolean(properties.getProperty(prefix + "enabled"))
                    && configuredPrice != null && !configuredPrice.isBlank()) {
                long price = toMinorUnits(configuredPrice, prefix + "price");
                enabledItems.add(new StoreItem((short) configuredId, itemName, category, price));
            }
        }
        System.out.println("[CivicCore/DEBUG] Marketplace blocked " + blockedDefinitions
                + " internal or NPC configured item(s)");

        enabledItems.sort(Comparator.comparing(StoreItem::category, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(StoreItem::name, String.CASE_INSENSITIVE_ORDER));
        return new StoreCatalog(enabledItems);
    }

    List<StoreItem> items() {
        return items;
    }

    private static boolean isBlocked(String itemName, String category) {
        String normalizedName = itemName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return BLOCKED_ITEM_NAMES.contains(normalizedName)
                || "npc".equalsIgnoreCase(category)
                || "npcs".equalsIgnoreCase(category);
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

    private static void loadJson(Path path, Properties properties) {
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Matcher itemMatcher = JSON_ITEM.matcher(json);
            while (itemMatcher.find()) {
                String prefix = "item." + itemMatcher.group(1) + ".";
                String body = itemMatcher.group(2);
                copyJsonString(body, "name", prefix, properties);
                copyJsonString(body, "category", prefix, properties);
                copyJsonScalar(body, "enabled", prefix, properties);
                copyJsonScalar(body, "price", prefix, properties);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load marketplace settings from " + path, exception);
        }
    }

    private static void copyJsonString(String body, String field, String prefix, Properties properties) {
        Matcher matcher = Pattern.compile("\\\"" + field + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
                .matcher(body);
        if (matcher.find()) {
            properties.setProperty(prefix + field, unescapeJson(matcher.group(1)));
        }
    }

    private static void copyJsonScalar(String body, String field, String prefix, Properties properties) {
        Matcher matcher = Pattern.compile("\\\"" + field + "\\\"\\s*:\\s*(true|false|-?\\d+(?:\\.\\d+)?)",
                Pattern.CASE_INSENSITIVE).matcher(body);
        if (matcher.find()) {
            properties.setProperty(prefix + field, matcher.group(1));
        }
    }

    private static void saveJson(Path path, Properties properties) {
        List<Integer> ids = properties.stringPropertyNames().stream()
                .map(key -> key.split("\\.", 3))
                .filter(parts -> parts.length == 3 && parts[0].equals("item"))
                .map(parts -> Integer.parseInt(parts[1]))
                .distinct().sorted().toList();
        StringBuilder json = new StringBuilder("{\n  \"items\": {\n");
        for (int index = 0; index < ids.size(); index++) {
            int id = ids.get(index);
            String prefix = "item." + id + ".";
            json.append("    \"").append(id).append("\": {\n")
                    .append("      \"name\": \"").append(escapeJson(properties.getProperty(prefix + "name", "item-" + id))).append("\",\n")
                    .append("      \"category\": \"").append(escapeJson(properties.getProperty(prefix + "category", "Other"))).append("\",\n")
                    .append("      \"enabled\": ").append(properties.getProperty(prefix + "enabled", "true"));
            String price = properties.getProperty(prefix + "price");
            if (price != null && !price.isBlank()) {
                json.append(",\n      \"price\": ").append(price.trim());
            }
            json.append("\n    }").append(index + 1 < ids.size() ? ",\n" : "\n");
        }
        json.append("  }\n}\n");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save marketplace settings to " + path, exception);
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String unescapeJson(String value) {
        return value.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
                .replace("\\\"", "\"").replace("\\\\", "\\");
    }

    record StoreItem(short id, String name, String category, long price) {
    }
}
