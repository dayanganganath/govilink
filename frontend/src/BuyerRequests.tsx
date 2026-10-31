import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from './api'
import { createBuyerRequest, listBuyerRequests } from './buyerApi'
import type { BuyerRequestPage } from './buyerApi'
import './BuyerRequests.css'

interface BuyerForm {
  buyerName: string
  phone: string
  cropName: string
  quantityKg: string
  district: string
  requiredBy: string
  maxPricePerKg: string
}

interface FormField {
  name: keyof BuyerForm
  label: string
  type: 'text' | 'tel' | 'number' | 'date'
  required: boolean
  maxLength?: number
  min?: string
  max?: string
  step?: string
  placeholder?: string
}

const fields: FormField[] = [
  {
    name: 'buyerName',
    label: 'Buyer name',
    type: 'text',
    required: true,
    maxLength: 150,
    placeholder: 'Buyer or business name',
  },
  {
    name: 'phone',
    label: 'Phone',
    type: 'tel',
    required: true,
    maxLength: 20,
    placeholder: 'Contact number',
  },
  {
    name: 'cropName',
    label: 'Crop name',
    type: 'text',
    required: true,
    maxLength: 100,
    placeholder: 'e.g. Tomato',
  },
  {
    name: 'quantityKg',
    label: 'Required quantity — kg',
    type: 'number',
    required: true,
    min: '0.001',
    max: '999999999.999',
    step: '0.001',
  },
  {
    name: 'district',
    label: 'District',
    type: 'text',
    required: true,
    maxLength: 100,
    placeholder: 'e.g. Matale',
  },
  {
    name: 'requiredBy',
    label: 'Required by',
    type: 'date',
    required: true,
  },
  {
    name: 'maxPricePerKg',
    label: 'Maximum price — LKR/kg',
    type: 'number',
    required: false,
    min: '0.01',
    max: '9999999999.99',
    step: '0.01',
  },
]

function localDate(daysAhead = 0): string {
  const date = new Date()
  date.setDate(date.getDate() + daysAhead)

  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')

  return `${date.getFullYear()}-${month}-${day}`
}

function emptyForm(): BuyerForm {
  return {
    buyerName: '',
    phone: '',
    cropName: '',
    quantityKg: '',
    district: '',
    requiredBy: localDate(7),
    maxPricePerKg: '',
  }
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'The request failed.'
}

