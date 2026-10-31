import { useState } from 'react'
import FarmerDirectory from './FarmerDirectory'
import HarvestListings from './HarvestListings'
import BuyerRequests from './BuyerRequests'
import './HarvestListings.css'

type Page = 'farmers' | 'harvests' | 'buyers'

const navigation: {
  id: Page
  label: string
  icon: string
}[] = [
  { id: 'farmers', label: 'Farmers', icon: '◈' },
  { id: 'harvests', label: 'Harvest listings', icon: '↗' },
  { id: 'buyers', label: 'Buyer requests', icon: '◇' },
]

export default function App() {
  const [page, setPage] = useState<Page>('farmers')
  const [visited, setVisited] = useState<Record<Page, boolean>>({
    farmers: true,
    harvests: false,
    buyers: false,
  })

  function navigate(next: Page) {
    setVisited((current) => current[next]
      ? current
      : { ...current, [next]: true })

    setPage(next)
  }

  const currentLabel = navigation.find((item) => item.id === page)?.label

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="/" aria-label="GoviLink home">
          <span className="brand-mark">G</span>
          <span>GoviLink<span className="brand-dot">.</span></span>
        </a>

        <p className="sidebar-label">WORKSPACE</p>

        <nav className="workspace-nav" aria-label="Main navigation">
          {navigation.map((item) => (
            <button
              key={item.id}
              className={`nav-link ${page === item.id ? 'selected' : ''}`}
              type="button"
              aria-current={page === item.id ? 'page' : undefined}
              onClick={() => navigate(item.id)}
            >
              <span aria-hidden="true">{item.icon}</span>
              {item.label}
            </button>
          ))}
        </nav>

        <div className="sidebar-note">
          <span className="small-leaf" aria-hidden="true">↗</span>
          <strong>Better connections.<br />Better coordination.</strong>
          <p>Keep farmer contacts, harvests and buyer demand together.</p>
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
            <span className="breadcrumb">/ {currentLabel}</span>
          </span>
          <span className="workspace-badge">Development workspace</span>
        </header>

        <div hidden={page !== 'farmers'}>
          <FarmerDirectory />
        </div>

        {visited.harvests && (
          <div hidden={page !== 'harvests'}>
            <HarvestListings />
          </div>
        )}

        {visited.buyers && (
          <div hidden={page !== 'buyers'}>
            <BuyerRequests />
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