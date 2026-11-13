import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../../auth/AuthProvider'
import { BrandMark } from '../../components/BrandMark'
import { Button } from '../../components/Button'
import { ApiError, apiRequest } from '../../lib/api'
import { CustomerAddressBook } from './CustomerAddressBook'
import { CustomerCheckout } from './CustomerCheckout'
import { CustomerOrders } from './CustomerOrders'
import { ProductImage } from './ProductImage'
import {
  buildMarketplaceSearchPath,
  filterMarketplaceShops,
  formatRupees,
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
type ProductSort = 'name' | 'price'

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Your session has expired. Sign in again to continue.'
    if (error.status === 403) return 'This account cannot access the customer marketplace.'
    return error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

function shopInitials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('')
}

export function CustomerStorefront() {
  const { session, signOut } = useAuth()
  const token = session?.token ?? ''

  const [shops, setShops] = useState<MarketplaceShop[]>([])
  const [categories, setCategories] = useState<string[]>([])
  const [searchMode, setSearchMode] = useState<SearchMode>('products')
  const [browseShopId, setBrowseShopId] = useState<number | null>(null)
  const [cartShopId, setCartShopId] = useState<number | null>(null)
  const [products, setProducts] = useState<MarketplaceInventoryItem[]>([])
  const [basket, setBasket] = useState<Basket | null>(null)
  const [searchInput, setSearchInput] = useState('')
  const [appliedSearch, setAppliedSearch] = useState('')
  const [activeCategory, setActiveCategory] = useState('')
  const [sortBy, setSortBy] = useState<ProductSort>('name')
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

  const productRequestRef = useRef(0)
  const basketRequestRef = useRef(0)
  const skipNextBasketFetchRef = useRef(false)

  const browseShop = useMemo(
    () => shops.find((shop) => shop.id === browseShopId) ?? null,
    [shops, browseShopId],
  )
  const cartShop = useMemo(
    () => shops.find((shop) => shop.id === cartShopId) ?? null,
    [shops, cartShopId],
  )
  const filteredShops = useMemo(
    () => filterMarketplaceShops(shops, appliedSearch),
    [shops, appliedSearch],
  )

  useEffect(() => {
    let active = true
    setLoadingShops(true)
    setPageError('')

    apiRequest<MarketplaceShop[]>('/api/customer/marketplace/shops', {}, token)
      .then((result) => {
        if (active) setShops(result.filter((shop) => shop.active))
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
    if (searchMode !== 'products') return

    const requestId = ++productRequestRef.current
    if (append) setLoadingMore(true)
    else {
      setLoadingProducts(true)
      setProductError('')
    }

    try {
      const path = buildMarketplaceSearchPath({
        query: appliedSearch,
        category: activeCategory,
        shopId: browseShopId,
        sortBy,
        direction: 'asc',
        page,
        size: PAGE_SIZE,
      })
      const result = await apiRequest<MarketplacePage<MarketplaceInventoryItem>>(path, {}, token)
      if (requestId !== productRequestRef.current) return
      setProducts((current) => append ? [...current, ...result.content] : result.content)
      setProductPage(page)
      setHasMore(!result.last && page + 1 < result.totalPages)
    } catch (error) {
      if (requestId !== productRequestRef.current) return
      setProductError(messageFor(error))
      if (!append) setProducts([])
    } finally {
      if (requestId === productRequestRef.current) {
        setLoadingProducts(false)
        setLoadingMore(false)
      }
    }
  }, [activeCategory, appliedSearch, browseShopId, searchMode, sortBy, token])

  useEffect(() => {
    if (searchMode === 'products') void loadProducts(0, false)
    else {
      productRequestRef.current += 1
      setProducts([])
      setHasMore(false)
      setLoadingProducts(false)
      setLoadingMore(false)
    }
  }, [loadProducts, searchMode])

  useEffect(() => {
    if (cartShopId === null) {
      setBasket(null)
      setLoadingBasket(false)
      return
    }

    if (skipNextBasketFetchRef.current) {
      skipNextBasketFetchRef.current = false
      return
    }

    let active = true
    const requestId = ++basketRequestRef.current
    setBasket(null)
    setLoadingBasket(true)
    setBasketError('')

    apiRequest<Basket>(`/api/customer/cart/${cartShopId}`, {}, token)
      .then((result) => {
        if (active && requestId === basketRequestRef.current) setBasket(result)
      })
      .catch((error: unknown) => {
        if (active && requestId === basketRequestRef.current) setBasketError(messageFor(error))
      })
      .finally(() => {
        if (active && requestId === basketRequestRef.current) setLoadingBasket(false)
      })

    return () => {
      active = false
    }
  }, [cartShopId, token])

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setAppliedSearch(searchInput.trim())
  }

  function changeSearchMode(mode: SearchMode) {
    setSearchMode(mode)
    setActiveCategory('')
    setBrowseShopId(null)
    setAppliedSearch(searchInput.trim())
    setProductError('')
  }

  function openShop(shop: MarketplaceShop) {
    setBrowseShopId(shop.id)
    setSearchMode('products')
    setSearchInput('')
    setAppliedSearch('')
    setActiveCategory('')
    setProductError('')
    setNotice('')
  }

  function showAllProducts() {
    setBrowseShopId(null)
    setSearchMode('products')
    setSearchInput('')
    setAppliedSearch('')
    setActiveCategory('')
  }

  async function addProduct(product: MarketplaceInventoryItem) {
    if (busyProductId !== null) return

    const switchingShop = cartShopId !== product.shopId
    const requestId = ++basketRequestRef.current
    if (switchingShop) {
      skipNextBasketFetchRef.current = true
      setCartShopId(product.shopId)
      setBasket(null)
      setLoadingBasket(true)
    }

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
      if (requestId === basketRequestRef.current) {
        setCartShopId(product.shopId)
        setBasket(updated)
        setLoadingBasket(false)
        setNotice(`${product.productName} added from ${product.shopName}.`)
      }
    } catch (error) {
      if (requestId === basketRequestRef.current) {
        setBasketError(messageFor(error))
        try {
          const existing = await apiRequest<Basket>(`/api/customer/cart/${product.shopId}`, {}, token)
          if (requestId === basketRequestRef.current) setBasket(existing)
        } catch {
          if (requestId === basketRequestRef.current) setBasket(null)
        } finally {
          if (requestId === basketRequestRef.current) setLoadingBasket(false)
        }
      }
    } finally {
      setBusyProductId(null)
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

  const greetingName = session?.name.trim().split(/\s+/)[0] || 'there'
  const basketCount = basket?.items.reduce((total, item) => total + item.quantity, 0) ?? 0
  const resultHeading = browseShop
    ? `Products at ${browseShop.name}`
    : appliedSearch
      ? `Products matching “${appliedSearch}”`
      : activeCategory || 'Popular essentials across local shops'

  return (
    <div className="customer-storefront customer-discovery">
      <a className="skip-link" href="#customer-main">Skip to marketplace</a>
      <header className="customer-header discovery-header">
        <div className="customer-wrap customer-header__inner">
          <BrandMark />
          <div className="customer-header__right discovery-header__actions">
            <span className="customer-header__welcome">Welcome, {greetingName}</span>
            <button
              className="customer-header__addresses"
              type="button"
              aria-label="Manage delivery addresses"
              aria-haspopup="dialog"
              onClick={() => setShowAddressBook(true)}
            >
              <span className="customer-header__addresses-label">Delivery address</span>
              <span className="customer-header__addresses-icon" aria-hidden="true">⌖</span>
            </button>
            <button
              className="customer-header__orders"
              type="button"
              onClick={() => setShowOrders(true)}
            >My orders</button>
            <a className="discovery-cart-link" href="#customer-basket">
              Basket <strong>{basketCount}</strong>
            </a>
            <Button tone="quiet" onClick={signOut}>Sign out</Button>
          </div>
        </div>
      </header>

      <main id="customer-main" className="customer-wrap customer-main discovery-main">
        <section className="discovery-welcome" aria-labelledby="discovery-title">
          <div>
            <p className="discovery-eyebrow">YOUR EVERYDAY MARKETPLACE</p>
            <h1 id="discovery-title">What do you need<br /><em>today?</em></h1>
            <p>Find everyday essentials from the shops on Apniko. Compare what is available, choose your shop and build your basket.</p>
          </div>
          <div className="discovery-welcome__aside">
            <span className="discovery-welcome__spark" aria-hidden="true">✳</span>
            <strong>Good things are closer.</strong>
            <span>PRODUCTS · LOCAL SHOPS · ONE PLACE</span>
          </div>
        </section>

        <section className="discovery-search-area" aria-label="Marketplace search">
          <div className="discovery-search-tabs" role="group" aria-label="Search by">
            <button
              type="button"
              className={searchMode === 'products' ? 'is-active' : ''}
              aria-pressed={searchMode === 'products'}
              onClick={() => changeSearchMode('products')}
            >Products</button>
            <button
              type="button"
              className={searchMode === 'shops' ? 'is-active' : ''}
              aria-pressed={searchMode === 'shops'}
              onClick={() => changeSearchMode('shops')}
            >Shops</button>
          </div>
          <form className="discovery-search" onSubmit={handleSearch} role="search">
            <label className="visually-hidden" htmlFor="discovery-search-input">
              {searchMode === 'shops' ? 'Search shops' : browseShop ? `Search products in ${browseShop.name}` : 'Search products across shops'}
            </label>
            <span className="discovery-search__icon" aria-hidden="true">⌕</span>
            <input
              id="discovery-search-input"
              name="q"
              type="search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder={searchMode === 'shops'
                ? 'Search shop name, owner or area…'
                : browseShop
                  ? `Search products at ${browseShop.name}…`
                  : 'Search for a product like rice, milk or paneer…'}
            />
            {searchInput && (
              <button
                type="button"
                className="discovery-search__clear"
                onClick={() => {
                  setSearchInput('')
                  setAppliedSearch('')
                }}
                aria-label="Clear search"
              >×</button>
            )}
            <button className="discovery-search__submit" type="submit">Search</button>
          </form>
        </section>

        {pageError && <div className="customer-state customer-state--error" role="alert">{pageError}</div>}

        {searchMode === 'shops' ? (
          <section className="discovery-results-section" aria-labelledby="shop-results-title">
            <div className="discovery-section-heading">
              <div>
                <p className="discovery-eyebrow">FIND YOUR REGULAR SHOP</p>
                <h2 id="shop-results-title">{appliedSearch ? `Shops matching “${appliedSearch}”` : 'Shops on Apniko'}</h2>
              </div>
              <span>{loadingShops ? 'Loading…' : `${filteredShops.length} ${filteredShops.length === 1 ? 'shop' : 'shops'}`}</span>
            </div>
            {loadingShops ? (
              <div className="customer-state customer-state--inline" role="status">Finding shops…</div>
            ) : filteredShops.length === 0 ? (
              <div className="customer-state discovery-empty">
                <strong>No matching shops found.</strong>
                <p>Try a different shop name, owner's name or area.</p>
              </div>
            ) : (
              <div className="discovery-shop-grid">
                {filteredShops.map((shop) => (
                  <article className="discovery-shop-card" key={shop.id}>
                    <div className="discovery-shop-card__top">
                      <span className="discovery-shop-card__initials" aria-hidden="true">{shopInitials(shop.name)}</span>
                      <span className="discovery-shop-card__state"><i /> Active on Apniko</span>
                    </div>
                    <h3>{shop.name}</h3>
                    <p>{shop.address || 'Address not listed'}</p>
                    {shop.ownerName && <span className="discovery-shop-card__owner">Run by {shop.ownerName}</span>}
                    <button type="button" onClick={() => openShop(shop)}>Browse this shop <span aria-hidden="true">→</span></button>
                  </article>
                ))}
              </div>
            )}
          </section>
        ) : (
          <>
            {browseShop ? (
              <section className="discovery-shop-context" aria-label="Selected shop">
                <button type="button" onClick={showAllProducts}>← All products and shops</button>
                <div>
                  <p className="discovery-eyebrow">SHOPPING FROM</p>
                  <h2>{browseShop.name}</h2>
                  <span>{browseShop.address || 'Local shop'}</span>
                </div>
              </section>
            ) : (
              <section className="discovery-shop-strip" aria-labelledby="shops-strip-title">
                <div className="discovery-section-heading discovery-section-heading--compact">
                  <div>
                    <p className="discovery-eyebrow">SHOP BY STORE</p>
                    <h2 id="shops-strip-title">Explore local shops</h2>
                  </div>
                  <button type="button" onClick={() => changeSearchMode('shops')}>View all shops <span aria-hidden="true">→</span></button>
                </div>
                {loadingShops ? (
                  <div className="discovery-shop-strip__loading" role="status">Loading shops…</div>
                ) : shops.length === 0 ? (
                  <p className="discovery-shop-strip__empty">Shops will appear here as they join Apniko.</p>
                ) : (
                  <div className="discovery-shop-strip__items">
                    {shops.slice(0, 6).map((shop) => (
                      <button type="button" className="discovery-shop-chip" key={shop.id} onClick={() => openShop(shop)}>
                        <span className="discovery-shop-chip__initials">{shopInitials(shop.name)}</span>
                        <span><strong>{shop.name}</strong><small>{shop.address || 'Local store'}</small></span>
                        <span aria-hidden="true">›</span>
                      </button>
                    ))}
                  </div>
                )}
              </section>
            )}

            <section className="discovery-category-section" aria-label="Product categories">
              <div className="discovery-section-heading discovery-section-heading--compact">
                <div>
                  <p className="discovery-eyebrow">BROWSE BY CATEGORY</p>
                  <h2>What’s on your list?</h2>
                </div>
                {activeCategory && <button type="button" onClick={() => setActiveCategory('')}>Clear filter</button>}
              </div>
              <div className="discovery-category-row">
                <button
                  type="button"
                  className={activeCategory === '' ? 'is-active' : ''}
                  aria-pressed={activeCategory === ''}
                  onClick={() => setActiveCategory('')}
                >All products</button>
                {categories.map((category) => (
                  <button
                    key={category}
                    type="button"
                    className={activeCategory === category ? 'is-active' : ''}
                    aria-pressed={activeCategory === category}
                    onClick={() => setActiveCategory(category)}
                  >{category}</button>
                ))}
              </div>
            </section>

            <section className="discovery-results-section" aria-labelledby="product-results-title">
              <div className="discovery-section-heading">
                <div>
                  <p className="discovery-eyebrow">{browseShop ? 'INSIDE THIS SHOP' : 'FROM AVAILABLE SHOPS'}</p>
                  <h2 id="product-results-title">{resultHeading}</h2>
                  <p className="discovery-results-subtitle">
                    {browseShop
                      ? 'Search and choose products from this shop.'
                      : 'Each product shows the shop selling it, its price and current stock.'}
                  </p>
                </div>
                <label className="discovery-sort">
                  <span>Sort</span>
                  <select value={sortBy} onChange={(event) => setSortBy(event.target.value as ProductSort)}>
                    <option value="name">Name</option>
                    <option value="price">Price: low to high</option>
                  </select>
                </label>
              </div>

              {productError && <div className="customer-state customer-state--error" role="alert">{productError}</div>}
              {loadingProducts ? (
                <div className="discovery-product-grid" aria-label="Loading products">
                  {Array.from({ length: 6 }, (_, index) => <div className="discovery-product-skeleton" key={index} />)}
                </div>
              ) : products.length === 0 ? (
                <div className="customer-state discovery-empty">
                  <strong>{appliedSearch ? 'No products found for this search.' : 'No products are listed here yet.'}</strong>
                  <p>Try another search or category, or explore a different shop.</p>
                  {browseShop && <button type="button" onClick={showAllProducts}>Browse all shops</button>}
                </div>
              ) : (
                <div className="discovery-product-grid">
                  {products.map((product) => (
                    <article className="discovery-product-card" key={product.id}>
                      <div className="discovery-product-card__image-wrap">
                        <ProductImage
                          imageUrl={product.imageUrl}
                          productName={product.productName}
                          category={product.category}
                        />
                        <span className={product.available && product.stockQuantity > 0
                          ? 'discovery-stock-tag'
                          : 'discovery-stock-tag discovery-stock-tag--out'}>
                          {product.available && product.stockQuantity > 0 ? 'In stock' : 'Unavailable'}
                        </span>
                      </div>
                      <div className="discovery-product-card__body">
                        <span className="discovery-product-card__category">{product.category || 'Everyday essentials'}</span>
                        <h3>{product.productName}</h3>
                        <button
                          type="button"
                          className="discovery-product-card__shop"
                          onClick={() => {
                            const shop = shops.find((entry) => entry.id === product.shopId)
                            if (shop) openShop(shop)
                          }}
                        >{product.shopName} <span aria-hidden="true">↗</span></button>
                        <div className="discovery-product-card__bottom">
                          <div>
                            <strong>{formatRupees(product.price)}</strong>
                            <small>{product.stockQuantity > 0 ? `${product.stockQuantity} available` : 'Out of stock'}</small>
                          </div>
                          <button
                            type="button"
                            disabled={!product.available || product.stockQuantity < 1 || busyProductId === product.productId}
                            onClick={() => void addProduct(product)}
                          >{busyProductId === product.productId ? 'Adding…' : 'Add +'}</button>
                        </div>
                      </div>
                    </article>
                  ))}
                </div>
              )}

              {hasMore && !loadingProducts && (
                <button
                  type="button"
                  className="discovery-load-more"
                  disabled={loadingMore}
                  onClick={() => void loadProducts(productPage + 1, true)}
                >{loadingMore ? 'Loading more…' : 'Show more products ↓'}</button>
              )}
            </section>
          </>
        )}

        <section className="discovery-basket-section" id="customer-basket" aria-labelledby="basket-title">
          <div className="discovery-basket-heading">
            <div>
              <p className="discovery-eyebrow">YOUR SHOPPING BASKET</p>
              <h2 id="basket-title">Ready when you are.</h2>
            </div>
            <span className="discovery-basket-count">{basketCount} {basketCount === 1 ? 'item' : 'items'}</span>
          </div>
          {basketError && <p className="basket-message basket-message--error" role="alert">{basketError}</p>}
          {notice && <p className="basket-message basket-message--success" role="status">{notice}</p>}
          {loadingBasket ? (
            <div className="discovery-basket-empty" role="status">Loading your shop basket…</div>
          ) : basket?.items.length ? (
            <>
              <p className="discovery-basket-shop">Order from <strong>{cartShop?.name ?? basket.shopName}</strong></p>
              <div className="discovery-basket-items">
                {basket.items.map((item) => (
                  <div className="discovery-basket-item" key={item.productId}>
                    <div>
                      <strong>{item.productName}</strong>
                      <span>{formatRupees(item.price)} each</span>
                    </div>
                    <div className="discovery-basket-item__controls">
                      <button type="button" aria-label={`Decrease ${item.productName}`} onClick={() => void changeQuantity(item.productId, item.quantity - 1)}>−</button>
                      <span>{item.quantity}</span>
                      <button type="button" aria-label={`Increase ${item.productName}`} disabled={item.quantity >= item.availableStock} onClick={() => void changeQuantity(item.productId, item.quantity + 1)}>+</button>
                      <button type="button" className="discovery-basket-remove" onClick={() => void removeProduct(item.productId)}>Remove</button>
                    </div>
                    <strong className="discovery-basket-item__total">{formatRupees(item.lineTotal)}</strong>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <div className="discovery-basket-empty">
              <strong>Your basket is empty.</strong>
              <p>Add products from a shop to start an order. Each shop keeps its own basket.</p>
            </div>
          )}
          <div className="discovery-basket-footer">
            <div><span>Basket total</span><strong>{formatRupees(basket?.totalAmount ?? 0)}</strong></div>
            <button
              type="button"
              disabled={loadingBasket || cartShopId === null || !basket?.items.length}
              onClick={() => setShowCheckout(true)}
            >Continue to checkout <span aria-hidden="true">→</span></button>
          </div>
          <p className="discovery-basket-note">Your order is placed with one shop at a time. You can switch shops and their baskets remain separate.</p>
        </section>
      </main>

      <footer className="customer-footer">
        <div className="customer-wrap customer-footer__inner">
          <BrandMark />
          <span>Everyday shopping, closer to home.</span>
          <span>APNIKO / CUSTOMER</span>
        </div>
      </footer>

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
