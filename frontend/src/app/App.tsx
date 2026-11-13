import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AuthProvider, useAuth } from '../auth/AuthProvider'
import { RequireRole } from './RequireRole'
import { HomePage } from '../pages/HomePage'
import { LoginPage } from '../pages/LoginPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { PortalPage } from '../pages/PortalPage'
import { RegisterPage } from '../pages/RegisterPage'
import { CustomerStorefront } from '../features/customer/CustomerStorefront'
import { ShopkeeperDashboard } from '../features/shopkeeper/ShopkeeperDashboard'
import { roleHomePath } from '../lib/roles'

function AccountRedirect() {
  const { session } = useAuth()
  return <Navigate to={session ? roleHomePath(session.role) : '/'} replace />
}

export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/account" element={<AccountRedirect />} />
          <Route path="/customer/*" element={<RequireRole allowed={['CUSTOMER']}><CustomerStorefront /></RequireRole>} />
          <Route path="/shopkeeper/*" element={<RequireRole allowed={['SHOPKEEPER']}><ShopkeeperDashboard /></RequireRole>} />
          <Route path="/delivery/*" element={<RequireRole allowed={['DELIVERY_PARTNER']}><PortalPage /></RequireRole>} />
          <Route path="/admin/*" element={<RequireRole allowed={['ADMIN']}><PortalPage /></RequireRole>} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
