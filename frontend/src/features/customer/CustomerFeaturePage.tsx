import { Link } from 'react-router'
import './customer-shell.css'

interface CustomerFeaturePageProps {
  feature: 'addresses' | 'payment-methods'
}

export function CustomerFeaturePage({ feature }: CustomerFeaturePageProps) {
  const isAddress = feature === 'addresses'

  return (
    <section className="customer-screen">
      <div className="customer-screen-heading">
        <p className="customer-eyebrow">ACCOUNT / {isAddress ? 'ADDRESSES' : 'PAYMENTS'}</p>
        <h1>{isAddress ? <>Saved <em>addresses.</em></> : <>Payment <em>methods.</em></>}</h1>
        <p>{isAddress ? 'Your saved delivery locations will be managed here.' : 'Payment choices and secure payment preferences will live here.'}</p>
      </div>
      <div className="customer-feature-placeholder">
        <span aria-hidden="true">{isAddress ? '⌖' : '₹'}</span>
        <strong>{isAddress ? 'Address book is part of the next delivery-location phase.' : 'Payment management is part of the checkout phase.'}</strong>
        <p>
          {isAddress
            ? 'GPS location, map pin selection, reverse geocoding and saved addresses will be connected in the address and delivery phase. No address is being saved on this screen yet.'
            : 'Apniko already has backend support for COD and Razorpay. Customer payment selection, online checkout, saved payment methods and payment history will be connected in the payment phase.'}
        </p>
        <Link to="/customer/account" className="customer-feature-back">← Back to account</Link>
      </div>
    </section>
  )
}
