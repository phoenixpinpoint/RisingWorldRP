# MongoDB Atlas storage

CivicCore uses the official MongoDB Java sync driver for all live mutable state:
balances, accounts, characters (including binary inventories and clothes), claims,
claim administrators, chests, clans, invitations, journals, custom spawns, and
marketplace listings. The driver is bundled in `CivicCore.jar`.

## Server setup

1. Create an Atlas cluster, a database user with `readWrite` access to the
   `civiccore` database, and a network access entry for the game server's IP.
2. Set `CIVICCORE_MONGODB_URI` in the game server process's environment to the
   Atlas connection string, for example:
   `mongodb+srv://USER:PASSWORD@CLUSTER.mongodb.net/?retryWrites=true&w=majority`.
   Percent-encode special characters in the username/password. Atlas SRV
   connections enable TLS by default; retain certificate validation.
3. Optionally set `CIVICCORE_MONGODB_DATABASE` (default: `civiccore`). The database
   name in the URI does not override this setting.
4. Alternatively, copy [`mongodb.properties.example`](../../../../../config/mongodb.properties.example)
   to `Plugins/CivicCore/mongodb.properties` and set `uri` and `database` there.
   Environment variables take precedence. This file is not installed by
   `installConfig`, so that task cannot overwrite server credentials.
5. Keep the world's existing `CivicCore/plugin.properties` opt-in file and start
   the updated plugin. Missing configuration, failed connectivity, or lack of
   transaction support fails startup; there is no SQLite fallback.

Credentials belong in the server environment or plugin configuration, never in
source control or the world save. See the official
[Atlas Java connection guide](https://www.mongodb.com/docs/drivers/java/sync/current/get-started/)
and [transaction documentation](https://www.mongodb.com/docs/drivers/java/sync/current/crud/transactions/).

## World identity and backups

On first configured startup, CivicCore writes a random, persistent ID to
`Worlds/<world>/CivicCore/mongodb-world-id.txt`. Every collection query and unique
index includes this world ID. Keep that file when moving or renaming a world to
retain access to its Atlas data. An independent copy of a world needs its own ID;
copies retaining the same ID deliberately access the same remote state.

Back up Atlas as well as world files. After conversion, the local `civiccore.db`
is a historical backup and does not receive new writes. Copying a world save
alone no longer backs up current characters, money, or claims.

## Existing SQLite worlds

Stop the old server before the upgrade and back up its world directory, including
`civiccore.db` and any SQLite WAL files. On startup, `SqliteMigrator` opens the
source read-only and imports all known tables into the matching empty Atlas world.
It preserves IDs, binary data, metadata, and auto-increment sequence high-water
marks. ASCII case-insensitive names retain their old lookup behavior.

The import and its completion marker commit in one MongoDB transaction. A failed
import rolls back and prevents plugin startup; correct the problem and restart to
retry. Importing into a nonempty world without a completion marker is refused,
so existing Atlas state cannot be silently merged or overwritten. The source is
never removed or modified. Later starts skip completed imports, even if the
old SQLite file remains. Existing flat-file migration markers are preserved too.

The import must fit the cluster's transaction lifetime and MongoDB's 16 MiB
per-document limit. An unusually large world or inventory may need a dedicated
migration procedure before enabling it; failures leave the original data intact.
[`schema.sql`](schema.sql) is the historical SQLite schema retained for migration
test fixtures, not the active database schema.

## Implementation

Services use `Database` and `DocumentStore`, with BSON filters rather than SQL.
`MongoSchema` defines world-scoped unique indexes. `MongoDatabase` owns one pooled
client, snapshot reads, and majority-committed transactions. A per-world lock
document serializes writers across clients, preserving checks involving multiple
documents (such as spawn limits and clan permissions) as well as money transfers.
The driver retries transient transaction conflicts. Transaction callbacks must
therefore contain only database work or repeatable reads, not game-side effects.
Related-record deletion is explicit in service transactions.

Calls remain synchronous, as with the old backend. Atlas network latency now
affects game callbacks; place the cluster close to the game server. Concurrent
server processes accessing the same world are transaction-safe at the database
layer, but this does not coordinate game-engine state or active player sessions.
