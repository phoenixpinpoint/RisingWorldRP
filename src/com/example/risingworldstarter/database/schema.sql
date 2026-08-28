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
