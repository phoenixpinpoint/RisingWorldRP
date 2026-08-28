package com.example.risingworldstarter.economy;

import com.example.risingworldstarter.database.Database;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;

public final class DatabaseEconomyService implements EconomyApi {
    private final Database database;

    public DatabaseEconomyService(Database database) {
        this.database = database;
    }

    @Override
    public long createAccount(String playerUid, long initialBalance) {
        requireNonNegative(initialBalance, "initialBalance");
        String uid = requireUid(playerUid);
        return database.transaction(connection -> {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO balances(account_id, balance) VALUES (?, ?) ON CONFLICT(account_id) DO NOTHING")) {
                insert.setString(1, uid);
                insert.setLong(2, initialBalance);
                insert.executeUpdate();
            }
            return selectBalance(connection, uid);
        });
    }

    @Override
    public long getBalance(String playerUid) {
        String uid = requireUid(playerUid);
        return database.read(connection -> selectBalance(connection, uid));
    }

    @Override
    public boolean hasAccount(String playerUid) {
        String uid = requireUid(playerUid);
        return database.read(connection -> {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT 1 FROM balances WHERE account_id = ?")) {
                query.setString(1, uid);
                try (ResultSet result = query.executeQuery()) { return result.next(); }
            }
        });
    }

    @Override
    public long setBalance(String playerUid, long amount) {
        requireNonNegative(amount, "amount");
        upsert(requireUid(playerUid), amount);
        return amount;
    }

    @Override
    public long deposit(String playerUid, long amount) {
        requirePositive(amount, "amount");
        String uid = requireUid(playerUid);
        return database.transaction(connection -> {
            long updated = Math.addExact(selectBalance(connection, uid), amount);
            upsert(connection, uid, updated);
            return updated;
        });
    }

    @Override
    public boolean withdraw(String playerUid, long amount) {
        requirePositive(amount, "amount");
        String uid = requireUid(playerUid);
        return database.transaction(connection -> {
            long current = selectBalance(connection, uid);
            if (current < amount) return false;
            upsert(connection, uid, current - amount);
            return true;
        });
    }

    @Override
    public boolean deleteAccount(String playerUid) {
        String uid = requireUid(playerUid);
        return database.transaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM balances WHERE account_id = ?")) {
                delete.setString(1, uid);
                return delete.executeUpdate() > 0;
            }
        });
    }

    public void migrateLegacy(Path dataFile) {
        if (!Files.isRegularFile(dataFile)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(dataFile)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not migrate balances from " + dataFile, exception);
        }
        database.transaction(connection -> {
            for (String uid : properties.stringPropertyNames()) {
                long balance;
                try { balance = Long.parseLong(properties.getProperty(uid)); }
                catch (NumberFormatException ignored) { continue; }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO balances(account_id, balance) VALUES (?, ?) ON CONFLICT(account_id) DO NOTHING")) {
                    insert.setString(1, uid);
                    insert.setLong(2, Math.max(0L, balance));
                    insert.executeUpdate();
                }
            }
            return null;
        });
    }

    private void upsert(String uid, long amount) {
        database.write(connection -> { upsert(connection, uid, amount); return null; });
    }

    private static void upsert(java.sql.Connection connection, String uid, long amount) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO balances(account_id, balance) VALUES (?, ?) "
                        + "ON CONFLICT(account_id) DO UPDATE SET balance = excluded.balance")) {
            statement.setString(1, uid);
            statement.setLong(2, amount);
            statement.executeUpdate();
        }
    }

    private static long selectBalance(java.sql.Connection connection, String uid) throws java.sql.SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT balance FROM balances WHERE account_id = ?")) {
            query.setString(1, uid);
            try (ResultSet result = query.executeQuery()) { return result.next() ? result.getLong(1) : 0L; }
        }
    }

    private static String requireUid(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("playerUid must not be blank");
        return value;
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must not be negative");
    }
}
