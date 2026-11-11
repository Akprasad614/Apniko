import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthProvider'
import { BrandMark } from '../components/BrandMark'
import { Button } from '../components/Button'
import { Field } from '../components/Field'
import { roleHomePath } from '../lib/roles'

export function LoginPage() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      const session = await signIn(email.trim(), password)
      const requestedPath = (location.state as { from?: string } | null)?.from
      navigate(requestedPath || roleHomePath(session.role), { replace: true })
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Sign in failed. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-panel auth-panel--copy">
        <BrandMark light />
        <div className="auth-panel__message">
          <p className="eyebrow eyebrow--light">Welcome back</p>
          <h1>Your local<br /><em>is waiting.</em></h1>
          <p>Sign in to continue to your LocalCart workspace.</p>
        </div>
        <span className="auth-panel__foot">Good groceries. Closer to home.</span>
      </section>

      <section className="auth-panel auth-panel--form">
        <Link className="auth-back" to="/">← Back to home</Link>
        <div className="auth-form-wrap">
          <p className="eyebrow">Your account</p>
          <h2>Sign in</h2>
          <p className="auth-form-wrap__intro">Use the email address linked to your LocalCart account.</p>
          <form className="auth-form" onSubmit={handleSubmit}>
            <Field
              label="Email address"
              name="email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
            <Field
              label="Password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
            {error && <p className="form-error" role="alert">{error}</p>}
            <Button type="submit" disabled={busy} className="auth-submit">
              {busy ? 'Signing in…' : 'Sign in'} <span aria-hidden="true">→</span>
            </Button>
          </form>
          <p className="auth-switch">New to LocalCart? <Link to="/register">Create a customer account</Link></p>
          <p className="auth-help">Shopkeeper, delivery and admin accounts are provisioned by LocalCart. Sign in with the account details provided to you.</p>
        </div>
      </section>
    </main>
  )
}
