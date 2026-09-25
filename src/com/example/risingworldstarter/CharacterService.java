package com.example.risingworldstarter;

import com.example.risingworldstarter.database.Database;
import com.example.risingworldstarter.database.DocumentStore;
import com.example.risingworldstarter.database.MongoSchema;
import org.bson.Document;
import static com.example.risingworldstarter.database.DocumentStore.*;
import net.risingworld.api.Server;
import net.risingworld.api.objects.Player;
import net.risingworld.api.objects.Skin;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

final class CharacterService {
    static final int MAX_SLOTS=4;
    private final Database database;
    CharacterService(Database database){this.database=database;}

    List<CharacterSummary> getCharacters(String accountUid){
        return database.read(s -> summaries(s, s.find("characters", doc("account_uid", accountUid), doc("slot", 1))));
    }

    List<CharacterSummary> findCharactersByName(String name){
        return database.read(s -> summaries(s, s.find("characters", doc("name_key", MongoSchema.nameKey(name)), doc("name", 1)))
                .stream().sorted(java.util.Comparator.comparing(CharacterSummary::name).thenComparing(CharacterSummary::profileName)).toList());
    }

    CharacterSummary ensureLegacyCharacter(Player player){List<CharacterSummary> existing=getCharacters(player.getUID());if(!existing.isEmpty())return existing.get(0);CharacterSummary legacy=createSummary(player.getUID(),player.getName(),player.getName(),1);saveCharacter(player,legacy);return legacy;}

    void captureProfileAppearance(Player player){Properties profile=loadAccountProfile(player.getUID());Skin skin=player.getSkin();profile.setProperty("profile-skin.gender",skin.getGender().name());profile.setProperty("profile-skin.color",Integer.toString(skin.getSkinColor()));profile.setProperty("profile-skin.hair-color",Integer.toString(skin.getHairColor()));profile.setProperty("profile-skin.eye-color",Integer.toString(skin.getEyeColor()));profile.setProperty("profile-skin.hairstyle",Byte.toString(skin.getHairstyle()));profile.setProperty("profile-skin.beard",Byte.toString(skin.getBeard()));profile.setProperty("profile-skin.variation",Byte.toString(skin.getVariation()));saveAccount(player.getUID(),player.getName(),profile);}

    boolean applyProfileAppearance(Player player){Properties profile=loadAccountProfile(player.getUID());if(!profile.containsKey("profile-skin.gender"))return false;applyAppearance(player.getSkin(),profile,"profile-skin.");return true;}

    CharacterSummary createCharacter(Player player,String requestedName,int slot){String name=requireCharacterName(requestedName);List<CharacterSummary> existing=getCharacters(player.getUID());if(existing.size()>=MAX_SLOTS)throw new IllegalStateException("All four character slots are occupied");if(slot<1||slot>MAX_SLOTS||existing.stream().anyMatch(character->character.slot()==slot))throw new IllegalArgumentException("That character slot is not available");if(existing.stream().anyMatch(character->character.name().equalsIgnoreCase(name)))throw new IllegalArgumentException("You already have a character with that name");String profileName=existing.isEmpty()?player.getName():existing.get(0).profileName();CharacterSummary created=createSummary(player.getUID(),profileName,name,slot);Vector3f spawn=resetPlayerForNewCharacter(player,name);saveCharacter(player,created,spawn,Quaternion.IDENTITY);return created;}

    void deleteCharacter(String accountUid,CharacterSummary character){
        database.write(s -> {
            if (s.delete("characters", doc("account_uid", accountUid, "slot", character.slot(), "character_id", character.id())) == 0)
                throw new IllegalStateException("That character slot has changed; reopen the character menu");
            return null;
        });
    }
    void saveCharacter(Player player,CharacterSummary character){saveCharacter(player,character,null,null);}

    private void saveCharacter(Player player,CharacterSummary character,Vector3f positionOverride,Quaternion rotationOverride){Properties state=new Properties();state.setProperty("name",character.name());Vector3f position=positionOverride==null?player.getPosition():positionOverride;Quaternion rotation=rotationOverride==null?player.getRotation():rotationOverride;state.setProperty("position",position.x+","+position.y+","+position.z);state.setProperty("rotation",rotation.x+","+rotation.y+","+rotation.z+","+rotation.w);Skin skin=player.getSkin();state.setProperty("skin.gender",skin.getGender().name());state.setProperty("skin.color",Integer.toString(skin.getSkinColor()));state.setProperty("skin.hair-color",Integer.toString(skin.getHairColor()));state.setProperty("skin.eye-color",Integer.toString(skin.getEyeColor()));state.setProperty("skin.hairstyle",Byte.toString(skin.getHairstyle()));state.setProperty("skin.beard",Byte.toString(skin.getBeard()));state.setProperty("skin.variation",Byte.toString(skin.getVariation()));state.setProperty("status.max-health",Integer.toString(player.getMaxHealth()));state.setProperty("status.health",Integer.toString(player.getHealth()));state.setProperty("status.hunger",Integer.toString(player.getHunger()));state.setProperty("status.thirst",Integer.toString(player.getThirst()));state.setProperty("status.max-stamina",Integer.toString(player.getMaxStamina()));state.setProperty("status.stamina",Integer.toString(player.getStamina()));state.setProperty("status.broken-bones",Boolean.toString(player.hasBrokenBones()));state.setProperty("status.bleeding",Boolean.toString(player.isBleeding()));byte[] inventory=player.getInventory().serialize(),clothes=player.getClothes().serialize();String serialized=serialize(state);database.write(s -> {
            if (s.update("characters", doc("character_id", character.id(), "account_uid", player.getUID()),
                    doc("name", character.name(), "name_key", MongoSchema.nameKey(character.name()), "state", serialized,
                            "inventory", inventory, "clothes", clothes)) == 0)
                throw new IllegalStateException("Character is not present in the database: " + character.name());
            return null;
        });}

