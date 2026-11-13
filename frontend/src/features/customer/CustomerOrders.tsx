import { useEffect, useState } from 'react'
import { useAuth } from '../../auth/AuthProvider'
import { ApiError, apiRequest } from '../../lib/api'
import { formatRupees } from './marketplace'
import {
  canCancelCustomerOrder,
  formatOrderStatus,
  getOrderProgressIndex,
  ORDER_PROGRESS,
} from './orders'
import type { OrderStatusHistoryEntry, CustomerOrder } from './orders'
import './customer-orders.css'

interface CustomerOrdersProps {
  refreshKey: number
}

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Your session has expired. Sign in again to see your orders.'
    if (error.status === 403) return 'This account cannot access customer orders.'
    return error.message
  }
  return error instanceof Error ? error.message : 'Could not load your orders. Please try again.'
}

function formatDateTime(value?: string | null): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('en-IN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

export function CustomerOrders({ refreshKey }: CustomerOrdersProps) {
  const { session } = useAuth()
  const token = session?.token ?? ''
  const [orders, setOrders] = useState<CustomerOrder[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [expandedOrderId, setExpandedOrderId] = useState<number | null>(null)
  const [historyByOrder, setHistoryByOrder] = useState<Record<number, OrderStatusHistoryEntry[]>>({})
  const [historyLoadingId, setHistoryLoadingId] = useState<number | null>(null)
  const [historyError, setHistoryError] = useState('')
  const [confirmCancelId, setConfirmCancelId] = useState<number | null>(null)
  const [cancellingOrderId, setCancellingOrderId] = useState<number | null>(null)
  const [actionError, setActionError] = useState('')
  const [manualRefreshKey, setManualRefreshKey] = useState(0)

  useEffect(() => {
    let active = true

    async function loadOrders(initialLoad: boolean) {
      if (initialLoad) setLoading(true)
      try {
        if (!session || !token) {
          if (active) {
            setOrders([])
            setError('Sign in as a customer to see your orders.')
          }
          return
        }

        const result = await apiRequest<CustomerOrder[]>(
          '/api/customer/orders',
          {},
          token,
        )
        if (!active) return
        setOrders([...result].sort((left, right) => right.id - left.id))
        setError('')
      } catch (loadError) {
        if (active) setError(messageFor(loadError))
      } finally {
        if (active && initialLoad) setLoading(false)
      }
    }

    void loadOrders(true)
    const intervalId = window.setInterval(() => void loadOrders(false), 20000)

    return () => {
      active = false
      window.clearInterval(intervalId)
    }
  }, [manualRefreshKey, refreshKey, session, token])

  async function toggleHistory(orderId: number) {
    if (expandedOrderId === orderId) {
      setExpandedOrderId(null)
      return
    }

    setExpandedOrderId(orderId)
    setHistoryError('')
    if (Object.prototype.hasOwnProperty.call(historyByOrder, orderId)) return

    setHistoryLoadingId(orderId)
    try {
      const history = await apiRequest<OrderStatusHistoryEntry[]>(
        `/api/orders/${orderId}/history`,
        {},
        token,
      )
      setHistoryByOrder((current) => ({ ...current, [orderId]: history }))
    } catch (loadError) {
      setHistoryError(messageFor(loadError))
    } finally {
      setHistoryLoadingId(null)
    }
  }

  async function cancelOrder(orderId: number) {
    setCancellingOrderId(orderId)
    setActionError('')
    try {
      const updated = await apiRequest<CustomerOrder>(
        `/api/orders/${orderId}/cancel`,
        {
          method: 'POST',
          body: JSON.stringify({ reason: 'Cancelled by customer' }),
        },
        token,
      )
      setOrders((current) => current.map((order) => order.id === orderId ? { ...order, status: updated.status } : order))
      setConfirmCancelId(null)
      setExpandedOrderId(orderId)
      setHistoryByOrder((current) => {
        const next = { ...current }
        delete next[orderId]
        return next
      })
      setManualRefreshKey((current) => current + 1)
    } catch (cancelError) {
      setActionError(messageFor(cancelError))
      setManualRefreshKey((current) => current + 1)
    } finally {
      setCancellingOrderId(null)
    }
  }

  return (
    <section id="customer-orders" className="customer-orders" aria-labelledby="customer-orders-title">
      <div className="customer-orders__heading">
        <div>
          <p className="customer-eyebrow">04 / AFTER YOU ORDER</p>
          <h2 id="customer-orders-title">Your orders</h2>
          <p>Keep an eye on what the shop is doing with your basket.</p>
        </div>
        <button
          className="customer-orders__refresh"
          type="button"
          onClick={() => setManualRefreshKey((current) => current + 1)}
          disabled={loading}
        >
          {loading ? 'Checking…' : 'Refresh orders'}
        </button>
      </div>

      <p className="customer-orders__poll-note">Order status checks automatically every 20 seconds while this page is open.</p>
      {error && <div className="customer-state customer-state--error" role="alert">{error}</div>}
      {actionError && <div className="customer-state customer-state--error" role="alert">{actionError}</div>}

      {loading ? (
        <div className="customer-state customer-state--inline" role="status">Loading your orders…</div>
      ) : orders.length === 0 ? (
        <div className="customer-state customer-state--empty">
          <strong>No orders yet.</strong>
          <p>Once you place an order from a local shop, it will appear here.</p>
        </div>
      ) : (
        <div className="customer-orders__list">
          {orders.map((order) => {
            const progressIndex = getOrderProgressIndex(order.status)
            const expanded = expandedOrderId === order.id
            return (
              <article className="customer-order" key={order.id}>
                <div className="customer-order__top">
                  <div>
                    <span className="customer-order__number">ORDER #{order.id}</span>
                    <h3>{order.shop?.name || 'Local shop'}</h3>
                    <span className="customer-order__mode">{formatOrderStatus(order.deliveryMode)}</span>
                  </div>
                  <div className="customer-order__amount">
                    <strong>{formatRupees(order.totalAmount)}</strong>
                    <span className={`customer-order__status customer-order__status--${order.status.toLowerCase()}`}>
                      {formatOrderStatus(order.status)}
                    </span>
                  </div>
                </div>

                {order.status === 'CANCELLED' ? (
                  <p className="customer-order__cancelled">This order has been cancelled.</p>
                ) : (
                  <ol className="order-progress" aria-label={`Order ${order.id} progress`}>
                    {ORDER_PROGRESS.map((step, index) => (
                      <li
                        className={index <= progressIndex ? 'is-complete' : ''}
                        key={step}
                      >
                        <span className="order-progress__dot" aria-hidden="true" />
                        <span>{formatOrderStatus(step)}</span>
                      </li>
                    ))}
                  </ol>
                )}

                <div className="customer-order__actions">
                  <button
                    type="button"
                    className="customer-order__details-button"
                    aria-expanded={expanded}
                    onClick={() => void toggleHistory(order.id)}
                  >
                    {expanded ? 'Hide details' : 'Items and status history'}
                  </button>
                  {canCancelCustomerOrder(order.status) && (
                    <button
                      type="button"
                      className="customer-order__cancel-button"
                      onClick={() => {
                        setActionError('')
                        setConfirmCancelId(order.id)
                      }}
                      disabled={cancellingOrderId === order.id}
                    >
                      Cancel order
                    </button>
                  )}
                </div>

                {confirmCancelId === order.id && (
                  <div className="customer-order__confirm" role="group" aria-label={`Confirm cancellation of order ${order.id}`}>
                    <p>Cancel this order? You can only cancel before the shop accepts it.</p>
                    <button
                      type="button"
                      onClick={() => void cancelOrder(order.id)}
                      disabled={cancellingOrderId === order.id}
                    >
                      {cancellingOrderId === order.id ? 'Cancelling…' : 'Yes, cancel it'}
                    </button>
                    <button type="button" onClick={() => setConfirmCancelId(null)} disabled={cancellingOrderId === order.id}>Keep order</button>
                  </div>
                )}

                {expanded && (
                  <div className="customer-order__details">
                    <div className="customer-order__items">
                      <h4>In this order</h4>
                      {order.items?.map((item) => (
                        <div className="customer-order__item" key={item.id}>
                          <span>{item.product?.name || 'Product'} <small>× {item.quantity}</small></span>
                          <strong>{formatRupees(item.price * item.quantity)}</strong>
                        </div>
                      ))}
                    </div>
                    <div className="customer-order__history">
                      <h4>Status history</h4>
                      {historyLoadingId === order.id ? (
                        <p role="status">Loading updates…</p>
                      ) : historyError ? (
                        <p className="customer-order__history-error" role="alert">{historyError}</p>
                      ) : (historyByOrder[order.id] ?? []).length ? (
                        <ol>
                          {(historyByOrder[order.id] ?? []).map((entry) => (
                            <li key={entry.id}>
                              <strong>{formatOrderStatus(entry.newStatus)}</strong>
                              <span>{entry.note || entry.changedByName || 'Order updated'}</span>
                              <time>{formatDateTime(entry.changedAt)}</time>
                            </li>
                          ))}
                        </ol>
                      ) : (
                        <p>No status changes have been recorded yet.</p>
                      )}
                    </div>
                  </div>
                )}
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}
