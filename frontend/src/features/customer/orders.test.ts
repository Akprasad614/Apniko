import { describe, expect, it } from 'vitest'
import {
  canCancelCustomerOrder,
  formatOrderStatus,
  getOrderProgressIndex,
} from './orders'

describe('customer order presentation', () => {
  it('allows cancellation only before the shop accepts an order', () => {
    expect(canCancelCustomerOrder('PLACED')).toBe(true)
    expect(canCancelCustomerOrder('ACCEPTED')).toBe(false)
    expect(canCancelCustomerOrder('PREPARING')).toBe(false)
    expect(canCancelCustomerOrder('OUT_FOR_DELIVERY')).toBe(false)
    expect(canCancelCustomerOrder('DELIVERED')).toBe(false)
    expect(canCancelCustomerOrder('CANCELLED')).toBe(false)
  })

  it('maps an order status to its place in the delivery progress', () => {
    expect(getOrderProgressIndex('PLACED')).toBe(0)
    expect(getOrderProgressIndex('READY')).toBe(3)
    expect(getOrderProgressIndex('DELIVERED')).toBe(5)
    expect(getOrderProgressIndex('CANCELLED')).toBe(-1)
  })

  it('turns backend enum values into readable labels', () => {
    expect(formatOrderStatus('OUT_FOR_DELIVERY')).toBe('Out For Delivery')
    expect(formatOrderStatus('CUSTOMER_PICKUP')).toBe('Customer Pickup')
  })
})
