import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Button } from '../../components/Button'
import { ApiError, apiRequest } from '../../lib/api'
import { formatRupees } from './marketplace'
import {
  filterCustomerOrders,
  formatCustomerOrderAddress,
  formatOrderDate,
  getDeliveryModeLabel,
  getOrderItemSummary,
  getOrderStatusLabel,
  sortCustomerOrders,
} from './customer-orders'
import type {
  CustomerOrder,
  CustomerOrderFilter,
  CustomerOrderStatusEvent,
} from './customer-orders'
import './customer-orders.css'

interface CustomerOrdersProps {
  token: string
  customerId: number
  onClose: () => void
}

const FILTERS: { value: CustomerOrderFilter; label: string }[] = [
  { value: 'ALL', label: 'All orders' },
  { value: 'ACTIVE', label: 'In progress' },
  { value: 'DELIVERED', label: 'Delivered' },
  { value: 'CANCELLED', label: 'Cancelled' },
]

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Your session has expired. Sign in again to continue.'
    if (error.status === 403) return 'You can only view orders placed by your own customer account.'
    if (error.status === 404) return 'This order could not be found.'
    return error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

function statusClass(status: string): string {
  return status.toLowerCase().replace(/[^a-z0-9]+/g, '-')
}

export function CustomerOrders({ token, customerId, onClose }: CustomerOrdersProps) {
  const [orders, setOrders] = useState<CustomerOrder[]>([])
  const [filter, setFilter] = useState<CustomerOrderFilter>('ALL')
  const [selectedOrder, setSelectedOrder] = useState<CustomerOrder | null>(null)
  const [statusHistory, setStatusHistory] = useState<CustomerOrderStatusEvent[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingDetails, setLoadingDetails] = useState(false)
  const [busy, setBusy] = useState(false)
  const [showCancelForm, setShowCancelForm] = useState(false)
  const [cancelReason, setCancelReason] = useState('')
  const [error, setError] = useState('')
  const [detailError, setDetailError] = useState('')
  const [notice, setNotice] = useState('')

  const loadOrders = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const result = await apiRequest<CustomerOrder[]>(
        `/api/orders/customer/${customerId}`,
        {},
        token,
      )
      setOrders(sortCustomerOrders(result))
    } catch (caught) {
      setError(messageFor(caught))
    } finally {
      setLoading(false)
    }
  }, [customerId, token])

  useEffect(() => {
    void loadOrders()
  }, [loadOrders])

  useEffect(() => {
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape' && !busy) onClose()
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [busy, onClose])

  const visibleOrders = useMemo(
    () => filterCustomerOrders(orders, filter),
    [filter, orders],
  )

  async function openOrder(order: CustomerOrder) {
    setSelectedOrder(order)
    setStatusHistory([])
    setShowCancelForm(false)
    setCancelReason('')
    setDetailError('')
    setLoadingDetails(true)
    setNotice('')

    try {
      const [freshOrder, history] = await Promise.all([
        apiRequest<CustomerOrder>(`/api/orders/${order.id}`, {}, token),
        apiRequest<CustomerOrderStatusEvent[]>(`/api/orders/${order.id}/history`, {}, token),
      ])
      setSelectedOrder(freshOrder)
      setStatusHistory(history)
    } catch (caught) {
      setDetailError(messageFor(caught))
    } finally {
      setLoadingDetails(false)
    }
  }

  async function cancelSelectedOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedOrder || busy || selectedOrder.status !== 'PLACED') return

    setBusy(true)
    setError('')
    setDetailError('')
    setNotice('')
    try {
      const updated = await apiRequest<CustomerOrder>(
        `/api/orders/${selectedOrder.id}/cancel`,
        {
          method: 'POST',
          body: JSON.stringify({ reason: cancelReason.trim() || 'Cancelled by customer' }),
        },
        token,
      )
      setSelectedOrder(updated)
      setOrders((current) => sortCustomerOrders(
        current.map((order) => order.id === updated.id ? updated : order),
      ))
      setShowCancelForm(false)
      setCancelReason('')
      setNotice('Your order has been cancelled.')
      const history = await apiRequest<CustomerOrderStatusEvent[]>(
        `/api/orders/${updated.id}/history`,
        {},
        token,
      )
      setStatusHistory(history)
    } catch (caught) {
      setDetailError(messageFor(caught))
    } finally {
      setBusy(false)
    }
  }

  function closeDetails() {
    setSelectedOrder(null)
    setStatusHistory([])
    setShowCancelForm(false)
    setCancelReason('')
    setDetailError('')
  }

  return (
    <div className="orders-sheet-backdrop">
      <section className="orders-sheet" role="dialog" aria-modal="true" aria-labelledby="orders-title">
        <header className="orders-sheet__header">
          <div>
            <p className="customer-eyebrow">YOUR APNIKO ORDERS</p>
            <h2 id="orders-title">My orders</h2>
            <p>Every basket, remembered in one place.</p>
          </div>
          <button className="orders-sheet__close" type="button" aria-label="Close order history" onClick={onClose}>×</button>
        </header>

        {error && <p className="orders-message orders-message--error" role="alert">{error}</p>}
        {notice && <p className="orders-message" role="status">{notice}</p>}

        {!selectedOrder && (
          <>
            <div className="orders-toolbar">
              <span>{loading ? 'Finding your orders…' : `${orders.length} ${orders.length === 1 ? 'order' : 'orders'} placed`}</span>
              <Button tone="quiet" disabled={loading} onClick={() => void loadOrders()}>Refresh</Button>
            </div>

            <nav className="orders-filters" aria-label="Filter orders">
              {FILTERS.map((item) => (
                <button
                  key={item.value}
                  type="button"
                  className={filter === item.value ? 'is-active' : ''}
                  aria-pressed={filter === item.value}
                  onClick={() => setFilter(item.value)}
                >{item.label}</button>
              ))}
            </nav>

            {loading ? (
              <p className="orders-state" role="status">Loading your order history…</p>
            ) : error ? null : visibleOrders.length === 0 ? (
              <div className="orders-empty">
                <span aria-hidden="true">✳</span>
                <h3>{orders.length ? 'Nothing in this filter.' : 'Your next order starts locally.'}</h3>
                <p>{orders.length ? 'Try another order filter to see more.' : 'When you place an order with a local shop, it will appear here.'}</p>
              </div>
            ) : (
              <div className="orders-list">
                {visibleOrders.map((order) => (
                  <article className="order-card" key={order.id}>
                    <div className="order-card__top">
                      <div>
                        <span className="order-card__eyebrow">ORDER #{order.id}</span>
                        <h3>{order.shop?.name || 'Local shop'}</h3>
                      </div>
                      <span className={`order-status order-status--${statusClass(order.status)}`}>{getOrderStatusLabel(order.status)}</span>
                    </div>
                    <p className="order-card__items">{getOrderItemSummary(order.items)}</p>
                    <div className="order-card__bottom">
                      <span>{getDeliveryModeLabel(order.deliveryMode)}</span>
                      <strong>{formatRupees(order.totalAmount)}</strong>
                    </div>
                    <button className="order-card__details" type="button" onClick={() => void openOrder(order)}>
                      View order details <span aria-hidden="true">→</span>
                    </button>
                  </article>
                ))}
              </div>
            )}
          </>
        )}

        {selectedOrder && (
          <section className="order-detail" aria-labelledby="order-detail-title">
            <button className="order-detail__back" type="button" onClick={closeDetails}>← All orders</button>
            <div className="order-detail__heading">
              <div>
                <p className="customer-eyebrow">ORDER #{selectedOrder.id}</p>
                <h3 id="order-detail-title">{selectedOrder.shop?.name || 'Local shop'}</h3>
                <p>{getDeliveryModeLabel(selectedOrder.deliveryMode)}</p>
              </div>
              <span className={`order-status order-status--${statusClass(selectedOrder.status)}`}>{getOrderStatusLabel(selectedOrder.status)}</span>
            </div>

            {detailError && <p className="orders-message orders-message--error" role="alert">{detailError}</p>}
            {loadingDetails ? (
              <p className="orders-state" role="status">Loading order details and status updates…</p>
            ) : (
              <>
                <section className="order-detail__section">
                  <h4>Items in this order</h4>
                  {(selectedOrder.items ?? []).length ? (
                    <div className="order-detail__items">
                      {selectedOrder.items?.map((item) => (
                        <div key={item.id} className="order-detail__item">
                          <span>{item.product?.name || 'Item'} <small>× {item.quantity}</small></span>
                          <strong>{formatRupees(item.price * item.quantity)}</strong>
                        </div>
                      ))}
                    </div>
                  ) : <p className="order-detail__muted">Item details are unavailable.</p>}
                  <div className="order-detail__total"><span>Order total</span><strong>{formatRupees(selectedOrder.totalAmount)}</strong></div>
                </section>

                <section className="order-detail__section">
                  <h4>{selectedOrder.deliveryMode === 'CUSTOMER_PICKUP' ? 'Pickup from' : 'Delivery address saved with this order'}</h4>
                  {selectedOrder.deliveryMode === 'CUSTOMER_PICKUP' ? (
                    <p>{selectedOrder.shop?.name || 'The shop'} — {selectedOrder.shop?.address || 'Shop address not listed'}</p>
                  ) : selectedOrder.deliveryAddressSnapshot ? (
                    <div className="order-address">
                      <strong>{selectedOrder.deliveryAddressSnapshot.label || 'Delivery address'}</strong>
                      <span>{selectedOrder.deliveryAddressSnapshot.recipientName} · {selectedOrder.deliveryAddressSnapshot.phone}</span>
                      <p>{formatCustomerOrderAddress(selectedOrder.deliveryAddressSnapshot)}</p>
                    </div>
                  ) : (
                    <p className="order-detail__muted">No delivery address snapshot is attached to this order.</p>
                  )}
                </section>

                <section className="order-detail__section">
                  <h4>Order timeline</h4>
                  {statusHistory.length ? (
                    <ol className="order-timeline">
                      {statusHistory.map((event) => (
                        <li key={event.id}>
                          <span className="order-timeline__dot" aria-hidden="true" />
                          <div>
                            <strong>{getOrderStatusLabel(event.newStatus)}</strong>
                            {event.note && <p>{event.note}</p>}
                            <time>{formatOrderDate(event.changedAt)}</time>
                          </div>
                        </li>
                      ))}
                    </ol>
                  ) : (
                    <p className="order-detail__muted">No status updates have been recorded yet.</p>
                  )}
                </section>

                {selectedOrder.status === 'PLACED' && (
                  <section className="order-cancel">
                    {!showCancelForm ? (
                      <Button tone="quiet" onClick={() => setShowCancelForm(true)}>Cancel this order</Button>
                    ) : (
                      <form onSubmit={(event) => void cancelSelectedOrder(event)}>
                        <h4>Cancel order #{selectedOrder.id}?</h4>
                        <p>The shop will be notified and reserved stock will be returned.</p>
                        <label htmlFor="order-cancel-reason">Reason (optional)</label>
                        <textarea
                          id="order-cancel-reason"
                          rows={2}
                          maxLength={500}
                          value={cancelReason}
                          onChange={(event) => setCancelReason(event.target.value)}
                          placeholder="Tell the shop why you are cancelling"
                        />
                        <div>
                          <Button tone="quiet" type="button" disabled={busy} onClick={() => setShowCancelForm(false)}>Keep order</Button>
                          <Button type="submit" disabled={busy}>{busy ? 'Cancelling…' : 'Confirm cancellation'}</Button>
                        </div>
                      </form>
                    )}
                  </section>
                )}
              </>
            )}
          </section>
        )}
      </section>
    </div>
  )
}
