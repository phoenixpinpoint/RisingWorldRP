package com.example.risingworldstarter.database;

import org.bson.Document;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import static com.example.risingworldstarter.database.DocumentStore.*;

/** One-time, read-only SQLite import. Source files are never modified or removed. */
public final class SqliteMigrator {
    private static final String MARKER = "sqlite_import_v1";
    private static final Map<String, String> SEQUENCES = Map.of(
            "journal_sections", "section_id", "journal_pages", "page_id", "user_store_listings", "listing_id");
    private SqliteMigrator() { }

    public static boolean migrate(Database target, Path file) {
        if (!Files.isRegularFile(file) || target.read(s -> s.exists("metadata", doc("key", MARKER)))) return false;
        Map<String, List<Document>> rows = new LinkedHashMap<>();
        Map<String, Long> sequences = new HashMap<>();
        try {
            Class.forName("org.sqlite.JDBC");
            // URI read-only mode also prevents an accidental empty database from being created.
            try (Connection source = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath().toUri() + "?mode=ro")) {
                source.setAutoCommit(false);
                Set<String> tables = new HashSet<>();
                try (Statement query = source.createStatement(); ResultSet result = query.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
                    while (result.next()) tables.add(result.getString(1));
                }
                if (!tables.containsAll(Set.of("metadata", "balances", "accounts", "characters")))
                    throw new IllegalStateException("Source is not a recognized CivicCore SQLite database");
                for (String table : MongoSchema.KEYS.keySet()) {
                    List<Document> documents = new ArrayList<>();
                    if (tables.contains(table)) try (Statement query = source.createStatement(); ResultSet result = query.executeQuery("SELECT * FROM " + table)) {
                        ResultSetMetaData columns = result.getMetaData();
                        while (result.next()) {
                            Document document = new Document();
                            for (int i = 1; i <= columns.getColumnCount(); i++) document.put(columns.getColumnName(i), result.getObject(i));
                            if (Set.of("groups", "characters", "custom_spawns").contains(table))
                                document.put("name_key", MongoSchema.nameKey(document.getString("name")));
                            if (table.equals("chests")) document.put("locked", number(document, "locked") != 0);
                            documents.add(document);
                        }
                    }
                    rows.put(table, documents);
                }
                if (tables.contains("sqlite_sequence")) try (Statement query = source.createStatement(); ResultSet result = query.executeQuery("SELECT name,seq FROM sqlite_sequence")) {
                    while (result.next()) if (SEQUENCES.containsKey(result.getString(1))) sequences.put(result.getString(1), result.getLong(2));
                }
                source.rollback();
            }
        } catch (SQLException | ClassNotFoundException exception) {
            throw new IllegalStateException("Could not read existing CivicCore SQLite data; Atlas import was not started", exception);
        }
        return target.transaction(s -> {
            if (s.exists("metadata", doc("key", MARKER))) return false;
            for (String table : MongoSchema.KEYS.keySet())
                if (s.exists(table, doc())) throw new IllegalStateException("Atlas world already contains data; refusing to merge SQLite data. Use an empty world ID.");
            rows.forEach((table, documents) -> {
                for (int offset = 0; offset < documents.size(); offset += 500)
                    s.insertMany(table, documents.subList(offset, Math.min(offset + 500, documents.size())));
            });
            SEQUENCES.forEach((table, field) -> s.advanceId(table, Math.max(sequences.getOrDefault(table, 0L),
                    rows.get(table).stream().mapToLong(d -> number(d, field)).max().orElse(0L))));
            s.insert("metadata", doc("key", MARKER, "value", java.time.Instant.now().toString()));
            return true;
        });
    }
}
