package com.example.risingworldstarter.tests;

import com.example.risingworldstarter.database.MongoSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class MongoSettingsTest {
    @TempDir Path root;
    @Test void environmentOverridesFileAndIdentityPersistsWithoutSecrets() throws Exception {
        Files.writeString(root.resolve("mongodb.properties"), "uri=mongodb://file/\ndatabase=filedb\n");
        Path world = root.resolve("world");
        var env = Map.of("CIVICCORE_MONGODB_URI", "mongodb+srv://user:secret@example.mongodb.net/", "CIVICCORE_MONGODB_DATABASE", "atlasdb");
        var first = MongoSettings.load(root, world, env);
        var second = MongoSettings.load(root, world, env);
        assertEquals(first.worldId(), second.worldId());
        assertNotEquals(first.worldId(), MongoSettings.load(root, root.resolve("other"), env).worldId());
        assertEquals(env.get("CIVICCORE_MONGODB_URI"), first.uri());
        assertEquals("atlasdb", first.database());
        assertFalse(first.toString().contains("secret"));
        assertFalse(Files.readString(world.resolve("mongodb-world-id.txt")).contains("secret"));
        assertEquals("filedb", MongoSettings.load(root, world, Map.of()).database());
    }
    @Test void rejectsMissingOrInvalidConfiguration() throws Exception {
        Path world = root.resolve("world");
        assertThrows(IllegalStateException.class, () -> MongoSettings.load(root, world, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> MongoSettings.load(root, world, Map.of("CIVICCORE_MONGODB_URI", "https://bad")));
        assertThrows(IllegalArgumentException.class, () -> MongoSettings.load(root, world, Map.of("CIVICCORE_MONGODB_URI", "mongodb://localhost", "CIVICCORE_MONGODB_DATABASE", "bad/name")));
        var env = Map.of("CIVICCORE_MONGODB_URI", "mongodb://localhost");
        assertEquals("civiccore", MongoSettings.load(root, world, env).database());
        Files.writeString(world.resolve("mongodb-world-id.txt"), " ");
        assertThrows(IllegalStateException.class, () -> MongoSettings.load(root, world, env));
        Path blocked = root.resolve("not-directory"); Files.writeString(blocked, "file");
        assertThrows(IllegalStateException.class, () -> MongoSettings.load(root, blocked, env));
    }
}
