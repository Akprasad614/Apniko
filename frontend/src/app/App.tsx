import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AuthProvider, useAuth } from '../auth/AuthProvider'
import { RequireRole } from './RequireRole'
import { HomePage } from '../pages/HomePage'
import { LoginPage } from '../pages/LoginPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { RegisterPage } from '../pages/RegisterPage'
import { CustomerStorefront } from '../features/customer/CustomerStorefront'
import { CustomerShell } from '../features/customer/CustomerShell'
import { CustomerCart } from '../features/customer/CustomerCart'
import { CustomerOrders } from '../features/customer/CustomerOrders'
import { CustomerAccount } from '../features/customer/CustomerAccount'
import { CustomerAddresses } from '../features/customer/CustomerAddresses'
import { CustomerFeaturePage } from '../features/customer/CustomerFeaturePage'
import { ShopkeeperDashboard } from '../features/shopkeeper/ShopkeeperDashboard'
import { AdminDashboard } from '../features/admin/AdminDashboard'
import { DeliveryDashboard } from '../features/delivery/DeliveryDashboard'
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
          <Route path="/customer" element={<RequireRole allowed={['CUSTOMER']}><CustomerShell /></RequireRole>}>
            <Route index element={<Navigate to="home" replace />} />
            <Route path="home" element={<CustomerStorefront />} />
            <Route path="categories" element={<CustomerStorefront view="categories" />} />
            <Route path="cart" element={<CustomerCart />} />
            <Route path="orders" element={<CustomerOrders refreshKey={0} />} />
            <Route path="account" element={<CustomerAccount />} />
            <Route path="account/addresses" element={<CustomerAddresses />} />
            <Route path="account/payment-methods" element={<CustomerFeaturePage feature="payment-methods" />} />
          </Route>
          <Route path="/shopkeeper/*" element={<RequireRole allowed={['SHOPKEEPER']}><ShopkeeperDashboard /></RequireRole>} />
          <Route path="/delivery/*" element={<RequireRole allowed={['DELIVERY_PARTNER']}><DeliveryDashboard /></RequireRole>} />
          <Route path="/admin/*" element={<RequireRole allowed={['ADMIN']}><AdminDashboard /></RequireRole>} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
