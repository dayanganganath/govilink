import { ApiError, request } from './http'

export type UserRole =
  | 'ADMIN'
  | 'COORDINATOR'
  | 'FARMER'
  | 'BUYER'

export interface Account {
  id: string
  email: string
  displayName: string
  roles: UserRole[]
}

export async function currentAccount(
  signal?: AbortSignal,
): Promise<Account | null> {
  try {
    return await request<Account>(
      '/api/v1/auth/me',
      { signal },
    )
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }

    throw error
  }
}

export async function signIn(
  email: string,
  password: string,
): Promise<Account> {
  await request<void>('/api/v1/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams({
      email: email.trim(),
      password,
    }),
  })

  const account = await currentAccount()

  if (!account) {
    throw new Error(
      'Unable to restore your session. Please sign in again.',
    )
  }

  return account
}

export function signOut(): Promise<void> {
  return request<void>('/api/v1/auth/logout', {
    method: 'POST',
  })
}