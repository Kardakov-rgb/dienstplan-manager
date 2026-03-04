import {
  Person, Dienst, Dienstplan, MonatsWunsch, FairnessScore, WunschStatistik,
  DienstArt, DienstStatus, DienstplanStatus, Wochentag, WunschTyp,
  dateToWochentag, GenerierungsResult
} from '../../shared/types'

// ============================================================
// Constants
// ============================================================
const SCORE_KEINE_BISHERIGEN_DIENSTE = 1000.0
const MAX_ABSTAND_TAGE = 30
const EPSILON = 0.1
const FREIWUNSCH_MALUS = 100.0
const DIENSTWUNSCH_BONUS = 50.0
const DAVINCI_SAMSTAG_MALUS = 80.0
const DAVINCI_MEHRFACH_MALUS = 70.0
const DAVINCI_MAX_PRO_MONAT = 1

// ============================================================
// Types
// ============================================================
interface DienstSlot {
  datum: string         // YYYY-MM-DD
  dienstArt: DienstArt
  zugewiesenePerson: Person | null
  moeglicheKandidaten: Person[]
}

// ============================================================
// Helpers
// ============================================================

function toIsoDate(date: Date): string {
  return date.toISOString().split('T')[0]
}

function parseDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

function addDays(iso: string, days: number): string {
  const d = parseDate(iso)
  d.setDate(d.getDate() + days)
  return toIsoDate(d)
}

function diffDays(a: string, b: string): number {
  return Math.abs(parseDate(a).getTime() - parseDate(b).getTime()) / 86400000
}

function getWochentag(iso: string): Wochentag {
  return dateToWochentag(parseDate(iso))
}

function getDatesInMonth(monat: string): string[] {
  const [y, m] = monat.split('-').map(Number)
  const dates: string[] = []
  const daysInMonth = new Date(y, m, 0).getDate()
  for (let d = 1; d <= daysInMonth; d++) {
    dates.push(`${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`)
  }
  return dates
}

/** Which weekdays each DienstArt is scheduled on */
const DIENSTART_WOCHENTAGE: Record<DienstArt, Set<Wochentag>> = {
  [DienstArt.DIENST_24H]: new Set([
    Wochentag.MONTAG, Wochentag.DIENSTAG, Wochentag.MITTWOCH,
    Wochentag.DONNERSTAG, Wochentag.FREITAG, Wochentag.SAMSTAG, Wochentag.SONNTAG
  ]),
  [DienstArt.VISTEN]: new Set([Wochentag.SAMSTAG, Wochentag.SONNTAG]),
  [DienstArt.DAVINCI]: new Set([Wochentag.FREITAG])
}

// ============================================================
// Generator
// ============================================================

export class DienstplanGenerator {
  private readonly personen: Person[]
  private readonly monat: string
  private readonly wuenscheByPersonByDatum: Map<number, Map<string, MonatsWunsch>>
  private readonly fairnessScores: Map<number, FairnessScore>

  private dienstSlots: DienstSlot[] = []
  private personDienste: Map<number, Set<string>> = new Map()
  private warnungen: Set<string> = new Set()
  private progressCallback?: (progress: number) => void

  constructor(
    personen: Person[],
    monat: string,
    monatsWuensche: MonatsWunsch[] = [],
    fairnessScores: Map<number, FairnessScore> = new Map()
  ) {
    this.personen = [...personen]
    this.monat = monat
    this.fairnessScores = fairnessScores

    // Index wishes by personId -> datum
    this.wuenscheByPersonByDatum = new Map()
    for (const w of monatsWuensche) {
      if (!this.wuenscheByPersonByDatum.has(w.personId)) {
        this.wuenscheByPersonByDatum.set(w.personId, new Map())
      }
      this.wuenscheByPersonByDatum.get(w.personId)!.set(w.datum, w)
    }
  }

  setProgressCallback(cb: (progress: number) => void): void {
    this.progressCallback = cb
  }

  generiereDienstplan(): GenerierungsResult {
    // 1. Initialize
    this.personDienste = new Map()
    this.warnungen = new Set()
    this.dienstSlots = []
    for (const p of this.personen) {
      this.personDienste.set(p.id, new Set())
    }

    // 2. Create slots
    this.createDienstSlots()

    // 3. Constraint propagation
    this.propagateConstraints()

    // 4. Sort by MCV
    this.sortByMCV()

    // 5. Backtracking
    this.backtrack(0)

    // 6. Build result
    const dienstplan = this.buildDienstplan()
    const stats = this.berechneWunschStatistiken()

    return {
      dienstplan,
      warnungen: Array.from(this.warnungen),
      erfolgreich: true,
      wunschStatistiken: stats
    }
  }

