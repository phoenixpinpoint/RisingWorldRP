package com.example.risingworldstarter.spawns;

import com.example.risingworldstarter.database.Database;

import java.sql.PreparedStatement;
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
        String key = requireCharacter(characterKey);
        String name = requireName(requestedName);
        database.write(connection -> {
            boolean exists;
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT 1 FROM custom_spawns WHERE character_key=? AND name=?")) {
                query.setString(1, key); query.setString(2, name);
                try (var row = query.executeQuery()) { exists = row.next(); }
            }
            if (!exists) {
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT COUNT(*) FROM custom_spawns WHERE character_key=?")) {
                    query.setString(1, key);
                    try (var row = query.executeQuery()) {
                        row.next();
                        if (row.getInt(1) >= MAX_SPAWNS)
                            throw new IllegalStateException("You can only have " + MAX_SPAWNS + " custom spawns.");
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO custom_spawns(character_key,name,position_x,position_y,position_z,"
                            + "rotation_x,rotation_y,rotation_z,rotation_w) VALUES(?,?,?,?,?,?,?,?,?) "
                            + "ON CONFLICT(character_key,name) DO UPDATE SET "
                            + "position_x=excluded.position_x,position_y=excluded.position_y,"
                            + "position_z=excluded.position_z,rotation_x=excluded.rotation_x,"
                            + "rotation_y=excluded.rotation_y,rotation_z=excluded.rotation_z,"
                            + "rotation_w=excluded.rotation_w")) {
                statement.setString(1, key); statement.setString(2, name);
                statement.setFloat(3, x); statement.setFloat(4, y); statement.setFloat(5, z);
                statement.setFloat(6, rotationX); statement.setFloat(7, rotationY);
                statement.setFloat(8, rotationZ); statement.setFloat(9, rotationW);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public Optional<CustomSpawn> find(String characterKey, String requestedName) {
        String key = requireCharacter(characterKey);
        String name = requireName(requestedName);
        return database.read(connection -> {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT name,position_x,position_y,position_z,rotation_x,rotation_y,rotation_z,rotation_w "
                            + "FROM custom_spawns WHERE character_key=? AND name=?")) {
                query.setString(1, key); query.setString(2, name);
                try (var row = query.executeQuery()) {
                    return row.next() ? Optional.of(readSpawn(row)) : Optional.empty();
                }
            }
        });
    }

    public List<CustomSpawn> getAll(String characterKey) {
        String key = requireCharacter(characterKey);
        return database.read(connection -> {
            List<CustomSpawn> spawns = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT name,position_x,position_y,position_z,rotation_x,rotation_y,rotation_z,rotation_w "
                            + "FROM custom_spawns WHERE character_key=? ORDER BY name COLLATE NOCASE")) {
                query.setString(1, key);
                try (var rows = query.executeQuery()) {
                    while (rows.next()) spawns.add(readSpawn(rows));
                }
            }
            return List.copyOf(spawns);
        });
    }

    public boolean delete(String characterKey, String requestedName) {
        String key = requireCharacter(characterKey);
        String name = requireName(requestedName);
        return database.transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM custom_spawns WHERE character_key=? AND name=?")) {
                statement.setString(1, key); statement.setString(2, name);
                return statement.executeUpdate() > 0;
            }
        });
    }

    public int deleteAll(String characterKey) {
        String key = requireCharacter(characterKey);
        return database.transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM custom_spawns WHERE character_key=?")) {
                statement.setString(1, key);
                return statement.executeUpdate();
            }
        });
    }

    private static CustomSpawn readSpawn(java.sql.ResultSet row) throws java.sql.SQLException {
        return new CustomSpawn(row.getString(1), row.getFloat(2), row.getFloat(3), row.getFloat(4),
                row.getFloat(5), row.getFloat(6), row.getFloat(7), row.getFloat(8));
    }

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
