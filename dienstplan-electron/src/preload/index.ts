import { contextBridge, ipcRenderer } from 'electron'
import { electronAPI } from '@electron-toolkit/preload'

const api = {
  // Persons
  getPersonen: () => ipcRenderer.invoke('persons:getAll'),
  createPerson: (person: unknown) => ipcRenderer.invoke('persons:create', person),
  updatePerson: (person: unknown) => ipcRenderer.invoke('persons:update', person),
  deletePerson: (id: number) => ipcRenderer.invoke('persons:delete', id),

  // Dienstplaene
  getDienstplaene: () => ipcRenderer.invoke('dienstplaene:getAll'),
  getDienstplan: (id: number) => ipcRenderer.invoke('dienstplaene:get', id),
  getDienstplaeneForMonat: (monat: string) => ipcRenderer.invoke('dienstplaene:forMonat', monat),
  saveDienstplan: (dp: unknown) => ipcRenderer.invoke('dienstplaene:save', dp),
  deleteDienstplan: (id: number) => ipcRenderer.invoke('dienstplaene:delete', id),
  generiereDienstplan: (monat: string) => ipcRenderer.invoke('dienstplaene:generate', monat),
  onGenerierungProgress: (cb: (progress: number) => void) => {
    ipcRenderer.on('dienstplaene:generate:progress', (_event, p) => cb(p))
    return () => ipcRenderer.removeAllListeners('dienstplaene:generate:progress')
  },

  // Wuensche
  getWuensche: (monat: string) => ipcRenderer.invoke('wuensche:forMonat', monat),
  getWuenscheForPerson: (personId: number, monat: string) =>
    ipcRenderer.invoke('wuensche:forPersonAndMonat', personId, monat),
  createWunsch: (w: unknown) => ipcRenderer.invoke('wuensche:create', w),
  createWuenscheBatch: (ws: unknown[]) => ipcRenderer.invoke('wuensche:createBatch', ws),
  deleteWunsch: (id: number) => ipcRenderer.invoke('wuensche:delete', id),
  deleteWuenscheForPersonAndMonat: (personId: number, monat: string) =>
    ipcRenderer.invoke('wuensche:deleteForPersonAndMonat', personId, monat),

  // Statistiken
  getGesamtStatistik: (von: string, bis: string) => ipcRenderer.invoke('statistiken:gesamt', von, bis),
  getFairnessScores: () => ipcRenderer.invoke('statistiken:fairness'),

  // Excel
  exportDienstplan: (id: number, path: string) => ipcRenderer.invoke('excel:exportDienstplan', id, path),
  exportStatistiken: (von: string, bis: string, path: string) =>
    ipcRenderer.invoke('excel:exportStatistiken', von, bis, path),
  importWuensche: (path: string, monat: string) => ipcRenderer.invoke('excel:importWuensche', path, monat),
  showSaveDialog: (name: string) => ipcRenderer.invoke('dialog:saveExcel', name),
  showOpenDialog: () => ipcRenderer.invoke('dialog:openExcel')
}

if (process.contextIsolated) {
  try {
    contextBridge.exposeInMainWorld('electron', electronAPI)
    contextBridge.exposeInMainWorld('api', api)
  } catch (error) {
    console.error(error)
  }
} else {
  // @ts-ignore
  window.electron = electronAPI
  // @ts-ignore
  window.api = api
}
