package com.example.risingworldstarter.tests;

import com.example.risingworldstarter.economy.DatabaseEconomyService;
import com.example.risingworldstarter.groups.GroupRole;
import com.example.risingworldstarter.groups.GroupService;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

final class GroupServiceTest extends SqliteTestSupport {
    @Test void creatorIsPersistedAsOwnerAndManagerCanUseTreasury() {
        var economy = new DatabaseEconomyService(database);
        var groups = new GroupService(database);
        economy.createAccount("owner", 5_000L);
        var clan = groups.create("Builders", "owner", "Owner");
        assertEquals(GroupRole.OWNER, groups.findByMember("owner").orElseThrow()
                .members().get("owner").role());
        groups.invite(clan.id(), "owner", "manager");
        groups.acceptInvitation("manager", "Manager");
        groups.setManager(clan.id(), "owner", "manager", true);
        assertEquals(1_500L, groups.deposit(clan.id(), "owner", 1_500L));
        assertEquals(1_000L, groups.withdraw(clan.id(), "manager", 500L));
        assertEquals(500L, economy.getBalance("manager"));
    }

    @Test void ordinaryMemberCannotViewTreasury() {
        var groups = new GroupService(database);
        var clan = groups.create("Builders", "owner", "Owner");
        groups.invite(clan.id(), "owner", "member");
        groups.acceptInvitation("member", "Member");
        assertThrows(IllegalStateException.class, () -> groups.getBalance(clan.id(), "member"));
    }

    @Test void enforcesMembershipAndManagementRules() {
        var groups = new GroupService(database);
        var first = groups.create("Builders", "owner", "Owner");
        assertThrows(IllegalStateException.class, () -> groups.create("builders", "other", "Other"));
        assertThrows(IllegalStateException.class, () -> groups.create("Other", "owner", "Owner"));
        groups.invite(first.id(), "owner", "manager");
        groups.acceptInvitation("manager", "Manager");
        groups.setManager(first.id(), "owner", "manager", true);
        groups.invite(first.id(), "manager", "member");
        groups.acceptInvitation("member", "Member");
        assertTrue(groups.canAccess("member", first.claimOwnerId()));
        assertFalse(groups.canAccess("unknown", first.claimOwnerId()));
        assertFalse(groups.canAccess("member", "personal"));
        assertThrows(IllegalStateException.class, () -> groups.kick(first.id(), "member", "manager"));
        assertThrows(IllegalStateException.class, () -> groups.kick(first.id(), "manager", "manager"));
        groups.kick(first.id(), "owner", "manager");
        groups.leave("member");
        assertTrue(groups.findByMember("member").isEmpty());
        assertThrows(IllegalStateException.class, () -> groups.leave("owner"));
        assertEquals(1, groups.getGroups().size());
    }

    @Test void protectsFundedClanAndDisbandsEmptyClan() {
        var groups = new GroupService(database);
        var economy = new DatabaseEconomyService(database);
        economy.createAccount("owner", 1_000L);
        var clan = groups.create("Builders", "owner", "Owner");
        groups.deposit(clan.id(), "owner", 500L);
        assertThrows(IllegalStateException.class, () -> groups.disband(clan.id(), "owner"));
        groups.withdraw(clan.id(), "owner", 500L);
        assertEquals(clan.id(), groups.disband(clan.id(), "owner").id());
        assertTrue(groups.get(clan.id()).isEmpty());
    }

    @Test void removesDeletedMembersAndMigratesLegacyFileOnce() throws Exception {
        var groups = new GroupService(database);
        var clan = groups.create("Current", "owner", "Owner");
        groups.invite(clan.id(), "owner", "member"); groups.acceptInvitation("member", "Member");
        groups.removeDeletedCharacter("member");
        assertTrue(groups.findByMember("member").isEmpty());
        assertThrows(IllegalStateException.class, () -> groups.removeDeletedCharacter("owner"));

        String id="legacy"; String key="legacy-character";
        String encodedName=Base64.getUrlEncoder().withoutPadding().encodeToString("Legacy Clan".getBytes(StandardCharsets.UTF_8));
        String encodedKey=Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
        String encodedMember=Base64.getUrlEncoder().withoutPadding().encodeToString("Legacy Owner".getBytes(StandardCharsets.UTF_8));
        var file=temporaryDirectory.resolve("groups.properties");
        Files.writeString(file,"group."+id+".name="+encodedName+"\n"
                +"group."+id+".member."+encodedKey+"=OWNER:"+encodedMember+"\n");
        groups.migrateLegacy(file); groups.migrateLegacy(file);
        assertEquals("Legacy Clan", groups.findByMember(key).orElseThrow().name());
    }

    @Test void validatesNamesInvitationsAndRoles() {
        var groups=new GroupService(database); var clan=groups.create("Builders","owner","Owner");
        assertThrows(IllegalArgumentException.class,()->groups.create(" ","x","X"));
        assertThrows(IllegalArgumentException.class,()->groups.create("x".repeat(33),"x","X"));
        assertThrows(IllegalStateException.class,()->groups.acceptInvitation("none","None"));
        groups.invite(clan.id(),"owner","member"); groups.acceptInvitation("member","Member");
        assertThrows(IllegalStateException.class,()->groups.invite(clan.id(),"owner","member"));
        assertThrows(IllegalStateException.class,()->groups.setManager(clan.id(),"member","owner",true));
        assertThrows(IllegalStateException.class,()->groups.setManager(clan.id(),"owner","owner",true));
        assertThrows(IllegalStateException.class,()->groups.disband(clan.id(),"member"));
    }
}
