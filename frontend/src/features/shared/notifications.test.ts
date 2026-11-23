import { describe, expect, it } from 'vitest'
import { formatNotificationDate, notificationDestination } from './notifications'

describe('notification destinations', () => {
  it('opens a customer order from an order notification', () => {
    expect(notificationDestination('CUSTOMER', 42)).toBe('/customer/orders?placed=42')
  })

  it('routes each role to its own workspace when no customer order is linked', () => {
    expect(notificationDestination('CUSTOMER')).toBe('/customer')
    expect(notificationDestination('SHOPKEEPER', 42)).toBe('/shopkeeper')
    expect(notificationDestination('DELIVERY_PARTNER', 42)).toBe('/delivery')
    expect(notificationDestination('ADMIN', 42)).toBe('/admin')
  })

  it('does not build a customer order route from an invalid identifier', () => {
    expect(notificationDestination('CUSTOMER', 0)).toBe('/customer')
    expect(notificationDestination('CUSTOMER', Number.NaN)).toBe('/customer')
  })
})

describe('notification date display', () => {
  it('formats a valid notification date using the Indian locale', () => {
    expect(formatNotificationDate('2026-11-23T10:15:00')).toContain('23 Nov')
  })

  it('returns no date label for invalid input', () => {
    expect(formatNotificationDate('not-a-date')).toBe('')
  })
})
