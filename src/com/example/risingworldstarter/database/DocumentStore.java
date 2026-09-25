package com.example.risingworldstarter.database;

import org.bson.Document;
import java.util.List;
import java.util.Optional;

/** Operations scoped to one database read or transaction; do not retain this object. */
public interface DocumentStore {
    List<Document> find(String collection, Document filter, Document sort);
    default List<Document> find(String collection, Document filter) {
        return find(collection, filter, new Document());
    }
    default Optional<Document> first(String collection, Document filter) {
        return find(collection, filter).stream().findFirst();
    }
    default boolean exists(String collection, Document filter) { return first(collection, filter).isPresent(); }
    void insert(String collection, Document document);
    default void insertMany(String collection, List<Document> documents) {
        documents.forEach(d -> insert(collection, d));
    }
    long update(String collection, Document filter, Document values);
    long delete(String collection, Document filter);
    long nextId(String collection);
    void advanceId(String collection, long minimum);
    default boolean insertIfAbsent(String collection, Document key, Document values) {
        if (exists(collection, key)) return false;
        Document document = new Document(key);
        document.putAll(values);
        insert(collection, document);
        return true;
    }
    default void put(String collection, Document key, Document values) {
        if (!insertIfAbsent(collection, key, values)) update(collection, key, values);
    }
    static Document doc(Object... pairs) {
        Document result = new Document();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
    static long number(Document document, String field) { return ((Number) document.get(field)).longValue(); }
    static long balance(DocumentStore store, String account) {
        return store.first("balances", doc("account_id", account)).map(d -> number(d, "balance")).orElse(0L);
    }
    static void balance(DocumentStore store, String account, long amount) {
        if (amount < 0) throw new IllegalArgumentException("Balance must not be negative");
        store.put("balances", doc("account_id", account), doc("balance", amount));
    }
}
