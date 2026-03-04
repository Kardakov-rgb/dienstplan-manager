// ============================================================
// Enums
// ============================================================

export enum Wochentag {
  MONTAG = 'MONTAG',
  DIENSTAG = 'DIENSTAG',
  MITTWOCH = 'MITTWOCH',
  DONNERSTAG = 'DONNERSTAG',
  FREITAG = 'FREITAG',
  SAMSTAG = 'SAMSTAG',
  SONNTAG = 'SONNTAG'
}

export const WochentagInfo: Record<Wochentag, { vollName: string; kurzName: string; dayIndex: number }> = {
  [Wochentag.MONTAG]:    { vollName: 'Montag',     kurzName: 'Mo', dayIndex: 1 },
  [Wochentag.DIENSTAG]:  { vollName: 'Dienstag',   kurzName: 'Di', dayIndex: 2 },
  [Wochentag.MITTWOCH]:  { vollName: 'Mittwoch',   kurzName: 'Mi', dayIndex: 3 },
  [Wochentag.DONNERSTAG]:{ vollName: 'Donnerstag', kurzName: 'Do', dayIndex: 4 },
  [Wochentag.FREITAG]:   { vollName: 'Freitag',    kurzName: 'Fr', dayIndex: 5 },
  [Wochentag.SAMSTAG]:   { vollName: 'Samstag',    kurzName: 'Sa', dayIndex: 6 },
  [Wochentag.SONNTAG]:   { vollName: 'Sonntag',    kurzName: 'So', dayIndex: 0 }
}

/** Convert a Date object to Wochentag */
export function dateToWochentag(date: Date): Wochentag {
  const day = date.getDay() // 0=So, 1=Mo, ..., 6=Sa
  const map: Record<number, Wochentag> = {
    0: Wochentag.SONNTAG,
    1: Wochentag.MONTAG,
    2: Wochentag.DIENSTAG,
    3: Wochentag.MITTWOCH,
    4: Wochentag.DONNERSTAG,
    5: Wochentag.FREITAG,
    6: Wochentag.SAMSTAG
  }
  return map[day]
}

export enum DienstArt {
  DIENST_24H = 'DIENST_24H',
  VISTEN = 'VISTEN',
  DAVINCI = 'DAVINCI'
}

export const DienstArtInfo: Record<DienstArt, { vollName: string; kurzName: string }> = {
  [DienstArt.DIENST_24H]: { vollName: 'Vordergrund', kurzName: '24h' },
  [DienstArt.VISTEN]:     { vollName: 'Visitendienst', kurzName: 'VD' },
  [DienstArt.DAVINCI]:    { vollName: 'DaVinci', kurzName: 'DV-D' }
}

export enum DienstStatus {
  GEPLANT = 'GEPLANT',
  BESTAETIGT = 'BESTAETIGT',
  ABGESAGT = 'ABGESAGT',
  ERSETZT = 'ERSETZT',
  ABGESCHLOSSEN = 'ABGESCHLOSSEN'
}

export const DienstStatusInfo: Record<DienstStatus, { vollName: string; kurzName: string }> = {
  [DienstStatus.GEPLANT]:       { vollName: 'Geplant',       kurzName: 'GP' },
  [DienstStatus.BESTAETIGT]:    { vollName: 'Bestätigt',     kurzName: 'B' },
  [DienstStatus.ABGESAGT]:      { vollName: 'Abgesagt',      kurzName: 'A' },
  [DienstStatus.ERSETZT]:       { vollName: 'Ersetzt',       kurzName: 'E' },
  [DienstStatus.ABGESCHLOSSEN]: { vollName: 'Abgeschlossen', kurzName: 'AB' }
}

export enum DienstplanStatus {
  ENTWURF = 'ENTWURF',
  GEPRUEFT = 'GEPRUEFT',
  VEROEFFENTLICHT = 'VEROEFFENTLICHT',
  ARCHIVIERT = 'ARCHIVIERT',
  STORNIERT = 'STORNIERT'
}

export const DienstplanStatusInfo: Record<DienstplanStatus, { vollName: string; kurzName: string }> = {
  [DienstplanStatus.ENTWURF]:        { vollName: 'Entwurf',        kurzName: 'E' },
  [DienstplanStatus.GEPRUEFT]:       { vollName: 'Geprüft',        kurzName: 'GP' },
  [DienstplanStatus.VEROEFFENTLICHT]:{ vollName: 'Veröffentlicht', kurzName: 'V' },
  [DienstplanStatus.ARCHIVIERT]:     { vollName: 'Archiviert',     kurzName: 'A' },
  [DienstplanStatus.STORNIERT]:      { vollName: 'Storniert',      kurzName: 'S' }
}

export enum WunschTyp {
  URLAUB = 'URLAUB',
  FREIWUNSCH = 'FREIWUNSCH',
  DIENSTWUNSCH = 'DIENSTWUNSCH'
}

