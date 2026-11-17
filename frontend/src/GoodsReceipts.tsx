import { useEffect, useRef, useState } from 'react'
import { ApiError } from './http'
import { getOrder, type ManagedOrder } from './orderApi'
import {
  getGoodsReceipt,
  listGoodsReceipts,
  recordGoodsReceipt,
  type CreateGoodsReceipt,
  type GoodsReceipt,
  type GoodsReceiptPage,
} from './goodsReceiptApi'

type Props = {
  order: ManagedOrder
  onChanged: (order: ManagedOrder) => void
}

const dateFormatter = new Intl.DateTimeFormat('en-LK', {
  timeZone: 'Asia/Colombo',
  dateStyle: 'medium',
  timeStyle: 'medium',
})

const quantityFormatter = new Intl.NumberFormat('en-LK', {
  maximumFractionDigits: 3,
})

function storageKey(orderId: string): string {
  return `govilink:goods-receipt:${orderId}`
}

function formatDate(value: string): string {
  const date = new Date(value)

  return Number.isNaN(date.getTime())
    ? value
    : dateFormatter.format(date)
}

function errorMessage(error: unknown): string {
  return error instanceof Error
    ? error.message
    : 'The request could not be completed. Please try again.'
}

function readPending(orderId: string): CreateGoodsReceipt | null {
  try {
    const stored = sessionStorage.getItem(storageKey(orderId))

    if (!stored) {
      return null
    }

    const value: unknown = JSON.parse(stored)

    if (!value || typeof value !== 'object') {
      return null
    }

    const candidate = value as Record<string, unknown>

    if (
      typeof candidate.requestId !== 'string' ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
        candidate.requestId,
      ) ||
      typeof candidate.version !== 'number' ||
      !Number.isSafeInteger(candidate.version) ||
      candidate.version < 0 ||
      typeof candidate.reservationId !== 'string' ||
      !candidate.reservationId ||
      typeof candidate.quantityKg !== 'number' ||
      !Number.isFinite(candidate.quantityKg) ||
      candidate.quantityKg <= 0 ||
      candidate.quantityKg > 999999999.999 ||
      typeof candidate.receivedAt !== 'string' ||
      Number.isNaN(new Date(candidate.receivedAt).getTime()) ||
      typeof candidate.reference !== 'string' ||
      !candidate.reference.trim() ||
      candidate.reference.length > 500
    ) {
      return null
    }

    return {
      requestId: candidate.requestId,
      version: candidate.version,
      reservationId: candidate.reservationId,
      quantityKg: candidate.quantityKg,
      receivedAt: candidate.receivedAt,
      reference: candidate.reference,
    }
  } catch {
    return null
  }
}

function removePending(orderId: string): void {
  try {
    sessionStorage.removeItem(storageKey(orderId))
  } catch {
    // Rechecking the same request remains safe if storage is unavailable.
  }
}

