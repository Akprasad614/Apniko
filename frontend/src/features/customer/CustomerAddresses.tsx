import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '../../auth/AuthProvider'
import { ApiError, apiRequest } from '../../lib/api'
import './customer-shell.css'

export interface CustomerAddress {
  id: number
  label: string
  recipientName: string
  phone: string
  addressLine1: string
  addressLine2: string | null
  landmark: string | null
  city: string
  state: string
  postalCode: string
  latitude: number | null
  longitude: number | null
  defaultAddress: boolean
}

interface AddressForm {
  label: string
  recipientName: string
  phone: string
  addressLine1: string
  addressLine2: string
  landmark: string
  city: string
  state: string
  postalCode: string
  latitude: number | null
  longitude: number | null
  defaultAddress: boolean
}

const emptyForm: AddressForm = {
  label: 'Home', recipientName: '', phone: '', addressLine1: '', addressLine2: '',
  landmark: '', city: '', state: '', postalCode: '', latitude: null, longitude: null,
  defaultAddress: false,
}

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Your session has expired. Sign in again.'
    if (error.status === 403) return 'Only customer accounts can manage delivery addresses.'
    return error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

function formFromAddress(address: CustomerAddress): AddressForm {
  return {
    label: address.label, recipientName: address.recipientName, phone: address.phone,
    addressLine1: address.addressLine1, addressLine2: address.addressLine2 ?? '',
    landmark: address.landmark ?? '', city: address.city, state: address.state,
    postalCode: address.postalCode, latitude: address.latitude, longitude: address.longitude,
    defaultAddress: address.defaultAddress,
  }
}

