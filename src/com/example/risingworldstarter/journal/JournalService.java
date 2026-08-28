package com.example.risingworldstarter.journal;

import com.example.risingworldstarter.database.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
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
        database.transaction(connection -> {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT 1 FROM journal_sections WHERE character_key=? LIMIT 1")) {
                query.setString(1, characterKey);
                try (ResultSet rows = query.executeQuery()) {
                    if (!rows.next()) createSection(connection, characterKey, "Notes");
                }
            }
            return null;
        });
        return getSections(characterKey);
    }

    public List<JournalSection> getSections(String characterKey) {
        requireCharacter(characterKey);
        return database.read(connection -> {
            List<JournalSection> result = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT section_id,title,section_order FROM journal_sections "
                            + "WHERE character_key=? ORDER BY section_order")) {
                query.setString(1, characterKey);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) result.add(new JournalSection(
                            rows.getLong(1), rows.getString(2), rows.getInt(3)));
                }
            }
            return List.copyOf(result);
        });
    }

    public JournalSection createSection(String characterKey, String title) {
        requireCharacter(characterKey);
        String normalizedTitle = requireTitle(title);
        return database.transaction(connection -> createSection(connection, characterKey, normalizedTitle));
    }

    public List<JournalPage> getPages(String characterKey, long sectionId) {
        return database.read(connection -> {
            requireOwnedSection(connection, characterKey, sectionId);
            List<JournalPage> result = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT page_id,page_number,content FROM journal_pages "
                            + "WHERE section_id=? ORDER BY page_number")) {
                query.setLong(1, sectionId);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) result.add(new JournalPage(rows.getLong(1), sectionId,
                            rows.getInt(2), rows.getString(3)));
                }
            }
            return List.copyOf(result);
        });
    }

    public JournalPage createPage(String characterKey, long sectionId) {
        return database.transaction(connection -> {
            requireOwnedSection(connection, characterKey, sectionId);
            int pageNumber;
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT COALESCE(MAX(page_number),0)+1 FROM journal_pages WHERE section_id=?")) {
                query.setLong(1, sectionId);
                try (ResultSet row = query.executeQuery()) { pageNumber = row.getInt(1); }
            }
            return insertPage(connection, sectionId, pageNumber);
        });
    }

    public void savePage(String characterKey, long pageId, String content) {
        String normalized = content == null ? "" : content;
        if (normalized.length() > MAX_PAGE_CHARACTERS)
            throw new IllegalArgumentException("Journal pages cannot exceed " + MAX_PAGE_CHARACTERS + " characters.");
        database.write(connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE journal_pages SET content=? WHERE page_id=? AND section_id IN "
                            + "(SELECT section_id FROM journal_sections WHERE character_key=?)")) {
                update.setString(1, normalized); update.setLong(2, pageId); update.setString(3, characterKey);
                if (update.executeUpdate() == 0) throw new IllegalStateException("Journal page no longer exists.");
            }
            return null;
        });
    }

    public int deleteJournal(String characterKey) {
        requireCharacter(characterKey);
        return database.transaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM journal_sections WHERE character_key=?")) {
                delete.setString(1, characterKey);
                return delete.executeUpdate();
            }
        });
    }

    private static JournalSection createSection(java.sql.Connection connection, String characterKey,
                                                 String title) throws java.sql.SQLException {
        int order;
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT COALESCE(MAX(section_order),0)+1 FROM journal_sections WHERE character_key=?")) {
            query.setString(1, characterKey);
            try (ResultSet row = query.executeQuery()) { order = row.getInt(1); }
        }
        long id;
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO journal_sections(character_key,title,section_order) VALUES(?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, characterKey); insert.setString(2, title); insert.setInt(3, order);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) throw new IllegalStateException("Could not create journal section.");
                id = keys.getLong(1);
            }
        }
        insertPage(connection, id, 1);
        return new JournalSection(id, title, order);
    }

    private static JournalPage insertPage(java.sql.Connection connection, long sectionId,
                                          int pageNumber) throws java.sql.SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO journal_pages(section_id,page_number,content) VALUES(?,?,'')",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setLong(1, sectionId); insert.setInt(2, pageNumber); insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) throw new IllegalStateException("Could not create journal page.");
                return new JournalPage(keys.getLong(1), sectionId, pageNumber, "");
            }
        }
    }

    private static void requireOwnedSection(java.sql.Connection connection, String characterKey,
                                            long sectionId) throws java.sql.SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM journal_sections WHERE section_id=? AND character_key=?")) {
            query.setLong(1, sectionId); query.setString(2, characterKey);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new IllegalStateException("Journal section no longer exists.");
            }
        }
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
