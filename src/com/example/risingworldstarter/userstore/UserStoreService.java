package com.example.risingworldstarter.userstore;

import com.example.risingworldstarter.database.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
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
        return database.transaction(connection -> {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO user_store_listings(seller_key,seller_name,item_type,item_variant,quantity,price) "
                            + "VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, sellerKey); insert.setString(2, sellerName);
                insert.setInt(3, Short.toUnsignedInt(itemType)); insert.setInt(4, variant);
                insert.setInt(5, quantity); insert.setLong(6, price); insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (!keys.next()) throw new IllegalStateException("Could not create user-store listing.");
                    return new UserStoreListing(keys.getLong(1), sellerKey, sellerName,
                            itemType, variant, quantity, price);
                }
            }
        });
    }

    public List<UserStoreListing> getListings() { return database.read(connection -> {
        List<UserStoreListing> result = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT listing_id,seller_key,seller_name,item_type,item_variant,quantity,price "
                        + "FROM user_store_listings ORDER BY created_at,listing_id");
             ResultSet rows = query.executeQuery()) {
            while (rows.next()) result.add(read(rows));
        }
        return List.copyOf(result);
    }); }

    public Set<Short> getListedItemTypes() { return database.read(connection -> {
        Set<Short> result = new HashSet<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT DISTINCT item_type FROM user_store_listings"); ResultSet rows = query.executeQuery()) {
            while (rows.next()) result.add((short) rows.getInt(1));
        }
        return Set.copyOf(result);
    }); }

    public boolean hasListings(String sellerKey) { return database.read(connection -> {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM user_store_listings WHERE seller_key=? LIMIT 1")) {
            query.setString(1, sellerKey); try (ResultSet row = query.executeQuery()) { return row.next(); }
        }
    }); }

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
        database.transaction(connection -> {
            long sellerBalance = balance(connection, listing.sellerKey());
            if (sellerBalance < listing.price())
                throw new IllegalStateException("Could not reverse marketplace settlement.");
            setBalance(connection, listing.sellerKey(), sellerBalance - listing.price());
            setBalance(connection, buyerKey, Math.addExact(balance(connection, buyerKey), listing.price()));
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO user_store_listings(listing_id,seller_key,seller_name,item_type,item_variant,quantity,price) VALUES(?,?,?,?,?,?,?)")) {
                insert.setLong(1, listing.id()); insert.setString(2, listing.sellerKey());
                insert.setString(3, listing.sellerName()); insert.setInt(4, Short.toUnsignedInt(listing.itemType()));
                insert.setInt(5, listing.itemVariant()); insert.setInt(6, listing.quantity());
                insert.setLong(7, listing.price()); insert.executeUpdate();
            }
            return null;
        });
    }

    private static Optional<UserStoreListing> find(java.sql.Connection connection, long id) throws java.sql.SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT listing_id,seller_key,seller_name,item_type,item_variant,quantity,price FROM user_store_listings WHERE listing_id=?")) {
            query.setLong(1, id); try (ResultSet row = query.executeQuery()) {
                return row.next() ? Optional.of(read(row)) : Optional.empty();
            }
        }
    }
    private static UserStoreListing read(ResultSet row) throws java.sql.SQLException { return new UserStoreListing(
            row.getLong(1), row.getString(2), row.getString(3), (short) row.getInt(4),
            row.getInt(5), row.getInt(6), row.getLong(7)); }
    private static void delete(java.sql.Connection c,long id)throws java.sql.SQLException{try(PreparedStatement q=c.prepareStatement("DELETE FROM user_store_listings WHERE listing_id=?")){q.setLong(1,id);if(q.executeUpdate()==0)throw new IllegalStateException("That listing is no longer available.");}}
    private static long balance(java.sql.Connection c,String id)throws java.sql.SQLException{try(PreparedStatement q=c.prepareStatement("SELECT balance FROM balances WHERE account_id=?")){q.setString(1,id);try(ResultSet r=q.executeQuery()){return r.next()?r.getLong(1):0L;}}}
    private static void setBalance(java.sql.Connection c,String id,long value)throws java.sql.SQLException{try(PreparedStatement q=c.prepareStatement("INSERT INTO balances(account_id,balance) VALUES(?,?) ON CONFLICT(account_id) DO UPDATE SET balance=excluded.balance")){q.setString(1,id);q.setLong(2,value);q.executeUpdate();}}
}
