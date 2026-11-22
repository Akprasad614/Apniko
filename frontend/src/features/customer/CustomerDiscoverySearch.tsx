import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, apiRequest } from '../../lib/api'
import {
  buildMarketplaceSearchPath,
  formatRupees,
  searchMarketplaceShops,
} from './marketplace'
import type {
  MarketplaceInventoryItem,
  MarketplacePage,
  MarketplaceShop,
} from './marketplace'
import './customer-discovery-search.css'

interface CustomerDiscoverySearchProps {
  shops: MarketplaceShop[]
  token: string
  onShopSelect: (shopId: number) => void
  onProductSelect: (product: MarketplaceInventoryItem, query: string) => void
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error) return error.message
  return 'Search is temporarily unavailable. Please try again.'
}

export function CustomerDiscoverySearch({
  shops,
  token,
  onShopSelect,
  onProductSelect,
}: CustomerDiscoverySearchProps) {
  const [query, setQuery] = useState('')
  const [submittedQuery, setSubmittedQuery] = useState('')
  const [products, setProducts] = useState<MarketplaceInventoryItem[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const matchedShops = submittedQuery
    ? searchMarketplaceShops(shops, submittedQuery)
    : []

  async function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const normalizedQuery = query.trim()
    if (!normalizedQuery) {
      setSubmittedQuery('')
      setProducts([])
      setError('')
      return
    }

    setSubmittedQuery(normalizedQuery)
    setProducts([])
    setError('')
    setLoading(true)
    try {
      const result = await apiRequest<MarketplacePage<MarketplaceInventoryItem>>(
        buildMarketplaceSearchPath({ query: normalizedQuery, page: 0, size: 8 }),
        {},
        token,
      )
      setProducts(result.content)
    } catch (searchError) {
      setError(errorMessage(searchError))
    } finally {
      setLoading(false)
    }
  }

  function clearSearch() {
    setQuery('')
    setSubmittedQuery('')
    setProducts([])
    setError('')
  }

  return (
    <section className="customer-discovery" aria-label="Search neighbourhood shops and products">
      <form className="customer-discovery__form" role="search" onSubmit={(event) => void search(event)}>
        <label htmlFor="customer-discovery-query">What are you looking for?</label>
        <div className="customer-discovery__input-row">
          <span aria-hidden="true">⌕</span>
          <input
            id="customer-discovery-query"
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search a shop, rice, milk, vegetables…"
            maxLength={100}
          />
          <button type="submit" disabled={!query.trim() || loading}>
            {loading ? 'Searching…' : 'Search'}
          </button>
        </div>
        <p>Search shop names and products across active neighbourhood stores.</p>
      </form>

      {submittedQuery && (
        <div className="customer-discovery__results" aria-live="polite">
          <div className="customer-discovery__result-heading">
            <div>
              <span className="customer-eyebrow">SEARCH RESULTS</span>
              <h2>Matches for “{submittedQuery}”</h2>
            </div>
            <button type="button" onClick={clearSearch}>Clear search</button>
          </div>

          <section className="customer-discovery__group" aria-labelledby="discovery-shops-title">
            <h3 id="discovery-shops-title">Shops <span>{matchedShops.length}</span></h3>
            {matchedShops.length ? (
              <div className="customer-discovery__shop-list">
                {matchedShops.slice(0, 6).map((shop) => (
                  <button type="button" key={shop.id} onClick={() => onShopSelect(shop.id)}>
                    <span className="customer-discovery__shop-mark" aria-hidden="true">⌂</span>
                    <span><strong>{shop.name}</strong><small>{shop.address || 'Neighbourhood shop'}</small></span>
                    <b aria-hidden="true">→</b>
                  </button>
                ))}
              </div>
            ) : (
              <p className="customer-discovery__empty">No shop name or address matches this search.</p>
            )}
          </section>

          <section className="customer-discovery__group" aria-labelledby="discovery-products-title">
            <h3 id="discovery-products-title">Products <span>{loading ? '…' : products.length}</span></h3>
            {loading ? (
              <p className="customer-discovery__empty" role="status">Looking across active shop shelves…</p>
            ) : error ? (
              <p className="customer-discovery__error" role="alert">{error}</p>
            ) : products.length ? (
              <div className="customer-discovery__product-list">
                {products.map((product) => (
                  <button
                    type="button"
                    key={product.id}
                    onClick={() => onProductSelect(product, submittedQuery)}
                  >
                    <span><strong>{product.productName}</strong><small>{product.shopName} · {product.stockQuantity} in stock</small></span>
                    <b>{formatRupees(product.price)}</b>
                  </button>
                ))}
              </div>
            ) : (
              <p className="customer-discovery__empty">No active products match yet. Try another term or choose a shop to browse its current shelf.</p>
            )}
          </section>
        </div>
      )}
    </section>
  )
}
