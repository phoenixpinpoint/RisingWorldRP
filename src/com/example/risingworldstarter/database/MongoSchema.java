package com.example.risingworldstarter.database;

import java.util.Map;
import java.util.List;

public final class MongoSchema {
    private MongoSchema() { }
    public static final Map<String, List<List<String>>> KEYS = Map.ofEntries(
        Map.entry("metadata", List.of(List.of("key"))),
        Map.entry("balances", List.of(List.of("account_id"))),
        Map.entry("claims", List.of(List.of("chunk_x", "chunk_z"))),
        Map.entry("claim_admins", List.of(List.of("player_uid"))),
        Map.entry("chests", List.of(List.of("global_id", "chunk_x", "chunk_y", "chunk_z"))),
        Map.entry("accounts", List.of(List.of("account_uid"))),
        Map.entry("characters", List.of(List.of("character_id"), List.of("account_uid", "slot"))),
        Map.entry("groups", List.of(List.of("group_id"), List.of("name_key"))),
        Map.entry("group_members", List.of(List.of("character_key"))),
        Map.entry("group_invitations", List.of(List.of("character_key"))),
        Map.entry("journal_sections", List.of(List.of("section_id"), List.of("character_key", "section_order"))),
        Map.entry("journal_pages", List.of(List.of("page_id"), List.of("section_id", "page_number"))),
        Map.entry("custom_spawns", List.of(List.of("character_key", "name_key"))),
        Map.entry("user_store_listings", List.of(List.of("listing_id")))
    );
    /** Matches SQLite NOCASE: only ASCII letters are folded. */
    public static String nameKey(String name) {
        StringBuilder key = new StringBuilder(name.length());
        for (char c : name.toCharArray()) key.append(c >= 'A' && c <= 'Z' ? (char) (c + 32) : c);
        return key.toString();
    }
}
