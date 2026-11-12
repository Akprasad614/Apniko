import { useCallback, useEffect, useState } from 'react'
import { Button } from '../../components/Button'
import { ApiError, apiRequest } from '../../lib/api'
import { formatRupees } from './marketplace'
import type { Basket } from './marketplace'
import { formatAddressLocation } from './customer-addresses'
import type { CustomerAddress } from './customer-addresses'
import './customer-checkout.css'

type DeliveryMode = 'SHOP_DELIVERY' | 'PLATFORM_DELIVERY' | 'CUSTOMER_PICKUP'

interface CheckoutOrder {
  id: number
  totalAmount: number
  status: string
  deliveryMode: DeliveryMode
}

interface CustomerCheckoutProps {
  token: string
  shopId: number
  basket: Basket
  onClose: () => void
  onManageAddresses: () => void
  onOrderPlaced: (order: CheckoutOrder) => void
}

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Your session has expired. Sign in again to continue.'
    if (error.status === 403) return 'This account cannot place customer orders.'
    return error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

export function CustomerCheckout({
  token,
  shopId,
  basket,
  onClose,
  onManageAddresses,
  onOrderPlaced,
}: CustomerCheckoutProps) {
  const [addresses, setAddresses] = useState<CustomerAddress[]>([])
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null)
  const [deliveryMode, setDeliveryMode] = useState<DeliveryMode>('SHOP_DELIVERY')
  const [loadingAddresses, setLoadingAddresses] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [paymentWarning, setPaymentWarning] = useState('')
  const [placedOrder, setPlacedOrder] = useState<CheckoutOrder | null>(null)

  const loadAddresses = useCallback(async () => {
    setLoadingAddresses(true)
    setError('')
    try {
      const result = await apiRequest<CustomerAddress[]>('/api/customer/addresses', {}, token)
      setAddresses(result)
      setSelectedAddressId((current) => {
        if (current !== null && result.some((address) => address.id === current)) return current
        return result.find((address) => address.defaultAddress)?.id ?? result[0]?.id ?? null
      })
    } catch (caught) {
      setError(messageFor(caught))
    } finally {
      setLoadingAddresses(false)
    }
  }, [token])

  useEffect(() => {
    void loadAddresses()
  }, [loadAddresses])

  useEffect(() => {
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape' && !busy) onClose()
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [busy, onClose])

  const needsAddress = deliveryMode !== 'CUSTOMER_PICKUP'
  const selectedAddress = addresses.find((address) => address.id === selectedAddressId) ?? null
  const itemCount = basket.items.reduce((total, item) => total + item.quantity, 0)

  async function placeOrder() {
    if (busy || placedOrder) return
    if (basket.items.length === 0) {
      setError('Your basket is empty. Add something from this shop before checkout.')
      return
    }
    if (needsAddress && (!selectedAddress || loadingAddresses)) {
      setError('Choose a saved delivery address before placing this order.')
      return
    }

    setBusy(true)
    setError('')
    setPaymentWarning('')

    try {
      const order = await apiRequest<CheckoutOrder>(
        `/api/customer/cart/${shopId}/checkout`,
        {
          method: 'POST',
          body: JSON.stringify({
            deliveryMode,
            addressId: needsAddress ? selectedAddressId : null,
          }),
        },
        token,
      )

      setPlacedOrder(order)
      onOrderPlaced(order)

      try {
        await apiRequest(
          `/api/payments/orders/${order.id}/initiate`,
          {
            method: 'POST',
            body: JSON.stringify({ method: 'COD' }),
          },
          token,
        )
      } catch {
        setPaymentWarning(
          'Your order was placed, but the cash-on-delivery payment record could not be created. Keep the order number and contact Apniko support before placing it again.',
        )
      }
    } catch (caught) {
      setError(messageFor(caught))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="checkout-sheet-backdrop">
      <section className="checkout-sheet" role="dialog" aria-modal="true" aria-labelledby="checkout-title">
        <header className="checkout-sheet__header">
          <div>
            <p className="customer-eyebrow">FROM YOUR LOCAL SHOP</p>
            <h2 id="checkout-title">{placedOrder ? 'Order placed.' : 'Complete your order'}</h2>
            <p>{basket.shopName}</p>
          </div>
          <button type="button" className="checkout-sheet__close" aria-label="Close checkout" onClick={onClose}>×</button>
        </header>

        {placedOrder ? (
          <div className="checkout-success">
            <span className="checkout-success__mark" aria-hidden="true">✓</span>
            <p className="customer-eyebrow">APNIKO ORDER CONFIRMATION</p>
            <h3>Thanks for shopping local.</h3>
            <p>Your order <strong>#{placedOrder.id}</strong> has been placed with {basket.shopName}.</p>
            <div className="checkout-success__summary">
              <span>Order total</span>
              <strong>{formatRupees(placedOrder.totalAmount)}</strong>
              <span>Order status</span>
              <strong>{placedOrder.status.replaceAll('_', ' ')}</strong>
              <span>Payment</span>
              <strong>Cash on delivery</strong>
            </div>
            {paymentWarning && <p className="checkout-message checkout-message--warning" role="alert">{paymentWarning}</p>}
            <Button className="checkout-success__close" onClick={onClose}>Back to the shop</Button>
          </div>
        ) : (
          <>
            {error && <p className="checkout-message checkout-message--error" role="alert">{error}</p>}

            <section className="checkout-block" aria-labelledby="delivery-mode-title">
              <div className="checkout-block__heading">
                <span>01</span>
                <div>
                  <h3 id="delivery-mode-title">How should it reach you?</h3>
                  <p>Choose the handover that works for this shop.</p>
                </div>
              </div>
              <div className="checkout-mode-list">
                <label className={deliveryMode === 'SHOP_DELIVERY' ? 'is-selected' : ''}>
                  <input type="radio" name="deliveryMode" value="SHOP_DELIVERY" checked={deliveryMode === 'SHOP_DELIVERY'} onChange={() => setDeliveryMode('SHOP_DELIVERY')} />
                  <span><strong>Shop delivery</strong><small>The shop's own delivery person brings it.</small></span>
                </label>
                <label className={deliveryMode === 'PLATFORM_DELIVERY' ? 'is-selected' : ''}>
                  <input type="radio" name="deliveryMode" value="PLATFORM_DELIVERY" checked={deliveryMode === 'PLATFORM_DELIVERY'} onChange={() => setDeliveryMode('PLATFORM_DELIVERY')} />
                  <span><strong>Apniko delivery</strong><small>A platform delivery partner handles the order.</small></span>
                </label>
                <label className={deliveryMode === 'CUSTOMER_PICKUP' ? 'is-selected' : ''}>
                  <input type="radio" name="deliveryMode" value="CUSTOMER_PICKUP" checked={deliveryMode === 'CUSTOMER_PICKUP'} onChange={() => setDeliveryMode('CUSTOMER_PICKUP')} />
                  <span><strong>I'll collect it</strong><small>Pick up the order from the shop.</small></span>
                </label>
              </div>
            </section>

            {needsAddress && (
              <section className="checkout-block" aria-labelledby="delivery-address-title">
                <div className="checkout-block__heading">
                  <span>02</span>
                  <div>
                    <h3 id="delivery-address-title">Where should we bring it?</h3>
                    <p>Select one of your saved delivery addresses.</p>
                  </div>
                </div>
                {loadingAddresses ? (
                  <p className="checkout-sheet__state" role="status">Loading saved addresses…</p>
                ) : addresses.length ? (
                  <div className="checkout-address-list">
                    {addresses.map((address) => (
                      <label className={selectedAddressId === address.id ? 'is-selected' : ''} key={address.id}>
                        <input type="radio" name="deliveryAddress" checked={selectedAddressId === address.id} onChange={() => setSelectedAddressId(address.id)} />
                        <span className="checkout-address-list__body">
                          <span className="checkout-address-list__top"><strong>{address.label}</strong>{address.defaultAddress && <small>DEFAULT</small>}</span>
                          <span>{address.recipientName} · {address.phone}</span>
                          <span>{formatAddressLocation(address)}</span>
                        </span>
                      </label>
                    ))}
                  </div>
                ) : (
                  <div className="checkout-address-empty">
                    <strong>No saved delivery address.</strong>
                    <p>Add a home or work address before choosing delivery.</p>
                    <Button tone="quiet" onClick={onManageAddresses}>Manage addresses</Button>
                  </div>
                )}
                {addresses.length > 0 && <button className="checkout-manage-addresses" type="button" onClick={onManageAddresses}>Edit or add an address</button>}
              </section>
            )}

            <section className="checkout-block" aria-labelledby="payment-title">
              <div className="checkout-block__heading">
                <span>{needsAddress ? '03' : '02'}</span>
                <div>
                  <h3 id="payment-title">Payment</h3>
                  <p>For this checkout release, pay the shop when your order arrives or when you collect it.</p>
                </div>
              </div>
              <div className="checkout-payment">
                <span aria-hidden="true">₹</span>
                <div><strong>Cash on delivery / pickup</strong><small>No online payment is collected now.</small></div>
              </div>
            </section>

            <section className="checkout-block" aria-labelledby="review-title">
              <div className="checkout-block__heading">
                <span>{needsAddress ? '04' : '03'}</span>
                <div>
                  <h3 id="review-title">Your basket</h3>
                  <p>{itemCount} {itemCount === 1 ? 'item' : 'items'} from {basket.shopName}</p>
                </div>
              </div>
              <div className="checkout-items">
                {basket.items.map((item) => (
                  <div className="checkout-item" key={item.productId}>
                    <span>{item.productName} <small>× {item.quantity}</small></span>
                    <strong>{formatRupees(item.lineTotal)}</strong>
                  </div>
                ))}
                <div className="checkout-total"><span>Order total</span><strong>{formatRupees(basket.totalAmount)}</strong></div>
              </div>
            </section>

            <div className="checkout-sheet__actions">
              <Button tone="quiet" disabled={busy} onClick={onClose}>Back to basket</Button>
              <Button className="checkout-place-order" disabled={busy || basket.items.length === 0 || loadingAddresses && needsAddress || needsAddress && !selectedAddress} onClick={() => void placeOrder()}>
                {busy ? 'Placing order…' : `Place order · ${formatRupees(basket.totalAmount)}`}
              </Button>
            </div>
            <p className="checkout-sheet__footnote">Stock is checked again by Apniko when you place the order. Your selected address is saved with this order, even if you edit your address book later.</p>
          </>
        )}
      </section>
    </div>
  )
}
