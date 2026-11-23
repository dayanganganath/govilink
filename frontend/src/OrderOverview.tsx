import type { ManagedOrder, OrderStatus } from './orderApi'
import './OrderOverview.css'

const stages: {
  status: OrderStatus
  title: string
}[] = [
  { status: 'CONFIRMED', title: 'Confirmed' },
  { status: 'COLLECTION_IN_PROGRESS', title: 'Collecting' },
  { status: 'COLLECTED', title: 'Collected' },
  { status: 'OUT_FOR_DELIVERY', title: 'Out for delivery' },
  { status: 'DELIVERED', title: 'Delivered' },
]

const statusLabels: Record<OrderStatus, string> = {
  CONFIRMED: 'confirmed',
  COLLECTION_IN_PROGRESS: 'collection in progress',
  COLLECTED: 'collected',
  OUT_FOR_DELIVERY: 'out for delivery',
  DELIVERED: 'delivered',
  CANCELLED: 'cancelled',
}

const guidance: Record<OrderStatus, string> = {
  CONFIRMED:
    'Arrange the farmer pickup, then start collection below.',
  COLLECTION_IN_PROGRESS:
    'Record goods as they arrive. Collection completes when the full allocation has been received.',
  COLLECTED:
    'All allocated goods have been received. Record dispatch when they leave for delivery.',
  OUT_FOR_DELIVERY:
    'The goods have been dispatched. Coordinate delivery with the buyer.',
  DELIVERED:
    'Delivery has been recorded for this order.',
  CANCELLED:
    'This order is cancelled.',
}

const numbers = new Intl.NumberFormat('en-LK', {
  maximumFractionDigits: 3,
})

const amounts = new Intl.NumberFormat('en-LK', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

export function OrderStatusBadge({
  status,
}: {
  status: OrderStatus
}) {
  return (
    <span className={`order-status-pill order-status-${status.toLowerCase()}`}>
      <span aria-hidden="true" className="order-status-dot" />
      {statusLabels[status]}
    </span>
  )
}

export default function OrderOverview({
  order,
}: {
  order: ManagedOrder
}) {
  const currentIndex = stages.findIndex(
    (stage) => stage.status === order.status,
  )

  const cancelled = order.status === 'CANCELLED'

  return (
    <div className="order-overview">
      <div className="order-summary-grid">
        <div className="order-summary-card">
          <span className="order-summary-label">
            Order quantity
          </span>

          <strong>
            {numbers.format(order.quantityKg)}
            <small> kg</small>
          </strong>

          <span className="order-summary-caption">
            Total allocated quantity
          </span>
        </div>

        <div className="order-summary-card">
          <span className="order-summary-label">
            Harvest sources
          </span>

          <strong>{order.reservations.length}</strong>

          <span className="order-summary-caption">
            Sources allocated to this order
          </span>
        </div>

        <div className="order-summary-card order-summary-accent">
          <span className="order-summary-label">
            Order value
          </span>

          <strong>
            <small>LKR </small>
            {amounts.format(order.total)}
          </strong>

          <span className="order-summary-caption">
            Includes service and delivery fees
          </span>
        </div>
      </div>

      <div className="order-progress-card">
        <div className="order-progress-heading">
          <div>
            <span className="order-progress-eyebrow">
              FULFILMENT
            </span>
            <h3>Order progress</h3>
          </div>

          <OrderStatusBadge status={order.status} />
        </div>

        {!cancelled && (
          <ol className="order-progress-list" aria-label="Order progress">
            {stages.map((stage, index) => {
              const complete = index < currentIndex
              const current = index === currentIndex

              const state = complete
                ? 'complete'
                : current
                  ? 'current'
                  : 'upcoming'

              return (
                <li
                  key={stage.status}
                  className={`order-progress-step is-${state}`}
                  aria-current={current ? 'step' : undefined}
                >
                  <span className="order-progress-marker" aria-hidden="true">
                    {complete ? '✓' : index + 1}
                  </span>

                  <span className="order-progress-title">
                    {stage.title}
                  </span>

                  <span className="order-progress-state">
                    {complete
                      ? 'Completed'
                      : current
                        ? 'Current stage'
                        : 'Upcoming'}
                  </span>
                </li>
              )
            })}
          </ol>
        )}

        <p className="order-next-step">
          {guidance[order.status]}
        </p>
      </div>
    </div>
  )
}