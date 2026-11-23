import { describe, expect, it } from 'vitest'
import { buildMarketplaceSearchPath, formatRupees, searchMarketplaceShops } from './marketplace'

describe('customer marketplace helpers', () => {
  it('builds a search URL from the selected shop and filters', () => {
    const path = buildMarketplaceSearchPath({
      query: '  basmati rice  ',
      category: 'Staples',
      shopId: 12,
      sortBy: 'price',
      direction: 'desc',
      page: 2,
      size: 24,
    })

    const url = new URL(path, 'http://localhost')
    expect(url.pathname).toBe('/api/customer/marketplace/products/search')
    expect(url.searchParams.get('q')).toBe('basmati rice')
    expect(url.searchParams.get('category')).toBe('Staples')
    expect(url.searchParams.get('shopId')).toBe('12')
    expect(url.searchParams.get('sortBy')).toBe('price')
    expect(url.searchParams.get('direction')).toBe('desc')
    expect(url.searchParams.get('page')).toBe('2')
  })

  it('omits empty filters and applies safe default paging', () => {
    const url = new URL(buildMarketplaceSearchPath({ query: '  ', category: '' }), 'http://localhost')
    expect(url.searchParams.has('q')).toBe(false)
    expect(url.searchParams.has('category')).toBe(false)
    expect(url.searchParams.get('page')).toBe('0')
    expect(url.searchParams.get('size')).toBe('24')
    expect(url.searchParams.get('sortBy')).toBe('name')
    expect(url.searchParams.get('direction')).toBe('asc')
  })

  it('includes the selected shop price range in the search request', () => {
    const url = new URL(buildMarketplaceSearchPath({
      shopId: 21,
      minPrice: 25,
      maxPrice: 150,
    }), 'http://localhost')

    expect(url.searchParams.get('shopId')).toBe('21')
    expect(url.searchParams.get('minPrice')).toBe('25')
    expect(url.searchParams.get('maxPrice')).toBe('150')
  })

  it('formats prices in Indian rupees', () => {
    expect(formatRupees(1250)).toContain('1,250')
    expect(formatRupees(1250)).toContain('₹')
  })
})


describe('neighbourhood shop search', () => {
  const shops = [
    { id: 1, name: 'Green Basket', ownerName: 'Anil', address: 'Indiranagar, Bengaluru', active: true },
    { id: 2, name: 'Corner Stores', ownerName: 'Meera', address: 'Whitefield, Bengaluru', active: true },
  ]

  it('matches shop name, owner and address without changing the source list', () => {
    expect(searchMarketplaceShops(shops, 'green').map((shop) => shop.id)).toEqual([1])
    expect(searchMarketplaceShops(shops, 'MEERA').map((shop) => shop.id)).toEqual([2])
    expect(searchMarketplaceShops(shops, 'whitefield').map((shop) => shop.id)).toEqual([2])
    expect(shops).toHaveLength(2)
  })

  it('returns all shops for an empty search and none for an unmatched query', () => {
    expect(searchMarketplaceShops(shops, '  ')).toHaveLength(2)
    expect(searchMarketplaceShops(shops, 'no such neighbourhood')).toEqual([])
  })
})
