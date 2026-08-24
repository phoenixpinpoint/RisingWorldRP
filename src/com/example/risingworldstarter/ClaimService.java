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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

final class ClaimService {
    private final Path dataFile;
    private final Map<String, Claim> claims = new HashMap<>();

    ClaimService(Path dataFile) {
        this.dataFile = Objects.requireNonNull(dataFile, "dataFile");
        load();
    }

    synchronized Optional<Claim> getClaim(int chunkX, int chunkZ) {
        return Optional.ofNullable(claims.get(key(chunkX, chunkZ)));
    }

    synchronized List<ClaimedChunk> getClaimsByOwner(String ownerUid) {
        return claims.entrySet().stream()
                .filter(entry -> entry.getValue().ownerUid().equals(ownerUid))
                .map(entry -> {
                    String[] coordinates = entry.getKey().split(",", 2);
                    return new ClaimedChunk(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]));
                })
                .sorted((left, right) -> {
                    int xComparison = Integer.compare(left.x(), right.x());
                    return xComparison != 0 ? xComparison : Integer.compare(left.z(), right.z());
                })
                .toList();
    }

    synchronized int getClaimCount() {
        return claims.size();
    }

    synchronized int deleteClaimsByOwner(String ownerUid) {
        int oldSize = claims.size();
        claims.entrySet().removeIf(entry -> entry.getValue().ownerUid().equals(ownerUid));
        int removed = oldSize - claims.size();
        if (removed > 0) save();
        return removed;
    }

    synchronized void migrateOwner(String oldOwnerUid, String newOwnerUid, String newOwnerName) {
        boolean changed = false;
        for (Map.Entry<String, Claim> entry : claims.entrySet()) {
            if (entry.getValue().ownerUid().equals(oldOwnerUid)) {
                entry.setValue(new Claim(newOwnerUid, newOwnerName));
                changed = true;
            }
        }
        if (changed) save();
    }

    synchronized boolean claim(int chunkX, int chunkZ, String ownerUid, String ownerName) {
        String key = key(chunkX, chunkZ);
        if (claims.containsKey(key)) {
            return false;
        }
        claims.put(key, new Claim(requireText(ownerUid, "ownerUid"), requireText(ownerName, "ownerName")));
        save();
        return true;
    }

    synchronized boolean unclaim(int chunkX, int chunkZ, String ownerUid) {
        String key = key(chunkX, chunkZ);
        Claim existing = claims.get(key);
        if (existing == null || !existing.ownerUid().equals(ownerUid)) {
            return false;
        }
        claims.remove(key);
        save();
        return true;
    }

    synchronized boolean forceUnclaim(int chunkX, int chunkZ) {
        if (claims.remove(key(chunkX, chunkZ)) == null) {
            return false;
        }
        save();
        return true;
    }

    private void load() {
        if (!Files.exists(dataFile)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(dataFile)) {
            properties.load(input);
            for (String chunkKey : properties.stringPropertyNames()) {
                String value = properties.getProperty(chunkKey);
                // The owner identifier may itself contain ':' (character UUIDs
                // use the "character:<uuid>" namespace). The final colon is
                // the stable separator before the Base64-encoded owner name.
                int separator = value.lastIndexOf(':');
                if (separator > 0 && separator + 1 < value.length()) {
                    String ownerUid = value.substring(0, separator);
                    String encodedName = value.substring(separator + 1);
                    String name = new String(Base64.getUrlDecoder().decode(encodedName), StandardCharsets.UTF_8);
                    claims.put(chunkKey, new Claim(ownerUid, name));
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not load claims from " + dataFile, exception);
        }
    }

    private void save() {
        Path temporaryFile = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
        Properties properties = new Properties();
        claims.forEach((chunk, claim) -> {
            String name = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(claim.ownerName().getBytes(StandardCharsets.UTF_8));
            properties.setProperty(chunk, claim.ownerUid() + ":" + name);
        });
        try {
            Files.createDirectories(dataFile.getParent());
            try (OutputStream output = Files.newOutputStream(temporaryFile)) {
                properties.store(output, "Rising World chunk claims");
            }
            try {
                Files.move(temporaryFile, dataFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporaryFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save claims to " + dataFile, exception);
        }
    }

    private static String key(int chunkX, int chunkZ) {
        return chunkX + "," + chunkZ;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
