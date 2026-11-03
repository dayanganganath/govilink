import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'

import { listBuyerRequests } from './buyerApi'
import type { BuyerRequest } from './buyerApi'

import { searchHarvests } from './harvestApi'
import type { HarvestListing } from './harvestApi'

import {
  createQuotationDraft,
  getQuotation,
} from './quotationApi'
import type { Quotation } from './quotationApi'

import QuotationIssue from './QuotationIssue'

import './Quotations.css'

type PageInfo = {
  page: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

type DraftLine = {
  harvest: HarvestListing
  quantity: string
  price: string
  confirmed: boolean
}

function message(error: unknown): string {
  return error instanceof Error
    ? error.message
    : 'The request failed. Please try again.'
}

function today(): string {
  const parts = new Intl.DateTimeFormat('en', {
    timeZone: 'Asia/Colombo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date())

  const part = (type: string) =>
    parts.find((value) => value.type === type)?.value

  return `${part('year')}-${part('month')}-${part('day')}`
}

function decimal(
  text: string,
  scale: number,
  integerDigits: number,
  zeroAllowed = false,
) {
  const value = text.trim()

  if (value.length > 32 || !/^\d+(\.\d+)?$/.test(value)) {
    return null
  }

  const [whole, fraction = ''] = value.split('.')

  if (fraction.length > scale) {
    return null
  }

  const units =
    BigInt(whole) * (10n ** BigInt(scale))
    + BigInt(fraction.padEnd(scale, '0'))

  if (
    (!zeroAllowed && units === 0n)
    || units > 10n ** BigInt(integerDigits + scale) - 1n
  ) {
    return null
  }

  return {
    units,
    number: Number(value),
  }
}

function quantityText(units: bigint): string {
  const whole = units / 1000n
  const fraction = String(units % 1000n).padStart(3, '0')

  return `${whole}.${fraction}`
}

function usePage<T>(
  load: (page: number, signal: AbortSignal) => Promise<T>,
) {
  const [query, setQuery] = useState({
    page: 0,
    revision: 0,
  })

  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()

    load(query.page, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) {
          setData(result)
          setError('')
          setLoading(false)
        }
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) {
          setError(message(failure))
          setLoading(false)
        }
      })

    return () => controller.abort()
  }, [load, query])

  function reload(page = query.page) {
    setLoading(true)
    setError('')

    setQuery((previous) => ({
      page,
      revision: previous.revision + 1,
    }))
  }

  return {
    data,
    loading,
    error,
    reload,
  }
}

function Pager({
  data,
  loading,
  label,
  change,
}: {
  data: PageInfo | null
  loading: boolean
  label: string
  change: (page: number) => void
}) {
  return (
    <div className="quote-pager">
      <button
        className="button button-secondary"
        type="button"
        disabled={loading || !data?.hasPrevious}
        onClick={() => data && change(data.page - 1)}
      >
        Previous {label}
      </button>

      <span>
        {data
          ? `Page ${data.totalPages ? data.page + 1 : 0} / ${data.totalPages}`
          : ''}
      </span>

      <button
        className="button button-secondary"
        type="button"
        disabled={loading || !data?.hasNext}
        onClick={() => data && change(data.page + 1)}
      >
        Next {label}
      </button>
    </div>
  )
}

