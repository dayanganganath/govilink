import { useEffect, useRef, useState, type FormEvent } from 'react'

import { getOrder, type ManagedOrder } from './orderApi'
import {
  cancelOrder,
  getCancellationDetails,
  type CancellationDetails,
} from './cancellationApi'

import './OrderCancellation.css'

interface Props {
  order: ManagedOrder
  onChanged: (order: ManagedOrder) => void
}

const dateTime = new Intl.DateTimeFormat('en-LK', {
  timeZone: 'Asia/Colombo',
  dateStyle: 'medium',
  timeStyle: 'short',
})

function message(failure: unknown): string {
  return failure instanceof Error
    ? failure.message
    : 'Unable to complete this request. Please try again.'
}

function statusLabel(value: string): string {
  return value.toLowerCase().replaceAll('_', ' ')
}

export default function OrderCancellation({ order, onChanged }: Props) {
  const [reason, setReason] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  const [audit, setAudit] = useState<CancellationDetails | null>(null)
  const [error, setError] = useState('')
  const [auditError, setAuditError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [mustReload, setMustReload] = useState(false)

  const working = useRef(false)

  const eligible =
    order.status === 'CONFIRMED' ||
    order.status === 'COLLECTION_IN_PROGRESS'

  useEffect(() => {
    if (order.status !== 'CANCELLED' || audit) {
      return
    }

    const controller = new AbortController()

    getCancellationDetails(order.id, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) {
          setAudit(result)
          setAuditError('')
        }
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) {
          setAuditError(message(failure))
        }
      })

    return () => controller.abort()
  }, [order.id, order.status, audit])

  async function reload(): Promise<void> {
    if (working.current) {
      return
    }

    working.current = true
    setBusy(true)
    setError('')
    setAuditError('')
    setNotice('')
    setConfirmed(false)
    setMustReload(true)

    try {
      const latest = await getOrder(order.id)

      if (latest.status === 'CANCELLED') {
        const saved = await getCancellationDetails(order.id)
        setAudit(saved)
      }

      onChanged(latest)
      setMustReload(false)
    } catch (failure: unknown) {
      setError(message(failure))
    } finally {
      working.current = false
      setBusy(false)
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault()

    if (working.current || mustReload || audit || !eligible) {
      return
    }

    setError('')
    setNotice('')

    const trimmedReason = reason.trim()

    if (!trimmedReason || reason.length > 500) {
      setError('Enter a cancellation reason of up to 500 characters.')
      return
    }

    if (!confirmed) {
      setError('Confirm the cancellation and stock release before continuing.')
      return
    }

    working.current = true
    setBusy(true)

    let saved: CancellationDetails

    try {
      saved = await cancelOrder(order.id, {
        version: order.version,
        reason: trimmedReason,
      })
    } catch (failure: unknown) {
      setError(message(failure))
      setMustReload(true)
      setConfirmed(false)
      working.current = false
      setBusy(false)
      return
    }

    // Keep the successful audit even if refreshing the order fails.
    setAudit(saved)
    setConfirmed(false)
    setNotice('Order cancelled. Reserved stock has been released.')

    try {
      const latest = await getOrder(order.id)
      onChanged(latest)
    } catch {
      setMustReload(true)
      setNotice(
        'Order cancelled and stock released. Reload cancellation status to refresh the order details.',
      )
    } finally {
      working.current = false
      setBusy(false)
    }
  }

  if (!eligible && order.status !== 'CANCELLED' && !audit) {
    return null
  }

  return (
    <section className="order-cancellation" aria-label="Order cancellation">
      <div className="order-cancellation-heading">
        <div>
          <p className="eyebrow">ORDER CONTROL</p>
          <h3>{audit || order.status === 'CANCELLED'
            ? 'Cancellation details'
            : 'Cancel order'}
          </h3>
        </div>

        <button
          className="button button-secondary"
          type="button"
          disabled={busy}
          onClick={() => void reload()}
        >
          Reload cancellation status
        </button>
      </div>

      {notice && <p role="status">{notice}</p>}

      {(error || auditError) && (
        <p className="notice notice-error" role="alert">
          {error || auditError}
        </p>
      )}

      {mustReload && (
        <p>
          Reload cancellation status before attempting another cancellation.
          Your entered reason has been kept.
        </p>
      )}

      {audit ? (
        <div className="order-cancellation-audit">
          <h4>Order cancelled</h4>

          <dl>
            <div>
              <dt>Cancellation ID</dt>
              <dd>{audit.id}</dd>
            </div>

            <div>
              <dt>Previous status</dt>
              <dd>{statusLabel(audit.previousStatus)}</dd>
            </div>

            <div>
              <dt>Released stock</dt>
              <dd>{audit.releasedQuantityKg} kg</dd>
            </div>

            <div>
              <dt>Cancelled</dt>
              <dd>
                {dateTime.format(new Date(audit.cancelledAt))}
                {' '}— Sri Lanka time
              </dd>
            </div>

            <div>
              <dt>Cancelled by staff</dt>
              <dd>{audit.cancelledBy}</dd>
            </div>

            <div>
              <dt>Reason</dt>
              <dd>{audit.reason}</dd>
            </div>
          </dl>
        </div>
      ) : order.status === 'CANCELLED' ? (
        !auditError && <p role="status">Loading cancellation details...</p>
      ) : (
        <form onSubmit={(event) => void submit(event)} noValidate>
          <p>
            Cancellation releases this order&apos;s reserved stock.
            Orders with any received goods cannot be cancelled here.
          </p>

          <fieldset disabled={busy || mustReload}>
            <label htmlFor={`cancel-reason-${order.id}`}>
              Cancellation reason
            </label>

            <textarea
              id={`cancel-reason-${order.id}`}
              name="cancellationReason"
              value={reason}
              maxLength={500}
              rows={3}
              placeholder="Explain why this order is being cancelled"
              onChange={(event) => {
                setReason(event.target.value)
                setConfirmed(false)
              }}
              required
            />

            <label className="order-cancellation-confirm">
              <input
                type="checkbox"
                checked={confirmed}
                onChange={(event) => setConfirmed(event.target.checked)}
              />

              <span>
                I confirm this order should be cancelled and its reserved
                stock released.
              </span>
            </label>

            <button
              className="button order-cancellation-submit"
              type="submit"
              disabled={busy || mustReload || !confirmed || !reason.trim()}
            >
              {busy ? 'Cancelling order...' : 'Cancel order'}
            </button>
          </fieldset>
        </form>
      )}
    </section>
  )
}