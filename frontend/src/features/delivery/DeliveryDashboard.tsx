import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../../auth/AuthProvider'
import { Button } from '../../components/Button'
import { Wordmark } from '../../components/Wordmark'
import { ApiError, apiRequest } from '../../lib/api'
import '../shared/operations.css'

type DeliveryTab = 'available' | 'assigned' | 'completed'

interface DeliveryOrder {
  id: number
  status: string
  deliveryMode: string
  totalAmount: number
  shop?: {
    id: number
    name: string
    address?: string | null
    phone?: string | null
  }
  items: Array<{
    id: number
    quantity: number
    price: number
    product?: { id: number; name: string }
  }>
}

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Your session has expired. Sign in again.'
    if (error.status === 403) return 'This account cannot access delivery work.'
    return error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

function formatRupees(value: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(value ?? 0)
}

function formatStatus(status: string): string {
  return status.toLowerCase().split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

export function DeliveryDashboard() {
  const { session, signOut } = useAuth()
  const token = session?.token ?? ''
  const [tab, setTab] = useState<DeliveryTab>('available')
  const [available, setAvailable] = useState<DeliveryOrder[]>([])
  const [assigned, setAssigned] = useState<DeliveryOrder[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busyOrderId, setBusyOrderId] = useState<number | null>(null)

  const activeAssigned = useMemo(
    () => assigned.filter((order) => !['DELIVERED', 'CANCELLED'].includes(order.status))
      .sort((left, right) => right.id - left.id),
    [assigned],
  )
  const completed = useMemo(
    () => assigned.filter((order) => ['DELIVERED', 'CANCELLED'].includes(order.status))
      .sort((left, right) => right.id - left.id),
    [assigned],
  )

  const loadOrders = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true)
    setError('')
    const results = await Promise.allSettled([
      apiRequest<DeliveryOrder[]>('/api/delivery/orders/available', {}, token),
      apiRequest<DeliveryOrder[]>('/api/delivery/orders', {}, token),
    ] as const)
    if (results[0].status === 'fulfilled') setAvailable(results[0].value)
    else setError(messageFor(results[0].reason))
    if (results[1].status === 'fulfilled') setAssigned(results[1].value)
    else setError((current) => [current, messageFor(results[1].reason)].filter(Boolean).join(' '))
    if (!quiet) setLoading(false)
  }, [token])

  useEffect(() => {
    void loadOrders()
    const intervalId = window.setInterval(() => void loadOrders(true), 20000)
    return () => window.clearInterval(intervalId)
  }, [loadOrders])

  async function runAction(order: DeliveryOrder, action: 'accept' | 'pickup' | 'deliver') {
    setBusyOrderId(order.id)
    setError('')
    setNotice('')
    try {
      await apiRequest<DeliveryOrder>(
        '/api/delivery/orders/' + order.id + '/' + action,
        { method: 'PUT' },
        token,
      )
      const message = action === 'accept'
        ? 'Order #' + order.id + ' is now assigned to you.'
        : action === 'pickup'
          ? 'Order #' + order.id + ' is marked out for delivery.'
          : 'Order #' + order.id + ' has been marked delivered.'
      setNotice(message)
      await loadOrders(true)
    } catch (actionError) {
      setError(messageFor(actionError))
      await loadOrders(true)
    } finally {
      setBusyOrderId(null)
    }
  }

  const currentOrders = tab === 'available' ? available : tab === 'assigned' ? activeAssigned : completed

  return (
    <div className="operations-app">
      <header className="operations-header">
        <div className="operations-shell operations-header__inner">
          <Wordmark />
          <div className="operations-user">
            <span>{session?.name}</span><span className="operations-role">DELIVERY</span>
            <Button tone="quiet" onClick={signOut}>Sign out</Button>
          </div>
        </div>
      </header>

      <main className="operations-shell operations-main">
        <section className="operations-intro">
          <div><p className="operations-eyebrow">DELIVERY DESK</p><h1>Every hand-off<br /><em>keeps things moving.</em></h1><p>Find orders ready for collection and update delivery progress.</p></div>
          <button className="operations-button operations-button--secondary" type="button" disabled={loading} onClick={() => void loadOrders()}>{loading ? 'Refreshing…' : 'Refresh jobs ↻'}</button>
        </section>

        <div className="operations-alert operations-alert--info" role="note">
          The current order data records the shop collection point, but checkout does not yet save a customer drop-off address. Confirm the destination through your agreed customer contact process before accepting a delivery. Do not use this screen as proof of a delivery address.
        </div>
        {error && <div className="operations-alert operations-alert--error" role="alert">{error}</div>}
        {notice && <div className="operations-alert operations-alert--success" role="status">{notice}</div>}

        <nav className="operations-tabs" aria-label="Delivery sections">
          {([
            ['available', 'Available (' + available.length + ')'],
            ['assigned', 'My deliveries (' + activeAssigned.length + ')'],
            ['completed', 'Completed (' + completed.length + ')'],
          ] as const).map(([key, label]) => <button type="button" key={key} className={tab === key ? 'is-active' : ''} aria-current={tab === key ? 'page' : undefined} onClick={() => setTab(key)}>{label}</button>)}
        </nav>

        {loading ? <div className="operations-state" role="status">Loading delivery jobs…</div> : currentOrders.length ? (
          <div className="delivery-order-list">
            {currentOrders.map((order) => {
              const busy = busyOrderId === order.id
              const nextAction = tab === 'available' ? 'accept' : order.status === 'READY' ? 'pickup' : order.status === 'OUT_FOR_DELIVERY' ? 'deliver' : null
              const actionLabel = nextAction === 'accept' ? 'Accept delivery' : nextAction === 'pickup' ? 'Confirm pickup' : nextAction === 'deliver' ? 'Mark delivered' : null
              return (
                <article className="operations-panel delivery-order" key={order.id}>
                  <div className="delivery-order__top">
                    <div><p className="operations-eyebrow">ORDER / {order.id}</p><h2>{order.shop?.name || 'Local shop'}</h2><p>{order.shop?.address || 'Shop address not recorded'}</p></div>
                    <span className="operations-status operations-status--active">{formatStatus(order.status)}</span>
                  </div>
                  <div className="delivery-order__items">
                    <strong>Items to collect</strong>
                    {(order.items ?? []).map((item) => <div key={item.id}><span>{item.product?.name || 'Product'} × {item.quantity}</span><span>{formatRupees(item.price * item.quantity)}</span></div>)}
                  </div>
                  <div className="delivery-order__footer">
                    <span>Order total <strong>{formatRupees(order.totalAmount)}</strong></span>
                    {actionLabel && nextAction && <button className="operations-button" type="button" disabled={busy} onClick={() => void runAction(order, nextAction)}>{busy ? 'Updating…' : actionLabel} →</button>}
                  </div>
                </article>
              )
            })}
          </div>
        ) : (
          <div className="operations-state">
            <h2>{tab === 'available' ? 'No orders waiting for a delivery partner' : tab === 'assigned' ? 'No active deliveries' : 'No completed deliveries yet'}</h2>
            <p>{tab === 'available' ? 'When a shop marks a platform-delivery order ready, it will appear here.' : tab === 'assigned' ? 'Accept an available order to add it to your delivery list.' : 'Delivered and cancelled orders will be kept here for reference.'}</p>
          </div>
        )}
      </main>

      <footer className="operations-footer operations-shell"><Wordmark /><span>Delivery / Apniko</span><Link to="/">Back to home ↗</Link></footer>
    </div>
  )
}
