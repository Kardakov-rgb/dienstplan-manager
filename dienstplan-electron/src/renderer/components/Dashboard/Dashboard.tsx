import React, { useEffect, useState } from 'react'

interface Props {
  onNavigate: (tab: 'personen' | 'dienstplan' | 'statistiken') => void
}

export default function Dashboard({ onNavigate }: Props): React.ReactElement {
  const [personCount, setPersonCount] = useState<number>(0)
  const [dienstplanCount, setDienstplanCount] = useState<number>(0)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const load = async () => {
      try {
        const personen = await window.api.getPersonen()
        setPersonCount(personen.length)
        const dienstplaene = await window.api.getDienstplaene()
        setDienstplanCount(dienstplaene.length)
      } catch (err) {
        console.error('Dashboard load error:', err)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  const currentMonth = () => {
    const now = new Date()
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  }

  return (
    <div>
      <div className="card">
        <h2 className="card-title">Übersicht</h2>

        {loading ? (
          <div className="empty-state">Lade Daten...</div>
        ) : (
          <div className="dashboard-grid">
            <div className="stat-card">
              <div className="stat-value">{personCount}</div>
              <div className="stat-label">Personen</div>
            </div>
            <div className="stat-card accent">
              <div className="stat-value">{dienstplanCount}</div>
              <div className="stat-label">Dienstpläne</div>
            </div>
            <div className="stat-card success">
              <div className="stat-value">{currentMonth()}</div>
              <div className="stat-label">Aktueller Monat</div>
            </div>
          </div>
        )}
      </div>

      <div className="card">
        <h2 className="card-title">Schnellzugriff</h2>
        <div className="quick-action-grid">
          <button className="btn btn-primary w-full" onClick={() => onNavigate('personen')}>
            👤 Personen verwalten
          </button>
          <button className="btn btn-accent w-full" onClick={() => onNavigate('dienstplan')}>
            📅 Dienstplan erstellen
          </button>
          <button className="btn btn-secondary w-full" onClick={() => onNavigate('statistiken')}>
            📊 Statistiken anzeigen
          </button>
        </div>
      </div>

      <div className="card">
        <h2 className="card-title">Info</h2>
        <div className="alert alert-info">
          Willkommen beim Dienstplan-Manager. Verwalten Sie Personen, erstellen Sie automatisch optimierte
          Dienstpläne und analysieren Sie Statistiken zur Wunscherfüllung und Fairness.
        </div>
        <div className="text-muted mt-8">
          <strong>Dienstarten:</strong> 24h-Dienste (täglich), Visitendienste (Sa+So), DaVinci (Freitag)
        </div>
      </div>
    </div>
  )
}
