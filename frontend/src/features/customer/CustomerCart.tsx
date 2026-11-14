import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '../../auth/AuthProvider'
import { ApiError, apiRequest } from '../../lib/api'
import { formatRupees } from './marketplace'
import type { Basket, MarketplaceShop } from './marketplace'
import type { CustomerAddress } from './CustomerAddresses'
import './customer-shell.css'

type DeliveryMode = 'SHOP_DELIVERY' | 'PLATFORM_DELIVERY' | 'CUSTOMER_PICKUP'
type PaymentMethod = 'COD' | 'RAZORPAY'
interface PaymentStart {
  id: number
  orderId: number
  amount: number
  currency: string
  method: PaymentMethod
  status: string
  razorpayKeyId: string | null
  razorpayOrderId: string | null
}
interface CheckoutOrder {
  id: number
  status: string
  totalAmount: number
}
interface RazorpayResponse {
  razorpay_order_id: string
  razorpay_payment_id: string
  razorpay_signature: string
}
interface RazorpayOptions {
  key: string
  amount: number
  currency: string
  name: string
  description: string
  order_id: string
  prefill: { name: string; email: string; contact: string }
  theme: { color: string }
  handler: (response: RazorpayResponse) => void
  modal: { ondismiss: () => void }
}
declare global {
  interface Window {
    Razorpay?: new (options: RazorpayOptions) => { open: () => void }
  }
}

const SELECTED_SHOP_KEY = 'apniko.customer.selectedShopId'
const SELECTED_ADDRESS_KEY = 'apniko.customer.selectedAddressId'

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Your session has expired. Sign in again.'
    if (error.status === 403) return 'This account cannot access the customer cart.'
    return error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

function loadRazorpay(): Promise<boolean> {
  if (window.Razorpay) return Promise.resolve(true)
  const existing = document.querySelector<HTMLScriptElement>('script[data-apniko-razorpay]')
  if (existing) {
    return new Promise((resolve) => {
      existing.addEventListener('load', () => resolve(Boolean(window.Razorpay)), { once: true })
      existing.addEventListener('error', () => resolve(false), { once: true })
    })
  }
  return new Promise((resolve) => {
    const script = document.createElement('script')
    script.src = 'https://checkout.razorpay.com/v1/checkout.js'
    script.async = true
    script.dataset.apnikoRazorpay = 'true'
    script.onload = () => resolve(Boolean(window.Razorpay))
    script.onerror = () => resolve(false)
    document.body.appendChild(script)
  })
}

function addressLabel(address: CustomerAddress): string {
  return [
    address.addressLine1, address.addressLine2, address.city, address.state, address.postalCode,
  ].filter(Boolean).join(', ')
}

