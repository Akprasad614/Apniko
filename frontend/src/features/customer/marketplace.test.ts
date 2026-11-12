import { describe, expect, it } from 'vitest'
import {
  buildMarketplaceSearchPath,
  filterMarketplaceProductsByArea,
  filterMarketplaceShops,
  formatRupees,
  getProductCategoryIcon,
} from './marketplace'
import type { MarketplaceInventoryItem, MarketplaceShop } from './marketplace'

const shops: MarketplaceShop[] = [
  { id: 1, name: 'Fresh Basket', ownerName: 'Anita', phone: '111', address: 'HSR Layout, Bengaluru', active: true },
  { id: 2, name: 'Daily Needs', ownerName: 'Ravi', phone: '222', address: 'BTM Layout, Bengaluru', active: true },
  { id: 3, name: 'Green Corner', ownerName: 'Meera', phone: '333', address: 'HSR Sector 2, Bengaluru', active: true },
]

const products: MarketplaceInventoryItem[] = [
  { id: 1, shopId: 1, shopName: 'Fresh Basket', shopAddress: 'HSR Layout, Bengaluru', productId: 101, productName: 'Tomato', category: 'Vegetables', imageUrl: null, price: 32, stockQuantity: 10, available: true },
  { id: 2, shopId: 2, shopName: 'Daily Needs', shopAddress: 'BTM Layout, Bengaluru', productId: 102, productName: 'Milk', category: 'Dairy', imageUrl: null, price: 28, stockQuantity: 8, available: true },
]

describe('customer marketplace helpers', () => {
  it('builds a product search URL scoped to a shop and filters', () => {
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

  it('supports global product search without a shop filter', () => {
    const url = new URL(buildMarketplaceSearchPath({ query: 'milk' }), 'http://localhost')
    expect(url.searchParams.get('q')).toBe('milk')
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

  it('filters shop search by name, owner and address', () => {
    expect(filterMarketplaceShops(shops, 'fresh', '')).toHaveLength(1)
    expect(filterMarketplaceShops(shops, 'ravi', '')[0]?.id).toBe(2)
    expect(filterMarketplaceShops(shops, '', 'HSR')).toHaveLength(2)
    expect(filterMarketplaceShops(shops, 'corner', 'HSR')[0]?.id).toBe(3)
  })

  it('filters products by the shop address text supplied by the backend', () => {
    expect(filterMarketplaceProductsByArea(products, 'hsr')).toEqual([products[0]])
    expect(filterMarketplaceProductsByArea(products, '')).toHaveLength(2)
  })

  it('returns consistent visual fallbacks for common product categories', () => {
    expect(getProductCategoryIcon('Vegetables')).toBe('🥬')
    expect(getProductCategoryIcon('Dairy & Eggs')).toBe('🥛')
    expect(getProductCategoryIcon('Household')).toBe('🧴')
    expect(getProductCategoryIcon('Unmapped')).toBe('🛒')
  })

  it('formats prices in Indian rupees', () => {
    expect(formatRupees(1250)).toContain('1,250')
    expect(formatRupees(1250)).toContain('₹')
  })
})
