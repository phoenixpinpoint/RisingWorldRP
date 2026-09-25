package com.example.risingworldstarter.tests;

import com.example.risingworldstarter.database.*;
import com.example.risingworldstarter.economy.DatabaseEconomyService;
import com.example.risingworldstarter.userstore.UserStoreService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.*;
import static com.example.risingworldstarter.database.DocumentStore.*;
import static org.junit.jupiter.api.Assertions.*;

final class MongoDatabaseTest extends MongoTestSupport {
    @Test void transactionRollsBackAndReadsCannotWrite() {
        assertThrows(IllegalStateException.class, () -> database.transaction(s -> {
            s.insert("balances", doc("account_id", "one", "balance", 10L));
            throw new IllegalStateException("rollback");
        }));
        assertFalse(database.read(s -> s.exists("balances", doc("account_id", "one"))).booleanValue());
        assertThrows(IllegalStateException.class, () -> database.read(s -> { s.insert("balances", doc()); return null; }));
        assertThrows(IllegalArgumentException.class, () -> database.read(s -> s.find("unknown", doc())));
        assertThrows(IllegalArgumentException.class, () -> database.transaction(s -> s.update("balances", doc(), doc("_world", "other"))));
        assertThrows(IllegalArgumentException.class, () -> database.transaction(s -> { balance(s, "one", -1); return null; }));
        assertThrows(IllegalArgumentException.class, () -> new MongoDatabase("", "test", "world"));
    }

    @Test void persistsAcrossClientsAndIsolatesWorlds() {
        var economy = new DatabaseEconomyService(database);
        economy.createAccount("one", 100);
        try (var same = new MongoDatabase(uri, databaseName, worldId);
             var other = new MongoDatabase(uri, databaseName, worldId + "_other")) {
            assertEquals(100, new DatabaseEconomyService(same).getBalance("one"));
            assertEquals(0, new DatabaseEconomyService(other).getBalance("one"));
            new DatabaseEconomyService(other).setBalance("one", 999);
            assertEquals(100, economy.getBalance("one"));
        }
    }

    @Test void concurrentClientsDoNotLoseDepositsOrDoubleSell() throws Exception {
        var economy = new DatabaseEconomyService(database);
        economy.createAccount("buyer", 1000);
        var store = new UserStoreService(database);
        var listing = store.create("seller", "Seller", (short) 1, 0, 1, 100);
        try (var second = new MongoDatabase(uri, databaseName, worldId)) {
            var otherEconomy = new DatabaseEconomyService(second);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                for (Future<?> future : pool.invokeAll(List.<Callable<Void>>of(
                        () -> { for (int i = 0; i < 20; i++) economy.deposit("counter", 1); return null; },
                        () -> { for (int i = 0; i < 20; i++) otherEconomy.deposit("counter", 1); return null; }))) future.get();
                assertEquals(40, economy.getBalance("counter"));
                var results = pool.invokeAll(List.<Callable<Boolean>>of(
                        () -> purchase(store, listing.id()), () -> purchase(new UserStoreService(second), listing.id())));
                assertNotEquals(results.get(0).get(), results.get(1).get());
                assertEquals(900, economy.getBalance("buyer"));
                assertEquals(100, economy.getBalance("seller"));
            } finally { pool.shutdownNow(); }
        }
    }
    private static boolean purchase(UserStoreService store, long id) {
        try { store.purchase(id, "buyer"); return true; }
        catch (IllegalStateException sold) { return false; }
    }

    @Test void uniqueViolationRollsBackEarlierWritesAndCounters() {
        database.write(s -> { s.insert("balances", doc("account_id", "one", "balance", 1L)); return null; });
        assertThrows(com.mongodb.MongoWriteException.class, () -> database.transaction(s -> {
            balance(s, "other", 100);
            s.nextId("journal_pages");
            s.insert("balances", doc("account_id", "one", "balance", 20L));
            return null;
        }));
        assertFalse(new DatabaseEconomyService(database).hasAccount("other"));
        assertEquals(1L, database.transaction(s -> s.nextId("journal_pages")).longValue());
    }
}
