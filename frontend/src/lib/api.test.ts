import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiRequest } from './api'

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('apiRequest', () => {
  it('sends JSON headers and the current bearer token', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ saved: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const result = await apiRequest<{ saved: boolean }>(
      '/api/orders',
      { method: 'POST', body: JSON.stringify({ shopId: 12 }) },
      'jwt-token',
    )

    expect(result).toEqual({ saved: true })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const request = fetchMock.mock.calls[0]?.[1]
    const headers = new Headers(request?.headers)
    expect(headers.get('Accept')).toBe('application/json')
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(headers.get('Authorization')).toBe('Bearer jwt-token')
  })

  it('returns undefined for a successful no-content response', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(
      new Response(null, { status: 204 }),
    ))

    await expect(apiRequest<void>('/api/notifications/1', { method: 'DELETE' }))
      .resolves.toBeUndefined()
  })

  it('surfaces backend validation messages', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ email: 'Enter a valid email address.' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      }),
    ))

    await expect(apiRequest('/api/auth/register', { method: 'POST', body: '{}' }))
      .rejects.toMatchObject({
        name: 'ApiError',
        status: 400,
        message: 'Enter a valid email address.',
      })
  })

  it('uses a clear message when the backend cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockRejectedValue(new Error('offline')))

    await expect(apiRequest('/api/customer/marketplace/shops'))
      .rejects.toMatchObject({
        name: 'ApiError',
        status: 0,
        message: 'Could not reach Apniko. Check that the backend is running.',
      })
  })

  it('keeps the HTTP status when the backend returns an error', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ error: 'Access denied' }), { status: 403 }),
    ))

    try {
      await apiRequest('/api/admin/users')
      throw new Error('Expected request to fail')
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      expect(error).toMatchObject({ status: 403, message: 'Access denied' })
    }
  })
})