    void loadCharacter(Player player,CharacterSummary character){CharacterData data=database.read(s -> {
            Document d = s.first("characters", doc("character_id", character.id(), "account_uid", player.getUID()))
                    .orElseThrow(() -> new IllegalStateException("Character is not present in the database: " + character.name()));
            return new CharacterData(parse(d.getString("state")), bytes(d, "inventory"), bytes(d, "clothes"));
        });Properties state=data.state();player.setName(character.name());applyAppearance(player.getSkin(),state,"skin.");if(data.clothes()!=null&&!Arrays.equals(data.clothes(),player.getClothes().serialize())&&!player.getClothes().deserialize(data.clothes()))throw new IllegalStateException("Stored clothing data is invalid for character: "+character.name());
        // Restore the model before inventory updates can equip the selected hotbar item.
        // Selecting the current character must not force another equip of identical data.
        boolean inventoryChanged=data.inventory()!=null&&!Arrays.equals(data.inventory(),player.getInventory().serialize());
        if(inventoryChanged&&!player.getInventory().deserialize(data.inventory()))throw new IllegalStateException("Stored inventory data is invalid for character: "+character.name());
        player.setMaxHealth(Integer.parseInt(state.getProperty("status.max-health",Integer.toString(player.getMaxHealth()))));player.setHealth(Integer.parseInt(state.getProperty("status.health",Integer.toString(player.getMaxHealth()))));player.setHunger(Integer.parseInt(state.getProperty("status.hunger","100")));player.setThirst(Integer.parseInt(state.getProperty("status.thirst","100")));player.setMaxStamina(Integer.parseInt(state.getProperty("status.max-stamina",Integer.toString(player.getMaxStamina()))));player.setStamina(Integer.parseInt(state.getProperty("status.stamina",Integer.toString(player.getMaxStamina()))));player.setBrokenBones(Boolean.parseBoolean(state.getProperty("status.broken-bones","false")));player.setBleeding(Boolean.parseBoolean(state.getProperty("status.bleeding","false")));if(inventoryChanged)player.getInventory().syncWithClient();float[] position=parseFloats(state.getProperty("position"),3),rotation=parseFloats(state.getProperty("rotation"),4);if(position!=null)player.setPosition(position[0],position[1],position[2]);if(rotation!=null)player.setRotation(new Quaternion(rotation[0],rotation[1],rotation[2],rotation[3]));}

    void migrateLegacy(Path root){if(!Files.isDirectory(root))return;try(var accounts=Files.list(root)){for(Path accountDirectory:accounts.filter(Files::isDirectory).toList()){String accountUid;try{accountUid=new String(Base64.getUrlDecoder().decode(accountDirectory.getFileName().toString()),StandardCharsets.UTF_8);}catch(IllegalArgumentException ignored){continue;}Properties account=loadFile(accountDirectory.resolve("account.properties"));String profileName=account.getProperty("profile-name","Unknown");Properties profile=new Properties();account.stringPropertyNames().stream().filter(key->key.startsWith("profile-")).forEach(key->profile.setProperty(key,account.getProperty(key)));saveAccount(accountUid,profileName,profile);for(int slot=1;slot<=MAX_SLOTS;slot++){String id=account.getProperty("slot."+slot+".id"),name=account.getProperty("slot."+slot+".name");if(id==null||name==null)continue;Path directory=accountDirectory.resolve(id);Properties state=loadFile(directory.resolve("state.properties"));byte[] inventory=readBytes(directory.resolve("inventory.bin")),clothes=readBytes(directory.resolve("clothes.bin"));int finalSlot=slot;database.write(s -> { s.insertIfAbsent("characters", doc("character_id", id),
                doc("account_uid", accountUid, "slot", finalSlot, "name", name, "name_key", MongoSchema.nameKey(name),
                        "state", serialize(state), "inventory", inventory, "clothes", clothes)); return null; });}}}catch(IOException exception){throw new IllegalStateException("Could not migrate legacy characters",exception);}}