  // ----------------------------------------------------------
  private createDienstSlots(): void {
    const dates = getDatesInMonth(this.monat)
    for (const datum of dates) {
      const wochentag = getWochentag(datum)
      for (const [art, erlaubteWochentage] of Object.entries(DIENSTART_WOCHENTAGE)) {
        if (erlaubteWochentage.has(wochentag)) {
          this.dienstSlots.push({
            datum,
            dienstArt: art as DienstArt,
            zugewiesenePerson: null,
            moeglicheKandidaten: []
          })
        }
      }
    }
    this.dienstSlots.sort((a, b) => {
      const d = a.datum.localeCompare(b.datum)
      if (d !== 0) return d
      return Object.values(DienstArt).indexOf(a.dienstArt) -
             Object.values(DienstArt).indexOf(b.dienstArt)
    })
  }

  // ----------------------------------------------------------
  private propagateConstraints(): void {
    let changed: boolean
    let iter = 0
    do {
      changed = false
      iter++
      for (const slot of this.dienstSlots) {
        if (slot.zugewiesenePerson) continue
        const kandidaten = this.findKandidaten(slot)
        slot.moeglicheKandidaten = kandidaten
        if (kandidaten.length === 0) {
          this.warnungen.add(`MANUELL ZUWEISEN: ${slot.datum} ${slot.dienstArt} - Keine Person verfügbar`)
        } else if (kandidaten.length === 1) {
          const p = kandidaten[0]
          if (this.kannPersonDienst(p, slot)) {
            slot.zugewiesenePerson = p
            this.personDienste.get(p.id)!.add(slot.datum)
            changed = true
          }
        }
      }
    } while (changed && iter < 100)
  }

  // ----------------------------------------------------------
  private sortByMCV(): void {
    this.dienstSlots.sort((a, b) => {
      if (a.zugewiesenePerson && !b.zugewiesenePerson) return 1
      if (!a.zugewiesenePerson && b.zugewiesenePerson) return -1
      if (a.zugewiesenePerson && b.zugewiesenePerson) return 0
      const k1 = a.moeglicheKandidaten.length || Infinity
      const k2 = b.moeglicheKandidaten.length || Infinity
      if (k1 !== k2) return k1 - k2
      const d = a.datum.localeCompare(b.datum)
      if (d !== 0) return d
      return Object.values(DienstArt).indexOf(a.dienstArt) -
             Object.values(DienstArt).indexOf(b.dienstArt)
    })
  }

  // ----------------------------------------------------------
  private backtrack(slotIndex: number): boolean {
    if (this.progressCallback && this.dienstSlots.length > 0) {
      this.progressCallback(slotIndex / this.dienstSlots.length)
    }
    if (slotIndex >= this.dienstSlots.length) return true

    const slot = this.dienstSlots[slotIndex]
    if (slot.zugewiesenePerson) return this.backtrack(slotIndex + 1)

    const kandidaten = this.findKandidaten(slot)
    kandidaten.sort((a, b) => this.comparePersonen(a, b, slot))

    for (const kandidat of kandidaten) {
      if (this.kannPersonDienst(kandidat, slot)) {
        slot.zugewiesenePerson = kandidat
        this.personDienste.get(kandidat.id)!.add(slot.datum)

        if (this.backtrack(slotIndex + 1)) return true

        slot.zugewiesenePerson = null
        this.personDienste.get(kandidat.id)!.delete(slot.datum)
      }
    }

    this.warnungen.add(`MANUELL ZUWEISEN: ${slot.datum} ${slot.dienstArt} - Keine Person verfügbar`)
    return this.backtrack(slotIndex + 1)
  }

  // ----------------------------------------------------------
  private findKandidaten(slot: DienstSlot): Person[] {
    return this.personen.filter((p) => this.grundlegendeVerfuegbarkeit(p, slot))
  }

  private grundlegendeVerfuegbarkeit(person: Person, slot: DienstSlot): boolean {
    if (!person.verfuegbareDienstArten.includes(slot.dienstArt)) return false
    if (!person.arbeitsTage.includes(getWochentag(slot.datum))) return false
    if (this.personDienste.get(person.id)?.has(slot.datum)) return false
    if (this.hatUrlaubAm(person.id, slot.datum)) return false
    return true
  }

