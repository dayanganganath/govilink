import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest'

import { currentAccount, signIn, signOut } from './authApi'
import {
  ApiError,
  request,
  SESSION_EXPIRED_EVENT,
} from './http'

const fetchMock = vi.fn<typeof fetch>()

const account = {
  id: 'account-1',
  email: 'admin@example.com',
  displayName: 'Test Admin',
  roles: ['ADMIN'],
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json',
    },
  })
}

function token(value = 'token-one') {
  return json({
    headerName: 'X-CSRF-TOKEN',
    token: value,
  })
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Session API', () => {
  it('restores the current account using the session cookie', async () => {
    fetchMock.mockResolvedValueOnce(json(account))

    await expect(currentAccount()).resolves.toEqual(account)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/auth/me',
      expect.objectContaining({
        method: 'GET',
        credentials: 'same-origin',
        cache: 'no-store',
      }),
    )
  })

  it('treats an anonymous session as signed out', async () => {
    fetchMock.mockResolvedValueOnce(
      json({ detail: 'Authentication required.' }, 401),
    )

    await expect(currentAccount()).resolves.toBeNull()
  })

  it('keeps a network error distinct from being signed out', async () => {
    fetchMock.mockRejectedValueOnce(
      new TypeError('Failed to fetch'),
    )

    await expect(currentAccount())
      .rejects.toThrow('Failed to fetch')
  })

  it('obtains CSRF before login and preserves password spaces', async () => {
    fetchMock
      .mockResolvedValueOnce(token())
      .mockResolvedValueOnce(
        new Response(null, { status: 204 }),
      )
      .mockResolvedValueOnce(json(account))

    await expect(
      signIn(' admin@example.com ', '  test password  '),
    ).resolves.toEqual(account)

    expect(fetchMock.mock.calls[0][0])
      .toBe('/api/v1/auth/csrf')

    const [path, options] = fetchMock.mock.calls[1]

    expect(path).toBe('/api/v1/auth/login')

    const headers = new Headers(options?.headers)

    expect(headers.get('X-CSRF-TOKEN')).toBe('token-one')

    expect(headers.get('Content-Type'))
      .toBe('application/x-www-form-urlencoded')

    const body = options?.body as URLSearchParams

    expect(body.get('email')).toBe('admin@example.com')
    expect(body.get('password')).toBe('  test password  ')

    expect(fetchMock.mock.calls[2][0])
      .toBe('/api/v1/auth/me')
  })

  it('returns the generic login error without fetching account details', async () => {
    fetchMock
      .mockResolvedValueOnce(token())
      .mockResolvedValueOnce(
        json({ detail: 'Invalid email or password.' }, 401),
      )

    await expect(
      signIn('admin@example.com', 'wrong'),
    ).rejects.toMatchObject({
      status: 401,
      message: 'Invalid email or password.',
    })

    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('obtains a fresh token for logout after login rotates CSRF', async () => {
    fetchMock
      .mockResolvedValueOnce(token('before-login'))
      .mockResolvedValueOnce(
        new Response(null, { status: 204 }),
      )
      .mockResolvedValueOnce(json(account))
      .mockResolvedValueOnce(token('after-login'))
      .mockResolvedValueOnce(
        new Response(null, { status: 204 }),
      )

    await signIn('admin@example.com', 'test password')
    await signOut()

    expect(fetchMock.mock.calls[3][0])
      .toBe('/api/v1/auth/csrf')

    expect(fetchMock.mock.calls[4][0])
      .toBe('/api/v1/auth/logout')

    const headers = new Headers(
      fetchMock.mock.calls[4][1]?.headers,
    )

    expect(headers.get('X-CSRF-TOKEN'))
      .toBe('after-login')
  })

  it('does not submit a mutation when token retrieval fails', async () => {
    fetchMock.mockResolvedValueOnce(
      json({ detail: 'Unavailable' }, 503),
    )

    await expect(signOut()).rejects.toBeInstanceOf(ApiError)

    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('rejects an invalid security-token response', async () => {
    fetchMock.mockResolvedValueOnce(
      json({
        headerName: 'Invalid',
        token: 'value',
      }),
    )

    await expect(signOut())
      .rejects.toThrow('valid security token')

    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('announces session expiry for a business request', async () => {
    const listener = vi.fn()

    window.addEventListener(
      SESSION_EXPIRED_EVENT,
      listener,
    )

    try {
      fetchMock.mockResolvedValueOnce(
        json({ detail: 'Sign in again.' }, 401),
      )

      await expect(
        request('/api/v1/farmers'),
      ).rejects.toMatchObject({
        status: 401,
      })

      expect(listener).toHaveBeenCalledOnce()
    } finally {
      window.removeEventListener(
        SESSION_EXPIRED_EVENT,
        listener,
      )
    }
  })

  it('preserves validation errors and does not retry a rejected mutation', async () => {
    fetchMock
      .mockResolvedValueOnce(token())
      .mockResolvedValueOnce(
        json({
          errors: {
            fullName: 'Required',
          },
        }, 400),
      )

    await expect(
      request('/api/v1/farmers', { method: 'POST' }),
    ).rejects.toMatchObject({
      status: 400,
      fieldErrors: {
        fullName: 'Required',
      },
    })

    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})