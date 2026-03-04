import { getDb } from './DatabaseManager'
import { Dienstplan, DienstplanStatus } from '../../shared/types'
import { getDiensteForDienstplan, createDienst, deleteDiensteForDienstplan } from './DienstDAO'

function rowToDienstplan(row: Record<string, unknown>): Dienstplan {
  const dp: Dienstplan = {
    id: row.id as number,
    name: row.name as string,
    monat: row.monat_jahr as string,
    erstelltAm: row.erstellt_am as string,
    letztesUpdate: row.letztes_update as string,
    status: row.status as DienstplanStatus,
    dienste: [],
    bemerkung: row.bemerkung as string | null
  }
  dp.dienste = getDiensteForDienstplan(dp.id!)
  return dp
}

export function getAllDienstplaene(): Dienstplan[] {
  const rows = getDb()
    .prepare('SELECT * FROM dienstplan ORDER BY monat_jahr DESC, name')
    .all()
  return (rows as Record<string, unknown>[]).map(rowToDienstplan)
}

export function getDienstplanById(id: number): Dienstplan | null {
  const row = getDb().prepare('SELECT * FROM dienstplan WHERE id = ?').get(id)
  return row ? rowToDienstplan(row as Record<string, unknown>) : null
}

export function getDienstplaeneForMonat(monat: string): Dienstplan[] {
  const rows = getDb()
    .prepare('SELECT * FROM dienstplan WHERE monat_jahr = ? ORDER BY name')
    .all(monat)
  return (rows as Record<string, unknown>[]).map(rowToDienstplan)
}

export function saveDienstplan(dienstplan: Dienstplan): Dienstplan {
  const db = getDb()

  const doSave = db.transaction(() => {
    if (dienstplan.id) {
      // Update
      db.prepare(`
        UPDATE dienstplan
        SET name = ?, monat_jahr = ?, letztes_update = date('now'), status = ?, bemerkung = ?
        WHERE id = ?
      `).run(
        dienstplan.name,
        dienstplan.monat,
        dienstplan.status,
        dienstplan.bemerkung ?? null,
        dienstplan.id
      )
      // Replace all dienste
      deleteDiensteForDienstplan(dienstplan.id, db)
      for (const dienst of dienstplan.dienste) {
        const newId = createDienst(dienst, dienstplan.id, db)
        dienst.id = newId
      }
      return getDienstplanById(dienstplan.id)!
    } else {
      // Insert
      const result = db.prepare(`
        INSERT INTO dienstplan (name, monat_jahr, status, bemerkung)
        VALUES (?, ?, ?, ?)
      `).run(
        dienstplan.name,
        dienstplan.monat,
        dienstplan.status,
        dienstplan.bemerkung ?? null
      )
      const newId = result.lastInsertRowid as number
      for (const dienst of dienstplan.dienste) {
        createDienst(dienst, newId, db)
      }
      return getDienstplanById(newId)!
    }
  })

  return doSave()
}

export function deleteDienstplan(id: number): void {
  getDb().prepare('DELETE FROM dienstplan WHERE id = ?').run(id)
}

export function countDienstplaene(): number {
  const row = getDb().prepare('SELECT COUNT(*) as cnt FROM dienstplan').get() as { cnt: number }
  return row.cnt
}
