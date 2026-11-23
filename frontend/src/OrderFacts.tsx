import type { ManagedOrder } from './orderApi'
import './OrderFacts.css'

const money = new Intl.NumberFormat('en-LK', {
  style: 'currency',
  currency: 'LKR',
})

const dates = new Intl.DateTimeFormat('en-LK', {
  timeZone: 'Asia/Colombo',
  dateStyle: 'medium',
  timeStyle: 'short',
})

function channelLabel(value: string): string {
  return value.toLowerCase().replaceAll('_', ' ')
}

export default function OrderFacts({
  order,
}: {
  order: ManagedOrder
}) {
  return (
    <div className="order-facts-grid">
      <section
        className="order-fact-card"
        aria-label="Order cost breakdown"
      >
        <div className="order-fact-heading">
          <span className="order-fact-icon" aria-hidden="true">
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.7"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M6 3h12v18l-3-2-3 2-3-2-3 2V3Z" />
              <path d="M9 7h6M9 11h6M9 15h3" />
            </svg>
          </span>

          <div>
            <span className="order-fact-eyebrow">
              PRICING
            </span>
            <h3>Cost breakdown</h3>
          </div>
        </div>

        <dl className="order-cost-list">
          <div>
            <dt>Quantity</dt>
            <dd>{order.quantityKg} kg</dd>
          </div>

          <div>
            <dt>Produce subtotal</dt>
            <dd>{money.format(order.produceSubtotal)}</dd>
          </div>

          <div>
            <dt>Service fee</dt>
            <dd>{money.format(order.serviceFee)}</dd>
          </div>

          <div>
            <dt>Delivery fee</dt>
            <dd>{money.format(order.deliveryFee)}</dd>
          </div>

          <div className="order-cost-total">
            <dt>Total</dt>
            <dd>{money.format(order.total)}</dd>
          </div>
        </dl>
      </section>

      <section
        className="order-fact-card"
        aria-label="Buyer approval details"
      >
        <div className="order-fact-heading">
          <span className="order-fact-icon" aria-hidden="true">
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.7"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <circle cx="9" cy="8" r="3" />
              <path d="M3 20v-2a6 6 0 0 1 12 0v2" />
              <path d="m16 11 2 2 4-4" />
            </svg>
          </span>

          <div>
            <span className="order-fact-eyebrow">
              BUYER CONFIRMATION
            </span>
            <h3>Buyer approval</h3>
          </div>
        </div>

        <dl className="order-approval-list">
          <div>
            <dt>Contact</dt>
            <dd>{order.buyerContactName}</dd>
          </div>

          <div>
            <dt>Channel</dt>
            <dd>
              <span className="order-channel-pill">
                {channelLabel(order.approvalChannel)}
              </span>
            </dd>
          </div>

          <div className="order-approval-reference">
            <dt>Reference</dt>
            <dd>{order.approvalReference}</dd>
          </div>

          <div>
            <dt>Approved</dt>
            <dd>
              {dates.format(new Date(order.approvedAt))}
              <small>Sri Lanka time</small>
            </dd>
          </div>

          <div>
            <dt>Recorded</dt>
            <dd>
              {dates.format(new Date(order.recordedAt))}
              <small>Sri Lanka time</small>
            </dd>
          </div>
        </dl>
      </section>
    </div>
  )
}