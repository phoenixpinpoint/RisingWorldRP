# Journal

The journal is character-scoped and stored in `civiccore.db`. `/journal` opens
an editor where players can write and save notes, navigate ordered pages, add
pages, and create named sections. The active page is automatically saved when
the player navigates, switches sections, or closes the journal.

The canonical `journal_sections` and `journal_pages` definitions are maintained
in [`../database/schema.sql`](../database/schema.sql).
