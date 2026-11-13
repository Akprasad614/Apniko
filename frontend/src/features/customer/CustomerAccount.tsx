import { Link } from 'react-router'
import { useAuth } from '../../auth/AuthProvider'
import './customer-shell.css'

export function CustomerAccount() {
  const { session, signOut } = useAuth()
  const initials = session?.name.trim().slice(0, 1).toUpperCase() || 'A'

  return (
    <section className="customer-screen customer-account-screen">
      <div className="customer-screen-heading">
        <p className="customer-eyebrow">YOUR APNIKO PROFILE</p>
        <h1>Your <em>account.</em></h1>
        <p>Manage your profile and the details you use when shopping locally.</p>
      </div>

      <article className="customer-account-summary">
        <span className="customer-account-avatar" aria-hidden="true">{initials}</span>
        <div>
          <strong>{session?.name}</strong>
          <span>{session?.email}</span>
          <small>Customer account</small>
        </div>
      </article>

      <div className="customer-account-grid">
        <Link to="/customer/account/addresses" className="customer-account-card">
          <span className="customer-account-card__symbol" aria-hidden="true">⌖</span>
          <strong>Saved addresses</strong>
          <span>Manage home, work and other delivery locations</span>
          <b>Manage addresses →</b>
        </Link>
        <Link to="/customer/account/payment-methods" className="customer-account-card">
          <span className="customer-account-card__symbol" aria-hidden="true">₹</span>
          <strong>Payment methods</strong>
          <span>Payment choices and checkout preferences</span>
          <b>View payment methods →</b>
        </Link>
        <Link to="/customer/orders" className="customer-account-card">
          <span className="customer-account-card__symbol" aria-hidden="true">▤</span>
          <strong>Order history</strong>
          <span>Track current orders and revisit past purchases</span>
          <b>View orders →</b>
        </Link>
      </div>

      <button className="customer-account-signout" type="button" onClick={signOut}>Sign out of Apniko</button>
    </section>
  )
}
