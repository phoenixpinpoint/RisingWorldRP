package com.example.risingworldstarter.groups;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/** Database-backed world-scoped clan membership and role service. */
public final class GroupService {
    private static final String MIGRATION_MARKER = "legacy_groups_migrated_v1";
    private final Database database;

    public GroupService(Database database) { this.database = database; }

    public Group create(String name, String ownerKey, String ownerName) {
        String normalizedName = requireText(name, "group name");
        return database.transaction(s -> {
            if (findByMember(s, ownerKey).isPresent()) throw new IllegalStateException("You already belong to a clan.");
            String nameKey = MongoSchema.nameKey(normalizedName);
            if (s.exists("groups", doc("name_key", nameKey))) throw new IllegalStateException("A clan with that name already exists.");
            String id = UUID.randomUUID().toString();
            s.insert("groups", doc("group_id", id, "name", normalizedName, "name_key", nameKey));
            s.insert("group_members", doc("character_key", ownerKey, "group_id", id, "character_name", ownerName, "role", "OWNER"));
            setBalance(s, "group:" + id, 0L);
            return requireGroup(s, id);
        });
    }

    public Optional<Group> get(String id) { return database.read(c -> findGroup(c, id)); }
    public Optional<Group> findByMember(String key) { return database.read(c -> findByMember(c, key)); }

    public boolean canAccess(String characterKey, String claimOwnerId) {
        if (claimOwnerId == null || !claimOwnerId.startsWith("group:")) return false;
        String id = claimOwnerId.substring("group:".length());
        return database.read(s -> s.exists("group_members", doc("group_id", id, "character_key", characterKey)));
    }

    public void invite(String groupId, String actorKey, String targetKey) {
        database.write(s -> {
            requireManagement(requireGroup(s, groupId), actorKey);
            if (findByMember(s, targetKey).isPresent()) throw new IllegalStateException("That character already belongs to a clan.");
            s.put("group_invitations", doc("character_key", targetKey), doc("group_id", groupId));
            return null;
        });
    }

    public Group acceptInvitation(String characterKey, String characterName) {
        return database.transaction(s -> {
            if (findByMember(s, characterKey).isPresent()) throw new IllegalStateException("You already belong to a clan.");
            String groupId = s.first("group_invitations", doc("character_key", characterKey))
                    .orElseThrow(() -> new IllegalStateException("You do not have a pending clan invitation.")).getString("group_id");
            requireGroup(s, groupId);
            s.insert("group_members", doc("character_key", characterKey, "group_id", groupId, "character_name", characterName, "role", "MEMBER"));
            s.delete("group_invitations", doc("character_key", characterKey));
            return requireGroup(s, groupId);
        });
    }

    public void leave(String characterKey) {
        database.write(connection -> {
            Group group = requireMembership(connection, characterKey);
            if (group.members().get(characterKey).role() == GroupRole.OWNER)
                throw new IllegalStateException("The owner must disband the clan instead of leaving.");
            connection.delete("group_members", doc("character_key", characterKey));
            connection.delete("group_invitations", doc("character_key", characterKey));
            return null;
        });
    }

    public void removeDeletedCharacter(String characterKey) {
        database.write(connection -> {
            Optional<Group> membership = findByMember(connection, characterKey);
            if (membership.isPresent() && membership.get().members().get(characterKey).role() == GroupRole.OWNER)
                throw new IllegalStateException("Disband the clan before deleting its owner character.");
            connection.delete("group_members", doc("character_key", characterKey));
            connection.delete("group_invitations", doc("character_key", characterKey));
            return null;
        });
    }

    public void kick(String groupId, String actorKey, String targetKey) {
        database.write(connection -> {
            Group group = requireGroup(connection, groupId);
            GroupMember actor = requireManagement(group, actorKey), target = requireMember(group, targetKey);
            if (target.role() == GroupRole.OWNER || (target.role() == GroupRole.MANAGER && actor.role() != GroupRole.OWNER))
                throw new IllegalStateException("Only the owner can remove a manager; the owner cannot be removed.");
            connection.delete("group_members", doc("character_key", targetKey, "group_id", groupId));
            return null;
        });
    }

    public void setManager(String groupId, String ownerKey, String targetKey, boolean manager) {
        database.write(s -> {
            Group group = requireGroup(s, groupId);
            if (requireMember(group, ownerKey).role() != GroupRole.OWNER)
                throw new IllegalStateException("Only the clan owner can change managers.");
            if (requireMember(group, targetKey).role() == GroupRole.OWNER)
                throw new IllegalStateException("The owner's role cannot be changed.");
            s.update("group_members", doc("group_id", groupId, "character_key", targetKey), doc("role", manager ? "MANAGER" : "MEMBER"));
            return null;
        });
    }

    public Group disband(String groupId, String ownerKey) {
        return database.transaction(s -> {
            Group group = requireGroup(s, groupId);
            if (requireMember(group, ownerKey).role() != GroupRole.OWNER)
                throw new IllegalStateException("Only the clan owner can disband it.");
            if (selectBalance(s, group.claimOwnerId()) != 0L)
                throw new IllegalStateException("Withdraw all clan funds before disbanding it.");
            s.delete("group_members", doc("group_id", groupId));
            s.delete("group_invitations", doc("group_id", groupId));
            s.delete("groups", doc("group_id", groupId));
            s.delete("balances", doc("account_id", group.claimOwnerId()));
            return group;
        });
    }

    public boolean canManage(String groupId, String characterKey) {
        return database.read(s -> s.first("group_members", doc("group_id", groupId, "character_key", characterKey))
                .map(d -> !d.getString("role").equals("MEMBER")).orElse(false));
    }

