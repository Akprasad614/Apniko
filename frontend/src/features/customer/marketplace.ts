export interface MarketplaceShop {
  id: number
  name: string
  ownerName: string
  phone: string
  address: string
  active: boolean
}

export interface MarketplaceInventoryItem {
  id: number
  shopId: number
  shopName: string
  productId: number
  productName: string
  category: string | null
  imageUrl?: string | null
  price: number
  stockQuantity: number
  available: boolean
}

export interface MarketplacePage<T> {
  content: T[]
  number: number
  totalElements: number
  totalPages: number
  last: boolean
}

export interface BasketItem {
  id: number
  productId: number
  productName: string
  category: string | null
  price: number
  quantity: number
  lineTotal: number
  availableStock: number
  available: boolean
}

export interface Basket {
  cartId: number | null
  shopId: number
  shopName: string
  items: BasketItem[]
  totalAmount: number
}

export interface MarketplaceSearchOptions {
  query?: string
  category?: string
  shopId?: number | null
  sortBy?: 'name' | 'price' | 'shop'
  direction?: 'asc' | 'desc'
  page?: number
  size?: number
}

const PRODUCT_IMAGE_FALLBACKS = {
  vegetables: 'https://images.unsplash.com/photo-1540420773420-3366772f4999?auto=format&fit=crop&w=640&h=640&q=82',
  fruits: 'https://images.unsplash.com/photo-1464965911861-746a04b4bca6?auto=format&fit=crop&w=640&h=640&q=82',
  grains: 'https://images.unsplash.com/photo-1586201375761-83865001e31c?auto=format&fit=crop&w=640&h=640&q=82',
  dairy: 'https://images.unsplash.com/photo-1550583724-b2692b85b150?auto=format&fit=crop&w=640&h=640&q=82',
  bakery: 'https://images.unsplash.com/photo-1509440159596-0249088772ff?auto=format&fit=crop&w=640&h=640&q=82',
  meat: 'https://images.unsplash.com/photo-1607623814075-e51df1bdc82f?auto=format&fit=crop&w=640&h=640&q=82',
  snacks: 'https://images.unsplash.com/photo-1621939514649-280e2aa33d2a?auto=format&fit=crop&w=640&h=640&q=82',
  beverages: 'https://images.unsplash.com/photo-1544145945-f90425340c7e?auto=format&fit=crop&w=640&h=640&q=82',
  sweets: 'https://images.unsplash.com/photo-1606313564200-e75d5e30476c?auto=format&fit=crop&w=640&h=640&q=82',
} as const

export function buildMarketplaceSearchPath(options: MarketplaceSearchOptions = {}): string {
  const params = new URLSearchParams()
  const query = options.query?.trim()
  const category = options.category?.trim()

  if (query) params.set('q', query)
  if (category) params.set('category', category)
  if (options.shopId !== undefined && options.shopId !== null) {
    params.set('shopId', String(options.shopId))
  }

  params.set('page', String(options.page ?? 0))
  params.set('size', String(options.size ?? 24))
  params.set('sortBy', options.sortBy ?? 'name')
  params.set('direction', options.direction ?? 'asc')

  return `/api/customer/marketplace/products/search?${params.toString()}`
}

export function filterMarketplaceShops(shops: MarketplaceShop[], query: string): MarketplaceShop[] {
  const normalized = query.trim().toLocaleLowerCase()
  if (!normalized) return shops
  return shops.filter((shop) =>
    [shop.name, shop.address, shop.ownerName]
      .filter(Boolean)
      .some((value) => value.toLocaleLowerCase().includes(normalized)),
  )
}

export function getMarketplaceProductImage(product: Pick<MarketplaceInventoryItem, 'productName' | 'category' | 'imageUrl'>): string {
  const supplied = product.imageUrl?.trim()
  if (supplied && /^https:\/\//i.test(supplied)) return supplied

  const identity = `${product.productName} ${product.category ?? ''}`.toLocaleLowerCase()
  if (/milk|curd|yogurt|paneer|cheese|butter|ghee|dairy/.test(identity)) return PRODUCT_IMAGE_FALLBACKS.dairy
  if (/apple|banana|mango|orange|grape|fruit|papaya|guava/.test(identity)) return PRODUCT_IMAGE_FALLBACKS.fruits
  if (/tomato|potato|onion|carrot|spinach|vegetable|cabbage|chilli|pepper/.test(identity)) return PRODUCT_IMAGE_FALLBACKS.vegetables
  if (/rice|atta|flour|grain|dal|lentil|pulse|wheat/.test(identity)) return PRODUCT_IMAGE_FALLBACKS.grains
  if (/bread|bun|pav|toast|bakery|cake|biscuit/.test(identity)) return PRODUCT_IMAGE_FALLBACKS.bakery
  if (/chicken|fish|meat|mutton|seafood|egg/.test(identity)) return PRODUCT_IMAGE_FALLBACKS.meat
  if (/chip|namkeen|snack|mixture|cracker/.test(identity)) return PRODUCT_IMAGE_FALLBACKS.snacks
  if (/juice|drink|water|soda|beverage|tea|coffee/.test(identity)) return PRODUCT_IMAGE_FALLBACKS.beverages
  if (/chocolate|sweet|ice cream|dessert|candy/.test(identity)) return PRODUCT_IMAGE_FALLBACKS.sweets

  const category = (product.category ?? '').toLocaleLowerCase()
  if (/fruit/.test(category)) return PRODUCT_IMAGE_FALLBACKS.fruits
  if (/vegetable|produce/.test(category)) return PRODUCT_IMAGE_FALLBACKS.vegetables
  if (/dairy/.test(category)) return PRODUCT_IMAGE_FALLBACKS.dairy
  if (/grain|staple|rice|pulse/.test(category)) return PRODUCT_IMAGE_FALLBACKS.grains
  if (/bakery|bread/.test(category)) return PRODUCT_IMAGE_FALLBACKS.bakery
  if (/meat|seafood|egg/.test(category)) return PRODUCT_IMAGE_FALLBACKS.meat
  if (/snack/.test(category)) return PRODUCT_IMAGE_FALLBACKS.snacks
  if (/drink|beverage/.test(category)) return PRODUCT_IMAGE_FALLBACKS.beverages
  if (/sweet|dessert/.test(category)) return PRODUCT_IMAGE_FALLBACKS.sweets

  return PRODUCT_IMAGE_FALLBACKS.vegetables
}

export function getCategoryIcon(category: string): string {
  const value = category.toLocaleLowerCase()
  if (/fruit/.test(value)) return '🍎'
  if (/vegetable|produce/.test(value)) return '🥬'
  if (/dairy|milk/.test(value)) return '🥛'
  if (/grain|staple|rice|pulse/.test(value)) return '🌾'
  if (/bakery|bread/.test(value)) return '🍞'
  if (/meat|fish|seafood|egg/.test(value)) return '🥚'
  if (/snack|chip/.test(value)) return '🥨'
  if (/drink|beverage|juice/.test(value)) return '🧃'
  if (/sweet|chocolate|dessert/.test(value)) return '🍫'
  if (/household|clean/.test(value)) return '🧽'
  return '🛒'
}

export function formatRupees(value: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  }).format(value)
}
