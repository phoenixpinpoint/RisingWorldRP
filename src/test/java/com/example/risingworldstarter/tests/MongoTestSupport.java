package com.example.risingworldstarter.tests;

import com.example.risingworldstarter.database.MongoDatabase;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Tests run against a real replica set, including MongoDB's actual transaction semantics. */
abstract class MongoTestSupport {
    @TempDir Path temporaryDirectory;
    protected MongoDatabase database;
    protected String worldId;
    protected static String uri;
    protected static String databaseName;
    private static Process server;

    @BeforeAll static synchronized void initializeServer() throws Exception {
        if (uri != null) return;
        String executable = System.getProperty("mongoTestExecutable", "");
        String configuredUri = System.getenv("CIVICCORE_TEST_MONGODB_URI");
        if (!executable.isBlank()) {
            int port;
            try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
            Path directory = Files.createTempDirectory(Path.of("build"), "mongo-test-");
            server = new ProcessBuilder(executable, "--replSet", "civiccoreTest", "--bind_ip", "127.0.0.1",
                    "--port", Integer.toString(port), "--dbpath", directory.toAbsolutePath().toString(),
                    "--logpath", directory.resolve("mongod.log").toAbsolutePath().toString())
                    .redirectErrorStream(true).redirectOutput(directory.resolve("process.log").toFile()).start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.destroy();
                try { if (!server.waitFor(5, TimeUnit.SECONDS)) server.destroyForcibly(); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }));
            String direct = "mongodb://127.0.0.1:" + port + "/?directConnection=true&serverSelectionTimeoutMS=1000";
            try (var client = MongoClients.create(direct)) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
                while (true) {
                    try { client.getDatabase("admin").runCommand(new Document("ping", 1)); break; }
                    catch (com.mongodb.MongoException failure) {
                        if (!server.isAlive() || System.nanoTime() > deadline) throw failure;
                        Thread.sleep(100);
                    }
                }
                client.getDatabase("admin").runCommand(new Document("replSetInitiate",
                        new Document("_id", "civiccoreTest").append("members", List.of(
                                new Document("_id", 0).append("host", "127.0.0.1:" + port)))));
                while (!client.getDatabase("admin").runCommand(new Document("hello", 1)).getBoolean("isWritablePrimary", false)) {
                    if (System.nanoTime() > deadline) throw new IllegalStateException("Test replica set did not become primary");
                    Thread.sleep(100);
                }
            }
            configuredUri = "mongodb://127.0.0.1:" + port + "/?replicaSet=civiccoreTest&serverSelectionTimeoutMS=5000";
        }
        if (configuredUri == null || configuredUri.isBlank())
            throw new IllegalStateException("Set CIVICCORE_TEST_MONGODB_URI to a test replica set, or -PmongoTestExecutable=/path/to/mongod");
        uri = configuredUri;
        databaseName = "civiccore_test_" + UUID.randomUUID().toString().replace("-", "");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (server == null) try (var client = MongoClients.create(uri)) { client.getDatabase(databaseName).drop(); }
        }));
    }

    @BeforeEach void openDatabase() {
        worldId = UUID.randomUUID().toString();
        database = new MongoDatabase(uri, databaseName, worldId);
    }
    @AfterEach void closeDatabase() { if (database != null) database.close(); }
}