    /** Returns the clan treasury balance after verifying the actor is an owner or manager. */
    public long getBalance(String groupId, String actorKey) {
        return database.read(connection -> {
            requireManagement(requireGroup(connection, groupId), actorKey);
            return selectBalance(connection, "group:" + groupId);
        });
    }

    /** Atomically moves funds from the actor's character account into the clan treasury. */
    public long deposit(String groupId, String actorKey, long amount) {
        requirePositiveAmount(amount);
        return database.transaction(connection -> {
            requireManagement(requireGroup(connection, groupId), actorKey);
            long characterBalance = selectBalance(connection, actorKey);
            if (characterBalance < amount) throw new IllegalStateException("You do not have enough funds.");
            String groupAccount = "group:" + groupId;
            long updatedGroupBalance = Math.addExact(selectBalance(connection, groupAccount), amount);
            setBalance(connection, actorKey, characterBalance - amount);
            setBalance(connection, groupAccount, updatedGroupBalance);
            return updatedGroupBalance;
        });
    }

    /** Atomically moves funds from the clan treasury into the actor's character account. */
    public long withdraw(String groupId, String actorKey, long amount) {
        requirePositiveAmount(amount);
        return database.transaction(connection -> {
            requireManagement(requireGroup(connection, groupId), actorKey);
            String groupAccount = "group:" + groupId;
            long groupBalance = selectBalance(connection, groupAccount);
            if (groupBalance < amount) throw new IllegalStateException("The clan does not have enough funds.");
            long updatedCharacterBalance = Math.addExact(selectBalance(connection, actorKey), amount);
            setBalance(connection, groupAccount, groupBalance - amount);
            setBalance(connection, actorKey, updatedCharacterBalance);
            return groupBalance - amount;
        });
    }

    public List<Group> getGroups() {
        return database.read(s -> s.find("groups", doc(), doc("name_key", 1)).stream()
                .map(d -> requireGroup(s, d.getString("group_id"))).toList());
    }

    public void migrateLegacy(Path file) {
        database.write(s -> {
            if (s.exists("metadata", doc("key", MIGRATION_MARKER))) return null;
            if (Files.isRegularFile(file)) migrateProperties(s, file);
            s.insert("metadata", doc("key", MIGRATION_MARKER, "value", java.time.Instant.now().toString()));
            return null;
        });
    }

    private static void migrateProperties(DocumentStore connection, Path file) {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(file)) { values.load(input); }
        catch (java.io.IOException exception) { throw new IllegalStateException("Could not migrate clans from " + file, exception); }
        for (String key : values.stringPropertyNames()) if (key.startsWith("group.") && key.endsWith(".name")) {
            String id = key.substring(6, key.length() - 5), name = decode(values.getProperty(key));
            if (!connection.exists("groups", doc("name_key", MongoSchema.nameKey(name))))
                connection.insertIfAbsent("groups", doc("group_id", id), doc("name", name, "name_key", MongoSchema.nameKey(name)));
        }
        for (String key : values.stringPropertyNames()) {
            if (key.startsWith("group.") && key.contains(".member.")) {
                int marker = key.indexOf(".member."); String id = key.substring(6, marker);
                String characterKey = decode(key.substring(marker + 8)); String[] value = values.getProperty(key).split(":", 2);
                if (value.length != 2) continue;
                requireGroup(connection, id);
                connection.insertIfAbsent("group_members", doc("character_key", characterKey),
                        doc("group_id", id, "character_name", decode(value[1]), "role", GroupRole.valueOf(value[0]).name()));
            } else if (key.startsWith("invite.")) {
                requireGroup(connection, values.getProperty(key));
                connection.insertIfAbsent("group_invitations", doc("character_key", decode(key.substring(7))), doc("group_id", values.getProperty(key)));
            }
        }
    }

    private static Optional<Group> findByMember(DocumentStore connection, String key) {
        return connection.first("group_members", doc("character_key", key)).flatMap(d -> findGroup(connection, d.getString("group_id")));
    }
    private static Optional<Group> findGroup(DocumentStore connection, String id) {
        return connection.first("groups", doc("group_id", id)).map(d -> {
            Map<String, GroupMember> members = new LinkedHashMap<>();
            for (Document m : connection.find("group_members", doc("group_id", id), doc("role", 1, "character_name", 1))) {
                String key = m.getString("character_key");
                members.put(key, new GroupMember(key, m.getString("character_name"), GroupRole.valueOf(m.getString("role"))));
            }
            return new Group(id, d.getString("name"), members);
        });
    }
    private static Group requireMembership(DocumentStore c, String key) { return findByMember(c, key).orElseThrow(() -> new IllegalStateException("You do not belong to a clan.")); }
    private static Group requireGroup(DocumentStore c, String id) { return findGroup(c, id).orElseThrow(() -> new IllegalStateException("Clan no longer exists.")); }
    private static GroupMember requireMember(Group group, String key) { GroupMember member = group.members().get(key); if (member == null) throw new IllegalStateException("That character is not in your clan."); return member; }
    private static GroupMember requireManagement(Group group, String key) { GroupMember member = requireMember(group, key); if (member.role() == GroupRole.MEMBER) throw new IllegalStateException("A clan owner or manager is required."); return member; }
    private static long selectBalance(DocumentStore connection, String accountId) {
        return balance(connection, accountId);
    }
    private static void setBalance(DocumentStore connection, String accountId, long amount) {
        balance(connection, accountId, amount);
    }
    private static void requirePositiveAmount(long amount) { if(amount<=0)throw new IllegalArgumentException("Amount must be greater than zero."); }
    private static String decode(String value) { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }
    private static String requireText(String value, String field) { if(value==null||value.isBlank())throw new IllegalArgumentException(field+" cannot be blank"); String result=value.trim(); if(result.length()>32)throw new IllegalArgumentException(field+" cannot exceed 32 characters"); return result; }
}
