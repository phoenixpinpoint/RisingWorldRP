package com.example.risingworldstarter.database;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** SQLite implementation behind CivicCore's vendor-neutral Database contract. */
public final class SqliteDatabase implements Database {
    private final String connectionUrl;

    public SqliteDatabase(Path databaseFile) {
        connectionUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath().normalize();
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("SQLite JDBC driver is not packaged with CivicCore", exception);
        }
        initializeSchema();
    }

    @Override
    public synchronized <T> T read(SqlWork<T> work) {
        try (Connection connection = openConnection()) {
            return work.run(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Database read failed", exception);
        }
    }

    @Override
    public synchronized <T> T transaction(SqlWork<T> work) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Database transaction failed", exception);
        }
    }

    @Override
    public void close() {
        // Connections are intentionally short-lived and closed after each operation.
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(connectionUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    private void initializeSchema() {
        read(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = WAL");
            }
            return null;
        });
        transaction(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS balances (account_id TEXT PRIMARY KEY, balance INTEGER NOT NULL CHECK(balance >= 0))");
                statement.execute("CREATE TABLE IF NOT EXISTS claims (chunk_x INTEGER NOT NULL, chunk_z INTEGER NOT NULL, owner_id TEXT NOT NULL, owner_name TEXT NOT NULL, PRIMARY KEY(chunk_x, chunk_z))");
                statement.execute("CREATE INDEX IF NOT EXISTS claims_owner_idx ON claims(owner_id)");
                statement.execute("CREATE TABLE IF NOT EXISTS claim_admins (player_uid TEXT PRIMARY KEY, player_name TEXT NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS chests (global_id INTEGER NOT NULL, chunk_x INTEGER NOT NULL, chunk_y INTEGER NOT NULL, chunk_z INTEGER NOT NULL, owner_id TEXT NOT NULL, owner_name TEXT NOT NULL, locked INTEGER NOT NULL, PRIMARY KEY(global_id, chunk_x, chunk_y, chunk_z))");
                statement.execute("CREATE TABLE IF NOT EXISTS accounts (account_uid TEXT PRIMARY KEY, profile_name TEXT NOT NULL, profile_state TEXT)");
                statement.execute("CREATE TABLE IF NOT EXISTS characters (character_id TEXT PRIMARY KEY, account_uid TEXT NOT NULL REFERENCES accounts(account_uid) ON DELETE CASCADE, slot INTEGER NOT NULL, name TEXT NOT NULL, state TEXT, inventory BLOB, clothes BLOB, UNIQUE(account_uid, slot))");
            }
            return null;
        });
    }
}
