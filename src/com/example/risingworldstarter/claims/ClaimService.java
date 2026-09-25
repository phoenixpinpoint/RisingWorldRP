package com.example.risingworldstarter.claims;

import com.example.risingworldstarter.database.Database;
import com.example.risingworldstarter.database.DocumentStore;
import com.example.risingworldstarter.database.MongoSchema;
import org.bson.Document;
import static com.example.risingworldstarter.database.DocumentStore.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public final class ClaimService {
    private final Database database;
    public ClaimService(Database database) { this.database = database; }

    public Optional<Claim> getClaim(int chunkX, int chunkZ) {
        return database.read(s -> s.first("claims", doc("chunk_x", chunkX, "chunk_z", chunkZ))
                .map(d -> new Claim(d.getString("owner_id"), d.getString("owner_name"))));
    }

    public List<ClaimedChunk> getClaimsByOwner(String ownerUid) {
        return database.read(s -> s.find("claims", doc("owner_id", ownerUid), doc("chunk_x", 1, "chunk_z", 1))
                .stream().map(d -> new ClaimedChunk((int) number(d, "chunk_x"), (int) number(d, "chunk_z"))).toList());
    }

    /** Returns all claims inside an inclusive chunk-coordinate square in one query. */
    public Map<ClaimedChunk, Claim> getClaimsInArea(int minimumX, int maximumX,
                                                    int minimumZ, int maximumZ) {
        return database.read(s -> {
            Map<ClaimedChunk, Claim> result = new LinkedHashMap<>();
            for (Document d : s.find("claims", doc("chunk_x", doc("$gte", minimumX, "$lte", maximumX),
                    "chunk_z", doc("$gte", minimumZ, "$lte", maximumZ))))
                result.put(new ClaimedChunk((int) number(d, "chunk_x"), (int) number(d, "chunk_z")),
                        new Claim(d.getString("owner_id"), d.getString("owner_name")));
            return Map.copyOf(result);
        });
    }

    public int getClaimCount() {
        return database.read(s -> s.find("claims", doc()).size());
    }

    public int deleteClaimsByOwner(String ownerUid) {
        return database.transaction(s -> Math.toIntExact(s.delete("claims", doc("owner_id", ownerUid))));
    }

    public void migrateOwner(String oldOwnerUid, String newOwnerUid, String newOwnerName) {
        database.write(s -> { s.update("claims", doc("owner_id", oldOwnerUid),
                doc("owner_id", newOwnerUid, "owner_name", newOwnerName)); return null; });
    }

    public boolean claim(int chunkX, int chunkZ, String ownerUid, String ownerName) {
        requireText(ownerUid, "ownerUid"); requireText(ownerName, "ownerName");
        return database.transaction(s -> s.insertIfAbsent("claims", doc("chunk_x", chunkX, "chunk_z", chunkZ),
                doc("owner_id", ownerUid, "owner_name", ownerName)));
    }

    public boolean unclaim(int chunkX, int chunkZ, String ownerUid) {
        return database.transaction(s -> s.delete("claims", doc("chunk_x", chunkX, "chunk_z", chunkZ, "owner_id", ownerUid)) > 0);
    }

    public boolean forceUnclaim(int chunkX, int chunkZ) {
        return database.transaction(s -> s.delete("claims", doc("chunk_x", chunkX, "chunk_z", chunkZ)) > 0);
    }

    public void migrateLegacy(Path file) {
        if (!Files.isRegularFile(file)) return;
        Properties properties=new Properties();
        try (InputStream input=Files.newInputStream(file)) { properties.load(input); }
        catch (Exception exception) { throw new IllegalStateException("Could not migrate claims",exception); }
        for (String chunk:properties.stringPropertyNames()) {
            String[] coordinates=chunk.split(",",2); String value=properties.getProperty(chunk); int separator=value.lastIndexOf(':');
            if (coordinates.length!=2 || separator<1) continue;
            try { claim(Integer.parseInt(coordinates[0]),Integer.parseInt(coordinates[1]),value.substring(0,separator),new String(Base64.getUrlDecoder().decode(value.substring(separator+1)),StandardCharsets.UTF_8)); }
            catch (IllegalArgumentException ignored) { }
        }
    }

    private static void requireText(String value,String name) { if(value==null||value.isBlank()) throw new IllegalArgumentException(name+" must not be blank"); }
}
