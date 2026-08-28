package com.example.risingworldstarter.groups;

import com.example.risingworldstarter.database.Database;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
        return database.transaction(connection -> {
            if (findByMember(connection, ownerKey).isPresent())
                throw new IllegalStateException("You already belong to a clan.");
            String id = UUID.randomUUID().toString();
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO groups(group_id,name) VALUES(?,?)")) {
                insert.setString(1, id); insert.setString(2, normalizedName);
                try { insert.executeUpdate(); }
                catch (java.sql.SQLException exception) {
                    if (exception.getMessage().toLowerCase().contains("unique"))
                        throw new IllegalStateException("A clan with that name already exists.");
                    throw exception;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO group_members(character_key,group_id,character_name,role) VALUES(?,?,?,'OWNER')")) {
                insert.setString(1, ownerKey); insert.setString(2, id); insert.setString(3, ownerName);
                insert.executeUpdate();
            }
            setBalance(connection, "group:" + id, 0L);
            return requireGroup(connection, id);
        });
    }

    public Optional<Group> get(String id) { return database.read(c -> findGroup(c, id)); }
    public Optional<Group> findByMember(String key) { return database.read(c -> findByMember(c, key)); }

    public boolean canAccess(String characterKey, String claimOwnerId) {
        if (claimOwnerId == null || !claimOwnerId.startsWith("group:")) return false;
        String id = claimOwnerId.substring("group:".length());
        return database.read(connection -> exists(connection,
                "SELECT 1 FROM group_members WHERE group_id=? AND character_key=?", id, characterKey));
    }

    public void invite(String groupId, String actorKey, String targetKey) {
        database.write(connection -> {
            Group group = requireGroup(connection, groupId); requireManagement(group, actorKey);
            if (findByMember(connection, targetKey).isPresent())
                throw new IllegalStateException("That character already belongs to a clan.");
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO group_invitations(character_key,group_id) VALUES(?,?) "
                            + "ON CONFLICT(character_key) DO UPDATE SET group_id=excluded.group_id")) {
                statement.setString(1, targetKey); statement.setString(2, groupId); statement.executeUpdate();
            }
            return null;
        });
    }

    public Group acceptInvitation(String characterKey, String characterName) {
        return database.transaction(connection -> {
            if (findByMember(connection, characterKey).isPresent())
                throw new IllegalStateException("You already belong to a clan.");
            String groupId = selectString(connection,
                    "SELECT group_id FROM group_invitations WHERE character_key=?", characterKey)
                    .orElseThrow(() -> new IllegalStateException("You do not have a pending clan invitation."));
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO group_members(character_key,group_id,character_name,role) VALUES(?,?,?,'MEMBER')")) {
                insert.setString(1, characterKey); insert.setString(2, groupId); insert.setString(3, characterName);
                insert.executeUpdate();
            }
            execute(connection, "DELETE FROM group_invitations WHERE character_key=?", characterKey);
            return requireGroup(connection, groupId);
        });
    }

    public void leave(String characterKey) {
        database.write(connection -> {
            Group group = requireMembership(connection, characterKey);
            if (group.members().get(characterKey).role() == GroupRole.OWNER)
                throw new IllegalStateException("The owner must disband the clan instead of leaving.");
            execute(connection, "DELETE FROM group_members WHERE character_key=?", characterKey);
            execute(connection, "DELETE FROM group_invitations WHERE character_key=?", characterKey);
            return null;
        });
    }

    public void removeDeletedCharacter(String characterKey) {
        database.write(connection -> {
            Optional<Group> membership = findByMember(connection, characterKey);
            if (membership.isPresent() && membership.get().members().get(characterKey).role() == GroupRole.OWNER)
                throw new IllegalStateException("Disband the clan before deleting its owner character.");
            execute(connection, "DELETE FROM group_members WHERE character_key=?", characterKey);
            execute(connection, "DELETE FROM group_invitations WHERE character_key=?", characterKey);
            return null;
        });
    }

    public void kick(String groupId, String actorKey, String targetKey) {
        database.write(connection -> {
            Group group = requireGroup(connection, groupId);
            GroupMember actor = requireManagement(group, actorKey), target = requireMember(group, targetKey);
            if (target.role() == GroupRole.OWNER || (target.role() == GroupRole.MANAGER && actor.role() != GroupRole.OWNER))
                throw new IllegalStateException("Only the owner can remove a manager; the owner cannot be removed.");
            execute(connection, "DELETE FROM group_members WHERE character_key=? AND group_id=?", targetKey, groupId);
            return null;
        });
    }

    public void setManager(String groupId, String ownerKey, String targetKey, boolean manager) {
        database.write(connection -> {
            Group group = requireGroup(connection, groupId);
            if (requireMember(group, ownerKey).role() != GroupRole.OWNER)
                throw new IllegalStateException("Only the clan owner can change managers.");
            GroupMember target = requireMember(group, targetKey);
            if (target.role() == GroupRole.OWNER) throw new IllegalStateException("The owner's role cannot be changed.");
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE group_members SET role=? WHERE group_id=? AND character_key=?")) {
                update.setString(1, manager ? "MANAGER" : "MEMBER"); update.setString(2, groupId);
                update.setString(3, targetKey); update.executeUpdate();
            }
            return null;
        });
    }

    public Group disband(String groupId, String ownerKey) {
        return database.transaction(connection -> {
            Group group = requireGroup(connection, groupId);
            if (requireMember(group, ownerKey).role() != GroupRole.OWNER)
                throw new IllegalStateException("Only the clan owner can disband it.");
            String groupAccount = group.claimOwnerId();
            if (selectBalance(connection, groupAccount) != 0L)
                throw new IllegalStateException("Withdraw all clan funds before disbanding it.");
            execute(connection, "DELETE FROM groups WHERE group_id=?", groupId);
            execute(connection, "DELETE FROM balances WHERE account_id=?", groupAccount);
            return group;
        });
    }

    public boolean canManage(String groupId, String characterKey) {
        return database.read(connection -> exists(connection,
                "SELECT 1 FROM group_members WHERE group_id=? AND character_key=? AND role IN ('OWNER','MANAGER')",
                groupId, characterKey));
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

    public List<Group> getGroups() { return database.read(connection -> {
        var result = new java.util.ArrayList<Group>();
        try (PreparedStatement query = connection.prepareStatement("SELECT group_id FROM groups ORDER BY name");
             ResultSet rows = query.executeQuery()) {
            while (rows.next()) result.add(requireGroup(connection, rows.getString(1)));
        }
        return List.copyOf(result);
    }); }

    public void migrateLegacy(Path file) {
        database.transaction(connection -> {
            if (exists(connection, "SELECT 1 FROM metadata WHERE key=?", MIGRATION_MARKER)) return null;
            if (Files.isRegularFile(file)) migrateProperties(connection, file);
            try (PreparedStatement marker = connection.prepareStatement("INSERT INTO metadata(key,value) VALUES(?,CURRENT_TIMESTAMP)")) {
                marker.setString(1, MIGRATION_MARKER); marker.executeUpdate();
            }
            return null;
        });
    }

    private static void migrateProperties(Connection connection, Path file) throws java.sql.SQLException {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(file)) { values.load(input); }
        catch (java.io.IOException exception) { throw new IllegalStateException("Could not migrate clans from " + file, exception); }
        for (String key : values.stringPropertyNames()) if (key.startsWith("group.") && key.endsWith(".name")) {
            String id = key.substring(6, key.length() - 5);
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO groups(group_id,name) VALUES(?,?) ON CONFLICT DO NOTHING")) {
                insert.setString(1, id); insert.setString(2, decode(values.getProperty(key))); insert.executeUpdate();
            }
        }
        for (String key : values.stringPropertyNames()) {
            if (key.startsWith("group.") && key.contains(".member.")) {
                int marker = key.indexOf(".member."); String id = key.substring(6, marker);
                String characterKey = decode(key.substring(marker + 8)); String[] value = values.getProperty(key).split(":", 2);
                if (value.length != 2) continue;
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO group_members(character_key,group_id,character_name,role) VALUES(?,?,?,?) ON CONFLICT DO NOTHING")) {
                    insert.setString(1, characterKey); insert.setString(2, id); insert.setString(3, decode(value[1]));
                    insert.setString(4, GroupRole.valueOf(value[0]).name()); insert.executeUpdate();
                }
            } else if (key.startsWith("invite.")) {
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO group_invitations(character_key,group_id) VALUES(?,?) ON CONFLICT DO NOTHING")) {
                    insert.setString(1, decode(key.substring(7))); insert.setString(2, values.getProperty(key)); insert.executeUpdate();
                }
            }
        }
    }

    private static Optional<Group> findByMember(Connection connection, String key) throws java.sql.SQLException {
        Optional<String> id = selectString(connection, "SELECT group_id FROM group_members WHERE character_key=?", key);
        return id.isEmpty() ? Optional.empty() : findGroup(connection, id.get());
    }
    private static Optional<Group> findGroup(Connection connection, String id) throws java.sql.SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT name FROM groups WHERE group_id=?")) {
            query.setString(1, id); try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return Optional.empty(); String name = row.getString(1);
                Map<String, GroupMember> members = new LinkedHashMap<>();
                try (PreparedStatement memberQuery = connection.prepareStatement("SELECT character_key,character_name,role FROM group_members WHERE group_id=? ORDER BY role,character_name")) {
                    memberQuery.setString(1, id); try (ResultSet memberRows = memberQuery.executeQuery()) {
                        while (memberRows.next()) { String key = memberRows.getString(1);
                            members.put(key, new GroupMember(key, memberRows.getString(2), GroupRole.valueOf(memberRows.getString(3)))); }
                    }
                }
                return Optional.of(new Group(id, name, members));
            }
        }
    }
    private static Group requireMembership(Connection c, String key) throws java.sql.SQLException { return findByMember(c, key).orElseThrow(() -> new IllegalStateException("You do not belong to a clan.")); }
    private static Group requireGroup(Connection c, String id) throws java.sql.SQLException { return findGroup(c, id).orElseThrow(() -> new IllegalStateException("Clan no longer exists.")); }
    private static GroupMember requireMember(Group group, String key) { GroupMember member = group.members().get(key); if (member == null) throw new IllegalStateException("That character is not in your clan."); return member; }
    private static GroupMember requireManagement(Group group, String key) { GroupMember member = requireMember(group, key); if (member.role() == GroupRole.MEMBER) throw new IllegalStateException("A clan owner or manager is required."); return member; }
    private static boolean exists(Connection c, String sql, String... values) throws java.sql.SQLException { return selectString(c, sql, values).isPresent(); }
    private static Optional<String> selectString(Connection c, String sql, String... values) throws java.sql.SQLException { try (PreparedStatement q = c.prepareStatement(sql)) { for (int i=0;i<values.length;i++) q.setString(i+1, values[i]); try (ResultSet rows=q.executeQuery()) { return rows.next()?Optional.ofNullable(rows.getString(1)):Optional.empty(); } } }
    private static long selectBalance(Connection connection, String accountId) throws java.sql.SQLException { try (PreparedStatement query=connection.prepareStatement("SELECT balance FROM balances WHERE account_id=?")) { query.setString(1,accountId); try(ResultSet row=query.executeQuery()){return row.next()?row.getLong(1):0L;} } }
    private static void setBalance(Connection connection, String accountId, long amount) throws java.sql.SQLException { try (PreparedStatement statement=connection.prepareStatement("INSERT INTO balances(account_id,balance) VALUES(?,?) ON CONFLICT(account_id) DO UPDATE SET balance=excluded.balance")) { statement.setString(1,accountId); statement.setLong(2,amount); statement.executeUpdate(); } }
    private static void requirePositiveAmount(long amount) { if(amount<=0)throw new IllegalArgumentException("Amount must be greater than zero."); }
    private static void execute(Connection c, String sql, String... values) throws java.sql.SQLException { try (PreparedStatement q=c.prepareStatement(sql)) { for(int i=0;i<values.length;i++)q.setString(i+1,values[i]); q.executeUpdate(); } }
    private static String decode(String value) { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }
    private static String requireText(String value, String field) { if(value==null||value.isBlank())throw new IllegalArgumentException(field+" cannot be blank"); String result=value.trim(); if(result.length()>32)throw new IllegalArgumentException(field+" cannot exceed 32 characters"); return result; }
}
