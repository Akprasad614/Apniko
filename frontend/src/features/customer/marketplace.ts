export interface GeoPoint {
  latitude: number
  longitude: number
}

export interface MarketplaceShop {
  id: number
  name: string
  ownerName: string
  phone: string
  address: string
  active: boolean
  latitude: number | null
  longitude: number | null
}

export interface MarketplaceInventoryItem {
  id: number
  shopId: number
  shopName: string
  shopAddress: string
  shopLatitude: number | null
  shopLongitude: number | null
  productId: number
  productName: string
  category: string | null
  imageUrl: string | null
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

export function filterMarketplaceShops(
  shops: MarketplaceShop[],
  query: string,
  area: string,
): MarketplaceShop[] {
  const normalizedQuery = query.trim().toLocaleLowerCase()
  const normalizedArea = area.trim().toLocaleLowerCase()

  return shops.filter((shop) => {
    const searchable = `${shop.name} ${shop.ownerName} ${shop.address}`.toLocaleLowerCase()
    const matchesQuery = !normalizedQuery || searchable.includes(normalizedQuery)
    const matchesArea = !normalizedArea || shop.address.toLocaleLowerCase().includes(normalizedArea)
    return matchesQuery && matchesArea
  })
}

export function filterMarketplaceProductsByArea(
  products: MarketplaceInventoryItem[],
  area: string,
): MarketplaceInventoryItem[] {
  const normalizedArea = area.trim().toLocaleLowerCase()
  if (!normalizedArea) return products
  return products.filter((product) =>
    (product.shopAddress ?? '').toLocaleLowerCase().includes(normalizedArea),
  )
}

export function getDistanceKm(origin: GeoPoint, destination: GeoPoint): number | null {
  const values = [
    origin.latitude,
    origin.longitude,
    destination.latitude,
    destination.longitude,
  ]
  if (!values.every(Number.isFinite)
      || Math.abs(origin.latitude) > 90
      || Math.abs(destination.latitude) > 90
      || Math.abs(origin.longitude) > 180
      || Math.abs(destination.longitude) > 180) {
    return null
  }

  const radians = (degrees: number) => (degrees * Math.PI) / 180
  const latitudeDelta = radians(destination.latitude - origin.latitude)
  const longitudeDelta = radians(destination.longitude - origin.longitude)
  const originLatitude = radians(origin.latitude)
  const destinationLatitude = radians(destination.latitude)
  const haversine = Math.sin(latitudeDelta / 2) ** 2
    + Math.cos(originLatitude) * Math.cos(destinationLatitude)
    * Math.sin(longitudeDelta / 2) ** 2

  return 6371 * 2 * Math.atan2(
    Math.sqrt(haversine),
    Math.sqrt(Math.max(0, 1 - haversine)),
  )
}

export function filterShopsWithinRadius(
  shops: MarketplaceShop[],
  origin: GeoPoint,
  radiusKm: number,
): MarketplaceShop[] {
  return shops
    .map((shop) => ({
      shop,
      distance: shop.latitude == null || shop.longitude == null
        ? null
        : getDistanceKm(origin, { latitude: shop.latitude, longitude: shop.longitude }),
    }))
    .filter((entry) => entry.distance !== null && entry.distance <= radiusKm)
    .sort((left, right) => (left.distance ?? Infinity) - (right.distance ?? Infinity))
    .map((entry) => entry.shop)
}

export function filterProductsWithinRadius(
  products: MarketplaceInventoryItem[],
  origin: GeoPoint,
  radiusKm: number,
): MarketplaceInventoryItem[] {
  return products
    .map((product) => ({
      product,
      distance: product.shopLatitude == null || product.shopLongitude == null
        ? null
        : getDistanceKm(origin, { latitude: product.shopLatitude, longitude: product.shopLongitude }),
    }))
    .filter((entry) => entry.distance !== null && entry.distance <= radiusKm)
    .sort((left, right) => (left.distance ?? Infinity) - (right.distance ?? Infinity))
    .map((entry) => entry.product)
}

export function getProductCategoryPhoto(category: string | null | undefined): string | null {
  const normalized = (category ?? '').toLocaleLowerCase()

  if (/vegetable|produce|greens/.test(normalized)) {
    return 'https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&w=640&q=80'
  }
  if (/fruit/.test(normalized)) {
    return 'https://images.unsplash.com/photo-1583258292688-d0213dc5a3a8?auto=format&fit=crop&w=640&q=80'
  }
  if (/snack|chip|namkeen|biscuit/.test(normalized)) {
    return 'https://images.unsplash.com/photo-1566040081794-16550083ad4e?auto=format&fit=crop&w=640&q=80'
  }
  if (/bread|bakery/.test(normalized)) {
    return 'https://images.unsplash.com/photo-1585735633320-d24595a213a1?auto=format&fit=crop&w=640&q=80'
  }
  return null
}

export function getProductCategoryIcon(category: string | null | undefined): string {
  const normalized = (category ?? '').toLocaleLowerCase()

  if (/vegetable|produce|greens/.test(normalized)) return '🥬'
  if (/fruit/.test(normalized)) return '🍎'
  if (/dairy|milk|paneer|cheese|egg/.test(normalized)) return '🥛'
  if (/bread|bakery|grain|rice|atta|flour|staple/.test(normalized)) return '🍞'
  if (/meat|chicken|fish|seafood/.test(normalized)) return '🍗'
  if (/snack|chip|namkeen|biscuit/.test(normalized)) return '🥨'
  if (/drink|beverage|juice|water/.test(normalized)) return '🧃'
  if (/frozen|ice cream/.test(normalized)) return '🍦'
  if (/household|clean|personal|care/.test(normalized)) return '🧴'
  if (/oil|spice|masala|pulse|dal/.test(normalized)) return '🫙'
  return '🛒'
}

export function formatRupees(value: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  }).format(value)
}
