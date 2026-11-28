import { afterEach, describe, expect, it, vi } from 'vitest'
import { onRequest } from './cloudflare-api-proxy'

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('Cloudflare Pages API proxy', () => {
  it('forwards the request and preserves separate authentication cookies', async () => {
    const upstreamHeaders = new Headers({ 'Content-Type': 'application/json' })
    upstreamHeaders.append(
      'Set-Cookie',
      'APNIKO_SESSION=session-value; Path=/; HttpOnly; Secure; SameSite=Lax',
    )
    upstreamHeaders.append(
      'Set-Cookie',
      'XSRF-TOKEN=csrf-value; Path=/; Secure; SameSite=Lax',
    )

    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ token: 'csrf-value' }), {
        status: 200,
        headers: upstreamHeaders,
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const request = new Request(
      'https://apniko-demo.pages.dev/api/auth/csrf?continue=%2Fcustomer%2Fhome',
      {
        headers: {
          Accept: 'application/json',
          Cookie: 'APNIKO_SESSION=old-session',
          'X-XSRF-TOKEN': 'old-csrf',
        },
      },
    )

    const response = await onRequest({
      request,
      env: { APNIKO_API_ORIGIN: 'https://apniko-api.onrender.com' },
    })

    expect(response.status).toBe(200)
    expect(await response.json()).toEqual({ token: 'csrf-value' })
    expect(fetchMock).toHaveBeenCalledTimes(1)

    const forwardedRequest = fetchMock.mock.calls[0]?.[0] as Request
    expect(forwardedRequest.url).toBe(
      'https://apniko-api.onrender.com/api/auth/csrf?continue=%2Fcustomer%2Fhome',
    )
    expect(forwardedRequest.headers.get('Cookie')).toBe('APNIKO_SESSION=old-session')
    expect(forwardedRequest.headers.get('X-XSRF-TOKEN')).toBe('old-csrf')

    const responseCookieHeaders = response.headers as Headers & {
      getSetCookie?: () => string[]
    }
    expect(responseCookieHeaders.getSetCookie?.()).toEqual([
      'APNIKO_SESSION=session-value; Path=/; HttpOnly; Secure; SameSite=Lax',
      'XSRF-TOKEN=csrf-value; Path=/; Secure; SameSite=Lax',
    ])
    expect(response.headers.get('Cache-Control')).toBe('private, no-store')
  })

  it('forwards POST bodies and CSRF headers without changing the browser URL', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ authenticated: true }), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const request = new Request('https://apniko-demo.pages.dev/api/auth/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': 'csrf-token',
      },
      body: JSON.stringify({ email: 'demo@example.test', password: 'not-a-real-password' }),
    })

    const response = await onRequest({
      request,
      env: { APNIKO_API_ORIGIN: 'https://apniko-api.onrender.com' },
    })

    expect(response.status).toBe(200)
    const forwardedRequest = fetchMock.mock.calls[0]?.[0] as Request
    expect(forwardedRequest.method).toBe('POST')
    expect(forwardedRequest.headers.get('X-XSRF-TOKEN')).toBe('csrf-token')
    expect(await forwardedRequest.text()).toBe(
      JSON.stringify({ email: 'demo@example.test', password: 'not-a-real-password' }),
    )
  })

  it('rejects a non-HTTPS API origin', async () => {
    const fetchMock = vi.fn<typeof fetch>()
    vi.stubGlobal('fetch', fetchMock)

    const response = await onRequest({
      request: new Request('https://apniko-demo.pages.dev/api/auth/csrf'),
      env: { APNIKO_API_ORIGIN: 'http://apniko-api.onrender.com' },
    })

    expect(response.status).toBe(500)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('returns a clear setup error when the API origin is missing', async () => {
    const response = await onRequest({
      request: new Request('https://apniko-demo.pages.dev/api/auth/csrf'),
      env: {},
    })

    expect(response.status).toBe(503)
    expect(response.headers.get('Cache-Control')).toBe('no-store')
  })
})
