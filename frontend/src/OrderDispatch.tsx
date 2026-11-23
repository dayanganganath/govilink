import { useEffect, useRef, useState, type FormEvent } from 'react'
import { getOrder, type ManagedOrder } from './orderApi'
import {
  dispatchOrder,
  getDispatchDetails,
  type DispatchDetails,
} from './dispatchApi'

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
    : 'Unable to complete the dispatch request.'
}

function isDispatched(order: ManagedOrder): boolean {
  return (
    order.status === 'OUT_FOR_DELIVERY' ||
    order.status === 'DELIVERED'
  )
}

export default function OrderDispatch({ order, onChanged }: Props) {
  const [reference, setReference] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  const [audit, setAudit] = useState<DispatchDetails | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [mustReload, setMustReload] = useState(false)

  const working = useRef(false)
  const alreadyDispatched = isDispatched(order)

  useEffect(() => {
    if (!alreadyDispatched) {
      return
    }

    const controller = new AbortController()

    getDispatchDetails(order.id, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) {
          setAudit(result)
          setError('')
        }
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) {
          setError(message(failure))
        }
      })

    return () => controller.abort()
  }, [order.id, alreadyDispatched])

  async function reload(): Promise<void> {
    if (working.current) {
      return
    }

    working.current = true
    setBusy(true)
    setError('')
    setNotice('')
    setConfirmed(false)

    try {
      const latest = await getOrder(order.id)

      if (isDispatched(latest)) {
        const saved = await getDispatchDetails(order.id)
        setAudit(saved)
      } else {
        setAudit(null)
      }

      onChanged(latest)
      setMustReload(false)
    } catch (failure: unknown) {
      setError(message(failure))
      setMustReload(true)
    } finally {
      working.current = false
      setBusy(false)
    }
  }

  async function submit(
    event: FormEvent<HTMLFormElement>,
  ): Promise<void> {
    event.preventDefault()

    if (
      working.current ||
      mustReload ||
      audit ||
      order.status !== 'COLLECTED'
    ) {
      return
    }

    const trimmedReference = reference.trim()

    if (!trimmedReference || trimmedReference.length > 500) {
      setError('Enter a dispatch reference of up to 500 characters.')
      return
    }

    if (!confirmed) {
      setError('Confirm that the collected goods were handed over for delivery.')
      return
    }

    working.current = true
    setBusy(true)
    setError('')
    setNotice('')

    let result: DispatchDetails

    try {
      result = await dispatchOrder(order.id, {
        version: order.version,
        reference: trimmedReference,
      })
    } catch (failure: unknown) {
      setError(message(failure))
      setMustReload(true)
      setConfirmed(false)
      working.current = false
      setBusy(false)
      return
    }

    setAudit(result)
    setConfirmed(false)
    setNotice('Dispatch recorded. The order is out for delivery.')

    try {
      const latest = await getOrder(order.id)
      onChanged(latest)
    } catch {
      setNotice(
        'Dispatch recorded. The order could not be refreshed. Reload dispatch status to check the latest order details.',
      )
    } finally {
      working.current = false
      setBusy(false)
    }
  }

  if (
    order.status !== 'COLLECTED' &&
    !alreadyDispatched &&
    !audit
  ) {
    return null
  }

  return (
    <section aria-label="Order dispatch">
      <h3>Delivery dispatch</h3>

      <button
        className="button button-secondary"
        type="button"
        onClick={() => void reload()}
        disabled={busy}
      >
        Reload dispatch status
      </button>

      {notice && <p role="status">{notice}</p>}

      {error && (
        <p className="notice notice-error" role="alert">
          {error}
        </p>
      )}

      {mustReload && (
        <p>
          Reload dispatch status before trying again. The previous request
          may have reached the server.
        </p>
      )}

      {audit ? (
        <div>
          <h4>Dispatch recorded</h4>
          <p>Dispatch ID: {audit.id}</p>
          <p>
            Dispatched: {dates.format(new Date(audit.dispatchedAt))}
            {' '}(Sri Lanka time)
          </p>
          <p>Dispatched by staff ID: {audit.dispatchedBy}</p>
          <p>Dispatch reference: {audit.reference}</p>
          <p>
            Dispatch records handover for delivery. Buyer delivery
            confirmation is a separate step.
          </p>
        </div>
      ) : alreadyDispatched ? (
        !error && <p role="status">Loading dispatch details...</p>
      ) : (
        <form onSubmit={(event) => void submit(event)}>
          <p>
            All allocated goods have been collected. Record the handover
            when the goods leave for delivery.
          </p>

          <fieldset disabled={busy || mustReload}>
            <label htmlFor="dispatch-reference">
              Dispatch reference
            </label>

            <textarea
              id="dispatch-reference"
              name="dispatchReference"
              value={reference}
              maxLength={500}
              onChange={(event) => {
                setReference(event.target.value)
                setConfirmed(false)
              }}
              required
            />

            <p>
              Include the driver, vehicle or delivery arrangement reference.
              The dispatch time is recorded by the server.
            </p>

            <label className="quote-checkbox">
              <input
                id="dispatch-confirmed"
                name="dispatchConfirmed"
                type="checkbox"
                checked={confirmed}
                onChange={(event) =>
                  setConfirmed(event.target.checked)
                }
              />
              I confirm the collected goods were handed over for delivery.
            </label>

            <button
              className="button"
              type="submit"
              disabled={
                busy ||
                mustReload ||
                !confirmed ||
                !reference.trim()
              }
            >
              {busy ? 'Recording dispatch...' : 'Dispatch order'}
            </button>
          </fieldset>
        </form>
      )}
    </section>
  )
}