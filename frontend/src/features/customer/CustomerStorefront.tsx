import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../../auth/AuthProvider'
import { BrandMark } from '../../components/BrandMark'
import { Button } from '../../components/Button'
import { CustomerAddressBook } from './CustomerAddressBook'
import { CustomerCheckout } from './CustomerCheckout'
import { CustomerOrders } from './CustomerOrders'
import { ApiError, apiRequest } from '../../lib/api'
import {
  buildMarketplaceSearchPath,
  formatRupees,
} from './marketplace'
import type {
  Basket,
  MarketplaceInventoryItem,
  MarketplacePage,
  MarketplaceShop,
} from './marketplace'
import './customer-storefront.css'

const PAGE_SIZE = 24

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Your session has expired. Sign in again to continue.'
    if (error.status === 403) return 'This account cannot access the customer marketplace.'
    return error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

export function CustomerStorefront() {
  const { session, signOut } = useAuth()
  const token = session?.token ?? ''

  const [shops, setShops] = useState<MarketplaceShop[]>([])
  const [categories, setCategories] = useState<string[]>([])
  const [selectedShopId, setSelectedShopId] = useState<number | null>(null)
  const [products, setProducts] = useState<MarketplaceInventoryItem[]>([])
  const [basket, setBasket] = useState<Basket | null>(null)
  const [searchInput, setSearchInput] = useState('')
  const [appliedSearch, setAppliedSearch] = useState('')
  const [activeCategory, setActiveCategory] = useState('')
  const [sortBy, setSortBy] = useState<'name' | 'price'>('name')
  const [productPage, setProductPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [loadingShops, setLoadingShops] = useState(true)
  const [loadingProducts, setLoadingProducts] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [loadingBasket, setLoadingBasket] = useState(false)
  const [busyProductId, setBusyProductId] = useState<number | null>(null)
  const [pageError, setPageError] = useState('')
  const [productError, setProductError] = useState('')
  const [basketError, setBasketError] = useState('')
  const [notice, setNotice] = useState('')
  const [showAddressBook, setShowAddressBook] = useState(false)
  const [showCheckout, setShowCheckout] = useState(false)
  const [showOrders, setShowOrders] = useState(false)

  const selectedShop = useMemo(
    () => shops.find((shop) => shop.id === selectedShopId) ?? null,
    [shops, selectedShopId],
  )

  useEffect(() => {
    let active = true
    setLoadingShops(true)
    setPageError('')

    apiRequest<MarketplaceShop[]>('/api/customer/marketplace/shops', {}, token)
      .then((result) => {
        if (!active) return
        setShops(result)
        setSelectedShopId((current) => {
          if (current !== null && result.some((shop) => shop.id === current)) return current
          return result[0]?.id ?? null
        })
      })
      .catch((error: unknown) => {
        if (active) setPageError(messageFor(error))
      })
      .finally(() => {
        if (active) setLoadingShops(false)
      })

    apiRequest<string[]>('/api/customer/marketplace/categories', {}, token)
      .then((result) => {
        if (active) setCategories(result)
      })
      .catch(() => {
        if (active) setCategories([])
      })

    return () => {
      active = false
    }
  }, [token])

  const loadProducts = useCallback(async (page: number, append: boolean) => {
    if (selectedShopId === null) {
      setProducts([])
      setHasMore(false)
      return
    }

    if (append) setLoadingMore(true)
    else {
      setLoadingProducts(true)
      setProductError('')
    }

    try {
      const path = buildMarketplaceSearchPath({
        query: appliedSearch,
        category: activeCategory,
        shopId: selectedShopId,
        sortBy,
        direction: 'asc',
        page,
        size: PAGE_SIZE,
      })
      const result = await apiRequest<MarketplacePage<MarketplaceInventoryItem>>(path, {}, token)
      setProducts((current) => append ? [...current, ...result.content] : result.content)
      setProductPage(page)
      setHasMore(!result.last && page + 1 < result.totalPages)
    } catch (error) {
      setProductError(messageFor(error))
      if (!append) setProducts([])
    } finally {
      setLoadingProducts(false)
      setLoadingMore(false)
    }
  }, [activeCategory, appliedSearch, selectedShopId, sortBy, token])

  useEffect(() => {
    void loadProducts(0, false)
  }, [loadProducts])

  useEffect(() => {
    if (selectedShopId === null) {
      setBasket(null)
      return
    }

    let active = true
    setBasket(null)
    setLoadingBasket(true)
    setBasketError('')
    apiRequest<Basket>(`/api/customer/cart/${selectedShopId}`, {}, token)
      .then((result) => {
        if (active) setBasket(result)
      })
      .catch((error: unknown) => {
        if (active) setBasketError(messageFor(error))
      })
      .finally(() => {
        if (active) setLoadingBasket(false)
      })

    return () => {
      active = false
    }
  }, [selectedShopId, token])

  async function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setAppliedSearch(searchInput.trim())
  }

  async function addProduct(product: MarketplaceInventoryItem) {
    if (selectedShopId === null || busyProductId !== null) return
    setBusyProductId(product.productId)
    setBasketError('')
    setNotice('')
    try {
      const updated = await apiRequest<Basket>(
        `/api/customer/cart/${selectedShopId}/items`,
        {
          method: 'POST',
          body: JSON.stringify({ productId: product.productId, quantity: 1 }),
        },
        token,
      )
      setBasket(updated)
      setNotice(`${product.productName} added to your basket.`)
    } catch (error) {
      setBasketError(messageFor(error))
    } finally {
      setBusyProductId(null)
    }
  }

  async function changeQuantity(productId: number, quantity: number) {
    if (selectedShopId === null) return
    const item = basket?.items.find((entry) => entry.productId === productId)
    if (!item) return

    if (quantity < 1) {
      await removeProduct(productId)
      return
    }
    if (quantity > item.availableStock) {
      setBasketError(`Only ${item.availableStock} available for ${item.productName}.`)
      return
    }

    setBasketError('')
    try {
      const updated = await apiRequest<Basket>(
        `/api/customer/cart/${selectedShopId}/items/${productId}`,
        { method: 'PUT', body: JSON.stringify({ quantity }) },
        token,
      )
      setBasket(updated)
    } catch (error) {
      setBasketError(messageFor(error))
    }
  }

  async function removeProduct(productId: number) {
    if (selectedShopId === null) return
    setBasketError('')
    try {
      const updated = await apiRequest<Basket>(
        `/api/customer/cart/${selectedShopId}/items/${productId}`,
        { method: 'DELETE' },
        token,
      )
      setBasket(updated)
    } catch (error) {
      setBasketError(messageFor(error))
    }
  }

  const greetingName = session?.name.trim().split(/\s+/)[0] || 'there'
  const basketCount = basket?.items.reduce((total, item) => total + item.quantity, 0) ?? 0

  return (
    <div className="customer-storefront">
      <a className="skip-link" href="#customer-main">Skip to marketplace</a>
      <header className="customer-header">
        <div className="customer-wrap customer-header__inner">
          <BrandMark />
          <div className="customer-header__right">
            <span className="customer-header__welcome">A good day to shop local, {greetingName}.</span>
            <button className="customer-header__orders" type="button" onClick={() => { setShowAddressBook(false); setShowOrders(true) }}>My orders</button>
            <button className="customer-header__addresses" type="button" aria-label="Open delivery address book" aria-haspopup="dialog" onClick={() => setShowAddressBook(true)}>
              <span className="customer-header__addresses-label">Addresses</span>
              <span className="customer-header__addresses-icon" aria-hidden="true">⌖</span>
            </button>
            <span className="customer-header__basket-count" aria-label={`${basketCount} items in basket`}>
              Basket <strong>{basketCount}</strong>
            </span>
            <Button tone="quiet" onClick={signOut}>Sign out</Button>
          </div>
        </div>
      </header>

      <main id="customer-main" className="customer-wrap customer-main">
        <section className="customer-intro" aria-labelledby="customer-title">
          <div>
            <p className="customer-eyebrow"><span /> THE LOCAL SHELF</p>
            <h1 id="customer-title">Good things are<br /><em>closer than you think.</em></h1>
            <p className="customer-intro__copy">
              Browse the shops on Apniko, see what they have in stock, and put together a basket from the store you choose.
            </p>
          </div>
          <div className="customer-intro__aside">
            <span className="customer-intro__aside-mark" aria-hidden="true">✳</span>
            <p>Every basket starts with a local shop.</p>
            <span>CHOOSE A SHOP · FIND YOUR USUALS</span>
          </div>
        </section>

        <section className="shop-section" aria-labelledby="shop-section-title">
          <div className="customer-section-heading">
            <div>
              <p className="customer-eyebrow">01 / PICK YOUR SHOP</p>
              <h2 id="shop-section-title">The shops on your shelf</h2>
            </div>
            <span className="customer-section-heading__count">
              {loadingShops ? 'Loading shops' : `${shops.length} ${shops.length === 1 ? 'shop' : 'shops'}`}
            </span>
          </div>

          {loadingShops ? (
            <div className="customer-state customer-state--inline" role="status">Finding the shops on Apniko…</div>
          ) : pageError ? (
            <div className="customer-state customer-state--error" role="alert">{pageError}</div>
          ) : shops.length === 0 ? (
            <div className="customer-state">
              <strong>Your local shelf is getting ready.</strong>
              <p>There aren't any active shops listed on Apniko yet. Check back when a local shop joins.</p>
            </div>
          ) : (
            <div className="shop-ribbon" aria-label="Choose a shop">
              {shops.map((shop, index) => (
                <button
                  className={`shop-choice${selectedShopId === shop.id ? ' is-selected' : ''}`}
                  key={shop.id}
                  type="button"
                  aria-pressed={selectedShopId === shop.id}
                  onClick={() => {
                    setSelectedShopId(shop.id)
                    setNotice('')
                  }}
                >
                  <span className="shop-choice__index">SHOP {String(index + 1).padStart(2, '0')}</span>
                  <strong>{shop.name}</strong>
                  <span className="shop-choice__address">{shop.address || 'Address not listed'}</span>
                  <span className="shop-choice__select">{selectedShopId === shop.id ? 'Selected ✓' : 'View this shop ↗'}</span>
                </button>
              ))}
            </div>
          )}
        </section>

        {selectedShop && (
          <section className="marketplace-section" aria-labelledby="marketplace-title">
            <div className="marketplace-heading">
              <div>
                <p className="customer-eyebrow">02 / FROM THE COUNTER OF</p>
                <h2 id="marketplace-title">{selectedShop.name}</h2>
                <p className="marketplace-heading__address">{selectedShop.address || 'Local shop'}</p>
              </div>
              <div className="marketplace-heading__owner">
                <span>SHOPKEEPER</span>
                <strong>{selectedShop.ownerName || 'Local store'}</strong>
              </div>
            </div>

            <div className="marketplace-tools">
              <form className="market-search" onSubmit={handleSearch} role="search">
                <label className="market-search__label" htmlFor="market-search-input">Find something on your list</label>
                <div className="market-search__row">
                  <input
                    id="market-search-input"
                    name="q"
                    type="search"
                    value={searchInput}
                    onChange={(event) => setSearchInput(event.target.value)}
                    placeholder="Try rice, milk, tomatoes…"
                  />
                  <button type="submit">Search <span aria-hidden="true">→</span></button>
                </div>
              </form>
              <label className="market-sort">
                <span>Sort by</span>
                <select value={sortBy} onChange={(event) => setSortBy(event.target.value as 'name' | 'price')}>
                  <option value="name">Name</option>
                  <option value="price">Price</option>
                </select>
              </label>
            </div>

            <div className="marketplace-content">
              <section className="product-area" aria-label="Products">
                <div className="category-row" aria-label="Filter by category">
                  <button
                    type="button"
                    className={`category-chip${activeCategory === '' ? ' is-active' : ''}`}
                    aria-pressed={activeCategory === ''}
                    onClick={() => setActiveCategory('')}
                  >All items</button>
                  {categories.map((category) => (
                    <button
                      type="button"
                      className={`category-chip${activeCategory === category ? ' is-active' : ''}`}
                      aria-pressed={activeCategory === category}
                      key={category}
                      onClick={() => setActiveCategory(category)}
                    >{category}</button>
                  ))}
                </div>

                <div className="product-area__heading">
                  <h3>{appliedSearch ? `Results for “${appliedSearch}”` : activeCategory || 'Available today'}</h3>
                  <span>Prices and stock are shown by this shop.</span>
                </div>

                {productError && <div className="customer-state customer-state--error" role="alert">{productError}</div>}
                {loadingProducts ? (
                  <div className="customer-state customer-state--inline" role="status">Checking this shop's shelf…</div>
                ) : products.length === 0 ? (
                  <div className="customer-state customer-state--empty">
                    <span aria-hidden="true">✳</span>
                    <strong>Nothing on this shelf just yet.</strong>
                    <p>Try another search or category, or choose a different shop.</p>
                  </div>
                ) : (
                  <div className="product-list">
                    {products.map((product, index) => (
                      <article className="product-row" key={product.id}>
                        <div className={`product-row__mark product-row__mark--${index % 4}`} aria-hidden="true">
                          <span>{product.category?.slice(0, 1).toUpperCase() || 'L'}</span>
                        </div>
                        <div className="product-row__details">
                          <span className="product-row__category">{product.category || 'Everyday essentials'}</span>
                          <h4>{product.productName}</h4>
                          <span className="product-row__stock">
                            {product.stockQuantity > 0 && product.available
                              ? `${product.stockQuantity} available`
                              : 'Currently unavailable'}
                          </span>
                        </div>
                        <div className="product-row__buy">
                          <strong>{formatRupees(product.price)}</strong>
                          <button
                            type="button"
                            disabled={!product.available || product.stockQuantity < 1 || busyProductId === product.productId}
                            onClick={() => void addProduct(product)}
                          >
                            {busyProductId === product.productId ? 'Adding…' : 'Add +'}
                          </button>
                        </div>
                      </article>
                    ))}
                  </div>
                )}

                {hasMore && !loadingProducts && (
                  <button
                    type="button"
                    className="load-more"
                    disabled={loadingMore}
                    onClick={() => void loadProducts(productPage + 1, true)}
                  >{loadingMore ? 'Loading…' : 'Show more from this shop ↓'}</button>
                )}
              </section>

              <aside className="basket-panel" aria-labelledby="basket-title">
                <div className="basket-panel__topline">
                  <span>YOUR SHOPPING LIST</span>
                  <span>{basketCount.toString().padStart(2, '0')}</span>
                </div>
                <div className="basket-panel__heading">
                  <div>
                    <p className="customer-eyebrow">03 / YOUR BASKET</p>
                    <h3 id="basket-title">A few good things.</h3>
                  </div>
                  <span className="basket-panel__asterisk" aria-hidden="true">✳</span>
                </div>
                <p className="basket-panel__shop">{selectedShop.name}</p>
                {basketError && <p className="basket-message basket-message--error" role="alert">{basketError}</p>}
                {notice && <p className="basket-message basket-message--success" role="status">{notice}</p>}
                {loadingBasket ? (
                  <div className="basket-empty" role="status">Opening your basket…</div>
                ) : basket?.items.length ? (
                  <div className="basket-items">
                    {basket.items.map((item) => (
                      <div className="basket-item" key={item.productId}>
                        <div className="basket-item__main">
                          <strong>{item.productName}</strong>
                          <span>{formatRupees(item.price)} each</span>
                        </div>
                        <div className="basket-item__controls">
                          <button type="button" aria-label={`Decrease ${item.productName}`} onClick={() => void changeQuantity(item.productId, item.quantity - 1)}>−</button>
                          <span aria-label={`Quantity ${item.quantity}`}>{item.quantity}</span>
                          <button type="button" aria-label={`Increase ${item.productName}`} disabled={item.quantity >= item.availableStock} onClick={() => void changeQuantity(item.productId, item.quantity + 1)}>+</button>
                          <button className="basket-item__remove" type="button" onClick={() => void removeProduct(item.productId)}>Remove</button>
                        </div>
                        <span className="basket-item__total">{formatRupees(item.lineTotal)}</span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="basket-empty">
                    <span aria-hidden="true">↗</span>
                    <strong>Your basket starts here.</strong>
                    <p>Add something from {selectedShop.name} and it will show up here.</p>
                  </div>
                )}
                <div className="basket-total">
                  <span>Basket total</span>
                  <strong>{formatRupees(basket?.totalAmount ?? 0)}</strong>
                </div>
                <button
                  className="basket-checkout"
                  type="button"
                  disabled={loadingBasket || !basket?.items.length}
                  onClick={() => setShowCheckout(true)}
                >Continue to checkout →</button>
                <p className="basket-panel__note">Your basket belongs to this shop. Choose another shop to see its separate basket.</p>
                <Link className="basket-panel__back" to="/">Back to Apniko home ↗</Link>
              </aside>
            </div>
          </section>
        )}
      </main>

      <footer className="customer-footer">
        <div className="customer-wrap customer-footer__inner">
          <BrandMark />
          <span>Good groceries. Closer to home.</span>
          <span>APNIKO / CUSTOMER</span>
        </div>
      </footer>
      {showCheckout && selectedShopId !== null && basket && (
        <CustomerCheckout
          token={token}
          shopId={selectedShopId}
          basket={basket}
          onClose={() => setShowCheckout(false)}
          onManageAddresses={() => {
            setShowCheckout(false)
            setShowAddressBook(true)
          }}
          onOrderPlaced={(order) => {
            setBasket((current) => current ? { ...current, items: [], totalAmount: 0 } : current)
            setBasketError('')
            setNotice(`Order #${order.id} placed successfully.`)
          }}
        />
      )}
      {showAddressBook && <CustomerAddressBook token={token} onClose={() => setShowAddressBook(false)} />}
      {showOrders && session && <CustomerOrders token={token} customerId={session.userId} onClose={() => setShowOrders(false)} />}
    </div>
  )
}
