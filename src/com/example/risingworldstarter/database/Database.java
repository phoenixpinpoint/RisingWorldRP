package com.example.risingworldstarter.database;

public interface Database extends AutoCloseable {
    <T> T read(java.util.function.Function<DocumentStore, T> work);

    <T> T transaction(java.util.function.Function<DocumentStore, T> work);

    default void write(java.util.function.Function<DocumentStore, Void> work) {
        transaction(work);
    }

    @Override
    void close();
}