    private CharacterSummary createSummary(String accountUid,String profileName,String name,int slot){
        String id = UUID.randomUUID().toString();
        database.write(s -> {
            Document key = doc("account_uid", accountUid);
            if (!s.insertIfAbsent("accounts", key, doc("profile_name", profileName, "profile_state", "")))
                s.update("accounts", key, doc("profile_name", profileName));
            if (s.exists("characters", doc("account_uid", accountUid, "name_key", MongoSchema.nameKey(name))))
                throw new IllegalArgumentException("You already have a character with that name");
            s.insert("characters", doc("character_id", id, "account_uid", accountUid, "slot", slot, "name", name,
                    "name_key", MongoSchema.nameKey(name), "state", ""));
            return null;
        });
        return new CharacterSummary(slot, id, name, profileName);
    }
    private Vector3f resetPlayerForNewCharacter(Player player,String name){player.setName(name);player.getInventory().clear();player.getInventory().syncWithClient();player.getClothes().removeAll();applyProfileAppearance(player);player.setHealth(player.getMaxHealth());player.setHunger(100);player.setThirst(100);player.setStamina(player.getMaxStamina());player.setBrokenBones(false);player.setBleeding(false);Vector3f spawn=Server.getDefaultSpawnPosition();player.setPosition(spawn);player.setRotation(Quaternion.IDENTITY);return spawn;}
    private Properties loadAccountProfile(String uid){
        return database.read(s -> s.first("accounts", doc("account_uid", uid)).map(d -> parse(d.getString("profile_state"))).orElseGet(Properties::new));
    }
    private void saveAccount(String uid,String profileName,Properties profile){
        database.write(s -> { s.put("accounts", doc("account_uid", uid), doc("profile_name", profileName, "profile_state", serialize(profile))); return null; });
    }
    private static List<CharacterSummary> summaries(DocumentStore s, List<Document> characters) {
        return characters.stream().map(d -> {
            String profile = s.first("accounts", doc("account_uid", d.getString("account_uid")))
                    .orElseThrow(() -> new IllegalStateException("Missing character account")).getString("profile_name");
            return new CharacterSummary((int) number(d, "slot"), d.getString("character_id"), d.getString("name"), profile);
        }).toList();
    }
    private static byte[] bytes(Document d, String field) {
        Object value = d.get(field);
        return value == null ? null : value instanceof byte[] b ? b : ((org.bson.types.Binary) value).getData();
    }
    private static String requireCharacterName(String value){String name=value==null?"":value.trim();if(!name.matches("[A-Za-z][A-Za-z0-9 _'-]{2,23}"))throw new IllegalArgumentException("Character names must be 3-24 characters and start with a letter");return name;}
    private static String serialize(Properties values){try{StringWriter writer=new StringWriter();values.store(writer,null);return writer.toString();}catch(IOException impossible){throw new IllegalStateException(impossible);}}
    private static Properties parse(String value){Properties properties=new Properties();if(value==null||value.isBlank())return properties;try{properties.load(new StringReader(value));return properties;}catch(IOException impossible){throw new IllegalStateException(impossible);}}
    private static Properties loadFile(Path file){Properties result=new Properties();if(!Files.isRegularFile(file))return result;try(var input=Files.newInputStream(file)){result.load(input);return result;}catch(IOException exception){throw new IllegalStateException("Could not read "+file,exception);}}
    private static byte[] readBytes(Path file){try{return Files.isRegularFile(file)?Files.readAllBytes(file):null;}catch(IOException exception){throw new IllegalStateException("Could not read "+file,exception);}}
    private static void applyAppearance(Skin skin,Properties values,String prefix){Skin.Gender gender=Skin.Gender.valueOf(values.getProperty(prefix+"gender","Male"));int skinColor=Integer.parseInt(values.getProperty(prefix+"color","0")),hairColor=Integer.parseInt(values.getProperty(prefix+"hair-color","0")),eyeColor=Integer.parseInt(values.getProperty(prefix+"eye-color","0"));byte hairstyle=Byte.parseByte(values.getProperty(prefix+"hairstyle","0")),beard=Byte.parseByte(values.getProperty(prefix+"beard","0")),variation=Byte.parseByte(values.getProperty(prefix+"variation","0"));if(skin.getGender()!=gender)skin.setGender(gender);if(skin.getSkinColor()!=skinColor)skin.setSkinColor(skinColor);if(skin.getHairColor()!=hairColor)skin.setHairColor(hairColor);if(skin.getEyeColor()!=eyeColor)skin.setEyeColor(eyeColor);if(skin.getHairstyle()!=hairstyle)skin.setHairstyle(hairstyle);if(skin.getBeard()!=beard)skin.setBeard(beard);if(skin.getVariation()!=variation)skin.setVariation(variation);}
    private static float[] parseFloats(String value,int expected){if(value==null)return null;String[] parts=value.split(",");if(parts.length!=expected)return null;try{float[] result=new float[expected];for(int index=0;index<expected;index++)result[index]=Float.parseFloat(parts[index]);return result;}catch(NumberFormatException ignored){return null;}}
    private record CharacterData(Properties state,byte[] inventory,byte[] clothes){}
    record CharacterSummary(int slot,String id,String name,String profileName){String economyKey(){return "character:"+id;}}
}
