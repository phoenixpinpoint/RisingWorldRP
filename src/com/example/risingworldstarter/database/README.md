# Database

CivicCore stores mutable world state behind the `Database` interface. SQLite is
the initial backend through `SqliteDatabase`; repositories depend on the
interface and JDBC rather than SQLite-specific APIs so another database backend
can be introduced later.

The per-world database is `Worlds/<world>/CivicCore/civiccore.db`. SQLite uses
foreign keys, a busy timeout, and WAL journaling. Schema creation is idempotent.
The canonical table definitions are maintained in [`schema.sql`](schema.sql),
which is packaged into the plugin and executed during database initialization.

Database state includes balances, characters and inventory blobs, claims, claim
administrators, chest locks, clans, memberships, roles, invitations, character
journals, and user-store escrow listings. Operator-managed
configuration remains in `plugin.properties`, `economy.properties`, and
`marketplace.json`.
