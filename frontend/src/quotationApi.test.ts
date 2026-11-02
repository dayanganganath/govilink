import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest'

import {
  createQuotationDraft,
  getQuotation,
} from './quotationApi'

import type { CreateQuotationDraft } from './quotationApi'
import { ApiError, SESSION_EXPIRED_EVENT } from './http'

const fetchMock = vi.fn<typeof fetch>()

const draft: CreateQuotationDraft = {
  buyerRequestId: 'buyer-1',
  serviceFee: 4500,
  deliveryFee: 2500,
  deliveryAddress: ' Test Address ',
  lines: [
    {
      harvestListingId: 'harvest-1',
      quantityKg: 500,
      farmerPricePerKg: 180,
      priceConfirmed: false,
    },
  ],
}

const quotation = {
  id: 'quote-1',
  status: 'DRAFT',
  currency: 'LKR',
  produceSubtotal: 90000,
  serviceFee: 4500,
  deliveryFee: 2500,
  total: 97000,
  priceConfirmationNeeded: true,
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json',
    },
  })
}

function csrf() {
  return json({
    headerName: 'X-CSRF-TOKEN',
    token: 'quote-token',
  })
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Quotation API', () => {
  it('creates a draft with CSRF and only editable request fields', async () => {
    fetchMock
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(json(quotation, 201))

    const input = {
      ...draft,
      total: 1,
      createdBy: 'untrusted-user',
    }

    await expect(
      createQuotationDraft(input),
    ).resolves.toEqual(quotation)

    const [path, options] = fetchMock.mock.calls[1]

    expect(path).toBe('/api/v1/quotations')
    expect(options?.method).toBe('POST')
    expect(options?.credentials).toBe('same-origin')

    const headers = new Headers(options?.headers)

    expect(headers.get('X-CSRF-TOKEN'))
      .toBe('quote-token')

    const body = JSON.parse(options?.body as string)

    expect(body).toEqual({
      ...draft,
      deliveryAddress: 'Test Address',
    })

    expect(body).not.toHaveProperty('total')
    expect(body).not.toHaveProperty('createdBy')
    expect(body.lines[0].priceConfirmed).toBe(false)
  })

  it('loads an existing quotation without an extra CSRF request', async () => {
    fetchMock.mockResolvedValueOnce(json(quotation))

    const controller = new AbortController()

    await expect(
      getQuotation('quote/one', controller.signal),
    ).resolves.toEqual(quotation)

    expect(fetchMock).toHaveBeenCalledOnce()

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/quotations/quote%2Fone',
      expect.objectContaining({
        method: 'GET',
        signal: controller.signal,
      }),
    )
  })

  it('preserves nested validation errors for the form', async () => {
    fetchMock
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(json({
        errors: {
          'lines[0].quantityKg': 'must be greater than zero',
        },
      }, 400))

    await expect(
      createQuotationDraft(draft),
    ).rejects.toMatchObject({
      status: 400,
      fieldErrors: {
        'lines[0].quantityKg': 'must be greater than zero',
      },
    })
  })

  it('preserves conflict details', async () => {
    fetchMock
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(json({
        detail:
          'The buyer request is closed or its required date has passed.',
      }, 409))

    await expect(
      createQuotationDraft(draft),
    ).rejects.toMatchObject({
      status: 409,
      message:
        'The buyer request is closed or its required date has passed.',
    })
  })

  it('announces an expired staff session', async () => {
    const listener = vi.fn()

    window.addEventListener(
      SESSION_EXPIRED_EVENT,
      listener,
    )

    try {
      fetchMock.mockResolvedValueOnce(json({
        detail: 'Authentication required.',
      }, 401))

      await expect(
        getQuotation('quote-1'),
      ).rejects.toBeInstanceOf(ApiError)

      expect(listener).toHaveBeenCalledOnce()
    } finally {
      window.removeEventListener(
        SESSION_EXPIRED_EVENT,
        listener,
      )
    }
  })

  it('does not create a draft when token retrieval fails', async () => {
    fetchMock.mockResolvedValueOnce(
      json({ detail: 'Unavailable' }, 503),
    )

    await expect(
      createQuotationDraft(draft),
    ).rejects.toMatchObject({
      status: 503,
    })

    expect(fetchMock).toHaveBeenCalledOnce()
  })

  it('does not automatically retry an unsuccessful create request', async () => {
    fetchMock
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(
        json({ detail: 'Unavailable' }, 503),
      )

    await expect(
      createQuotationDraft(draft),
    ).rejects.toMatchObject({
      status: 503,
    })

    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('normalizes an empty delivery address to null', async () => {
    fetchMock
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(json(quotation, 201))

    await createQuotationDraft({
      ...draft,
      deliveryAddress: '   ',
    })

    const body = JSON.parse(
      fetchMock.mock.calls[1][1]?.body as string,
    )

    expect(body.deliveryAddress).toBeNull()
  })
})