export interface CustomerOrderAddress {
  label?: string | null
  recipientName?: string | null
  phone?: string | null
  addressLine1?: string | null
  addressLine2?: string | null
  landmark?: string | null
  locality?: string | null
  city?: string | null
  state?: string | null
  postalCode?: string | null
}

export interface CustomerOrderItem {
  id: number
  quantity: number
  price: number
  product?: {
    id?: number
    name?: string
    category?: string | null
  } | null
}

export interface CustomerOrder {
  id: number
  totalAmount: number
  status: string
  deliveryMode?: string | null
  deliveryAddressSnapshot?: CustomerOrderAddress | null
  shop?: {
    id?: number
    name?: string
    address?: string
  } | null
  items?: CustomerOrderItem[] | null
}

export interface CustomerOrderStatusEvent {
  id: number
  orderId: number
  previousStatus: string | null
  newStatus: string
  note: string | null
  changedAt: string | number[] | null
}

export type CustomerOrderFilter = 'ALL' | 'ACTIVE' | 'DELIVERED' | 'CANCELLED'

export function sortCustomerOrders(orders: CustomerOrder[]): CustomerOrder[] {
  return [...orders].sort((left, right) => right.id - left.id)
}

export function filterCustomerOrders(
  orders: CustomerOrder[],
  filter: CustomerOrderFilter,
): CustomerOrder[] {
  if (filter === 'ALL') return orders
  if (filter === 'ACTIVE') {
    return orders.filter((order) => order.status !== 'DELIVERED' && order.status !== 'CANCELLED')
  }
  return orders.filter((order) => order.status === filter)
}

export function getDeliveryModeLabel(mode: string | null | undefined): string {
  switch (mode) {
    case 'SHOP_DELIVERY':
      return 'Shop delivery'
    case 'PLATFORM_DELIVERY':
      return 'Apniko delivery'
    case 'CUSTOMER_PICKUP':
      return 'Customer pickup'
    default:
      return 'Delivery details unavailable'
  }
}

export function getOrderStatusLabel(status: string): string {
  return status
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

export function getOrderItemSummary(items: CustomerOrderItem[] | null | undefined): string {
  if (!items?.length) return 'No item details available'
  const names = items.slice(0, 2).map((item) => {
    const name = item.product?.name || 'Item'
    return `${name} × ${item.quantity}`
  })
  const remaining = items.length - names.length
  return remaining > 0 ? `${names.join(', ')} + ${remaining} more` : names.join(', ')
}

export function formatOrderDate(value: string | number[] | null | undefined): string {
  if (!value) return 'Time not available'

  let date: Date
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0, nanosecond = 0] = value
    date = new Date(year, month - 1, day, hour, minute, second, Math.floor(nanosecond / 1_000_000))
  } else {
    date = new Date(value)
  }

  if (Number.isNaN(date.getTime())) return 'Time not available'
  return new Intl.DateTimeFormat('en-IN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

export function formatCustomerOrderAddress(address: CustomerOrderAddress | null | undefined): string {
  if (!address) return ''
  return [
    address.addressLine1,
    address.addressLine2,
    [address.locality, address.landmark].filter(Boolean).join(' · '),
    [address.city, address.state, address.postalCode].filter(Boolean).join(', '),
  ].filter((part): part is string => Boolean(part?.trim())).join(', ')
}