function BuyerPicker({
  choose,
}: {
  choose: (buyer: BuyerRequest) => void
}) {
  const {
    data,
    loading,
    error,
    reload,
  } = usePage(listBuyerRequests)

  return (
    <section className="panel quote-panel">
      <h2>Select a buyer request</h2>

      <button
        className="text-button"
        type="button"
        disabled={loading}
        onClick={() => reload()}
      >
        Refresh buyers
      </button>

      {loading && (
        <p role="status">Loading buyer requests…</p>
      )}

      {error && (
        <p role="alert">
          {error}{' '}
          <button
            type="button"
            onClick={() => reload()}
          >
            Retry buyers
          </button>
        </p>
      )}

      {!loading && !error && data && (
        <>
          {data.content.length === 0 && (
            <p>
              No buyer requests yet. Add one in Buyer requests.
            </p>
          )}

          <div className="quote-cards">
            {data.content.map((buyer) => (
              <article
                className="quote-source"
                key={buyer.id}
              >
                <strong>
                  {buyer.buyerName} · {buyer.cropName}
                </strong>

                <p>
                  {buyer.quantityKg} kg · {buyer.district}
                  {' · '}Required {buyer.requiredBy}
                </p>

                <button
                  className="button button-primary"
                  type="button"
                  disabled={
                    buyer.status !== 'OPEN'
                    || buyer.requiredBy < today()
                  }
                  onClick={() => choose(buyer)}
                >
                  Prepare quote for {buyer.buyerName}
                </button>
              </article>
            ))}
          </div>
        </>
      )}

      <Pager
        data={data}
        loading={loading || Boolean(error)}
        label="buyers"
        change={reload}
      />
    </section>
  )
}

function Sources({
  buyer,
  lines,
  add,
}: {
  buyer: BuyerRequest
  lines: DraftLine[]
  add: (harvest: HarvestListing) => void
}) {
  const load = useCallback(
    (page: number, signal: AbortSignal) =>
      searchHarvests(
        page,
        {
          query: buyer.cropName,
          district: buyer.district,
          status: 'AVAILABLE',
        },
        signal,
      ),
    [buyer.cropName, buyer.district],
  )

  const {
    data,
    loading,
    error,
    reload,
  } = usePage(load)

  const eligible = data?.content.filter((harvest) =>
    harvest.status === 'AVAILABLE'
    && harvest.cropName.trim().toLowerCase()
      === buyer.cropName.trim().toLowerCase()
    && harvest.district.trim().toLowerCase()
      === buyer.district.trim().toLowerCase()
    && harvest.availableFrom <= buyer.requiredBy,
  ) ?? []

  return (
    <section
      className="quote-sources"
      aria-label="Harvest sources"
    >
      <h3>Choose harvest sources</h3>

      <p>
        Sources can supply part of the requested quantity.
        Drafts do not reserve harvests.
      </p>

      <button
        className="text-button"
        type="button"
        disabled={loading}
        onClick={() => reload()}
      >
        Refresh sources
      </button>

      {loading && (
        <p role="status">Loading harvest sources…</p>
      )}

      {error && (
        <p role="alert">
          {error}{' '}
          <button
            type="button"
            onClick={() => reload()}
          >
            Retry sources
          </button>
        </p>
      )}

      {!loading && !error && (
        <div className="quote-cards">
          {eligible.length === 0 && (
            <p>No eligible sources on this result page.</p>
          )}

          {eligible.map((harvest) => (
            <article
              className="quote-source"
              key={harvest.id}
            >
              <strong>{harvest.farmerName}</strong>

              <p>
                {harvest.quantityKg} kg
                {' · '}Available {harvest.availableFrom}
              </p>

              <p>
                {harvest.expectedPricePerKg === null
                  ? 'Farmer price needs confirmation'
                  : `Expected LKR ${harvest.expectedPricePerKg} / kg`}
              </p>

              <button
                className="button button-secondary"
                type="button"
                disabled={
                  lines.length >= 50
                  || lines.some(
                    (line) =>
                      line.harvest.id === harvest.id,
                  )
                }
                onClick={() => add(harvest)}
              >
                Add source {harvest.farmerName}
              </button>
            </article>
          ))}
        </div>
      )}

      <Pager
        data={data}
        loading={loading || Boolean(error)}
        label="sources"
        change={reload}
      />
    </section>
  )
}

