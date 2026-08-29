-- CivicCore SQLite schema, version 1.
-- Mutable world state belongs here; operator configuration remains file-based.

CREATE TABLE IF NOT EXISTS metadata (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS balances (
    account_id TEXT PRIMARY KEY,
    balance INTEGER NOT NULL CHECK (balance >= 0)
);

CREATE TABLE IF NOT EXISTS claims (
    chunk_x INTEGER NOT NULL,
    chunk_z INTEGER NOT NULL,
    owner_id TEXT NOT NULL,
    owner_name TEXT NOT NULL,
    PRIMARY KEY (chunk_x, chunk_z)
);

CREATE INDEX IF NOT EXISTS claims_owner_idx ON claims (owner_id);

CREATE TABLE IF NOT EXISTS claim_admins (
    player_uid TEXT PRIMARY KEY,
    player_name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS chests (
    global_id INTEGER NOT NULL,
    chunk_x INTEGER NOT NULL,
    chunk_y INTEGER NOT NULL,
    chunk_z INTEGER NOT NULL,
    owner_id TEXT NOT NULL,
    owner_name TEXT NOT NULL,
    locked INTEGER NOT NULL CHECK (locked IN (0, 1)),
    PRIMARY KEY (global_id, chunk_x, chunk_y, chunk_z)
);

CREATE TABLE IF NOT EXISTS accounts (
    account_uid TEXT PRIMARY KEY,
    profile_name TEXT NOT NULL,
    profile_state TEXT
);

CREATE TABLE IF NOT EXISTS characters (
    character_id TEXT PRIMARY KEY,
    account_uid TEXT NOT NULL REFERENCES accounts (account_uid) ON DELETE CASCADE,
    slot INTEGER NOT NULL CHECK (slot BETWEEN 1 AND 4),
    name TEXT NOT NULL,
    state TEXT,
    inventory BLOB,
    clothes BLOB,
    UNIQUE (account_uid, slot)
);

CREATE TABLE IF NOT EXISTS groups (
    group_id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE COLLATE NOCASE
);

CREATE TABLE IF NOT EXISTS group_members (
    character_key TEXT PRIMARY KEY,
    group_id TEXT NOT NULL REFERENCES groups (group_id) ON DELETE CASCADE,
    character_name TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('OWNER', 'MANAGER', 'MEMBER'))
);

CREATE INDEX IF NOT EXISTS group_members_group_idx ON group_members (group_id);

CREATE TABLE IF NOT EXISTS group_invitations (
    character_key TEXT PRIMARY KEY,
    group_id TEXT NOT NULL REFERENCES groups (group_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS journal_sections (
    section_id INTEGER PRIMARY KEY AUTOINCREMENT,
    character_key TEXT NOT NULL,
    title TEXT NOT NULL,
    section_order INTEGER NOT NULL,
    UNIQUE (character_key, section_order)
);

CREATE INDEX IF NOT EXISTS journal_sections_character_idx
    ON journal_sections (character_key, section_order);

CREATE TABLE IF NOT EXISTS journal_pages (
    page_id INTEGER PRIMARY KEY AUTOINCREMENT,
    section_id INTEGER NOT NULL REFERENCES journal_sections (section_id) ON DELETE CASCADE,
    page_number INTEGER NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    UNIQUE (section_id, page_number)
);

CREATE TABLE IF NOT EXISTS user_store_listings (
    listing_id INTEGER PRIMARY KEY AUTOINCREMENT,
    seller_key TEXT NOT NULL,
    seller_name TEXT NOT NULL,
    item_type INTEGER NOT NULL,
    item_variant INTEGER NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    price INTEGER NOT NULL CHECK (price > 0),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS user_store_item_idx ON user_store_listings (item_type);
CREATE INDEX IF NOT EXISTS user_store_seller_idx ON user_store_listings (seller_key);
