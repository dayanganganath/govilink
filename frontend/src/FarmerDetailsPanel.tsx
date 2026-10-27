import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, getFarmer, updateFarmer } from './api'
import type { CreateFarmerRequest, Farmer } from './api'
import './FarmerDetailsPanel.css'

interface Props {
  farmerId: string
  onClose: () => void
  onSaved: (farmer: Farmer) => void
}

const fields: {
  name: keyof CreateFarmerRequest
  label: string
  maxLength: number
  required: boolean
}[] = [
  { name: 'fullName', label: 'Full name', maxLength: 150, required: true },
  { name: 'phone', label: 'Phone', maxLength: 20, required: true },
  { name: 'district', label: 'District', maxLength: 100, required: true },
  { name: 'village', label: 'Village', maxLength: 150, required: false },
]

function toForm(farmer: Farmer): CreateFarmerRequest {
  return {
    fullName: farmer.fullName,
    phone: farmer.phone,
    district: farmer.district,
    village: farmer.village ?? '',
  }
}

function message(error: unknown): string {
  return error instanceof Error ? error.message : 'The request failed.'
}

function formatDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString()
}

export default function FarmerDetailsPanel({
  farmerId,
  onClose,
  onSaved,
}: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const savingRef = useRef(false)

  const [farmer, setFarmer] = useState<Farmer | null>(null)
  const [form, setForm] = useState<CreateFarmerRequest>({
    fullName: '',
    phone: '',
    district: '',
    village: '',
  })
  const [revision, setRevision] = useState(0)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [saveError, setSaveError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [conflict, setConflict] = useState(false)
  const [missing, setMissing] = useState(false)
  const [savedMessage, setSavedMessage] = useState('')

  const original = farmer ? toForm(farmer) : null
  const dirty = original !== null && fields.some(
    ({ name }) => form[name] !== original[name],
  )

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  useEffect(() => {
    const controller = new AbortController()

    getFarmer(farmerId, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return

        setFarmer(result)
        setForm(toForm(result))
        setLoadError('')
        setSaveError('')
        setFieldErrors({})
        setConflict(false)
        setMissing(false)
        setLoading(false)
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        setLoadError(message(error))
        setLoading(false)
      })

    return () => controller.abort()
  }, [farmerId, revision])

  function requestClose() {
    if (savingRef.current) return

    if (dirty && !window.confirm('Discard your unsaved changes and close?')) {
      return
    }

    onClose()
  }

  function reloadLatest() {
    if (savingRef.current) return

    if (
      dirty &&
      !window.confirm('Reload the latest details and discard your unsaved changes?')
    ) {
      return
    }

    setLoading(true)
    setLoadError('')
    setSavedMessage('')
    setRevision((current) => current + 1)
  }

  function changeField(name: keyof CreateFarmerRequest, value: string) {
    setForm((current) => ({ ...current, [name]: value }))
    setSavedMessage('')
    setFieldErrors((current) => {
      const next = { ...current }
      delete next[name]
      return next
    })
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!farmer || savingRef.current || loading || conflict || missing) return

    savingRef.current = true
    setSaving(true)
    setSaveError('')
    setSavedMessage('')
    setFieldErrors({})

    try {
      const updated = await updateFarmer(farmer.id, {
        fullName: form.fullName.trim(),
        phone: form.phone.trim(),
        district: form.district.trim(),
        village: form.village.trim(),
        version: farmer.version,
      })

      setFarmer(updated)
      setForm(toForm(updated))
      setSavedMessage('Farmer details updated successfully.')
      onSaved(updated)
    } catch (error: unknown) {
      setSaveError(message(error))

      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors)
        if (error.status === 409) setConflict(true)
        if (error.status === 404) setMissing(true)
      }
    } finally {
      savingRef.current = false
      setSaving(false)
    }
  }

  return (
    <dialog
      ref={dialogRef}
      className="farmer-dialog"
      aria-labelledby="farmer-dialog-title"
      onCancel={(event) => {
        event.preventDefault()
        requestClose()
      }}
    >
      <header className="details-header">
        <div>
          <p className="eyebrow">FARMER RECORD</p>
          <h2 id="farmer-dialog-title">View & edit farmer</h2>
          <p>Keep contact and location details up to date.</p>
        </div>
        <button
          className="button button-secondary button-small"
          type="button"
          onClick={requestClose}
          disabled={saving}
        >
          Close
        </button>
      </header>

      <div className="details-body">
        {loading && <p role="status">Loading farmer details…</p>}

        {loadError && (
          <div className="notice notice-error" role="alert">
            {loadError}
          </div>
        )}

        {!loading && farmer && (
          <>
            <dl className="record-meta">
              <div className="record-id">
                <dt>Farmer ID</dt>
                <dd>{farmer.id}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{farmer.active ? 'Active' : 'Inactive'}</dd>
              </div>
              <div>
                <dt>Record version</dt>
                <dd>{farmer.version}</dd>
              </div>
              <div>
                <dt>Created</dt>
                <dd>{formatDate(farmer.createdAt)}</dd>
              </div>
              <div>
                <dt>Updated</dt>
                <dd>{formatDate(farmer.updatedAt)}</dd>
              </div>
            </dl>

            {savedMessage && (
              <div className="notice notice-success" role="status">
                {savedMessage}
              </div>
            )}

            {saveError && (
              <div className="notice notice-error" role="alert">
                <p>{saveError}</p>
                {conflict && (
                  <p>
                    Your entries are still here. Reload the latest record before
                    making another update.
                  </p>
                )}
              </div>
            )}

            <form onSubmit={save}>
              <fieldset disabled={saving || missing || Boolean(loadError)}>
                {fields.map((field) => (
                  <div className="field" key={field.name}>
                    <label htmlFor={`edit-${field.name}`}>
                      {field.label}
                      {field.required ? ' *' : <span>optional</span>}
                    </label>
                    <input
                      id={`edit-${field.name}`}
                      type={field.name === 'phone' ? 'tel' : 'text'}
                      value={form[field.name]}
                      onChange={(event) => changeField(field.name, event.target.value)}
                      required={field.required}
                      maxLength={field.maxLength}
                      aria-invalid={Boolean(fieldErrors[field.name])}
                      aria-describedby={
                        fieldErrors[field.name] ? `error-${field.name}` : undefined
                      }
                    />
                    {fieldErrors[field.name] && (
                      <p id={`error-${field.name}`} className="field-error">
                        {fieldErrors[field.name]}
                      </p>
                    )}
                  </div>
                ))}

                <div className="details-actions">
                  <span>{dirty ? 'Unsaved changes' : 'No unsaved changes'}</span>
                  <button
                    className="button button-primary"
                    type="submit"
                    disabled={!dirty || conflict || missing || Boolean(loadError)}
                  >
                    {saving ? 'Saving…' : 'Save changes'}
                  </button>
                </div>
              </fieldset>
            </form>
          </>
        )}

        <div className="details-reload">
          <button
            className="button button-secondary button-small"
            type="button"
            onClick={reloadLatest}
            disabled={loading || saving}
          >
            Reload latest details
          </button>
        </div>
      </div>
    </dialog>
  )
}