function DraftForm({
  buyer,
  saved,
  cancel,
}: {
  buyer: BuyerRequest
  saved: (quote: Quotation) => void
  cancel: () => void
}) {
  const [lines, setLines] = useState<DraftLine[]>([])
  const [serviceFee, setServiceFee] = useState('')
  const [deliveryFee, setDeliveryFee] = useState('')
  const [address, setAddress] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const saving = useRef(false)
  const heading = useRef<HTMLHeadingElement>(null)

  useEffect(() => {
    heading.current?.focus()
  }, [])

  function add(harvest: HarvestListing) {
    setLines((current) => {
      if (
        current.length >= 50
        || current.some(
          (line) => line.harvest.id === harvest.id,
        )
      ) {
        return current
      }

      const demand =
        decimal(String(buyer.quantityKg), 3, 9)?.units
        ?? 0n

      const used = current.reduce(
        (sum, line) =>
          sum
          + (
            decimal(line.quantity, 3, 9)?.units
            ?? 0n
          ),
        0n,
      )

      const remaining =
        demand > used ? demand - used : 0n

      const available =
        decimal(String(harvest.quantityKg), 3, 9)?.units
        ?? 0n

      const quantity =
        remaining < available ? remaining : available

      return [
        ...current,
        {
          harvest,
          quantity:
            quantity > 0n ? quantityText(quantity) : '',
          price:
            harvest.expectedPricePerKg === null
              ? ''
              : String(harvest.expectedPricePerKg),
          confirmed: false,
        },
      ]
    })
  }

  function change(
    index: number,
    update: Partial<DraftLine>,
  ) {
    setLines((current) =>
      current.map((line, position) =>
        position === index
          ? { ...line, ...update }
          : line,
      ),
    )
  }

  async function submit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    if (saving.current) return

    setError('')

    if (!lines.length) {
      setError('Select at least one harvest source.')
      return
    }

    const service = decimal(serviceFee, 2, 10, true)
    const delivery = decimal(deliveryFee, 2, 10, true)

    if (!service || !delivery) {
      setError(
        'Enter valid service and delivery fees, including 0 when no fee applies.',
      )
      return
    }

    const demand = decimal(
      String(buyer.quantityKg),
      3,
      9,
    )

    const budget =
      buyer.maxPricePerKg === null
        ? null
        : decimal(
          String(buyer.maxPricePerKg),
          2,
          10,
        )

    let totalQuantity = 0n

    const selected = []

    for (const line of lines) {
      const quantity = decimal(line.quantity, 3, 9)
      const price = decimal(line.price, 2, 10)

      const available = decimal(
        String(line.harvest.quantityKg),
        3,
        9,
      )

      if (
        !quantity
        || !price
        || !available
        || quantity.units > available.units
      ) {
        setError(
          'Check each source: quantity must be positive, within the listed stock and use at most 3 decimals. Price must be positive and use at most 2 decimals.',
        )
        return
      }

      if (budget && price.units > budget.units) {
        setError(
          'A farmer price exceeds the buyer’s maximum price per kg.',
        )
        return
      }

      totalQuantity += quantity.units

      selected.push({
        harvestListingId: line.harvest.id,
        quantityKg: quantity.number,
        farmerPricePerKg: price.number,
        priceConfirmed: line.confirmed,
      })
    }

    if (!demand || totalQuantity > demand.units) {
      setError(
        'Selected quantities exceed the buyer request.',
      )
      return
    }

    saving.current = true
    setBusy(true)

    try {
      const result = await createQuotationDraft({
        buyerRequestId: buyer.id,
        serviceFee: service.number,
        deliveryFee: delivery.number,
        deliveryAddress: address.trim() || null,
        lines: selected,
      })

      saved(result)
    } catch (failure) {
      setError(message(failure))
    } finally {
      saving.current = false
      setBusy(false)
    }
  }

  return (
    <section className="panel quote-panel">
      <h2 ref={heading} tabIndex={-1}>
        Draft quotation for {buyer.buyerName}
      </h2>

      <p>
        {buyer.cropName} · {buyer.quantityKg} kg
        {' · '}{buyer.district}
        {' · '}Required {buyer.requiredBy}
      </p>

      {buyer.maxPricePerKg !== null && (
        <p>
          Buyer price limit: LKR {buyer.maxPricePerKg} / kg.
          Service and delivery fees are listed separately.
        </p>
      )}

      <button
        className="text-button"
        type="button"
        disabled={busy}
        onClick={cancel}
      >
        Discard unsaved draft
      </button>

      <form noValidate onSubmit={submit}>
        <fieldset
          className="quote-fieldset"
          disabled={busy}
        >
          <Sources
            buyer={buyer}
            lines={lines}
            add={add}
          />

          <h3>Selected sources</h3>

          {lines.length === 0 && (
            <p>No sources selected.</p>
          )}

          {lines.map((line, index) => (
            <section
              className="quote-line"
              key={line.harvest.id}
              aria-label={`Source ${index + 1}`}
            >
              <strong>{line.harvest.farmerName}</strong>

              <label>
                Quantity kg — source {index + 1}
                <input
                  type="number"
                  min="0.001"
                  step="0.001"
                  value={line.quantity}
                  onChange={(event) =>
                    change(index, {
                      quantity: event.target.value,
                    })
                  }
                />
              </label>

              <label>
                Farmer price LKR/kg — source {index + 1}
                <input
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={line.price}
                  onChange={(event) =>
                    change(index, {
                      price: event.target.value,
                      confirmed: false,
                    })
                  }
                />
              </label>

              <label className="quote-checkbox">
                <input
                  type="checkbox"
                  checked={line.confirmed}
                  onChange={(event) =>
                    change(index, {
                      confirmed: event.target.checked,
                    })
                  }
                />
                Price agreed with farmer — source {index + 1}
              </label>

              <button
                className="text-button"
                type="button"
                onClick={() =>
                  setLines((current) =>
                    current.filter(
                      (_, position) => position !== index,
                    ),
                  )
                }
              >
                Remove source {index + 1}
              </button>
            </section>
          ))}

          <div className="quote-fees">
            <label>
              Service fee — LKR
              <input
                type="number"
                min="0"
                step="0.01"
                value={serviceFee}
                onChange={(event) =>
                  setServiceFee(event.target.value)
                }
              />
            </label>

            <label>
              Delivery fee — LKR
              <input
                type="number"
                min="0"
                step="0.01"
                value={deliveryFee}
                onChange={(event) =>
                  setDeliveryFee(event.target.value)
                }
              />
            </label>
          </div>

          <label>
            Delivery address — optional
            <textarea
              maxLength={500}
              value={address}
              onChange={(event) =>
                setAddress(event.target.value)
              }
            />
          </label>

          <p>
            Prices can remain unconfirmed in a draft.
            Review the saved totals before issuing a quotation.
          </p>

          {error && (
            <p
              className="notice notice-error"
              role="alert"
            >
              {error}
            </p>
          )}

          <button
            className="button button-primary"
            type="submit"
          >
            {busy
              ? 'Saving draft…'
              : 'Save draft quotation'}
          </button>
        </fieldset>
      </form>
    </section>
  )
}

