package com.example.risingworldstarter.tests;

import com.example.risingworldstarter.claims.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

final class ClaimServicesTest extends MongoTestSupport {
    @Test void boundsOwnershipAndDeletionArePreserved() {
        var claims = new ClaimService(database);
        assertTrue(claims.claim(-2, 3, "owner", "Owner"));
        assertFalse(claims.claim(-2, 3, "other", "Other"));
        claims.claim(9, 9, "owner", "Owner");
        assertEquals(2, claims.getClaimCount());
        assertEquals(2, claims.getClaimsByOwner("owner").size());
        assertEquals(1, claims.getClaimsInArea(-2, 2, 0, 3).size());
        assertTrue(claims.getClaim(0, 0).isEmpty());
        assertEquals("owner", claims.getClaim(-2, 3).orElseThrow().ownerUid());
        assertFalse(claims.unclaim(-2, 3, "other"));
        claims.migrateOwner("owner", "new", "New");
        assertTrue(claims.unclaim(-2, 3, "new"));
        assertTrue(claims.forceUnclaim(9, 9));
        assertFalse(claims.forceUnclaim(9, 9));
        claims.claim(1, 1, "new", "New");
        assertEquals(1, claims.deleteClaimsByOwner("new"));
        assertThrows(IllegalArgumentException.class, () -> claims.claim(0, 0, " ", "Name"));
    }
    @Test void chestKeysAndAdministratorUpdatesArePersistent() {
        var chests = new ChestService(database);
        assertTrue(chests.get(1, 1, 1, 1).isEmpty());
        var ownership = chests.assign(1, 1, 1, 1, "owner", "Owner");
        assertEquals(ownership, chests.assign(1, 1, 1, 1, "other", "Other"));
        chests.setLocked(1, 1, 1, 1, ownership, true);
        assertTrue(chests.get(1, 1, 1, 1).orElseThrow().locked());
        assertTrue(chests.get(1, 1, 2, 1).isEmpty());
        chests.remove(1, 1, 1, 1);
        assertTrue(chests.get(1, 1, 1, 1).isEmpty());
        var admins = new ClaimAdminService(database);
        admins.add("admin", "First"); admins.add("admin", "Updated");
        assertTrue(admins.contains("admin"));
        assertEquals("Updated", admins.getAll().get("admin"));
        assertTrue(admins.remove("admin")); assertFalse(admins.remove("admin"));
    }
    @Test void importsLegacyClaimsChestsAndAdmins() throws Exception {
        var claims = new ClaimService(database); var chests = new ChestService(database); var admins = new ClaimAdminService(database);
        var missing = temporaryDirectory.resolve("missing");
        claims.migrateLegacy(missing); chests.migrateLegacy(missing); admins.migrateLegacy(missing);
        var claimFile = temporaryDirectory.resolve("claims.properties");
        Files.writeString(claimFile, "1,2=owner:" + encode("Owner") + "\nbad=bad\na,b=owner:bad\n");
        claims.migrateLegacy(claimFile); claims.migrateLegacy(claimFile);
        assertEquals(1, claims.getClaimCount());
        var chestFile = temporaryDirectory.resolve("chests.properties");
        Files.writeString(chestFile, "1,2,3,42=" + encode("owner") + ":" + encode("Owner") + ":true\nbad=bad\na,b,c,d=x:y:true\n");
        chests.migrateLegacy(chestFile);
        assertTrue(chests.get(42, 1, 2, 3).orElseThrow().locked());
        var adminFile = temporaryDirectory.resolve("admins.properties");
        Files.writeString(adminFile, "admin=" + encode("Admin") + "\nbad=!\n");
        admins.migrateLegacy(adminFile);
        assertEquals("Admin", admins.getAll().get("admin"));
    }
    private static String encode(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
}