export default function GoodsReceipts({ order, onChanged }: Props) {
  const [query, setQuery] = useState({ page: 0, revision: 0 })
  const [data, setData] = useState<GoodsReceiptPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)

  const [reservationId, setReservationId] = useState('')
  const [quantity, setQuantity] = useState('')
  const [receivedAt, setReceivedAt] = useState('')
  const [reference, setReference] = useState('')
  const [confirmed, setConfirmed] = useState(false)

  const [pending, setPending] = useState<CreateGoodsReceipt | null>(
    () => readPending(order.id),
  )

  const working = useRef(false)

  useEffect(() => {
    const controller = new AbortController()
    let active = true

    setLoading(true)
    setLoadError('')

    listGoodsReceipts(order.id, query.page, controller.signal)
      .then((result) => {
        if (active) {
          setData(result)
        }
      })
      .catch((failure: unknown) => {
        if (active && !controller.signal.aborted) {
          setData(null)
          setLoadError(errorMessage(failure))
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false)
        }
      })

    return () => {
      active = false
      controller.abort()
    }
  }, [order.id, query.page, query.revision])

  function reload(page = query.page): void {
    setLoading(true)
    setData(null)
    setLoadError('')
    setConfirmed(false)

    setQuery((current) => ({
      page,
      revision: current.revision + 1,
    }))
  }

  async function saved(receipt: GoodsReceipt): Promise<void> {
    removePending(order.id)
    setPending(null)
    setReservationId('')
    setQuantity('')
    setReceivedAt('')
    setReference('')
    setConfirmed(false)
    setError('')
    setNotice(`Receipt saved: ${receipt.id}`)

    reload(0)

    try {
      const updatedOrder = await getOrder(order.id)
      onChanged(updatedOrder)
    } catch {
      setNotice(
        `Receipt saved: ${receipt.id}. The order could not be refreshed. Refresh the order to check its latest status.`,
      )
    }
  }

  async function send(payload: CreateGoodsReceipt): Promise<void> {
    if (working.current) {
      return
    }

    working.current = true
    setBusy(true)
    setError('')
    setNotice('')

    try {
      const receipt = await recordGoodsReceipt(order.id, payload)
      await saved(receipt)
    } catch (failure: unknown) {
      if (
        failure instanceof ApiError &&
        (failure.status === 400 || failure.status === 409)
      ) {
        removePending(order.id)
        setPending(null)
        reload(0)
        setError(
          `${errorMessage(failure)} Review the refreshed balances before submitting again.`,
        )
      } else {
        setError(
          `${errorMessage(failure)} The receipt result is uncertain. Check the receipt result or retry the same request below.`,
        )
      }
    } finally {
      working.current = false
      setBusy(false)
    }
  }

  async function checkPending(): Promise<void> {
    if (!pending || working.current) {
      return
    }

    working.current = true
    setBusy(true)
    setError('')
    setNotice('')

    try {
      const receipt = await getGoodsReceipt(order.id, pending.requestId)
      await saved(receipt)
    } catch (failure: unknown) {
      if (failure instanceof ApiError && failure.status === 404) {
        setError(
          'No saved receipt was found yet. Retry the same receipt request to resolve it safely.',
        )
      } else {
        setError(errorMessage(failure))
      }
    } finally {
      working.current = false
      setBusy(false)
    }
  }

  function submit(event: React.FormEvent<HTMLFormElement>): void {
    event.preventDefault()

    if (
      working.current ||
      pending ||
      loading ||
      loadError ||
      !data
    ) {
      return
    }

    setError('')
    setNotice('')

    if (data.orderStatus !== 'COLLECTION_IN_PROGRESS') {
      setError('Goods can only be received while collection is in progress.')
      return
    }

    const reservation = data.reservations.find(
      (item) => item.id === reservationId && item.status === 'ACTIVE',
    )

    if (!reservation) {
      setError('Select an active harvest reservation.')
      return
    }

    const enteredQuantity = quantity.trim()

    if (!/^\d{1,9}(\.\d{1,3})?$/.test(enteredQuantity)) {
      setError('Enter a positive quantity with up to three decimal places.')
      return
    }

    const quantityKg = Number(enteredQuantity)

    if (!Number.isFinite(quantityKg) || quantityKg <= 0) {
      setError('Enter a quantity greater than zero.')
      return
    }

    if (
      Math.round(quantityKg * 1000) >
      Math.round(reservation.remainingQuantityKg * 1000)
    ) {
      setError('Received quantity cannot exceed the remaining allocation.')
      return
    }

    if (
      !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(receivedAt)
    ) {
      setError('Enter the received date and time in Sri Lanka time.')
      return
    }

    const receivedDate = new Date(`${receivedAt}+05:30`)

    if (
      Number.isNaN(receivedDate.getTime()) ||
      receivedDate.getTime() > Date.now()
    ) {
      setError('Received date and time must be valid and cannot be in the future.')
      return
    }

    const trimmedReference = reference.trim()

    if (!trimmedReference || trimmedReference.length > 500) {
      setError('Enter a goods receipt reference of up to 500 characters.')
      return
    }

    if (!confirmed) {
      setError('Confirm that these goods and this quantity were received.')
      return
    }

    let payload: CreateGoodsReceipt

    try {
      payload = {
        requestId: crypto.randomUUID(),
        version: data.orderVersion,
        reservationId: reservation.id,
        quantityKg,
        receivedAt: receivedDate.toISOString(),
        reference: trimmedReference,
      }

      sessionStorage.setItem(
        storageKey(order.id),
        JSON.stringify(payload),
      )
    } catch {
      setError(
        'The receipt request could not be saved in this browser. Enable session storage and try again.',
      )
      return
    }

    setPending(payload)
    void send(payload)
  }

  return (
    <section aria-label="Goods receipts">
      <h3>Goods receipts</h3>

      <button
        type="button"
        onClick={() => reload()}
        disabled={loading || busy}
      >
        Refresh receipts
      </button>

      {notice && <p role="status">{notice}</p>}

      {error && (
        <p role="alert" className="error-message">
          {error}
        </p>
      )}

      {pending && (
        <div className="panel">
          <h4>Receipt awaiting confirmation</h4>

          <p>
            Quantity: {quantityFormatter.format(pending.quantityKg)} kg
          </p>

          <p>Reference: {pending.reference}</p>

          <p>
            Check whether this receipt was saved before recording another
            receipt. Retrying sends the same request to prevent duplicates.
          </p>

          <button
            type="button"
            onClick={() => void checkPending()}
            disabled={busy}
          >
            Check receipt result
          </button>

          <button
            type="button"
            onClick={() => void send(pending)}
            disabled={busy}
          >
            Retry same receipt
          </button>
        </div>
      )}

      {loading && <p role="status">Loading goods receipts…</p>}

      {loadError && (
        <div role="alert">
          <p>{loadError}</p>

          <button
            type="button"
            onClick={() => reload()}
            disabled={busy}
          >
            Retry loading receipts
          </button>
        </div>
      )}

      {!loading && data && (
        <>
          <h4>Collection balances</h4>

          <div className="table-scroll">
            <table aria-label="Collection balances">
              <thead>
                <tr>
                  <th scope="col">Farmer / crop</th>
                  <th scope="col">Allocated kg</th>
                  <th scope="col">Collected kg</th>
                  <th scope="col">Remaining kg</th>
                  <th scope="col">Status</th>
                </tr>
              </thead>

              <tbody>
                {data.reservations.map((reservation) => (
                  <tr key={reservation.id}>
                    <td>
                      {reservation.farmerName} · {reservation.cropName}
                    </td>
                    <td>
                      {quantityFormatter.format(reservation.quantityKg)}
                    </td>
                    <td>
                      {quantityFormatter.format(
                        reservation.collectedQuantityKg,
                      )}
                    </td>
                    <td>
                      {quantityFormatter.format(
                        reservation.remainingQuantityKg,
                      )}
                    </td>
                    <td>{reservation.status.toLowerCase()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {data.orderStatus === 'COLLECTED' && (
            <p role="status">
              Collection complete. All reserved quantities have been received.
            </p>
          )}

          {data.orderStatus === 'COLLECTION_IN_PROGRESS' && !pending && (
            <form onSubmit={submit}>
              <h4>Record received goods</h4>

              <fieldset disabled={busy}>
                <label htmlFor="receipt-reservation">
                  Harvest reservation
                </label>

                <select
                  id="receipt-reservation"
                  name="reservationId"
                  value={reservationId}
                  onChange={(event) => {
                    setReservationId(event.target.value)
                    setConfirmed(false)
                  }}
                  required
                >
                  <option value="">Select a reservation</option>

                  {data.reservations
                    .filter(
                      (reservation) =>
                        reservation.status === 'ACTIVE' &&
                        reservation.remainingQuantityKg > 0,
                    )
                    .map((reservation) => (
                      <option
                        key={reservation.id}
                        value={reservation.id}
                      >
                        {reservation.farmerName} · {reservation.cropName}
                        {' · Remaining: '}
                        {quantityFormatter.format(
                          reservation.remainingQuantityKg,
                        )}
                        {' kg'}
                      </option>
                    ))}
                </select>

                <label htmlFor="receipt-quantity">
                  Received quantity — kg
                </label>

                <input
                  id="receipt-quantity"
                  name="quantityKg"
                  type="number"
                  min="0.001"
                  max="999999999.999"
                  step="0.001"
                  value={quantity}
                  onChange={(event) => {
                    setQuantity(event.target.value)
                    setConfirmed(false)
                  }}
                  required
                />

                <label htmlFor="receipt-received-at">
                  Received date and time — Sri Lanka time
                </label>

                <input
                  id="receipt-received-at"
                  name="receivedAt"
                  type="datetime-local"
                  step="1"
                  value={receivedAt}
                  onChange={(event) => {
                    setReceivedAt(event.target.value)
                    setConfirmed(false)
                  }}
                  required
                />

                <label htmlFor="receipt-reference">
                  Goods receipt reference
                </label>

                <textarea
                  id="receipt-reference"
                  name="reference"
                  maxLength={500}
                  value={reference}
                  onChange={(event) => {
                    setReference(event.target.value)
                    setConfirmed(false)
                  }}
                  required
                />

                <label className="quote-checkbox">
                  <input
                    id="receipt-confirmed"
                    name="confirmed"
                    type="checkbox"
                    checked={confirmed}
                    onChange={(event) =>
                      setConfirmed(event.target.checked)
                    }
                  />
                  I confirm these goods and this quantity were received.
                </label>

                <p>
                  Enter the actual quantity received. The remaining
                  allocation stays reserved.
                </p>

                <button type="submit" disabled={!confirmed || busy}>
                  {busy ? 'Recording receipt…' : 'Record goods receipt'}
                </button>
              </fieldset>
            </form>
          )}

          <h4>Receipt history</h4>

          {data.content.length === 0 ? (
            <p>No goods receipts recorded.</p>
          ) : (
            <div className="table-scroll">
              <table aria-label="Goods receipt history">
                <thead>
                  <tr>
                    <th scope="col">Receipt ID</th>
                    <th scope="col">Quantity kg</th>
                    <th scope="col">Received — Sri Lanka time</th>
                    <th scope="col">Recorded by staff</th>
                    <th scope="col">Reference</th>
                  </tr>
                </thead>

                <tbody>
                  {data.content.map((receipt) => (
                    <tr key={receipt.id}>
                      <td>{receipt.id}</td>
                      <td>
                        {quantityFormatter.format(receipt.quantityKg)}
                      </td>
                      <td>{formatDate(receipt.receivedAt)}</td>
                      <td>{receipt.receivedBy}</td>
                      <td>{receipt.reference}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="pagination">
            <button
              type="button"
              disabled={!data.hasPrevious || busy}
              onClick={() => reload(Math.max(0, data.page - 1))}
            >
              Previous receipts
            </button>

            <span>
              Page {data.totalPages === 0 ? 0 : data.page + 1}
              {' / '}
              {data.totalPages}
            </span>

            <button
              type="button"
              disabled={!data.hasNext || busy}
              onClick={() => reload(data.page + 1)}
            >
              Next receipts
            </button>
          </div>
        </>
      )}
    </section>
  )
}