package com.example.risingworldstarter.tests;

import com.example.risingworldstarter.database.*;
import com.example.risingworldstarter.economy.DatabaseEconomyService;
import com.example.risingworldstarter.spawns.CustomSpawnService;
import com.example.risingworldstarter.claims.ChestService;
import org.bson.types.Binary;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import static com.example.risingworldstarter.database.DocumentStore.*;
import static org.junit.jupiter.api.Assertions.*;

final class SqliteMigratorTest extends MongoTestSupport {
    @Test void importsEveryCollectionAndBinaryDataOnceWithoutChangingSource() throws Exception {
        Path file = fixture();
        byte[] original = Files.readAllBytes(file);
        assertTrue(SqliteMigrator.migrate(database, file));
        assertFalse(SqliteMigrator.migrate(database, file));
        database.read(s -> {
            for (String collection : MongoSchema.KEYS.keySet())
                assertEquals(collection.equals("metadata") ? 2 : 1, s.find(collection, doc()).size(), collection);
            var character = s.first("characters", doc("character_id", "char")).orElseThrow();
            assertArrayEquals(new byte[]{0, 1, 2, (byte) 255}, character.get("inventory", Binary.class).getData());
            assertNull(character.get("clothes"));
            assertEquals("state-value", character.getString("state"));
            assertEquals("profile-value", s.first("accounts", doc()).orElseThrow().getString("profile_state"));
            assertEquals("role", s.first("metadata", doc("key", "legacy")).orElseThrow().getString("value"));
            return null;
        });
        assertEquals(9_000_000_000L, new DatabaseEconomyService(database).getBalance("char"));
        assertTrue(new ChestService(database).get(42, 1, 2, 3).orElseThrow().locked());
        assertEquals(1.25f, new CustomSpawnService(database).find("char", "HOME").orElseThrow().x());
        assertEquals(101L, database.transaction(s -> s.nextId("journal_sections")).longValue());
        assertEquals(12L, database.transaction(s -> s.nextId("journal_pages")).longValue());
        assertEquals(14L, database.transaction(s -> s.nextId("user_store_listings")).longValue());
        assertArrayEquals(original, Files.readAllBytes(file));
        // Re-import cannot resurrect deleted records or roll back new balances.
        new DatabaseEconomyService(database).setBalance("char", 3);
        assertFalse(SqliteMigrator.migrate(database, file));
        assertEquals(3, new DatabaseEconomyService(database).getBalance("char"));
    }

    @Test void refusesToMergeAndLeavesBothSidesUntouched() throws Exception {
        Path file = fixture();
        new DatabaseEconomyService(database).setBalance("existing", 7);
        assertThrows(IllegalStateException.class, () -> SqliteMigrator.migrate(database, file));
        assertEquals(7, new DatabaseEconomyService(database).getBalance("existing"));
        assertFalse(new DatabaseEconomyService(database).hasAccount("char"));
        assertFalse(database.read(s -> s.exists("metadata", doc("key", "sqlite_import_v1"))).booleanValue());
    }

    @Test void missingInvalidAndUnrecognizedSourcesCannotPopulateAtlas() throws Exception {
        Path missing = temporaryDirectory.resolve("missing.db");
        assertFalse(SqliteMigrator.migrate(database, missing));
        assertFalse(Files.exists(missing));
        Path invalid = temporaryDirectory.resolve("bad.db");
        Files.writeString(invalid, "not sqlite");
        assertThrows(IllegalStateException.class, () -> SqliteMigrator.migrate(database, invalid));
        Path empty = temporaryDirectory.resolve("empty.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + empty)) {
            connection.createStatement().execute("CREATE TABLE unrelated(id INTEGER)");
        }
        assertThrows(IllegalStateException.class, () -> SqliteMigrator.migrate(database, empty));
    }

    @Test void failedImportRollsBackAllCollectionsAndCanBeRetried() throws Exception {
        Path file = fixture();
        // The historical SQL schema permits a blob larger than MongoDB's document limit.
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file); var q = c.prepareStatement("UPDATE characters SET inventory=?")) {
            q.setBytes(1, new byte[17 * 1024 * 1024]); q.executeUpdate();
        }
        assertThrows(RuntimeException.class, () -> SqliteMigrator.migrate(database, file));
        database.read(s -> {
            for (String collection : MongoSchema.KEYS.keySet()) assertTrue(s.find(collection, doc()).isEmpty(), collection);
            return null;
        });
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file); var q = c.createStatement()) {
            q.executeUpdate("UPDATE characters SET inventory=X'000102FF'");
        }
        assertTrue(SqliteMigrator.migrate(database, file));
    }

    private Path fixture() throws Exception {
        Path file = temporaryDirectory.resolve("civiccore.db");
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file); var statement = c.createStatement()) {
            try (var input = getClass().getResourceAsStream("/com/example/risingworldstarter/database/schema.sql")) {
                String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^\\s*--.*(?:\\R|$)", "");
                for (String sql : schema.split(";")) if (!sql.isBlank()) statement.execute(sql);
            }
            for (String sql : new String[]{
                    "INSERT INTO metadata VALUES('legacy','role')",
                    "INSERT INTO balances VALUES('char',9000000000)",
                    "INSERT INTO claims VALUES(1,3,'char','Player')",
                    "INSERT INTO claim_admins VALUES('admin','Admin')",
                    "INSERT INTO chests VALUES(42,1,2,3,'char','Player',1)",
                    "INSERT INTO accounts VALUES('account','Profile','profile-value')",
                    "INSERT INTO characters VALUES('char','account',1,'Player','state-value',X'000102FF',NULL)",
                    "INSERT INTO groups VALUES('group','Builders')",
                    "INSERT INTO group_members VALUES('char','group','Player','OWNER')",
                    "INSERT INTO group_invitations VALUES('guest','group')",
                    "INSERT INTO journal_sections VALUES(7,'char','Notes',1)",
                    "INSERT INTO journal_sections VALUES(100,'char','Deleted',2)",
                    "DELETE FROM journal_sections WHERE section_id=100",
                    "INSERT INTO journal_pages VALUES(11,7,1,'Journal content')",
                    "INSERT INTO custom_spawns VALUES('char','Home',1.25,2,3,0,0,0,1)",
                    "INSERT INTO user_store_listings(listing_id,seller_key,seller_name,item_type,item_variant,quantity,price) VALUES(13,'char','Player',65535,2,4,500)"
            }) statement.executeUpdate(sql);
        }
        return file;
    }
}
