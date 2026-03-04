import Database from 'better-sqlite3'
import { getDb } from './DatabaseManager'
import { Dienst, DienstArt, DienstStatus } from '../../shared/types'

function rowToDienst(row: Record<string, unknown>): Dienst {
  return {
    id: row.id as number,
    datum: row.datum as string,
    art: row.art as DienstArt,
    personId: row.person_id as number | null,
    personName: row.person_name as string | null,
    status: row.status as DienstStatus,
    bemerkung: row.bemerkung as string | null
  }
}

export function getDiensteForDienstplan(dienstplanId: number): Dienst[] {
  const rows = getDb()
    .prepare('SELECT * FROM dienst WHERE dienstplan_id = ? ORDER BY datum, art')
    .all(dienstplanId)
  return (rows as Record<string, unknown>[]).map(rowToDienst)
}

export function createDienst(dienst: Dienst, dienstplanId: number, conn?: Database.Database): number {
  const database = conn ?? getDb()
  const result = database.prepare(`
    INSERT INTO dienst (dienstplan_id, datum, art, person_id, person_name, status, bemerkung)
    VALUES (?, ?, ?, ?, ?, ?, ?)
  `).run(
    dienstplanId,
    dienst.datum,
    dienst.art,
    dienst.personId ?? null,
    dienst.personName ?? null,
    dienst.status,
    dienst.bemerkung ?? null
  )
  return result.lastInsertRowid as number
}

export function updateDienst(dienst: Dienst): void {
  getDb().prepare(`
    UPDATE dienst
    SET person_id = ?, person_name = ?, status = ?, bemerkung = ?
    WHERE id = ?
  `).run(
    dienst.personId ?? null,
    dienst.personName ?? null,
    dienst.status,
    dienst.bemerkung ?? null,
    dienst.id
  )
}

export function deleteDiensteForDienstplan(dienstplanId: number, conn?: Database.Database): void {
  const database = conn ?? getDb()
  database.prepare('DELETE FROM dienst WHERE dienstplan_id = ?').run(dienstplanId)
}

export function countDiensteByStatus(dienstplanId: number): Record<DienstStatus, number> {
  const rows = getDb()
    .prepare('SELECT status, COUNT(*) as cnt FROM dienst WHERE dienstplan_id = ? GROUP BY status')
    .all(dienstplanId) as { status: DienstStatus; cnt: number }[]
  const result = {} as Record<DienstStatus, number>
  for (const row of rows) {
    result[row.status] = row.cnt
  }
  return result
}
