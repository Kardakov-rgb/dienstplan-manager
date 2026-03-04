import React, { useEffect, useState } from 'react'
import {
  Person, DienstArt, Wochentag,
  DienstArtInfo, WochentagInfo
} from '../../../shared/types'

const ALL_WOCHENTAGE = Object.values(Wochentag)
const ALL_DIENSTART = Object.values(DienstArt)

const emptyPerson = (): Omit<Person, 'id'> => ({
  name: '',
  anzahlDienste: 0,
  arbeitsTage: [...ALL_WOCHENTAGE],
  verfuegbareDienstArten: [...ALL_DIENSTART]
})

export default function Personenverwaltung(): React.ReactElement {
  const [personen, setPersonen] = useState<Person[]>([])
  const [selected, setSelected] = useState<Person | null>(null)
  const [formData, setFormData] = useState<Omit<Person, 'id'>>(emptyPerson())
  const [isNew, setIsNew] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [search, setSearch] = useState('')

  useEffect(() => { loadPersonen() }, [])

  const loadPersonen = async () => {
    const data = await window.api.getPersonen()
    setPersonen(data)
  }

  const filtered = personen.filter((p) =>
    p.name.toLowerCase().includes(search.toLowerCase())
  )

  const selectPerson = (p: Person) => {
    setSelected(p)
    setFormData({
      name: p.name,
      anzahlDienste: p.anzahlDienste,
      arbeitsTage: [...p.arbeitsTage],
      verfuegbareDienstArten: [...p.verfuegbareDienstArten]
    })
    setIsNew(false)
    setError(null)
    setSuccess(null)
  }

  const startNew = () => {
    setSelected(null)
    setFormData(emptyPerson())
    setIsNew(true)
    setError(null)
    setSuccess(null)
  }

  const handleSave = async () => {
    if (!formData.name.trim()) { setError('Name ist erforderlich.'); return }
    setSaving(true)
    setError(null)
    setSuccess(null)
    try {
      if (isNew) {
        await window.api.createPerson(formData)
        setSuccess('Person wurde erstellt.')
        setIsNew(false)
        setFormData(emptyPerson())
      } else if (selected) {
        await window.api.updatePerson({ ...formData, id: selected.id })
        setSuccess('Person wurde gespeichert.')
      }
      await loadPersonen()
    } catch (e: unknown) {
      setError(String(e))
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (!selected) return
    if (!confirm(`Person "${selected.name}" wirklich löschen?`)) return
    try {
      await window.api.deletePerson(selected.id)
      setSelected(null)
      setFormData(emptyPerson())
      setIsNew(false)
      await loadPersonen()
    } catch (e: unknown) {
      setError(String(e))
    }
  }

  const toggleWochentag = (w: Wochentag) => {
    setFormData((prev) => ({
      ...prev,
      arbeitsTage: prev.arbeitsTage.includes(w)
        ? prev.arbeitsTage.filter((x) => x !== w)
        : [...prev.arbeitsTage, w]
    }))
  }

  const toggleDienstArt = (a: DienstArt) => {
    setFormData((prev) => ({
      ...prev,
      verfuegbareDienstArten: prev.verfuegbareDienstArten.includes(a)
        ? prev.verfuegbareDienstArten.filter((x) => x !== a)
        : [...prev.verfuegbareDienstArten, a]
    }))
  }

  return (
    <div className="personen-layout">
      {/* Left: Table */}
      <div className="personen-table-section card" style={{ overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
        <div className="section-header">
          <h2 className="card-title" style={{ margin: 0 }}>Personen ({personen.length})</h2>
          <button className="btn btn-primary btn-sm" onClick={startNew}>+ Neu</button>
        </div>
        <input
          className="form-control"
          placeholder="Suchen..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{ marginBottom: 10 }}
        />
        <div className="table-container" style={{ flex: 1, overflow: 'auto' }}>
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Soll-Dienste</th>
                <th>Dienstarten</th>
              </tr>
            </thead>
            <tbody>
              {filtered.length === 0 && (
                <tr><td colSpan={3} className="empty-state">Keine Personen gefunden</td></tr>
              )}
              {filtered.map((p) => (
                <tr
                  key={p.id}
                  onClick={() => selectPerson(p)}
                  style={{
                    cursor: 'pointer',
                    background: selected?.id === p.id ? '#e8eaf6' : undefined
                  }}
                >
                  <td><strong>{p.name}</strong></td>
                  <td>{p.anzahlDienste || '–'}</td>
                  <td>{p.verfuegbareDienstArten.map((a) => DienstArtInfo[a].kurzName).join(', ') || '–'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Right: Form */}
      <div className="personen-form-section">
        {(selected || isNew) ? (
          <div className="card">
            <h2 className="card-title">{isNew ? 'Neue Person' : `Bearbeiten: ${selected?.name}`}</h2>

            {error && <div className="alert alert-error">{error}</div>}
            {success && <div className="alert alert-success">{success}</div>}

            <div className="form-group">
              <label className="form-label">Name *</label>
              <input
                className="form-control"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                placeholder="Vollständiger Name"
              />
            </div>

            <div className="form-group">
              <label className="form-label">Soll-Dienste pro Monat</label>
              <input
                className="form-control"
                type="number"
                min={0}
                max={31}
                value={formData.anzahlDienste}
                onChange={(e) => setFormData({ ...formData, anzahlDienste: parseInt(e.target.value) || 0 })}
              />
              <small className="text-muted">0 = unbegrenzt</small>
            </div>

            <div className="form-group">
              <label className="form-label">Arbeitstage</label>
              <div className="checkbox-group">
                {ALL_WOCHENTAGE.map((w) => (
                  <label key={w} className="checkbox-item">
                    <input
                      type="checkbox"
                      checked={formData.arbeitsTage.includes(w)}
                      onChange={() => toggleWochentag(w)}
                    />
                    {WochentagInfo[w].kurzName}
                  </label>
                ))}
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Verfügbare Dienstarten</label>
              <div className="checkbox-group">
                {ALL_DIENSTART.map((a) => (
                  <label key={a} className="checkbox-item">
                    <input
                      type="checkbox"
                      checked={formData.verfuegbareDienstArten.includes(a)}
                      onChange={() => toggleDienstArt(a)}
                    />
                    {DienstArtInfo[a].vollName}
                  </label>
                ))}
              </div>
            </div>

            <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
              <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
                {saving ? <><span className="spinner" /> Speichern...</> : '💾 Speichern'}
              </button>
              {!isNew && (
                <button className="btn btn-danger" onClick={handleDelete}>🗑 Löschen</button>
              )}
              <button className="btn btn-secondary" onClick={() => { setSelected(null); setIsNew(false) }}>
                Abbrechen
              </button>
            </div>
          </div>
        ) : (
          <div className="card">
            <div className="empty-state">
              <div style={{ fontSize: 40 }}>👤</div>
              <p style={{ marginTop: 8 }}>Person auswählen oder neu erstellen</p>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
