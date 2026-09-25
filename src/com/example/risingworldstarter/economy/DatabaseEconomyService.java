package com.example.risingworldstarter.economy;

import com.example.risingworldstarter.database.Database;
import com.example.risingworldstarter.database.DocumentStore;
import com.example.risingworldstarter.database.MongoSchema;
import org.bson.Document;
import static com.example.risingworldstarter.database.DocumentStore.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
        return database.transaction(store -> {
            store.insertIfAbsent("balances", doc("account_id", uid), doc("balance", initialBalance));
            return balance(store, uid);
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
        return database.read(store -> store.exists("balances", doc("account_id", uid)));
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
        return database.transaction(store -> store.delete("balances", doc("account_id", uid)) > 0);
    }

    public void migrateLegacy(Path dataFile) {
        if (!Files.isRegularFile(dataFile)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(dataFile)) { properties.load(input); }
        catch (IOException exception) { throw new IllegalStateException("Could not migrate balances", exception); }
        database.write(store -> {
            for (String uid : properties.stringPropertyNames()) {
                try {
                    long value = Math.max(0, Long.parseLong(properties.getProperty(uid)));
                    store.insertIfAbsent("balances", doc("account_id", uid), doc("balance", value));
                } catch (NumberFormatException ignored) { }
            }
            return null;
        });
    }

    private void upsert(String uid, long amount) {
        database.write(connection -> { upsert(connection, uid, amount); return null; });
    }

    private static void upsert(DocumentStore connection, String uid, long amount) {
        balance(connection, uid, amount);
    }

    private static long selectBalance(DocumentStore connection, String uid) {
        return balance(connection, uid);
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
