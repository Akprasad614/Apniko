import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../../auth/AuthProvider'
import { Button } from '../../components/Button'
import { ApiError, apiRequest } from '../../lib/api'
import {
  canCancelShopkeeperOrder,
  canRejectShopkeeperOrder,
  formatInventoryMovement,
  formatShopkeeperRupees,
  formatShopkeeperStatus,
  nextShopkeeperOrderStatus,
} from './shopkeeper'
import type {
  InventoryMovement,
  ShopkeeperInventoryItem,
  ShopkeeperOrder,
  ShopkeeperOrderHistory,
  ShopkeeperProduct,
  ShopkeeperShop,
} from './shopkeeper'
import './shopkeeper-dashboard.css'

type DashboardTab = 'overview' | 'orders' | 'inventory'
type OrderFilter = 'ALL' | 'PLACED' | 'ACCEPTED' | 'PREPARING' | 'READY' | 'CANCELLED'

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Your session has expired. Sign in again to continue.'
    if (error.status === 403) return 'This account cannot access this shop or its records.'
    return error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
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

export function ShopkeeperDashboard() {
  const { session, signOut } = useAuth()
  const token = session?.token ?? ''
  const [shops, setShops] = useState<ShopkeeperShop[]>([])
  const [selectedShopId, setSelectedShopId] = useState<number | null>(null)
  const [orders, setOrders] = useState<ShopkeeperOrder[]>([])
  const [inventory, setInventory] = useState<ShopkeeperInventoryItem[]>([])
  const [catalogue, setCatalogue] = useState<ShopkeeperProduct[]>([])
  const [tab, setTab] = useState<DashboardTab>('overview')
  const [orderFilter, setOrderFilter] = useState<OrderFilter>('ALL')
  const [search, setSearch] = useState('')
  const [loadingShops, setLoadingShops] = useState(true)
  const [loadingOrders, setLoadingOrders] = useState(false)
  const [loadingInventory, setLoadingInventory] = useState(false)
  const [catalogueLoading, setCatalogueLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busyKey, setBusyKey] = useState('')
  const [expandedOrderId, setExpandedOrderId] = useState<number | null>(null)
  const [historyByOrder, setHistoryByOrder] = useState<Record<number, ShopkeeperOrderHistory[]>>({})
  const [historyLoadingId, setHistoryLoadingId] = useState<number | null>(null)
  const [historyError, setHistoryError] = useState('')
  const [confirmAction, setConfirmAction] = useState<{ orderId: number; action: 'reject' | 'cancel' } | null>(null)
  const [actionReason, setActionReason] = useState('')
  const [showAddInventory, setShowAddInventory] = useState(false)
  const [newProductId, setNewProductId] = useState('')
  const [newPrice, setNewPrice] = useState('')
  const [newStock, setNewStock] = useState('')
  const [editingItem, setEditingItem] = useState<ShopkeeperInventoryItem | null>(null)
  const [editPrice, setEditPrice] = useState('')
  const [editStock, setEditStock] = useState('')
  const [editAvailable, setEditAvailable] = useState(true)
  const [restockItem, setRestockItem] = useState<ShopkeeperInventoryItem | null>(null)
  const [restockQuantity, setRestockQuantity] = useState('')
  const [restockNote, setRestockNote] = useState('')
  const [movementItem, setMovementItem] = useState<ShopkeeperInventoryItem | null>(null)
  const [movements, setMovements] = useState<InventoryMovement[]>([])
  const [movementLoading, setMovementLoading] = useState(false)
  const [lowStockOnly, setLowStockOnly] = useState(false)

  const selectedShop = useMemo(
    () => shops.find((shop) => shop.id === selectedShopId) ?? null,
    [shops, selectedShopId],
  )

  const selectedShopOrders = useMemo(
    () => orders.filter((order) => order.shop?.id === selectedShopId),
    [orders, selectedShopId],
  )

  const visibleOrders = useMemo(() => {
    const filtered = orderFilter === 'ALL'
      ? selectedShopOrders
      : selectedShopOrders.filter((order) => order.status === orderFilter)
    return [...filtered].sort((left, right) => right.id - left.id)
  }, [selectedShopOrders, orderFilter])

  const visibleInventory = useMemo(() => {
    const term = search.trim().toLowerCase()
    return inventory.filter((item) => {
      const matchesSearch = !term
        || item.productName.toLowerCase().includes(term)
        || (item.category ?? '').toLowerCase().includes(term)
      const matchesStock = !lowStockOnly || item.lowStock || item.stockQuantity <= item.lowStockThreshold
      return matchesSearch && matchesStock
    }).sort((left, right) => left.productName.localeCompare(right.productName))
  }, [inventory, lowStockOnly, search])

  const pendingOrders = selectedShopOrders.filter((order) => order.status === 'PLACED').length
  const activeOrders = selectedShopOrders.filter((order) => ['ACCEPTED', 'PREPARING'].includes(order.status)).length
  const readyOrders = selectedShopOrders.filter((order) => order.status === 'READY').length
  const deliveredSales = selectedShopOrders
    .filter((order) => order.status === 'DELIVERED')
    .reduce((total, order) => total + order.totalAmount, 0)
  const lowStockCount = inventory.filter((item) => item.lowStock || item.stockQuantity <= item.lowStockThreshold).length

  const loadShops = useCallback(async () => {
    setLoadingShops(true)
    setError('')
    try {
      const result = await apiRequest<ShopkeeperShop[]>('/api/shopkeeper/shops', {}, token)
      setShops(result)
      setSelectedShopId((current) => {
        if (current !== null && result.some((shop) => shop.id === current)) return current
        return result.find((shop) => shop.active)?.id ?? result[0]?.id ?? null
      })
    } catch (loadError) {
      setError(messageFor(loadError))
    } finally {
      setLoadingShops(false)
    }
  }, [token])

  const loadOrders = useCallback(async (quiet = false) => {
    if (!quiet) setLoadingOrders(true)
    try {
      const result = await apiRequest<ShopkeeperOrder[]>('/api/shopkeeper/orders', {}, token)
      setOrders([...result].sort((left, right) => right.id - left.id))
    } catch (loadError) {
      setError(messageFor(loadError))
    } finally {
      if (!quiet) setLoadingOrders(false)
    }
  }, [token])

  const loadInventory = useCallback(async (shopId: number, quiet = false) => {
    if (!quiet) setLoadingInventory(true)
    try {
      const result = await apiRequest<ShopkeeperInventoryItem[]>(
        `/api/shopkeeper/shops/${shopId}/inventory`,
        {},
        token,
      )
      setInventory(result)
    } catch (loadError) {
      setError(messageFor(loadError))
    } finally {
      if (!quiet) setLoadingInventory(false)
    }
  }, [token])

  useEffect(() => {
    void loadShops()
  }, [loadShops])

  useEffect(() => {
    void loadOrders()
    const intervalId = window.setInterval(() => void loadOrders(true), 20000)
    return () => window.clearInterval(intervalId)
  }, [loadOrders])

  useEffect(() => {
    if (selectedShopId === null) {
      setInventory([])
      return
    }
    void loadInventory(selectedShopId)
  }, [loadInventory, selectedShopId])

  async function openAddInventory() {
    setShowAddInventory(true)
    setError('')
    if (catalogue.length || catalogueLoading) return
    setCatalogueLoading(true)
    try {
      const result = await apiRequest<ShopkeeperProduct[]>('/api/products', {}, token)
      setCatalogue(result)
      const existingIds = new Set(inventory.map((item) => item.productId))
      const firstAvailable = result.find((product) => !existingIds.has(product.id))
      if (firstAvailable) setNewProductId(String(firstAvailable.id))
    } catch (loadError) {
      setError(messageFor(loadError))
    } finally {
      setCatalogueLoading(false)
    }
  }

  async function updateOrderStatus(order: ShopkeeperOrder, status: string) {
    setBusyKey(`order-${order.id}`)
    setError('')
    setNotice('')
    try {
      const params = new URLSearchParams({ status })
      await apiRequest<ShopkeeperOrder>(
        `/api/shopkeeper/orders/${order.id}/status?${params.toString()}`,
        { method: 'PUT' },
        token,
      )
      setNotice(`Order #${order.id} moved to ${formatShopkeeperStatus(status)}.`)
      await loadOrders(true)
    } catch (actionError) {
      setError(messageFor(actionError))
      await loadOrders(true)
    } finally {
      setBusyKey('')
    }
  }

  async function confirmOrderAction() {
    if (!confirmAction) return
    const { orderId, action } = confirmAction
    setBusyKey(`order-${orderId}`)
    setError('')
    setNotice('')
    try {
      await apiRequest<ShopkeeperOrder>(
        `/api/shopkeeper/orders/${orderId}/${action === 'reject' ? 'reject' : 'cancel'}`,
        {
          method: 'PUT',
          body: JSON.stringify({
            reason: actionReason.trim() || (action === 'reject' ? 'Rejected by shopkeeper' : 'Cancelled by shopkeeper'),
          }),
        },
        token,
      )
      setNotice(action === 'reject'
        ? `Order #${orderId} was rejected and its stock was returned.`
        : `Order #${orderId} was cancelled and its stock was returned.`)
      setConfirmAction(null)
      setActionReason('')
      await loadOrders(true)
      if (selectedShopId !== null) await loadInventory(selectedShopId, true)
    } catch (actionError) {
      setError(messageFor(actionError))
      await loadOrders(true)
    } finally {
      setBusyKey('')
    }
  }

  async function toggleOrderHistory(orderId: number) {
    if (expandedOrderId === orderId) {
      setExpandedOrderId(null)
      return
    }
    setExpandedOrderId(orderId)
    setHistoryError('')
    if (Object.prototype.hasOwnProperty.call(historyByOrder, orderId)) return
    setHistoryLoadingId(orderId)
    try {
      const result = await apiRequest<ShopkeeperOrderHistory[]>(
        `/api/orders/${orderId}/history`,
        {},
        token,
      )
      setHistoryByOrder((current) => ({ ...current, [orderId]: result }))
    } catch (loadError) {
      setHistoryError(messageFor(loadError))
    } finally {
      setHistoryLoadingId(null)
    }
  }

  async function addInventory(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (selectedShopId === null) return
    const price = Number(newPrice)
    const stockQuantity = Number(newStock)
    if (!newProductId || !Number.isFinite(price) || price < 0 || !Number.isInteger(stockQuantity) || stockQuantity < 0) {
      setError('Choose a product and enter a valid price and whole-number stock quantity.')
      return
    }
    setBusyKey('inventory-add')
    setError('')
    setNotice('')
    try {
      await apiRequest<ShopkeeperInventoryItem>(
        `/api/shopkeeper/shops/${selectedShopId}/inventory`,
        {
          method: 'POST',
          body: JSON.stringify({ productId: Number(newProductId), price, stockQuantity }),
        },
        token,
      )
      setShowAddInventory(false)
      setNewPrice('')
      setNewStock('')
      setNotice('Product added to this shop’s inventory.')
      await loadInventory(selectedShopId, true)
    } catch (actionError) {
      setError(messageFor(actionError))
    } finally {
      setBusyKey('')
    }
  }

  function startEditing(item: ShopkeeperInventoryItem) {
    setEditingItem(item)
    setEditPrice(String(item.price))
    setEditStock(String(item.stockQuantity))
    setEditAvailable(item.available)
    setError('')
  }

  async function saveInventory(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editingItem) return
    const price = Number(editPrice)
    const stockQuantity = Number(editStock)
    if (!Number.isFinite(price) || price < 0 || !Number.isInteger(stockQuantity) || stockQuantity < 0) {
      setError('Enter a valid non-negative price and whole-number stock quantity.')
      return
    }
    setBusyKey(`inventory-${editingItem.id}`)
    setError('')
    try {
      await apiRequest<ShopkeeperInventoryItem>(
        `/api/shopkeeper/shops/inventory/${editingItem.id}`,
        {
          method: 'PUT',
          body: JSON.stringify({ price, stockQuantity, available: editAvailable }),
        },
        token,
      )
      setNotice(`${editingItem.productName} was updated.`)
      setEditingItem(null)
      if (selectedShopId !== null) await loadInventory(selectedShopId, true)
    } catch (actionError) {
      setError(messageFor(actionError))
    } finally {
      setBusyKey('')
    }
  }

  async function restockInventory(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!restockItem || selectedShopId === null) return
    const quantity = Number(restockQuantity)
    if (!Number.isInteger(quantity) || quantity < 1) {
      setError('Restock quantity must be a whole number greater than zero.')
      return
    }
    setBusyKey(`restock-${restockItem.id}`)
    setError('')
    try {
      await apiRequest<ShopkeeperInventoryItem>(
        `/api/shopkeeper/shops/${selectedShopId}/inventory/${restockItem.id}/restock`,
        {
          method: 'POST',
          body: JSON.stringify({ quantity, note: restockNote.trim() }),
        },
        token,
      )
      setNotice(`${restockItem.productName} restocked by ${quantity}.`)
      setRestockItem(null)
      setRestockQuantity('')
      setRestockNote('')
      await loadInventory(selectedShopId, true)
    } catch (actionError) {
      setError(messageFor(actionError))
    } finally {
      setBusyKey('')
    }
  }

  async function updateThreshold(item: ShopkeeperInventoryItem, value: string) {
    const lowStockThreshold = Number(value)
    if (!Number.isInteger(lowStockThreshold) || lowStockThreshold < 0) return
    try {
      await apiRequest<ShopkeeperInventoryItem>(
        `/api/shopkeeper/shops/${item.shopId}/inventory/${item.id}/low-stock-threshold`,
        {
          method: 'PUT',
          body: JSON.stringify({ lowStockThreshold }),
        },
        token,
      )
      await loadInventory(item.shopId, true)
    } catch (actionError) {
      setError(messageFor(actionError))
    }
  }

  async function openMovements(item: ShopkeeperInventoryItem) {
    setMovementItem(item)
    setMovementLoading(true)
    setMovements([])
    try {
      const result = await apiRequest<InventoryMovement[]>(
        `/api/shopkeeper/shops/${item.shopId}/inventory/${item.id}/movements`,
        {},
        token,
      )
      setMovements(result)
    } catch (loadError) {
      setError(messageFor(loadError))
    } finally {
      setMovementLoading(false)
    }
  }

  const existingProductIds = new Set(inventory.map((item) => item.productId))
  const addableProducts = catalogue.filter((product) => !existingProductIds.has(product.id))
  const greeting = session?.name.trim().split(/\s+/)[0] || 'there'

  return (
    <div className="shopkeeper-app">
      <header className="shopkeeper-header">
        <div className="shopkeeper-shell shopkeeper-header__inner">
          <Link to="/" className="shopkeeper-wordmark">apniko<span>.</span></Link>
          <div className="shopkeeper-header__right">
            <span className="shopkeeper-header__welcome">Shopkeeper desk · {greeting}</span>
            <span className="shopkeeper-role">SHOPKEEPER</span>
            <Button tone="quiet" onClick={signOut}>Sign out</Button>
          </div>
        </div>
      </header>

      <main className="shopkeeper-shell shopkeeper-main">
        <section className="shopkeeper-intro">
          <div>
            <p className="shopkeeper-eyebrow">YOUR DAILY COUNTER</p>
            <h1>Good to see you,<br /><em>{greeting}.</em></h1>
            <p>Orders, stock and the small jobs that keep your shop moving.</p>
          </div>
          <div className="shopkeeper-shop-picker">
            <label htmlFor="shopkeeper-shop">Working shop</label>
            {loadingShops ? (
              <span role="status">Loading your shops…</span>
            ) : shops.length ? (
              <select
                id="shopkeeper-shop"
                value={selectedShopId ?? ''}
                onChange={(event) => setSelectedShopId(Number(event.target.value))}
              >
                {shops.map((shop) => (
                  <option value={shop.id} key={shop.id}>{shop.name}{shop.active ? '' : ' (inactive)'}</option>
                ))}
              </select>
            ) : (
              <span>No shop is linked to this account yet.</span>
            )}
            {selectedShop && <small>{selectedShop.address || 'Address not listed'}</small>}
          </div>
        </section>

        <nav className="shopkeeper-tabs" aria-label="Shopkeeper sections">
          {([
            ['overview', 'Overview'],
            ['orders', 'Orders'],
            ['inventory', 'Inventory'],
          ] as const).map(([key, label]) => (
            <button
              type="button"
              key={key}
              className={tab === key ? 'is-active' : ''}
              aria-current={tab === key ? 'page' : undefined}
              onClick={() => {
                setTab(key)
                setError('')
                setNotice('')
              }}
            >{label}</button>
          ))}
          <span className="shopkeeper-tabs__shop">{selectedShop?.name ?? 'No shop selected'}</span>
        </nav>

        {error && <div className="shopkeeper-alert shopkeeper-alert--error" role="alert">{error}</div>}
        {notice && <div className="shopkeeper-alert shopkeeper-alert--success" role="status">{notice}</div>}

        {loadingShops ? (
          <div className="shopkeeper-empty" role="status">Getting your shop ready…</div>
        ) : shops.length === 0 ? (
          <div className="shopkeeper-empty">
            <h2>No shop linked yet</h2>
            <p>Your account is active, but it does not have a shop assigned. Ask the Apniko administrator to link your store before managing orders or stock.</p>
          </div>
        ) : !selectedShop ? (
          <div className="shopkeeper-empty">Choose a shop to continue.</div>
        ) : (
          <>
            {tab === 'overview' && (
              <section className="shopkeeper-overview">
                <div className="shopkeeper-section-title">
                  <div>
                    <p className="shopkeeper-eyebrow">TODAY AT A GLANCE</p>
                    <h2>{selectedShop.name}</h2>
                  </div>
                  <button type="button" className="shopkeeper-text-button" onClick={() => { void loadOrders(); void loadInventory(selectedShop.id) }}>Refresh desk ↻</button>
                </div>
                <div className="shopkeeper-stats">
                  <article><span>Needs attention</span><strong>{pendingOrders}</strong><small>New orders to review</small></article>
                  <article><span>In progress</span><strong>{activeOrders}</strong><small>Accepted or being prepared</small></article>
                  <article><span>Ready to hand off</span><strong>{readyOrders}</strong><small>Waiting for collection or delivery</small></article>
                  <article><span>Delivered sales</span><strong>{formatShopkeeperRupees(deliveredSales)}</strong><small>Completed orders only</small></article>
                </div>
                <div className="shopkeeper-overview__columns">
                  <section className="shopkeeper-overview__panel">
                    <div className="shopkeeper-panel-heading">
                      <div><p className="shopkeeper-eyebrow">ORDER QUEUE</p><h3>Needs a look</h3></div>
                      <button type="button" onClick={() => setTab('orders')}>All orders ↗</button>
                    </div>
                    {loadingOrders ? <p className="shopkeeper-muted">Loading orders…</p> : selectedShopOrders.filter((order) => order.status === 'PLACED').length ? (
                      selectedShopOrders.filter((order) => order.status === 'PLACED').slice(0, 4).map((order) => (
                        <div className="shopkeeper-queue-row" key={order.id}>
                          <span>#{order.id}</span>
                          <div><strong>{order.items?.length ?? 0} line items</strong><small>{formatShopkeeperStatus(order.deliveryMode)}</small></div>
                          <strong>{formatShopkeeperRupees(order.totalAmount)}</strong>
                        </div>
                      ))
                    ) : <p className="shopkeeper-muted">No new orders waiting right now.</p>}
                  </section>
                  <section className="shopkeeper-overview__panel">
                    <div className="shopkeeper-panel-heading">
                      <div><p className="shopkeeper-eyebrow">STOCK CHECK</p><h3>Running low</h3></div>
                      <button type="button" onClick={() => setTab('inventory')}>Inventory ↗</button>
                    </div>
                    {loadingInventory ? <p className="shopkeeper-muted">Checking stock…</p> : inventory.filter((item) => item.lowStock || item.stockQuantity <= item.lowStockThreshold).length ? (
                      inventory.filter((item) => item.lowStock || item.stockQuantity <= item.lowStockThreshold).slice(0, 5).map((item) => (
                        <div className="shopkeeper-stock-row" key={item.id}>
                          <div><strong>{item.productName}</strong><small>{item.category || 'Everyday essentials'}</small></div>
                          <span>{item.stockQuantity} left</span>
                        </div>
                      ))
                    ) : <p className="shopkeeper-muted">Your current stock levels look healthy.</p>}
                  </section>
                </div>
              </section>
            )}

            {tab === 'orders' && (
              <section className="shopkeeper-orders">
                <div className="shopkeeper-section-title">
                  <div><p className="shopkeeper-eyebrow">FROM YOUR CUSTOMERS</p><h2>Orders</h2><p>New orders appear here automatically while this page is open.</p></div>
                  <button type="button" className="shopkeeper-text-button" onClick={() => void loadOrders()} disabled={loadingOrders}>Refresh orders ↻</button>
                </div>
                <div className="shopkeeper-filter-row">
                  <label htmlFor="order-filter">Show</label>
                  <select id="order-filter" value={orderFilter} onChange={(event) => setOrderFilter(event.target.value as OrderFilter)}>
                    <option value="ALL">All orders</option>
                    <option value="PLACED">New</option>
                    <option value="ACCEPTED">Accepted</option>
                    <option value="PREPARING">Preparing</option>
                    <option value="READY">Ready</option>
                    <option value="CANCELLED">Cancelled</option>
                  </select>
                  <span>{visibleOrders.length} {visibleOrders.length === 1 ? 'order' : 'orders'}</span>
                </div>
                {loadingOrders ? <div className="shopkeeper-empty" role="status">Loading your orders…</div> : visibleOrders.length === 0 ? (
                  <div className="shopkeeper-empty"><h3>No orders in this view</h3><p>When customers order from your shop, the details will show here.</p></div>
                ) : (
                  <div className="shopkeeper-order-list">
                    {visibleOrders.map((order) => {
                      const nextStatus = nextShopkeeperOrderStatus(order.status)
                      const expanded = expandedOrderId === order.id
                      return (
                        <article className="shopkeeper-order" key={order.id}>
                          <div className="shopkeeper-order__top">
                            <div><span className="shopkeeper-order__number">ORDER #{order.id}</span><h3>{order.items?.length ?? 0} items from {order.shop?.name || selectedShop.name}</h3><small>{formatShopkeeperStatus(order.deliveryMode)}</small></div>
                            <div className="shopkeeper-order__summary"><strong>{formatShopkeeperRupees(order.totalAmount)}</strong><span className={`shopkeeper-order__status shopkeeper-order__status--${order.status.toLowerCase()}`}>{formatShopkeeperStatus(order.status)}</span></div>
                          </div>
                          <div className="shopkeeper-order__items">
                            {(order.items ?? []).map((item) => (
                              <div className="shopkeeper-order__item" key={item.id}>
                                <span>{item.product?.name || 'Product'} <small>× {item.quantity}</small></span>
                                <strong>{formatShopkeeperRupees(item.price * item.quantity)}</strong>
                              </div>
                            ))}
                          </div>
                          <div className="shopkeeper-order__actions">
                            <button type="button" className="shopkeeper-text-button" aria-expanded={expanded} onClick={() => void toggleOrderHistory(order.id)}>{expanded ? 'Hide timeline' : 'Status timeline'}</button>
                            <div>
                              {nextStatus && <button type="button" className="shopkeeper-action-button" disabled={busyKey === `order-${order.id}`} onClick={() => void updateOrderStatus(order, nextStatus)}>{busyKey === `order-${order.id}` ? 'Saving…' : nextStatus === 'ACCEPTED' ? 'Accept order' : nextStatus === 'PREPARING' ? 'Start preparing' : 'Mark ready'} <span>→</span></button>}
                              {canRejectShopkeeperOrder(order.status) && <button type="button" className="shopkeeper-danger-button" disabled={busyKey === `order-${order.id}`} onClick={() => { setConfirmAction({ orderId: order.id, action: 'reject' }); setActionReason('') }}>Reject</button>}
                              {!canRejectShopkeeperOrder(order.status) && canCancelShopkeeperOrder(order.status) && <button type="button" className="shopkeeper-danger-button" disabled={busyKey === `order-${order.id}`} onClick={() => { setConfirmAction({ orderId: order.id, action: 'cancel' }); setActionReason('') }}>Cancel order</button>}
                            </div>
                          </div>
                          {confirmAction?.orderId === order.id && (
                            <div className="shopkeeper-order__confirm">
                              <p>{confirmAction.action === 'reject' ? 'Reject this new order? Its reserved stock will be returned to inventory.' : 'Cancel this order? Its reserved stock will be returned to inventory.'}</p>
                              <input aria-label="Reason" value={actionReason} maxLength={500} onChange={(event) => setActionReason(event.target.value)} placeholder="Reason (optional)" />
                              <button type="button" className="shopkeeper-danger-button shopkeeper-danger-button--solid" disabled={busyKey === `order-${order.id}`} onClick={() => void confirmOrderAction()}>{busyKey === `order-${order.id}` ? 'Working…' : confirmAction.action === 'reject' ? 'Confirm rejection' : 'Confirm cancellation'}</button>
                              <button type="button" className="shopkeeper-text-button" onClick={() => setConfirmAction(null)} disabled={busyKey === `order-${order.id}`}>Keep order</button>
                            </div>
                          )}
                          {expanded && (
                            <div className="shopkeeper-order__history">
                              <h4>Status history</h4>
                              {historyLoadingId === order.id ? <p>Loading timeline…</p> : historyError ? <p className="shopkeeper-error-copy">{historyError}</p> : (historyByOrder[order.id] ?? []).length ? (
                                <ol>{(historyByOrder[order.id] ?? []).map((entry) => <li key={entry.id}><strong>{formatShopkeeperStatus(entry.newStatus)}</strong><span>{entry.note || entry.changedByName || 'Order updated'}</span><time>{formatDateTime(entry.changedAt)}</time></li>)}</ol>
                              ) : <p>No timeline entries were returned.</p>}
                            </div>
                          )}
                        </article>
                      )
                    })}
                  </div>
                )}
              </section>
            )}

            {tab === 'inventory' && (
              <section className="shopkeeper-inventory">
                <div className="shopkeeper-section-title">
                  <div><p className="shopkeeper-eyebrow">WHAT'S ON YOUR SHELF</p><h2>Inventory</h2><p>Keep prices and available quantities up to date for customers.</p></div>
                  <button type="button" className="shopkeeper-primary-button" onClick={() => void openAddInventory()}>Add a product <span>+</span></button>
                </div>
                <div className="shopkeeper-inventory-tools">
                  <input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Find a product or category…" aria-label="Search inventory" />
                  <label><input type="checkbox" checked={lowStockOnly} onChange={(event) => setLowStockOnly(event.target.checked)} /> Low stock only ({lowStockCount})</label>
                  <button type="button" className="shopkeeper-text-button" disabled={loadingInventory} onClick={() => void loadInventory(selectedShop.id)}>Refresh stock ↻</button>
                </div>

                {showAddInventory && (
                  <form className="shopkeeper-form-panel" onSubmit={(event) => void addInventory(event)}>
                    <div className="shopkeeper-form-panel__heading"><div><p className="shopkeeper-eyebrow">ADD TO THIS SHOP</p><h3>Put a product on the shelf</h3></div><button type="button" onClick={() => setShowAddInventory(false)} aria-label="Close add product form">×</button></div>
                    {catalogueLoading ? <p>Loading the product catalogue…</p> : addableProducts.length ? (
                      <div className="shopkeeper-form-grid">
                        <label>Product<select required value={newProductId} onChange={(event) => setNewProductId(event.target.value)}><option value="">Choose a product</option>{addableProducts.map((product) => <option key={product.id} value={product.id}>{product.name}{product.category ? ` · ${product.category}` : ''}</option>)}</select></label>
                        <label>Price (₹)<input type="number" min="0" step="0.01" required value={newPrice} onChange={(event) => setNewPrice(event.target.value)} placeholder="e.g. 48" /></label>
                        <label>Opening stock<input type="number" min="0" step="1" required value={newStock} onChange={(event) => setNewStock(event.target.value)} placeholder="e.g. 20" /></label>
                      </div>
                    ) : !catalogueLoading ? <p className="shopkeeper-muted">Every catalogue product is already listed in this shop. Ask an administrator to add a new product to the shared catalogue when needed.</p> : null}
                    <div className="shopkeeper-form-actions"><button className="shopkeeper-primary-button" type="submit" disabled={busyKey === 'inventory-add' || catalogueLoading || addableProducts.length === 0}>{busyKey === 'inventory-add' ? 'Adding…' : 'Add to inventory'}</button><button type="button" className="shopkeeper-text-button" onClick={() => setShowAddInventory(false)}>Close</button></div>
                  </form>
                )}

                {editingItem && (
                  <form className="shopkeeper-form-panel" onSubmit={(event) => void saveInventory(event)}>
                    <div className="shopkeeper-form-panel__heading"><div><p className="shopkeeper-eyebrow">EDIT LISTING</p><h3>{editingItem.productName}</h3></div><button type="button" onClick={() => setEditingItem(null)} aria-label="Close edit inventory form">×</button></div>
                    <div className="shopkeeper-form-grid">
                      <label>Price (₹)<input type="number" min="0" step="0.01" value={editPrice} onChange={(event) => setEditPrice(event.target.value)} required /></label>
                      <label>Available quantity<input type="number" min="0" step="1" value={editStock} onChange={(event) => setEditStock(event.target.value)} required /></label>
                      <label className="shopkeeper-checkbox-field"><input type="checkbox" checked={editAvailable} onChange={(event) => setEditAvailable(event.target.checked)} /> Listed for customers</label>
                    </div>
                    <p className="shopkeeper-form-help">Hiding a listing keeps its stock record and movement history. A listing cannot be shown when its quantity is zero.</p>
                    <div className="shopkeeper-form-actions"><button className="shopkeeper-primary-button" type="submit" disabled={busyKey === `inventory-${editingItem.id}`}>{busyKey === `inventory-${editingItem.id}` ? 'Saving…' : 'Save changes'}</button><button type="button" className="shopkeeper-text-button" onClick={() => setEditingItem(null)}>Discard</button></div>
                  </form>
                )}

                {restockItem && (
                  <form className="shopkeeper-form-panel" onSubmit={(event) => void restockInventory(event)}>
                    <div className="shopkeeper-form-panel__heading"><div><p className="shopkeeper-eyebrow">STOCK IN</p><h3>Restock {restockItem.productName}</h3></div><button type="button" onClick={() => setRestockItem(null)} aria-label="Close restock form">×</button></div>
                    <div className="shopkeeper-form-grid">
                      <label>Units received<input type="number" min="1" step="1" required value={restockQuantity} onChange={(event) => setRestockQuantity(event.target.value)} placeholder="How many arrived?" /></label>
                      <label>Note (optional)<input maxLength={500} value={restockNote} onChange={(event) => setRestockNote(event.target.value)} placeholder="Supplier delivery, purchase, etc." /></label>
                    </div>
                    <div className="shopkeeper-form-actions"><button className="shopkeeper-primary-button" type="submit" disabled={busyKey === `restock-${restockItem.id}`}>{busyKey === `restock-${restockItem.id}` ? 'Saving…' : 'Record restock'}</button><button type="button" className="shopkeeper-text-button" onClick={() => setRestockItem(null)}>Cancel</button></div>
                  </form>
                )}

                {loadingInventory ? <div className="shopkeeper-empty" role="status">Loading stock records…</div> : visibleInventory.length ? (
                  <div className="shopkeeper-inventory-table-wrap">
                    <table className="shopkeeper-inventory-table">
                      <thead><tr><th>Product</th><th>Price</th><th>On hand</th><th>Listing</th><th>Low-stock level</th><th>Actions</th></tr></thead>
                      <tbody>
                        {visibleInventory.map((item) => (
                          <tr key={item.id}>
                            <td><strong>{item.productName}</strong><small>{item.category || 'Everyday essentials'}</small></td>
                            <td>{formatShopkeeperRupees(item.price)}</td>
                            <td><strong>{item.stockQuantity}</strong>{(item.lowStock || item.stockQuantity <= item.lowStockThreshold) && <span className="shopkeeper-low-stock">Low</span>}</td>
                            <td><span className={item.available && item.stockQuantity > 0 ? 'shopkeeper-listing shopkeeper-listing--live' : 'shopkeeper-listing'}>{item.available && item.stockQuantity > 0 ? 'Visible' : 'Hidden'}</span></td>
                            <td><input className="shopkeeper-threshold-input" type="number" min="0" step="1" aria-label={`Low stock threshold for ${item.productName}`} value={item.lowStockThreshold} onChange={(event) => { const value = event.target.value; setInventory((current) => current.map((entry) => entry.id === item.id ? { ...entry, lowStockThreshold: Number(value) } : entry)) }} onBlur={(event) => void updateThreshold(item, event.target.value)} /></td>
                            <td><div className="shopkeeper-row-actions"><button type="button" onClick={() => startEditing(item)}>Edit</button><button type="button" onClick={() => { setRestockItem(item); setRestockQuantity(''); setRestockNote('') }}>Restock</button><button type="button" onClick={() => void openMovements(item)}>History</button></div></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <div className="shopkeeper-empty"><h3>{inventory.length ? 'No matching products' : 'Your shelf is empty'}</h3><p>{inventory.length ? 'Try a different search or switch off the low-stock filter.' : 'Add products from the shared catalogue to start managing your shelf.'}</p></div>
                )}

                {movementItem && (
                  <div className="shopkeeper-movements">
                    <div className="shopkeeper-panel-heading"><div><p className="shopkeeper-eyebrow">AUDIT TRAIL</p><h3>{movementItem.productName}</h3></div><button type="button" onClick={() => setMovementItem(null)}>Close ×</button></div>
                    {movementLoading ? <p>Loading stock movements…</p> : movements.length ? (
                      <div className="shopkeeper-movement-list">{movements.map((movement) => <article key={movement.id}><div><strong>{formatInventoryMovement(movement.movementType)}</strong><small>{movement.note || movement.performedByName || 'Stock updated'}</small><time>{formatDateTime(movement.createdAt)}</time></div><span className={movement.quantityChange >= 0 ? 'is-increase' : 'is-decrease'}>{movement.quantityChange > 0 ? '+' : ''}{movement.quantityChange}</span><small>{movement.previousQuantity} → {movement.newQuantity}</small></article>)}</div>
                    ) : <p className="shopkeeper-muted">No stock movements have been recorded for this item.</p>}
                  </div>
                )}
              </section>
            )}
          </>
        )}
      </main>
      <footer className="shopkeeper-footer"><div className="shopkeeper-shell"><Link to="/" className="shopkeeper-wordmark">apniko<span>.</span></Link><span>Good groceries. Closer to home.</span><span>SHOPKEEPER / APNIKO</span></div></footer>
    </div>
  )
}
