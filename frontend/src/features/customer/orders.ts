export const ORDER_PROGRESS = [
  'PLACED',
  'ACCEPTED',
  'PREPARING',
  'READY',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
] as const

export type OrderProgressStatus = typeof ORDER_PROGRESS[number]

export interface CustomerOrderItem {
  id: number
  quantity: number
  price: number
  product?: {
    id: number
    name: string
    category?: string | null
  }
}

export interface CustomerOrder {
  id: number
  status: string
  deliveryMode: string
  totalAmount: number
  shop?: {
    id: number
    name: string
    address?: string | null
  }
  items: CustomerOrderItem[]
}

export interface OrderStatusHistoryEntry {
  id: number
  previousStatus?: string | null
  newStatus: string
  changedByName?: string | null
  note?: string | null
  changedAt?: string | null
}

export function canCancelCustomerOrder(status: string): boolean {
  return status === 'PLACED'
}

export function getOrderProgressIndex(status: string): number {
  return ORDER_PROGRESS.indexOf(status as OrderProgressStatus)
}

export function formatOrderStatus(status: string): string {
  return status
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}
