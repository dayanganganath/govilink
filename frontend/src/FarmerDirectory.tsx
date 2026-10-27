import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { createFarmer, listFarmers } from './api'
import type { CreateFarmerRequest, Farmer, FarmerPage } from './api'
import FarmerDetailsPanel from './FarmerDetailsPanel'

const emptyForm: CreateFarmerRequest = {
  fullName: '',
  phone: '',
  district: '',
  village: '',
}

const fields: {
  name: keyof CreateFarmerRequest
  label: string
  maxLength: number
  required: boolean
}[] = [
  { name: 'fullName', label: 'Full name', maxLength: 150, required: true },
  { name: 'phone', label: 'Phone', maxLength: 20, required: true },
  { name: 'district', label: 'District', maxLength: 100, required: true },
  { name: 'village', label: 'Village', maxLength: 150, required: false },
]

function message(error: unknown): string {
  return error instanceof Error ? error.message : 'The request failed.'
}

function displayDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? '—'
    : date.toLocaleDateString('en-GB')
}

export default function FarmerDirectory() {
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
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const savingRef = useRef(false)

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
        setListError(message(error))
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

  function refresh(page = filters.page) {
    setLoading(true)
    setListError('')
    setFilters((current) => ({
      ...current,
      page,
      revision: current.revision + 1,
    }))
  }

  function handleUpdated(farmer: Farmer) {
    setSuccess(`${farmer.fullName} was updated successfully.`)
    refresh(0)
  }

  async function saveFarmer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (savingRef.current) return

    savingRef.current = true
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
      setSaveError(message(error))
    } finally {
      savingRef.current = false
      setSaving(false)
    }
  }

  const ready = !loading && !listError && data !== null

  return (
    <>
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
          onClick={() => refresh()}
          disabled={loading}
        >
          {loading ? 'Loading…' : 'Refresh directory'}
        </button>
      </section>

      <section className="stats-grid" aria-label="Directory summary">
        <article className="stat-card">
          <span>Matching farmers</span>
          <strong>{ready ? data.totalElements : '—'}</strong>
          <small>Across all matching pages</small>
        </article>
        <article className="stat-card">
          <span>Active on this page</span>
          <strong>
            {ready ? data.content.filter((farmer) => farmer.active).length : '—'}
          </strong>
          <small>Active farmer records shown below</small>
        </article>
        <article className="stat-card stat-accent">
          <span>Directory page</span>
          <strong>
            {ready
              ? `${data.totalPages === 0 ? 0 : data.page + 1} / ${data.totalPages}`
              : '—'}
          </strong>
          <small>Up to 10 farmers per page</small>
        </article>
      </section>

      {success && (
        <div className="notice notice-success" role="status">{success}</div>
      )}

      <div className="workspace-grid">
        <section className="panel" aria-labelledby="directory-title">
          <div className="panel-heading">
            <h2 id="directory-title">Your farmer network</h2>
            <p>Search by name, phone or village.</p>
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
              {listError}
              <button
                className="text-button"
                type="button"
                onClick={() => refresh()}
              >
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
                  <th scope="col">Actions</th>
                </tr>
              </thead>
              <tbody>
                {loading && (
                  <tr>
                    <td colSpan={5} className="table-message" role="status">
                      Loading your farmer network…
                    </td>
                  </tr>
                )}

                {ready && data.content.length === 0 && (
                  <tr>
                    <td colSpan={5} className="table-message">
                      <strong>No farmers found</strong>
                      <span>Try another search or add your first farmer.</span>
                    </td>
                  </tr>
                )}

                {ready && data.content.map((farmer) => (
                  <tr key={farmer.id}>
                    <td>
                      <div className="farmer-cell">
                        <span className="avatar" aria-hidden="true">
                          {farmer.fullName.trim().split(/\s+/).slice(0, 2)
                            .map((part) => part[0]).join('').toUpperCase()}
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
                      <span
                        className={`status-badge ${
                          farmer.active ? 'active' : 'inactive'
                        }`}
                      >
                        {farmer.active ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="date-cell">{displayDate(farmer.createdAt)}</td>
                    <td>
                      <button
                        className="button button-secondary button-small"
                        type="button"
                        onClick={() => setSelectedId(farmer.id)}
                        aria-label={`View and edit ${farmer.fullName}`}
                      >
                        View / edit
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <footer className="pagination">
            <span>
              {ready
                ? `${data.content.length} of ${data.totalElements} matching farmers`
                : 'Directory results'}
            </span>
            <div className="page-buttons">
              <button
                className="button button-secondary button-small"
                type="button"
                disabled={!ready || !data.hasPrevious}
                onClick={() => refresh(filters.page - 1)}
              >
                Previous
              </button>
              <button
                className="button button-secondary button-small"
                type="button"
                disabled={!ready || !data.hasNext}
                onClick={() => refresh(filters.page + 1)}
              >
                Next
              </button>
            </div>
          </footer>
        </section>

        <section className="panel add-panel" aria-labelledby="add-title">
          <div className="panel-heading">
            <span className="form-icon" aria-hidden="true">+</span>
            <h2 id="add-title">Add a farmer</h2>
            <p>Start with the essential contact details.</p>
          </div>

          <form className="farmer-form" onSubmit={saveFarmer}>
            <fieldset disabled={saving}>
              {fields.map((field) => (
                <div className="field" key={field.name}>
                  <label htmlFor={`create-${field.name}`}>
                    {field.label}
                    {field.required ? ' *' : <span>optional</span>}
                  </label>
                  <input
                    id={`create-${field.name}`}
                    type={field.name === 'phone' ? 'tel' : 'text'}
                    value={form[field.name]}
                    onChange={(event) => setForm((current) => ({
                      ...current,
                      [field.name]: event.target.value,
                    }))}
                    required={field.required}
                    maxLength={field.maxLength}
                  />
                </div>
              ))}

              {saveError && (
                <div className="notice notice-error" role="alert">{saveError}</div>
              )}

              <button className="button button-primary save-button" type="submit">
                {saving ? 'Saving farmer…' : 'Add farmer'}
              </button>
            </fieldset>
            <p className="form-note">Use fictional contact details while testing.</p>
          </form>
        </section>
      </div>

      {selectedId && (
        <FarmerDetailsPanel
          key={selectedId}
          farmerId={selectedId}
          onClose={() => setSelectedId(null)}
          onSaved={handleUpdated}
        />
      )}
    </>
  )
}