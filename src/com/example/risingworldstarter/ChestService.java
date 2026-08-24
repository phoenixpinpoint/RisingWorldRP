package com.example.risingworldstarter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

final class ChestService {
    private final Path dataFile;
    private final Map<String, ChestOwnership> chests = new HashMap<>();

    ChestService(Path dataFile) {
        this.dataFile = dataFile;
        load();
    }

    synchronized Optional<ChestOwnership> get(long globalId, int chunkX, int chunkY, int chunkZ) {
        return Optional.ofNullable(chests.get(key(globalId, chunkX, chunkY, chunkZ)));
    }

    synchronized ChestOwnership assign(long globalId, int chunkX, int chunkY, int chunkZ,
                                       String ownerUid, String ownerName) {
        String key = key(globalId, chunkX, chunkY, chunkZ);
        ChestOwnership existing = chests.get(key);
        if (existing != null) return existing;
        ChestOwnership created = new ChestOwnership(ownerUid, ownerName, false);
        chests.put(key, created);
        save();
        return created;
    }

    synchronized ChestOwnership setLocked(long globalId, int chunkX, int chunkY, int chunkZ,
                                          ChestOwnership ownership, boolean locked) {
        ChestOwnership updated = new ChestOwnership(ownership.ownerUid(), ownership.ownerName(), locked);
        chests.put(key(globalId, chunkX, chunkY, chunkZ), updated);
        save();
        return updated;
    }

    synchronized void remove(long globalId, int chunkX, int chunkY, int chunkZ) {
        if (chests.remove(key(globalId, chunkX, chunkY, chunkZ)) != null) save();
    }

    private void load() {
        if (!Files.exists(dataFile)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(dataFile)) {
            properties.load(input);
            for (String key : properties.stringPropertyNames()) {
                String[] values = properties.getProperty(key).split(":", 3);
                if (values.length != 3) continue;
                String ownerUid = new String(Base64.getUrlDecoder().decode(values[0]), StandardCharsets.UTF_8);
                String name = new String(Base64.getUrlDecoder().decode(values[1]), StandardCharsets.UTF_8);
                chests.put(key, new ChestOwnership(ownerUid, name, Boolean.parseBoolean(values[2])));
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not load chest ownership from " + dataFile, exception);
        }
    }

    private void save() {
        Path temporary = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
        Properties properties = new Properties();
        chests.forEach((key, chest) -> properties.setProperty(key,
                Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(chest.ownerUid().getBytes(StandardCharsets.UTF_8))
                        + ":" + Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(chest.ownerName().getBytes(StandardCharsets.UTF_8))
                        + ":" + chest.locked()));
        try {
            Files.createDirectories(dataFile.getParent());
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Rising World chest ownership and locks");
            }
            try {
                Files.move(temporary, dataFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, dataFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save chest ownership to " + dataFile, exception);
        }
    }

    private static String key(long globalId, int chunkX, int chunkY, int chunkZ) {
        return chunkX + "," + chunkY + "," + chunkZ + "," + globalId;
    }
}
