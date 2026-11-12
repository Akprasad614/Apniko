import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useAuth } from '../../auth/AuthProvider'
import { BrandMark } from '../../components/BrandMark'
import { Button } from '../../components/Button'
import { CustomerAddressBook } from './CustomerAddressBook'
import { CustomerCheckout } from './CustomerCheckout'
import { CustomerOrders } from './CustomerOrders'
import { ApiError, apiRequest } from '../../lib/api'
import { ProductImage } from './ProductImage'
import {
  buildMarketplaceSearchPath,
  filterMarketplaceProductsByArea,
  filterMarketplaceShops,
  formatRupees,
  getProductCategoryIcon,
} from './marketplace'
import type {
  Basket,
  MarketplaceInventoryItem,
  MarketplacePage,
  MarketplaceShop,
} from './marketplace'
import './customer-storefront.css'

const PAGE_SIZE = 24
type SearchMode = 'products' | 'shops'
type ProductSort = 'name' | 'price' | 'shop'

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

  const [searchMode, setSearchMode] = useState<SearchMode>('products')
  const [searchInput, setSearchInput] = useState('')
  const [appliedSearch, setAppliedSearch] = useState('')
  const [areaInput, setAreaInput] = useState('')
  const [appliedArea, setAppliedArea] = useState('')
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
  const [showBasket, setShowBasket] = useState(false)

  const selectedShop = useMemo(
    () => shops.find((shop) => shop.id === selectedShopId) ?? null,
    [shops, selectedShopId],
  )

  const matchingShops = useMemo(
    () => filterMarketplaceShops(
      shops,
      searchMode === 'shops' ? appliedSearch : '',
      appliedArea,
    ),
    [shops, searchMode, appliedSearch, appliedArea],
  )

  const visibleProducts = useMemo(
    () => filterMarketplaceProductsByArea(products, appliedArea),
    [products, appliedArea],
  )

  const basketCount = basket?.items.reduce((total, item) => total + item.quantity, 0) ?? 0

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
      setLoadingBasket(false)
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

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setAppliedSearch(searchInput.trim())
    setProductPage(0)
  }

  function handleAreaSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setAppliedArea(areaInput.trim())
  }

  function chooseSearchMode(mode: SearchMode) {
    setSearchMode(mode)
    setAppliedSearch('')
    setSearchInput('')
    setActiveCategory('')
    if (mode === 'shops') setSelectedShopId(null)
  }

  function openShop(shop: MarketplaceShop) {
    setSelectedShopId(shop.id)
    setSearchMode('products')
    setSearchInput('')
    setAppliedSearch('')
    setActiveCategory('')
    setNotice('')
    setProductError('')
    setShowBasket(false)
  }

  function showAllProducts() {
    setSelectedShopId(null)
    setSearchMode('products')
    setSearchInput('')
    setAppliedSearch('')
    setActiveCategory('')
    setProductError('')
  }

  async function addProduct(product: MarketplaceInventoryItem) {
    if (busyProductId !== null) return
    setBusyProductId(product.productId)
    setBasketError('')
    setNotice('')
    setSelectedShopId(product.shopId)

    try {
      const updated = await apiRequest<Basket>(
        `/api/customer/cart/${product.shopId}/items`,
        {
          method: 'POST',
          body: JSON.stringify({ productId: product.productId, quantity: 1 }),
        },
        token,
      )
      setBasket(updated)
      setNotice(`${product.productName} added from ${product.shopName}.`)
      setShowBasket(true)
    } catch (error) {
      setBasketError(messageFor(error))
    } finally {
      setBusyProductId(null)
    }
  }

  async function changeQuantity(productId: number, quantity: number) {
    const cartShopId = basket?.shopId
    const item = basket?.items.find((entry) => entry.productId === productId)
    if (cartShopId === undefined || !item) return

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
    const cartShopId = basket?.shopId
    if (cartShopId === undefined) return
    setBasketError('')
    try {
      const updated = await apiRequest<Basket>(
        `/api/customer/cart/${cartShopId}/items/${productId}`,
        { method: 'DELETE' },
        token,
      )
      setBasket(updated)
      if (!updated.items.length) setShowBasket(false)
    } catch (error) {
      setBasketError(messageFor(error))
    }
  }

  return (
    <div className="customer-storefront">
      <a className="skip-link" href="#customer-main">Skip to marketplace</a>

      <header className="customer-header">
        <div className="customer-wrap customer-header__inner">
          <BrandMark />
          <div className="customer-header__tools">
            <form className="area-search" onSubmit={handleAreaSearch} role="search" aria-label="Filter shops by area">
              <label className="visually-hidden" htmlFor="customer-area">Area or PIN code</label>
              <span aria-hidden="true">⌖</span>
              <input
                id="customer-area"
                value={areaInput}
                onChange={(event) => setAreaInput(event.target.value)}
                placeholder="Area or PIN code"
              />
              <button type="submit">Set area</button>
            </form>
            <button className="header-action" type="button" onClick={() => { setShowAddressBook(true); setShowOrders(false) }}>Addresses</button>
            <button className="header-action" type="button" onClick={() => { setShowOrders(true); setShowAddressBook(false) }}>My orders</button>
            <button
              className="header-action header-action--basket"
              type="button"
              onClick={() => setShowBasket(true)}
              aria-label={`Open basket with ${basketCount} items`}
            >
              Basket <strong>{basketCount}</strong>
            </button>
            <Button tone="quiet" onClick={signOut}>Sign out</Button>
          </div>
        </div>
      </header>

      <main id="customer-main" className="customer-wrap customer-main">
        <section className="customer-hero" aria-labelledby="customer-title">
          <div className="customer-hero__copy">
            <p className="customer-eyebrow"><span /> YOUR NEIGHBOURHOOD MARKET</p>
            <h1 id="customer-title">Your everyday<br /><em>shopping, closer.</em></h1>
            <p>Find the products you need from the independent shops around you. Compare what's available, choose your shop and order in a few taps.</p>
            <div className="customer-hero__meta">
              <span>PRODUCTS FROM LOCAL SHOPS</span>
              <span>·</span>
              <span>ONE SHOP PER ORDER</span>
            </div>
          </div>
          <div className="customer-hero__art" aria-hidden="true">
            <span className="customer-hero__art-item customer-hero__art-item--one">🥬</span>
            <span className="customer-hero__art-item customer-hero__art-item--two">🍎</span>
            <span className="customer-hero__art-item customer-hero__art-item--three">🥛</span>
            <span className="customer-hero__art-item customer-hero__art-item--four">🍞</span>
            <span className="customer-hero__art-note">LOCAL FINDS<br />EVERY DAY</span>
          </div>
        </section>

        <section className="discovery-panel" aria-label="Search Apniko">
          <form className="discovery-search" onSubmit={handleSearch} role="search">
            <label className="visually-hidden" htmlFor="customer-search">Search products or shops</label>
            <span className="discovery-search__icon" aria-hidden="true">⌕</span>
            <input
              id="customer-search"
              name="q"
              type="search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder={searchMode === 'products'
                ? selectedShop ? `Search products in ${selectedShop.name}…` : 'Search for products across local shops…'
                : 'Search for a shop by name or area…'}
            />
            <button type="submit">Search <span aria-hidden="true">→</span></button>
          </form>
          <div className="discovery-panel__bottom">
            <div className="search-mode" role="group" aria-label="Search type">
              <button type="button" className={searchMode === 'products' ? 'is-active' : ''} aria-pressed={searchMode === 'products'} onClick={() => chooseSearchMode('products')}>Products</button>
              <button type="button" className={searchMode === 'shops' ? 'is-active' : ''} aria-pressed={searchMode === 'shops'} onClick={() => chooseSearchMode('shops')}>Shops</button>
            </div>
            <span className="discovery-panel__hint">
              {appliedArea ? `Matching addresses: ${appliedArea}` : 'Browse active shops and products available on Apniko'}
              {appliedArea && <button type="button" onClick={() => { setAreaInput(''); setAppliedArea('') }}>Clear area</button>}
            </span>
          </div>
        </section>

        {searchMode === 'products' && (
          <section className="category-section" aria-label="Shop by category">
            <div className="section-heading">
              <div>
                <p className="customer-eyebrow">BROWSE BY CATEGORY</p>
                <h2>What are you looking for?</h2>
              </div>
              <span className="section-heading__caption">Choose a category to narrow the shelf</span>
            </div>
            <div className="category-grid">
              <button type="button" className={activeCategory === '' ? 'category-tile is-active' : 'category-tile'} onClick={() => setActiveCategory('')} aria-pressed={activeCategory === ''}>
                <span>🛍️</span><strong>All products</strong>
              </button>
              {categories.map((category) => (
                <button type="button" key={category} className={activeCategory === category ? 'category-tile is-active' : 'category-tile'} onClick={() => setActiveCategory(category)} aria-pressed={activeCategory === category}>
                  <span aria-hidden="true">{getProductCategoryIcon(category)}</span><strong>{category}</strong>
                </button>
              ))}
            </div>
          </section>
        )}

        {searchMode === 'shops' ? (
          <section className="results-section" aria-labelledby="shop-results-title">
            <div className="section-heading">
              <div>
                <p className="customer-eyebrow">SHOP DISCOVERY</p>
                <h2 id="shop-results-title">{appliedSearch ? `Shops matching “${appliedSearch}”` : 'Choose a local shop'}</h2>
              </div>
              <span className="section-heading__caption">{loadingShops ? 'Loading shops…' : `${matchingShops.length} available`}</span>
            </div>
            {loadingShops ? (
              <div className="customer-state" role="status">Finding active shops on Apniko…</div>
            ) : pageError ? (
              <div className="customer-state customer-state--error" role="alert">{pageError}</div>
            ) : matchingShops.length === 0 ? (
              <div className="customer-state">
                <strong>No matching shops found.</strong>
                <p>Try another shop name or area, or clear the area filter.</p>
              </div>
            ) : (
              <div className="shop-grid">
                {matchingShops.map((shop) => (
                  <article className="shop-card" key={shop.id}>
                    <div className="shop-card__visual" aria-hidden="true">🏪</div>
                    <div className="shop-card__body">
                      <span className="shop-card__status">ACTIVE ON APNIKO</span>
                      <h3>{shop.name}</h3>
                      <p>{shop.address || 'Address not listed'}</p>
                      {shop.ownerName && <span className="shop-card__owner">Local shop · {shop.ownerName}</span>}
                      <button type="button" onClick={() => openShop(shop)}>Browse this shop <span aria-hidden="true">→</span></button>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>
        ) : (
          <>
            {selectedShop && (
              <section className="selected-shop" aria-label="Selected shop">
                <div className="selected-shop__icon" aria-hidden="true">🏪</div>
                <div className="selected-shop__details">
                  <span>SHOP CATALOGUE</span>
                  <strong>{selectedShop.name}</strong>
                  <small>{selectedShop.address || 'Address not listed'}</small>
                </div>
                <button type="button" onClick={showAllProducts}>Browse all shops <span aria-hidden="true">↗</span></button>
              </section>
            )}

            <section className="results-section" aria-labelledby="product-results-title">
              <div className="section-heading">
                <div>
                  <p className="customer-eyebrow">{selectedShop ? 'FROM THIS SHOP' : 'AVAILABLE ACROSS LOCAL SHOPS'}</p>
                  <h2 id="product-results-title">
                    {appliedSearch ? `Results for “${appliedSearch}”` : activeCategory || 'Products for your everyday'}
                  </h2>
                </div>
                <label className="product-sort">
                  <span>Sort</span>
                  <select value={sortBy} onChange={(event) => setSortBy(event.target.value as ProductSort)}>
                    <option value="name">Name</option>
                    <option value="price">Price: low to high</option>
                    <option value="shop">Shop name</option>
                  </select>
                </label>
              </div>

              {pageError && <div className="customer-state customer-state--error" role="alert">{pageError}</div>}
              {productError && <div className="customer-state customer-state--error" role="alert">{productError}</div>}
              {loadingProducts ? (
                <div className="customer-state" role="status">Finding products and checking shop availability…</div>
              ) : visibleProducts.length === 0 ? (
                <div className="customer-state">
                  <strong>{products.length ? 'No products match that area.' : 'No products are listed yet.'}</strong>
                  <p>{products.length
                    ? 'Try a different locality or PIN code. Area matching uses the shop address text currently on file.'
                    : 'Try another search or category, or check again when local shops add their inventory.'}</p>
                </div>
              ) : (
                <div className="product-grid">
                  {visibleProducts.map((product) => (
                    <article className="product-card" key={product.id}>
                      <ProductImage name={product.productName} category={product.category} imageUrl={product.imageUrl} />
                      <div className="product-card__content">
                        <span className="product-card__category">{product.category || 'Everyday essentials'}</span>
                        <h3>{product.productName}</h3>
                        <div className="product-card__shop">
                          <span>{product.shopName}</span>
                          <button type="button" onClick={() => {
                            const shop = shops.find((entry) => entry.id === product.shopId)
                            if (shop) openShop(shop)
                          }}>Shop page</button>
                        </div>
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
                  {loadingMore ? 'Loading…' : 'Show more products'}
                </button>
              )}
            </section>

            {!selectedShop && (
              <section className="nearby-shops-section" aria-labelledby="nearby-shops-title">
                <div className="section-heading">
                  <div>
                    <p className="customer-eyebrow">LOCAL STORES</p>
                    <h2 id="nearby-shops-title">Shops on your shelf</h2>
                  </div>
                  <button type="button" className="text-action" onClick={() => chooseSearchMode('shops')}>View all shops ↗</button>
                </div>
                {loadingShops ? (
                  <div className="customer-state" role="status">Loading shops…</div>
                ) : matchingShops.length === 0 ? (
                  <div className="customer-state">No active shops match the selected area yet.</div>
                ) : (
                  <div className="shop-strip">
                    {matchingShops.slice(0, 4).map((shop) => (
                      <button className="shop-strip__item" type="button" key={shop.id} onClick={() => openShop(shop)}>
                        <span aria-hidden="true">🏪</span>
                        <span><strong>{shop.name}</strong><small>{shop.address || 'Address not listed'}</small></span>
                        <span aria-hidden="true">→</span>
                      </button>
                    ))}
                  </div>
                )}
              </section>
            )}
          </>
        )}
      </main>

      <footer className="customer-footer">
        <div className="customer-wrap customer-footer__inner">
          <BrandMark />
          <span>Good things happen close to home.</span>
          <span>APNIKO / CUSTOMER</span>
        </div>
      </footer>

      {showBasket && basket && (
        <div className="basket-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setShowBasket(false) }}>
          <section className="basket-drawer" role="dialog" aria-modal="true" aria-labelledby="basket-title">
            <div className="basket-drawer__heading">
              <div>
                <p className="customer-eyebrow">YOUR BASKET</p>
                <h2 id="basket-title">{basket.shopName}</h2>
                <span>{basketCount} item{basketCount === 1 ? '' : 's'}</span>
              </div>
              <button type="button" className="basket-drawer__close" onClick={() => setShowBasket(false)} aria-label="Close basket">×</button>
            </div>
            {basketError && <p className="basket-drawer__error" role="alert">{basketError}</p>}
            {notice && <p className="basket-drawer__notice" role="status">{notice}</p>}
            {loadingBasket ? <p role="status">Refreshing your basket…</p> : basket.items.length ? (
              <div className="basket-drawer__items">
                {basket.items.map((item) => (
                  <article className="basket-line" key={item.productId}>
                    <ProductImage name={item.productName} category={item.category} compact />
                    <div className="basket-line__details">
                      <strong>{item.productName}</strong>
                      <span>{formatRupees(item.price)} each</span>
                      <small>{formatRupees(item.lineTotal)}</small>
                    </div>
                    <div className="basket-line__quantity">
                      <button type="button" aria-label={`Decrease ${item.productName}`} onClick={() => void changeQuantity(item.productId, item.quantity - 1)}>−</button>
                      <span>{item.quantity}</span>
                      <button type="button" aria-label={`Increase ${item.productName}`} disabled={item.quantity >= item.availableStock} onClick={() => void changeQuantity(item.productId, item.quantity + 1)}>+</button>
                      <button type="button" className="basket-line__remove" onClick={() => void removeProduct(item.productId)}>Remove</button>
                    </div>
                  </article>
                ))}
              </div>
            ) : (
              <div className="basket-drawer__empty">
                <span aria-hidden="true">🛒</span>
                <strong>Your basket is empty.</strong>
                <p>Choose products from this shop to get started.</p>
              </div>
            )}
            <div className="basket-drawer__total">
              <span>Subtotal</span><strong>{formatRupees(basket.totalAmount)}</strong>
            </div>
            <Button
              className="basket-drawer__checkout"
              disabled={!basket.items.length || loadingBasket}
              onClick={() => setShowCheckout(true)}
            >Continue to checkout <span aria-hidden="true">→</span></Button>
            <p className="basket-drawer__note">Each order is placed with one shop. Your other shop baskets remain separate.</p>
          </section>
        </div>
      )}

      {showCheckout && basket && (
        <CustomerCheckout
          token={token}
          shopId={basket.shopId}
          basket={basket}
          onClose={() => setShowCheckout(false)}
          onManageAddresses={() => {
            setShowCheckout(false)
            setShowAddressBook(true)
          }}
          onOrderPlaced={(order) => {
            setBasket((current) => current && current.shopId === basket.shopId
              ? { ...current, items: [], totalAmount: 0 }
              : current)
            setBasketError('')
            setNotice(`Order #${order.id} placed successfully.`)
            setShowCheckout(false)
            setShowBasket(false)
          }}
        />
      )}
      {showAddressBook && <CustomerAddressBook token={token} onClose={() => setShowAddressBook(false)} />}
      {showOrders && session && <CustomerOrders token={token} customerId={session.userId} onClose={() => setShowOrders(false)} />}
    </div>
  )
}
