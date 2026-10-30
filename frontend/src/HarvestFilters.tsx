import { useState } from 'react'
import type { FormEvent } from 'react'
import type { HarvestFilterValues } from './harvestApi'

interface Props {
  busy: boolean
  onApply: (filters: HarvestFilterValues) => void
}

const emptyFilters: HarvestFilterValues = {
  query: '',
  district: '',
  status: '',
}

export default function HarvestFilters({ busy, onApply }: Props) {
  const [draft, setDraft] = useState<HarvestFilterValues>(emptyFilters)

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return

    onApply({
      query: draft.query.trim(),
      district: draft.district.trim(),
      status: draft.status,
    })
  }

  function clear() {
    if (busy) return

    setDraft({ ...emptyFilters })
    onApply({ ...emptyFilters })
  }

  return (
    <form
      className="harvest-filter-form"
      aria-label="Harvest search filters"
      onSubmit={submit}
    >
      <div className="field">
        <label htmlFor="harvest-search-query">Search crop</label>
        <input
          id="harvest-search-query"
          value={draft.query}
          maxLength={100}
          placeholder="e.g. Tomato"
          onChange={(event) => setDraft((current) => ({
            ...current,
            query: event.target.value,
          }))}
        />
      </div>

      <div className="field">
        <label htmlFor="harvest-search-district">Filter by district</label>
        <input
          id="harvest-search-district"
          value={draft.district}
          maxLength={100}
          placeholder="All districts"
          onChange={(event) => setDraft((current) => ({
            ...current,
            district: event.target.value,
          }))}
        />
      </div>

      <div className="field">
        <label htmlFor="harvest-search-status">Listing status</label>
        <select
          id="harvest-search-status"
          value={draft.status}
          onChange={(event) => setDraft((current) => ({
            ...current,
            status: event.target.value as HarvestFilterValues['status'],
          }))}
        >
          <option value="">All statuses</option>
          <option value="AVAILABLE">Available</option>
          <option value="SOLD">Sold</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
      </div>

      <div className="harvest-filter-buttons">
        <button
          className="button button-primary"
          type="submit"
          disabled={busy}
        >
          Search harvests
        </button>

        <button
          className="button button-secondary"
          type="button"
          disabled={busy}
          onClick={clear}
        >
          Clear filters
        </button>
      </div>
    </form>
  )
}