export const WunschTypInfo: Record<WunschTyp, { vollName: string; kuerzel: string; isHart: boolean }> = {
  [WunschTyp.URLAUB]:       { vollName: 'Urlaub',              kuerzel: 'U', isHart: true },
  [WunschTyp.FREIWUNSCH]:   { vollName: 'Freiwunsch',          kuerzel: 'F', isHart: false },
  [WunschTyp.DIENSTWUNSCH]: { vollName: 'Dienstwunsch (24h)',  kuerzel: 'D', isHart: false }
}

// ============================================================
// Data Models
// ============================================================

export interface Person {
  id: number
  name: string
  anzahlDienste: number
  arbeitsTage: Wochentag[]
  verfuegbareDienstArten: DienstArt[]
}

export interface Dienst {
  id?: number
  datum: string           // ISO date string YYYY-MM-DD
  art: DienstArt
  personId?: number | null
  personName?: string | null
  status: DienstStatus
  bemerkung?: string | null
}

export interface Dienstplan {
  id?: number
  name: string
  monat: string           // YYYY-MM format
  erstelltAm: string      // YYYY-MM-DD
  letztesUpdate: string   // YYYY-MM-DD
  status: DienstplanStatus
  dienste: Dienst[]
  bemerkung?: string | null
}

export interface MonatsWunsch {
  id?: number
  personId: number
  personName: string
  datum: string           // YYYY-MM-DD
  monat: string           // YYYY-MM
  typ: WunschTyp
  erfuellt?: boolean | null
  bemerkung?: string | null
}

export interface FairnessScore {
  personId: number
  personName: string
  anzahlMonate: number
  gesamtWuensche: number
  erfuellteWuensche: number
  durchschnittlicheErfuellung: number
}

export interface WunschStatistik {
  personId: number
  personName: string
  monat: string
  anzahlUrlaub: number
  anzahlFreiwuensche: number
  erfuellteFreiwuensche: number
  anzahlDienstwuensche: number
  erfuellteDienstwuensche: number
}

export interface FairnessHistorie {
  id?: number
  personId: number
  personName: string
  monatJahr: string       // YYYY-MM
  anzahlWuensche: number
  erfuellteWuensche: number
}

// ============================================================
// Service result types
// ============================================================

export interface GenerierungsResult {
  dienstplan: Dienstplan | null
  warnungen: string[]
  erfolgreich: boolean
  wunschStatistiken: Record<number, WunschStatistik>
}

export interface GesamtStatistik {
  gesamtDienste: number
  zugewieseneDienste: number
  offeneDienste: number
  zuweisungsgrad: number
  wunschErfuellungsQuote: number
  personStatistiken: PersonDienstStatistik[]
  offeneDiensteListe: OffenerDienst[]
  konflikte: Konflikt[]
}

export interface PersonDienstStatistik {
  personId: number
  personName: string
  sollDienste: number
  istDienste: number
  dienste24h: number
  diensteVisten: number
  diensteDAVinci: number
}

export interface OffenerDienst {
  datum: string
  dienstArt: DienstArt
  dienstplanName: string
}

export interface Konflikt {
  personName: string
  datum: string
  dienste: string[]
}

// ============================================================
// IPC Channel types (for type-safe communication)
// ============================================================

export interface IpcApi {
  // Persons
  getPersonen: () => Promise<Person[]>
  createPerson: (person: Omit<Person, 'id'>) => Promise<Person>
  updatePerson: (person: Person) => Promise<Person>
  deletePerson: (id: number) => Promise<void>

  // Dienstplaene
  getDienstplaene: () => Promise<Dienstplan[]>
  getDienstplan: (id: number) => Promise<Dienstplan | null>
  getDienstplaeneForMonat: (monat: string) => Promise<Dienstplan[]>
  saveDienstplan: (dienstplan: Dienstplan) => Promise<Dienstplan>
  deleteDienstplan: (id: number) => Promise<void>
  generiereDienstplan: (monat: string) => Promise<GenerierungsResult>

  // Wuensche
  getWuensche: (monat: string) => Promise<MonatsWunsch[]>
  getWuenscheForPerson: (personId: number, monat: string) => Promise<MonatsWunsch[]>
  createWunsch: (wunsch: Omit<MonatsWunsch, 'id'>) => Promise<MonatsWunsch>
  createWuenscheBatch: (wuensche: Omit<MonatsWunsch, 'id'>[]) => Promise<MonatsWunsch[]>
  deleteWunsch: (id: number) => Promise<void>
  deleteWuenscheForPersonAndMonat: (personId: number, monat: string) => Promise<void>

  // Statistiken
  getGesamtStatistik: (monatVon: string, monatBis: string) => Promise<GesamtStatistik>
  getFairnessScores: (monat: string) => Promise<FairnessScore[]>

  // Excel
  exportDienstplan: (dienstplanId: number, filePath: string) => Promise<void>
  exportStatistiken: (monatVon: string, monatBis: string, filePath: string) => Promise<void>
  importWuensche: (filePath: string, monat: string) => Promise<MonatsWunsch[]>
  showSaveDialog: (defaultName: string, ext: string) => Promise<string | null>
  showOpenDialog: (ext: string) => Promise<string | null>
}