  private kannPersonDienst(person: Person, slot: DienstSlot): boolean {
    const dienste = this.personDienste.get(person.id)!
    const vortag = addDays(slot.datum, -1)

    if (dienste.has(vortag)) {
      // Exception: Visten Sa→So is allowed
      const isVistenSoNachSa =
        slot.dienstArt === DienstArt.VISTEN &&
        getWochentag(slot.datum) === Wochentag.SONNTAG &&
        this.hatPersonVistenAm(person, vortag)
      if (!isVistenSoNachSa) return false
    }

    if (this.hatUrlaubAm(person.id, vortag)) return false

    const max = person.anzahlDienste
    if (max > 0 && dienste.size >= max) return false

    return true
  }

  private hatUrlaubAm(personId: number, datum: string): boolean {
    const w = this.wuenscheByPersonByDatum.get(personId)?.get(datum)
    return w?.typ === WunschTyp.URLAUB
  }

  private hatPersonVistenAm(person: Person, datum: string): boolean {
    return this.dienstSlots.some(
      (s) => s.datum === datum && s.dienstArt === DienstArt.VISTEN && s.zugewiesenePerson?.id === person.id
    )
  }

  // ----------------------------------------------------------
  private comparePersonen(p1: Person, p2: Person, slot: DienstSlot): number {
    // 0. Visten weekend package: prefer same person for Sa+So
    if (slot.dienstArt === DienstArt.VISTEN && getWochentag(slot.datum) === Wochentag.SONNTAG) {
      const samstag = addDays(slot.datum, -1)
      const p1Sa = this.hatPersonVistenAm(p1, samstag)
      const p2Sa = this.hatPersonVistenAm(p2, samstag)
      if (p1Sa && !p2Sa) return -1
      if (!p1Sa && p2Sa) return 1
    }

    // 1. Wish score
    const w1 = this.wunschScore(p1, slot)
    const w2 = this.wunschScore(p2, slot)
    if (Math.abs(w1 - w2) > EPSILON) return w2 - w1

    // 2. DaVinci score
    const dv1 = this.davinciScore(p1, slot)
    const dv2 = this.davinciScore(p2, slot)
    if (Math.abs(dv1 - dv2) > EPSILON) return dv2 - dv1

    // 3. Fairness
    const f = this.compareFairness(p1, p2)
    if (f !== 0) return f

    // 4. Soll fulfillment
    const s = this.compareSoll(p1, p2)
    if (s !== 0) return s

    // 5. Gap score
    const g1 = this.abstandScore(p1, slot.datum)
    const g2 = this.abstandScore(p2, slot.datum)
    if (Math.abs(g1 - g2) > EPSILON) return g2 - g1

    // 6. Fewer shifts so far
    const n1 = this.personDienste.get(p1.id)!.size
    const n2 = this.personDienste.get(p2.id)!.size
    if (n1 !== n2) return n1 - n2

    // 7. DienstArt balance
    const b1 = this.dienstArtBalance(p1, slot.dienstArt)
    const b2 = this.dienstArtBalance(p2, slot.dienstArt)
    if (b1 !== b2) return b1 - b2

    // 8. Alphabetical
    return p1.name.localeCompare(p2.name)
  }

  private wunschScore(person: Person, slot: DienstSlot): number {
    const w = this.wuenscheByPersonByDatum.get(person.id)?.get(slot.datum)
    if (!w) return 0
    if (w.typ === WunschTyp.FREIWUNSCH) return -FREIWUNSCH_MALUS
    if (w.typ === WunschTyp.DIENSTWUNSCH && slot.dienstArt === DienstArt.DIENST_24H) return DIENSTWUNSCH_BONUS
    return 0
  }

  private davinciScore(person: Person, slot: DienstSlot): number {
    let score = 0
    if (getWochentag(slot.datum) === Wochentag.SAMSTAG) {
      const freitag = addDays(slot.datum, -1)
      const hatteDaVinci = this.dienstSlots.some(
        (s) => s.datum === freitag && s.dienstArt === DienstArt.DAVINCI && s.zugewiesenePerson?.id === person.id
      )
      if (hatteDaVinci) score -= DAVINCI_SAMSTAG_MALUS
    }
    if (slot.dienstArt === DienstArt.DAVINCI) {
      const count = this.dienstArtBalance(person, DienstArt.DAVINCI)
      if (count >= DAVINCI_MAX_PRO_MONAT) score -= DAVINCI_MEHRFACH_MALUS
    }
    return score
  }

