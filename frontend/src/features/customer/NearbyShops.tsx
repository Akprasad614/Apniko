import { useState } from 'react'
import { ApiError, apiRequest } from '../../lib/api'
import type { MarketplaceShop } from './marketplace'
import './nearby-shops.css'

interface NearbyShopsProps {
  token: string
  onShopSelect: (shopId: number) => void
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error) return error.message
  return 'Nearby shops could not be loaded. Please try again.'
}

export function NearbyShops({ token, onShopSelect }: NearbyShopsProps) {
  const [radiusKm, setRadiusKm] = useState('10')
  const [shops, setShops] = useState<MarketplaceShop[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  function findNearby() {
    setError('')
    if (!navigator.geolocation) {
      setError('Location access is not available in this browser. You can still choose a shop from the full list.')
      return
    }
    setLoading(true)
    navigator.geolocation.getCurrentPosition(
      async (position) => {
        try {
          const result = await apiRequest<MarketplaceShop[]>(
            '/api/customer/marketplace/shops/nearby',
            {
              method: 'POST',
              body: JSON.stringify({
                latitude: position.coords.latitude,
                longitude: position.coords.longitude,
                radiusKm: Number(radiusKm),
              }),
            },
            token,
          )
          setShops(result)
        } catch (searchError) {
          setError(errorMessage(searchError))
          setShops(null)
        } finally {
          setLoading(false)
        }
      },
      (locationError) => {
        setLoading(false)
        setError(locationError.code === locationError.PERMISSION_DENIED
          ? 'Location permission was not granted. You can still choose a shop from the full list.'
          : 'Your location could not be determined. Try again or choose a shop from the full list.')
      },
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 60000 },
    )
  }

  return (
    <section className="nearby-shops" aria-labelledby="nearby-shops-title">
      <div className="nearby-shops__heading">
        <div>
          <p className="customer-eyebrow">AROUND YOUR AREA</p>
          <h2 id="nearby-shops-title">Find shops near you</h2>
          <p>Your location is used for this search only and is not saved to your Apniko account.</p>
        </div>
        <div className="nearby-shops__controls">
          <label htmlFor="nearby-radius">Within</label>
          <select id="nearby-radius" value={radiusKm} onChange={(event) => setRadiusKm(event.target.value)}>
            <option value="2">2 km</option>
            <option value="5">5 km</option>
            <option value="10">10 km</option>
            <option value="20">20 km</option>
            <option value="50">50 km</option>
          </select>
          <button type="button" onClick={findNearby} disabled={loading}>
            {loading ? 'Finding…' : 'Use my location'}
          </button>
        </div>
      </div>
      {error && <p className="nearby-shops__message nearby-shops__message--error" role="alert">{error}</p>}
      {loading && <p className="nearby-shops__message" role="status">Checking shops around your location…</p>}
      {shops && !loading && (
        shops.length ? (
          <div className="nearby-shops__list" aria-live="polite">
            {shops.map((shop) => (
              <button type="button" className="nearby-shops__item" key={shop.id}
                onClick={() => onShopSelect(shop.id)}>
                <span className="nearby-shops__mark" aria-hidden="true">⌂</span>
                <span className="nearby-shops__details">
                  <strong>{shop.name}</strong>
                  <small>{shop.address || 'Neighbourhood shop'}</small>
                </span>
                <span className="nearby-shops__distance">
                  <strong>{shop.distanceKm == null ? 'Nearby' : shop.distanceKm.toFixed(1) + ' km'}</strong>
                  <small>Shop details</small>
                </span>
              </button>
            ))}
          </div>
        ) : (
          <p className="nearby-shops__message">No active shops with verified map pins were found within {radiusKm} km. Try a wider radius or choose from all shops.</p>
        )
      )}
    </section>
  )
}
