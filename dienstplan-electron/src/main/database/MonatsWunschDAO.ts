import { getDb } from './DatabaseManager'
import { MonatsWunsch, WunschTyp } from '../../shared/types'

function rowToMonatsWunsch(row: Record<string, unknown>): MonatsWunsch {
  return {
    id: row.id as number,
    personId: row.person_id as number,
    personName: row.person_name as string,
    datum: row.datum as string,
    monat: row.monat_jahr as string,
    typ: row.typ as WunschTyp,
    erfuellt: row.erfuellt === null ? null : Boolean(row.erfuellt),
    bemerkung: row.bemerkung as string | null
  }
}

export function getWuenscheForMonat(monat: string): MonatsWunsch[] {
  const rows = getDb()
    .prepare('SELECT * FROM monats_wunsch WHERE monat_jahr = ? ORDER BY person_name, datum')
    .all(monat)
  return (rows as Record<string, unknown>[]).map(rowToMonatsWunsch)
}

export function getWuenscheForPersonAndMonat(personId: number, monat: string): MonatsWunsch[] {
  const rows = getDb()
    .prepare('SELECT * FROM monats_wunsch WHERE person_id = ? AND monat_jahr = ? ORDER BY datum')
    .all(personId, monat)
  return (rows as Record<string, unknown>[]).map(rowToMonatsWunsch)
}

export function getWuenscheForMonatGroupedByPerson(monat: string): Map<number, MonatsWunsch[]> {
  const all = getWuenscheForMonat(monat)
  const map = new Map<number, MonatsWunsch[]>()
  for (const w of all) {
    if (!map.has(w.personId)) map.set(w.personId, [])
    map.get(w.personId)!.push(w)
  }
  return map
}

export function createWunsch(wunsch: Omit<MonatsWunsch, 'id'>): MonatsWunsch {
  const result = getDb().prepare(`
    INSERT INTO monats_wunsch (person_id, person_name, datum, monat_jahr, typ, erfuellt, bemerkung)
    VALUES (?, ?, ?, ?, ?, ?, ?)
  `).run(
    wunsch.personId,
    wunsch.personName,
    wunsch.datum,
    wunsch.monat,
    wunsch.typ,
    wunsch.erfuellt === null || wunsch.erfuellt === undefined ? null : wunsch.erfuellt ? 1 : 0,
    wunsch.bemerkung ?? null
  )
  return { ...wunsch, id: result.lastInsertRowid as number }
}

export function createWuenscheBatch(wuensche: Omit<MonatsWunsch, 'id'>[]): MonatsWunsch[] {
  const db = getDb()
  const stmt = db.prepare(`
    INSERT INTO monats_wunsch (person_id, person_name, datum, monat_jahr, typ, erfuellt, bemerkung)
    VALUES (?, ?, ?, ?, ?, ?, ?)
  `)
  const doInsert = db.transaction((items: Omit<MonatsWunsch, 'id'>[]) => {
    return items.map((w) => {
      const result = stmt.run(
        w.personId, w.personName, w.datum, w.monat, w.typ,
        w.erfuellt === null || w.erfuellt === undefined ? null : w.erfuellt ? 1 : 0,
        w.bemerkung ?? null
      )
      return { ...w, id: result.lastInsertRowid as number } as MonatsWunsch
    })
  })
  return doInsert(wuensche)
}

export function updateWunschErfuellung(id: number, erfuellt: boolean): void {
  getDb()
    .prepare('UPDATE monats_wunsch SET erfuellt = ? WHERE id = ?')
    .run(erfuellt ? 1 : 0, id)
}

export function deleteWunsch(id: number): void {
  getDb().prepare('DELETE FROM monats_wunsch WHERE id = ?').run(id)
}

export function deleteWuenscheForPersonAndMonat(personId: number, monat: string): void {
  getDb()
    .prepare('DELETE FROM monats_wunsch WHERE person_id = ? AND monat_jahr = ?')
    .run(personId, monat)
}

export function hatUrlaubAm(personId: number, datum: string): boolean {
  const row = getDb().prepare(`
    SELECT 1 FROM monats_wunsch WHERE person_id = ? AND datum = ? AND typ = 'URLAUB' LIMIT 1
  `).get(personId, datum)
  return !!row
}
