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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class ClaimAdminService {
    private final Database database;
    public ClaimAdminService(Database database) { this.database=database; }
    public boolean contains(String uid) {
        return database.read(s -> s.exists("claim_admins", doc("player_uid", uid)));
    }
    public void add(String uid,String name) {
        database.write(s -> { s.put("claim_admins", doc("player_uid", uid), doc("player_name", name)); return null; });
    }
    public boolean remove(String uid) {
        return database.transaction(s -> s.delete("claim_admins", doc("player_uid", uid)) > 0);
    }
    public Map<String,String> getAll() {
        return database.read(s -> {
            Map<String, String> values = new LinkedHashMap<>();
            for (Document d : s.find("claim_admins", doc(), doc("player_name", 1)))
                values.put(d.getString("player_uid"), d.getString("player_name"));
            return Map.copyOf(values);
        });
    }
    public void migrateLegacy(Path file) { if(!Files.isRegularFile(file))return;Properties values=new Properties();try(InputStream input=Files.newInputStream(file)){values.load(input);}catch(Exception exception){throw new IllegalStateException("Could not migrate claim admins",exception);}values.forEach((uid,encoded)->{try{add(uid.toString(),new String(Base64.getUrlDecoder().decode(encoded.toString()),StandardCharsets.UTF_8));}catch(IllegalArgumentException ignored){}}); }
}
