# User store

`/userstore` opens character-owned marketplace listings. A player lists part of
their equipped stack with `/userstore sell <price> [quantity]`; the item is
removed immediately and held in escrow. Buyers purchase an entire listing and
the price is transferred atomically from buyer to seller. Sellers can cancel
their own listings from the dialog to reclaim the escrowed items.

While any user listing exists for an item type, that item is shown as out of
stock in the configured normal store. Listings are stored in `civiccore.db`.
