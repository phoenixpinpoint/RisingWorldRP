package com.example.risingworldstarter.tests;

import com.example.risingworldstarter.economy.EconomySettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class EconomySettingsTest {
    @TempDir Path directory;

    @Test void createsDefaultsAndLoadsConfiguredAmounts() throws Exception {
        Path file = directory.resolve("economy.properties");
        EconomySettings defaults = EconomySettings.load(file);
        assertTrue(Files.isRegularFile(file));
        assertTrue(defaults.defaultBalance() > 0);
        Files.writeString(file, "default-balance=12.34\nclaim-cost=5.00\nbase-salary=1.25\n");
        EconomySettings configured = EconomySettings.load(file);
        assertEquals(1_234L, configured.defaultBalance());
        assertEquals(500L, configured.claimCost());
        assertEquals(125L, configured.baseSalary());
    }

    @Test void rejectsNegativeMalformedAndOverPreciseValues() throws Exception {
        Path file = directory.resolve("economy.properties");
        Files.writeString(file, "default-balance=-1\nclaim-cost=5\nbase-salary=1\n");
        assertThrows(IllegalArgumentException.class, () -> EconomySettings.load(file));
        Files.writeString(file, "default-balance=1.001\nclaim-cost=5\nbase-salary=1\n");
        assertThrows(IllegalArgumentException.class, () -> EconomySettings.load(file));
        Files.writeString(file, "default-balance=abc\nclaim-cost=5\nbase-salary=1\n");
        assertThrows(IllegalArgumentException.class, () -> EconomySettings.load(file));
    }
}
