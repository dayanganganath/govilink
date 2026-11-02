import { useEffect, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { currentAccount, signIn, signOut } from './authApi'
import type { Account } from './authApi'
import { ApiError, SESSION_EXPIRED_EVENT } from './http'
import './AuthGate.css'

type Props = {
  children: ReactNode
}

export default function AuthGate({ children }: Props) {
  const [account, setAccount] = useState<Account | null>(null)
  const [checking, setChecking] = useState(true)
  const [checkFailed, setCheckFailed] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const [busy, setBusy] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [message, setMessage] = useState('')

  useEffect(() => {
    let active = true
    const controller = new AbortController()

    currentAccount(controller.signal)
      .then((value) => {
        if (active) setAccount(value)
      })
      .catch(() => {
        if (active) {
          setCheckFailed(true)
          setMessage(
            'Unable to connect to GoviLink. Check the backend and try again.',
          )
        }
      })
      .finally(() => {
        if (active) setChecking(false)
      })

    return () => {
      active = false
      controller.abort()
    }
  }, [attempt])

  useEffect(() => {
    function expired() {
      setAccount(null)
      setPassword('')
      setMessage('Your session has ended. Please sign in again.')
    }

    window.addEventListener(SESSION_EXPIRED_EVENT, expired)

    return () => {
      window.removeEventListener(SESSION_EXPIRED_EVENT, expired)
    }
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setMessage('')

    try {
      setAccount(await signIn(email, password))
    } catch (error) {
      setMessage(
        error instanceof ApiError && error.status === 401
          ? 'Invalid email or password.'
          : 'Unable to sign in. Check your connection and try again.',
      )
    } finally {
      setPassword('')
      setBusy(false)
    }
  }

  async function logout() {
    setBusy(true)
    setMessage('')

    try {
      await signOut()
      setAccount(null)
      setPassword('')
    } catch {
      setMessage(
        'Unable to confirm sign out. Check your connection and try again.',
      )
    } finally {
      setBusy(false)
    }
  }

  if (checking || checkFailed) {
    return (
      <main className="auth-screen">
        <section className="auth-card" aria-label="Session check">
          <div className="auth-brand">GoviLink.</div>

          {checking ? (
            <p role="status">Checking your session…</p>
          ) : (
            <>
              <p role="alert">{message}</p>
              <button
                className="auth-button"
                type="button"
                onClick={() => {
                  setMessage('')
                  setChecking(true)
                  setCheckFailed(false)
                  setAttempt((value) => value + 1)
                }}
              >
                Retry connection
              </button>
            </>
          )}
        </section>
      </main>
    )
  }

  if (account) {
    const staff = account.roles.some(
      (role) => role === 'ADMIN' || role === 'COORDINATOR',
    )

    return (
      <>
        <header className="session-bar" aria-label="Your account">
          <span>
            <strong>{account.displayName}</strong>
            <span className="session-email">{account.email}</span>
          </span>

          <button
            className="session-signout"
            type="button"
            disabled={busy}
            onClick={logout}
          >
            {busy ? 'Signing out…' : 'Sign out'}
          </button>
        </header>

        {message && (
          <p className="session-message" role="alert">{message}</p>
        )}

        {staff ? children : (
          <main className="auth-screen">
            <section className="auth-card">
              <h1>Staff access required</h1>
              <p>
                This workspace is available to GoviLink administrators
                and coordinators.
              </p>
            </section>
          </main>
        )}
      </>
    )
  }

  return (
    <main className="auth-screen">
      <section className="auth-card" aria-labelledby="signin-title">
        <div className="auth-brand">GoviLink.</div>
        <p className="auth-eyebrow">TEAM WORKSPACE</p>
        <h1 id="signin-title">Welcome back</h1>
        <p className="auth-intro">
          Sign in to coordinate farmers, harvests and buyer requests.
        </p>

        {message && (
          <p className="auth-message" role="alert">{message}</p>
        )}

        <form onSubmit={submit}>
          <fieldset className="auth-fields" disabled={busy}>
            <label htmlFor="signin-email">Email address</label>
            <input
              id="signin-email"
              type="email"
              autoComplete="username"
              required
              maxLength={254}
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />

            <label htmlFor="signin-password">Password</label>
            <input
              id="signin-password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />

            <button className="auth-button" type="submit">
              {busy ? 'Signing in…' : 'Sign in'}
            </button>
          </fieldset>
        </form>

        <p className="auth-note">
          Use the staff account provided by your GoviLink administrator.
        </p>
      </section>
    </main>
  )
}