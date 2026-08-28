package com.example.risingworldstarter.claims;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public final class ClaimAdminService {
    private final Path dataFile;
    private final Map<String, String> admins = new LinkedHashMap<>();

    public ClaimAdminService(Path dataFile) {
        this.dataFile = Objects.requireNonNull(dataFile, "dataFile");
        load();
    }

    public synchronized boolean contains(String playerUid) {
        return admins.containsKey(playerUid);
    }

    public synchronized void add(String playerUid, String playerName) {
        admins.put(playerUid, playerName);
        save();
    }

    public synchronized boolean remove(String playerUid) {
        if (admins.remove(playerUid) == null) {
            return false;
        }
        save();
        return true;
    }

    public synchronized Map<String, String> getAll() {
        return Map.copyOf(admins);
    }

    private void load() {
        if (!Files.exists(dataFile)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(dataFile)) {
            properties.load(input);
            for (String uid : properties.stringPropertyNames()) {
                String name = new String(Base64.getUrlDecoder().decode(properties.getProperty(uid)),
                        StandardCharsets.UTF_8);
                admins.put(uid, name);
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not load claim administrators from " + dataFile, exception);
        }
    }

    private void save() {
        Path temporaryFile = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
        Properties properties = new Properties();
        admins.forEach((uid, name) -> properties.setProperty(uid, Base64.getUrlEncoder().withoutPadding()
                .encodeToString(name.getBytes(StandardCharsets.UTF_8))));
        try {
            Files.createDirectories(dataFile.getParent());
            try (OutputStream output = Files.newOutputStream(temporaryFile)) {
                properties.store(output, "Whitelisted Rising World claim administrators");
            }
            try {
                Files.move(temporaryFile, dataFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporaryFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save claim administrators to " + dataFile, exception);
        }
    }
}
