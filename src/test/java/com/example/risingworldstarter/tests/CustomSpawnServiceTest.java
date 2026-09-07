package com.example.risingworldstarter.tests;

import com.example.risingworldstarter.spawns.CustomSpawnService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class CustomSpawnServiceTest extends SqliteTestSupport {
    @Test void savesUpdatesFindsAndDeletesCaseInsensitively() {
        var spawns = new CustomSpawnService(database);
        spawns.save("character", "Home", 1, 2, 3, 0, 0, 0, 1);
        spawns.save("character", "home", 4, 5, 6, 0, 0.5f, 0, 0.5f);

        var saved = spawns.find("character", "HOME").orElseThrow();
        assertAll(() -> assertEquals("Home", saved.name()),
                () -> assertEquals(4, saved.x()),
                () -> assertEquals(0.5f, saved.rotationY()),
                () -> assertEquals(1, spawns.getAll("character").size()));
        assertTrue(spawns.delete("character", "hOmE"));
        assertFalse(spawns.delete("character", "home"));
    }

    @Test void limitsEachCharacterToThreeSpawns() {
        var spawns = new CustomSpawnService(database);
        spawns.save("one", "a", 0, 0, 0, 0, 0, 0, 1);
        spawns.save("one", "b", 0, 0, 0, 0, 0, 0, 1);
        spawns.save("one", "c", 0, 0, 0, 0, 0, 0, 1);
        assertThrows(IllegalStateException.class,
                () -> spawns.save("one", "d", 0, 0, 0, 0, 0, 0, 1));

        assertDoesNotThrow(() -> spawns.save("two", "d", 0, 0, 0, 0, 0, 0, 1));
        assertEquals(3, spawns.getAll("one").size());
        assertEquals(1, spawns.getAll("two").size());
        assertEquals(3, spawns.deleteAll("one"));
        assertTrue(spawns.getAll("one").isEmpty());
        assertEquals(1, spawns.getAll("two").size());
    }

    @Test void validatesCharacterAndSpawnNames() {
        var spawns = new CustomSpawnService(database);
        assertThrows(IllegalArgumentException.class,
                () -> spawns.save(" ", "home", 0, 0, 0, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> spawns.save("character", "two words", 0, 0, 0, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> spawns.find("character", "x".repeat(25)));
    }
}
