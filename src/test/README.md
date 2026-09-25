# Tests

The persistence tests use a **real MongoDB replica set**, including rollback,
world isolation, cross-client concurrency, and SQLite migration. A standalone
MongoDB server is insufficient because it does not support transactions.

To let the suite start and stop a temporary loopback-only replica set, provide
the path to an installed MongoDB Community `mongod` executable:

```text
./gradlew test check -PmongoTestExecutable=/absolute/path/to/mongod
```

On Windows without a wrapper batch file:

```powershell
java '-Dgradle.user.home=.gradle-user' -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test check '-PmongoTestExecutable=C:/path/to/mongod.exe'
```

Alternatively set `CIVICCORE_TEST_MONGODB_URI` to a dedicated test replica set.
The suite creates a randomly named `civiccore_test_*` database and drops only
that generated database on exit. The test user needs permission to create and
drop it. No production configuration or URI is read. Without either setting,
persistence tests fail with a configuration message rather than silently skip.

Temporary server data and logs remain under `build/mongo-test-*` for diagnosis;
the process stops when the test worker exits. JUnit 5 runs the service tests and
Mockito isolates validation tests. The core suite does not need the game SDK.
The historical `schema.sql` builds SQLite import fixtures only.

JaCoCo runs with the test task and writes HTML and XML coverage reports under
`build/reports/jacoco/test/`. When `alternateBuildDir` is used, replace `build`
with that directory.

`check` enforces the existing 90% line-coverage threshold. `jar` separately
compiles the full plugin against Rising World's SDK and bundles the MongoDB
driver and SQLite importer.
