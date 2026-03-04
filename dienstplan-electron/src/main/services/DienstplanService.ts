import {
  Dienstplan, GenerierungsResult, FairnessScore, MonatsWunsch, WunschStatistik
} from '../../shared/types'
import { getAllPersonen } from '../database/PersonDAO'
import { saveDienstplan, getDienstplaeneForMonat } from '../database/DienstplanDAO'
import { getWuenscheForMonat } from '../database/MonatsWunschDAO'
import { getFairnessScoresForMonth, saveHistorie } from '../database/FairnessHistorieDAO'
import { DienstplanGenerator } from '../algorithm/DienstplanGenerator'
import log from 'electron-log'

export async function generiereDienstplan(
  monat: string,
  progressCallback?: (p: number) => void
): Promise<GenerierungsResult> {
  log.info(`Generating Dienstplan for ${monat}`)

  const personen = getAllPersonen()
  if (personen.length === 0) {
    return { dienstplan: null, warnungen: ['Keine Personen vorhanden.'], erfolgreich: false, wunschStatistiken: {} }
  }

  const wuensche: MonatsWunsch[] = getWuenscheForMonat(monat)
  const fairnessMap: Map<number, FairnessScore> = getFairnessScoresForMonth(monat)

  const generator = new DienstplanGenerator(personen, monat, wuensche, fairnessMap)
  if (progressCallback) generator.setProgressCallback(progressCallback)

  const result = generator.generiereDienstplan()

  // Persist fairness statistics after generation
  if (result.dienstplan) {
    await persistFairnessHistorie(result.wunschStatistiken, monat)
  }

  return result
}

async function persistFairnessHistorie(
  stats: Record<number, WunschStatistik>,
  monat: string
): Promise<void> {
  for (const stat of Object.values(stats)) {
    const soft = stat.anzahlFreiwuensche + stat.anzahlDienstwuensche
    const erfuellt = stat.erfuellteFreiwuensche + stat.erfuellteDienstwuensche
    if (soft > 0) {
      saveHistorie({
        personId: stat.personId,
        personName: stat.personName,
        monatJahr: monat,
        anzahlWuensche: soft,
        erfuellteWuensche: erfuellt
      })
    }
  }
}
