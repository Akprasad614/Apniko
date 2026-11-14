import { useEffect, useRef, useState } from 'react'

export interface MapPoint {
  lat: number
  lng: number
}

type LeafletIcon = object
interface LeafletLayer {
  addTo(map: LeafletMap): LeafletLayer
}
interface LeafletMarker {
  addTo(map: LeafletMap): LeafletMarker
  setLatLng(position: [number, number]): LeafletMarker
  getLatLng(): MapPoint
  on(event: 'dragend', handler: () => void): LeafletMarker
}
interface LeafletClickEvent {
  latlng: MapPoint
}
interface LeafletMap {
  setView(center: [number, number], zoom?: number): LeafletMap
  getZoom(): number
  on(event: 'click', handler: (event: LeafletClickEvent) => void): LeafletMap
  invalidateSize(): LeafletMap
  remove(): void
}
interface LeafletNamespace {
  map(element: HTMLElement, options?: Record<string, unknown>): LeafletMap
  tileLayer(url: string, options: { attribution: string; maxZoom: number }): LeafletLayer
  marker(position: [number, number], options: { icon: LeafletIcon; draggable: boolean }): LeafletMarker
  divIcon(options: {
    className: string
    html: string
    iconSize: [number, number]
    iconAnchor: [number, number]
  }): LeafletIcon
}

declare global {
  interface Window {
    L?: LeafletNamespace
  }
}

interface MapLocationPickerProps {
  initialLocation: MapPoint
  onLocationChange: (point: MapPoint) => void
  onLoadError: (message: string) => void
}

const LEAFLET_VERSION = '1.9.4'
const LEAFLET_SCRIPT_ID = 'apniko-leaflet-script'
const LEAFLET_STYLE_ID = 'apniko-leaflet-style'

function loadLeaflet(): Promise<LeafletNamespace> {
  if (window.L) return Promise.resolve(window.L)

  if (!document.getElementById(LEAFLET_STYLE_ID)) {
    const style = document.createElement('link')
    style.id = LEAFLET_STYLE_ID
    style.rel = 'stylesheet'
    style.href = 'https://unpkg.com/leaflet@' + LEAFLET_VERSION + '/dist/leaflet.css'
    document.head.appendChild(style)
  }

  return new Promise((resolve, reject) => {
    const existing = document.getElementById(LEAFLET_SCRIPT_ID) as HTMLScriptElement | null
    if (existing) {
      existing.addEventListener('load', () => {
        if (window.L) resolve(window.L)
        else reject(new Error('The map library did not finish loading.'))
      }, { once: true })
      existing.addEventListener('error', () => reject(new Error('The map library could not be loaded.')), { once: true })
      return
    }

    const script = document.createElement('script')
    script.id = LEAFLET_SCRIPT_ID
    script.src = 'https://unpkg.com/leaflet@' + LEAFLET_VERSION + '/dist/leaflet.js'
    script.async = true
    script.onload = () => {
      if (window.L) resolve(window.L)
      else reject(new Error('The map library did not finish loading.'))
    }
    script.onerror = () => reject(new Error('The map library could not be loaded.'))
    document.body.appendChild(script)
  })
}

const MAP_TILE_URL = import.meta.env.VITE_MAP_TILE_URL
  || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'
const MAP_ATTRIBUTION = import.meta.env.VITE_MAP_ATTRIBUTION
  || '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap contributors</a>'

export function MapLocationPicker({
  initialLocation,
  onLocationChange,
  onLoadError,
}: MapLocationPickerProps) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const mapRef = useRef<LeafletMap | null>(null)
  const markerRef = useRef<LeafletMarker | null>(null)
  const onLocationChangeRef = useRef(onLocationChange)
  const [mapReady, setMapReady] = useState(false)
  const initialLocationRef = useRef(initialLocation)
  const onLoadErrorRef = useRef(onLoadError)

  useEffect(() => {
    onLocationChangeRef.current = onLocationChange
  }, [onLocationChange])

  useEffect(() => {
    initialLocationRef.current = initialLocation
  }, [initialLocation.lat, initialLocation.lng])

  useEffect(() => {
    onLoadErrorRef.current = onLoadError
  }, [onLoadError])

  useEffect(() => {
    let cancelled = false
    let map: LeafletMap | null = null
    let invalidateTimer: number | undefined

    void loadLeaflet()
      .then((leaflet) => {
        if (cancelled || !containerRef.current) return

        map = leaflet.map(containerRef.current, {
          center: [initialLocationRef.current.lat, initialLocationRef.current.lng],
          zoom: 17,
          zoomControl: true,
          scrollWheelZoom: true,
        })

        leaflet.tileLayer(MAP_TILE_URL, {
          attribution: MAP_ATTRIBUTION,
          maxZoom: 19,
        }).addTo(map)

        const marker = leaflet.marker(
          [initialLocationRef.current.lat, initialLocationRef.current.lng],
          {
            draggable: true,
            icon: leaflet.divIcon({
              className: 'apniko-leaflet-marker',
              html: '<span class="apniko-leaflet-marker__halo"></span><span class="apniko-leaflet-marker__pin"><span></span></span>',
              iconSize: [44, 56],
              iconAnchor: [22, 34],
            }),
          },
        ).addTo(map)

        const reportMarkerPosition = () => {
          const position = marker.getLatLng()
          onLocationChangeRef.current({ lat: position.lat, lng: position.lng })
        }

        marker.on('dragend', reportMarkerPosition)
        map.on('click', (event) => {
          marker.setLatLng([event.latlng.lat, event.latlng.lng])
          reportMarkerPosition()
        })

        mapRef.current = map
        markerRef.current = marker
        setMapReady(true)
        invalidateTimer = window.setTimeout(() => map?.invalidateSize(), 120)
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'The map could not be loaded.'
        if (!cancelled) onLoadErrorRef.current(message)
      })

    return () => {
      cancelled = true
      if (invalidateTimer !== undefined) window.clearTimeout(invalidateTimer)
      map?.remove()
      mapRef.current = null
      markerRef.current = null
      
    }
  }, [])

  useEffect(() => {
    const map = mapRef.current
    const marker = markerRef.current
    if (!map || !marker) return

    marker.setLatLng([initialLocation.lat, initialLocation.lng])
    map.setView([initialLocation.lat, initialLocation.lng], map.getZoom())
  }, [initialLocation.lat, initialLocation.lng])

  return (
    <div className="customer-map-frame">
      <div
        ref={containerRef}
        className="customer-map-canvas"
        aria-label="Map showing your selected delivery point. Drag the pin or tap the map to adjust it."
      />
      {!mapReady && <div className="customer-map-loading" role="status">Loading map…</div>}
    </div>
  )
}
