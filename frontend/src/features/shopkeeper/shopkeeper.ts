export const SHOPKEEPER_ORDER_PROGRESS = [
  'PLACED',
  'ACCEPTED',
  'PREPARING',
  'READY',
] as const

export type ShopkeeperOrderStatus = typeof SHOPKEEPER_ORDER_PROGRESS[number]

export interface ShopkeeperShop {
  id: number
  name: string
  ownerName?: string
  phone?: string
  address?: string
  active: boolean
}

export interface ShopkeeperProduct {
  id: number
  name: string
  category?: string | null
}

export interface ShopkeeperInventoryItem {
  id: number
  shopId: number
  shopName: string
  productId: number
  productName: string
  category?: string | null
  price: number
  stockQuantity: number
  available: boolean
  lowStockThreshold: number
  lowStock: boolean
}

export interface ShopkeeperOrderItem {
  id: number
  quantity: number
  price: number
  product?: {
    id: number
    name: string
    category?: string | null
  }
}

export interface ShopkeeperOrder {
  id: number
  totalAmount: number
  status: string
  deliveryMode: string
  shop?: {
    id: number
    name: string
  }
  items: ShopkeeperOrderItem[]
}

export interface ShopkeeperOrderHistory {
  id: number
  previousStatus?: string | null
  newStatus: string
  note?: string | null
  changedByName?: string | null
  changedAt?: string | null
}

export interface InventoryMovement {
  id: number
  inventoryId: number
  shopId: number
  productId: number
  productName: string
  movementType: string
  quantityChange: number
  previousQuantity: number
  newQuantity: number
  performedByName?: string | null
  note?: string | null
  createdAt?: string | null
}

export function nextShopkeeperOrderStatus(status: string): ShopkeeperOrderStatus | null {
  switch (status) {
    case 'PLACED': return 'ACCEPTED'
    case 'ACCEPTED': return 'PREPARING'
    case 'PREPARING': return 'READY'
    default: return null
  }
}

export function canRejectShopkeeperOrder(status: string): boolean {
  return status === 'PLACED'
}

export function canCancelShopkeeperOrder(status: string): boolean {
  return ['PLACED', 'ACCEPTED', 'PREPARING', 'READY'].includes(status)
}

export function formatShopkeeperStatus(value: string): string {
  return value.toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

export function formatInventoryMovement(value: string): string {
  return value.toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

export function formatShopkeeperRupees(value: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  }).format(value)
}
