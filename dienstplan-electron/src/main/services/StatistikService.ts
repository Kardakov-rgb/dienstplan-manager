import { getDb } from '../database/DatabaseManager'
import {
  GesamtStatistik, PersonDienstStatistik, OffenerDienst, Konflikt,
  DienstArt, DienstStatus, FairnessScore
} from '../../shared/types'
import { getAllFairnessScores } from '../database/FairnessHistorieDAO'

export function berechneGesamtStatistik(monatVon: string, monatBis: string): GesamtStatistik {
  const db = getDb()

  // Overall counts
  const totals = db.prepare(`
    SELECT
      COUNT(*) as gesamt,
      SUM(CASE WHEN person_id IS NOT NULL AND status != 'ABGESAGT' THEN 1 ELSE 0 END) as zugewiesen,
      SUM(CASE WHEN person_id IS NULL OR status = 'ABGESAGT' THEN 1 ELSE 0 END) as offen
    FROM dienst d
    JOIN dienstplan dp ON d.dienstplan_id = dp.id
    WHERE dp.monat_jahr >= ? AND dp.monat_jahr <= ?
  `).get(monatVon, monatBis) as { gesamt: number; zugewiesen: number; offen: number }

  const zuweisungsgrad = totals.gesamt > 0 ? totals.zugewiesen / totals.gesamt : 0

  // Per-person statistics
  const personRows = db.prepare(`
    SELECT
      p.id as person_id,
      p.name as person_name,
      p.anzahl_dienste as soll_dienste,
      COUNT(d.id) as ist_dienste,
      SUM(CASE WHEN d.art = 'DIENST_24H' THEN 1 ELSE 0 END) as dienste_24h,
      SUM(CASE WHEN d.art = 'VISTEN' THEN 1 ELSE 0 END) as dienste_visten,
      SUM(CASE WHEN d.art = 'DAVINCI' THEN 1 ELSE 0 END) as dienste_davinci
    FROM person p
    LEFT JOIN dienst d ON d.person_id = p.id
    LEFT JOIN dienstplan dp ON d.dienstplan_id = dp.id AND dp.monat_jahr >= ? AND dp.monat_jahr <= ?
    GROUP BY p.id
    ORDER BY p.name
  `).all(monatVon, monatBis) as {
    person_id: number; person_name: string; soll_dienste: number
    ist_dienste: number; dienste_24h: number; dienste_visten: number; dienste_davinci: number
  }[]

  const personStatistiken: PersonDienstStatistik[] = personRows.map((r) => ({
    personId: r.person_id,
    personName: r.person_name,
    sollDienste: r.soll_dienste,
    istDienste: r.ist_dienste || 0,
    dienste24h: r.dienste_24h || 0,
    diensteVisten: r.dienste_visten || 0,
    diensteDAVinci: r.dienste_davinci || 0
  }))

  // Open shifts
  const offeneRows = db.prepare(`
    SELECT d.datum, d.art, dp.name as dp_name
    FROM dienst d
    JOIN dienstplan dp ON d.dienstplan_id = dp.id
    WHERE (d.person_id IS NULL OR d.status = 'ABGESAGT')
      AND dp.monat_jahr >= ? AND dp.monat_jahr <= ?
    ORDER BY d.datum, d.art
  `).all(monatVon, monatBis) as { datum: string; art: string; dp_name: string }[]

  const offeneDiensteListe: OffenerDienst[] = offeneRows.map((r) => ({
    datum: r.datum,
    dienstArt: r.art as DienstArt,
    dienstplanName: r.dp_name
  }))

  // Conflicts (same person, same date, multiple shifts)
  const konfliktRows = db.prepare(`
    SELECT d.person_name, d.datum, GROUP_CONCAT(d.art, ', ') as dienste_arten, COUNT(*) as cnt
    FROM dienst d
    JOIN dienstplan dp ON d.dienstplan_id = dp.id
    WHERE d.person_id IS NOT NULL
      AND d.art != 'VISTEN'
      AND dp.monat_jahr >= ? AND dp.monat_jahr <= ?
    GROUP BY d.person_id, d.datum
    HAVING cnt > 1
  `).all(monatVon, monatBis) as { person_name: string; datum: string; dienste_arten: string }[]

  const konflikte: Konflikt[] = konfliktRows.map((r) => ({
    personName: r.person_name,
    datum: r.datum,
    dienste: r.dienste_arten.split(', ')
  }))

  // Wish fulfillment quote from fairness history
  const wunschRow = db.prepare(`
    SELECT SUM(anzahl_wuensche) as gesamt, SUM(erfuellte_wuensche) as erfuellt
    FROM fairness_historie
    WHERE monat_jahr >= ? AND monat_jahr <= ?
  `).get(monatVon, monatBis) as { gesamt: number; erfuellt: number }

  const wunschErfuellungsQuote = wunschRow?.gesamt > 0
    ? wunschRow.erfuellt / wunschRow.gesamt
    : 0

  return {
    gesamtDienste: totals.gesamt || 0,
    zugewieseneDienste: totals.zugewiesen || 0,
    offeneDienste: totals.offen || 0,
    zuweisungsgrad,
    wunschErfuellungsQuote,
    personStatistiken,
    offeneDiensteListe,
    konflikte
  }
}

export function getFairnessScores(): FairnessScore[] {
  return getAllFairnessScores()
}
