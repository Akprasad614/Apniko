import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { apiRequest } from '../lib/api'
import { isAppRole } from '../lib/roles'
import type { AppRole } from '../lib/roles'

export interface AuthSession {
  // Present only in the isolated bearer-token compatibility mode used by API tests.
  // Production browser sessions use an HttpOnly cookie and never expose the JWT to JS.
  token?: string
  userId: number
  name: string
  email: string
  role: AppRole
}

interface AuthResponse {
  token?: string | null
  userId: number
  name: string
  email: string
  role: string
}

interface AuthContextValue {
  session: AuthSession | null
  loading: boolean
  signIn: (email: string, password: string) => Promise<AuthSession>
  signUpCustomer: (name: string, email: string, password: string) => Promise<AuthSession>
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

function toSession(response: AuthResponse): AuthSession {
  if (!isAppRole(response.role)) {
    throw new Error('Apniko returned an unknown account role. Contact support.')
  }

  return {
    ...(response.token ? { token: response.token } : {}),
    userId: response.userId,
    name: response.name,
    email: response.email,
    role: response.role,
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true

    apiRequest<AuthResponse>('/api/auth/session')
      .then((response) => {
        if (active) setSession(toSession(response))
      })
      .catch(() => {
        // A missing/expired browser cookie is an ordinary signed-out state.
        if (active) setSession(null)
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [])

  const signIn = useCallback(async (email: string, password: string): Promise<AuthSession> => {
    const response = await apiRequest<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    const nextSession = toSession(response)
    setSession(nextSession)
    return nextSession
  }, [])

  const signUpCustomer = useCallback(async (
    name: string,
    email: string,
    password: string,
  ): Promise<AuthSession> => {
    const response = await apiRequest<AuthResponse>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ name, email, password, role: 'CUSTOMER' }),
    })
    const nextSession = toSession(response)
    setSession(nextSession)
    return nextSession
  }, [])

  const signOut = useCallback(async () => {
    try {
      await apiRequest<void>('/api/auth/logout', { method: 'POST' }, session?.token)
    } finally {
      setSession(null)
    }
  }, [session?.token])

  const value = useMemo(
    () => ({ session, loading, signIn, signUpCustomer, signOut }),
    [session, loading, signIn, signUpCustomer, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
