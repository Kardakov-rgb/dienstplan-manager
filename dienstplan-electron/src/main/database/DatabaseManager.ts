import Database from 'better-sqlite3'
import { app } from 'electron'
import path from 'path'
import fs from 'fs'
import log from 'electron-log'

let db: Database.Database | null = null

export function getDb(): Database.Database {
  if (!db) {
    throw new Error('Database not initialized. Call initDatabase() first.')
  }
  return db
}

export function initDatabase(): void {
  const userDataPath = app.getPath('userData')
  const dbPath = path.join(userDataPath, 'dienstplan.db')

  log.info('Initializing database at:', dbPath)

  db = new Database(dbPath)
  db.pragma('journal_mode = WAL')
  db.pragma('foreign_keys = ON')
  db.pragma('synchronous = NORMAL')

  createSchema()
  log.info('Database initialized successfully')
}

function createSchema(): void {
  const database = getDb()

  database.exec(`
    CREATE TABLE IF NOT EXISTS person (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL UNIQUE,
      anzahl_dienste INTEGER NOT NULL DEFAULT 0,
      arbeits_tage TEXT NOT NULL DEFAULT '',
      verfuegbare_dienst_arten TEXT NOT NULL DEFAULT '',
      erstellt_am TEXT NOT NULL DEFAULT (date('now')),
      letztes_update TEXT NOT NULL DEFAULT (date('now'))
    );

    CREATE TABLE IF NOT EXISTS dienstplan (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      monat_jahr TEXT NOT NULL,
      erstellt_am TEXT NOT NULL DEFAULT (date('now')),
      letztes_update TEXT NOT NULL DEFAULT (date('now')),
      status TEXT NOT NULL DEFAULT 'ENTWURF',
      bemerkung TEXT
    );

    CREATE TABLE IF NOT EXISTS dienst (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      dienstplan_id INTEGER NOT NULL,
      datum TEXT NOT NULL,
      art TEXT NOT NULL,
      person_id INTEGER,
      person_name TEXT,
      status TEXT NOT NULL DEFAULT 'GEPLANT',
      bemerkung TEXT,
      FOREIGN KEY (dienstplan_id) REFERENCES dienstplan(id) ON DELETE CASCADE,
      FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE SET NULL
    );

    CREATE TABLE IF NOT EXISTS monats_wunsch (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      person_id INTEGER NOT NULL,
      person_name TEXT NOT NULL,
      datum TEXT NOT NULL,
      monat_jahr TEXT NOT NULL,
      typ TEXT NOT NULL,
      erfuellt INTEGER,
      bemerkung TEXT,
      FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS fairness_historie (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      person_id INTEGER NOT NULL,
      person_name TEXT NOT NULL,
      monat_jahr TEXT NOT NULL,
      anzahl_wuensche INTEGER NOT NULL DEFAULT 0,
      erfuellte_wuensche INTEGER NOT NULL DEFAULT 0,
      erstellt_am TEXT NOT NULL DEFAULT (date('now')),
      FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE CASCADE
    );

    CREATE INDEX IF NOT EXISTS idx_dienst_dienstplan ON dienst(dienstplan_id);
    CREATE INDEX IF NOT EXISTS idx_dienst_datum ON dienst(datum);
    CREATE INDEX IF NOT EXISTS idx_dienst_person ON dienst(person_id);
    CREATE INDEX IF NOT EXISTS idx_wunsch_person ON monats_wunsch(person_id);
    CREATE INDEX IF NOT EXISTS idx_wunsch_monat ON monats_wunsch(monat_jahr);
    CREATE INDEX IF NOT EXISTS idx_fairness_person ON fairness_historie(person_id);
  `)
}

export function closeDatabase(): void {
  if (db) {
    db.close()
    db = null
    log.info('Database closed')
  }
}

export function getDatabasePath(): string {
  const userDataPath = app.getPath('userData')
  return path.join(userDataPath, 'dienstplan.db')
}

export function getDatabaseSize(): number {
  const dbPath = getDatabasePath()
  try {
    const stat = fs.statSync(dbPath)
    return stat.size
  } catch {
    return 0
  }
}
