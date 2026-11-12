import { describe, expect, it } from 'vitest'
import {
  createEmptyAddressDraft,
  formatAddressLocation,
  toAddressDraft,
} from './customer-addresses'
import type { CustomerAddress } from './customer-addresses'

const address: CustomerAddress = {
  id: 5,
  label: 'Home',
  recipientName: 'Avtar',
  phone: '+919876543210',
  addressLine1: '12, Market Road',
  addressLine2: null,
  landmark: 'Near the park',
  locality: 'Indiranagar',
  city: 'Bengaluru',
  state: 'Karnataka',
  postalCode: '560038',
  defaultAddress: true,
}

describe('customer address helpers', () => {
  it('starts a new address with a useful label and empty delivery details', () => {
    const draft = createEmptyAddressDraft()
    expect(draft.label).toBe('Home')
    expect(draft.recipientName).toBe('')
    expect(draft.postalCode).toBe('')
    expect(draft.defaultAddress).toBe(false)
  })

  it('maps a saved address into an editable form without losing its default flag', () => {
    const draft = toAddressDraft(address)
    expect(draft.addressLine1).toBe('12, Market Road')
    expect(draft.addressLine2).toBe('')
    expect(draft.defaultAddress).toBe(true)
  })

  it('formats a readable Indian delivery address', () => {
    expect(formatAddressLocation(address))
      .toBe('12, Market Road, Indiranagar · Near the park, Bengaluru, Karnataka, 560038')
  })
})
