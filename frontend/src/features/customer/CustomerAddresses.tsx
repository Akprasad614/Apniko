import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '../../auth/AuthProvider'
import { ApiError, apiRequest } from '../../lib/api'
import { MapLocationPicker } from './MapLocationPicker'
import type { MapPoint } from './MapLocationPicker'
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

interface GeocodingResult {
  lat: string
  lon: string
  display_name: string
  address?: Record<string, string>
}

const emptyForm: AddressForm = {
  label: 'Home', recipientName: '', phone: '', addressLine1: '', addressLine2: '',
  landmark: '', city: '', state: '', postalCode: '', latitude: null, longitude: null,
  defaultAddress: false,
}

const BENGALURU_MAP_FALLBACK: MapPoint = { lat: 12.9716, lng: 77.5946 }
const GEOCODING_BASE_URL = (import.meta.env.VITE_GEOCODING_BASE_URL || 'https://nominatim.openstreetmap.org').replace(/\/$/, '')
let lastGeocodingRequestAt = 0

async function fetchGeocoding(url: string): Promise<Response> {
  const wait = Math.max(0, 1000 - (Date.now() - lastGeocodingRequestAt))
  if (wait > 0) {
    await new Promise<void>((resolve) => window.setTimeout(resolve, wait))
  }
  lastGeocodingRequestAt = Date.now()
  return fetch(url, { headers: { Accept: 'application/json' } })
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

function addressFieldsFromGeocoding(result: GeocodingResult): Partial<AddressForm> {
  const parts = result.address ?? {}
  const house = parts.house_number ?? ''
  const road = parts.road ?? parts.pedestrian ?? parts.residential ?? parts.footway ?? ''
  const line1 = [house, road].filter(Boolean).join(' ').trim()
  const city = parts.city ?? parts.town ?? parts.municipality ?? parts.county ?? parts.village ?? ''
  const locality = parts.neighbourhood ?? parts.suburb ?? parts.city_district ?? ''

  return {
    addressLine1: line1 || road || result.display_name.split(',')[0]?.trim() || '',
    addressLine2: locality,
    city,
    state: parts.state ?? '',
    postalCode: parts.postcode ?? '',
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
  const [mapOpen, setMapOpen] = useState(false)
  const [mapLocation, setMapLocation] = useState<MapPoint>(BENGALURU_MAP_FALLBACK)
  const [mapStatus, setMapStatus] = useState('')
  const [mapAccuracy, setMapAccuracy] = useState<number | null>(null)
  const [mapLocationReady, setMapLocationReady] = useState(false)
  const [mapSearch, setMapSearch] = useState('')
  const [mapSearchResults, setMapSearchResults] = useState<GeocodingResult[]>([])
  const [searchingMap, setSearchingMap] = useState(false)
  const [confirmingMapLocation, setConfirmingMapLocation] = useState(false)
  const [selectedPlaceName, setSelectedPlaceName] = useState('')
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
    if (address.latitude !== null && address.longitude !== null) {
      setMapLocation({ lat: address.latitude, lng: address.longitude })
    }
    setError('')
    setNotice('')
  }

  function requestDeviceLocation() {
    if (!navigator.geolocation) {
      setLocating(false)
      setMapStatus('Location is not available in this browser. Search for your area or move the pin manually.')
      return
    }

    setLocating(true)
    setMapStatus('Requesting your current location…')
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const point = { lat: position.coords.latitude, lng: position.coords.longitude }
        setMapLocation(point)
        setMapLocationReady(true)
        setMapAccuracy(position.coords.accuracy)
        setSelectedPlaceName('Your current location')
        setMapSearchResults([])
        setMapStatus('Current location found. Check the pin, or drag it to the exact entrance.')
        setLocating(false)
      },
      (locationError) => {
        setLocating(false)
        setMapStatus(locationError.code === locationError.PERMISSION_DENIED
          ? 'Location permission was denied. Search for your address or move the pin manually.'
          : 'Could not get your location. Search for your area or move the pin manually.')
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 30000 },
    )
  }

  function openMapPicker() {
    setError('')
    setMapSearch('')
    setMapSearchResults([])
    setMapAccuracy(null)
    const hasSavedPoint = form.latitude !== null && form.longitude !== null
    setMapLocation(
      hasSavedPoint
        ? { lat: form.latitude as number, lng: form.longitude as number }
        : BENGALURU_MAP_FALLBACK,
    )
    setMapLocationReady(hasSavedPoint)
    setSelectedPlaceName('')
    setMapOpen(true)
    requestDeviceLocation()
  }

  async function searchMapLocation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const queryText = mapSearch.trim()
    if (!queryText) return

    setSearchingMap(true)
    setMapStatus('Searching this address…')
    setMapSearchResults([])
    try {
      const query = new URLSearchParams({
        format: 'jsonv2',
        q: queryText,
        limit: '5',
        addressdetails: '1',
        countrycodes: 'in',
      })
      const response = await fetchGeocoding(GEOCODING_BASE_URL + '/search?' + query.toString())
      if (!response.ok) throw new Error('Address search is temporarily unavailable.')
      const results = await response.json() as GeocodingResult[]
      if (!results.length) {
        setMapStatus('No matching place found. Try a nearby landmark or full address.')
        return
      }
      setMapSearchResults(results)
      const first = results[0]
      if (first) {
        const point = { lat: Number(first.lat), lng: Number(first.lon) }
        setMapLocation(point)
        setMapLocationReady(true)
        setSelectedPlaceName(first.display_name)
        setMapStatus('Choose the correct result below, then adjust the pin if needed.')
      }
    } catch (searchError) {
      setMapStatus(messageFor(searchError))
    } finally {
      setSearchingMap(false)
    }
  }

  function chooseSearchResult(result: GeocodingResult) {
    const point = { lat: Number(result.lat), lng: Number(result.lon) }
    setMapLocation(point)
    setMapLocationReady(true)
    setSelectedPlaceName(result.display_name)
    setMapSearchResults([])
    setMapStatus('Location selected. Drag the pin or tap the map to fine-tune it.')
  }

  function handleMapPositionChange(point: MapPoint) {
    setMapLocation(point)
    setMapLocationReady(true)
    setSelectedPlaceName('Selected map point')
    setMapAccuracy(null)
    setMapStatus('Pin moved. Confirm the location when it points to the right entrance.')
  }

  async function confirmMapLocation() {
    setConfirmingMapLocation(true)
    setMapStatus('Getting address details for this point…')
    const query = new URLSearchParams({
      format: 'jsonv2',
      lat: String(mapLocation.lat),
      lon: String(mapLocation.lng),
      zoom: '18',
      addressdetails: '1',
    })

    try {
      const response = await fetchGeocoding(GEOCODING_BASE_URL + '/reverse?' + query.toString())
      if (!response.ok) throw new Error('Address details could not be looked up.')
      const result = await response.json() as GeocodingResult
      const addressFields = addressFieldsFromGeocoding(result)
      setForm((current) => ({
        ...current,
        ...addressFields,
        latitude: mapLocation.lat,
        longitude: mapLocation.lng,
      }))
      setNotice('Map location confirmed. Review the address details and save your address.')
    } catch {
      setForm((current) => ({
        ...current,
        latitude: mapLocation.lat,
        longitude: mapLocation.lng,
      }))
      setNotice('Map coordinates confirmed. Complete the address fields manually before saving.')
    } finally {
      setConfirmingMapLocation(false)
      setMapOpen(false)
    }
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
          <button type="button" className="customer-location-button" onClick={openMapPicker} disabled={locating}>
            <span aria-hidden="true">⌖</span>{locating ? 'Finding your location…' : 'Use my current location'}
          </button>
          <p className="customer-address-location-note">A map will open so you can check the pin, move it to your exact entrance, and confirm.</p>

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

      {mapOpen && (
        <div className="customer-map-overlay" role="presentation">
          <section className="customer-map-dialog" role="dialog" aria-modal="true" aria-labelledby="customer-map-title">
            <header className="customer-map-header">
              <button type="button" className="customer-map-back" onClick={() => setMapOpen(false)} aria-label="Close map">←</button>
              <div>
                <p className="customer-eyebrow">DELIVERY LOCATION</p>
                <h2 id="customer-map-title">Pin your exact location</h2>
              </div>
            </header>

            <form className="customer-map-search" onSubmit={(event) => void searchMapLocation(event)}>
              <input
                value={mapSearch}
                onChange={(event) => setMapSearch(event.target.value)}
                placeholder="Search an area or address"
                aria-label="Search an area or address"
              />
              <button type="submit" disabled={searchingMap || !mapSearch.trim()}>
                {searchingMap ? 'Searching…' : 'Search'}
              </button>
            </form>

            {mapSearchResults.length > 0 && (
              <div className="customer-map-prelude">
                <div className="customer-map-results" role="list">
                  {mapSearchResults.map((result) => (
                    <button type="button" role="listitem" key={result.lat + ':' + result.lon} onClick={() => chooseSearchResult(result)}>
                      <strong>{result.display_name.split(',')[0]}</strong>
                      <span>{result.display_name}</span>
                    </button>
                  ))}
                </div>
                <div className="customer-map-instructions">Drag the pin or tap the map to mark the exact delivery point.</div>
              </div>

            <MapLocationPicker
              initialLocation={mapLocation}
              onLocationChange={handleMapPositionChange}
              onLoadError={setMapStatus}
            />

            <div className="customer-map-bottom-sheet">
              <div className="customer-map-place">
                <span className="customer-map-place__pin" aria-hidden="true">●</span>
                <div>
                  <strong>{selectedPlaceName || 'Selected delivery point'}</strong>
                  <p>{mapStatus || 'Check that the pin is on the correct building or entrance.'}</p>
                  {mapAccuracy !== null && <small>GPS accuracy approximately ±{Math.round(mapAccuracy)} m</small>}
                  <small>{mapLocation.lat.toFixed(6)}, {mapLocation.lng.toFixed(6)}</small>
                </div>
              </div>
              <div className="customer-map-actions">
                <button type="button" className="customer-map-current" onClick={requestDeviceLocation} disabled={locating}>
                  <span aria-hidden="true">⌖</span>{locating ? 'Locating…' : 'Current location'}
                </button>
                <button type="button" className="customer-map-confirm" onClick={() => void confirmMapLocation()} disabled={confirmingMapLocation || !mapLocationReady}>
                  {confirmingMapLocation ? 'Confirming…' : 'Confirm & proceed'} <span aria-hidden="true">→</span>
                </button>
              </div>
              {!mapLocationReady && <small className="customer-checkout-required-note">Allow location, search your address, or move the map pin before confirming.</small>}
              <small className="customer-map-attribution-note">Map data © OpenStreetMap contributors</small>
            </div>
          </section>
        </div>
      )}
    </section>
  )
}
