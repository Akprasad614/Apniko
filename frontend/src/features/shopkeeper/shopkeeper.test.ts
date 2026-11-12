import { describe, expect, it } from 'vitest'
import { nextShopkeeperStatus, statusActionLabel, statusLabel } from './shopkeeper'

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
