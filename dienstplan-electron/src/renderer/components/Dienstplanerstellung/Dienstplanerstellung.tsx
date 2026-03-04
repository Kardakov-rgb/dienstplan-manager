import React, { useEffect, useState, useCallback } from 'react'
import {
  Dienstplan, Dienst, Person, MonatsWunsch,
  DienstArt, DienstArtInfo, DienstStatus, DienstplanStatus, DienstplanStatusInfo,
  WunschTyp, WunschTypInfo,
  GenerierungsResult
} from '../../../shared/types'

// Weekday names (0=Sun)
const TAGE_KURZ = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa']

function getWochentagKurz(datum: string): string {
  const [y, m, d] = datum.split('-').map(Number)
  return TAGE_KURZ[new Date(y, m - 1, d).getDay()]
}

function isWeekend(datum: string): boolean {
  const [y, m, d] = datum.split('-').map(Number)
  const day = new Date(y, m - 1, d).getDay()
  return day === 0 || day === 6
}

function getDatesForMonth(monat: string): string[] {
  const [y, m] = monat.split('-').map(Number)
  const days = new Date(y, m, 0).getDate()
  const dates: string[] = []
  for (let d = 1; d <= days; d++) {
    dates.push(`${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`)
  }
  return dates
}

function currentMonat(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function artClass(art: DienstArt): string {
  if (art === DienstArt.DIENST_24H) return 'dienst-24h'
  if (art === DienstArt.VISTEN) return 'dienst-visten'
  if (art === DienstArt.DAVINCI) return 'dienst-davinci'
  return ''
}

export default function Dienstplanerstellung(): React.ReactElement {
  const [monat, setMonat] = useState(currentMonat())
  const [personen, setPersonen] = useState<Person[]>([])
  const [dienstplaene, setDienstplaene] = useState<Dienstplan[]>([])
  const [selectedDp, setSelectedDp] = useState<Dienstplan | null>(null)
  const [wuensche, setWuensche] = useState<MonatsWunsch[]>([])
  const [generating, setGenerating] = useState(false)
  const [progress, setProgress] = useState(0)
  const [generierungsResult, setGenerierungsResult] = useState<GenerierungsResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [editingDienst, setEditingDienst] = useState<Dienst | null>(null)
  const [showWunschModal, setShowWunschModal] = useState(false)
  const [wunschPersonId, setWunschPersonId] = useState<number | ''>('')
  const [wunschDatum, setWunschDatum] = useState('')
  const [wunschTyp, setWunschTyp] = useState<WunschTyp>(WunschTyp.FREIWUNSCH)

  useEffect(() => {
    loadData()
  }, [monat])

  useEffect(() => {
    // Listen for progress events
    const off = window.api.onGenerierungProgress((p: number) => setProgress(p))
    return off
  }, [])

  const loadData = async () => {
    try {
      const [p, dp, w] = await Promise.all([
        window.api.getPersonen(),
        window.api.getDienstplaeneForMonat(monat),
        window.api.getWuensche(monat)
      ])
      setPersonen(p)
      setDienstplaene(dp)
      setWuensche(w)
      setSelectedDp(dp[0] ?? null)
      setError(null)
    } catch (e: unknown) {
      setError(String(e))
    }
  }

  const handleGenerate = async () => {
    setGenerating(true)
    setProgress(0)
    setError(null)
    setGenerierungsResult(null)
    try {
      const result: GenerierungsResult = await window.api.generiereDienstplan(monat)
      setGenerierungsResult(result)
      if (result.dienstplan) {
        const saved = await window.api.saveDienstplan(result.dienstplan)
        const dp = await window.api.getDienstplaeneForMonat(monat)
        setDienstplaene(dp)
        setSelectedDp(dp.find((d) => d.id === saved.id) ?? dp[0] ?? null)
      }
    } catch (e: unknown) {
      setError(String(e))
    } finally {
      setGenerating(false)
      setProgress(1)
    }
  }

  const handleSaveDienstplan = async () => {
    if (!selectedDp) return
    try {
      const saved = await window.api.saveDienstplan(selectedDp)
      const dp = await window.api.getDienstplaeneForMonat(monat)
      setDienstplaene(dp)
      setSelectedDp(dp.find((d) => d.id === saved.id) ?? null)
    } catch (e: unknown) {
      setError(String(e))
    }
  }

  const handleDeleteDp = async () => {
    if (!selectedDp?.id) return
    if (!confirm(`Dienstplan "${selectedDp.name}" wirklich löschen?`)) return
    await window.api.deleteDienstplan(selectedDp.id)
    await loadData()
  }

  const handleExport = async () => {
    if (!selectedDp?.id) return
    const path = await window.api.showSaveDialog(`Dienstplan-${monat}.xlsx`)
    if (!path) return
    await window.api.exportDienstplan(selectedDp.id, path)
  }

  const updateDienst = (dienst: Dienst) => {
    if (!selectedDp) return
    const updated = {
      ...selectedDp,
      dienste: selectedDp.dienste.map((d) =>
        d.id === dienst.id ? dienst : d
      )
    }
    setSelectedDp(updated)
  }

  const assignPerson = (dienst: Dienst, personId: number | null) => {
    const person = personId ? personen.find((p) => p.id === personId) ?? null : null
    updateDienst({
      ...dienst,
      personId: person?.id ?? null,
      personName: person?.name ?? null,
      status: person ? DienstStatus.GEPLANT : DienstStatus.ABGESAGT
    })
  }

  const addWunsch = async () => {
    if (!wunschPersonId || !wunschDatum) return
    const person = personen.find((p) => p.id === Number(wunschPersonId))
    if (!person) return
    await window.api.createWunsch({
      personId: person.id,
      personName: person.name,
      datum: wunschDatum,
      monat,
      typ: wunschTyp
    })
    setShowWunschModal(false)
    setWunschPersonId('')
    setWunschDatum('')
    const w = await window.api.getWuensche(monat)
    setWuensche(w)
  }

  const deleteWunsch = async (id: number) => {
    await window.api.deleteWunsch(id)
    const w = await window.api.getWuensche(monat)
    setWuensche(w)
  }

  // Group dienste by date
  const diensteByDate = new Map<string, Dienst[]>()
  for (const d of (selectedDp?.dienste ?? [])) {
    if (!diensteByDate.has(d.datum)) diensteByDate.set(d.datum, [])
    diensteByDate.get(d.datum)!.push(d)
  }

  // Wuensche by date
  const wuenscheByDate = new Map<string, MonatsWunsch[]>()
  for (const w of wuensche) {
    if (!wuenscheByDate.has(w.datum)) wuenscheByDate.set(w.datum, [])
    wuenscheByDate.get(w.datum)!.push(w)
  }

  const dates = getDatesForMonth(monat)

  return (
    <div className="dp-layout">
      {/* Sidebar */}
      <div className="dp-sidebar">
        <div className="card">
          <h3 className="card-title">Einstellungen</h3>
          <div className="form-group">
            <label className="form-label">Monat</label>
            <input
              className="form-control"
              type="month"
              value={monat}
              onChange={(e) => setMonat(e.target.value)}
            />
          </div>

          <button
            className="btn btn-primary w-full"
            onClick={handleGenerate}
            disabled={generating || personen.length === 0}
          >
            {generating ? <><span className="spinner" /> Generiere...</> : '⚡ Automatisch generieren'}
          </button>

          {generating && (
            <div style={{ marginTop: 8 }}>
              <div className="progress-bar-container">
                <div className="progress-bar" style={{ width: `${Math.round(progress * 100)}%` }} />
              </div>
              <small className="text-muted">{Math.round(progress * 100)}%</small>
            </div>
          )}

          {generierungsResult && (
            <div className={`alert ${generierungsResult.erfolgreich ? 'alert-success' : 'alert-warning'}`} style={{ marginTop: 8 }}>
              {generierungsResult.erfolgreich ? '✅ Erfolgreich generiert' : '⚠️ Teilweise generiert'}
              {generierungsResult.warnungen.length > 0 && (
                <ul style={{ marginTop: 4, fontSize: 11, paddingLeft: 14 }}>
                  {generierungsResult.warnungen.slice(0, 5).map((w, i) => (
                    <li key={i}>{w}</li>
                  ))}
                  {generierungsResult.warnungen.length > 5 && <li>...und {generierungsResult.warnungen.length - 5} weitere</li>}
                </ul>
              )}
            </div>
          )}
        </div>

        {/* Dienstplan selector */}
        {dienstplaene.length > 0 && (
          <div className="card">
            <h3 className="card-title">Dienstpläne ({dienstplaene.length})</h3>
            {dienstplaene.map((dp) => (
              <div
                key={dp.id}
                onClick={() => setSelectedDp(dp)}
                style={{
                  padding: '6px 10px',
                  borderRadius: 4,
                  cursor: 'pointer',
                  background: selectedDp?.id === dp.id ? '#e8eaf6' : 'transparent',
                  marginBottom: 4,
                  fontSize: 13
                }}
              >
                <div><strong>{dp.name}</strong></div>
                <div className="text-muted">{dp.status}</div>
              </div>
            ))}
          </div>
        )}

        {/* Wünsche */}
        <div className="card">
          <div className="section-header">
            <h3 className="card-title" style={{ margin: 0 }}>Wünsche ({wuensche.length})</h3>
            <button className="btn btn-sm btn-outline" onClick={() => setShowWunschModal(true)}>+ Neu</button>
          </div>
          <div style={{ maxHeight: 200, overflow: 'auto' }}>
            {wuensche.length === 0 && <div className="text-muted">Keine Wünsche</div>}
            {wuensche.map((w) => (
              <div key={w.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '4px 0', borderBottom: '1px solid #eee', fontSize: 12 }}>
                <div>
                  <strong>{w.personName}</strong> &ndash; {w.datum}
                  <span className="badge" style={{ marginLeft: 4, background: w.typ === WunschTyp.URLAUB ? '#f3e5f5' : w.typ === WunschTyp.FREIWUNSCH ? '#e3f2fd' : '#e8f5e9', color: '#333' }}>
                    {WunschTypInfo[w.typ].kuerzel}
                  </span>
                </div>
                <button className="btn btn-sm btn-danger" onClick={() => deleteWunsch(w.id!)}>✕</button>
              </div>
            ))}
          </div>
        </div>

        {/* Legend */}
        <div className="card">
          <h3 className="card-title">Legende</h3>
          <div className="legend" style={{ flexDirection: 'column' }}>
            <div className="legend-item"><div className="legend-dot" style={{ background: 'var(--dienst-24h)' }} />24h-Dienst</div>
            <div className="legend-item"><div className="legend-dot" style={{ background: 'var(--dienst-visten)' }} />Visitendienst</div>
            <div className="legend-item"><div className="legend-dot" style={{ background: 'var(--dienst-davinci)' }} />DaVinci</div>
            <div className="legend-item"><div className="legend-dot" style={{ background: 'var(--dienst-offen)' }} />Offen</div>
            <div className="legend-item"><div className="legend-dot" style={{ background: 'var(--dienst-urlaub)' }} />Urlaub</div>
          </div>
        </div>
      </div>

      {/* Main calendar area */}
      <div className="dp-main">
        {error && <div className="alert alert-error">{error}</div>}

        {selectedDp ? (
          <>
            <div className="card">
              <div className="section-header">
                <div>
                  <h2 className="card-title" style={{ margin: 0 }}>{selectedDp.name}</h2>
                  <span className={`badge badge-${selectedDp.status.toLowerCase()}`}>
                    {DienstplanStatusInfo[selectedDp.status].vollName}
                  </span>
                </div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <button className="btn btn-primary btn-sm" onClick={handleSaveDienstplan}>💾 Speichern</button>
                  <button className="btn btn-secondary btn-sm" onClick={handleExport}>📥 Excel-Export</button>
                  <button className="btn btn-danger btn-sm" onClick={handleDeleteDp}>🗑</button>
                </div>
              </div>

              {/* Summary bar */}
              <div style={{ display: 'flex', gap: 16, fontSize: 12, marginBottom: 12 }}>
                <span>Gesamt: <strong>{selectedDp.dienste.length}</strong></span>
                <span>Zugewiesen: <strong>{selectedDp.dienste.filter((d) => d.personId).length}</strong></span>
                <span>Offen: <strong>{selectedDp.dienste.filter((d) => !d.personId).length}</strong></span>
              </div>

              {/* Calendar table */}
              <div className="calendar-grid">
                <div className="cal-header">Datum</div>
                <div className="cal-header">24h</div>
                <div className="cal-header">Visten</div>
                <div className="cal-header">DaVinci</div>

                {dates.map((datum) => {
                  const dayDienste = diensteByDate.get(datum) ?? []
                  const dayWuensche = wuenscheByDate.get(datum) ?? []
                  const urlaubPersonen = dayWuensche
                    .filter((w) => w.typ === WunschTyp.URLAUB)
                    .map((w) => w.personName)
                    .join(', ')

                  const get = (art: DienstArt) => dayDienste.find((d) => d.art === art)

                  return (
                    <React.Fragment key={datum}>
                      <div className={`cal-date-cell ${isWeekend(datum) ? 'weekend' : ''}`}>
                        <strong>{datum.slice(8)}</strong>
                        <div className="text-muted">{getWochentagKurz(datum)}</div>
                        {urlaubPersonen && <div style={{ fontSize: 10, color: '#9c27b0' }}>🏖 {urlaubPersonen}</div>}
                      </div>

                      {([DienstArt.DIENST_24H, DienstArt.VISTEN, DienstArt.DAVINCI] as DienstArt[]).map((art) => {
                        const dienst = get(art)
                        if (!dienst) {
                          return <div key={art} style={{ borderBottom: '1px solid var(--color-border)', borderRight: '1px solid var(--color-border)' }} />
                        }
                        return (
                          <div
                            key={art}
                            className={`cal-dienst-cell ${dienst.personId ? artClass(art) : 'dienst-offen'}`}
                            onClick={() => setEditingDienst(dienst)}
                            title={`${DienstArtInfo[art].vollName} – ${dienst.personName ?? 'offen'}`}
                          >
                            <div className="dienst-person">{dienst.personName ?? '– offen –'}</div>
                          </div>
                        )
                      })}
                    </React.Fragment>
                  )
                })}
              </div>
            </div>
          </>
        ) : (
          <div className="card">
            <div className="empty-state">
              <div style={{ fontSize: 48 }}>📅</div>
              <p style={{ marginTop: 8 }}>Kein Dienstplan für {monat} vorhanden.</p>
              <p className="text-muted">Klicke auf "Automatisch generieren" um einen zu erstellen.</p>
            </div>
          </div>
        )}
      </div>

      {/* Edit Dienst Modal */}
      {editingDienst && (
        <div className="modal-overlay" onClick={() => setEditingDienst(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h2 className="modal-title">
              {DienstArtInfo[editingDienst.art].vollName} – {editingDienst.datum}
            </h2>
            <div className="form-group">
              <label className="form-label">Person zuweisen</label>
              <select
                className="form-control"
                value={editingDienst.personId ?? ''}
                onChange={(e) => setEditingDienst({
                  ...editingDienst,
                  personId: e.target.value ? Number(e.target.value) : null,
                  personName: personen.find((p) => p.id === Number(e.target.value))?.name ?? null,
                  status: e.target.value ? DienstStatus.GEPLANT : DienstStatus.ABGESAGT
                })}
              >
                <option value="">– offen –</option>
                {personen.map((p) => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Status</label>
              <select
                className="form-control"
                value={editingDienst.status}
                onChange={(e) => setEditingDienst({ ...editingDienst, status: e.target.value as DienstStatus })}
              >
                {Object.values(DienstStatus).map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Bemerkung</label>
              <input
                className="form-control"
                value={editingDienst.bemerkung ?? ''}
                onChange={(e) => setEditingDienst({ ...editingDienst, bemerkung: e.target.value })}
              />
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={() => setEditingDienst(null)}>Abbrechen</button>
              <button className="btn btn-primary" onClick={() => {
                updateDienst(editingDienst)
                setEditingDienst(null)
              }}>Übernehmen</button>
            </div>
          </div>
        </div>
      )}

      {/* Add Wunsch Modal */}
      {showWunschModal && (
        <div className="modal-overlay" onClick={() => setShowWunschModal(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h2 className="modal-title">Wunsch hinzufügen</h2>
            <div className="form-group">
              <label className="form-label">Person</label>
              <select
                className="form-control"
                value={wunschPersonId}
                onChange={(e) => setWunschPersonId(e.target.value ? Number(e.target.value) : '')}
              >
                <option value="">– wählen –</option>
                {personen.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Datum</label>
              <input
                className="form-control"
                type="date"
                value={wunschDatum}
                onChange={(e) => setWunschDatum(e.target.value)}
              />
            </div>
            <div className="form-group">
              <label className="form-label">Typ</label>
              <select
                className="form-control"
                value={wunschTyp}
                onChange={(e) => setWunschTyp(e.target.value as WunschTyp)}
              >
                {Object.values(WunschTyp).map((t) => (
                  <option key={t} value={t}>{WunschTypInfo[t].vollName}</option>
                ))}
              </select>
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={() => setShowWunschModal(false)}>Abbrechen</button>
              <button className="btn btn-primary" onClick={addWunsch} disabled={!wunschPersonId || !wunschDatum}>
                Hinzufügen
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