export default function BuyerRequests() {
  const [pagination, setPagination] = useState({
    page: 0,
    revision: 0,
  })
  const [data, setData] = useState<BuyerRequestPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [form, setForm] = useState<BuyerForm>(emptyForm)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [success, setSuccess] = useState('')
  const savingRef = useRef(false)

  useEffect(() => {
    const controller = new AbortController()

    listBuyerRequests(pagination.page, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return
        setData(result)
        setListError('')
        setLoading(false)
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        setListError(errorMessage(error))
        setLoading(false)
      })

    return () => controller.abort()
  }, [pagination])

  function reload(page: number) {
    setLoading(true)
    setListError('')
    setPagination((current) => ({
      page,
      revision: current.revision + 1,
    }))
  }

  function changeField(name: keyof BuyerForm, value: string) {
    setForm((current) => ({ ...current, [name]: value }))

    setFieldErrors((current) => {
      const next = { ...current }
      delete next[name]
      return next
    })
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (savingRef.current) return

    setSaveError('')
    setFieldErrors({})
    setSuccess('')

    const buyerName = form.buyerName.trim()
    const phone = form.phone.trim()
    const cropName = form.cropName.trim()
    const district = form.district.trim()

    if (!buyerName || !phone || !cropName || !district) {
      setSaveError('Enter the buyer name, phone, crop and district.')
      return
    }

    if (!form.requiredBy || form.requiredBy < localDate()) {
      setSaveError('Required by must be today or a future date.')
      return
    }

    const quantity = Number(form.quantityKg)
    const price = form.maxPricePerKg.trim() === ''
      ? null
      : Number(form.maxPricePerKg)

    if (
      !Number.isFinite(quantity) ||
      quantity <= 0 ||
      (price !== null && (!Number.isFinite(price) || price <= 0))
    ) {
      setSaveError('Enter a positive quantity and optional maximum price.')
      return
    }

    savingRef.current = true
    setSaving(true)

    try {
      const created = await createBuyerRequest({
        buyerName,
        phone,
        cropName,
        quantityKg: quantity,
        district,
        requiredBy: form.requiredBy,
        maxPricePerKg: price,
      })

      setSuccess(
        `${created.cropName} request for ${created.buyerName} was added.`,
      )
      setForm(emptyForm())
      reload(0)
    } catch (error: unknown) {
      setSaveError(errorMessage(error))

      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors)
      }
    } finally {
      savingRef.current = false
      setSaving(false)
    }
  }

  const ready = !loading && !listError && data !== null

  const quantityFormat = new Intl.NumberFormat('en-LK', {
    maximumFractionDigits: 3,
  })

  const priceFormat = new Intl.NumberFormat('en-LK', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })

  return (
    <>
      <section className="page-heading">
        <div>
          <p className="eyebrow">UNDERSTAND BUYER DEMAND</p>
          <h1>Buyer requests</h1>
          <p className="subtitle">
            Record what buyers need, where they need it and their price limit.
          </p>
        </div>

        <button
          className="button button-secondary"
          type="button"
          disabled={loading}
          onClick={() => reload(pagination.page)}
        >
          {loading ? 'Loading…' : 'Refresh requests'}
        </button>
      </section>

      <section className="stats-grid" aria-label="Buyer request summary">
        <article className="stat-card">
          <span>Total requests</span>
          <strong>{ready ? data.totalElements : '—'}</strong>
          <small>Across all pages</small>
        </article>

        <article className="stat-card">
          <span>Open on this page</span>
          <strong>
            {ready
              ? data.content.filter((request) => request.status === 'OPEN').length
              : '—'}
          </strong>
          <small>Open requests shown below</small>
        </article>

        <article className="stat-card stat-accent">
          <span>Requests page</span>
          <strong>
            {ready
              ? `${data.totalPages === 0 ? 0 : data.page + 1} / ${data.totalPages}`
              : '—'}
          </strong>
          <small>Up to 10 requests per page</small>
        </article>
      </section>

      {success && (
        <div className="notice notice-success" role="status">
          {success}
        </div>
      )}

      <div className="workspace-grid">
        <section className="panel" aria-labelledby="buyer-list-title">
          <div className="panel-heading">
            <h2 id="buyer-list-title">Recorded buyer demand</h2>
            <p>Newest requests first. Quantities are in kilograms.</p>
          </div>

          {listError && (
            <div className="notice notice-error directory-error" role="alert">
              {listError}
              <button
                className="text-button"
                type="button"
                onClick={() => reload(pagination.page)}
              >
                Try again
              </button>
            </div>
          )}

          <div className="table-wrap" aria-busy={loading}>
            <table aria-label="Buyer requests">
              <thead>
                <tr>
                  <th scope="col">Crop / buyer</th>
                  <th scope="col">Quantity</th>
                  <th scope="col">District</th>
                  <th scope="col">Required by</th>
                  <th scope="col">Max LKR / kg</th>
                  <th scope="col">Status</th>
                </tr>
              </thead>

              <tbody>
                {loading && (
                  <tr>
                    <td colSpan={6} className="table-message" role="status">
                      Loading buyer requests…
                    </td>
                  </tr>
                )}

                {ready && data.content.length === 0 && (
                  <tr>
                    <td colSpan={6} className="table-message">
                      <strong>No buyer requests yet</strong>
                      <span>Add the first request using the form.</span>
                    </td>
                  </tr>
                )}

                {ready && data.content.map((request) => (
                  <tr key={request.id}>
                    <td>
                      <strong>{request.cropName}</strong>
                      <span className="cell-secondary">
                        {request.buyerName}
                      </span>
                      <span className="cell-secondary">
                        {request.phone}
                      </span>
                    </td>

                    <td>
                      {quantityFormat.format(request.quantityKg)} kg
                    </td>

                    <td>{request.district}</td>
                    <td>{request.requiredBy}</td>

                    <td>
                      {request.maxPricePerKg === null
                        ? 'Not specified'
                        : priceFormat.format(request.maxPricePerKg)}
                    </td>

                    <td>
                      <span
                        className={`status-badge ${
                          request.status === 'OPEN' ? 'active' : 'inactive'
                        }`}
                      >
                        {request.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <footer className="pagination">
            <span>
              {ready
                ? `${data.content.length} of ${data.totalElements} requests`
                : 'Buyer request results'}
            </span>

            <div className="page-buttons">
              <button
                className="button button-secondary button-small"
                type="button"
                disabled={!ready || !data.hasPrevious}
                onClick={() => reload(pagination.page - 1)}
              >
                Previous
              </button>

              <button
                className="button button-secondary button-small"
                type="button"
                disabled={!ready || !data.hasNext}
                onClick={() => reload(pagination.page + 1)}
              >
                Next
              </button>
            </div>
          </footer>
        </section>

        <section className="panel add-panel" aria-labelledby="buyer-add-title">
          <div className="panel-heading">
            <span className="form-icon" aria-hidden="true">+</span>
            <h2 id="buyer-add-title">Add a buyer request</h2>
            <p>Capture the produce requirement and contact details.</p>
          </div>

          <form className="farmer-form buyer-form" onSubmit={save}>
            <fieldset disabled={saving}>
              {fields.map((field) => (
                <div className="field" key={field.name}>
                  <label htmlFor={`buyer-${field.name}`}>
                    {field.label}
                    {field.required ? ' *' : <span>optional</span>}
                  </label>

                  <input
                    id={`buyer-${field.name}`}
                    type={field.type}
                    value={form[field.name]}
                    required={field.required}
                    maxLength={field.maxLength}
                    min={
                      field.name === 'requiredBy'
                        ? localDate()
                        : field.min
                    }
                    max={field.max}
                    step={field.step}
                    placeholder={field.placeholder}
                    onChange={(event) => changeField(
                      field.name,
                      event.target.value,
                    )}
                    aria-invalid={Boolean(fieldErrors[field.name])}
                    aria-describedby={
                      fieldErrors[field.name]
                        ? `buyer-error-${field.name}`
                        : undefined
                    }
                  />

                  {fieldErrors[field.name] && (
                    <p
                      id={`buyer-error-${field.name}`}
                      className="buyer-field-error"
                    >
                      {fieldErrors[field.name]}
                    </p>
                  )}
                </div>
              ))}

              {saveError && (
                <div className="notice notice-error" role="alert">
                  {saveError}
                </div>
              )}

              <button
                className="button button-primary save-button"
                type="submit"
              >
                {saving ? 'Saving request…' : 'Add request'}
              </button>
            </fieldset>

            <p className="form-note">
              New requests start as OPEN. Use fictional contact details
              while testing.
            </p>
          </form>
        </section>
      </div>
    </>
  )
}