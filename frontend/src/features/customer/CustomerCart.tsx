import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '../../auth/AuthProvider'
import { ApiError, apiRequest } from '../../lib/api'
import { formatRupees } from './marketplace'
import type { Basket, MarketplaceShop } from './marketplace'
import './customer-shell.css'

type DeliveryMode = 'SHOP_DELIVERY' | 'PLATFORM_DELIVERY' | 'CUSTOMER_PICKUP'
const SELECTED_SHOP_KEY = 'apniko.customer.selectedShopId'

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Your session has expired. Sign in again.'
    if (error.status === 403) return 'This account cannot access the customer cart.'
    return error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

export function CustomerCart() {
  const { session } = useAuth()
  const navigate = useNavigate()
  const token = session?.token ?? ''
  const [shops, setShops] = useState<MarketplaceShop[]>([])
  const [selectedShopId, setSelectedShopId] = useState<number | null>(null)
  const [basket, setBasket] = useState<Basket | null>(null)
  const [loadingShops, setLoadingShops] = useState(true)
  const [loadingBasket, setLoadingBasket] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [deliveryMode, setDeliveryMode] = useState<DeliveryMode>('SHOP_DELIVERY')

  const selectedShop = useMemo(
    () => shops.find((shop) => shop.id === selectedShopId) ?? null,
    [shops, selectedShopId],
  )
  const itemCount = basket?.items.reduce((total, item) => total + item.quantity, 0) ?? 0

  useEffect(() => {
    let active = true
    setLoadingShops(true)
    apiRequest<MarketplaceShop[]>('/api/customer/marketplace/shops', {}, token)
      .then((result) => {
        if (!active) return
        setShops(result)
        const savedId = Number(window.sessionStorage.getItem(SELECTED_SHOP_KEY))
        const savedShop = result.find((shop) => shop.id === savedId)
        setSelectedShopId(savedShop?.id ?? result[0]?.id ?? null)
      })
      .catch((loadError: unknown) => {
        if (active) setError(messageFor(loadError))
      })
      .finally(() => {
        if (active) setLoadingShops(false)
      })
    return () => { active = false }
  }, [token])

  useEffect(() => {
    if (selectedShopId === null) {
      setBasket(null)
      setLoadingBasket(false)
      return
    }
    let active = true
    setLoadingBasket(true)
    setError('')
    apiRequest<Basket>(`/api/customer/cart/${selectedShopId}`, {}, token)
      .then((result) => { if (active) setBasket(result) })
      .catch((loadError: unknown) => { if (active) setError(messageFor(loadError)) })
      .finally(() => { if (active) setLoadingBasket(false) })
    return () => { active = false }
  }, [selectedShopId, token])

  function selectShop(value: string) {
    const id = Number(value)
    if (!Number.isFinite(id)) return
    setSelectedShopId(id)
    window.sessionStorage.setItem(SELECTED_SHOP_KEY, String(id))
    setError('')
    setNotice('')
  }

  async function updateQuantity(productId: number, quantity: number) {
    if (selectedShopId === null || busy || !basket) return
    const item = basket.items.find((entry) => entry.productId === productId)
    if (!item) return
    if (quantity < 1) {
      await removeItem(productId)
      return
    }
    if (quantity > item.availableStock) {
      setError(`Only ${item.availableStock} available for ${item.productName}.`)
      return
    }
    setBusy(true)
    setError('')
    try {
      const updated = await apiRequest<Basket>(
        `/api/customer/cart/${selectedShopId}/items/${productId}`,
        { method: 'PUT', body: JSON.stringify({ quantity }) },
        token,
      )
      setBasket(updated)
    } catch (updateError) {
      setError(messageFor(updateError))
    } finally {
      setBusy(false)
    }
  }

  async function removeItem(productId: number) {
    if (selectedShopId === null || busy) return
    setBusy(true)
    setError('')
    try {
      const updated = await apiRequest<Basket>(
        `/api/customer/cart/${selectedShopId}/items/${productId}`,
        { method: 'DELETE' },
        token,
      )
      setBasket(updated)
    } catch (removeError) {
      setError(messageFor(removeError))
    } finally {
      setBusy(false)
    }
  }

  async function placeOrder() {
    if (selectedShopId === null || !basket?.items.length || busy) return
    if (basket.items.some((item) => !item.available || item.quantity > item.availableStock)) {
      setError('One or more items are unavailable or have insufficient stock. Update your basket before checkout.')
      return
    }
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await apiRequest<{ id: number; status: string }>(
        `/api/customer/cart/${selectedShopId}/checkout`,
        { method: 'POST', body: JSON.stringify({ deliveryMode }) },
        token,
      )
      navigate('/customer/orders')
    } catch (checkoutError) {
      setError(messageFor(checkoutError))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="customer-screen customer-cart-screen">
      <div className="customer-screen-heading">
        <p className="customer-eyebrow">YOUR SHOPPING BAG</p>
        <h1>Your <em>cart.</em></h1>
        <p>Review your items, update quantities and choose how you would like to receive your order.</p>
      </div>

      {error && <div className="customer-state customer-state--error" role="alert">{error}</div>}
      {notice && <div className="customer-state" role="status">{notice}</div>}

      <div className="customer-cart-layout">
        <section className="customer-cart-items-panel" aria-labelledby="cart-items-title">
          <div className="customer-cart-section-title">
            <div><p className="customer-eyebrow">01 / REVIEW</p><h2 id="cart-items-title">Your items <span>{itemCount}</span></h2></div>
            <Link to="/customer/home">Continue shopping →</Link>
          </div>

          {shops.length > 1 && (
            <label className="customer-cart-shop-select">
              <span>Shopping from</span>
              <select value={selectedShopId ?? ''} onChange={(event) => selectShop(event.target.value)}>
                {shops.map((shop) => <option key={shop.id} value={shop.id}>{shop.name}</option>)}
              </select>
            </label>
          )}

          {loadingShops || loadingBasket ? (
            <div className="customer-state customer-state--inline" role="status">Loading your basket…</div>
          ) : !shops.length ? (
            <div className="customer-state customer-state--empty"><strong>No shops are available yet.</strong><p>Come back when a neighbourhood shop joins Apniko.</p></div>
          ) : !basket?.items.length ? (
            <div className="customer-state customer-state--empty">
              <span aria-hidden="true">▣</span>
              <strong>Your cart is empty.</strong>
              <p>Browse a neighbourhood shop and add a few everyday essentials.</p>
              <Link to="/customer/home" className="customer-feature-back">Explore shops →</Link>
            </div>
          ) : (
            <div className="customer-cart-item-list">
              {basket.items.map((item) => (
                <article className="customer-cart-item" key={item.productId}>
                  <div className="customer-cart-item__symbol" aria-hidden="true">{item.productName.slice(0, 1).toUpperCase()}</div>
                  <div className="customer-cart-item__details">
                    <strong>{item.productName}</strong>
                    <span>{formatRupees(item.price)} each</span>
                    <small>{item.available ? `${item.availableStock} available` : 'Currently unavailable'}</small>
                  </div>
                  <div className="customer-cart-item__actions">
                    <div className="customer-cart-quantity">
                      <button type="button" aria-label={`Decrease ${item.productName}`} disabled={busy} onClick={() => void updateQuantity(item.productId, item.quantity - 1)}>−</button>
                      <span>{item.quantity}</span>
                      <button type="button" aria-label={`Increase ${item.productName}`} disabled={busy || item.quantity >= item.availableStock} onClick={() => void updateQuantity(item.productId, item.quantity + 1)}>+</button>
                    </div>
                    <button type="button" className="customer-cart-remove" disabled={busy} onClick={() => void removeItem(item.productId)}>Remove</button>
                  </div>
                  <strong className="customer-cart-item__total">{formatRupees(item.lineTotal)}</strong>
                </article>
              ))}
            </div>
          )}
        </section>

        <aside className="customer-cart-summary" aria-labelledby="cart-summary-title">
          <p className="customer-eyebrow">02 / ORDER SUMMARY</p>
          <h2 id="cart-summary-title">A few good things.</h2>
          <p className="customer-cart-summary__shop">{selectedShop?.name ?? basket?.shopName ?? 'Your selected shop'}</p>
          <div className="customer-cart-summary__line"><span>Item total</span><strong>{formatRupees(basket?.totalAmount ?? 0)}</strong></div>
          <div className="customer-cart-summary__line"><span>Delivery and other charges</span><span>Shown at checkout</span></div>
          <div className="customer-cart-summary__total"><span>Basket total</span><strong>{formatRupees(basket?.totalAmount ?? 0)}</strong></div>

          <label className="customer-cart-delivery">
            <span>How would you like to receive it?</span>
            <select value={deliveryMode} onChange={(event) => setDeliveryMode(event.target.value as DeliveryMode)}>
              <option value="SHOP_DELIVERY">Delivery by this shop</option>
              <option value="PLATFORM_DELIVERY">Apniko delivery</option>
              <option value="CUSTOMER_PICKUP">I'll collect it</option>
            </select>
          </label>

          <p className="customer-cart-phase-note">
            This phase uses the existing order-creation flow. Delivery address selection, the final bill with charges and payment selection are being added in their dedicated phases. Use test orders only until those features are complete.
          </p>
          <button
            className="customer-cart-checkout"
            type="button"
            disabled={busy || loadingBasket || !basket?.items.length || basket.items.some((item) => !item.available || item.quantity > item.availableStock)}
            onClick={() => void placeOrder()}
          >
            {busy ? 'Please wait…' : 'Place test order'} <span aria-hidden="true">→</span>
          </button>
        </aside>
      </div>
    </section>
  )
}