export function CustomerCart() {
  const { session } = useAuth()
  const navigate = useNavigate()
  const token = session?.token ?? ''
  const [shops, setShops] = useState<MarketplaceShop[]>([])
  const [selectedShopId, setSelectedShopId] = useState<number | null>(null)
  const [basket, setBasket] = useState<Basket | null>(null)
  const [addresses, setAddresses] = useState<CustomerAddress[]>([])
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethod[]>(['COD'])
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null)
  const [deliveryMode, setDeliveryMode] = useState<DeliveryMode>('SHOP_DELIVERY')
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('COD')
  const [loadingShops, setLoadingShops] = useState(true)
  const [loadingBasket, setLoadingBasket] = useState(false)
  const [loadingCheckoutOptions, setLoadingCheckoutOptions] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const selectedShop = useMemo(
    () => shops.find((shop) => shop.id === selectedShopId) ?? null,
    [shops, selectedShopId],
  )
  const selectedAddress = useMemo(
    () => addresses.find((address) => address.id === selectedAddressId) ?? null,
    [addresses, selectedAddressId],
  )
  const itemCount = basket?.items.reduce((total, item) => total + item.quantity, 0) ?? 0
  const hasRazorpay = paymentMethods.includes('RAZORPAY')
  const invalidItems = basket?.items.some((item) => !item.available || item.quantity > item.availableStock) ?? false
  const needsAddress = deliveryMode !== 'CUSTOMER_PICKUP'
  const canCheckout = Boolean(
    basket?.items.length && !invalidItems && !busy && !loadingBasket
    && !loadingCheckoutOptions && (!needsAddress || selectedAddress),
  )

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
      .catch((loadError: unknown) => { if (active) setError(messageFor(loadError)) })
      .finally(() => { if (active) setLoadingShops(false) })
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
    apiRequest<Basket>('/api/customer/cart/' + selectedShopId, {}, token)
      .then((result) => { if (active) setBasket(result) })
      .catch((loadError: unknown) => { if (active) setError(messageFor(loadError)) })
      .finally(() => { if (active) setLoadingBasket(false) })
    return () => { active = false }
  }, [selectedShopId, token])

  useEffect(() => {
    let active = true
    setLoadingCheckoutOptions(true)
    Promise.all([
      apiRequest<CustomerAddress[]>('/api/customer/addresses', {}, token),
      apiRequest<PaymentMethod[]>('/api/payments/methods', {}, token),
    ])
      .then(([savedAddresses, methods]) => {
        if (!active) return
        setAddresses(savedAddresses)
        const queryId = Number(new URLSearchParams(window.location.search).get('addressId'))
        const storedId = Number(window.localStorage.getItem(SELECTED_ADDRESS_KEY))
        const selected = savedAddresses.find((address) => address.id === queryId)
          ?? savedAddresses.find((address) => address.id === storedId)
          ?? savedAddresses.find((address) => address.defaultAddress)
          ?? savedAddresses[0]
        setSelectedAddressId(selected?.id ?? null)
        if (selected) window.localStorage.setItem(SELECTED_ADDRESS_KEY, String(selected.id))
        const allowed = methods.filter((method): method is PaymentMethod => method === 'COD' || method === 'RAZORPAY')
        setPaymentMethods(allowed.includes('COD') ? allowed : ['COD', ...allowed])
        if (!allowed.includes('RAZORPAY')) setPaymentMethod('COD')
      })
      .catch((loadError: unknown) => {
        if (active) {
          setPaymentMethods(['COD'])
          setError(messageFor(loadError))
        }
      })
      .finally(() => { if (active) setLoadingCheckoutOptions(false) })
    return () => { active = false }
  }, [token])

  function selectShop(value: string) {
    const id = Number(value)
    if (!Number.isFinite(id)) return
    setSelectedShopId(id)
    window.sessionStorage.setItem(SELECTED_SHOP_KEY, String(id))
    setError('')
    setNotice('')
  }

  function selectAddress(value: string) {
    const id = value ? Number(value) : null
    setSelectedAddressId(id)
    if (id !== null) window.localStorage.setItem(SELECTED_ADDRESS_KEY, String(id))
    setError('')
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
      setError('Only ' + item.availableStock + ' available for ' + item.productName + '.')
      return
    }
    setBusy(true)
    setError('')
    try {
      const updated = await apiRequest<Basket>(
        '/api/customer/cart/' + selectedShopId + '/items/' + productId,
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
        '/api/customer/cart/' + selectedShopId + '/items/' + productId,
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
    if (needsAddress && !selectedAddressId) {
      setError('Select or add a delivery address before placing this order.')
      return
    }
    if (paymentMethod === 'RAZORPAY' && !hasRazorpay) {
      setError('Online payment is not configured on this server yet. Choose Cash on Delivery.')
      return
    }
    if (invalidItems) {
      setError('One or more items are unavailable or have insufficient stock. Update your basket before checkout.')
      return
    }

    setBusy(true)
    setError('')
    setNotice('')
    try {
      const order = await apiRequest<CheckoutOrder>(
        '/api/customer/cart/' + selectedShopId + '/checkout',
        {
          method: 'POST',
          body: JSON.stringify({
            deliveryMode,
            addressId: needsAddress ? selectedAddressId : null,
          }),
        },
        token,
      )
      const payment = await apiRequest<PaymentStart>(
        '/api/payments/orders/' + order.id + '/initiate',
        { method: 'POST', body: JSON.stringify({ method: paymentMethod }) },
        token,
      )

      if (paymentMethod === 'COD') {
        window.localStorage.removeItem(SELECTED_ADDRESS_KEY)
        navigate('/customer/orders?placed=' + order.id)
        return
      }

      const scriptLoaded = await loadRazorpay()
      if (!scriptLoaded || !window.Razorpay || !payment.razorpayKeyId || !payment.razorpayOrderId) {
        navigate('/customer/orders?payment=pending&placed=' + order.id)
        return
      }

      const checkout = new window.Razorpay({
        key: payment.razorpayKeyId,
        amount: Math.round(Number(payment.amount) * 100),
        currency: payment.currency || 'INR',
        name: 'Apniko',
        description: 'Order #' + order.id,
        order_id: payment.razorpayOrderId,
        prefill: {
          name: session?.name ?? '',
          email: session?.email ?? '',
          contact: selectedAddress?.phone ?? '',
        },
        theme: { color: '#17463a' },
        handler: async (response) => {
          try {
            await apiRequest(
              '/api/payments/verify',
              {
                method: 'POST',
                body: JSON.stringify({
                  paymentId: payment.id,
                  razorpayOrderId: response.razorpay_order_id,
                  razorpayPaymentId: response.razorpay_payment_id,
                  razorpaySignature: response.razorpay_signature,
                }),
              },
              token,
            )
            window.localStorage.removeItem(SELECTED_ADDRESS_KEY)
            navigate('/customer/orders?paid=' + order.id)
          } catch (verifyError) {
            setError('Payment could not be verified: ' + messageFor(verifyError))
            navigate('/customer/orders?payment=verify-failed&placed=' + order.id)
          }
        },
        modal: {
          ondismiss: () => navigate('/customer/orders?payment=pending&placed=' + order.id),
        },
      })
      checkout.open()
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
        <p>Review your items, choose a delivery location and select how you would like to pay.</p>
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
              <span aria-hidden="true">▣</span><strong>Your cart is empty.</strong>
              <p>Browse a neighbourhood shop and add a few everyday essentials.</p>
              <Link to="/customer/home" className="customer-feature-back">Explore shops →</Link>
            </div>
          ) : (
            <div className="customer-cart-item-list">
              {basket.items.map((item) => (
                <article className="customer-cart-item" key={item.productId}>
                  <div className="customer-cart-item__symbol" aria-hidden="true">{item.productName.slice(0, 1).toUpperCase()}</div>
                  <div className="customer-cart-item__details">
                    <strong>{item.productName}</strong><span>{formatRupees(item.price)} each</span>
                    <small>{item.available ? item.availableStock + ' available' : 'Currently unavailable'}</small>
                  </div>
                  <div className="customer-cart-item__actions">
                    <div className="customer-cart-quantity">
                      <button type="button" aria-label={'Decrease ' + item.productName} disabled={busy} onClick={() => void updateQuantity(item.productId, item.quantity - 1)}>−</button>
                      <span>{item.quantity}</span>
                      <button type="button" aria-label={'Increase ' + item.productName} disabled={busy || item.quantity >= item.availableStock} onClick={() => void updateQuantity(item.productId, item.quantity + 1)}>+</button>
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
          <p className="customer-eyebrow">02 / CHECKOUT</p>
          <h2 id="cart-summary-title">A few good things.</h2>
          <p className="customer-cart-summary__shop">{selectedShop?.name ?? basket?.shopName ?? 'Your selected shop'}</p>
          <div className="customer-cart-summary__line"><span>Item total</span><strong>{formatRupees(basket?.totalAmount ?? 0)}</strong></div>
          <div className="customer-cart-summary__line"><span>Delivery charges</span><span>Not configured</span></div>
          <div className="customer-cart-summary__total"><span>Basket total</span><strong>{formatRupees(basket?.totalAmount ?? 0)}</strong></div>

          <label className="customer-cart-delivery">
            <span>How would you like to receive it?</span>
            <select value={deliveryMode} onChange={(event) => setDeliveryMode(event.target.value as DeliveryMode)}>
              <option value="SHOP_DELIVERY">Delivery by this shop</option>
              <option value="PLATFORM_DELIVERY">Apniko delivery</option>
              <option value="CUSTOMER_PICKUP">I'll collect it</option>
            </select>
          </label>

          {needsAddress && (
            <section className="customer-checkout-section" aria-labelledby="checkout-address-title">
              <div className="customer-checkout-section__heading">
                <div><p className="customer-eyebrow">03 / DELIVERY LOCATION</p><h3 id="checkout-address-title">Where should we deliver?</h3></div>
                <Link to="/customer/account/addresses?returnTo=cart">+ Add address</Link>
              </div>
              {loadingCheckoutOptions ? (
                <p className="customer-checkout-muted">Loading saved addresses…</p>
              ) : addresses.length === 0 ? (
                <div className="customer-checkout-empty">
                  <strong>No delivery address saved.</strong>
                  <p>Add your location to continue. Use device location or enter it manually.</p>
                  <Link to="/customer/account/addresses?returnTo=cart" className="customer-checkout-add-link">Add delivery address →</Link>
                </div>
              ) : (
                <>
                  <label className="customer-cart-delivery">
                    <span>Select delivery address</span>
                    <select value={selectedAddressId ?? ''} onChange={(event) => selectAddress(event.target.value)} required>
                      <option value="">Choose a saved address</option>
                      {addresses.map((address) => (
                        <option key={address.id} value={address.id}>{address.label} · {address.addressLine1}, {address.city}</option>
                      ))}
                    </select>
                  </label>
                  {selectedAddress && (
                    <div className="customer-checkout-address-card">
                      <div><strong>{selectedAddress.label}</strong>{selectedAddress.defaultAddress && <span>Default</span>}</div>
                      <p>{selectedAddress.recipientName} · {selectedAddress.phone}</p>
                      <p>{addressLabel(selectedAddress)}</p>
                      <Link to="/customer/account/addresses?returnTo=cart">Change or edit address</Link>
                    </div>
                  )}
                </>
              )}
            </section>
          )}

          <section className="customer-checkout-section" aria-labelledby="checkout-payment-title">
            <div className="customer-checkout-section__heading">
              <div><p className="customer-eyebrow">{needsAddress ? '04' : '03'} / PAYMENT</p><h3 id="checkout-payment-title">How would you like to pay?</h3></div>
              <Link to="/customer/account/payment-methods">Payment details</Link>
            </div>
            <div className="customer-payment-options">
              <label className={'customer-payment-option' + (paymentMethod === 'COD' ? ' is-selected' : '')}>
                <input type="radio" name="paymentMethod" value="COD" checked={paymentMethod === 'COD'} onChange={() => setPaymentMethod('COD')} />
                <span className="customer-payment-option__symbol">₹</span>
                <span><strong>Cash on Delivery</strong><small>Pay the shop or delivery partner when your order arrives.</small></span>
              </label>
              <label className={'customer-payment-option' + (paymentMethod === 'RAZORPAY' ? ' is-selected' : '') + (!hasRazorpay ? ' is-unavailable' : '')}>
                <input type="radio" name="paymentMethod" value="RAZORPAY" checked={paymentMethod === 'RAZORPAY'} disabled={!hasRazorpay} onChange={() => setPaymentMethod('RAZORPAY')} />
                <span className="customer-payment-option__symbol">↗</span>
                <span><strong>Pay online with Razorpay</strong><small>{hasRazorpay ? 'UPI, cards and supported online options at secure checkout.' : 'Online payment is not configured on this server yet.'}</small></span>
              </label>
            </div>
          </section>

          <p className="customer-cart-phase-note">The total currently includes item prices only. Delivery charges are not configured in this build. Your selected address is copied onto the order.</p>
          <button className="customer-cart-checkout" type="button" disabled={!canCheckout} onClick={() => void placeOrder()}>
            {busy ? 'Processing…' : paymentMethod === 'RAZORPAY' ? 'Continue to secure payment' : 'Place order'} <span aria-hidden="true">→</span>
          </button>
          {!loadingCheckoutOptions && needsAddress && !addresses.length && <p className="customer-checkout-required-note">A delivery address is required before checkout.</p>}
          {!loadingCheckoutOptions && needsAddress && addresses.length > 0 && !selectedAddress && <p className="customer-checkout-required-note">Select a delivery address to place this order.</p>}
        </aside>
      </div>
    </section>
  )
}
