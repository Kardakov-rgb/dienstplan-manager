import React, { useEffect, useState } from 'react'
import { GesamtStatistik, FairnessScore, DienstArtInfo, DienstArt } from '../../../shared/types'

function currentMonat(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function sixMonthsAgo(): string {
  const now = new Date()
  now.setMonth(now.getMonth() - 5)
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

export default function Statistiken(): React.ReactElement {
  const [monatVon, setMonatVon] = useState(sixMonthsAgo())
  const [monatBis, setMonatBis] = useState(currentMonat())
  const [statistik, setStatistik] = useState<GesamtStatistik | null>(null)
  const [fairnessScores, setFairnessScores] = useState<FairnessScore[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => { loadData() }, [monatVon, monatBis])

  const loadData = async () => {
    setLoading(true)
    setError(null)
    try {
      const [stat, scores] = await Promise.all([
        window.api.getGesamtStatistik(monatVon, monatBis),
        window.api.getFairnessScores()
      ])
      setStatistik(stat)
      setFairnessScores(scores)
    } catch (e: unknown) {
      setError(String(e))
    } finally {
      setLoading(false)
    }
  }

  const handleExport = async () => {
    const path = await window.api.showSaveDialog(`Statistiken-${monatVon}-${monatBis}.xlsx`)
    if (!path) return
    await window.api.exportStatistiken(monatVon, monatBis, path)
  }

  const pct = (v: number) => `${(v * 100).toFixed(1)}%`

  return (
    <div className="statistiken-layout">
      {/* Sidebar: filters */}
      <div className="statistiken-sidebar">
        <div className="card">
          <h3 className="card-title">Zeitraum</h3>
          <div className="form-group">
            <label className="form-label">Von</label>
            <input className="form-control" type="month" value={monatVon} onChange={(e) => setMonatVon(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label">Bis</label>
            <input className="form-control" type="month" value={monatBis} onChange={(e) => setMonatBis(e.target.value)} />
          </div>
          <button className="btn btn-primary w-full" onClick={loadData}>🔄 Aktualisieren</button>
          <button className="btn btn-secondary w-full" style={{ marginTop: 8 }} onClick={handleExport} disabled={!statistik}>
            📥 Excel-Export
          </button>
        </div>

        {/* Fairness overview */}
        {fairnessScores.length > 0 && (
          <div className="card">
            <h3 className="card-title">Fairness-Scores</h3>
            {fairnessScores.map((s) => (
              <div key={s.personId} style={{ marginBottom: 10 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
                  <span><strong>{s.personName}</strong></span>
                  <span>{pct(s.durchschnittlicheErfuellung)}</span>
                </div>
                <div className="progress-bar-container">
                  <div
                    className={`progress-bar ${s.durchschnittlicheErfuellung < 0.7 ? 'accent' : 'success'}`}
                    style={{ width: pct(s.durchschnittlicheErfuellung) }}
                  />
                </div>
                <div className="text-muted">{s.erfuellteWuensche}/{s.gesamtWuensche} Wünsche ({s.anzahlMonate} Monate)</div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Main content */}
      <div className="statistiken-main">
        {error && <div className="alert alert-error">{error}</div>}

        {loading ? (
          <div className="card"><div className="empty-state">Lade Statistiken...</div></div>
        ) : statistik ? (
          <>
            {/* Overview cards */}
            <div className="dashboard-grid">
              <div className="stat-card">
                <div className="stat-value">{statistik.gesamtDienste}</div>
                <div className="stat-label">Gesamt Dienste</div>
              </div>
              <div className="stat-card success">
                <div className="stat-value">{statistik.zugewieseneDienste}</div>
                <div className="stat-label">Zugewiesen</div>
              </div>
              <div className="stat-card warning">
                <div className="stat-value">{statistik.offeneDienste}</div>
                <div className="stat-label">Offen</div>
              </div>
              <div className="stat-card accent">
                <div className="stat-value">{pct(statistik.zuweisungsgrad)}</div>
                <div className="stat-label">Zuweisungsgrad</div>
              </div>
              <div className="stat-card">
                <div className="stat-value">{pct(statistik.wunschErfuellungsQuote)}</div>
                <div className="stat-label">Wunscherfüllung</div>
              </div>
              <div className="stat-card warning">
                <div className="stat-value">{statistik.konflikte.length}</div>
                <div className="stat-label">Konflikte</div>
              </div>
            </div>

            {/* Person distribution */}
            <div className="card">
              <h2 className="card-title">Dienst-Verteilung pro Person</h2>
              <div className="table-container">
                <table>
                  <thead>
                    <tr>
                      <th>Person</th>
                      <th>Soll</th>
                      <th>Ist</th>
                      <th>24h</th>
                      <th>Visten</th>
                      <th>DaVinci</th>
                      <th>Erfüllung</th>
                    </tr>
                  </thead>
                  <tbody>
                    {statistik.personStatistiken.map((p) => {
                      const erfuellung = p.sollDienste > 0 ? p.istDienste / p.sollDienste : null
                      return (
                        <tr key={p.personId}>
                          <td><strong>{p.personName}</strong></td>
                          <td>{p.sollDienste || '–'}</td>
                          <td>{p.istDienste}</td>
                          <td>{p.dienste24h}</td>
                          <td>{p.diensteVisten}</td>
                          <td>{p.diensteDAVinci}</td>
                          <td>
                            {erfuellung !== null ? (
                              <div>
                                <div className="progress-bar-container" style={{ width: 80 }}>
                                  <div
                                    className={`progress-bar ${erfuellung >= 1 ? 'success' : erfuellung >= 0.8 ? '' : 'accent'}`}
                                    style={{ width: `${Math.min(erfuellung * 100, 100)}%` }}
                                  />
                                </div>
                                <small className="text-muted">{pct(erfuellung)}</small>
                              </div>
                            ) : '–'}
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Open shifts */}
            {statistik.offeneDiensteListe.length > 0 && (
              <div className="card">
                <h2 className="card-title">Offene Dienste ({statistik.offeneDiensteListe.length})</h2>
                <div className="table-container">
                  <table>
                    <thead>
                      <tr><th>Datum</th><th>Dienstart</th><th>Dienstplan</th></tr>
                    </thead>
                    <tbody>
                      {statistik.offeneDiensteListe.map((d, i) => (
                        <tr key={i}>
                          <td>{d.datum}</td>
                          <td>{DienstArtInfo[d.dienstArt]?.vollName ?? d.dienstArt}</td>
                          <td>{d.dienstplanName}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {/* Conflicts */}
            {statistik.konflikte.length > 0 && (
              <div className="card">
                <h2 className="card-title">Konflikte ({statistik.konflikte.length})</h2>
                <div className="table-container">
                  <table>
                    <thead>
                      <tr><th>Person</th><th>Datum</th><th>Dienste</th></tr>
                    </thead>
                    <tbody>
                      {statistik.konflikte.map((k, i) => (
                        <tr key={i}>
                          <td><strong>{k.personName}</strong></td>
                          <td>{k.datum}</td>
                          <td>{k.dienste.join(', ')}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </>
        ) : (
          <div className="card">
            <div className="empty-state">
              <div style={{ fontSize: 48 }}>📊</div>
              <p>Keine Daten für den gewählten Zeitraum</p>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
