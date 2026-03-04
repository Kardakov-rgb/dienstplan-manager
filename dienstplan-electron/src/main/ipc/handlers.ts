import { ipcMain, dialog } from 'electron'
import log from 'electron-log'

import { getAllPersonen, createPerson, updatePerson, deletePerson } from '../database/PersonDAO'
import {
  getAllDienstplaene, getDienstplanById, getDienstplaeneForMonat,
  saveDienstplan, deleteDienstplan
} from '../database/DienstplanDAO'
import {
  getWuenscheForMonat, getWuenscheForPersonAndMonat,
  createWunsch, createWuenscheBatch, deleteWunsch, deleteWuenscheForPersonAndMonat
} from '../database/MonatsWunschDAO'
import { getFairnessScoresForMonth } from '../database/FairnessHistorieDAO'
import { generiereDienstplan } from '../services/DienstplanService'
import { berechneGesamtStatistik, getFairnessScores } from '../services/StatistikService'
import {
  exportDienstplanToExcel, exportStatistikToExcel,
  importWuenscheFromExcel
} from '../services/ExcelExporter'

export function registerIpcHandlers(): void {
  log.info('Registering IPC handlers')

  // ============================================================
  // Personen
  // ============================================================
  ipcMain.handle('persons:getAll', async () => {
    return getAllPersonen()
  })

  ipcMain.handle('persons:create', async (_event, person) => {
    return createPerson(person)
  })

  ipcMain.handle('persons:update', async (_event, person) => {
    return updatePerson(person)
  })

  ipcMain.handle('persons:delete', async (_event, id: number) => {
    deletePerson(id)
  })

  // ============================================================
  // Dienstpläne
  // ============================================================
  ipcMain.handle('dienstplaene:getAll', async () => {
    return getAllDienstplaene()
  })

  ipcMain.handle('dienstplaene:get', async (_event, id: number) => {
    return getDienstplanById(id)
  })

  ipcMain.handle('dienstplaene:forMonat', async (_event, monat: string) => {
    return getDienstplaeneForMonat(monat)
  })

  ipcMain.handle('dienstplaene:save', async (_event, dienstplan) => {
    return saveDienstplan(dienstplan)
  })

  ipcMain.handle('dienstplaene:delete', async (_event, id: number) => {
    deleteDienstplan(id)
  })

  ipcMain.handle('dienstplaene:generate', async (event, monat: string) => {
    return await generiereDienstplan(monat, (progress) => {
      event.sender.send('dienstplaene:generate:progress', progress)
    })
  })

  // ============================================================
  // Wünsche
  // ============================================================
  ipcMain.handle('wuensche:forMonat', async (_event, monat: string) => {
    return getWuenscheForMonat(monat)
  })

  ipcMain.handle('wuensche:forPersonAndMonat', async (_event, personId: number, monat: string) => {
    return getWuenscheForPersonAndMonat(personId, monat)
  })

  ipcMain.handle('wuensche:create', async (_event, wunsch) => {
    return createWunsch(wunsch)
  })

  ipcMain.handle('wuensche:createBatch', async (_event, wuensche) => {
    return createWuenscheBatch(wuensche)
  })

  ipcMain.handle('wuensche:delete', async (_event, id: number) => {
    deleteWunsch(id)
  })

  ipcMain.handle('wuensche:deleteForPersonAndMonat', async (_event, personId: number, monat: string) => {
    deleteWuenscheForPersonAndMonat(personId, monat)
  })

  // ============================================================
  // Statistiken
  // ============================================================
  ipcMain.handle('statistiken:gesamt', async (_event, monatVon: string, monatBis: string) => {
    return berechneGesamtStatistik(monatVon, monatBis)
  })

  ipcMain.handle('statistiken:fairness', async () => {
    return getFairnessScores()
  })

  // ============================================================
  // Excel
  // ============================================================
  ipcMain.handle('excel:exportDienstplan', async (_event, dienstplanId: number, filePath: string) => {
    const dp = getDienstplanById(dienstplanId)
    if (!dp) throw new Error('Dienstplan nicht gefunden')
    await exportDienstplanToExcel(dp, filePath)
  })

  ipcMain.handle('excel:exportStatistiken', async (_event, monatVon: string, monatBis: string, filePath: string) => {
    const stats = berechneGesamtStatistik(monatVon, monatBis)
    await exportStatistikToExcel(stats, monatVon, monatBis, filePath)
  })

  ipcMain.handle('excel:importWuensche', async (_event, filePath: string, monat: string) => {
    const personen = getAllPersonen()
    const personMap = new Map(personen.map((p) => [p.name, p.id]))
    const items = await importWuenscheFromExcel(filePath, monat, personMap)
    return items
  })

  // ============================================================
  // File dialogs
  // ============================================================
  ipcMain.handle('dialog:saveExcel', async (_event, defaultName: string) => {
    const result = await dialog.showSaveDialog({
      defaultPath: defaultName,
      filters: [{ name: 'Excel', extensions: ['xlsx'] }]
    })
    return result.canceled ? null : result.filePath
  })

  ipcMain.handle('dialog:openExcel', async () => {
    const result = await dialog.showOpenDialog({
      filters: [{ name: 'Excel', extensions: ['xlsx'] }],
      properties: ['openFile']
    })
    return result.canceled ? null : result.filePaths[0]
  })
}
