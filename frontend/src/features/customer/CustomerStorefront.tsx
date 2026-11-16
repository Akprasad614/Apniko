import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../../auth/AuthProvider'
import { ApiError, apiRequest } from '../../lib/api'
import { ApprovedProductImage } from './ApprovedProductImage'
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
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc')
  const [minPriceInput, setMinPriceInput] = useState('')
  const [maxPriceInput, setMaxPriceInput] = useState('')
  const [appliedMinPrice, setAppliedMinPrice] = useState<number | undefined>(undefined)
  const [appliedMaxPrice, setAppliedMaxPrice] = useState<number | undefined>(undefined)
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
          const savedId = Number(window.sessionStorage.getItem('apniko.customer.selectedShopId'))
          const preferredId = current ?? (Number.isSafeInteger(savedId) && savedId > 0 ? savedId : null)
          if (preferredId !== null && result.some((shop) => shop.id === preferredId)) return preferredId
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
        direction: sortDirection,
        minPrice: appliedMinPrice,
        maxPrice: appliedMaxPrice,
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
  }, [activeCategory, appliedMaxPrice, appliedMinPrice, appliedSearch, selectedShopId, sortBy, sortDirection, token])

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

  function applyPriceFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const min = minPriceInput.trim() ? Number(minPriceInput) : undefined
    const max = maxPriceInput.trim() ? Number(maxPriceInput) : undefined
    if ((min !== undefined && (!Number.isFinite(min) || min < 0))
      || (max !== undefined && (!Number.isFinite(max) || max < 0))
      || (min !== undefined && max !== undefined && min > max)) {
      setProductError('Enter a valid price range. The minimum cannot exceed the maximum.')
      return
    }
    setProductError('')
    setAppliedMinPrice(min)
    setAppliedMaxPrice(max)
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

  const basketCount = basket?.items.reduce((total, item) => total + item.quantity, 0) ?? 0

  return (
    <div className="customer-storefront">
      <div className={`customer-wrap customer-main customer-main--${view}`}>
        {view === 'home' && (
        <section className="customer-intro" aria-labelledby="customer-title">
          <div>
            <p className="customer-eyebrow"><span /> YOUR NEIGHBOURHOOD GROCERY STORE</p>
            <h1 id="customer-title">Everyday groceries,<br /><em>closer to home.</em></h1>
            <p className="customer-intro__copy">
              Browse genuine products, check your local shop's prices and availability, and build your basket in a few taps.
            </p>
          </div>
          <div className="customer-intro__aside">
            <span className="customer-intro__aside-mark" aria-hidden="true">✳</span>
            <p>One familiar catalogue. Stock and prices from your selected shop.</p>
            <span>LOCAL SHOPS · REAL AVAILABILITY</span>
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
              <p className="customer-eyebrow">SHOP LOCAL</p>
              <h2 id="shop-section-title">Choose your store</h2>
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
                <p className="customer-eyebrow">SHOPPING FROM</p>
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
                <select value={sortBy + ':' + sortDirection} onChange={(event) => {
                  const [nextSort, nextDirection] = event.target.value.split(':')
                  setSortBy(nextSort as 'name' | 'price')
                  setSortDirection(nextDirection as 'asc' | 'desc')
                }}>
                  <option value="name:asc">Name A–Z</option>
                  <option value="price:asc">Price: low to high</option>
                  <option value="price:desc">Price: high to low</option>
                </select>
              </label>
              <form className="market-price-filter" onSubmit={applyPriceFilters}>
                <label>
                  <span>Min ₹</span>
                  <input type="number" min="0" step="1" value={minPriceInput}
                    onChange={(event) => setMinPriceInput(event.target.value)} placeholder="Any" />
                </label>
                <label>
                  <span>Max ₹</span>
                  <input type="number" min="0" step="1" value={maxPriceInput}
                    onChange={(event) => setMaxPriceInput(event.target.value)} placeholder="Any" />
                </label>
                <button type="submit">Apply</button>
                {(appliedMinPrice !== undefined || appliedMaxPrice !== undefined) && (
                  <button type="button" className="market-price-filter__clear"
                    onClick={() => {
                      setMinPriceInput('')
                      setMaxPriceInput('')
                      setAppliedMinPrice(undefined)
                      setAppliedMaxPrice(undefined)
                    }}>Clear</button>
                )}
              </form>
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
                        <span className="customer-category-card__symbol" aria-hidden="true">▦</span>
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
                  <div className="product-list catalogue-product-grid">
                    {products.map((product) => (
                      <article className="catalogue-product-card" key={product.id}>
                        <div className="catalogue-product-card__media">
                          <ApprovedProductImage
                            variant="card"
                            imageUrl={product.imageUrl}
                            productName={product.productName}
                            sourceName={product.imageSourceName}
                            sourceUrl={product.imageSourceUrl}
                            license={product.imageLicense}
                            attribution={product.imageAttribution}
                          />
                          {(!product.available || product.stockQuantity < 1) && (
                            <span className="catalogue-product-card__unavailable">Out of stock</span>
                          )}
                        </div>
                        <div className="catalogue-product-card__body">
                          <p className="catalogue-product-card__brand">{product.brand || product.category || 'Grocery essential'}</p>
                          <h4>{product.productName}</h4>
                          <p className="catalogue-product-card__pack">
                            {[product.packSize, product.subcategory].filter(Boolean).join(' · ') || 'Standard pack'}
                          </p>
                          {product.stockQuantity > 0 && product.stockQuantity <= 5 && product.available && (
                            <p className="catalogue-product-card__stock">Only {product.stockQuantity} left at this shop</p>
                          )}
                          <div className="catalogue-product-card__purchase">
                            <div className="catalogue-product-card__price">
                              <strong>{formatRupees(product.price)}</strong>
                              {product.mrp !== null && product.mrp !== undefined && product.mrp > product.price && (
                                <del>{formatRupees(product.mrp)}</del>
                              )}
                            </div>
                            <button
                              type="button"
                              disabled={!product.available || product.stockQuantity < 1 || busyProductId === product.productId}
                              onClick={() => void addProduct(product)}
                            >
                              {busyProductId === product.productId ? 'Adding…' : 'ADD'}
                            </button>
                          </div>
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
