import React, { useState } from 'react'
import Dashboard from './components/Dashboard/Dashboard'
import Personenverwaltung from './components/Personenverwaltung/Personenverwaltung'
import Dienstplanerstellung from './components/Dienstplanerstellung/Dienstplanerstellung'
import Statistiken from './components/Statistiken/Statistiken'

type Tab = 'dashboard' | 'personen' | 'dienstplan' | 'statistiken'

export default function App(): React.ReactElement {
  const [activeTab, setActiveTab] = useState<Tab>('dashboard')

  return (
    <div className="app">
      <header className="app-header">
        <div className="app-title">
          <span className="app-logo">📋</span>
          <h1>Dienstplan-Manager</h1>
        </div>
        <nav className="app-nav">
          <button
            className={`nav-btn ${activeTab === 'dashboard' ? 'active' : ''}`}
            onClick={() => setActiveTab('dashboard')}
          >
            Dashboard
          </button>
          <button
            className={`nav-btn ${activeTab === 'personen' ? 'active' : ''}`}
            onClick={() => setActiveTab('personen')}
          >
            Personen
          </button>
          <button
            className={`nav-btn ${activeTab === 'dienstplan' ? 'active' : ''}`}
            onClick={() => setActiveTab('dienstplan')}
          >
            Dienstplanerstellung
          </button>
          <button
            className={`nav-btn ${activeTab === 'statistiken' ? 'active' : ''}`}
            onClick={() => setActiveTab('statistiken')}
          >
            Statistiken
          </button>
        </nav>
      </header>

      <main className="app-content">
        {activeTab === 'dashboard' && <Dashboard onNavigate={setActiveTab} />}
        {activeTab === 'personen' && <Personenverwaltung />}
        {activeTab === 'dienstplan' && <Dienstplanerstellung />}
        {activeTab === 'statistiken' && <Statistiken />}
      </main>

      <footer className="app-footer">
        <span>Dienstplan-Manager v1.0.0</span>
      </footer>
    </div>
  )
}
