package com.example.risingworldstarter.spawns;

import com.example.risingworldstarter.database.Database;
import com.example.risingworldstarter.database.DocumentStore;
import com.example.risingworldstarter.database.MongoSchema;
import org.bson.Document;
import static com.example.risingworldstarter.database.DocumentStore.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persistent, character-scoped custom teleport destinations. */
public final class CustomSpawnService {
    public static final int MAX_SPAWNS = 3;
    public static final int MAX_NAME_LENGTH = 24;
    private final Database database;

    public CustomSpawnService(Database database) { this.database = database; }

    public void save(String characterKey, String requestedName, float x, float y, float z,
                     float rotationX, float rotationY, float rotationZ, float rotationW) {
        String key = requireCharacter(characterKey), name = requireName(requestedName);
        database.write(s -> {
            Document filter = doc("character_key", key, "name_key", MongoSchema.nameKey(name));
            if (!s.exists("custom_spawns", filter) && s.find("custom_spawns", doc("character_key", key)).size() >= MAX_SPAWNS)
                throw new IllegalStateException("You can only have " + MAX_SPAWNS + " custom spawns.");
            Document values = doc("position_x", (double) x, "position_y", (double) y, "position_z", (double) z,
                    "rotation_x", (double) rotationX, "rotation_y", (double) rotationY,
                    "rotation_z", (double) rotationZ, "rotation_w", (double) rotationW);
            if (s.exists("custom_spawns", filter)) s.update("custom_spawns", filter, values);
            else s.insertIfAbsent("custom_spawns", filter, values.append("name", name));
            return null;
        });
    }

    public Optional<CustomSpawn> find(String characterKey, String requestedName) {
        String key = requireCharacter(characterKey), name = requireName(requestedName);
        return database.read(s -> s.first("custom_spawns", doc("character_key", key, "name_key", MongoSchema.nameKey(name)))
                .map(CustomSpawnService::readSpawn));
    }

    public List<CustomSpawn> getAll(String characterKey) {
        String key = requireCharacter(characterKey);
        return database.read(s -> s.find("custom_spawns", doc("character_key", key), doc("name_key", 1))
                .stream().map(CustomSpawnService::readSpawn).toList());
    }

    public boolean delete(String characterKey, String requestedName) {
        String key = requireCharacter(characterKey), name = requireName(requestedName);
        return database.transaction(s -> s.delete("custom_spawns", doc("character_key", key, "name_key", MongoSchema.nameKey(name))) > 0);
    }

    public int deleteAll(String characterKey) {
        String key = requireCharacter(characterKey);
        return database.transaction(s -> Math.toIntExact(s.delete("custom_spawns", doc("character_key", key))));
    }

    private static CustomSpawn readSpawn(Document row) {
        return new CustomSpawn(row.getString("name"), f(row, "position_x"), f(row, "position_y"), f(row, "position_z"),
                f(row, "rotation_x"), f(row, "rotation_y"), f(row, "rotation_z"), f(row, "rotation_w"));
    }

    private static float f(Document d, String key) { return ((Number) d.get(key)).floatValue(); }

    private static String requireCharacter(String value) {
        String key = value == null ? "" : value.trim();
        if (key.isEmpty()) throw new IllegalArgumentException("A character is required.");
        return key;
    }

    private static String requireName(String value) {
        String name = value == null ? "" : value.trim();
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9_-]{0," + (MAX_NAME_LENGTH - 1) + "}"))
            throw new IllegalArgumentException("Spawn names must be 1-" + MAX_NAME_LENGTH
                    + " characters using letters, numbers, underscores, or hyphens.");
        return name;
    }
}
