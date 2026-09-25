package com.example.risingworldstarter.journal;

import com.example.risingworldstarter.database.Database;
import com.example.risingworldstarter.database.DocumentStore;
import com.example.risingworldstarter.database.MongoSchema;
import org.bson.Document;
import static com.example.risingworldstarter.database.DocumentStore.*;

import java.util.ArrayList;
import java.util.List;

/** Character-scoped journal sections and ordered pages. */
public final class JournalService {
    public static final int MAX_SECTION_TITLE = 48;
    public static final int MAX_PAGE_CHARACTERS = 8_000;
    private final Database database;

    public JournalService(Database database) { this.database = database; }

    /** Ensures a journal has at least one section and page and returns its sections. */
    public List<JournalSection> open(String characterKey) {
        requireCharacter(characterKey);
        database.write(s -> {
            if (!s.exists("journal_sections", doc("character_key", characterKey))) createSection(s, characterKey, "Notes");
            return null;
        });
        return getSections(characterKey);
    }

    public List<JournalSection> getSections(String characterKey) {
        requireCharacter(characterKey);
        return database.read(s -> s.find("journal_sections", doc("character_key", characterKey), doc("section_order", 1))
                .stream().map(d -> new JournalSection(number(d, "section_id"), d.getString("title"), (int) number(d, "section_order"))).toList());
    }

    public JournalSection createSection(String characterKey, String title) {
        requireCharacter(characterKey);
        String normalizedTitle = requireTitle(title);
        return database.transaction(connection -> createSection(connection, characterKey, normalizedTitle));
    }

    public List<JournalPage> getPages(String characterKey, long sectionId) {
        return database.read(s -> {
            requireOwnedSection(s, characterKey, sectionId);
            return s.find("journal_pages", doc("section_id", sectionId), doc("page_number", 1)).stream()
                    .map(d -> new JournalPage(number(d, "page_id"), sectionId, (int) number(d, "page_number"), d.getString("content"))).toList();
        });
    }

    public JournalPage createPage(String characterKey, long sectionId) {
        return database.transaction(s -> {
            requireOwnedSection(s, characterKey, sectionId);
            int next = s.find("journal_pages", doc("section_id", sectionId)).stream()
                    .mapToInt(d -> (int) number(d, "page_number")).max().orElse(0) + 1;
            return insertPage(s, sectionId, next);
        });
    }

    public void savePage(String characterKey, long pageId, String content) {
        String normalized = content == null ? "" : content;
        if (normalized.length() > MAX_PAGE_CHARACTERS)
            throw new IllegalArgumentException("Journal pages cannot exceed " + MAX_PAGE_CHARACTERS + " characters.");
        database.write(s -> {
            Document page = s.first("journal_pages", doc("page_id", pageId))
                    .orElseThrow(() -> new IllegalStateException("Journal page no longer exists."));
            requireOwnedSection(s, characterKey, number(page, "section_id"));
            s.update("journal_pages", doc("page_id", pageId), doc("content", normalized));
            return null;
        });
    }

    public int deleteJournal(String characterKey) {
        requireCharacter(characterKey);
        return database.transaction(s -> {
            for (Document d : s.find("journal_sections", doc("character_key", characterKey)))
                s.delete("journal_pages", doc("section_id", number(d, "section_id")));
            return Math.toIntExact(s.delete("journal_sections", doc("character_key", characterKey)));
        });
    }

    private static JournalSection createSection(DocumentStore connection, String characterKey,
                                                 String title) {
        int order = connection.find("journal_sections", doc("character_key", characterKey)).stream()
                .mapToInt(d -> (int) number(d, "section_order")).max().orElse(0) + 1;
        long id = connection.nextId("journal_sections");
        connection.insert("journal_sections", doc("section_id", id, "character_key", characterKey, "title", title, "section_order", order));
        insertPage(connection, id, 1);
        return new JournalSection(id, title, order);
    }

    private static JournalPage insertPage(DocumentStore connection, long sectionId,
                                          int pageNumber) {
        long id = connection.nextId("journal_pages");
        connection.insert("journal_pages", doc("page_id", id, "section_id", sectionId, "page_number", pageNumber, "content", ""));
        return new JournalPage(id, sectionId, pageNumber, "");
    }

    private static void requireOwnedSection(DocumentStore connection, String characterKey,
                                            long sectionId) {
        if (!connection.exists("journal_sections", doc("section_id", sectionId, "character_key", characterKey)))
            throw new IllegalStateException("Journal section no longer exists.");
    }

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Section name cannot be blank.");
        String normalized = title.trim();
        if (normalized.length() > MAX_SECTION_TITLE)
            throw new IllegalArgumentException("Section names cannot exceed " + MAX_SECTION_TITLE + " characters.");
        return normalized;
    }

    private static void requireCharacter(String characterKey) {
        if (characterKey == null || characterKey.isBlank())
            throw new IllegalArgumentException("characterKey cannot be blank.");
    }
}
