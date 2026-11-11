import { describe, expect, it } from 'vitest'
import { isAppRole, publicRegistrationRole, roleHomePath } from './roles'

describe('LocalCart role routing', () => {
  it('maps each backend role to its own portal', () => {
    expect(roleHomePath('CUSTOMER')).toBe('/customer')
    expect(roleHomePath('SHOPKEEPER')).toBe('/shopkeeper')
    expect(roleHomePath('DELIVERY_PARTNER')).toBe('/delivery')
    expect(roleHomePath('ADMIN')).toBe('/admin')
  })

  it('accepts only roles returned by the backend', () => {
    expect(isAppRole('CUSTOMER')).toBe(true)
    expect(isAppRole('ADMIN')).toBe(true)
    expect(isAppRole('SUPER_ADMIN')).toBe(false)
    expect(isAppRole(null)).toBe(false)
  })

  it('keeps public registration customer-only', () => {
    expect(publicRegistrationRole()).toBe('CUSTOMER')
  })
})
