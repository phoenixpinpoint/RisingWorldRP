package com.example.risingworldstarter.tests;

import com.example.risingworldstarter.database.SqliteDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

abstract class SqliteTestSupport {
    @TempDir Path temporaryDirectory;
    protected SqliteDatabase database;

    @BeforeEach void openDatabase() {
        database = new SqliteDatabase(temporaryDirectory.resolve("test.db"));
    }

    @AfterEach void closeDatabase() {
        database.close();
    }
}
