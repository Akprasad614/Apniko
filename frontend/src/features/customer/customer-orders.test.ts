import { describe, expect, it } from 'vitest'
import {
  filterCustomerOrders,
  formatCustomerOrderAddress,
  formatOrderDate,
  getDeliveryModeLabel,
  getOrderItemSummary,
  getOrderStatusLabel,
  sortCustomerOrders,
} from './customer-orders'
import type { CustomerOrder } from './customer-orders'

const orders: CustomerOrder[] = [
  { id: 11, totalAmount: 290, status: 'PLACED', deliveryMode: 'SHOP_DELIVERY' },
  { id: 9, totalAmount: 150, status: 'DELIVERED', deliveryMode: 'CUSTOMER_PICKUP' },
  { id: 10, totalAmount: 80, status: 'CANCELLED', deliveryMode: 'PLATFORM_DELIVERY' },
]

describe('customer order helpers', () => {
  it('sorts orders newest first without mutating the source array', () => {
    const sorted = sortCustomerOrders(orders)
    expect(sorted.map((order) => order.id)).toEqual([11, 10, 9])
    expect(orders.map((order) => order.id)).toEqual([11, 9, 10])
  })

  it('filters active, delivered and cancelled orders', () => {
    expect(filterCustomerOrders(orders, 'ACTIVE').map((order) => order.id)).toEqual([11])
    expect(filterCustomerOrders(orders, 'DELIVERED').map((order) => order.id)).toEqual([9])
    expect(filterCustomerOrders(orders, 'CANCELLED').map((order) => order.id)).toEqual([10])
    expect(filterCustomerOrders(orders, 'ALL')).toHaveLength(3)
  })

  it('uses customer-friendly delivery and status labels', () => {
    expect(getDeliveryModeLabel('CUSTOMER_PICKUP')).toBe('Customer pickup')
    expect(getDeliveryModeLabel('SHOP_DELIVERY')).toBe('Shop delivery')
    expect(getOrderStatusLabel('OUT_FOR_DELIVERY')).toBe('Out For Delivery')
  })

  it('summarizes basket items without hiding the remaining count', () => {
    expect(getOrderItemSummary([
      { id: 1, quantity: 2, price: 40, product: { name: 'Rice' } },
      { id: 2, quantity: 1, price: 20, product: { name: 'Milk' } },
      { id: 3, quantity: 3, price: 10, product: { name: 'Tomato' } },
    ])).toBe('Rice × 2, Milk × 1 + 1 more')
    expect(getOrderItemSummary([])).toBe('No item details available')
  })

  it('formats the saved delivery snapshot without empty separators', () => {
    expect(formatCustomerOrderAddress({
      addressLine1: '12 Market Road',
      addressLine2: 'Floor 2',
      locality: 'Indiranagar',
      landmark: 'Near Metro',
      city: 'Bengaluru',
      state: 'Karnataka',
      postalCode: '560038',
    })).toBe('12 Market Road, Floor 2, Indiranagar · Near Metro, Bengaluru, Karnataka, 560038')
    expect(formatCustomerOrderAddress(null)).toBe('')
  })

  it('handles ISO strings and Java LocalDateTime arrays', () => {
    expect(formatOrderDate('not-a-date')).toBe('Time not available')
    expect(formatOrderDate(null)).toBe('Time not available')
    expect(formatOrderDate([2026, 11, 12, 12, 30, 0])).not.toBe('Time not available')
  })
})
