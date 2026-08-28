package com.example.risingworldstarter.claims;

import com.example.risingworldstarter.database.Database;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public final class ClaimService {
    private final Database database;
    public ClaimService(Database database) { this.database = database; }

    public Optional<Claim> getClaim(int chunkX, int chunkZ) {
        return database.read(connection -> {
            try (PreparedStatement query = connection.prepareStatement("SELECT owner_id, owner_name FROM claims WHERE chunk_x=? AND chunk_z=?")) {
                query.setInt(1, chunkX); query.setInt(2, chunkZ);
                try (ResultSet result = query.executeQuery()) {
                    return result.next() ? Optional.of(new Claim(result.getString(1), result.getString(2))) : Optional.empty();
                }
            }
        });
    }

    public List<ClaimedChunk> getClaimsByOwner(String ownerUid) {
        return database.read(connection -> {
            List<ClaimedChunk> chunks = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement("SELECT chunk_x, chunk_z FROM claims WHERE owner_id=? ORDER BY chunk_x, chunk_z")) {
                query.setString(1, ownerUid);
                try (ResultSet result = query.executeQuery()) { while (result.next()) chunks.add(new ClaimedChunk(result.getInt(1), result.getInt(2))); }
            }
            return List.copyOf(chunks);
        });
    }

    public int getClaimCount() {
        return database.read(connection -> { try (var query=connection.prepareStatement("SELECT COUNT(*) FROM claims"); var result=query.executeQuery()) { return result.getInt(1); } });
    }

    public int deleteClaimsByOwner(String ownerUid) {
        return database.transaction(connection -> { try (PreparedStatement statement=connection.prepareStatement("DELETE FROM claims WHERE owner_id=?")) { statement.setString(1, ownerUid); return statement.executeUpdate(); } });
    }

    public void migrateOwner(String oldOwnerUid, String newOwnerUid, String newOwnerName) {
        database.write(connection -> { try (PreparedStatement statement=connection.prepareStatement("UPDATE claims SET owner_id=?, owner_name=? WHERE owner_id=?")) { statement.setString(1,newOwnerUid); statement.setString(2,newOwnerName); statement.setString(3,oldOwnerUid); statement.executeUpdate(); } return null; });
    }

    public boolean claim(int chunkX, int chunkZ, String ownerUid, String ownerName) {
        requireText(ownerUid,"ownerUid"); requireText(ownerName,"ownerName");
        return database.transaction(connection -> { try (PreparedStatement statement=connection.prepareStatement("INSERT INTO claims(chunk_x,chunk_z,owner_id,owner_name) VALUES(?,?,?,?) ON CONFLICT(chunk_x,chunk_z) DO NOTHING")) { statement.setInt(1,chunkX); statement.setInt(2,chunkZ); statement.setString(3,ownerUid); statement.setString(4,ownerName); return statement.executeUpdate()>0; } });
    }

    public boolean unclaim(int chunkX, int chunkZ, String ownerUid) {
        return database.transaction(connection -> { try (PreparedStatement statement=connection.prepareStatement("DELETE FROM claims WHERE chunk_x=? AND chunk_z=? AND owner_id=?")) { statement.setInt(1,chunkX); statement.setInt(2,chunkZ); statement.setString(3,ownerUid); return statement.executeUpdate()>0; } });
    }

    public boolean forceUnclaim(int chunkX, int chunkZ) {
        return database.transaction(connection -> { try (PreparedStatement statement=connection.prepareStatement("DELETE FROM claims WHERE chunk_x=? AND chunk_z=?")) { statement.setInt(1,chunkX); statement.setInt(2,chunkZ); return statement.executeUpdate()>0; } });
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
