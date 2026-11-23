import { useEffect, useRef, useState, type FormEvent } from 'react'
import { getOrder, type ManagedOrder } from './orderApi'
import { getDispatchDetails } from './dispatchApi'
import {
  confirmDelivery,
  getDeliveryDetails,
  type DeliveryDetails,
} from './deliveryApi'

type Props = {
  order: ManagedOrder
  onChanged: (order: ManagedOrder) => void
}

type DeliveryState = {
  ready: boolean
  dispatchedAt: string | null
  audit: DeliveryDetails | null
  error: string
}

const dates = new Intl.DateTimeFormat('en-LK', {
  timeZone: 'Asia/Colombo',
  dateStyle: 'medium',
  timeStyle: 'short',
})

function message(error: unknown): string {
  return error instanceof Error
    ? error.message
    : 'Unable to complete the delivery request.'
}

function sriLankaInput(instant: string): string {
  const date = new Date(instant)

  return new Date(date.getTime() + 330 * 60 * 1000)
    .toISOString()
    .slice(0, 19)
}

async function loadDetails(
  order: ManagedOrder,
  signal?: AbortSignal,
): Promise<DeliveryState> {
  if (order.status === 'DELIVERED') {
    const audit = await getDeliveryDetails(order.id, signal)

    return {
      ready: true,
      dispatchedAt: null,
      audit,
      error: '',
    }
  }

  if (order.status === 'OUT_FOR_DELIVERY') {
    const dispatch = await getDispatchDetails(order.id, signal)

    return {
      ready: true,
      dispatchedAt: dispatch.dispatchedAt,
      audit: null,
      error: '',
    }
  }

  return {
    ready: true,
    dispatchedAt: null,
    audit: null,
    error: '',
  }
}

