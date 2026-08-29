package com.example.risingworldstarter.tests;

import com.example.risingworldstarter.economy.DatabaseEconomyService;
import com.example.risingworldstarter.userstore.UserStoreService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class UserStoreServiceTest extends SqliteTestSupport {
    @Test void purchaseTransfersFundsAndConsumesListing() {
        var economy = new DatabaseEconomyService(database);
        var store = new UserStoreService(database);
        economy.createAccount("seller", 100L); economy.createAccount("buyer", 2_000L);
        var listing = store.create("seller", "Seller", (short) 7, 0, 3, 1_250L);
        var purchased = store.purchase(listing.id(), "buyer");
        assertAll(() -> assertEquals(3, purchased.quantity()),
                () -> assertEquals(750L, economy.getBalance("buyer")),
                () -> assertEquals(1_350L, economy.getBalance("seller")),
                () -> assertTrue(store.getListings().isEmpty()));
    }

    @Test void reversalRestoresFundsAndListing() {
        var economy = new DatabaseEconomyService(database);
        var store = new UserStoreService(database);
        economy.createAccount("seller", 0L); economy.createAccount("buyer", 2_000L);
        var purchased = store.purchase(store.create("seller", "Seller", (short) 7, 0, 1, 500L).id(), "buyer");
        store.reversePurchase(purchased, "buyer");
        assertEquals(2_000L, economy.getBalance("buyer"));
        assertEquals(0L, economy.getBalance("seller"));
        assertEquals(1, store.getListings().size());
    }

    @Test void exposesStockAndSupportsSellerCancellation() {
        var store=new UserStoreService(database);
        var listing=store.create("seller","Seller",(short)7,2,4,500L);
        assertTrue(store.hasListings("seller"));
        assertFalse(store.hasListings("other"));
        assertTrue(store.getListedItemTypes().contains((short)7));
        assertThrows(IllegalStateException.class,()->store.cancel(listing.id(),"other"));
        assertEquals(listing,store.cancel(listing.id(),"seller").orElseThrow());
        assertTrue(store.cancel(listing.id(),"seller").isEmpty());
    }

    @Test void rejectsInvalidOrUnauthorizedPurchases() {
        var economy=new DatabaseEconomyService(database);var store=new UserStoreService(database);
        economy.createAccount("buyer",100L);
        assertThrows(IllegalArgumentException.class,()->store.create("seller","Seller",(short)1,0,0,100));
        assertThrows(IllegalArgumentException.class,()->store.create("seller","Seller",(short)1,0,1,0));
        var listing=store.create("seller","Seller",(short)1,0,1,500L);
        assertThrows(IllegalStateException.class,()->store.purchase(listing.id(),"seller"));
        assertThrows(IllegalStateException.class,()->store.purchase(listing.id(),"buyer"));
        assertThrows(IllegalStateException.class,()->store.purchase(999,"buyer"));
        assertEquals(1,store.getListings().size());
    }

    @Test void reversalFailsWhenSellerFundsAreGone() {
        var economy=new DatabaseEconomyService(database);var store=new UserStoreService(database);
        economy.createAccount("buyer",1_000L);economy.createAccount("seller",0L);
        var purchased=store.purchase(store.create("seller","Seller",(short)1,0,1,500L).id(),"buyer");
        economy.setBalance("seller",0L);
        assertThrows(IllegalStateException.class,()->store.reversePurchase(purchased,"buyer"));
    }
}
