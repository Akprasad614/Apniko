type ProxyEnvironment = {
  APNIKO_API_ORIGIN?: string
}

type PagesRequestContext = {
  request: Request
  env: ProxyEnvironment
}

type CookieAwareHeaders = Headers & {
  getAll?: (name: string) => string[]
  getSetCookie?: () => string[]
}

/**
 * Proxies browser /api requests to the private implementation origin.
 *
 * The browser always talks to the Pages origin. This keeps the API, HttpOnly
 * authentication cookie and CSRF cookie on one browser origin, while the
 * backend remains on a separate Render service.
 */
export async function onRequest({ request, env }: PagesRequestContext): Promise<Response> {
  const configuredOrigin = env.APNIKO_API_ORIGIN?.trim()
  if (!configuredOrigin) {
    return new Response('Apniko API origin is not configured.', {
      status: 503,
      headers: { 'Cache-Control': 'no-store' },
    })
  }

  let apiOrigin: URL
  try {
    apiOrigin = new URL(configuredOrigin)
  } catch {
    return new Response('Apniko API origin is invalid.', {
      status: 500,
      headers: { 'Cache-Control': 'no-store' },
    })
  }

  // Never allow this function to become an arbitrary URL or open proxy.
  if (
    apiOrigin.protocol !== 'https:' ||
    apiOrigin.username ||
    apiOrigin.password ||
    apiOrigin.pathname !== '/' ||
    apiOrigin.search ||
    apiOrigin.hash
  ) {
    return new Response('Apniko API origin must be a clean HTTPS origin.', {
      status: 500,
      headers: { 'Cache-Control': 'no-store' },
    })
  }

  const incomingUrl = new URL(request.url)
  if (incomingUrl.pathname !== '/api' && !incomingUrl.pathname.startsWith('/api/')) {
    return new Response('Not found.', { status: 404 })
  }

  const targetUrl = new URL(
    `${incomingUrl.pathname}${incomingUrl.search}`,
    apiOrigin,
  )

  let upstreamResponse: Response
  try {
    // Copy the original method, body, cookies, CSRF header and authorization
    // headers. The URL changes only on the server side.
    upstreamResponse = await fetch(new Request(targetUrl.toString(), request))
  } catch {
    return new Response('Apniko API is temporarily unavailable.', {
      status: 502,
      headers: { 'Cache-Control': 'no-store' },
    })
  }

  const responseHeaders = new Headers(upstreamResponse.headers)
  const cookieHeaders = upstreamResponse.headers as CookieAwareHeaders
  const setCookies = cookieHeaders.getSetCookie?.()
    ?? cookieHeaders.getAll?.('Set-Cookie')
    ?? []

  // Cloudflare exposes multiple Set-Cookie values through getAll(). Preserve
  // them individually so the browser stores both the auth and CSRF cookies.
  responseHeaders.delete('Set-Cookie')
  for (const cookie of setCookies) {
    responseHeaders.append('Set-Cookie', cookie)
  }

  // Authentication and customer data must never be cached by the edge.
  responseHeaders.set('Cache-Control', 'private, no-store')

  return new Response(upstreamResponse.body, {
    status: upstreamResponse.status,
    statusText: upstreamResponse.statusText,
    headers: responseHeaders,
  })
}
