import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import type { Farmer, FarmerPage } from './api'
import {
  createHarvest,
  listHarvests,
  searchActiveFarmers,
  searchHarvests,
} from './harvestApi'
import type {
  HarvestFilterValues,
  HarvestListing,
  HarvestPage,
} from './harvestApi'
import HarvestFilters from './HarvestFilters'
import HarvestStatusActions from './HarvestStatusActions'
import './HarvestListings.css'

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'The request failed.'
}

function localDate(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

function emptyForm() {
  return {
    cropName: '',
    quantityKg: '',
    expectedPricePerKg: '',
    availableFrom: localDate(),
  }
}

interface FarmerPickerProps {
  selected: Farmer | null
  onSelect: (farmer: Farmer | null) => void
}

function FarmerPicker({ selected, onSelect }: FarmerPickerProps) {
  const [query, setQuery] = useState('')
  const [filters, setFilters] = useState({
    query: '',
    page: 0,
    revision: 0,
  })
  const [data, setData] = useState<FarmerPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()

    searchActiveFarmers(filters.query, filters.page, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return
        setData(result)
        setError('')
        setLoading(false)
      })
      .catch((failure: unknown) => {
        if (controller.signal.aborted) return
        setError(errorMessage(failure))
        setLoading(false)
      })

    return () => controller.abort()
  }, [filters])

  function search() {
    setLoading(true)
    setError('')
    setFilters((current) => ({
      query: query.trim(),
      page: 0,
      revision: current.revision + 1,
    }))
  }

  function changePage(page: number) {
    setLoading(true)
    setError('')
    setFilters((current) => ({ ...current, page }))
  }

  const ready = !loading && !error && data !== null

  return (
    <div className="harvest-picker">
      <div className="field">
        <label htmlFor="harvest-farmer-query">Find an active farmer</label>
        <div className="harvest-search-row">
          <input
            id="harvest-farmer-query"
            value={query}
            maxLength={150}
            placeholder="Name, phone or village"
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                event.preventDefault()
                search()
              }
            }}
          />
          <button
            type="button"
            className="button button-secondary"
            onClick={search}
            disabled={loading}
          >
            Search
          </button>
        </div>
      </div>

      {loading && <p role="status">Loading farmers…</p>}

      {error && (
        <div className="notice notice-error" role="alert">
          {error}
          <button type="button" className="text-button" onClick={search}>
            Retry farmer search
          </button>
        </div>
      )}

      {ready && (
        <>
          <div className="field">
            <label htmlFor="harvest-farmer">Farmer *</label>
            <select
              id="harvest-farmer"
              value={selected?.id ?? ''}
              required
              onChange={(event) => {
                const id = event.target.value

                if (!id) {
                  onSelect(null)
                  return
                }

                const farmer = data.content.find((item) => item.id === id)
                if (farmer) onSelect(farmer)
              }}
            >
              <option value="">Select a farmer</option>

              {selected && !data.content.some(
                (farmer) => farmer.id === selected.id,
              ) && (
                <option value={selected.id}>
                  {selected.fullName} — {selected.phone}
                </option>
              )}

              {data.content.map((farmer) => (
                <option key={farmer.id} value={farmer.id}>
                  {farmer.fullName} — {farmer.phone} — {farmer.district}
                </option>
              ))}
            </select>
          </div>

          <div className="harvest-picker-pages">
            <span>
              {data.totalElements} matching farmers
              {data.totalPages > 0 &&
                ` · Page ${data.page + 1}/${data.totalPages}`}
            </span>

            <div className="page-buttons">
              <button
                className="button button-secondary button-small"
                type="button"
                disabled={!data.hasPrevious}
                onClick={() => changePage(data.page - 1)}
                aria-label="Previous farmer options"
              >
                Previous
              </button>

              <button
                className="button button-secondary button-small"
                type="button"
                disabled={!data.hasNext}
                onClick={() => changePage(data.page + 1)}
                aria-label="Next farmer options"
              >
                Next
              </button>
            </div>
          </div>

          {data.totalElements === 0 && (
            <p className="harvest-help">
              No matching active farmers. Try another search or add a farmer
              from the Farmers screen.
            </p>
          )}
        </>
      )}

      {selected && (
        <p className="harvest-selected">
          Selected: <strong>{selected.fullName}</strong> · {selected.district}
        </p>
      )}
    </div>
  )
}

interface SearchState extends HarvestFilterValues {
  page: number
  revision: number
}