export function CustomerAddresses() {
  const { session } = useAuth()
  const navigate = useNavigate()
  const token = session?.token ?? ''
  const [addresses, setAddresses] = useState<CustomerAddress[]>([])
  const [form, setForm] = useState<AddressForm>({ ...emptyForm, recipientName: session?.name ?? '' })
  const [editingId, setEditingId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [locating, setLocating] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const returnToCart = new URLSearchParams(window.location.search).get('returnTo') === 'cart'

  async function loadAddresses() {
    setLoading(true)
    try {
      const result = await apiRequest<CustomerAddress[]>('/api/customer/addresses', {}, token)
      setAddresses(result)
    } catch (loadError) {
      setError(messageFor(loadError))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let active = true
    apiRequest<CustomerAddress[]>('/api/customer/addresses', {}, token)
      .then((result) => { if (active) setAddresses(result) })
      .catch((loadError: unknown) => { if (active) setError(messageFor(loadError)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token])

  function updateField<K extends keyof AddressForm>(key: K, value: AddressForm[K]) {
    setForm((current) => ({ ...current, [key]: value }))
  }

  function startNewAddress() {
    setEditingId(null)
    setForm({ ...emptyForm, recipientName: session?.name ?? '', defaultAddress: addresses.length === 0 })
    setError('')
    setNotice('')
  }

  function editAddress(address: CustomerAddress) {
    setEditingId(address.id)
    setForm(formFromAddress(address))
    setError('')
    setNotice('')
  }

  function useCurrentLocation() {
    if (!navigator.geolocation) {
      setError('Location is not available in this browser. Enter your address manually.')
      return
    }
    setLocating(true)
    setError('')
    setNotice('Requesting your location…')
    navigator.geolocation.getCurrentPosition(
      async (position) => {
        const latitude = position.coords.latitude
        const longitude = position.coords.longitude
        setForm((current) => ({ ...current, latitude, longitude }))
        setNotice('Location captured. Looking up the nearby address…')
        try {
          const query = new URLSearchParams({
            format: 'jsonv2', lat: String(latitude), lon: String(longitude), 'accept-language': 'en',
          })
          const response = await fetch('https://nominatim.openstreetmap.org/reverse?' + query.toString(), {
            headers: { Accept: 'application/json' },
          })
          if (!response.ok) throw new Error('Address lookup was unavailable')
          const result = await response.json() as { address?: Record<string, string> }
          const parts = result.address ?? {}
          const house = parts.house_number ?? ''
          const road = parts.road ?? parts.pedestrian ?? parts.residential ?? ''
          const line1 = [house, road].filter(Boolean).join(' ').trim()
          const neighbourhood = parts.neighbourhood ?? parts.suburb ?? parts.village ?? ''
          const city = parts.city ?? parts.town ?? parts.municipality ?? parts.county ?? parts.village ?? ''
          setForm((current) => ({
            ...current,
            addressLine1: line1 || current.addressLine1,
            addressLine2: current.addressLine2 || neighbourhood,
            city: city || current.city,
            state: parts.state ?? current.state,
            postalCode: parts.postcode ?? current.postalCode,
            latitude,
            longitude,
          }))
          setNotice(line1 || city
            ? 'Location details added. Review the address before saving.'
            : 'Location captured. Complete the address details below.')
        } catch {
          setNotice('Location captured. Complete the address details below and save.')
        } finally {
          setLocating(false)
        }
      },
      (locationError) => {
        setLocating(false)
        setNotice('')
        setError(locationError.code === locationError.PERMISSION_DENIED
          ? 'Location permission was denied. You can still enter the address manually.'
          : 'Could not get your location. Check device location settings or enter the address manually.')
      },
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 60000 },
    )
  }

  async function saveAddress(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const saved = await apiRequest<CustomerAddress>(
        editingId ? '/api/customer/addresses/' + editingId : '/api/customer/addresses',
        { method: editingId ? 'PUT' : 'POST', body: JSON.stringify(form) },
        token,
      )
      await loadAddresses()
      setEditingId(null)
      setForm({ ...emptyForm, recipientName: session?.name ?? '' })
      setNotice('Address saved.')
      if (returnToCart) {
        window.localStorage.setItem('apniko.customer.selectedAddressId', String(saved.id))
        navigate('/customer/cart?addressId=' + saved.id)
      }
    } catch (saveError) {
      setError(messageFor(saveError))
    } finally {
      setSaving(false)
    }
  }

  async function makeDefault(addressId: number) {
    setError('')
    try {
      await apiRequest<void>('/api/customer/addresses/' + addressId + '/default', { method: 'PUT' }, token)
      await loadAddresses()
      setNotice('Default delivery address updated.')
    } catch (updateError) {
      setError(messageFor(updateError))
    }
  }

  async function deleteAddress(addressId: number) {
    if (!window.confirm('Delete this saved address?')) return
    setError('')
    try {
      await apiRequest<void>('/api/customer/addresses/' + addressId, { method: 'DELETE' }, token)
      setAddresses((current) => current.filter((address) => address.id !== addressId))
      setNotice('Address deleted.')
    } catch (deleteError) {
      setError(messageFor(deleteError))
    }
  }

  return (
    <section className="customer-screen customer-address-screen">
      <div className="customer-screen-heading">
        <p className="customer-eyebrow">ACCOUNT / DELIVERY LOCATIONS</p>
        <h1>Your <em>addresses.</em></h1>
        <p>Save the places where you would like your neighbourhood orders delivered.</p>
      </div>
      {error && <div className="customer-state customer-state--error" role="alert">{error}</div>}
      {notice && <div className="customer-state" role="status">{notice}</div>}

      <div className="customer-address-layout">
        <section className="customer-address-list" aria-labelledby="saved-addresses-title">
          <div className="customer-address-heading">
            <div><p className="customer-eyebrow">SAVED LOCATIONS</p><h2 id="saved-addresses-title">Where should we go?</h2></div>
            <button type="button" className="customer-address-secondary" onClick={startNewAddress}>+ Add address</button>
          </div>
          {loading ? (
            <div className="customer-state customer-state--inline" role="status">Loading saved addresses…</div>
          ) : addresses.length === 0 ? (
            <div className="customer-state customer-state--empty">
              <strong>No saved addresses yet.</strong>
              <p>Add your first delivery location. You can use your current location to fill in nearby address details.</p>
            </div>
          ) : (
            <div className="customer-address-cards">
              {addresses.map((address) => (
                <article className="customer-address-card" key={address.id}>
                  <div className="customer-address-card__top">
                    <span className="customer-address-tag">{address.label}</span>
                    {address.defaultAddress && <span className="customer-address-default">Default</span>}
                  </div>
                  <strong>{address.recipientName}</strong>
                  <span>{address.phone}</span>
                  <p>{address.addressLine1}{address.addressLine2 ? ', ' + address.addressLine2 : ''}{address.landmark ? ', near ' + address.landmark : ''}, {address.city}, {address.state} - {address.postalCode}</p>
                  {address.latitude !== null && address.longitude !== null && (
                    <small>Location pin saved · {address.latitude.toFixed(5)}, {address.longitude.toFixed(5)}</small>
                  )}
                  <div className="customer-address-card__actions">
                    {!address.defaultAddress && <button type="button" onClick={() => void makeDefault(address.id)}>Make default</button>}
                    <button type="button" onClick={() => editAddress(address)}>Edit</button>
                    <button type="button" className="is-danger" onClick={() => void deleteAddress(address.id)}>Delete</button>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>

        <section className="customer-address-editor" aria-labelledby="address-editor-title">
          <p className="customer-eyebrow">{editingId ? 'EDIT LOCATION' : 'NEW LOCATION'}</p>
          <h2 id="address-editor-title">{editingId ? 'Update this address.' : 'Add a delivery address.'}</h2>
          <button type="button" className="customer-location-button" onClick={useCurrentLocation} disabled={locating}>
            <span aria-hidden="true">⌖</span>{locating ? 'Finding your location…' : 'Use my current location'}
          </button>
          <p className="customer-address-location-note">Your browser will ask for permission only after you tap the button. You can also fill in every field manually.</p>

          <form className="customer-address-form" onSubmit={(event) => void saveAddress(event)}>
            <label><span>Save this place as</span>
              <select value={form.label} onChange={(event) => updateField('label', event.target.value)} required>
                <option>Home</option><option>Work</option><option>Other</option>
              </select>
            </label>
            <div className="customer-address-form__grid">
              <label><span>Recipient name</span><input value={form.recipientName} onChange={(event) => updateField('recipientName', event.target.value)} required maxLength={100} /></label>
              <label><span>Phone number</span><input value={form.phone} onChange={(event) => updateField('phone', event.target.value)} required inputMode="tel" maxLength={24} placeholder="10-digit mobile number" /></label>
            </div>
            <label><span>House / Flat, street and area</span><input value={form.addressLine1} onChange={(event) => updateField('addressLine1', event.target.value)} required maxLength={180} placeholder="House no., street name" /></label>
            <label><span>Area / locality (optional)</span><input value={form.addressLine2} onChange={(event) => updateField('addressLine2', event.target.value)} maxLength={180} placeholder="Neighbourhood, layout" /></label>
            <label><span>Landmark (optional)</span><input value={form.landmark} onChange={(event) => updateField('landmark', event.target.value)} maxLength={120} /></label>
            <div className="customer-address-form__grid customer-address-form__grid--city">
              <label><span>City</span><input value={form.city} onChange={(event) => updateField('city', event.target.value)} required maxLength={80} /></label>
              <label><span>State</span><input value={form.state} onChange={(event) => updateField('state', event.target.value)} required maxLength={80} /></label>
              <label><span>PIN code</span><input value={form.postalCode} onChange={(event) => updateField('postalCode', event.target.value)} required inputMode="numeric" pattern="[1-9][0-9]{5}" maxLength={6} /></label>
            </div>
            <label className="customer-address-default-toggle">
              <input type="checkbox" checked={form.defaultAddress} onChange={(event) => updateField('defaultAddress', event.target.checked)} />
              <span>Make this my default delivery address</span>
            </label>
            {form.latitude !== null && form.longitude !== null && (
              <p className="customer-address-coordinates">Location pin: {form.latitude.toFixed(5)}, {form.longitude.toFixed(5)}</p>
            )}
            <div className="customer-address-form__actions">
              <button className="customer-cart-checkout" type="submit" disabled={saving || locating}>{saving ? 'Saving…' : editingId ? 'Save changes' : 'Save address'} <span aria-hidden="true">→</span></button>
              {editingId && <button type="button" className="customer-address-secondary" onClick={startNewAddress}>Cancel edit</button>}
            </div>
          </form>
          <Link to="/customer/cart" className="customer-feature-back">← Back to cart</Link>
        </section>
      </div>
    </section>
  )
}
