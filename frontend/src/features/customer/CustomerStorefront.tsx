import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useAuth } from '../../auth/AuthProvider'
import { BrandMark } from '../../components/BrandMark'
import { Button } from '../../components/Button'
import { CustomerAddressBook } from './CustomerAddressBook'
import { CustomerCheckout } from './CustomerCheckout'
import { CustomerOrders } from './CustomerOrders'
import { ApiError, apiRequest } from '../../lib/api'
import {
  buildMarketplaceSearchPath,
  filterMarketplaceShops,
  formatRupees,
  getCategoryIcon,
  getMarketplaceProductImage,
} from './marketplace'
import type {
  Basket,
  MarketplaceInventoryItem,
  MarketplacePage,
  MarketplaceShop,
} from './marketplace'
import './customer-storefront.css'
import './customer-discovery.css'

const PAGE_SIZE = 24

type SearchMode = 'products' | 'shops'
type SortOption = 'name' | 'price' | 'shop'

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Your session has expired. Sign in again to continue.'
    if (error.status === 403) return 'This account cannot access the customer marketplace.'
    return error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

function ProductImage({ product }: { product: MarketplaceInventoryItem }) {
  const [failed, setFailed] = useState(false)
  const imageUrl = getMarketplaceProductImage(product)

  return (
    <div className="product-card__media">
      {!failed ? (
        <img
          src={imageUrl}
          alt={product.imageUrl ? `Photo of ${product.productName}` : `${product.category || 'Grocery'} category image`}
          loading="lazy"
          decoding="async"
          onError={() => setFailed(true)}
        />
      ) : (
        <div className="product-card__image-fallback" aria-label={`Image unavailable for ${product.productName}`}>
          <span aria-hidden="true">{getCategoryIcon(product.category || '')}</span>
          <small>{product.category || 'Everyday essentials'}</small>
        </div>
      )}
    </div>
  )
}

