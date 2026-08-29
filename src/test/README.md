# Tests

Run the non-UI suite with:

```text
./gradlew test
```

The suite uses JUnit 5, Mockito for dependency-isolation tests, and temporary
SQLite databases for persistence and transaction behavior. `test` compiles
only the database-backed modules, allowing backend tests to run independently
from Rising World's UI and game-event API.

JaCoCo runs with the test task and writes HTML and XML coverage reports under
`build/reports/jacoco/test/`. When `alternateBuildDir` is used, replace `build`
with that directory.