  private compareFairness(p1: Person, p2: Person): number {
    const s1 = this.fairnessScores.get(p1.id)
    const s2 = this.fairnessScores.get(p2.id)
    if (!s1 && !s2) return 0
    if (!s1) return 1
    if (!s2) return -1
    return s1.durchschnittlicheErfuellung - s2.durchschnittlicheErfuellung
  }

  private compareSoll(p1: Person, p2: Person): number {
    const soll1 = p1.anzahlDienste
    const soll2 = p2.anzahlDienste
    const ist1 = this.personDienste.get(p1.id)!.size
    const ist2 = this.personDienste.get(p2.id)!.size
    if (soll1 > 0 && soll2 === 0) return ist1 < soll1 ? -1 : 1
    if (soll1 === 0 && soll2 > 0) return ist2 < soll2 ? 1 : -1
    if (soll1 === 0 && soll2 === 0) return 0
    return ist1 / soll1 - ist2 / soll2
  }

  private abstandScore(person: Person, datum: string): number {
    const dienste = Array.from(this.personDienste.get(person.id)!)
    if (dienste.length === 0) return SCORE_KEINE_BISHERIGEN_DIENSTE
    return Math.min(...dienste.map((d) => diffDays(d, datum)))
  }

  private dienstArtBalance(person: Person, art: DienstArt): number {
    return this.dienstSlots.filter(
      (s) => s.zugewiesenePerson?.id === person.id && s.dienstArt === art
    ).length
  }

  // ----------------------------------------------------------
  private buildDienstplan(): Dienstplan {
    const today = toIsoDate(new Date())
    const dienste: Dienst[] = this.dienstSlots.map((slot) => ({
      datum: slot.datum,
      art: slot.dienstArt,
      personId: slot.zugewiesenePerson?.id ?? null,
      personName: slot.zugewiesenePerson?.name ?? null,
      status: slot.zugewiesenePerson ? DienstStatus.GEPLANT : DienstStatus.ABGESAGT,
      bemerkung: slot.zugewiesenePerson ? null : 'MANUELL ZUWEISEN - Keine Person verfügbar'
    }))

    return {
      name: `Dienstplan ${this.monat}`,
      monat: this.monat,
      erstelltAm: today,
      letztesUpdate: today,
      status: DienstplanStatus.ENTWURF,
      dienste
    }
  }

  // ----------------------------------------------------------
  berechneWunschStatistiken(): Record<number, WunschStatistik> {
    const result: Record<number, WunschStatistik> = {}

    for (const [personId, wunschByDatum] of this.wuenscheByPersonByDatum) {
      const person = this.personen.find((p) => p.id === personId)
      const personName = person?.name ?? 'Unbekannt'

      const dienstTage = new Set(
        this.dienstSlots
          .filter((s) => s.zugewiesenePerson?.id === personId)
          .map((s) => s.datum)
      )

      let anzahlUrlaub = 0
      let anzahlFreiwuensche = 0
      let erfuellteFreiwuensche = 0
      let anzahlDienstwuensche = 0
      let erfuellteDienstwuensche = 0

      for (const [datum, wunsch] of wunschByDatum) {
        if (wunsch.typ === WunschTyp.URLAUB) {
          anzahlUrlaub++
        } else if (wunsch.typ === WunschTyp.FREIWUNSCH) {
          anzahlFreiwuensche++
          if (!dienstTage.has(datum)) erfuellteFreiwuensche++
        } else if (wunsch.typ === WunschTyp.DIENSTWUNSCH) {
          anzahlDienstwuensche++
          const hat24h = this.dienstSlots.some(
            (s) => s.datum === datum && s.dienstArt === DienstArt.DIENST_24H && s.zugewiesenePerson?.id === personId
          )
          if (hat24h) erfuellteDienstwuensche++
        }
      }

      result[personId] = {
        personId,
        personName,
        monat: this.monat,
        anzahlUrlaub,
        anzahlFreiwuensche,
        erfuellteFreiwuensche,
        anzahlDienstwuensche,
        erfuellteDienstwuensche
      }
    }

    return result
  }
}
