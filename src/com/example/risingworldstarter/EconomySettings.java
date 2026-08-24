package com.example.risingworldstarter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

record EconomySettings(long defaultBalance, long claimCost) {
    private static final String DEFAULT_BALANCE = "25000.00";
    private static final String CLAIM_COST = "10000.00";

    static EconomySettings load(Path path) {
        Properties properties = new Properties();
        if (Files.exists(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException exception) {
                throw new IllegalStateException("Could not load economy settings from " + path, exception);
            }
        } else {
            properties.setProperty("default-balance", DEFAULT_BALANCE);
            properties.setProperty("claim-cost", CLAIM_COST);
            try {
                Files.createDirectories(path.getParent());
                try (OutputStream output = Files.newOutputStream(path)) {
                    properties.store(output, "Amounts are expressed in dollars");
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Could not create economy settings at " + path, exception);
            }
        }

        return new EconomySettings(
                toMinorUnits(properties.getProperty("default-balance", DEFAULT_BALANCE), "default-balance"),
                toMinorUnits(properties.getProperty("claim-cost", CLAIM_COST), "claim-cost"));
    }

    private static long toMinorUnits(String value, String settingName) {
        try {
            long amount = new BigDecimal(value).movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            if (amount < 0) {
                throw new IllegalArgumentException(settingName + " must not be negative");
            }
            return amount;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(settingName + " must be a valid amount with at most two decimals", exception);
        }
    }
}
