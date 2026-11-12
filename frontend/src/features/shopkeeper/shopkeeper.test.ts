import { describe, expect, it } from 'vitest'
import { filterOrdersForShop, nextShopkeeperStatus, statusActionLabel, statusLabel } from './shopkeeper'

describe('shopkeeper order helpers', () => {
  it('only exposes the backend-supported shopkeeper order transitions', () => {
    expect(nextShopkeeperStatus.PLACED).toBe('ACCEPTED')
    expect(nextShopkeeperStatus.ACCEPTED).toBe('PREPARING')
    expect(nextShopkeeperStatus.PREPARING).toBe('READY')
    expect(nextShopkeeperStatus.READY).toBeUndefined()
    expect(statusActionLabel.PREPARING).toBe('Mark ready')
  })

  it('formats status labels for readable cards', () => {
    expect(statusLabel('OUT_FOR_DELIVERY')).toBe('Out for delivery')
  })
})

describe('shopkeeper shop scoping', () => {
  it('only shows orders belonging to the selected shop', () => {
    const orders = [
      { id: 1, shop: { id: 10, name: 'Corner Store' } },
      { id: 2, shop: { id: 20, name: 'Market Road Shop' } },
    ] as Parameters<typeof filterOrdersForShop>[0]

    expect(filterOrdersForShop(orders, 10).map((order) => order.id)).toEqual([1])
    expect(filterOrdersForShop(orders, null)).toEqual([])
  })
})
