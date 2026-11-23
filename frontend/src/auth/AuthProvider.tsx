import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { apiRequest } from '../lib/api'
import { isAppRole } from '../lib/roles'
import type { AppRole } from '../lib/roles'

const SESSION_KEY = 'apniko.auth.session'

export interface AuthSession {
  token: string
  userId: number
  name: string
  email: string
  role: AppRole
}

interface AuthResponse {
  token: string
  userId: number
  name: string
  email: string
  role: string
}

interface AuthContextValue {
  session: AuthSession | null
  signIn: (email: string, password: string) => Promise<AuthSession>
  signUpCustomer: (name: string, email: string, password: string) => Promise<AuthSession>
  signOut: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

function readStoredSession(): AuthSession | null {
  try {
    const raw = window.sessionStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object') return null
    const candidate = parsed as Partial<AuthSession>
    if (
      typeof candidate.token !== 'string' ||
      typeof candidate.userId !== 'number' ||
      typeof candidate.name !== 'string' ||
      typeof candidate.email !== 'string' ||
      !isAppRole(candidate.role)
    ) return null
    return candidate as AuthSession
  } catch {
    return null
  }
}

function saveSession(response: AuthResponse): AuthSession {
  if (!isAppRole(response.role)) {
    throw new Error('Apniko returned an unknown account role. Contact support.')
  }
  const session: AuthSession = {
    token: response.token,
    userId: response.userId,
    name: response.name,
    email: response.email,
    role: response.role,
  }
  window.sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
  return session
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(readStoredSession)

  const signIn = useCallback(async (email: string, password: string): Promise<AuthSession> => {
    const response = await apiRequest<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    const nextSession = saveSession(response)
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
    const nextSession = saveSession(response)
    setSession(nextSession)
    return nextSession
  }, [])

  const signOut = useCallback(() => {
    window.sessionStorage.removeItem(SESSION_KEY)
    setSession(null)
  }, [])

  const value = useMemo(
    () => ({ session, signIn, signUpCustomer, signOut }),
    [session, signIn, signUpCustomer, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
