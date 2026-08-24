package com.example.risingworldstarter;

import net.risingworld.api.Server;
import net.risingworld.api.objects.Player;
import net.risingworld.api.objects.Skin;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

final class CharacterService {
    static final int MAX_SLOTS = 4;
    private final Path root;

    CharacterService(Path root) {
        this.root = root;
    }

    List<CharacterSummary> getCharacters(String accountUid) {
        Properties account = loadProperties(accountFile(accountUid));
        String profileName = account.getProperty("profile-name", "Unknown");
        List<CharacterSummary> result = new ArrayList<>();
        for (int slot = 1; slot <= MAX_SLOTS; slot++) {
            String id = account.getProperty("slot." + slot + ".id");
            String name = account.getProperty("slot." + slot + ".name");
            if (id != null && name != null) {
                result.add(new CharacterSummary(slot, id, name, profileName));
            }
        }
        return result;
    }

    CharacterSummary ensureLegacyCharacter(Player player) {
        List<CharacterSummary> existing = getCharacters(player.getUID());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        CharacterSummary legacy = createSummary(player.getUID(), player.getName(), player.getName(), 1);
        saveCharacter(player, legacy);
        return legacy;
    }

    CharacterSummary createCharacter(Player player, String requestedName, int slot) {
        String name = requireCharacterName(requestedName);
        List<CharacterSummary> existing = getCharacters(player.getUID());
        if (existing.size() >= MAX_SLOTS) {
            throw new IllegalStateException("All four character slots are occupied");
        }
        if (slot < 1 || slot > MAX_SLOTS
                || existing.stream().anyMatch(character -> character.slot() == slot)) {
            throw new IllegalArgumentException("That character slot is not available");
        }
        if (existing.stream().anyMatch(character -> character.name().equalsIgnoreCase(name))) {
            throw new IllegalArgumentException("You already have a character with that name");
        }
        String profileName = existing.isEmpty() ? player.getName() : existing.get(0).profileName();
        CharacterSummary created = createSummary(player.getUID(), profileName, name, slot);
        resetPlayerForNewCharacter(player, name);
        saveCharacter(player, created);
        return created;
    }

