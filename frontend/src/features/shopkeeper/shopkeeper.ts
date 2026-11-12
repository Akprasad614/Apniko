export interface Shop {
  id: number
  name: string
  ownerName: string
  phone: string
  address: string
  active: boolean
}

export interface Product {
  id: number
  name: string
  category: string | null
}

export interface InventoryItem {
  id: number
  shopId: number
  shopName: string
  productId: number
  productName: string
  category: string | null
  price: number
  stockQuantity: number
  available: boolean
  lowStockThreshold: number
  lowStock: boolean
}

export type OrderStatus = 'PLACED' | 'ACCEPTED' | 'PREPARING' | 'READY' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'CANCELLED'

export interface ShopkeeperOrder {
  id: number
  shop: { id: number; name: string }
  totalAmount: number
  status: OrderStatus
  deliveryMode: 'SHOP_DELIVERY' | 'PLATFORM_DELIVERY' | string
  items: Array<{
    id: number
    product: Product
    quantity: number
    price: number
  }>
}

export const nextShopkeeperStatus: Partial<Record<OrderStatus, OrderStatus>> = {
  PLACED: 'ACCEPTED',
  ACCEPTED: 'PREPARING',
  PREPARING: 'READY',
}

export const statusActionLabel: Partial<Record<OrderStatus, string>> = {
  PLACED: 'Accept order',
  ACCEPTED: 'Start preparing',
  PREPARING: 'Mark ready',
}

export function formatRupees(value: number): string {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 }).format(value)
}

export function statusLabel(status: OrderStatus): string {
  return status.replaceAll('_', ' ').toLowerCase().replace(/^./, (letter) => letter.toUpperCase())
}

export function filterOrdersForShop(orders: ShopkeeperOrder[], shopId: number | null): ShopkeeperOrder[] {
  if (shopId === null) return []
  return orders.filter((order) => order.shop.id === shopId)
}
