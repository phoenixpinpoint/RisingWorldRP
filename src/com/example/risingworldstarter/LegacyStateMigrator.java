package com.example.risingworldstarter;

import com.example.risingworldstarter.claims.ChestService;
import com.example.risingworldstarter.claims.ClaimAdminService;
import com.example.risingworldstarter.claims.ClaimService;
import com.example.risingworldstarter.database.Database;
import com.example.risingworldstarter.database.DocumentStore;
import com.example.risingworldstarter.database.MongoSchema;
import org.bson.Document;
import static com.example.risingworldstarter.database.DocumentStore.*;
import com.example.risingworldstarter.economy.DatabaseEconomyService;

import java.nio.file.Path;

final class LegacyStateMigrator {
    private static final String MARKER = "legacy_flat_files_migrated_v1";

    private LegacyStateMigrator() { }

    static boolean migrate(Database database, Path worldDataPath, DatabaseEconomyService economy,
                           ClaimService claims, ClaimAdminService claimAdmins, ChestService chests,
                           CharacterService characters) {
        boolean alreadyMigrated = database.read(s -> s.exists("metadata", doc("key", MARKER)));
        if (alreadyMigrated) return false;
        economy.migrateLegacy(worldDataPath.resolve("balances.properties"));
        claims.migrateLegacy(worldDataPath.resolve("claims.properties"));
        claimAdmins.migrateLegacy(worldDataPath.resolve("claim-admins.properties"));
        chests.migrateLegacy(worldDataPath.resolve("chests.properties"));
        characters.migrateLegacy(worldDataPath.resolve("characters"));
        database.write(s -> { s.insertIfAbsent("metadata", doc("key", MARKER), doc("value", java.time.Instant.now().toString())); return null; });
        return true;
    }
}
