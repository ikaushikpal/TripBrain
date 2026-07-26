-- MAIN TABLE
CREATE TABLE IF NOT EXISTS country (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    iso2 TEXT NOT NULL UNIQUE,
    iso3 TEXT NOT NULL,
    currency TEXT NOT NULL,
    flag TEXT,
    timezone TEXT
)^^^

-- ALIAS TABLE
CREATE TABLE IF NOT EXISTS country_alias (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    alias TEXT NOT NULL COLLATE NOCASE UNIQUE,
    country_id INTEGER NOT NULL,
    FOREIGN KEY (country_id) REFERENCES country(id) ON DELETE CASCADE
)^^^

CREATE INDEX IF NOT EXISTS idx_country_alias ON country_alias(alias)^^^

-- FTS TABLE
CREATE VIRTUAL TABLE IF NOT EXISTS country_fts USING fts5(
    name,
    iso2,
    iso3,
    currency,
    content='country',
    content_rowid='id'
)^^^

-- TRIGGERS
CREATE TRIGGER IF NOT EXISTS country_ai
AFTER INSERT ON country
BEGIN
    INSERT INTO country_fts(rowid, name, iso2, iso3, currency)
    VALUES (new.id, new.name, new.iso2, new.iso3, new.currency);
END^^^

CREATE TRIGGER IF NOT EXISTS country_ad
AFTER DELETE ON country
BEGIN
    INSERT INTO country_fts(country_fts, rowid, name, iso2, iso3, currency)
    VALUES ('delete', old.id, old.name, old.iso2, old.iso3, old.currency);
END^^^

CREATE TRIGGER IF NOT EXISTS country_au
AFTER UPDATE ON country
BEGIN
    INSERT INTO country_fts(country_fts, rowid, name, iso2, iso3, currency)
    VALUES ('delete', old.id, old.name, old.iso2, old.iso3, old.currency);
    INSERT INTO country_fts(rowid, name, iso2, iso3, currency)
    VALUES (new.id, new.name, new.iso2, new.iso3, new.currency);
END^^^