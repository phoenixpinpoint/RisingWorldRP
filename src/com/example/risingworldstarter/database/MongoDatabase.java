package com.example.risingworldstarter.database;

import com.mongodb.*;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import org.bson.Document;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.example.risingworldstarter.database.DocumentStore.doc;

/** Atlas-backed, world-isolated storage. A world lock serializes writers across servers. */
public final class MongoDatabase implements Database {
    private static final TransactionOptions TRANSACTION = TransactionOptions.builder()
            .readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY)
            .readPreference(ReadPreference.primary()).build();
    private final MongoClient client;
    private final com.mongodb.client.MongoDatabase database;
    private final String world;

    public MongoDatabase(String uri, String databaseName, String world) {
        if (uri == null || uri.isBlank() || databaseName == null || databaseName.isBlank()
                || world == null || world.isBlank())
            throw new IllegalArgumentException("MongoDB URI, database, and world ID are required");
        this.world = world;
        client = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri)).applicationName("CivicCore")
                .applyToClusterSettings(b -> b.serverSelectionTimeout(15, TimeUnit.SECONDS))
                .applyToSocketSettings(b -> b.connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS))
                .build());
        database = client.getDatabase(databaseName);
        try {
            database.runCommand(doc("ping", 1));
            for (var entry : MongoSchema.KEYS.entrySet()) {
                for (var key : entry.getValue()) {
                    Document index = doc("_world", 1);
                    key.forEach(field -> index.append(field, 1));
                    database.getCollection(entry.getKey()).createIndex(index, new IndexOptions().unique(true));
                }
            }
            index("claims", "owner_id");
            index("group_members", "group_id");
            index("group_invitations", "group_id");
            index("user_store_listings", "seller_key");
            index("characters", "name_key");
            database.getCollection("world_locks").updateOne(doc("_id", world),
                    doc("$setOnInsert", doc("revision", 0L)), new UpdateOptions().upsert(true));
            database.getCollection("counters").createIndex(doc("_world", 1, "name", 1), new IndexOptions().unique(true));
            // Fail startup immediately if the server does not support transactions.
            transaction(store -> null);
        } catch (RuntimeException failure) {
            client.close();
            throw failure;
        }
    }

    private void index(String collection, String field) {
        database.getCollection(collection).createIndex(doc("_world", 1, field, 1));
    }

    @Override public <T> T read(Function<DocumentStore, T> work) { return run(work, false); }
    @Override public <T> T transaction(Function<DocumentStore, T> work) { return run(work, true); }
    private <T> T run(Function<DocumentStore, T> work, boolean writable) {
        try (ClientSession session = client.startSession()) {
            return session.withTransaction(() -> {
                if (writable) database.getCollection("world_locks").updateOne(session,
                        doc("_id", world), doc("$inc", doc("revision", 1L)));
                return work.apply(new Store(session, writable));
            }, TRANSACTION);
        }
    }
    @Override public void close() { client.close(); }

    private final class Store implements DocumentStore {
        private final ClientSession session;
        private final boolean writable;
        private Store(ClientSession session, boolean writable) { this.session = session; this.writable = writable; }
        private Document scoped(Document filter) { return new Document(filter).append("_world", world); }
        private MongoCollection<Document> collection(String name) {
            if (!MongoSchema.KEYS.containsKey(name)) throw new IllegalArgumentException("Unknown collection: " + name);
            return database.getCollection(name);
        }
        private void requireWrite() { if (!writable) throw new IllegalStateException("Cannot mutate a database read"); }
        @Override public List<Document> find(String name, Document filter, Document sort) {
            return collection(name).find(session, scoped(filter)).sort(sort).into(new ArrayList<>());
        }
        @Override public Optional<Document> first(String name, Document filter) {
            return Optional.ofNullable(collection(name).find(session, scoped(filter)).first());
        }
        @Override public void insert(String name, Document document) {
            requireWrite();
            collection(name).insertOne(session, scoped(document));
        }
        @Override public void insertMany(String name, List<Document> documents) {
            requireWrite();
            if (!documents.isEmpty()) collection(name).insertMany(session, documents.stream().map(this::scoped).toList());
        }
        @Override public void advanceId(String name, long minimum) {
            requireWrite();
            collection(name);
            database.getCollection("counters").updateOne(session, scoped(doc("name", name)),
                    doc("$max", doc("value", minimum)), new UpdateOptions().upsert(true));
        }
        @Override public long update(String name, Document filter, Document values) {
            requireWrite();
            if (values.containsKey("_world") || values.containsKey("_id")) throw new IllegalArgumentException("Reserved field");
            return collection(name).updateMany(session, scoped(filter), doc("$set", values)).getMatchedCount();
        }
        @Override public long delete(String name, Document filter) {
            requireWrite();
            return collection(name).deleteMany(session, scoped(filter)).getDeletedCount();
        }
        @Override public long nextId(String name) {
            requireWrite();
            collection(name); // Validate the counter name.
            Document counter = database.getCollection("counters").findOneAndUpdate(session,
                    scoped(doc("name", name)), doc("$inc", doc("value", 1L)),
                    new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
            return DocumentStore.number(counter, "value");
        }
    }
}
