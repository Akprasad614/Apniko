export interface CustomerAddress {
  id: number
  label: string
  recipientName: string
  phone: string
  addressLine1: string
  addressLine2: string | null
  landmark: string | null
  locality: string
  city: string
  state: string
  postalCode: string
  defaultAddress: boolean
}

export interface CustomerAddressDraft {
  label: string
  recipientName: string
  phone: string
  addressLine1: string
  addressLine2: string
  landmark: string
  locality: string
  city: string
  state: string
  postalCode: string
  defaultAddress: boolean
}

export function createEmptyAddressDraft(): CustomerAddressDraft {
  return {
    label: 'Home',
    recipientName: '',
    phone: '',
    addressLine1: '',
    addressLine2: '',
    landmark: '',
    locality: '',
    city: '',
    state: '',
    postalCode: '',
    defaultAddress: false,
  }
}

export function toAddressDraft(address: CustomerAddress): CustomerAddressDraft {
  return {
    label: address.label,
    recipientName: address.recipientName,
    phone: address.phone,
    addressLine1: address.addressLine1,
    addressLine2: address.addressLine2 ?? '',
    landmark: address.landmark ?? '',
    locality: address.locality,
    city: address.city,
    state: address.state,
    postalCode: address.postalCode,
    defaultAddress: address.defaultAddress,
  }
}

export function formatAddressLocation(address: CustomerAddress): string {
  const locality = [address.locality, address.landmark].filter(Boolean).join(' · ')
  const region = [address.city, address.state, address.postalCode].filter(Boolean).join(', ')
  return [
    address.addressLine1,
    address.addressLine2,
    locality,
    region,
  ].filter((line): line is string => Boolean(line?.trim())).join(', ')
}