export default function OrderDelivery({ order, onChanged }: Props) {
  const [details, setDetails] = useState<DeliveryState>({
    ready: false,
    dispatchedAt: null,
    audit: null,
    error: '',
  })

  const [receiverName, setReceiverName] = useState('')
  const [deliveredAt, setDeliveredAt] = useState('')
  const [reference, setReference] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  const [busy, setBusy] = useState(false)
  const [mustReload, setMustReload] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const working = useRef(false)

  const eligible =
    order.status === 'OUT_FOR_DELIVERY' ||
    order.status === 'DELIVERED'

  useEffect(() => {
    if (
      order.status !== 'OUT_FOR_DELIVERY' &&
      order.status !== 'DELIVERED'
    ) {
      return
    }

    const controller = new AbortController()

    loadDetails(order, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) {
          setDetails(result)
        }
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) {
          setDetails({
            ready: false,
            dispatchedAt: null,
            audit: null,
            error: message(failure),
          })
        }
      })

    return () => controller.abort()
  }, [order])

  async function reload(): Promise<void> {
    if (working.current) {
      return
    }

    working.current = true
    setBusy(true)
    setError('')
    setNotice('')
    setConfirmed(false)
    setMustReload(true)

    try {
      const latest = await getOrder(order.id)
      const result = await loadDetails(latest)

      setDetails(result)
      onChanged(latest)
      setMustReload(false)
    } catch (failure: unknown) {
      setError(message(failure))
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
      !details.ready ||
      details.audit ||
      order.status !== 'OUT_FOR_DELIVERY'
    ) {
      return
    }

    setError('')
    setNotice('')

    const receiver = receiverName.trim()
    const deliveryReference = reference.trim()

    if (!receiver || receiver.length > 150) {
      setError('Enter the receiver name using up to 150 characters.')
      return
    }

    if (
      !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(deliveredAt)
    ) {
      setError('Enter the delivery date and time in Sri Lanka time.')
      return
    }

    const deliveredDate = new Date(`${deliveredAt}+05:30`)

    if (
      Number.isNaN(deliveredDate.getTime()) ||
      deliveredDate.getTime() > Date.now()
    ) {
      setError('Delivery time must be valid and cannot be in the future.')
      return
    }

    if (
      !details.dispatchedAt ||
      deliveredDate.getTime() <
        new Date(details.dispatchedAt).getTime()
    ) {
      setError('Delivery time cannot be earlier than dispatch.')
      return
    }

    if (!deliveryReference || deliveryReference.length > 500) {
      setError('Enter a delivery reference of up to 500 characters.')
      return
    }

    if (!confirmed) {
      setError('Confirm that the full order was received.')
      return
    }

    working.current = true
    setBusy(true)

    let saved: DeliveryDetails

    try {
      saved = await confirmDelivery(order.id, {
        version: order.version,
        receiverName: receiver,
        deliveredAt: deliveredDate.toISOString(),
        reference: deliveryReference,
      })
    } catch (failure: unknown) {
      setError(message(failure))
      setMustReload(true)
      setConfirmed(false)
      working.current = false
      setBusy(false)
      return
    }

    setDetails((current) => ({
      ...current,
      ready: true,
      audit: saved,
      error: '',
    }))

    setConfirmed(false)
    setNotice('Delivery confirmed. The order is marked as delivered.')

    try {
      const latest = await getOrder(order.id)
      onChanged(latest)
    } catch {
      setNotice(
        'Delivery confirmed. The order could not be refreshed. Reload delivery status to check the latest order details.',
      )
    } finally {
      working.current = false
      setBusy(false)
    }
  }

  if (!eligible && !details.audit) {
    return null
  }

  return (
    <section aria-label="Order delivery">
      <h3>Delivery confirmation</h3>

      <button
        className="button button-secondary"
        type="button"
        disabled={busy}
        onClick={() => void reload()}
      >
        Reload delivery status
      </button>

      {notice && <p role="status">{notice}</p>}

      {(error || details.error) && (
        <p className="notice notice-error" role="alert">
          {error || details.error}
        </p>
      )}

      {mustReload && (
        <p>
          Reload delivery status before trying again. The previous request
          may have reached the server.
        </p>
      )}

      {details.audit ? (
        <div>
          <h4>Delivery confirmed</h4>

          <p>Delivery ID: {details.audit.id}</p>
          <p>Received by: {details.audit.receiverName}</p>

          <p>
            Delivered:{' '}
            {dates.format(new Date(details.audit.deliveredAt))}
            {' '}(Sri Lanka time)
          </p>

          <p>
            Recorded:{' '}
            {dates.format(new Date(details.audit.recordedAt))}
            {' '}(Sri Lanka time)
          </p>

          <p>Recorded by staff ID: {details.audit.recordedBy}</p>
          <p>Delivery reference: {details.audit.reference}</p>
          <p>
            Delivery confirmation records receipt of the goods.
            Payment tracking is separate.
          </p>
        </div>
      ) : !details.ready ? (
        !details.error && (
          <p role="status">Loading delivery details...</p>
        )
      ) : order.status === 'OUT_FOR_DELIVERY' ? (
        <form onSubmit={(event) => void submit(event)} noValidate>
          <h4>Record buyer receipt</h4>

          <p>
            Confirm delivery only after the buyer or their representative
            has received the full order.
          </p>

          {details.dispatchedAt && (
            <p>
              Dispatched:{' '}
              {dates.format(new Date(details.dispatchedAt))}
              {' '}(Sri Lanka time)
            </p>
          )}

          <fieldset disabled={busy || mustReload}>
            <label htmlFor="delivery-receiver">
              Receiver name
            </label>

            <input
              id="delivery-receiver"
              name="receiverName"
              type="text"
              autoComplete="off"
              maxLength={150}
              placeholder="Name of the person who received the goods"
              value={receiverName}
              onChange={(event) => {
                setReceiverName(event.target.value)
                setConfirmed(false)
              }}
              required
            />

            <label htmlFor="delivery-received-at">
              Delivered date and time — Sri Lanka time
            </label>

            <input
              id="delivery-received-at"
              name="deliveredAt"
              type="datetime-local"
              step="1"
              min={
                details.dispatchedAt
                  ? sriLankaInput(details.dispatchedAt)
                  : undefined
              }
              value={deliveredAt}
              onChange={(event) => {
                setDeliveredAt(event.target.value)
                setConfirmed(false)
              }}
              required
            />

            <label htmlFor="delivery-reference">
              Delivery reference
            </label>

            <textarea
              id="delivery-reference"
              name="deliveryReference"
              maxLength={500}
              placeholder="Delivery note number or buyer confirmation details"
              value={reference}
              onChange={(event) => {
                setReference(event.target.value)
                setConfirmed(false)
              }}
              required
            />

            <label className="quote-checkbox">
              <input
                id="delivery-confirmed"
                name="deliveryConfirmed"
                type="checkbox"
                checked={confirmed}
                onChange={(event) =>
                  setConfirmed(event.target.checked)
                }
              />
              I confirm the full order was received by the named receiver.
            </label>

            <button
              className="button button-primary"
              type="submit"
              disabled={busy || mustReload || !confirmed}
            >
              {busy ? 'Confirming delivery...' : 'Confirm delivery'}
            </button>
          </fieldset>
        </form>
      ) : null}
    </section>
  )
}