package com.example.risingworldstarter;

import com.example.risingworldstarter.claims.ChestService;
import com.example.risingworldstarter.claims.ClaimAdminService;
import com.example.risingworldstarter.claims.ClaimService;
import com.example.risingworldstarter.database.Database;
import com.example.risingworldstarter.economy.DatabaseEconomyService;

import java.nio.file.Path;
import java.sql.PreparedStatement;

final class LegacyStateMigrator {
    private static final String MARKER = "legacy_flat_files_migrated_v1";

    private LegacyStateMigrator() { }

    static boolean migrate(Database database, Path worldDataPath, DatabaseEconomyService economy,
                           ClaimService claims, ClaimAdminService claimAdmins, ChestService chests,
                           CharacterService characters) {
        boolean alreadyMigrated = database.read(connection -> {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT 1 FROM metadata WHERE key = ?")) {
                query.setString(1, MARKER);
                try (var result = query.executeQuery()) { return result.next(); }
            }
        });
        if (alreadyMigrated) return false;

        economy.migrateLegacy(worldDataPath.resolve("balances.properties"));
        claims.migrateLegacy(worldDataPath.resolve("claims.properties"));
        claimAdmins.migrateLegacy(worldDataPath.resolve("claim-admins.properties"));
        chests.migrateLegacy(worldDataPath.resolve("chests.properties"));
        characters.migrateLegacy(worldDataPath.resolve("characters"));

        database.write(connection -> {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO metadata(key, value) VALUES (?, CURRENT_TIMESTAMP)")) {
                insert.setString(1, MARKER);
                insert.executeUpdate();
            }
            return null;
        });
        return true;
    }
}
