import { Link, useLocation } from 'react-router'
import { useAuth } from '../auth/AuthProvider'
import { BrandMark } from '../components/BrandMark'
import { Button } from '../components/Button'
import type { AppRole } from '../lib/roles'

const PORTAL_COPY: Record<AppRole, { kicker: string; title: string; description: string; phase: string }> = {
  CUSTOMER: {
    kicker: 'CUSTOMER SPACE',
    title: 'Your neighbourhood,\nat your pace.',
    description: 'Your customer account is connected. The storefront, search and basket experience will be added in the next build.',
    phase: 'Customer storefront is next.',
  },
  SHOPKEEPER: {
    kicker: 'SHOPKEEPER DESK',
    title: 'Your shop.\nYour daily rhythm.',
    description: 'Your shopkeeper account is recognised. Shop, inventory and order-management screens will be added in the next build.',
    phase: 'Shop operations are next.',
  },
  DELIVERY_PARTNER: {
    kicker: 'DELIVERY SPACE',
    title: 'The next hand-off\nstarts here.',
    description: 'Your delivery-partner account is recognised. Available jobs and delivery milestones will be added in the next build.',
    phase: 'Delivery workflow is next.',
  },
  ADMIN: {
    kicker: 'APNIKO CONTROL',
    title: 'Keep the local\nloop moving.',
    description: 'Your administrator account is recognised. Platform operations and oversight screens will be added in the next build.',
    phase: 'Administration is next.',
  },
}

export function PortalPage() {
  const { session, signOut } = useAuth()
  const location = useLocation()
  if (!session) return null
  const copy = PORTAL_COPY[session.role]
  const role = session.role.replaceAll('_', ' ').toLowerCase()

  return (
    <main className="portal-page">
      <header className="portal-header wrap">
        <BrandMark />
        <div className="portal-user">
          <span className="portal-user__name">{session.name}</span>
          <span className="portal-user__role">{role}</span>
          <Button tone="quiet" onClick={signOut}>Sign out</Button>
        </div>
      </header>
      <section className="portal-main wrap">
        <div className="portal-main__meta">
          <span className="eyebrow">{copy.kicker}</span>
          <span className="portal-main__id">ACCOUNT / {session.userId}</span>
        </div>
        <h1>{copy.title.split('\n').map((line, index) => <span key={line}>{index > 0 && <br />}{line}</span>)}</h1>
        <p className="portal-main__description">{copy.description}</p>
        <div className="portal-next">
          <span className="portal-next__icon" aria-hidden="true">✳</span>
          <div>
            <strong>{copy.phase}</strong>
            <p>This is the frontend foundation. Your backend services remain separate and continue to enforce account permissions.</p>
          </div>
        </div>
        <Link className="text-link" to="/">Return to Apniko home ↗</Link>
        <span className="portal-main__route">{location.pathname}</span>
      </section>
    </main>
  )
}
