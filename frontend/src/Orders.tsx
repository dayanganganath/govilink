import { useEffect, useState, type FormEvent } from 'react'

import { getOrder, listOrders } from './orderApi'
import type {
  ManagedOrder,
  OrdersPage,
  OrderStatus,
} from './orderApi'

import OrderCollection from './OrderCollection'
import GoodsReceipts from './GoodsReceipts'
import OrderDispatch from './OrderDispatch'
import OrderDelivery from './OrderDelivery'
import OrderCancellation from './OrderCancellation'
import OrderFacts from './OrderFacts'
import OrderOverview, { OrderStatusBadge } from './OrderOverview'

import './Orders.css'

const statuses: OrderStatus[] = [
  'CONFIRMED',
  'COLLECTION_IN_PROGRESS',
  'COLLECTED',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'CANCELLED',
]

const money = new Intl.NumberFormat('en-LK', {
  style: 'currency',
  currency: 'LKR',
})

function label(value: string): string {
  return value.toLowerCase().replaceAll('_', ' ')
}

function message(error: unknown): string {
  return error instanceof Error
    ? error.message
    : 'Unable to load orders. Please try again.'
}

function OrderDetails({ id }: { id: string }) {
  const [order, setOrder] = useState<ManagedOrder | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

    getOrder(id, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) {
          return
        }

        setOrder(result)
        setError('')
        setLoading(false)
      })
      .catch((failure: unknown) => {
        if (controller.signal.aborted) {
          return
        }

        setError(message(failure))
        setLoading(false)
      })

    return () => controller.abort()
  }, [id, revision])

  function reload(): void {
    setOrder(null)
    setError('')
    setLoading(true)
    setRevision((value) => value + 1)
  }

  return (
    <section
      className="panel orders-panel"
      aria-label="Order details"
    >
      <h2>Order details</h2>

      <button
        className="button button-secondary"
        type="button"
        disabled={loading}
        onClick={reload}
      >
        Refresh order
      </button>

      {loading && (
        <p role="status">Loading order details...</p>
      )}

      {error && (
        <p className="notice notice-error" role="alert">
          {error}
        </p>
      )}

      {!loading && !error && order && (
        <>
          <p>
            Order ID:{' '}
            <strong className="quote-id">{order.id}</strong>
          </p>

          <p>
            Quotation ID:{' '}
            <span className="quote-id">
              {order.quotationId}
            </span>
          </p>

          <p>
            Status: <strong>{label(order.status)}</strong>
          </p>

          <OrderOverview order={order} />

          <OrderFacts order={order} />

          <OrderCancellation
            key={`cancellation-${order.id}`}
            order={order}
            onChanged={setOrder}
          />

          {order.status !== 'CANCELLED' && (
            <>
          <OrderCollection
            key={`collection-${order.id}`}
            order={order}
            onChanged={setOrder}
          />

          {order.status !== 'CONFIRMED' && (
            <GoodsReceipts
              key={`receipts-${order.id}`}
              order={order}
              onChanged={setOrder}
            />
          )}

          <OrderDispatch
            key={`dispatch-${order.id}`}
            order={order}
            onChanged={setOrder}
          />

          <OrderDelivery
            key={`delivery-${order.id}`}
            order={order}
            onChanged={setOrder}
          />

            </>
          )}
          <h3>Stock reservations</h3>

          <div className="table-wrap">
            <table aria-label="Order stock reservations">
              <thead>
                <tr>
                  <th scope="col">Farmer</th>
                  <th scope="col">Crop</th>
                  <th scope="col">Quantity kg</th>
                  <th scope="col">Reservation status</th>
                </tr>
              </thead>

              <tbody>
                {order.reservations.map((reservation) => (
                  <tr key={reservation.id}>
                    <td>{reservation.farmerName}</td>
                    <td>{reservation.cropName}</td>
                    <td>{reservation.quantityKg}</td>
                    <td>{label(reservation.status)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  )
}

export default function Orders() {
  const [query, setQuery] = useState<{
    page: number
    status: OrderStatus | ''
    revision: number
  }>({
    page: 0,
    status: '',
    revision: 0,
  })

  const [data, setData] = useState<OrdersPage | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [selectedId, setSelectedId] = useState('')
  const [lookupId, setLookupId] = useState('')

  useEffect(() => {
    const controller = new AbortController()

    listOrders(query.page, query.status, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) {
          return
        }

        setData(result)
        setError('')
        setLoading(false)
      })
      .catch((failure: unknown) => {
        if (controller.signal.aborted) {
          return
        }

        setError(message(failure))
        setLoading(false)
      })

    return () => controller.abort()
  }, [query])

  function change(
    page: number,
    status: OrderStatus | '' = query.status,
  ): void {
    setLoading(true)
    setData(null)
    setError('')

    setQuery((current) => ({
      page,
      status,
      revision: current.revision + 1,
    }))
  }

  function open(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault()

    if (lookupId.trim()) {
      setSelectedId(lookupId.trim())
    }
  }

  return (
    <>
      <section className="page-heading">
        <div>
          <p className="eyebrow">MANAGED SALES</p>
          <h1>Orders</h1>
          <p className="subtitle">
            Review buyer approvals, order totals and reserved stock.
          </p>
        </div>
      </section>

      <section
        className="panel orders-panel"
        aria-label="Orders workspace"
      >
        <div className="orders-toolbar">
          <label htmlFor="orders-status">Order status</label>

          <select
            id="orders-status"
            name="status"
            value={query.status}
            onChange={(event) =>
              change(0, event.target.value as OrderStatus | '')
            }
          >
            <option value="">All statuses</option>

            {statuses.map((status) => (
              <option key={status} value={status}>
                {label(status)}
              </option>
            ))}
          </select>

          <button
            className="button button-secondary"
            type="button"
            disabled={loading}
            onClick={() => change(query.page)}
          >
            Refresh orders
          </button>
        </div>

        {loading && (
          <p role="status">Loading orders...</p>
        )}

        {error && (
          <p className="notice notice-error" role="alert">
            {error}{' '}
            <button
              type="button"
              onClick={() => change(query.page)}
            >
              Retry orders
            </button>
          </p>
        )}

        {!loading && !error && data && (
          <>
            <p>{data.totalElements} orders match this filter.</p>

            {data.content.length === 0 ? (
              <p>No orders on this page.</p>
            ) : (
              <div className="table-wrap">
                <table aria-label="Managed orders">
                  <thead>
                    <tr>
                      <th scope="col">Buyer / district</th>
                      <th scope="col">Required by</th>
                      <th scope="col">Status</th>
                      <th scope="col">Quantity kg</th>
                      <th scope="col">Total</th>
                      <th scope="col">Action</th>
                    </tr>
                  </thead>

                  <tbody>
                    {data.content.map((order) => (
                      <tr key={order.id}>
                        <td>
                          {order.buyerName} · {order.district}
                        </td>

                        <td>{order.requiredBy}</td>
                        <td><OrderStatusBadge status={order.status} /></td>
                        <td>{order.quantityKg}</td>
                        <td>{money.format(order.total)}</td>

                        <td>
                          <button
                            className="button button-secondary"
                            type="button"
                            onClick={() => setSelectedId(order.id)}
                          >
                            View order for {order.buyerName}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <div className="orders-pager">
              <button
                className="button button-secondary"
                type="button"
                disabled={!data.hasPrevious}
                onClick={() => change(data.page - 1)}
              >
                Previous orders
              </button>

              <span>
                Page {data.totalPages ? data.page + 1 : 0}
                {' / '}
                {data.totalPages}
              </span>

              <button
                className="button button-secondary"
                type="button"
                disabled={!data.hasNext}
                onClick={() => change(data.page + 1)}
              >
                Next orders
              </button>
            </div>
          </>
        )}
      </section>

      <form
        className="panel orders-panel"
        onSubmit={open}
      >
        <label htmlFor="saved-order-id">Saved order ID</label>

        <input
          id="saved-order-id"
          name="orderId"
          value={lookupId}
          onChange={(event) => setLookupId(event.target.value)}
          required
        />

        <button
          className="button button-secondary"
          type="submit"
        >
          Open saved order
        </button>
      </form>

      {selectedId && (
        <>
          <button
            className="button button-secondary"
            type="button"
            onClick={() => setSelectedId('')}
          >
            Close order details
          </button>

          <OrderDetails
            key={selectedId}
            id={selectedId}
          />
        </>
      )}
    </>
  )
}