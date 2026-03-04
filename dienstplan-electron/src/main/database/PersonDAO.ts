import { getDb } from './DatabaseManager'
import { Person, DienstArt, Wochentag } from '../../shared/types'

function rowToPerson(row: Record<string, unknown>): Person {
  return {
    id: row.id as number,
    name: row.name as string,
    anzahlDienste: row.anzahl_dienste as number,
    arbeitsTage: row.arbeits_tage
      ? (row.arbeits_tage as string).split(',').filter(Boolean).map((s) => s as Wochentag)
      : [],
    verfuegbareDienstArten: row.verfuegbare_dienst_arten
      ? (row.verfuegbare_dienst_arten as string).split(',').filter(Boolean).map((s) => s as DienstArt)
      : []
  }
}

export function getAllPersonen(): Person[] {
  const rows = getDb().prepare('SELECT * FROM person ORDER BY name').all()
  return (rows as Record<string, unknown>[]).map(rowToPerson)
}

export function getPersonById(id: number): Person | null {
  const row = getDb().prepare('SELECT * FROM person WHERE id = ?').get(id)
  return row ? rowToPerson(row as Record<string, unknown>) : null
}

export function createPerson(person: Omit<Person, 'id'>): Person {
  const stmt = getDb().prepare(`
    INSERT INTO person (name, anzahl_dienste, arbeits_tage, verfuegbare_dienst_arten)
    VALUES (?, ?, ?, ?)
  `)
  const result = stmt.run(
    person.name,
    person.anzahlDienste,
    person.arbeitsTage.join(','),
    person.verfuegbareDienstArten.join(',')
  )
  return getPersonById(result.lastInsertRowid as number)!
}

export function updatePerson(person: Person): Person {
  getDb().prepare(`
    UPDATE person
    SET name = ?, anzahl_dienste = ?, arbeits_tage = ?, verfuegbare_dienst_arten = ?,
        letztes_update = date('now')
    WHERE id = ?
  `).run(
    person.name,
    person.anzahlDienste,
    person.arbeitsTage.join(','),
    person.verfuegbareDienstArten.join(','),
    person.id
  )
  return getPersonById(person.id)!
}

export function deletePerson(id: number): void {
  getDb().prepare('DELETE FROM person WHERE id = ?').run(id)
}

export function countPersonen(): number {
  const row = getDb().prepare('SELECT COUNT(*) as cnt FROM person').get() as { cnt: number }
  return row.cnt
}
