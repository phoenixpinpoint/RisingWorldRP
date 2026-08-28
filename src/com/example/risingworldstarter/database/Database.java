package com.example.risingworldstarter.database;

public interface Database extends AutoCloseable {
    <T> T read(SqlWork<T> work);

    <T> T transaction(SqlWork<T> work);

    default void write(SqlWork<Void> work) {
        transaction(work);
    }

    @Override
    void close();
}
