package com.example.risingworldstarter.claims;

import com.example.risingworldstarter.database.Database;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.util.Base64;
import java.util.Optional;
import java.util.Properties;

public final class ChestService {
    private final Database database;
    public ChestService(Database database) { this.database=database; }

    public Optional<ChestOwnership> get(long globalId,int chunkX,int chunkY,int chunkZ) {
        return database.read(connection->{try(PreparedStatement query=connection.prepareStatement("SELECT owner_id,owner_name,locked FROM chests WHERE global_id=? AND chunk_x=? AND chunk_y=? AND chunk_z=?")){bindKey(query,globalId,chunkX,chunkY,chunkZ);try(var result=query.executeQuery()){return result.next()?Optional.of(new ChestOwnership(result.getString(1),result.getString(2),result.getBoolean(3))):Optional.empty();}}});
    }

    public ChestOwnership assign(long globalId,int chunkX,int chunkY,int chunkZ,String ownerUid,String ownerName) {
        return database.transaction(connection->{try(PreparedStatement insert=connection.prepareStatement("INSERT INTO chests(global_id,chunk_x,chunk_y,chunk_z,owner_id,owner_name,locked) VALUES(?,?,?,?,?,?,0) ON CONFLICT(global_id,chunk_x,chunk_y,chunk_z) DO NOTHING")){bindKey(insert,globalId,chunkX,chunkY,chunkZ);insert.setString(5,ownerUid);insert.setString(6,ownerName);insert.executeUpdate();}try(PreparedStatement query=connection.prepareStatement("SELECT owner_id,owner_name,locked FROM chests WHERE global_id=? AND chunk_x=? AND chunk_y=? AND chunk_z=?")){bindKey(query,globalId,chunkX,chunkY,chunkZ);try(var result=query.executeQuery()){if(!result.next())throw new IllegalStateException("Could not assign chest");return new ChestOwnership(result.getString(1),result.getString(2),result.getBoolean(3));}}});
    }

    public ChestOwnership setLocked(long globalId,int chunkX,int chunkY,int chunkZ,ChestOwnership ownership,boolean locked) {
        database.write(connection->{try(PreparedStatement update=connection.prepareStatement("UPDATE chests SET locked=? WHERE global_id=? AND chunk_x=? AND chunk_y=? AND chunk_z=?")){update.setBoolean(1,locked);update.setLong(2,globalId);update.setInt(3,chunkX);update.setInt(4,chunkY);update.setInt(5,chunkZ);update.executeUpdate();}return null;});
        return new ChestOwnership(ownership.ownerUid(),ownership.ownerName(),locked);
    }

    public void remove(long globalId,int chunkX,int chunkY,int chunkZ) { database.write(connection->{try(PreparedStatement delete=connection.prepareStatement("DELETE FROM chests WHERE global_id=? AND chunk_x=? AND chunk_y=? AND chunk_z=?")){bindKey(delete,globalId,chunkX,chunkY,chunkZ);delete.executeUpdate();}return null;}); }

    public void migrateLegacy(Path file) {
        if(!Files.isRegularFile(file))return;Properties values=new Properties();try(InputStream input=Files.newInputStream(file)){values.load(input);}catch(Exception exception){throw new IllegalStateException("Could not migrate chests",exception);}
        values.forEach((rawKey,rawValue)->{try{String[] key=rawKey.toString().split(",",4);String[] value=rawValue.toString().split(":",3);if(key.length!=4||value.length!=3)return;int x=Integer.parseInt(key[0]),y=Integer.parseInt(key[1]),z=Integer.parseInt(key[2]);long id=Long.parseLong(key[3]);String owner=new String(Base64.getUrlDecoder().decode(value[0]),StandardCharsets.UTF_8);String name=new String(Base64.getUrlDecoder().decode(value[1]),StandardCharsets.UTF_8);ChestOwnership created=assign(id,x,y,z,owner,name);if(Boolean.parseBoolean(value[2])&&!created.locked())setLocked(id,x,y,z,created,true);}catch(IllegalArgumentException ignored){}});
    }

    private static void bindKey(PreparedStatement statement,long globalId,int x,int y,int z)throws java.sql.SQLException{statement.setLong(1,globalId);statement.setInt(2,x);statement.setInt(3,y);statement.setInt(4,z);}
}
