package com.example.risingworldstarter.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Secrets live in the server environment or plugin directory, never in a world save. */
public final class MongoSettings {
    private final String uri;
    private final String database;
    private final String worldId;
    private MongoSettings(String uri, String database, String worldId) {
        this.uri = uri; this.database = database; this.worldId = worldId;
    }
    public String uri() { return uri; }
    public String database() { return database; }
    public String worldId() { return worldId; }
    @Override public String toString() { return "MongoSettings[credentials redacted]"; }

    public static MongoSettings load(Path pluginPath, Path worldPath) {
        return load(pluginPath, worldPath, System.getenv());
    }
    public static MongoSettings load(Path pluginPath, Path worldPath, Map<String, String> environment) {
        Properties properties = new Properties();
        Path config = pluginPath.resolve("mongodb.properties");
        try {
            if (Files.isRegularFile(config)) try (var input = Files.newInputStream(config)) { properties.load(input); }
            String uri = value(environment, properties, "CIVICCORE_MONGODB_URI", "uri", "");
            String database = value(environment, properties, "CIVICCORE_MONGODB_DATABASE", "database", "civiccore");
            if (uri.isBlank()) throw new IllegalStateException("Set CIVICCORE_MONGODB_URI or uri in Plugins/CivicCore/mongodb.properties before enabling CivicCore");
            if (!uri.startsWith("mongodb+srv://") && !uri.startsWith("mongodb://"))
                throw new IllegalArgumentException("MongoDB URI must start with mongodb+srv:// or mongodb://");
            if (!database.matches("[A-Za-z0-9_-]{1,63}")) throw new IllegalArgumentException("Invalid MongoDB database name");
            Files.createDirectories(worldPath);
            Path identity = worldPath.resolve("mongodb-world-id.txt");
            String world;
            if (Files.exists(identity)) world = Files.readString(identity).trim();
            else {
                world = UUID.randomUUID().toString();
                Files.writeString(identity, world + System.lineSeparator(), java.nio.file.StandardOpenOption.CREATE_NEW);
            }
            if (!world.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalStateException("Invalid mongodb-world-id.txt");
            return new MongoSettings(uri, database, world);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load MongoDB configuration or world identity", exception);
        }
    }
    private static String value(Map<String, String> env, Properties properties, String variable, String key, String fallback) {
        String value = env.get(variable);
        return value != null && !value.isBlank() ? value.trim() : properties.getProperty(key, fallback).trim();
    }
}
