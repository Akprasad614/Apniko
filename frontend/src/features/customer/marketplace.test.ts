import { describe, expect, it } from 'vitest'
import {
  buildMarketplaceSearchPath,
  filterMarketplaceShops,
  formatRupees,
  getMarketplaceProductImage,
} from './marketplace'

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

  it('builds a global product search when no shop is selected', () => {
    const url = new URL(buildMarketplaceSearchPath({ query: 'paneer', shopId: null }), 'http://localhost')
    expect(url.searchParams.get('q')).toBe('paneer')
    expect(url.searchParams.has('shopId')).toBe(false)
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

  it('filters shops by name, owner or address without matching case', () => {
    const shops = [
      { id: 1, name: 'Fresh Basket', ownerName: 'Asha', phone: '', address: 'HSR Layout', active: true },
      { id: 2, name: 'Daily Needs', ownerName: 'Ravi', phone: '', address: 'Koramangala', active: true },
    ]

    expect(filterMarketplaceShops(shops, '  HSR ')).toEqual([shops[0]])
    expect(filterMarketplaceShops(shops, 'ravi')).toEqual([shops[1]])
    expect(filterMarketplaceShops(shops, '')).toEqual(shops)
  })

  it('prefers a supplied secure catalogue image URL', () => {
    expect(getMarketplaceProductImage({
      productName: 'Basmati rice',
      category: 'Staples',
      imageUrl: 'https://cdn.example.com/rice.webp',
    })).toBe('https://cdn.example.com/rice.webp')
  })

  it('ignores an insecure image URL and chooses a matching category image', () => {
    expect(getMarketplaceProductImage({
      productName: 'Basmati rice',
      category: 'Staples',
      imageUrl: 'http://example.com/rice.jpg',
    })).toContain('images.unsplash.com')
  })

  it('formats prices in Indian rupees', () => {
    expect(formatRupees(1250)).toContain('1,250')
    expect(formatRupees(1250)).toContain('₹')
  })
})
