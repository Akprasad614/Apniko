const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
const COOKIE_AUTH_ENABLED = import.meta.env.VITE_AUTH_COOKIE_MODE !== 'false'
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

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

async function getCsrfToken(): Promise<string> {
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}/api/auth/csrf`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
  } catch {
    throw new ApiError('Could not establish a secure Apniko session. Check your connection.', 0)
  }

  if (!response.ok) {
    throw new ApiError('Could not establish a secure Apniko session.', response.status)
  }

  const payload: unknown = await response.json()
  if (
    !payload ||
    typeof payload !== 'object' ||
    typeof (payload as Record<string, unknown>).token !== 'string'
  ) {
    throw new ApiError('Apniko did not return a valid request-verification token.', response.status)
  }

  return (payload as { token: string }).token
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
  token?: string,
): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase()
  const headers = new Headers(options.headers)

  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  headers.set('Accept', 'application/json')

  if (token) headers.set('Authorization', `Bearer ${token}`)

  if (COOKIE_AUTH_ENABLED && !SAFE_METHODS.has(method)) {
    headers.set('X-XSRF-TOKEN', await getCsrfToken())
  }

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      method,
      credentials: COOKIE_AUTH_ENABLED ? 'include' : 'same-origin',
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
