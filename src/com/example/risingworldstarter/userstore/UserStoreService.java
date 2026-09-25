package com.example.risingworldstarter.userstore;

import com.example.risingworldstarter.database.Database;
import com.example.risingworldstarter.database.DocumentStore;
import com.example.risingworldstarter.database.MongoSchema;
import org.bson.Document;
import static com.example.risingworldstarter.database.DocumentStore.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Database-backed escrow listings and atomic buyer-to-seller settlement. */
public final class UserStoreService {
    private final Database database;
    public UserStoreService(Database database) { this.database = database; }

    public UserStoreListing create(String sellerKey, String sellerName, short itemType,
                                   int variant, int quantity, long price) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be greater than zero.");
        if (price <= 0) throw new IllegalArgumentException("Price must be greater than zero.");
        return database.transaction(s -> {
            UserStoreListing listing = new UserStoreListing(s.nextId("user_store_listings"), sellerKey, sellerName, itemType, variant, quantity, price);
            insert(s, listing);
            return listing;
        });
    }

    public List<UserStoreListing> getListings() {
        return database.read(s -> s.find("user_store_listings", doc(), doc("listing_id", 1)).stream()
                .map(UserStoreService::read).toList());
    }

    public Set<Short> getListedItemTypes() {
        return database.read(s -> {
            Set<Short> result = new HashSet<>();
            for (Document d : s.find("user_store_listings", doc())) result.add((short) number(d, "item_type"));
            return Set.copyOf(result);
        });
    }

    public boolean hasListings(String sellerKey) {
        return database.read(s -> s.exists("user_store_listings", doc("seller_key", sellerKey)));
    }

    public Optional<UserStoreListing> cancel(long listingId, String sellerKey) {
        return database.transaction(connection -> {
            UserStoreListing listing = find(connection, listingId).orElse(null);
            if (listing == null) return Optional.empty();
            if (!listing.sellerKey().equals(sellerKey))
                throw new IllegalStateException("You can only cancel your own listings.");
            delete(connection, listingId);
            return Optional.of(listing);
        });
    }

    public UserStoreListing purchase(long listingId, String buyerKey) {
        return database.transaction(connection -> {
            UserStoreListing listing = find(connection, listingId)
                    .orElseThrow(() -> new IllegalStateException("That listing is no longer available."));
            if (listing.sellerKey().equals(buyerKey))
                throw new IllegalStateException("Cancel your own listing instead of buying it.");
            long buyerBalance = balance(connection, buyerKey);
            if (buyerBalance < listing.price()) throw new IllegalStateException("You cannot afford this listing.");
            long sellerBalance = Math.addExact(balance(connection, listing.sellerKey()), listing.price());
            setBalance(connection, buyerKey, buyerBalance - listing.price());
            setBalance(connection, listing.sellerKey(), sellerBalance);
            delete(connection, listingId);
            return listing;
        });
    }

    /** Compensates a completed purchase when the buyer's inventory rejects the item. */
    public void reversePurchase(UserStoreListing listing, String buyerKey) {
        database.write(s -> {
            long sellerBalance = balance(s, listing.sellerKey());
            if (sellerBalance < listing.price()) throw new IllegalStateException("Could not reverse marketplace settlement.");
            setBalance(s, listing.sellerKey(), sellerBalance - listing.price());
            setBalance(s, buyerKey, Math.addExact(balance(s, buyerKey), listing.price()));
            insert(s, listing);
            return null;
        });
    }

    private static void insert(DocumentStore s, UserStoreListing listing) {
        s.insert("user_store_listings", doc("listing_id", listing.id(), "seller_key", listing.sellerKey(),
                "seller_name", listing.sellerName(), "item_type", Short.toUnsignedInt(listing.itemType()),
                "item_variant", listing.itemVariant(), "quantity", listing.quantity(), "price", listing.price(),
                "created_at", java.time.Instant.now().toString()));
    }
    private static Optional<UserStoreListing> find(DocumentStore connection, long id) {
        return connection.first("user_store_listings", doc("listing_id", id)).map(UserStoreService::read);
    }
    private static UserStoreListing read(Document row) {
        return new UserStoreListing(number(row, "listing_id"), row.getString("seller_key"), row.getString("seller_name"),
                (short) number(row, "item_type"), (int) number(row, "item_variant"), (int) number(row, "quantity"), number(row, "price"));
    }
    private static void delete(DocumentStore c,long id){
        if (c.delete("user_store_listings", doc("listing_id", id)) == 0)
            throw new IllegalStateException("That listing is no longer available.");
    }
    private static long balance(DocumentStore c,String id){
        return DocumentStore.balance(c, id);
    }
    private static void setBalance(DocumentStore c,String id,long value){
        DocumentStore.balance(c, id, value);
    }
}
