import { useRef, useState } from 'react'
import { ApiError } from './api'
import { updateHarvestStatus } from './harvestApi'
import type { HarvestCloseStatus, HarvestListing } from './harvestApi'

interface Props {
  listing: HarvestListing
  onChanged: (listing: HarvestListing) => void
  onReload: () => void
}

export default function HarvestStatusActions({
  listing,
  onChanged,
  onReload,
}: Props) {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [needsReload, setNeedsReload] = useState(false)
  const savingRef = useRef(false)

  async function changeStatus(target: HarvestCloseStatus) {
    if (
      savingRef.current ||
      needsReload ||
      listing.status !== 'AVAILABLE'
    ) {
      return
    }

    const action = target === 'SOLD' ? 'mark as SOLD' : 'CANCEL'
    const confirmed = window.confirm(
      `${action}: ${listing.cropName} for ${listing.farmerName}? `
      + 'This listing cannot be reopened in the current version.',
    )

    if (!confirmed) return

    savingRef.current = true
    setSaving(true)
    setError('')

    try {
      const updated = await updateHarvestStatus(
        listing.id,
        target,
        listing.version,
      )

      onChanged(updated)
    } catch (failure: unknown) {
      setError(
        failure instanceof Error ? failure.message : 'Status update failed.',
      )

      if (
        failure instanceof ApiError &&
        (failure.status === 409 || failure.status === 404)
      ) {
        setNeedsReload(true)
      }
    } finally {
      savingRef.current = false
      setSaving(false)
    }
  }

  if (listing.status !== 'AVAILABLE') {
    return <span className="cell-secondary">Closed listing</span>
  }

  return (
    <div className="harvest-status-actions">
      <div className="page-buttons">
        <button
          className="button button-secondary button-small"
          type="button"
          disabled={saving || needsReload}
          onClick={() => changeStatus('SOLD')}
          aria-label={`Mark ${listing.cropName} as sold`}
        >
          Mark sold
        </button>

        <button
          className="button button-secondary button-small"
          type="button"
          disabled={saving || needsReload}
          onClick={() => changeStatus('CANCELLED')}
          aria-label={`Cancel ${listing.cropName} listing`}
        >
          Cancel listing
        </button>
      </div>

      {saving && <p role="status">Updating status…</p>}

      {error && (
        <div className="notice notice-error" role="alert">
          <p>{error}</p>

          {needsReload ? (
            <>
              <p>Reload the latest listings before trying another update.</p>
              <button
                className="text-button"
                type="button"
                onClick={onReload}
              >
                Reload latest listings
              </button>
            </>
          ) : (
            <p>The update was not confirmed. You can retry or refresh listings.</p>
          )}
        </div>
      )}
    </div>
  )
}