import { describe, expect, it } from 'vitest'
import {
  canAcceptShopkeeperOrderWithSavedFulfilment,
  canCancelShopkeeperOrder,
  canRejectShopkeeperOrder,
  canSaveShopkeeperOrderFulfilment,
  formatInventoryMovement,
  formatShopkeeperStatus,
  nextShopkeeperOrderStatus,
} from './shopkeeper'

describe('shopkeeper order workflow', () => {
  it('allows the existing shopkeeper transitions in sequence', () => {
    expect(nextShopkeeperOrderStatus('PLACED')).toBe('ACCEPTED')
    expect(nextShopkeeperOrderStatus('ACCEPTED')).toBe('PREPARING')
    expect(nextShopkeeperOrderStatus('PREPARING')).toBe('READY')
    expect(nextShopkeeperOrderStatus('READY', 'SHOP_DELIVERY')).toBe('OUT_FOR_DELIVERY')
    expect(nextShopkeeperOrderStatus('OUT_FOR_DELIVERY', 'SHOP_DELIVERY')).toBe('DELIVERED')
    expect(nextShopkeeperOrderStatus('READY', 'CUSTOMER_PICKUP')).toBe('DELIVERED')
    expect(nextShopkeeperOrderStatus('READY', 'PLATFORM_DELIVERY')).toBeNull()
    expect(nextShopkeeperOrderStatus('OUT_FOR_DELIVERY', 'PLATFORM_DELIVERY')).toBeNull()
    expect(nextShopkeeperOrderStatus('DELIVERED')).toBeNull()
  })

  it('requires a per-order fulfilment choice to be saved before acceptance', () => {
    expect(canSaveShopkeeperOrderFulfilment('PLACED', 'SHOP_DELIVERY', null, true)).toBe(true)
    expect(canAcceptShopkeeperOrderWithSavedFulfilment('PLACED', 'SHOP_DELIVERY', null, true)).toBe(false)
    expect(canAcceptShopkeeperOrderWithSavedFulfilment('PLACED', 'SHOP_DELIVERY', 'SHOP_DELIVERY', true)).toBe(true)
    expect(canAcceptShopkeeperOrderWithSavedFulfilment('PLACED', 'SHOP_DELIVERY', 'PLATFORM_DELIVERY', true)).toBe(false)
    expect(canSaveShopkeeperOrderFulfilment('ACCEPTED', 'SHOP_DELIVERY', null, true)).toBe(false)
  })

  it('only allows delivery fulfilment when an order has a saved address', () => {
    expect(canSaveShopkeeperOrderFulfilment('PLACED', 'SHOP_DELIVERY', null, false)).toBe(false)
    expect(canSaveShopkeeperOrderFulfilment('PLACED', 'PLATFORM_DELIVERY', null, false)).toBe(false)
    expect(canSaveShopkeeperOrderFulfilment('PLACED', 'CUSTOMER_PICKUP', null, false)).toBe(true)
    expect(canAcceptShopkeeperOrderWithSavedFulfilment('PLACED', 'CUSTOMER_PICKUP', 'CUSTOMER_PICKUP', false)).toBe(true)
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
