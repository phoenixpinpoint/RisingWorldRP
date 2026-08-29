package com.example.risingworldstarter.tests;

import com.example.risingworldstarter.journal.JournalService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class JournalServiceTest extends SqliteTestSupport {
    @Test void sectionsAndOrderedPagesAreCharacterScoped() {
        var journals = new JournalService(database);
        var section = journals.createSection("one", "Plans");
        var first = journals.getPages("one", section.id()).get(0);
        journals.savePage("one", first.id(), "First page");
        var second = journals.createPage("one", section.id());
        journals.savePage("one", second.id(), "Second page");
        var pages = journals.getPages("one", section.id());
        assertAll(() -> assertEquals(2, pages.size()),
                () -> assertEquals(1, pages.get(0).pageNumber()),
                () -> assertEquals("Second page", pages.get(1).content()));
        assertThrows(IllegalStateException.class, () -> journals.getPages("two", section.id()));
    }

    @Test void opensDefaultsAndDeletesWholeJournal() {
        var journals=new JournalService(database);
        var sections=journals.open("character");
        assertEquals("Notes",sections.get(0).title());
        journals.createSection("character","Ideas");
        assertEquals(2,journals.getSections("character").size());
        assertEquals(2,journals.deleteJournal("character"));
        assertTrue(journals.getSections("character").isEmpty());
    }

    @Test void validatesTitlesContentAndOwnership() {
        var journals=new JournalService(database);
        assertThrows(IllegalArgumentException.class,()->journals.open(" "));
        assertThrows(IllegalArgumentException.class,()->journals.createSection("character"," "));
        assertThrows(IllegalArgumentException.class,()->journals.createSection("character","x".repeat(49)));
        var section=journals.createSection("character","Notes");
        var page=journals.getPages("character",section.id()).get(0);
        assertThrows(IllegalArgumentException.class,()->journals.savePage("character",page.id(),"x".repeat(8_001)));
        assertThrows(IllegalStateException.class,()->journals.savePage("other",page.id(),"no"));
        assertThrows(IllegalStateException.class,()->journals.createPage("other",section.id()));
        journals.savePage("character",page.id(),null);
        assertEquals("",journals.getPages("character",section.id()).get(0).content());
    }
}
