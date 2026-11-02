import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AuthGate from './AuthGate'
import { currentAccount, signIn, signOut } from './authApi'
import { ApiError, SESSION_EXPIRED_EVENT } from './http'

vi.mock('./authApi', () => ({
  currentAccount: vi.fn(),
  signIn: vi.fn(),
  signOut: vi.fn(),
}))

const currentMock = vi.mocked(currentAccount)
const signInMock = vi.mocked(signIn)
const signOutMock = vi.mocked(signOut)

const admin = {
  id: 'admin-1',
  email: 'admin@example.com',
  displayName: 'Test Admin',
  roles: ['ADMIN'] as ('ADMIN' | 'BUYER')[],
}

function openGate() {
  return render(
    <AuthGate>
      <p>Private workspace</p>
    </AuthGate>,
  )
}

beforeEach(() => {
  vi.resetAllMocks()
  currentMock.mockResolvedValue(null)
})

describe('Staff sign in', () => {
  it('hides the workspace while checking the session', () => {
    currentMock.mockImplementation(
      () => new Promise(() => {}),
    )

    openGate()

    expect(screen.getByRole('status'))
      .toHaveTextContent('Checking your session')

    expect(screen.queryByText('Private workspace'))
      .not.toBeInTheDocument()
  })

  it('restores the staff account and workspace', async () => {
    currentMock.mockResolvedValue(admin)

    openGate()

    expect(await screen.findByText('Private workspace'))
      .toBeInTheDocument()

    expect(screen.getByText('Test Admin'))
      .toBeInTheDocument()
  })

  it('clears a rejected password and allows a successful retry', async () => {
    const user = userEvent.setup()

    signInMock
      .mockRejectedValueOnce(
        new ApiError('Invalid email or password.', 401),
      )
      .mockResolvedValueOnce(admin)

    openGate()

    const email = await screen.findByLabelText('Email address')
    const password = screen.getByLabelText('Password')

    await user.type(email, 'admin@example.com')
    await user.type(password, 'wrong')
    await user.click(
      screen.getByRole('button', { name: 'Sign in' }),
    )

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('Invalid email or password.')

    expect(password).toHaveValue('')
    expect(email).toHaveValue('admin@example.com')

    await user.type(password, 'correct test password')
    await user.click(
      screen.getByRole('button', { name: 'Sign in' }),
    )

    expect(await screen.findByText('Private workspace'))
      .toBeInTheDocument()

    expect(signInMock).toHaveBeenLastCalledWith(
      'admin@example.com',
      'correct test password',
    )
  })

  it('retries a failed connection', async () => {
    currentMock
      .mockRejectedValueOnce(new Error('Offline'))
      .mockResolvedValueOnce(admin)

    openGate()

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('Unable to connect')

    fireEvent.click(
      screen.getByRole('button', { name: 'Retry connection' }),
    )

    expect(await screen.findByText('Private workspace'))
      .toBeInTheDocument()
  })

  it('removes the workspace after logout', async () => {
    currentMock.mockResolvedValue(admin)
    signOutMock.mockResolvedValue(undefined)

    openGate()

    fireEvent.click(
      await screen.findByRole('button', { name: 'Sign out' }),
    )

    expect(
      await screen.findByRole('heading', { name: 'Welcome back' }),
    ).toBeInTheDocument()

    expect(screen.queryByText('Private workspace'))
      .not.toBeInTheDocument()

    expect(signOutMock).toHaveBeenCalledOnce()
  })

  it('retains the session when logout cannot be confirmed', async () => {
    currentMock.mockResolvedValue(admin)
    signOutMock.mockRejectedValueOnce(new Error('Offline'))

    openGate()

    fireEvent.click(
      await screen.findByRole('button', { name: 'Sign out' }),
    )

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('Unable to confirm sign out')

    expect(screen.getByText('Private workspace'))
      .toBeInTheDocument()

    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: 'Sign out' }),
      ).toBeEnabled()
    })
  })

  it('hides private content when the session expires', async () => {
    currentMock.mockResolvedValue(admin)

    openGate()

    await screen.findByText('Private workspace')

    act(() => {
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
    })

    expect(screen.queryByText('Private workspace'))
      .not.toBeInTheDocument()

    expect(screen.getByRole('alert'))
      .toHaveTextContent('Your session has ended')
  })

  it('hides internal screens from buyer accounts', async () => {
    currentMock.mockResolvedValue({
      ...admin,
      roles: ['BUYER'],
    })

    openGate()

    expect(
      await screen.findByRole('heading', {
        name: 'Staff access required',
      }),
    ).toBeInTheDocument()

    expect(screen.queryByText('Private workspace'))
      .not.toBeInTheDocument()

    expect(
      screen.getByRole('button', { name: 'Sign out' }),
    ).toBeInTheDocument()
  })
})