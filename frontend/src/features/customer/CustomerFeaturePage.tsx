import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../../auth/AuthProvider'
import { ApiError, apiRequest } from '../../lib/api'
import './customer-shell.css'

type PaymentMethod = 'COD' | 'RAZORPAY'

function messageFor(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return error instanceof Error ? error.message : 'Payment options could not be loaded.'
}

export function CustomerFeaturePage() {
  const { session } = useAuth()
  const [methods, setMethods] = useState<PaymentMethod[]>(['COD'])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    apiRequest<PaymentMethod[]>('/api/payments/methods', {}, session?.token ?? '')
      .then((result) => { if (active) setMethods(result) })
      .catch((loadError: unknown) => { if (active) setError(messageFor(loadError)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [session?.token])

  const onlineAvailable = methods.includes('RAZORPAY')

  return (
    <section className="customer-screen">
      <div className="customer-screen-heading">
        <p className="customer-eyebrow">ACCOUNT / PAYMENTS</p>
        <h1>Payment <em>methods.</em></h1>
        <p>See which payment methods are available for your checkout.</p>
      </div>
      {error && <div className="customer-state customer-state--error" role="alert">{error}</div>}
      <div className="customer-payment-page">
        {loading ? <p>Checking payment availability…</p> : (
          <>
            <article className="customer-payment-page__method is-available">
              <span className="customer-payment-option__symbol">₹</span>
              <div><strong>Cash on Delivery</strong><p>Available. Pay when your order is delivered or collected.</p></div>
              <span className="customer-payment-page__status">Available</span>
            </article>
            <article className={'customer-payment-page__method' + (onlineAvailable ? ' is-available' : '')}>
              <span className="customer-payment-option__symbol">↗</span>
              <div><strong>Razorpay online payment</strong><p>{onlineAvailable ? 'Available at checkout for supported online payment options.' : 'Not enabled on this server. Razorpay test or live keys must be configured by the administrator.'}</p></div>
              <span className="customer-payment-page__status">{onlineAvailable ? 'Available' : 'Not configured'}</span>
            </article>
          </>
        )}
        <p className="customer-cart-phase-note">Apniko does not save card numbers or UPI credentials. Payment details are entered on the secure payment provider checkout when online payments are enabled.</p>
        <Link to="/customer/cart" className="customer-payment-page__back">Choose payment in your cart →</Link>
      </div>
    </section>
  )
}
