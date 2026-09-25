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
import java.util.Base64;
import java.util.Optional;
import java.util.Properties;

public final class ChestService {
    private final Database database;
    public ChestService(Database database) { this.database=database; }

    public Optional<ChestOwnership> get(long globalId,int chunkX,int chunkY,int chunkZ) {
        return database.read(s -> s.first("chests", key(globalId, chunkX, chunkY, chunkZ)).map(ChestService::ownership));
    }

    public ChestOwnership assign(long globalId,int chunkX,int chunkY,int chunkZ,String ownerUid,String ownerName) {
        return database.transaction(s -> {
            Document key = key(globalId, chunkX, chunkY, chunkZ);
            s.insertIfAbsent("chests", key, doc("owner_id", ownerUid, "owner_name", ownerName, "locked", false));
            return ownership(s.first("chests", key).orElseThrow());
        });
    }

    public ChestOwnership setLocked(long globalId,int chunkX,int chunkY,int chunkZ,ChestOwnership ownership,boolean locked) {
        database.write(s -> { s.update("chests", key(globalId, chunkX, chunkY, chunkZ), doc("locked", locked)); return null; });
        return new ChestOwnership(ownership.ownerUid(), ownership.ownerName(), locked);
    }

    public void remove(long globalId,int chunkX,int chunkY,int chunkZ) {
        database.write(s -> { s.delete("chests", key(globalId, chunkX, chunkY, chunkZ)); return null; });
    }

    public void migrateLegacy(Path file) {
        if(!Files.isRegularFile(file))return;Properties values=new Properties();try(InputStream input=Files.newInputStream(file)){values.load(input);}catch(Exception exception){throw new IllegalStateException("Could not migrate chests",exception);}
        values.forEach((rawKey,rawValue)->{try{String[] key=rawKey.toString().split(",",4);String[] value=rawValue.toString().split(":",3);if(key.length!=4||value.length!=3)return;int x=Integer.parseInt(key[0]),y=Integer.parseInt(key[1]),z=Integer.parseInt(key[2]);long id=Long.parseLong(key[3]);String owner=new String(Base64.getUrlDecoder().decode(value[0]),StandardCharsets.UTF_8);String name=new String(Base64.getUrlDecoder().decode(value[1]),StandardCharsets.UTF_8);ChestOwnership created=assign(id,x,y,z,owner,name);if(Boolean.parseBoolean(value[2])&&!created.locked())setLocked(id,x,y,z,created,true);}catch(IllegalArgumentException ignored){}});
    }

    private static Document key(long id, int x, int y, int z) {
        return doc("global_id", id, "chunk_x", x, "chunk_y", y, "chunk_z", z);
    }
    private static ChestOwnership ownership(Document d) {
        return new ChestOwnership(d.getString("owner_id"), d.getString("owner_name"), d.getBoolean("locked"));
    }
}
