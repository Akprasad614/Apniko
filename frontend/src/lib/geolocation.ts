export interface Coordinates {
  latitude: number
  longitude: number
}

export function getCurrentCoordinates(): Promise<Coordinates> {
  if (!navigator.geolocation) {
    return Promise.reject(new Error('This browser does not support location access.'))
  }

  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (position) => resolve({
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
      }),
      (error) => {
        const message = error.code === error.PERMISSION_DENIED
          ? 'Location permission was denied. You can still browse all shops.'
          : error.code === error.TIMEOUT
            ? 'Location lookup timed out. Please try again.'
            : 'Your location is unavailable right now. Please try again.'
        reject(new Error(message))
      },
      {
        enableHighAccuracy: false,
        timeout: 12000,
        maximumAge: 300000,
      },
    )
  })
}