    void saveCharacter(Player player, CharacterSummary character) {
        Path directory = characterDirectory(player.getUID(), character.id());
        Properties state = new Properties();
        state.setProperty("name", character.name());
        Vector3f position = player.getPosition();
        Quaternion rotation = player.getRotation();
        state.setProperty("position", position.x + "," + position.y + "," + position.z);
        state.setProperty("rotation", rotation.x + "," + rotation.y + "," + rotation.z + "," + rotation.w);
        Skin skin = player.getSkin();
        state.setProperty("skin.gender", skin.getGender().name());
        state.setProperty("skin.color", Integer.toString(skin.getSkinColor()));
        state.setProperty("skin.hair-color", Integer.toString(skin.getHairColor()));
        state.setProperty("skin.eye-color", Integer.toString(skin.getEyeColor()));
        state.setProperty("skin.hairstyle", Byte.toString(skin.getHairstyle()));
        state.setProperty("skin.beard", Byte.toString(skin.getBeard()));
        state.setProperty("skin.variation", Byte.toString(skin.getVariation()));
        state.setProperty("status.max-health", Integer.toString(player.getMaxHealth()));
        state.setProperty("status.health", Integer.toString(player.getHealth()));
        state.setProperty("status.hunger", Integer.toString(player.getHunger()));
        state.setProperty("status.thirst", Integer.toString(player.getThirst()));
        state.setProperty("status.max-stamina", Integer.toString(player.getMaxStamina()));
        state.setProperty("status.stamina", Integer.toString(player.getStamina()));
        state.setProperty("status.broken-bones", Boolean.toString(player.hasBrokenBones()));
        state.setProperty("status.bleeding", Boolean.toString(player.isBleeding()));
        saveProperties(directory.resolve("state.properties"), state);
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve("inventory.bin"), player.getInventory().serialize());
            Files.write(directory.resolve("clothes.bin"), player.getClothes().serialize());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save character " + character.name(), exception);
        }
    }

    void loadCharacter(Player player, CharacterSummary character) {
        Path directory = characterDirectory(player.getUID(), character.id());
        Properties state = loadProperties(directory.resolve("state.properties"));
        player.setName(character.name());
        try {
            Path inventory = directory.resolve("inventory.bin");
            if (Files.exists(inventory)) player.getInventory().deserialize(Files.readAllBytes(inventory));
            Path clothes = directory.resolve("clothes.bin");
            if (Files.exists(clothes)) player.getClothes().deserialize(Files.readAllBytes(clothes));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load character " + character.name(), exception);
        }
        Skin skin = player.getSkin();
        skin.setGender(Skin.Gender.valueOf(state.getProperty("skin.gender", "Male")));
        skin.setSkinColor(Integer.parseInt(state.getProperty("skin.color", "0")));
        skin.setHairColor(Integer.parseInt(state.getProperty("skin.hair-color", "0")));
        skin.setEyeColor(Integer.parseInt(state.getProperty("skin.eye-color", "0")));
        skin.setHairstyle(Byte.parseByte(state.getProperty("skin.hairstyle", "0")));
        skin.setBeard(Byte.parseByte(state.getProperty("skin.beard", "0")));
        skin.setVariation(Byte.parseByte(state.getProperty("skin.variation", "0")));
        player.setMaxHealth(Integer.parseInt(state.getProperty("status.max-health",
                Integer.toString(player.getMaxHealth()))));
        player.setHealth(Integer.parseInt(state.getProperty("status.health",
                Integer.toString(player.getMaxHealth()))));
        player.setHunger(Integer.parseInt(state.getProperty("status.hunger", "100")));
        player.setThirst(Integer.parseInt(state.getProperty("status.thirst", "100")));
        player.setMaxStamina(Integer.parseInt(state.getProperty("status.max-stamina",
                Integer.toString(player.getMaxStamina()))));
        player.setStamina(Integer.parseInt(state.getProperty("status.stamina",
                Integer.toString(player.getMaxStamina()))));
        player.setBrokenBones(Boolean.parseBoolean(state.getProperty("status.broken-bones", "false")));
        player.setBleeding(Boolean.parseBoolean(state.getProperty("status.bleeding", "false")));
        player.getInventory().syncWithClient();
        float[] position = parseFloats(state.getProperty("position"), 3);
        float[] rotation = parseFloats(state.getProperty("rotation"), 4);
        if (position != null) player.setPosition(position[0], position[1], position[2]);
        if (rotation != null) player.setRotation(new Quaternion(rotation[0], rotation[1], rotation[2], rotation[3]));
    }

    private CharacterSummary createSummary(String accountUid, String profileName, String characterName, int slot) {
        Properties account = loadProperties(accountFile(accountUid));
        account.setProperty("profile-name", profileName);
        String id = UUID.randomUUID().toString();
        account.setProperty("slot." + slot + ".id", id);
        account.setProperty("slot." + slot + ".name", characterName);
        saveProperties(accountFile(accountUid), account);
        return new CharacterSummary(slot, id, characterName, profileName);
    }

    private static void resetPlayerForNewCharacter(Player player, String characterName) {
        player.setName(characterName);
        player.getInventory().clear();
        player.getInventory().syncWithClient();
        player.getClothes().removeAll();
        Skin skin = player.getSkin();
        skin.setGender(Skin.Gender.Male);
        skin.setSkinColor(0);
        skin.setHairColor(0);
        skin.setEyeColor(0);
        skin.setHairstyle((byte) 0);
        skin.setBeard((byte) 0);
        skin.setVariation((byte) 0);
        player.setHealth(player.getMaxHealth());
        player.setHunger(100);
        player.setThirst(100);
        player.setStamina(player.getMaxStamina());
        player.setBrokenBones(false);
        player.setBleeding(false);
        Vector3f spawn = Server.getDefaultSpawnPosition();
        player.setPosition(spawn);
        player.setRotation(Quaternion.IDENTITY);
    }

    private Path accountDirectory(String uid) {
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(uid.getBytes(StandardCharsets.UTF_8));
        return root.resolve(encoded);
    }

    private Path accountFile(String uid) { return accountDirectory(uid).resolve("account.properties"); }
    private Path characterDirectory(String uid, String id) { return accountDirectory(uid).resolve(id); }

    private static String requireCharacterName(String value) {
        String name = value == null ? "" : value.trim();
        if (!name.matches("[A-Za-z][A-Za-z0-9 _'-]{2,23}")) {
            throw new IllegalArgumentException("Character names must be 3-24 characters and start with a letter");
        }
        return name;
    }

    private static float[] parseFloats(String value, int expected) {
        if (value == null) return null;
        String[] parts = value.split(",");
        if (parts.length != expected) return null;
        try {
            float[] result = new float[expected];
            for (int index = 0; index < expected; index++) result[index] = Float.parseFloat(parts[index]);
            return result;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Properties loadProperties(Path path) {
        Properties properties = new Properties();
        if (!Files.exists(path)) return properties;
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load " + path, exception);
        }
    }

    private static void saveProperties(Path path, Properties properties) {
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "Rising World character data");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save " + path, exception);
        }
    }

    record CharacterSummary(int slot, String id, String name, String profileName) {
        String economyKey() { return "character:" + id; }
    }
}
