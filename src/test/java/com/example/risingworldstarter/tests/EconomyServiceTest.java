package com.example.risingworldstarter.tests;

import com.example.risingworldstarter.economy.DatabaseEconomyService;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

final class EconomyServiceTest extends SqliteTestSupport {
    @Test void depositsAndWithdrawalsPersist() {
        var economy = new DatabaseEconomyService(database);
        assertEquals(1_000L, economy.createAccount("character", 1_000L));
        assertEquals(1_250L, economy.deposit("character", 250L));
        assertTrue(economy.withdraw("character", 400L));
        assertFalse(economy.withdraw("character", 1_000L));
        assertEquals(850L, economy.getBalance("character"));
    }

    @Test void rejectsInvalidAmounts() {
        var economy = new DatabaseEconomyService(database);
        assertThrows(IllegalArgumentException.class, () -> economy.deposit("character", 0));
        assertThrows(IllegalArgumentException.class, () -> economy.setBalance("character", -1));
    }

    @Test void supportsAccountLifecycleAndLegacyMigration() throws Exception {
        var legacy = temporaryDirectory.resolve("balances.properties");
        Files.writeString(legacy, "one=1200\ninvalid=nope\nnegative=-50\n");
        var economy = new DatabaseEconomyService(database);
        economy.migrateLegacy(legacy);
        assertTrue(economy.hasAccount("one"));
        assertEquals(1_200L, economy.getBalance("one"));
        assertEquals(0L, economy.getBalance("negative"));
        assertEquals(900L, economy.setBalance("one", 900L));
        assertTrue(economy.deleteAccount("one"));
        assertFalse(economy.deleteAccount("one"));
        economy.migrateLegacy(temporaryDirectory.resolve("missing.properties"));
    }

    @Test void rejectsBlankAccountsAndOverflow() {
        var economy = new DatabaseEconomyService(database);
        assertThrows(IllegalArgumentException.class, () -> economy.getBalance(" "));
        economy.createAccount("rich", Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> economy.deposit("rich", 1));
    }
}
