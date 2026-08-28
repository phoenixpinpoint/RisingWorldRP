# Groups and clans

Clans, roles, memberships, and invitations are stored in the world-scoped
`civiccore.db`. The canonical tables are in
[`../database/schema.sql`](../database/schema.sql). Existing
`groups.properties` data is imported once and retained only as a legacy backup.

Owners control the clan and appoint managers. Managers can invite or remove
ordinary members and manage clan land. All members can access clan-owned chunks,
which use the stable claim owner identity `group:<group-id>`.

Commands include `/clan create`, `info`, `invite`, `accept`, `leave`, `kick`,
`promote`, `demote`, `claim`, `unclaim`, and `disband`. `/group` is an alias.
