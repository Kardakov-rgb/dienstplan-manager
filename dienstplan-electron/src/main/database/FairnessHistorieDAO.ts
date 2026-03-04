import { getDb } from './DatabaseManager'
import { FairnessHistorie, FairnessScore } from '../../shared/types'

function rowToHistorie(row: Record<string, unknown>): FairnessHistorie {
  return {
    id: row.id as number,
    personId: row.person_id as number,
    personName: row.person_name as string,
    monatJahr: row.monat_jahr as string,
    anzahlWuensche: row.anzahl_wuensche as number,
    erfuellteWuensche: row.erfuellte_wuensche as number
  }
}

export function getHistorieForPerson(personId: number): FairnessHistorie[] {
  const rows = getDb()
    .prepare('SELECT * FROM fairness_historie WHERE person_id = ? ORDER BY monat_jahr DESC')
    .all(personId)
  return (rows as Record<string, unknown>[]).map(rowToHistorie)
}

export function saveHistorie(historie: Omit<FairnessHistorie, 'id'>): void {
  // Upsert: replace existing entry for same person + month
  getDb().prepare(`
    INSERT OR REPLACE INTO fairness_historie (person_id, person_name, monat_jahr, anzahl_wuensche, erfuellte_wuensche)
    VALUES (?, ?, ?, ?, ?)
  `).run(
    historie.personId,
    historie.personName,
    historie.monatJahr,
    historie.anzahlWuensche,
    historie.erfuellteWuensche
  )
}

/**
 * Calculate aggregated FairnessScores for all persons based on history up to (but not including) the given month.
 */
export function getFairnessScoresForMonth(monat: string): Map<number, FairnessScore> {
  const rows = getDb().prepare(`
    SELECT person_id, person_name,
           COUNT(*) as anzahl_monate,
           SUM(anzahl_wuensche) as gesamt_wuensche,
           SUM(erfuellte_wuensche) as erfuellte_wuensche
    FROM fairness_historie
    WHERE monat_jahr < ?
    GROUP BY person_id
  `).all(monat) as {
    person_id: number
    person_name: string
    anzahl_monate: number
    gesamt_wuensche: number
    erfuellte_wuensche: number
  }[]

  const map = new Map<number, FairnessScore>()
  for (const row of rows) {
    const quote = row.gesamt_wuensche > 0
      ? row.erfuellte_wuensche / row.gesamt_wuensche
      : 1.0
    map.set(row.person_id, {
      personId: row.person_id,
      personName: row.person_name,
      anzahlMonate: row.anzahl_monate,
      gesamtWuensche: row.gesamt_wuensche,
      erfuellteWuensche: row.erfuellte_wuensche,
      durchschnittlicheErfuellung: quote
    })
  }
  return map
}

export function getAllFairnessScores(): FairnessScore[] {
  const rows = getDb().prepare(`
    SELECT person_id, person_name,
           COUNT(*) as anzahl_monate,
           SUM(anzahl_wuensche) as gesamt_wuensche,
           SUM(erfuellte_wuensche) as erfuellte_wuensche
    FROM fairness_historie
    GROUP BY person_id
    ORDER BY person_name
  `).all() as {
    person_id: number
    person_name: string
    anzahl_monate: number
    gesamt_wuensche: number
    erfuellte_wuensche: number
  }[]

  return rows.map((row) => ({
    personId: row.person_id,
    personName: row.person_name,
    anzahlMonate: row.anzahl_monate,
    gesamtWuensche: row.gesamt_wuensche,
    erfuellteWuensche: row.erfuellte_wuensche,
    durchschnittlicheErfuellung: row.gesamt_wuensche > 0
      ? row.erfuellte_wuensche / row.gesamt_wuensche
      : 1.0
  }))
}