export function CustomerStorefront() {
  const { session, signOut } = useAuth()
  const token = session?.token ?? ''

  const [shops, setShops] = useState<MarketplaceShop[]>([])
  const [categories, setCategories] = useState<string[]>([])
  const [selectedShopId, setSelectedShopId] = useState<number | null>(null)
  const [cartShopId, setCartShopId] = useState<number | null>(null)
  const [products, setProducts] = useState<MarketplaceInventoryItem[]>([])
  const [basket, setBasket] = useState<Basket | null>(null)
  const [searchMode, setSearchMode] = useState<SearchMode>('products')
  const [searchInput, setSearchInput] = useState('')
  const [appliedSearch, setAppliedSearch] = useState('')
  const [activeCategory, setActiveCategory] = useState('')
  const [sortBy, setSortBy] = useState<SortOption>('name')
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
  const productRequestId = useRef(0)

  const selectedShop = useMemo(
    () => shops.find((shop) => shop.id === selectedShopId) ?? null,
    [shops, selectedShopId],
  )
  const cartShop = useMemo(
    () => shops.find((shop) => shop.id === cartShopId) ?? null,
    [shops, cartShopId],
  )
  const visibleShops = useMemo(
    () => filterMarketplaceShops(shops, appliedSearch),
    [shops, appliedSearch],
  )

  useEffect(() => {
    let active = true
    setLoadingShops(true)
    setPageError('')

    apiRequest<MarketplaceShop[]>('/api/customer/marketplace/shops', {}, token)
      .then((result) => {
        if (active) setShops(result)
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
    const requestId = ++productRequestId.current
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
      if (requestId !== productRequestId.current) return
      setProducts((current) => append ? [...current, ...result.content] : result.content)
      setProductPage(page)
      setHasMore(!result.last && page + 1 < result.totalPages)
    } catch (error) {
      if (requestId !== productRequestId.current) return
      setProductError(messageFor(error))
      if (!append) setProducts([])
    } finally {
      if (requestId === productRequestId.current) {
        setLoadingProducts(false)
        setLoadingMore(false)
      }
    }
  }, [activeCategory, appliedSearch, selectedShopId, sortBy, token])

  useEffect(() => {
    if (searchMode === 'products') void loadProducts(0, false)
  }, [loadProducts, searchMode])

  useEffect(() => {
    if (cartShopId === null) {
      setBasket(null)
      setLoadingBasket(false)
      return
    }

    let active = true
    setBasket(null)
    setLoadingBasket(true)
    setBasketError('')
    apiRequest<Basket>(`/api/customer/cart/${cartShopId}`, {}, token)
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
  }, [cartShopId, token])

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setAppliedSearch(searchInput.trim())
    if (searchMode === 'products') setProductError('')
  }

  function chooseShop(shop: MarketplaceShop) {
    setSelectedShopId(shop.id)
    setCartShopId(shop.id)
    setSearchMode('products')
    setSearchInput('')
    setAppliedSearch('')
    setActiveCategory('')
    setNotice('')
    setBasketError('')
  }

  function browseAllProducts() {
    setSelectedShopId(null)
    setSearchMode('products')
    setSearchInput('')
    setAppliedSearch('')
    setActiveCategory('')
    setNotice('')
  }

  async function addProduct(product: MarketplaceInventoryItem) {
    if (busyProductId !== null) return
    setBusyProductId(product.productId)
    setBasketError('')
    setNotice('')
    try {
      const updated = await apiRequest<Basket>(
        `/api/customer/cart/${product.shopId}/items`,
        {
          method: 'POST',
          body: JSON.stringify({ productId: product.productId, quantity: 1 }),
        },
        token,
      )
      setCartShopId(product.shopId)
      setBasket(updated)
      setNotice(`${product.productName} added from ${product.shopName}.`)
    } catch (error) {
      setBasketError(messageFor(error))
    } finally {
      setBusyProductId(null)
    }
  }

  async function changeQuantity(productId: number, quantity: number) {
    if (cartShopId === null) return
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
        `/api/customer/cart/${cartShopId}/items/${productId}`,
        { method: 'PUT', body: JSON.stringify({ quantity }) },
        token,
      )
      setBasket(updated)
    } catch (error) {
      setBasketError(messageFor(error))
    }
  }

  async function removeProduct(productId: number) {
    if (cartShopId === null) return
    setBasketError('')
    try {
      const updated = await apiRequest<Basket>(
        `/api/customer/cart/${cartShopId}/items/${productId}`,
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
    <div className="customer-storefront customer-storefront--discovery">
      <a className="skip-link" href="#customer-main">Skip to marketplace</a>
      <header className="customer-header">
        <div className="customer-wrap customer-header__inner">
          <BrandMark />
          <div className="customer-header__right">
            <span className="customer-header__welcome">Welcome, {greetingName}</span>
            <button className="customer-header__orders" type="button" onClick={() => { setShowAddressBook(false); setShowOrders(true) }}>My orders</button>
            <button className="customer-header__addresses" type="button" aria-label="Open delivery address book" aria-haspopup="dialog" onClick={() => setShowAddressBook(true)}>
              <span className="customer-header__addresses-label">Addresses</span>
              <span className="customer-header__addresses-icon" aria-hidden="true">⌖</span>
            </button>
            <button className="customer-header__basket-count" type="button" aria-label={`Open basket with ${basketCount} items`} onClick={() => cartShopId !== null && document.getElementById('customer-basket')?.scrollIntoView({ behavior: 'smooth', block: 'start' })}>
              Basket <strong>{basketCount}</strong>
            </button>
            <Button tone="quiet" onClick={signOut}>Sign out</Button>
          </div>
        </div>
      </header>

      <main id="customer-main" className="customer-wrap customer-main customer-main--discovery">
        <section className="discovery-hero" aria-labelledby="customer-title">
          <div className="discovery-hero__copy">
            <p className="customer-eyebrow"><span /> YOUR NEIGHBOURHOOD MARKETPLACE</p>
            <h1 id="customer-title">Your everyday list.<br /><em>One local shelf.</em></h1>
            <p>Discover products across Apniko shops, compare prices and choose the store you want to order from.</p>
          </div>
          <div className="discovery-hero__note">
            <span aria-hidden="true">✳</span>
            <strong>Local shops, your choice.</strong>
            <small>Browse first. Choose the shop that works for you.</small>
          </div>
        </section>

        <section className="discovery-search" aria-label="Search products or shops">
          <form className="discovery-search__form" onSubmit={handleSearch} role="search">
            <span className="discovery-search__icon" aria-hidden="true">⌕</span>
            <input
              id="customer-market-search"
              name="q"
              type="search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder={searchMode === 'products' ? 'Search products like paneer, rice, milk…' : 'Search a shop by name or area…'}
              aria-label={searchMode === 'products' ? 'Search products across local shops' : 'Search local shops'}
            />
            {searchInput && <button type="button" className="discovery-search__clear" onClick={() => { setSearchInput(''); setAppliedSearch('') }}>Clear</button>}
            <button type="submit" className="discovery-search__submit">Search</button>
          </form>
          <div className="discovery-search__modes" role="group" aria-label="Search type">
            <button type="button" className={searchMode === 'products' ? 'is-active' : ''} aria-pressed={searchMode === 'products'} onClick={() => setSearchMode('products')}>Products</button>
            <button type="button" className={searchMode === 'shops' ? 'is-active' : ''} aria-pressed={searchMode === 'shops'} onClick={() => setSearchMode('shops')}>Shops</button>
          </div>
        </section>

        {searchMode === 'products' ? (
          <>
            <section className="discovery-shop-strip" aria-label="Choose a shop filter">
              <div className="discovery-shop-strip__heading">
                <div>
                  <p className="customer-eyebrow">SHOP BY STORE</p>
                  <h2>{selectedShop ? selectedShop.name : 'Explore local shops'}</h2>
                </div>
                {selectedShop ? (
                  <button type="button" onClick={browseAllProducts}>Browse all shops ×</button>
                ) : (
                  <button type="button" onClick={() => { setSearchMode('shops'); setSearchInput(''); setAppliedSearch('') }}>Find a shop →</button>
                )}
              </div>
              {loadingShops ? (
                <p className="customer-state customer-state--inline" role="status">Loading active shops…</p>
              ) : pageError ? (
                <p className="customer-state customer-state--error" role="alert">{pageError}</p>
              ) : shops.length === 0 ? (
                <p className="discovery-hint">No active shops are listed yet. Once local shops join Apniko, their products will appear here.</p>
              ) : (
                <div className="discovery-shop-chips">
                  <button type="button" className={!selectedShop ? 'is-selected' : ''} aria-pressed={!selectedShop} onClick={browseAllProducts}>All shops</button>
                  {shops.slice(0, 8).map((shop) => (
                    <button key={shop.id} type="button" className={selectedShopId === shop.id ? 'is-selected' : ''} aria-pressed={selectedShopId === shop.id} onClick={() => chooseShop(shop)}>
                      <span aria-hidden="true">⌂</span>{shop.name}
                    </button>
                  ))}
                  {shops.length > 8 && <button type="button" onClick={() => setSearchMode('shops')}>See all {shops.length} shops →</button>}
                </div>
              )}
            </section>

            <section className="discovery-categories" aria-label="Shop by category">
              <div className="discovery-section-heading">
                <div>
                  <p className="customer-eyebrow">FIND YOUR USUALS</p>
                  <h2>Shop by category</h2>
                </div>
                {activeCategory && <button type="button" onClick={() => setActiveCategory('')}>Clear category ×</button>}
              </div>
              <div className="discovery-category-list">
                <button type="button" className={!activeCategory ? 'is-active' : ''} aria-pressed={!activeCategory} onClick={() => setActiveCategory('')}>
                  <span aria-hidden="true">🛍️</span><strong>All products</strong>
                </button>
                {categories.map((category) => (
                  <button key={category} type="button" className={activeCategory === category ? 'is-active' : ''} aria-pressed={activeCategory === category} onClick={() => setActiveCategory(category)}>
                    <span aria-hidden="true">{getCategoryIcon(category)}</span><strong>{category}</strong>
                  </button>
                ))}
              </div>
            </section>

            <section className="discovery-products" aria-labelledby="products-heading">
              <div className="discovery-section-heading discovery-products__heading">
                <div>
                  <p className="customer-eyebrow">{selectedShop ? 'INSIDE THIS SHOP' : 'AVAILABLE FROM LOCAL SHOPS'}</p>
                  <h2 id="products-heading">{appliedSearch ? `Products matching “${appliedSearch}”` : activeCategory || (selectedShop ? `Products at ${selectedShop.name}` : 'Popular on your local shelf')}</h2>
                  <p>{selectedShop ? `Showing the catalogue and live stock for ${selectedShop.name}.` : 'Compare available products and prices across active Apniko shops.'}</p>
                </div>
                <label className="discovery-sort">
                  <span>Sort by</span>
                  <select value={sortBy} onChange={(event) => setSortBy(event.target.value as SortOption)}>
                    <option value="name">Product name</option>
                    <option value="price">Price: low to high</option>
                    <option value="shop">Shop name</option>
                  </select>
                </label>
              </div>

              {productError && <div className="customer-state customer-state--error" role="alert">{productError}</div>}
              {loadingProducts ? (
                <div className="discovery-product-skeleton" role="status" aria-label="Loading products">
                  {Array.from({ length: 8 }, (_, index) => <div className="discovery-product-skeleton__card" key={index}><span /><i /><i /></div>)}
                </div>
              ) : products.length === 0 ? (
                <div className="customer-state customer-state--empty">
                  <span aria-hidden="true">🛒</span>
                  <strong>No matching products on this shelf yet.</strong>
                  <p>Try another product name or category, or browse a different shop.</p>
                  {selectedShop && <button type="button" className="discovery-inline-action" onClick={browseAllProducts}>Browse products from all shops</button>}
                </div>
              ) : (
                <div className="discovery-product-grid">
                  {products.map((product) => (
                    <article className="product-card" key={product.id}>
                      <ProductImage product={product} />
                      <div className="product-card__body">
                        <span className="product-card__category">{product.category || 'Everyday essentials'}</span>
                        <h3>{product.productName}</h3>
                        {!selectedShop && (
                          <button type="button" className="product-card__shop" onClick={() => {
                            const shop = shops.find((entry) => entry.id === product.shopId)
                            if (shop) chooseShop(shop)
                          }}>{product.shopName} ↗</button>
                        )}
                        <div className="product-card__bottom">
                          <div>
                            <strong>{formatRupees(product.price)}</strong>
                            <small>{product.available && product.stockQuantity > 0 ? `${product.stockQuantity} available` : 'Out of stock'}</small>
                          </div>
                          <button
                            type="button"
                            className="product-card__add"
                            disabled={!product.available || product.stockQuantity < 1 || busyProductId === product.productId}
                            onClick={() => void addProduct(product)}
                          >
                            {busyProductId === product.productId ? 'Adding…' : 'Add +'}
                          </button>
                        </div>
                      </div>
                    </article>
                  ))}
                </div>
              )}

              {hasMore && !loadingProducts && (
                <button type="button" className="load-more" disabled={loadingMore} onClick={() => void loadProducts(productPage + 1, true)}>
                  {loadingMore ? 'Loading…' : 'Show more products ↓'}
                </button>
              )}
            </section>
          </>
        ) : (
          <section className="discovery-shops" aria-labelledby="shops-heading">
            <div className="discovery-section-heading">
              <div>
                <p className="customer-eyebrow">CHOOSE YOUR STORE</p>
                <h2 id="shops-heading">{appliedSearch ? `Shops matching “${appliedSearch}”` : 'Browse Apniko shops'}</h2>
                <p>Open a shop to search its own product catalogue and see its prices and stock.</p>
              </div>
              <button type="button" onClick={() => { setSearchMode('products'); setSearchInput(''); setAppliedSearch('') }}>Back to products →</button>
            </div>
            {loadingShops ? (
              <p className="customer-state customer-state--inline" role="status">Finding active shops…</p>
            ) : pageError ? (
              <p className="customer-state customer-state--error" role="alert">{pageError}</p>
            ) : visibleShops.length === 0 ? (
              <div className="customer-state customer-state--empty"><strong>No shops match that search.</strong><p>Try the shop name or a different area.</p></div>
            ) : (
              <div className="discovery-shop-grid">
                {visibleShops.map((shop) => (
                  <article className="discovery-shop-card" key={shop.id}>
                    <span className="discovery-shop-card__icon" aria-hidden="true">⌂</span>
                    <div>
                      <h3>{shop.name}</h3>
                      <p>{shop.address || 'Address not listed'}</p>
                      <small>{shop.ownerName ? `Local shop · ${shop.ownerName}` : 'Local shop on Apniko'}</small>
                    </div>
                    <button type="button" onClick={() => chooseShop(shop)}>Open shop & search products →</button>
                  </article>
                ))}
              </div>
            )}
          </section>
        )}

        <section className="discovery-local-note">
          <span aria-hidden="true">✳</span>
          <p><strong>Every order supports a local business.</strong> Product prices and available quantities come from each shop's current inventory.</p>
        </section>
      </main>

      <footer className="customer-footer">
        <div className="customer-wrap customer-footer__inner">
          <BrandMark />
          <span>Good things happen close to home.</span>
          <span>APNIKO / CUSTOMER</span>
        </div>
      </footer>

      <aside id="customer-basket" className="discovery-basket-anchor" aria-label="Your basket">
        <div className="customer-wrap">
          <div className="basket-panel basket-panel--discovery" aria-labelledby="basket-title">
            <div className="basket-panel__topline">
              <span>YOUR SHOPPING LIST</span>
              <span>{basketCount.toString().padStart(2, '0')} ITEMS</span>
            </div>
            <div className="basket-panel__heading">
              <div>
                <p className="customer-eyebrow">YOUR BASKET</p>
                <h3 id="basket-title">{cartShop?.name || 'Start with a local shop'}</h3>
              </div>
              <span className="basket-panel__asterisk" aria-hidden="true">✳</span>
            </div>
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
                <span aria-hidden="true">🛍️</span>
                <strong>Your basket starts here.</strong>
                <p>Choose a shop or add a product to open its separate basket.</p>
              </div>
            )}
            <div className="basket-total">
              <span>Basket total</span>
              <strong>{formatRupees(basket?.totalAmount ?? 0)}</strong>
            </div>
            <div className="basket-panel__actions">
              <button
                className="basket-checkout"
                type="button"
                disabled={loadingBasket || !basket?.items.length || cartShopId === null}
                onClick={() => setShowCheckout(true)}
              >Continue to checkout →</button>
              {cartShopId !== null && <button type="button" className="basket-panel__switch" onClick={() => { setSelectedShopId(cartShopId); setSearchMode('products') }}>View {cartShop?.name || 'this shop'} products</button>}
            </div>
            <p className="basket-panel__note">Each shop has its own basket. Switching shops does not combine their orders.</p>
          </div>
        </div>
      </aside>

      {showCheckout && cartShopId !== null && basket && (
        <CustomerCheckout
          token={token}
          shopId={cartShopId}
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
