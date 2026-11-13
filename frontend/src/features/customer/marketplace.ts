export interface MarketplaceShop {
  id: number
  name: string
  ownerName: string
  phone: string
  address: string
  active: boolean
  latitude?: number | null
  longitude?: number | null
}

export interface MarketplaceInventoryItem {
  id: number
  shopId: number
  shopName: string
  shopLatitude?: number | null
  shopLongitude?: number | null
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

export interface CustomerCoordinates {
  latitude: number
  longitude: number
}

const EARTH_RADIUS_KM = 6371

const CATEGORY_IMAGE_URLS: Array<{ match: RegExp; url: string }> = [
  {
    match: /vegetable|produce|greens|grocery/i,
    url: 'https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&w=640&q=82',
  },
  {
    match: /fruit/i,
    url: 'https://images.unsplash.com/photo-1490474418585-ba9bad8fd0ea?auto=format&fit=crop&w=640&q=82',
  },
  {
    match: /dairy|milk|egg|bread|bakery/i,
    url: 'https://images.unsplash.com/photo-1509440159596-0249088772ff?auto=format&fit=crop&w=640&q=82',
  },
  {
    match: /meat|fish|seafood|chicken/i,
    url: 'https://images.unsplash.com/photo-1607623814075-e51df1bdc82f?auto=format&fit=crop&w=640&q=82',
  },
  {
    match: /snack|chip|namkeen|chocolate|sweet/i,
    url: 'https://images.unsplash.com/photo-1621939514649-280e2aa4849c?auto=format&fit=crop&w=640&q=82',
  },
  {
    match: /drink|beverage|juice|water|coffee|tea/i,
    url: 'https://images.unsplash.com/photo-1544145945-f90425340c7e?auto=format&fit=crop&w=640&q=82',
  },
  {
    match: /rice|grain|pulses|dal|staple|flour/i,
    url: 'https://images.unsplash.com/photo-1586201375761-83865001e31c?auto=format&fit=crop&w=640&q=82',
  },
]

const DEFAULT_PRODUCT_IMAGE =
  'https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&w=640&q=82'

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
): MarketplaceShop[] {
  const normalizedQuery = query.trim().toLocaleLowerCase()
  if (!normalizedQuery) return shops

  return shops.filter((shop) => [
    shop.name,
    shop.ownerName,
    shop.address,
  ].some((value) => value.toLocaleLowerCase().includes(normalizedQuery)))
}

export function distanceBetweenCoordinatesKm(
  origin: CustomerCoordinates,
  destination: CustomerCoordinates,
): number | null {
  const coordinates = [
    origin.latitude,
    origin.longitude,
    destination.latitude,
    destination.longitude,
  ]
  if (!coordinates.every(Number.isFinite)) return null
  if (Math.abs(origin.latitude) > 90 || Math.abs(destination.latitude) > 90) return null
  if (Math.abs(origin.longitude) > 180 || Math.abs(destination.longitude) > 180) return null

  const radians = (degrees: number) => degrees * Math.PI / 180
  const latitudeDelta = radians(destination.latitude - origin.latitude)
  const longitudeDelta = radians(destination.longitude - origin.longitude)
  const originLatitude = radians(origin.latitude)
  const destinationLatitude = radians(destination.latitude)

  const haversine = Math.sin(latitudeDelta / 2) ** 2
    + Math.cos(originLatitude) * Math.cos(destinationLatitude)
    * Math.sin(longitudeDelta / 2) ** 2
  const centralAngle = 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine))

  return EARTH_RADIUS_KM * centralAngle
}

export function getShopDistanceKm(
  shop: Pick<MarketplaceShop, 'latitude' | 'longitude'>,
  origin: CustomerCoordinates | null,
): number | null {
  if (!origin || shop.latitude == null || shop.longitude == null) return null

  return distanceBetweenCoordinatesKm(origin, {
    latitude: shop.latitude,
    longitude: shop.longitude,
  })
}

export function sortShopsByDistance(
  shops: MarketplaceShop[],
  origin: CustomerCoordinates | null,
): MarketplaceShop[] {
  if (!origin) return [...shops]

  return shops
    .map((shop, index) => ({
      shop,
      index,
      distance: getShopDistanceKm(shop, origin),
    }))
    .sort((left, right) => {
      if (left.distance === null && right.distance === null) return left.index - right.index
      if (left.distance === null) return 1
      if (right.distance === null) return -1
      return left.distance - right.distance || left.index - right.index
    })
    .map((entry) => entry.shop)
}

export function formatDistanceKm(distanceKm: number | null): string | null {
  if (distanceKm === null || !Number.isFinite(distanceKm) || distanceKm < 0) return null
  if (distanceKm < 1) return `${Math.max(50, Math.round(distanceKm * 1000 / 50) * 50)} m away`
  return `${distanceKm.toFixed(distanceKm < 10 ? 1 : 0)} km away`
}

export function isSafeProductImageUrl(value: string | null | undefined): value is string {
  if (!value?.trim()) return false

  try {
    const parsed = new URL(value)
    return parsed.protocol === 'https:' && Boolean(parsed.hostname)
  } catch {
    return false
  }
}

export function getFallbackProductImage(category: string | null | undefined): string {
  const match = CATEGORY_IMAGE_URLS.find((entry) => entry.match.test(category ?? ''))
  return match?.url ?? DEFAULT_PRODUCT_IMAGE
}

export function resolveProductImageUrl(
  imageUrl: string | null | undefined,
  category: string | null | undefined,
): string {
  return isSafeProductImageUrl(imageUrl)
    ? imageUrl
    : getFallbackProductImage(category)
}

export function formatRupees(value: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  }).format(value)
}
