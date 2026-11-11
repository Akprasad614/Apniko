import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthProvider'
import { BrandMark } from '../components/BrandMark'
import { Button } from '../components/Button'
import { Field } from '../components/Field'

export function RegisterPage() {
  const { signUpCustomer } = useAuth()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      await signUpCustomer(name.trim(), email.trim(), password)
      navigate('/customer', { replace: true })
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Account creation failed. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-panel auth-panel--copy">
        <BrandMark light />
        <div className="auth-panel__message">
          <p className="eyebrow eyebrow--light">A good place to start</p>
          <h1>Make room<br />for <em>local.</em></h1>
          <p>Create a customer account and get ready to explore the shops around you.</p>
        </div>
        <span className="auth-panel__foot">One small choice. A stronger local loop.</span>
      </section>

      <section className="auth-panel auth-panel--form">
        <Link className="auth-back" to="/">← Back to home</Link>
        <div className="auth-form-wrap">
          <p className="eyebrow">Customer account</p>
          <h2>Create account</h2>
          <p className="auth-form-wrap__intro">A few details and you're on your way.</p>
          <form className="auth-form" onSubmit={handleSubmit}>
            <Field
              label="Your name"
              name="name"
              type="text"
              autoComplete="name"
              required
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
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
              label="Create a password"
              name="password"
              type="password"
              autoComplete="new-password"
              minLength={6}
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              hint="Use at least 6 characters."
            />
            {error && <p className="form-error" role="alert">{error}</p>}
            <Button type="submit" disabled={busy} className="auth-submit">
              {busy ? 'Creating account…' : 'Create customer account'} <span aria-hidden="true">→</span>
            </Button>
          </form>
          <p className="auth-switch">Already have an account? <Link to="/login">Sign in</Link></p>
          <p className="auth-help">Public registration is customer-only. Shopkeeper, delivery and admin accounts are created through Apniko administration.</p>
        </div>
      </section>
    </main>
  )
}
