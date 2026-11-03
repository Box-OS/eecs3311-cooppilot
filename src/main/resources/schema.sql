CREATE TABLE IF NOT EXISTS vault_entries (
    id    TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    kind  TEXT NOT NULL,
    use   TEXT NOT NULL,
    date  TEXT NOT NULL DEFAULT '',
    tags  TEXT NOT NULL DEFAULT '',
    body  TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS resumes (
    id     TEXT PRIMARY KEY,
    name   TEXT NOT NULL,
    family TEXT NOT NULL DEFAULT '',
    kind   TEXT NOT NULL,
    blocks TEXT NOT NULL DEFAULT '[]',
    latex  TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS postings (
    id       TEXT PRIMARY KEY,
    company  TEXT NOT NULL DEFAULT '',
    role     TEXT NOT NULL DEFAULT '',
    raw_text TEXT NOT NULL,
    term     TEXT NOT NULL DEFAULT '',
    deadline TEXT,
    analysis TEXT
);
