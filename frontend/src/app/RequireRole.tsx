import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useAuth } from '../auth/AuthProvider'
import { roleHomePath } from '../lib/roles'
import type { AppRole } from '../lib/roles'

export function RequireRole({ allowed, children }: { allowed: AppRole[]; children: ReactNode }) {
  const { session } = useAuth()
  const location = useLocation()

  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (!allowed.includes(session.role)) {
    return <Navigate to={roleHomePath(session.role)} replace />
  }
  return children
}
