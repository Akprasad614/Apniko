import { describe, expect, it } from 'vitest'
import {
  buildMarketplaceSearchPath,
  filterMarketplaceShops,
  formatRupees,
  getFallbackProductImage,
  isSafeProductImageUrl,
  resolveProductImageUrl,
} from './marketplace'
import type { MarketplaceShop } from './marketplace'

const shops: MarketplaceShop[] = [
  { id: 1, name: 'Fresh Basket', ownerName: 'Asha', phone: '', address: 'HSR Layout', active: true },
  { id: 2, name: 'Daily Needs', ownerName: 'Ravi', phone: '', address: 'BTM Layout', active: true },
  { id: 3, name: 'Green Corner', ownerName: 'Meera', phone: '', address: 'Indiranagar', active: true },
]

describe('customer marketplace helpers', () => {
  it('builds a search URL for all shops when no shop is selected', () => {
    const path = buildMarketplaceSearchPath({
      query: '  basmati rice  ',
      category: 'Staples',
      sortBy: 'price',
      direction: 'desc',
      page: 2,
      size: 24,
    })

    const url = new URL(path, 'http://localhost')
    expect(url.pathname).toBe('/api/customer/marketplace/products/search')
    expect(url.searchParams.get('q')).toBe('basmati rice')
    expect(url.searchParams.get('category')).toBe('Staples')
    expect(url.searchParams.has('shopId')).toBe(false)
    expect(url.searchParams.get('sortBy')).toBe('price')
    expect(url.searchParams.get('direction')).toBe('desc')
    expect(url.searchParams.get('page')).toBe('2')
  })

  it('scopes a product search to a chosen shop when shopId is supplied', () => {
    const url = new URL(buildMarketplaceSearchPath({
      query: 'milk',
      shopId: 12,
    }), 'http://localhost')

    expect(url.searchParams.get('q')).toBe('milk')
    expect(url.searchParams.get('shopId')).toBe('12')
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

  it('filters shops by name, owner and address without changing the source list', () => {
    expect(filterMarketplaceShops(shops, 'fresh').map((shop) => shop.id)).toEqual([1])
    expect(filterMarketplaceShops(shops, 'ravi').map((shop) => shop.id)).toEqual([2])
    expect(filterMarketplaceShops(shops, 'indiranagar').map((shop) => shop.id)).toEqual([3])
    expect(filterMarketplaceShops(shops, ' ').map((shop) => shop.id)).toEqual([1, 2, 3])
    expect(shops).toHaveLength(3)
  })

  it('uses category-appropriate fallback product photography', () => {
    expect(getFallbackProductImage('Fresh Vegetables')).toContain('images.unsplash.com')
    expect(getFallbackProductImage('Dairy and Eggs')).toContain('images.unsplash.com')
    expect(getFallbackProductImage('Unknown category')).toContain('images.unsplash.com')
  })

  it('accepts only secure HTTPS product image URLs', () => {
    expect(isSafeProductImageUrl('https://cdn.example.com/products/milk.jpg')).toBe(true)
    expect(isSafeProductImageUrl('http://cdn.example.com/products/milk.jpg')).toBe(false)
    expect(isSafeProductImageUrl('javascript:alert(1)')).toBe(false)
    expect(isSafeProductImageUrl('not a url')).toBe(false)
  })

  it('prefers the stored product image URL over a category fallback', () => {
    expect(resolveProductImageUrl('https://cdn.example.com/rice.jpg', 'Staples'))
      .toBe('https://cdn.example.com/rice.jpg')
    expect(resolveProductImageUrl('http://unsafe.example.com/rice.jpg', 'Staples'))
      .toBe(getFallbackProductImage('Staples'))
  })

  it('formats prices in Indian rupees', () => {
    expect(formatRupees(1250)).toContain('1,250')
    expect(formatRupees(1250)).toContain('₹')
  })
})
