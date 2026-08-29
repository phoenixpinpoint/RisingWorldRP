# Land claims

This package contains CivicCore's chunk ownership, claim-administrator, and
claim-backed chest ownership services.

## Components

- `ClaimService` creates, queries, migrates, and removes horizontal chunk claims.
- `Claim` identifies a claimed chunk's character owner.
- `ClaimedChunk` represents horizontal chunk coordinates.
- `ClaimAdminService` persists the claim-administrator whitelist.
- `ChestService` persists chest ownership and lock state.
- `ChestOwnership` identifies a chest owner and whether the chest is locked.

Claims, claim administrators, and chest locks are stored in the world-scoped
database. Other plugins can access claims through `CivicCore.getClaimService()`.

## Player commands

- `/claim` claims the current chunk.
- `/chunk` reports and visualizes the current chunk and owner.
- `/claims` lists personal claims and toggles a 21-by-21 chunk ownership overview
  centered on the active character's current chunk.
- `/unclaim` releases the current chunk.
- `/claimadmin add <online-player>` adds a claim administrator.
- `/claimadmin remove <online-player>` removes a claim administrator.
- `/claimadmin list` lists claim administrators.

In the `/claims` overview, boundaries are green when available, blue when owned
by the viewing character, purple when owned by that character's clan, and red
when unavailable. The overview uses one bounded database query when opened.

## Protection rules

Claimed chunks reject building, terrain changes, harvesting, and object changes
by other characters. Unclaimed chunks allow gathering natural resources but
must be claimed before building, planting, or changing terrain. Server and
claim administrators can use the session-only `ADMIN BYPASS` dashboard toggle
to work within another character's claim.

## Chest ownership

Storage placed in a claim belongs to that claim's character owner and begins
unlocked. Existing chests inherit the current claim owner when first accessed.
The owner can use `/chest status`, `/chest lock`, and `/chest unlock` while
looking at the storage object. Locked chests are restricted to their owner,
unless administrator bypass is active. State is removed from the database when
the chest is destroyed.
