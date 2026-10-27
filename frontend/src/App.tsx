import { useState } from 'react'
import FarmerDirectory from './FarmerDirectory'
import HarvestListings from './HarvestListings'
import './HarvestListings.css'

type Page = 'farmers' | 'harvests'

export default function App() {
  const [page, setPage] = useState<Page>('farmers')
  const [harvestVisited, setHarvestVisited] = useState(false)

  function navigate(next: Page) {
    if (next === 'harvests') setHarvestVisited(true)
    setPage(next)
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="/" aria-label="GoviLink home">
          <span className="brand-mark">G</span>
          <span>GoviLink<span className="brand-dot">.</span></span>
        </a>

        <p className="sidebar-label">WORKSPACE</p>

        <nav className="workspace-nav" aria-label="Main navigation">
          <button
            className={`nav-link ${page === 'farmers' ? 'selected' : ''}`}
            type="button"
            aria-current={page === 'farmers' ? 'page' : undefined}
            onClick={() => navigate('farmers')}
          >
            <span aria-hidden="true">◈</span>
            Farmers
          </button>

          <button
            className={`nav-link ${page === 'harvests' ? 'selected' : ''}`}
            type="button"
            aria-current={page === 'harvests' ? 'page' : undefined}
            onClick={() => navigate('harvests')}
          >
            <span aria-hidden="true">↗</span>
            Harvest listings
          </button>
        </nav>

        <div className="sidebar-note">
          <span className="small-leaf" aria-hidden="true">↗</span>
          <strong>Better connections.<br />Better coordination.</strong>
          <p>Keep farmer contacts and available harvests together.</p>
        </div>

        <div className="sidebar-footer">
          <span className="status-dot" />
          Local development
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <span>
            Workspace
            <span className="breadcrumb">
              / {page === 'farmers' ? 'Farmers' : 'Harvest listings'}
            </span>
          </span>
          <span className="workspace-badge">Development workspace</span>
        </header>

        <div hidden={page !== 'farmers'}>
          <FarmerDirectory />
        </div>

        {harvestVisited && (
          <div hidden={page !== 'harvests'}>
            <HarvestListings />
          </div>
        )}

        <footer className="page-footer">
          <span>GoviLink · Farmer collection & sales coordination</span>
          <span>Portfolio project · In development</span>
        </footer>
      </main>
    </div>
  )
}