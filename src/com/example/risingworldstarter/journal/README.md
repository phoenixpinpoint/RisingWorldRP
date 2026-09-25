# Journal

The journal is character-scoped and stored in MongoDB Atlas. `/journal` opens
an editor where players can write and save notes, navigate ordered pages, add
pages, and create named sections. The active page is automatically saved when
the player navigates, switches sections, or closes the journal.

The `journal_sections` and `journal_pages` collections use world-scoped unique
indexes defined in [`MongoSchema`](../database/MongoSchema.java). Deleting a
journal removes its sections and pages in one transaction.
