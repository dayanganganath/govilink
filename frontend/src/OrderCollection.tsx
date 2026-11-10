import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'

import {
  getCollection,
  startCollection,
} from './collectionApi'
import type { CollectionDetails } from './collectionApi'

import { getOrder } from './orderApi'
import type { ManagedOrder } from './orderApi'

type Props = {
  order: ManagedOrder
  onChanged: (order: ManagedOrder) => void
}

const dates = new Intl.DateTimeFormat('en-LK', {
  timeZone: 'Asia/Colombo',
  dateStyle: 'medium',
  timeStyle: 'short',
})

function message(error: unknown): string {
  return error instanceof Error
    ? error.message
    : 'Unable to update collection. Reload before trying again.'
}

export default function OrderCollection({
  order,
  onChanged,
}: Props) {
  const [reference, setReference] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  const [details, setDetails] = useState<CollectionDetails | null>(null)
  const [busy, setBusy] = useState(false)
  const [needsReload, setNeedsReload] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [revision, setRevision] = useState(0)

  const working = useRef(false)

  useEffect(() => {
    if (order.status !== 'COLLECTION_IN_PROGRESS') return

    const controller = new AbortController()

    getCollection(order.id, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return
        setDetails(result)
        setError('')
      })
      .catch((failure: unknown) => {
        if (controller.signal.aborted) return
        setError(message(failure))
      })

    return () => controller.abort()
  }, [order.id, order.status, revision])

  const activeReservations = order.reservations.length > 0
    && order.reservations.every(
      (reservation) => reservation.status === 'ACTIVE',
    )

  async function reload() {
    if (working.current) return

    working.current = true
    setBusy(true)
    setError('')
    setNotice('')

    try {
      const latest = await getOrder(order.id)

      setConfirmed(false)
      setNeedsReload(false)
      setRevision((value) => value + 1)
      onChanged(latest)
    } catch (failure) {
      setError(message(failure))
      setNeedsReload(true)
    } finally {
      working.current = false
      setBusy(false)
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (working.current || needsReload || details) return

    setError('')
    setNotice('')

    if (order.status !== 'CONFIRMED' || !activeReservations) {
      setError(
        'A confirmed order with active reservations is required.',
      )
      return
    }

    if (!reference.trim()) {
      setError('Enter the collection arrangements or reference.')
      return
    }

    if (!confirmed) {
      setError('Confirm that collection arrangements are ready.')
      return
    }

    working.current = true
    setBusy(true)

    try {
      const result = await startCollection(order.id, {
        version: order.version,
        reference: reference.trim(),
      })

      setDetails(result)

      try {
        const latest = await getOrder(order.id)
        onChanged(latest)
      } catch {
        setNotice(
          'Collection started. Reload collection status to update the order display.',
        )
      }
    } catch (failure) {
      setError(message(failure))
      setNeedsReload(true)
    } finally {
      working.current = false
      setBusy(false)
    }
  }

  if (
    !details
    && order.status !== 'CONFIRMED'
    && order.status !== 'COLLECTION_IN_PROGRESS'
  ) {
    return null
  }

  return (
    <section className="orders-panel" aria-label="Order collection">
      <h3>Collection</h3>

      <button
        className="button button-secondary"
        type="button"
        disabled={busy}
        onClick={() => void reload()}
      >
        {busy ? 'Please wait…' : 'Reload collection status'}
      </button>

      {error && (
        <p className="notice notice-error" role="alert">
          {error}
        </p>
      )}

      {notice && <p role="status">{notice}</p>}

      {details ? (
        <>
          <p role="status"><strong>Collection started</strong></p>

          <p>
            Started: {dates.format(new Date(details.startedAt))}
            {' '}(Sri Lanka time)
          </p>

          <p>
            Started by staff ID:{' '}
            <span className="quote-id">{details.startedBy}</span>
          </p>

          <p>Collection reference: {details.reference}</p>

          <p>
            Stock remains reserved. Starting collection does not
            record goods as received or delivered.
          </p>
        </>
      ) : order.status === 'COLLECTION_IN_PROGRESS' ? (
        <p role="status">Collection is in progress. Loading its record…</p>
      ) : (
        <>
          <p>
            Start collection after arranging pickup with the farmer.
            This keeps the order’s stock reserved.
          </p>

          {!activeReservations && (
            <p className="notice">
              All reservations must be active before collection starts.
            </p>
          )}

          {needsReload && (
            <p>
              Reload before trying again. The previous request may
              have reached the server.
            </p>
          )}

          <form noValidate onSubmit={submit}>
            <fieldset
              className="quote-fieldset"
              disabled={busy || needsReload || !activeReservations}
            >
              <label htmlFor={`collection-reference-${order.id}`}>
                Collection arrangements or reference
              </label>

              <textarea
                id={`collection-reference-${order.id}`}
                name="collectionReference"
                maxLength={500}
                value={reference}
                onChange={(event) => setReference(event.target.value)}
                required
              />

              <label className="quote-checkbox">
                <input
                  name="collectionConfirmed"
                  type="checkbox"
                  checked={confirmed}
                  onChange={(event) => setConfirmed(event.target.checked)}
                />
                I confirm collection arrangements are ready.
              </label>

              <button
                className="button button-primary"
                type="submit"
                disabled={!confirmed || !reference.trim()}
              >
                {busy ? 'Starting collection…' : 'Start collection'}
              </button>
            </fieldset>
          </form>
        </>
      )}
    </section>
  )
}