export default function Quotations() {
  const [buyer, setBuyer] =
    useState<BuyerRequest | null>(null)

  const [quote, setQuote] =
    useState<Quotation | null>(null)

  const [id, setId] = useState('')
  const [lookupError, setLookupError] = useState('')
  const [lookingUp, setLookingUp] = useState(false)

  const lookup =
    useRef<AbortController | null>(null)

  const resultHeading =
    useRef<HTMLHeadingElement>(null)

  useEffect(() => {
    if (quote) {
      resultHeading.current?.focus()
    }
  }, [quote])

  useEffect(() => {
    return () => lookup.current?.abort()
  }, [])

  async function open(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    if (!id.trim() || lookingUp) {
      return
    }

    lookup.current?.abort()

    const controller = new AbortController()

    lookup.current = controller

    setLookingUp(true)
    setLookupError('')

    try {
      const result = await getQuotation(
        id.trim(),
        controller.signal,
      )

      if (!controller.signal.aborted) {
        setQuote(result)
      }
    } catch (failure) {
      if (!controller.signal.aborted) {
        setLookupError(message(failure))
      }
    } finally {
      if (!controller.signal.aborted) {
        setLookingUp(false)
      }
    }
  }

  const money = new Intl.NumberFormat('en-LK', {
    style: 'currency',
    currency: 'LKR',
  })

  return (
    <>
      <section className="page-heading">
        <div>
          <p className="eyebrow">MANAGED SALES</p>

          <h1>Quotations</h1>

          <p className="subtitle">
            Prepare clear prices for produce, coordination and delivery.
          </p>
        </div>
      </section>

      {quote ? (
        <section
          className="panel quote-panel"
          aria-label="Saved quotation"
        >
          <p role="status">
            Quotation {quote.status}
          </p>

          <h2 ref={resultHeading} tabIndex={-1}>
            {quote.buyerName} · {quote.district}
          </h2>

          <p>
            Quotation ID:{' '}
            <strong className="quote-id">
              {quote.id}
            </strong>
          </p>

          <p>
            {quote.quantityKg} kg
            {' · '}Required {quote.requiredBy}
          </p>

          <div className="table-wrap">
            <table aria-label="Quotation lines">
              <thead>
                <tr>
                  <th>Farmer / crop</th>
                  <th>Quantity kg</th>
                  <th>LKR / kg</th>
                  <th>Subtotal</th>
                  <th>Price</th>
                </tr>
              </thead>

              <tbody>
                {quote.lines.map((line) => (
                  <tr key={line.id}>
                    <td>
                      {line.farmerName} · {line.cropName}
                    </td>

                    <td>{line.quantityKg}</td>

                    <td>
                      {money.format(line.farmerPricePerKg)}
                    </td>

                    <td>
                      {money.format(line.lineSubtotal)}
                    </td>

                    <td>
                      {line.priceConfirmed
                        ? 'Confirmed'
                        : 'Needs confirmation'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <dl className="quote-totals">
            <div>
              <dt>Produce subtotal</dt>
              <dd>
                {money.format(quote.produceSubtotal)}
              </dd>
            </div>

            <div>
              <dt>Service fee</dt>
              <dd>
                {money.format(quote.serviceFee)}
              </dd>
            </div>

            <div>
              <dt>Delivery fee</dt>
              <dd>
                {money.format(quote.deliveryFee)}
              </dd>
            </div>

            <div>
              <dt>Total</dt>
              <dd>
                {money.format(quote.total)}
              </dd>
            </div>
          </dl>

          {quote.priceConfirmationNeeded && (
            <p className="notice">
              Farmer price confirmation is still needed.
            </p>
          )}

          <p>
            {quote.status === 'DRAFT'
              ? 'This draft has not reserved stock or approved an order.'
              : `Current status: ${quote.status}`}
          </p>

          <QuotationIssue
            key={`${quote.id}:${quote.version}`}
            quotation={quote}
            onChanged={setQuote}
          />

          <button
            className="button button-secondary"
            type="button"
            onClick={() => {
              setQuote(null)
              setBuyer(null)
              setId('')
            }}
          >
            Prepare another quotation
          </button>
        </section>
      ) : buyer ? (
        <DraftForm
          key={buyer.id}
          buyer={buyer}
          saved={setQuote}
          cancel={() => setBuyer(null)}
        />
      ) : (
        <>
          <form
            className="panel quote-panel quote-lookup"
            onSubmit={open}
          >
            <label>
              Quotation ID
              <input
                value={id}
                onChange={(event) =>
                  setId(event.target.value)
                }
                required
              />
            </label>

            <button
              className="button button-secondary"
              type="submit"
              disabled={lookingUp}
            >
              {lookingUp
                ? 'Opening…'
                : 'Open saved quotation'}
            </button>

            {lookupError && (
              <p role="alert">{lookupError}</p>
            )}
          </form>

          <BuyerPicker
            choose={(value) => {
              lookup.current?.abort()
              setLookingUp(false)
              setLookupError('')
              setBuyer(value)
            }}
          />
        </>
      )}
    </>
  )
}