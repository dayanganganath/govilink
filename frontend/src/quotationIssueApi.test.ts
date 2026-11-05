import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest'

import { issueQuotation } from './quotationApi'
import { SESSION_EXPIRED_EVENT } from './http'

const fetchMock = vi.fn<typeof fetch>()

const issueRequest = {
  version: 2,
  expiresAt: '2099-01-02T10:00:00Z',
  confirmedLineIds: ['line-1', 'line-2'],
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json',
    },
  })
}

function csrf(): Response {
  return json({
    headerName: 'X-CSRF-TOKEN',
    token: 'issue-token',
  })
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Quotation issue API', () => {
  it('sends explicit confirmations with CSRF and session credentials', async () => {
    const issued = {
      id: 'quote-1',
      status: 'ISSUED',
      version: 3,
      issuedBy: 'staff-1',
      issuedAt: '2099-01-01T10:00:00Z',
      expiresAt: issueRequest.expiresAt,
      priceConfirmationNeeded: false,
    }

    fetchMock
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(json(issued))

    const controller = new AbortController()

    const input = {
      ...issueRequest,
      issuedBy: 'untrusted-user',
      total: 1,
      status: 'ACCEPTED',
    }

    await expect(
      issueQuotation('quote/one', input, controller.signal),
    ).resolves.toEqual(issued)

    expect(fetchMock).toHaveBeenCalledTimes(2)

    expect(fetchMock.mock.calls[0][0])
      .toBe('/api/v1/auth/csrf')

    const [path, options] = fetchMock.mock.calls[1]

    expect(path)
      .toBe('/api/v1/quotations/quote%2Fone/issue')

    expect(options).toEqual(expect.objectContaining({
      method: 'POST',
      credentials: 'same-origin',
      cache: 'no-store',
      signal: controller.signal,
    }))

    const headers = new Headers(options?.headers)

    expect(headers.get('X-CSRF-TOKEN'))
      .toBe('issue-token')

    expect(headers.get('Content-Type'))
      .toBe('application/json')

    expect(JSON.parse(options?.body as string))
      .toEqual(issueRequest)
  })

  it('preserves stale-version conflicts without automatically retrying', async () => {
    const detail =
      'Quotation has changed. Reload and review it again.'

    fetchMock
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(json({ detail }, 409))

    await expect(
      issueQuotation('quote-1', issueRequest),
    ).rejects.toMatchObject({
      status: 409,
      message: detail,
    })

    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('preserves expiry validation errors', async () => {
    fetchMock
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(json({
        errors: {
          expiresAt: 'must be a future date',
        },
      }, 400))

    await expect(
      issueQuotation('quote-1', issueRequest),
    ).rejects.toMatchObject({
      status: 400,
      fieldErrors: {
        expiresAt: 'must be a future date',
      },
    })
  })

  it('does not send an issue request when CSRF retrieval fails', async () => {
    fetchMock.mockResolvedValueOnce(
      json({ detail: 'Unavailable' }, 503),
    )

    await expect(
      issueQuotation('quote-1', issueRequest),
    ).rejects.toMatchObject({
      status: 503,
    })

    expect(fetchMock).toHaveBeenCalledOnce()
    expect(fetchMock.mock.calls[0][0])
      .toBe('/api/v1/auth/csrf')
  })

  it('announces an expired session when issuance returns 401', async () => {
    const listener = vi.fn()

    window.addEventListener(
      SESSION_EXPIRED_EVENT,
      listener,
    )

    try {
      fetchMock
        .mockResolvedValueOnce(csrf())
        .mockResolvedValueOnce(json({
          detail: 'Authentication required.',
        }, 401))

      await expect(
        issueQuotation('quote-1', issueRequest),
      ).rejects.toMatchObject({
        status: 401,
      })

      expect(listener).toHaveBeenCalledOnce()
      expect(fetchMock).toHaveBeenCalledTimes(2)
    } finally {
      window.removeEventListener(
        SESSION_EXPIRED_EVENT,
        listener,
      )
    }
  })
})