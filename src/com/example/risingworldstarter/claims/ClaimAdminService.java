package com.example.risingworldstarter.claims;

import com.example.risingworldstarter.database.Database;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class ClaimAdminService {
    private final Database database;
    public ClaimAdminService(Database database) { this.database=database; }
    public boolean contains(String uid) { return database.read(connection->{ try(PreparedStatement query=connection.prepareStatement("SELECT 1 FROM claim_admins WHERE player_uid=?")){query.setString(1,uid);try(var result=query.executeQuery()){return result.next();}}}); }
    public void add(String uid,String name) { database.write(connection->{try(PreparedStatement statement=connection.prepareStatement("INSERT INTO claim_admins(player_uid,player_name) VALUES(?,?) ON CONFLICT(player_uid) DO UPDATE SET player_name=excluded.player_name")){statement.setString(1,uid);statement.setString(2,name);statement.executeUpdate();}return null;}); }
    public boolean remove(String uid) { return database.transaction(connection->{try(PreparedStatement statement=connection.prepareStatement("DELETE FROM claim_admins WHERE player_uid=?")){statement.setString(1,uid);return statement.executeUpdate()>0;}}); }
    public Map<String,String> getAll() { return database.read(connection->{Map<String,String> values=new LinkedHashMap<>();try(var query=connection.prepareStatement("SELECT player_uid,player_name FROM claim_admins ORDER BY player_name");var result=query.executeQuery()){while(result.next())values.put(result.getString(1),result.getString(2));}return Map.copyOf(values);}); }
    public void migrateLegacy(Path file) { if(!Files.isRegularFile(file))return;Properties values=new Properties();try(InputStream input=Files.newInputStream(file)){values.load(input);}catch(Exception exception){throw new IllegalStateException("Could not migrate claim admins",exception);}values.forEach((uid,encoded)->{try{add(uid.toString(),new String(Base64.getUrlDecoder().decode(encoded.toString()),StandardCharsets.UTF_8));}catch(IllegalArgumentException ignored){}}); }
}