export default function HarvestListings() {
  const [filters, setFilters] = useState<SearchState>({
    query: '',
    district: '',
    status: '',
    page: 0,
    revision: 0,
  })
  const [data, setData] = useState<HarvestPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [selectedFarmer, setSelectedFarmer] = useState<Farmer | null>(null)
  const [form, setForm] = useState(emptyForm)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState('')
  const [success, setSuccess] = useState('')
  const savingRef = useRef(false)

  const hasFilters = Boolean(
    filters.query || filters.district || filters.status,
  )

  useEffect(() => {
    const controller = new AbortController()

    const hasAppliedFilters = Boolean(
      filters.query || filters.district || filters.status,
    )

    const request = hasAppliedFilters
      ? searchHarvests(
          filters.page,
          {
            query: filters.query,
            district: filters.district,
            status: filters.status,
          },
          controller.signal,
        )
      : listHarvests(filters.page, controller.signal)

    request
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

  function reload(page: number) {
    setLoading(true)
    setListError('')
    setFilters((current) => ({
      ...current,
      page,
      revision: current.revision + 1,
    }))
  }

  function applyFilters(next: HarvestFilterValues) {
    setLoading(true)
    setListError('')
    setSuccess('')
    setFilters((current) => ({
      ...next,
      page: 0,
      revision: current.revision + 1,
    }))
  }

  function handleStatusChanged(updated: HarvestListing) {
    setSuccess(
      `${updated.cropName} listing for ${updated.farmerName} `
      + `was marked ${updated.status}.`,
    )
    reload(0)
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (savingRef.current) return

    setSaveError('')
    setSuccess('')

    if (!selectedFarmer) {
      setSaveError('Select an active farmer before adding a listing.')
      return
    }

    if (!form.cropName.trim()) {
      setSaveError('Enter a crop name.')
      return
    }

    const quantity = Number(form.quantityKg)
    const price = form.expectedPricePerKg.trim() === ''
      ? null
      : Number(form.expectedPricePerKg)

    if (
      !Number.isFinite(quantity) || quantity <= 0 ||
      (price !== null && (!Number.isFinite(price) || price <= 0))
    ) {
      setSaveError('Enter a valid positive quantity and optional price.')
      return
    }

    savingRef.current = true
    setSaving(true)

    try {
      const created = await createHarvest({
        farmerId: selectedFarmer.id,
        cropName: form.cropName.trim(),
        quantityKg: quantity,
        expectedPricePerKg: price,
        availableFrom: form.availableFrom,
      })

      setSuccess(
        `${created.cropName} listing for ${created.farmerName} was added.`,
      )
      setForm(emptyForm())
      reload(0)
    } catch (error: unknown) {
      setSaveError(errorMessage(error))
    } finally {
      savingRef.current = false
      setSaving(false)
    }
  }

  const ready = !loading && !listError && data !== null

  const numberFormat = new Intl.NumberFormat('en-LK', {
    maximumFractionDigits: 3,
  })

  const priceFormat = new Intl.NumberFormat('en-LK', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })

  return (
    <>
      <section className="page-heading">
        <div>
          <p className="eyebrow">CONNECT HARVESTS TO OPPORTUNITIES</p>
          <h1>Harvest listings</h1>
          <p className="subtitle">
            Find produce by crop, district and listing status.
          </p>
        </div>

        <button
          className="button button-secondary"
          type="button"
          disabled={loading}
          onClick={() => reload(filters.page)}
        >
          {loading ? 'Loading…' : 'Refresh listings'}
        </button>
      </section>

      <section className="stats-grid" aria-label="Harvest summary">
        <article className="stat-card">
          <span>Matching listings</span>
          <strong>{ready ? data.totalElements : '—'}</strong>
          <small>Across all matching pages</small>
        </article>

        <article className="stat-card">
          <span>Available on this page</span>
          <strong>
            {ready
              ? data.content.filter((item) => item.status === 'AVAILABLE').length
              : '—'}
          </strong>
          <small>Listings shown below</small>
        </article>

        <article className="stat-card stat-accent">
          <span>Listings page</span>
          <strong>
            {ready
              ? `${data.totalPages === 0 ? 0 : data.page + 1} / ${data.totalPages}`
              : '—'}
          </strong>
          <small>Up to 10 listings per page</small>
        </article>
      </section>

      {success && (
        <div className="notice notice-success" role="status">
          {success}
          {hasFilters && (
            <span className="harvest-success-note">
              Current filters remain applied.
            </span>
          )}
        </div>
      )}

      <div className="workspace-grid">
        <section className="panel" aria-labelledby="harvest-list-title">
          <div className="panel-heading">
            <h2 id="harvest-list-title">Recorded harvests</h2>
            <p>Newest listings first. Quantities are in kilograms.</p>
          </div>

          <HarvestFilters busy={loading} onApply={applyFilters} />

          <p className="harvest-filter-summary">
            {hasFilters
              ? [
                  filters.query && `Crop contains: ${filters.query}`,
                  filters.district && `District: ${filters.district}`,
                  filters.status && `Status: ${filters.status}`,
                ].filter(Boolean).join(' · ')
              : 'Showing all listings'}
          </p>

          {listError && (
            <div className="notice notice-error directory-error" role="alert">
              {listError}
              <button
                type="button"
                className="text-button"
                onClick={() => reload(filters.page)}
              >
                Try again
              </button>
            </div>
          )}

          <div className="table-wrap" aria-busy={loading}>
            <table>
              <thead>
                <tr>
                  <th scope="col">Crop / farmer</th>
                  <th scope="col">Quantity</th>
                  <th scope="col">LKR / kg</th>
                  <th scope="col">Available from</th>
                  <th scope="col">Status</th>
                  <th scope="col">Actions</th>
                </tr>
              </thead>

              <tbody>
                {loading && (
                  <tr>
                    <td colSpan={6} className="table-message" role="status">
                      Loading harvest listings…
                    </td>
                  </tr>
                )}

                {ready && data.content.length === 0 && (
                  <tr>
                    <td colSpan={6} className="table-message">
                      <strong>
                        {hasFilters
                          ? 'No matching harvest listings'
                          : 'No harvest listings yet'}
                      </strong>
                      <span>
                        {hasFilters
                          ? 'Try another search or clear the filters.'
                          : 'Add the first listing using the form.'}
                      </span>
                    </td>
                  </tr>
                )}

                {ready && data.content.map((listing) => (
                  <tr key={listing.id}>
                    <td>
                      <strong>{listing.cropName}</strong>
                      <span className="cell-secondary">
                        {listing.farmerName} · {listing.district}
                      </span>
                    </td>

                    <td>{numberFormat.format(listing.quantityKg)} kg</td>

                    <td>
                      {listing.expectedPricePerKg === null
                        ? 'Not specified'
                        : priceFormat.format(listing.expectedPricePerKg)}
                    </td>

                    <td>{listing.availableFrom}</td>

                    <td>
                      <span
                        className={`status-badge ${
                          listing.status === 'AVAILABLE' ? 'active' : 'inactive'
                        }`}
                      >
                        {listing.status}
                      </span>
                    </td>

                    <td className="harvest-action-cell">
                      <HarvestStatusActions
                        listing={listing}
                        onChanged={handleStatusChanged}
                        onReload={() => reload(filters.page)}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <footer className="pagination">
            <span>
              {ready
                ? `${data.content.length} of ${data.totalElements} listings`
                : 'Harvest results'}
            </span>

            <div className="page-buttons">
              <button
                type="button"
                className="button button-secondary button-small"
                disabled={!ready || !data.hasPrevious}
                onClick={() => reload(filters.page - 1)}
              >
                Previous
              </button>

              <button
                type="button"
                className="button button-secondary button-small"
                disabled={!ready || !data.hasNext}
                onClick={() => reload(filters.page + 1)}
              >
                Next
              </button>
            </div>
          </footer>
        </section>

        <section className="panel add-panel" aria-labelledby="add-harvest-title">
          <div className="panel-heading">
            <span className="form-icon" aria-hidden="true">+</span>
            <h2 id="add-harvest-title">Add a harvest listing</h2>
            <p>Select a farmer and enter the available produce.</p>
          </div>

          <form className="farmer-form" onSubmit={save}>
            <fieldset disabled={saving}>
              <FarmerPicker
                selected={selectedFarmer}
                onSelect={setSelectedFarmer}
              />

              <div className="field">
                <label htmlFor="harvest-crop">Crop name *</label>
                <input
                  id="harvest-crop"
                  value={form.cropName}
                  required
                  maxLength={100}
                  placeholder="e.g. Tomato"
                  onChange={(event) => setForm((current) => ({
                    ...current,
                    cropName: event.target.value,
                  }))}
                />
              </div>

              <div className="field">
                <label htmlFor="harvest-quantity">Quantity — kg *</label>
                <input
                  id="harvest-quantity"
                  type="number"
                  required
                  min="0.001"
                  max="999999999.999"
                  step="0.001"
                  value={form.quantityKg}
                  onChange={(event) => setForm((current) => ({
                    ...current,
                    quantityKg: event.target.value,
                  }))}
                />
              </div>

              <div className="field">
                <label htmlFor="harvest-price">
                  Expected price — LKR/kg <span>optional</span>
                </label>
                <input
                  id="harvest-price"
                  type="number"
                  min="0.01"
                  max="9999999999.99"
                  step="0.01"
                  value={form.expectedPricePerKg}
                  onChange={(event) => setForm((current) => ({
                    ...current,
                    expectedPricePerKg: event.target.value,
                  }))}
                />
              </div>

              <div className="field">
                <label htmlFor="harvest-date">Available from *</label>
                <input
                  id="harvest-date"
                  type="date"
                  required
                  value={form.availableFrom}
                  onChange={(event) => setForm((current) => ({
                    ...current,
                    availableFrom: event.target.value,
                  }))}
                />
              </div>

              {saveError && (
                <div className="notice notice-error" role="alert">
                  {saveError}
                </div>
              )}

              <button
                className="button button-primary save-button"
                type="submit"
                disabled={!selectedFarmer}
              >
                {saving ? 'Saving listing…' : 'Add listing'}
              </button>
            </fieldset>

            <p className="form-note">
              New listings start as AVAILABLE. The selected farmer is kept
              for your next listing.
            </p>
          </form>
        </section>
      </div>
    </>
  )
}