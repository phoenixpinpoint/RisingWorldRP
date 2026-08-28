package com.example.risingworldstarter.database;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
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
            String schema = loadSchema().replaceAll("(?m)^\\s*--.*(?:\\R|$)", "");
            for (String sql : schema.split(";")) {
                if (sql.isBlank()) continue;
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(sql);
                }
            }
            return null;
        });
    }

    private static String loadSchema() {
        String resource = "/com/example/risingworldstarter/database/schema.sql";
        try (InputStream input = SqliteDatabase.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Database schema resource is missing: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load database schema", exception);
        }
    }
}
