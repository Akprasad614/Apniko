import { describe, expect, it } from 'vitest'
import {
  canCancelShopkeeperOrder,
  canRejectShopkeeperOrder,
  formatInventoryMovement,
  formatShopkeeperStatus,
  nextShopkeeperOrderStatus,
} from './shopkeeper'

describe('shopkeeper order workflow', () => {
  it('allows the existing shopkeeper transitions in sequence', () => {
    expect(nextShopkeeperOrderStatus('PLACED')).toBe('ACCEPTED')
    expect(nextShopkeeperOrderStatus('ACCEPTED')).toBe('PREPARING')
    expect(nextShopkeeperOrderStatus('PREPARING')).toBe('READY')
    expect(nextShopkeeperOrderStatus('READY')).toBeNull()
    expect(nextShopkeeperOrderStatus('DELIVERED')).toBeNull()
  })

  it('only offers rejection before the shop accepts an order', () => {
    expect(canRejectShopkeeperOrder('PLACED')).toBe(true)
    expect(canRejectShopkeeperOrder('ACCEPTED')).toBe(false)
    expect(canRejectShopkeeperOrder('PREPARING')).toBe(false)
    expect(canRejectShopkeeperOrder('READY')).toBe(false)
  })

  it('matches cancellation eligibility supported by the backend', () => {
    expect(canCancelShopkeeperOrder('PLACED')).toBe(true)
    expect(canCancelShopkeeperOrder('ACCEPTED')).toBe(true)
    expect(canCancelShopkeeperOrder('PREPARING')).toBe(true)
    expect(canCancelShopkeeperOrder('READY')).toBe(true)
    expect(canCancelShopkeeperOrder('OUT_FOR_DELIVERY')).toBe(false)
    expect(canCancelShopkeeperOrder('DELIVERED')).toBe(false)
    expect(canCancelShopkeeperOrder('CANCELLED')).toBe(false)
  })

  it('formats backend enum values for the dashboard', () => {
    expect(formatShopkeeperStatus('OUT_FOR_DELIVERY')).toBe('Out For Delivery')
    expect(formatInventoryMovement('CANCELLATION_RESTOCK')).toBe('Cancellation Restock')
  })
})
