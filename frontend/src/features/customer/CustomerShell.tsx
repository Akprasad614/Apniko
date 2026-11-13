import { Link, Outlet, useLocation } from 'react-router'
import { useAuth } from '../../auth/AuthProvider'
import { Button } from '../../components/Button'
import './customer-shell.css'

const navigation = [
  { key: 'home', label: 'Home', to: '/customer/home', symbol: '⌂' },
  { key: 'categories', label: 'Categories', to: '/customer/categories', symbol: '▦' },
  { key: 'cart', label: 'Cart', to: '/customer/cart', symbol: '▣' },
  { key: 'orders', label: 'Orders', to: '/customer/orders', symbol: '▤' },
  { key: 'account', label: 'Account', to: '/customer/account', symbol: '◎' },
]

export function CustomerShell() {
  const { session, signOut } = useAuth()
  const location = useLocation()
  const path = location.pathname

  function isActive(key: string) {
    if (key === 'account') return path.startsWith('/customer/account')
    return path === `/customer/${key}` || (key === 'home' && path === '/customer')
  }

  return (
    <div className="customer-app-shell">
      <a className="skip-link" href="#customer-main">Skip to customer content</a>
      <header className="customer-shell-header">
        <div className="customer-shell-header__inner">
          <Link className="customer-shell-brand" to="/customer/home" aria-label="Apniko home">apniko<span>.</span></Link>
          <div className="customer-shell-header__account">
            <span>Good to see you, <strong>{session?.name.split(/\s+/)[0] || 'there'}</strong></span>
            <Button tone="quiet" onClick={signOut}>Sign out</Button>
          </div>
        </div>
      </header>

      <nav className="customer-primary-nav" aria-label="Customer navigation">
        {navigation.map((item) => (
          <Link
            key={item.key}
            to={item.to}
            className={`customer-nav-link${isActive(item.key) ? ' is-active' : ''}`}
            aria-current={isActive(item.key) ? 'page' : undefined}
          >
            <span className="customer-nav-link__icon" aria-hidden="true">{item.symbol}</span>
            <span>{item.label}</span>
          </Link>
        ))}
      </nav>

      <main id="customer-main" className="customer-shell-main">
        <Outlet />
      </main>

      <nav className="customer-bottom-nav" aria-label="Customer navigation">
        {navigation.map((item) => (
          <Link
            key={item.key}
            to={item.to}
            className={`customer-bottom-nav__item${isActive(item.key) ? ' is-active' : ''}`}
            aria-current={isActive(item.key) ? 'page' : undefined}
          >
            <span className="customer-bottom-nav__icon" aria-hidden="true">{item.symbol}</span>
            <span>{item.label}</span>
          </Link>
        ))}
      </nav>

      <footer className="customer-shell-footer">
        <Link className="customer-shell-footer__brand" to="/customer/home">apniko<span>.</span></Link>
        <span>Good groceries. Closer to home.</span>
        <span>APNIKO / CUSTOMER</span>
      </footer>
    </div>
  )
}
