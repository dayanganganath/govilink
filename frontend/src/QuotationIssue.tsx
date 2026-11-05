import { useRef, useState } from 'react'
import type { FormEvent } from 'react'

import {
  getQuotation,
  issueQuotation,
} from './quotationApi'
import type { Quotation } from './quotationApi'

type Props = {
  quotation: Quotation
  onChanged: (quotation: Quotation) => void
}

function errorMessage(error: unknown): string {
  return error instanceof Error
    ? error.message
    : 'The request failed. Please reload the quotation.'
}

export default function QuotationIssue({
  quotation,
  onChanged,
}: Props) {
  const [expiry, setExpiry] = useState('')
  const [confirmedIds, setConfirmedIds] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [needsReload, setNeedsReload] = useState(false)
  const [notice, setNotice] = useState('')

  const working = useRef(false)

  const money = new Intl.NumberFormat('en-LK', {
    style: 'currency',
    currency: 'LKR',
  })

  const dateTime = new Intl.DateTimeFormat('en-LK', {
    timeZone: 'Asia/Colombo',
    dateStyle: 'medium',
    timeStyle: 'short',
  })

  const allConfirmed = quotation.lines.length > 0
    && quotation.lines.every((line) =>
      confirmedIds.includes(line.id),
    )

  function toggle(id: string, checked: boolean) {
    setConfirmedIds((current) =>
      checked
        ? [...new Set([...current, id])]
        : current.filter((value) => value !== id),
    )
  }

  async function reload() {
    if (working.current) return

    working.current = true
    setBusy(true)
    setError('')
    setNotice('')

    try {
      const latest = await getQuotation(quotation.id)

      setConfirmedIds([])
      setExpiry('')
      setNeedsReload(false)

      setNotice(
        'Latest quotation loaded. Review prices again before issuing.',
      )

      onChanged(latest)
    } catch (failure) {
      setError(errorMessage(failure))
      setNeedsReload(true)
    } finally {
      working.current = false
      setBusy(false)
    }
  }

  async function submit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    if (working.current || needsReload) return

    setError('')
    setNotice('')

    if (!allConfirmed) {
      setError(
        'Review and confirm every farmer price before issuing.',
      )
      return
    }

    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(expiry)) {
      setError('Enter a valid expiry date and time.')
      return
    }

    const expiresAt = new Date(`${expiry}:00+05:30`)

    if (
      Number.isNaN(expiresAt.getTime())
      || expiresAt.getTime() <= Date.now()
    ) {
      setError('Expiry must be in the future.')
      return
    }

    if (expiry.slice(0, 10) > quotation.requiredBy) {
      setError(
        'Expiry cannot be after the buyer’s required date.',
      )
      return
    }

    working.current = true
    setBusy(true)

    try {
      const issued = await issueQuotation(
        quotation.id,
        {
          version: quotation.version,
          expiresAt: expiresAt.toISOString(),
          confirmedLineIds: [...confirmedIds],
        },
      )

      onChanged(issued)
    } catch (failure) {
      setError(errorMessage(failure))
      setNeedsReload(true)
    } finally {
      working.current = false
      setBusy(false)
    }
  }

  return (
    <section aria-label="Quotation issuance">
      <button
        className="button button-secondary"
        type="button"
        disabled={busy}
        onClick={() => void reload()}
      >
        {busy ? 'Please wait…' : 'Reload quotation'}
      </button>

      {notice && (
        <p role="status">{notice}</p>
      )}

      {error && (
        <p
          className="notice notice-error"
          role="alert"
        >
          {error}
        </p>
      )}

      {quotation.status === 'DRAFT' ? (
        <>
          <h3>Review and issue quotation</h3>

          <p>
            Confirm each price with the farmer and review
            the quotation totals above. Issuing does not
            reserve stock or approve an order.
          </p>

          {needsReload && (
            <p>
              Reload to check the latest status before
              trying again. The previous request may have
              reached the server.
            </p>
          )}

          <form noValidate onSubmit={submit}>
            <fieldset
              className="quote-fieldset"
              disabled={busy || needsReload}
            >
              {quotation.lines.map((line) => (
                <label
                  className="quote-checkbox"
                  key={line.id}
                >
                  <input
                    type="checkbox"
                    checked={confirmedIds.includes(line.id)}
                    onChange={(event) =>
                      toggle(line.id, event.target.checked)
                    }
                  />

                  <span>
                    I confirm the price with {line.farmerName}
                    {' — '}{line.cropName}
                    {' · '}{line.quantityKg} kg
                    {' · '}
                    {money.format(line.farmerPricePerKg)}
                    {' / kg'}
                  </span>
                </label>
              ))}

              <label>
                Expiry date and time — Sri Lanka time
                <input
                  type="datetime-local"
                  step="60"
                  max={`${quotation.requiredBy}T23:59`}
                  value={expiry}
                  onChange={(event) =>
                    setExpiry(event.target.value)
                  }
                  required
                />
              </label>

              <p>
                Choose a future time on or before
                {' '}{quotation.requiredBy}.
                All prices must be reviewed again
                after reloading.
              </p>

              <button
                className="button button-primary"
                type="submit"
                disabled={!allConfirmed || !expiry}
              >
                {busy
                  ? 'Issuing quotation…'
                  : 'Issue quotation'}
              </button>
            </fieldset>
          </form>
        </>
      ) : (
        <section aria-label="Quotation issuance details">
          {quotation.issuedAt && (
            <p>
              Issued:{' '}
              {dateTime.format(
                new Date(quotation.issuedAt),
              )}
              {' '}(Sri Lanka time)
            </p>
          )}

          {quotation.expiresAt && (
            <p>
              Expires:{' '}
              {dateTime.format(
                new Date(quotation.expiresAt),
              )}
              {' '}(Sri Lanka time)
            </p>
          )}

          <p>
            Issuing a quotation does not reserve stock.
            Buyer approval and order processing are
            separate steps.
          </p>
        </section>
      )}
    </section>
  )
}