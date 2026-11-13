import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../../auth/AuthProvider'
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

export function CustomerStorefront({ view = 'home' }: { view?: 'home' | 'categories' }) {
  const { session } = useAuth()
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

  const selectedShop = useMemo(
    () => shops.find((shop) => shop.id === selectedShopId) ?? null,
    [shops, selectedShopId],
  )

  useEffect(() => {
    if (selectedShopId !== null) {
      window.sessionStorage.setItem('apniko.customer.selectedShopId', String(selectedShopId))
    }
  }, [selectedShopId])

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
      <div className={`customer-wrap customer-main customer-main--${view}`}>
        {view === 'home' && (
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
        )}

        {view === 'categories' && (
          <section className="customer-screen-heading">
            <p className="customer-eyebrow">FIND YOUR EVERYDAY ESSENTIALS</p>
            <h1>Shop by <em>category.</em></h1>
            <p>Choose a category, then browse the products available from your selected neighbourhood shop.</p>
          </section>
        )}



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

            <div className="marketplace-content marketplace-content--full">
              <section className="product-area" aria-label="Products">
                {view === 'categories' && (
                  <div className="customer-category-grid" aria-label="Product categories">
                    {categories.map((category) => (
                      <button
                        className={`customer-category-card${activeCategory === category ? ' is-selected' : ''}`}
                        key={category}
                        type="button"
                        aria-pressed={activeCategory === category}
                        onClick={() => setActiveCategory(activeCategory === category ? '' : category)}
                      >
                        <span className="customer-category-card__symbol" aria-hidden="true">{category.slice(0, 1).toUpperCase()}</span>
                        <strong>{category}</strong>
                        <span>Explore products <span aria-hidden="true">→</span></span>
                      </button>
                    ))}
                    {categories.length === 0 && (
                      <div className="customer-state customer-state--empty">
                        <strong>Categories are being organised.</strong>
                        <p>Choose a shop to see the categories currently available in its catalogue.</p>
                      </div>
                    )}
                  </div>
                )}
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


            </div>
          </section>
        )}

        <div className="customer-basket-shortcut">
          <div>
            <strong>{basketCount} {basketCount === 1 ? 'item' : 'items'} in your cart</strong>
            {loadingBasket && <small role="status">Refreshing your cart…</small>}
            {basketError && <small className="customer-basket-shortcut__error" role="alert">{basketError}</small>}
            {notice && <small role="status">{notice}</small>}
          </div>
          <Link to="/customer/cart">Review your cart →</Link>
        </div>

      </div>
    </div>
  )
}
