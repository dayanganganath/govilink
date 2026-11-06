import { useRef, useState } from 'react'
import type { FormEvent } from 'react'

import { approveOrder } from './orderApi'
import type {
  ApprovalChannel,
  ManagedOrder,
} from './orderApi'

import { getQuotation } from './quotationApi'
import type { Quotation } from './quotationApi'

type Props = {
  quotation: Quotation
  onChanged: (quotation: Quotation) => void
}

function message(error: unknown): string {
  return error instanceof Error
    ? error.message
    : 'The request failed. Reload before trying again.'
}

const money = new Intl.NumberFormat('en-LK', {
  style: 'currency',
  currency: 'LKR',
})

export default function BuyerApproval({
  quotation,
  onChanged,
}: Props) {
  const [contact, setContact] = useState('')
  const [channel, setChannel] =
    useState<ApprovalChannel>('PHONE')
  const [reference, setReference] = useState('')
  const [approvedAt, setApprovedAt] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  const [busy, setBusy] = useState(false)
  const [needsReload, setNeedsReload] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [order, setOrder] = useState<ManagedOrder | null>(null)

  const working = useRef(false)

  async function reload() {
    if (working.current) return

    working.current = true
    setBusy(true)
    setError('')

    try {
      const latest = await getQuotation(quotation.id)

      setConfirmed(false)
      setNeedsReload(false)
      setNotice('Latest quotation loaded. Review before confirming.')
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

    if (working.current || needsReload || order) return

    setError('')
    setNotice('')

    if (quotation.status !== 'ISSUED') {
      setError('Only an issued quotation can be approved.')
      return
    }

    if (
      !quotation.expiresAt
      || new Date(quotation.expiresAt).getTime() <= Date.now()
    ) {
      setError('This quotation has expired. Reload its latest status.')
      return
    }

    if (!contact.trim() || !reference.trim()) {
      setError('Enter the buyer contact name and approval reference.')
      return
    }

    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(approvedAt)) {
      setError('Enter the approval date and time.')
      return
    }

    const approvalTime = new Date(`${approvedAt}:00+05:30`)

    if (
      Number.isNaN(approvalTime.getTime())
      || approvalTime.getTime() > Date.now()
    ) {
      setError('Approval time must not be in the future.')
      return
    }

    if (
      !quotation.issuedAt
      || approvalTime.getTime()
        < new Date(quotation.issuedAt).getTime()
    ) {
      setError('Approval time cannot be before the quotation was issued.')
      return
    }

    if (!confirmed) {
      setError('Confirm that the buyer approved this quotation and total.')
      return
    }

    working.current = true
    setBusy(true)

    try {
      const created = await approveOrder(quotation.id, {
        quotationVersion: quotation.version,
        buyerContactName: contact.trim(),
        channel,
        reference: reference.trim(),
        approvedAt: approvalTime.toISOString(),
      })

      setOrder(created)

      try {
        const latest = await getQuotation(quotation.id)
        onChanged(latest)
      } catch {
        setNotice(
          'Order confirmed. Reload the quotation to update its displayed status.',
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

  if (order) {
    return (
      <section aria-label="Confirmed managed order">
        <h3>Order confirmed</h3>

        <p role="status">
          Stock reserved for {order.quantityKg} kg.
        </p>

        <p>
          Order ID: <strong className="quote-id">{order.id}</strong>
        </p>

        <p>Total: {money.format(order.total)}</p>

        <p>
          Approval recorded for {order.buyerContactName}.
          Collection and delivery still need to be coordinated.
        </p>

        {notice && <p>{notice}</p>}

        <button
          className="button button-secondary"
          type="button"
          disabled={busy}
          onClick={() => void reload()}
        >
          Refresh order quotation
        </button>

        {error && <p role="alert">{error}</p>}
      </section>
    )
  }

  if (quotation.status === 'ACCEPTED') {
    return (
      <section aria-label="Buyer approval">
        <h3>Buyer approval recorded</h3>
        <p>
          This quotation has already been accepted.
          Another approval cannot create a second order.
        </p>
      </section>
    )
  }

  if (quotation.status !== 'ISSUED') return null

  return (
    <section aria-label="Buyer approval">
      <h3>Record buyer approval</h3>

      <p>
        Record the buyer’s approval of this quotation and its total of{' '}
        <strong>{money.format(quotation.total)}</strong>.
        Confirming creates an order and reserves the selected stock.
      </p>

      {notice && <p role="status">{notice}</p>}

      {error && (
        <p className="notice notice-error" role="alert">
          {error}
        </p>
      )}

      {needsReload && (
        <p>
          Reload before trying again. The previous request may have
          reached the server.
        </p>
      )}

      <button
        className="button button-secondary"
        type="button"
        disabled={busy}
        onClick={() => void reload()}
      >
        Reload approval quotation
      </button>

      <form noValidate onSubmit={submit}>
        <fieldset
          className="quote-fieldset"
          disabled={busy || needsReload}
        >
          <label htmlFor="approval-contact">
            Buyer contact name
          </label>
          <input
            id="approval-contact"
            name="buyerContactName"
            maxLength={150}
            value={contact}
            onChange={(event) => setContact(event.target.value)}
            required
          />

          <label htmlFor="approval-channel">
            Approval channel
          </label>
          <select
            id="approval-channel"
            name="channel"
            value={channel}
            onChange={(event) =>
              setChannel(event.target.value as ApprovalChannel)
            }
          >
            <option value="PHONE">Phone call</option>
            <option value="WHATSAPP">WhatsApp</option>
            <option value="EMAIL">Email</option>
            <option value="IN_PERSON">In person</option>
          </select>

          <label htmlFor="approval-reference">
            Approval reference or notes
          </label>
          <textarea
            id="approval-reference"
            name="reference"
            maxLength={500}
            value={reference}
            onChange={(event) => setReference(event.target.value)}
            required
          />

          <label htmlFor="approval-time">
            Buyer approval date and time — Sri Lanka time
          </label>
          <input
            id="approval-time"
            name="approvedAt"
            type="datetime-local"
            step="60"
            value={approvedAt}
            onChange={(event) => setApprovedAt(event.target.value)}
            required
          />

          <p>
            Enter when the buyer approved, after this quotation was issued.
          </p>

          <label className="quote-checkbox">
            <input
              name="approvalConfirmed"
              type="checkbox"
              checked={confirmed}
              onChange={(event) => setConfirmed(event.target.checked)}
            />
            I confirm the buyer approved this quotation and total.
          </label>

          <button
            className="button button-primary"
            type="submit"
            disabled={!confirmed}
          >
            {busy ? 'Confirming order…' : 'Confirm order and reserve stock'}
          </button>
        </fieldset>
      </form>
    </section>
  )
}