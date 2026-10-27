import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { createFarmer, listFarmers } from './api'
import type { CreateFarmerRequest, FarmerPage } from './api'

const emptyForm: CreateFarmerRequest = {
  fullName: '',
  phone: '',
  district: '',
  village: '',
}

function errorMessage(error: unknown): string {
  return error instanceof Error
    ? error.message
    : 'Something went wrong. Please try again.'
}

function initials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase()
}

function displayDate(value: string): string {
  const date = new Date(value)

  return Number.isNaN(date.getTime())
    ? '—'
    : new Intl.DateTimeFormat('en-GB', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
      }).format(date)
}

export default function App() {
  const [filters, setFilters] = useState({
    query: '',
    district: '',
    page: 0,
    revision: 0,
  })

  const [queryInput, setQueryInput] = useState('')
  const [districtInput, setDistrictInput] = useState('')
  const [data, setData] = useState<FarmerPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [form, setForm] = useState<CreateFarmerRequest>(emptyForm)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState('')
  const [success, setSuccess] = useState('')

  useEffect(() => {
    const controller = new AbortController()

    listFarmers(
      filters.page,
      filters.query,
      filters.district,
      controller.signal,
    )
      .then((result) => {
        if (controller.signal.aborted) return
        setData(result)
        setListError('')
        setLoading(false)
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        setListError(errorMessage(error))
        setLoading(false)
      })

    return () => controller.abort()
  }, [filters])

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setLoading(true)
    setListError('')

    setFilters((current) => ({
      query: queryInput.trim(),
      district: districtInput.trim(),
      page: 0,
      revision: current.revision + 1,
    }))
  }

  function refresh() {
    setLoading(true)
    setListError('')
    setFilters((current) => ({
      ...current,
      revision: current.revision + 1,
    }))
  }

  function changePage(page: number) {
    setLoading(true)
    setListError('')
    setFilters((current) => ({ ...current, page }))
  }

  function updateField(field: keyof CreateFarmerRequest, value: string) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  async function saveFarmer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (saving) return

    setSaving(true)
    setSaveError('')
    setSuccess('')

    try {
      const created = await createFarmer({
        fullName: form.fullName.trim(),
        phone: form.phone.trim(),
        district: form.district.trim(),
        village: form.village.trim(),
      })

      setSuccess(`${created.fullName} was added successfully.`)
      setForm(emptyForm)
      setQueryInput('')
      setDistrictInput('')
      setLoading(true)
      setListError('')

      setFilters((current) => ({
        query: '',
        district: '',
        page: 0,
        revision: current.revision + 1,
      }))
    } catch (error: unknown) {
      setSaveError(errorMessage(error))
    } finally {
      setSaving(false)
    }
  }

  const showResults = !loading && !listError && data !== null
  const activeOnPage = data?.content.filter((farmer) => farmer.active).length

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="/" aria-label="GoviLink home">
          <span className="brand-mark">G</span>
          <span>GoviLink<span className="brand-dot">.</span></span>
        </a>

        <p className="sidebar-label">WORKSPACE</p>
        <nav aria-label="Main navigation">
          <a className="nav-link selected" href="/" aria-current="page">
            <span aria-hidden="true">◈</span>
            Farmers
          </a>
        </nav>

        <div className="sidebar-note">
          <span className="small-leaf" aria-hidden="true">↗</span>
          <strong>Better connections.<br />Better coordination.</strong>
          <p>Your farmer directory is the first step.</p>
        </div>

        <div className="sidebar-footer">
          <span className="status-dot" />
          Local development
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <span>Workspace <span className="breadcrumb">/ Farmers</span></span>
          <span className="workspace-badge">Development workspace</span>
        </header>

        <section className="page-heading">
          <div>
            <p className="eyebrow">GROW YOUR NETWORK</p>
            <h1>Farmer directory</h1>
            <p className="subtitle">
              Keep farmer details organised and find the right contact.
            </p>
          </div>

          <button
            className="button button-secondary"
            type="button"
            onClick={refresh}
            disabled={loading}
          >
            {loading ? 'Loading…' : 'Refresh directory'}
          </button>
        </section>

        <section className="stats-grid" aria-label="Directory summary">
          <article className="stat-card">
            <span>Matching farmers</span>
            <strong>{showResults ? data.totalElements : '—'}</strong>
            <small>Across all matching pages</small>
          </article>

          <article className="stat-card">
            <span>Active on this page</span>
            <strong>{showResults ? activeOnPage : '—'}</strong>
            <small>Active farmer records shown below</small>
          </article>

          <article className="stat-card stat-accent">
            <span>Directory page</span>
            <strong>
              {showResults
                ? `${data.totalPages === 0 ? 0 : data.page + 1} / ${data.totalPages}`
                : '—'}
            </strong>
            <small>Up to 10 farmers per page</small>
          </article>
        </section>

        {success && (
          <div className="notice notice-success" role="status">
            {success}
          </div>
        )}

        <div className="workspace-grid">
          <section className="panel directory-panel" aria-labelledby="directory-title">
            <div className="panel-heading">
              <div>
                <h2 id="directory-title">Your farmer network</h2>
                <p>Search by name, phone or village.</p>
              </div>
            </div>

            <form className="search-form" onSubmit={search}>
              <div className="field search-field">
                <label htmlFor="search">Search directory</label>
                <input
                  id="search"
                  value={queryInput}
                  onChange={(event) => setQueryInput(event.target.value)}
                  placeholder="Name, phone or village"
                  maxLength={150}
                />
              </div>

              <div className="field district-filter">
                <label htmlFor="district-filter">District</label>
                <input
                  id="district-filter"
                  value={districtInput}
                  onChange={(event) => setDistrictInput(event.target.value)}
                  placeholder="All districts"
                  maxLength={100}
                />
              </div>

              <button className="button button-primary" type="submit">
                Search
              </button>
            </form>

            {listError && (
              <div className="notice notice-error directory-error" role="alert">
                <strong>Could not load the directory.</strong>
                <span>{listError}</span>
                <button className="text-button" onClick={refresh} type="button">
                  Try again
                </button>
              </div>
            )}

            <div className="table-wrap" aria-busy={loading}>
              <table>
                <thead>
                  <tr>
                    <th scope="col">Farmer</th>
                    <th scope="col">Location</th>
                    <th scope="col">Status</th>
                    <th scope="col">Added</th>
                  </tr>
                </thead>
                <tbody>
                  {loading && (
                    <tr>
                      <td colSpan={4} className="table-message" role="status">
                        Loading your farmer network…
                      </td>
                    </tr>
                  )}

                  {showResults && data.content.length === 0 && (
                    <tr>
                      <td colSpan={4} className="table-message">
                        <strong>No farmers found</strong>
                        <span>Try another search or add your first farmer.</span>
                      </td>
                    </tr>
                  )}

                  {showResults && data.content.map((farmer) => (
                    <tr key={farmer.id}>
                      <td>
                        <div className="farmer-cell">
                          <span className="avatar" aria-hidden="true">
                            {initials(farmer.fullName)}
                          </span>
                          <div>
                            <strong>{farmer.fullName}</strong>
                            <span className="cell-secondary">{farmer.phone}</span>
                          </div>
                        </div>
                      </td>
                      <td>
                        <strong className="location-name">{farmer.district}</strong>
                        <span className="cell-secondary">{farmer.village || '—'}</span>
                      </td>
                      <td>
                        <span className={`status-badge ${farmer.active ? 'active' : 'inactive'}`}>
                          {farmer.active ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                      <td className="date-cell">{displayDate(farmer.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <footer className="pagination">
              <span>
                {showResults
                  ? `${data.content.length} of ${data.totalElements} matching farmers`
                  : 'Directory results'}
              </span>
              <div className="page-buttons">
                <button
                  className="button button-secondary button-small"
                  type="button"
                  disabled={!showResults || !data.hasPrevious}
                  onClick={() => changePage(filters.page - 1)}
                >
                  Previous
                </button>
                <button
                  className="button button-secondary button-small"
                  type="button"
                  disabled={!showResults || !data.hasNext}
                  onClick={() => changePage(filters.page + 1)}
                >
                  Next
                </button>
              </div>
            </footer>
          </section>

          <section className="panel add-panel" aria-labelledby="add-title">
            <div className="panel-heading">
              <div>
                <span className="form-icon" aria-hidden="true">+</span>
                <h2 id="add-title">Add a farmer</h2>
                <p>Start with the essential contact details.</p>
              </div>
            </div>

            <form className="farmer-form" onSubmit={saveFarmer}>
              <fieldset disabled={saving}>
                <div className="field">
                  <label htmlFor="full-name">Full name *</label>
                  <input
                    id="full-name"
                    value={form.fullName}
                    onChange={(event) => updateField('fullName', event.target.value)}
                    placeholder="Farmer’s full name"
                    autoComplete="name"
                    required
                    maxLength={150}
                  />
                </div>

                <div className="field">
                  <label htmlFor="phone">Phone *</label>
                  <input
                    id="phone"
                    type="tel"
                    value={form.phone}
                    onChange={(event) => updateField('phone', event.target.value)}
                    placeholder="Contact number"
                    autoComplete="tel"
                    required
                    maxLength={20}
                  />
                </div>

                <div className="field">
                  <label htmlFor="district">District *</label>
                  <input
                    id="district"
                    value={form.district}
                    onChange={(event) => updateField('district', event.target.value)}
                    placeholder="e.g. Matale"
                    required
                    maxLength={100}
                  />
                </div>

                <div className="field">
                  <label htmlFor="village">Village <span>optional</span></label>
                  <input
                    id="village"
                    value={form.village}
                    onChange={(event) => updateField('village', event.target.value)}
                    placeholder="Village or locality"
                    maxLength={150}
                  />
                </div>

                {saveError && (
                  <div className="notice notice-error" role="alert">{saveError}</div>
                )}

                <button className="button button-primary save-button" type="submit">
                  {saving ? 'Saving farmer…' : 'Add farmer'}
                </button>
              </fieldset>
              <p className="form-note">
                Use fictional contact details while testing.
              </p>
            </form>
          </section>
        </div>

        <footer className="page-footer">
          <span>GoviLink · Farmer collection & sales coordination</span>
          <span>Portfolio project · In development</span>
        </footer>
      </main>
    </div>
  )
}