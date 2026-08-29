# Groups and clans

Clans, roles, memberships, and invitations are stored in the world-scoped
`civiccore.db`. The canonical tables are in
[`../database/schema.sql`](../database/schema.sql). Existing
`groups.properties` data is imported once and retained only as a legacy backup.

Owners control the clan and appoint managers. Managers can invite or remove
ordinary members and manage clan land. All members can access clan-owned chunks,
which use the stable claim owner identity `group:<group-id>`.

Commands include `/clan create`, `info`, `invite`, `accept`, `leave`, `kick`,
`promote`, `demote`, `balance`, `deposit`, `withdraw`, `claim`, `unclaim`, and
`disband`. `/group` is an alias. Running `/clan` or `/group` without arguments
opens the role-aware clan management dialog; the command forms remain available
for chat-based administration.

The dialog allows unaffiliated characters to create a clan or accept an
invitation. Members can inspect the roster and leave. Managers can invite and
remove ordinary members, manage treasury funds, and claim or release the current
chunk. Owners can additionally promote or demote managers, remove managers, and
disband the clan. Destructive actions require confirmation.

Each clan has a treasury account under its stable `group:<group-id>` identity.
Owners and managers can view its balance, deposit funds from their active
character account, and withdraw funds back to that account. Each transfer is a
single database transaction. A funded clan must empty its treasury before it
can be disbanded.
