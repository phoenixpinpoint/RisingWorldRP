package com.example.risingworldstarter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

final class FileEconomyService implements EconomyApi {
    private final Path dataFile;
    private final Map<String, Long> balances = new HashMap<>();

    FileEconomyService(Path dataFile) {
        this.dataFile = Objects.requireNonNull(dataFile, "dataFile");
        load();
    }

    @Override
    public synchronized long createAccount(String playerUid, long initialBalance) {
        requireNonNegative(initialBalance, "initialBalance");
        String uid = requireUid(playerUid);
        Long existing = balances.get(uid);
        if (existing != null) {
            return existing;
        }
        balances.put(uid, initialBalance);
        save();
        return initialBalance;
    }

    @Override
    public synchronized long getBalance(String playerUid) {
        return balances.getOrDefault(requireUid(playerUid), 0L);
    }

    @Override
    public synchronized boolean hasAccount(String playerUid) {
        return balances.containsKey(requireUid(playerUid));
    }

    @Override
    public synchronized long setBalance(String playerUid, long amount) {
        requireNonNegative(amount, "amount");
        balances.put(requireUid(playerUid), amount);
        save();
        return amount;
    }

    @Override
    public synchronized long deposit(String playerUid, long amount) {
        requirePositive(amount, "amount");
        String uid = requireUid(playerUid);
        long updated = Math.addExact(balances.getOrDefault(uid, 0L), amount);
        balances.put(uid, updated);
        save();
        return updated;
    }

    @Override
    public synchronized boolean withdraw(String playerUid, long amount) {
        requirePositive(amount, "amount");
        String uid = requireUid(playerUid);
        long current = balances.getOrDefault(uid, 0L);
        if (current < amount) {
            return false;
        }
        balances.put(uid, current - amount);
        save();
        return true;
    }

    private void load() {
        if (!Files.exists(dataFile)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(dataFile)) {
            properties.load(input);
            for (String uid : properties.stringPropertyNames()) {
                long balance = Long.parseLong(properties.getProperty(uid));
                if (balance >= 0) {
                    balances.put(uid, balance);
                }
            }
        } catch (IOException | NumberFormatException exception) {
            throw new IllegalStateException("Could not load economy data from " + dataFile, exception);
        }
    }

    private void save() {
        Path temporaryFile = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
        Properties properties = new Properties();
        balances.forEach((uid, balance) -> properties.setProperty(uid, Long.toString(balance)));

        try {
            Files.createDirectories(dataFile.getParent());
            try (OutputStream output = Files.newOutputStream(temporaryFile)) {
                properties.store(output, "Rising World economy balances (minor currency units)");
            }
            try {
                Files.move(temporaryFile, dataFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporaryFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save economy data to " + dataFile, exception);
        }
    }

    private static String requireUid(String playerUid) {
        if (playerUid == null || playerUid.isBlank()) {
            throw new IllegalArgumentException("playerUid must not be blank");
        }
        return playerUid;
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
