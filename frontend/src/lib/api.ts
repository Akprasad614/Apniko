const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

function getMessage(payload: unknown, fallback: string): string {
  if (typeof payload === 'string' && payload.trim()) return payload
  if (payload && typeof payload === 'object') {
    const record = payload as Record<string, unknown>
    for (const key of ['message', 'detail', 'error']) {
      const value = record[key]
      if (typeof value === 'string' && value.trim()) return value
    }
    const validationMessage = Object.values(record).find(
      (value): value is string => typeof value === 'string' && value.trim().length > 0,
    )
    if (validationMessage) return validationMessage
  }
  return fallback
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
  token?: string,
): Promise<T> {
  const headers = new Headers(options.headers)
  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  headers.set('Accept', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers,
    })
  } catch {
    throw new ApiError('Could not reach Apniko. Check that the backend is running.', 0)
  }

  const rawBody = await response.text()
  let payload: unknown = rawBody
  if (rawBody) {
    try {
      payload = JSON.parse(rawBody) as unknown
    } catch {
      payload = rawBody
    }
  }

  if (!response.ok) {
    throw new ApiError(
      getMessage(payload, `Apniko request failed (${response.status}).`),
      response.status,
    )
  }

  if (response.status === 204 || !rawBody.trim()) return undefined as T
  return payload as T
}
