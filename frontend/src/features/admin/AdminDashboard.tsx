import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../../auth/AuthProvider'
import { Button } from '../../components/Button'
import { Wordmark } from '../../components/Wordmark'
import { ApiError, apiRequest } from '../../lib/api'
import { CatalogueReviewQueue } from './CatalogueReviewQueue'
import '../shared/operations.css'
import { NotificationCenter } from '../shared/NotificationCenter'

type StaffRole = 'SHOPKEEPER' | 'DELIVERY_PARTNER'
type AdminTab = 'overview' | 'people' | 'shops' | 'catalogue'

interface AdminUser {
  id: number
  name: string
  email: string
  role: 'CUSTOMER' | 'SHOPKEEPER' | 'DELIVERY_PARTNER' | 'ADMIN'
  active: boolean
}

interface AdminShop {
  id: number
  name: string
  ownerId: number | null
  ownerName?: string | null
  ownerEmail?: string | null
  phone?: string | null
  address?: string | null
  active: boolean
  latitude?: number | null
  longitude?: number | null
}

interface AdminProduct {
  id: number
  name: string
  category?: string | null
  subcategory?: string | null
  brand?: string | null
  barcode?: string | null
  packSize?: string | null
  description?: string | null
  mrp?: number | null
  mrpSource?: string | null
}

interface AdminCategory {
  id: number
  name: string
  slug: string
  subcategories: Array<{ id: number; name: string; slug: string }>
}

interface AdminOverview {
  from?: string
  to?: string
  totalOrders: number
  deliveredOrders: number
  cancelledOrders: number
  openOrders: number
  customersWithOrders: number
  unitsInNonCancelledOrders: number
  grossOrderValue: number
  nonCancelledOrderValue: number
  deliveredOrderValue: number
  averageNonCancelledOrderValue: number
  shopsCovered: number
}

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Your session has expired. Sign in again.'
    if (error.status === 403) return 'This account cannot access administration.'
    return error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

function formatRupees(value: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(value ?? 0)
}

function formatRole(role: string): string {
  return role.toLowerCase().replaceAll('_', ' ')
}

export function AdminDashboard() {
  const { session, signOut } = useAuth()
  const token = session?.token ?? ''
  const [tab, setTab] = useState<AdminTab>('overview')
  const [overview, setOverview] = useState<AdminOverview | null>(null)
  const [users, setUsers] = useState<AdminUser[]>([])
  const [shops, setShops] = useState<AdminShop[]>([])
  const [shopLocationDrafts, setShopLocationDrafts] = useState<Record<number, { latitude: string; longitude: string }>>({})
  const [categories, setCategories] = useState<AdminCategory[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busyKey, setBusyKey] = useState('')
  const [staffRole, setStaffRole] = useState<StaffRole>('SHOPKEEPER')
  const [staffName, setStaffName] = useState('')
  const [staffEmail, setStaffEmail] = useState('')
  const [staffPassword, setStaffPassword] = useState('')
  const [roleFilter, setRoleFilter] = useState('ALL')
  const [productName, setProductName] = useState('')
  const [productCategory, setProductCategory] = useState('')
  const [productSubcategory, setProductSubcategory] = useState('')
  const [productBrand, setProductBrand] = useState('')
  const [productBarcode, setProductBarcode] = useState('')
  const [productPackSize, setProductPackSize] = useState('')
  const [productDescription, setProductDescription] = useState('')
  const [productMrp, setProductMrp] = useState('')
  const [productMrpSource, setProductMrpSource] = useState('')

  const shopkeepers = useMemo(
    () => users.filter((user) => user.role === 'SHOPKEEPER' && user.active),
    [users],
  )
  const visibleUsers = useMemo(
    () => roleFilter === 'ALL' ? users : users.filter((user) => user.role === roleFilter),
    [roleFilter, users],
  )

  const loadData = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true)
    setError('')
    const results = await Promise.allSettled([
      apiRequest<AdminOverview>('/api/admin/analytics/overview', {}, token),
      apiRequest<AdminUser[]>('/api/admin/users', {}, token),
      apiRequest<AdminShop[]>('/api/admin/shops', {}, token),
      apiRequest<AdminCategory[]>('/api/catalogue/categories', {}, token),
    ] as const)
    const failures: string[] = []
    if (results[0].status === 'fulfilled') setOverview(results[0].value)
    else failures.push(messageFor(results[0].reason))
    if (results[1].status === 'fulfilled') setUsers(results[1].value)
    else failures.push(messageFor(results[1].reason))
    if (results[2].status === 'fulfilled') {
      setShops(results[2].value)
      setShopLocationDrafts((current) => Object.fromEntries(results[2].value.map((shop) => [
        shop.id,
        current[shop.id] ?? {
          latitude: shop.latitude == null ? '' : String(shop.latitude),
          longitude: shop.longitude == null ? '' : String(shop.longitude),
        },
      ])))
    } else failures.push(messageFor(results[2].reason))
    if (results[3].status === 'fulfilled') setCategories(results[3].value)
    else failures.push(messageFor(results[3].reason))
    if (failures.length) setError(failures.join(' '))
    if (!quiet) setLoading(false)
  }, [token])

  useEffect(() => {
    void loadData()
  }, [loadData])

  async function createStaff(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const roleLabel = staffRole === 'SHOPKEEPER' ? 'shopkeeper' : 'delivery partner'
    setBusyKey('create-staff')
    setError('')
    setNotice('')
    try {
      const created = await apiRequest<AdminUser>(
        staffRole === 'SHOPKEEPER' ? '/api/admin/users/shopkeepers' : '/api/admin/users/delivery-partners',
        {
          method: 'POST',
          body: JSON.stringify({
            name: staffName.trim(),
            email: staffEmail.trim().toLowerCase(),
            password: staffPassword,
          }),
        },
        token,
      )
      setUsers((current) => [...current, created])
      setStaffName('')
      setStaffEmail('')
      setStaffPassword('')
      setNotice('Created ' + roleLabel + ' account for ' + created.email + '. Share the temporary password securely.')
    } catch (createError) {
      setError(messageFor(createError))
    } finally {
      setBusyKey('')
    }
  }

  async function toggleUser(user: AdminUser) {
    setBusyKey('user-' + user.id)
    setError('')
    setNotice('')
    try {
      const updated = await apiRequest<AdminUser>(
        '/api/admin/users/' + user.id + '/active',
        { method: 'PATCH', body: JSON.stringify({ active: !user.active }) },
        token,
      )
      setUsers((current) => current.map((item) => item.id === updated.id ? updated : item))
      setNotice(updated.name + ' is now ' + (updated.active ? 'active' : 'inactive') + '.')
    } catch (updateError) {
      setError(messageFor(updateError))
    } finally {
      setBusyKey('')
    }
  }

  async function assignOwner(shop: AdminShop, ownerId: string) {
    if (!ownerId) return
    setBusyKey('shop-' + shop.id)
    setError('')
    setNotice('')
    try {
      const updated = await apiRequest<AdminShop>(
        '/api/admin/shops/' + shop.id + '/owner',
        { method: 'PUT', body: JSON.stringify({ ownerId: Number(ownerId) }) },
        token,
      )
      setShops((current) => current.map((item) => item.id === updated.id ? updated : item))
      setNotice(updated.name + ' is assigned to ' + (updated.ownerName || 'the selected shopkeeper') + '.')
    } catch (updateError) {
      setError(messageFor(updateError))
    } finally {
      setBusyKey('')
    }
  }

  function updateShopLocationDraft(shopId: number, field: 'latitude' | 'longitude', value: string) {
    setShopLocationDrafts((current) => ({
      ...current,
      [shopId]: {
        latitude: current[shopId]?.latitude ?? '',
        longitude: current[shopId]?.longitude ?? '',
        [field]: value,
      },
    }))
  }

  async function saveShopLocation(shop: AdminShop) {
    const draft = shopLocationDrafts[shop.id]
    const latitude = Number(draft?.latitude)
    const longitude = Number(draft?.longitude)
    if (!draft || !draft.latitude.trim() || !draft.longitude.trim()
      || !Number.isFinite(latitude) || latitude < -90 || latitude > 90
      || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
      setError('Enter a valid latitude (-90 to 90) and longitude (-180 to 180).')
      return
    }

    setBusyKey('shop-location-' + shop.id)
    setError('')
    setNotice('')
    try {
      const updated = await apiRequest<AdminShop>(
        '/api/admin/shops/' + shop.id + '/location',
        { method: 'PATCH', body: JSON.stringify({ latitude, longitude }) },
        token,
      )
      setShops((current) => current.map((item) => item.id === updated.id ? updated : item))
      setShopLocationDrafts((current) => ({
        ...current,
        [updated.id]: {
          latitude: updated.latitude == null ? '' : String(updated.latitude),
          longitude: updated.longitude == null ? '' : String(updated.longitude),
        },
      }))
      setNotice(updated.name + ' has been pinned for nearby-shop discovery.')
    } catch (locationError) {
      setError(messageFor(locationError))
    } finally {
      setBusyKey('')
    }
  }

  function useCurrentLocationForShop(shop: AdminShop) {
    if (!navigator.geolocation) {
      setError('This browser does not provide location access. Enter the shop coordinates manually.')
      return
    }
    setError('')
    setNotice('')
    setBusyKey('shop-geolocate-' + shop.id)
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setShopLocationDrafts((current) => ({
          ...current,
          [shop.id]: {
            latitude: position.coords.latitude.toFixed(6),
            longitude: position.coords.longitude.toFixed(6),
          },
        }))
        setNotice('Device location captured for ' + shop.name + '. Confirm you are physically at this shop, then save the pin.')
        setBusyKey('')
      },
      (locationError) => {
        setError(locationError.code === locationError.PERMISSION_DENIED
          ? 'Location permission was denied. Enter the shop coordinates manually.'
          : 'The device location could not be read. Try again or enter the shop coordinates manually.')
        setBusyKey('')
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 },
    )
  }

  async function toggleShop(shop: AdminShop) {
    setBusyKey('shop-' + shop.id)
    setError('')
    setNotice('')
    try {
      const updated = await apiRequest<AdminShop>(
        '/api/admin/shops/' + shop.id + '/active',
        { method: 'PATCH', body: JSON.stringify({ active: !shop.active }) },
        token,
      )
      setShops((current) => current.map((item) => item.id === updated.id ? updated : item))
      setNotice(updated.name + ' is now ' + (updated.active ? 'active' : 'inactive') + '.')
    } catch (updateError) {
      setError(messageFor(updateError))
    } finally {
      setBusyKey('')
    }
  }

  async function createProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusyKey('create-product')
    setError('')
    setNotice('')
    try {
      const created = await apiRequest<AdminProduct>(
        '/api/products',
        {
          method: 'POST',
          body: JSON.stringify({
            name: productName.trim(),
            category: productCategory.trim(),
            subcategory: productSubcategory.trim() || null,
            brand: productBrand.trim() || null,
            barcode: productBarcode.trim() || null,
            packSize: productPackSize.trim() || null,
            description: productDescription.trim() || null,
            mrp: productMrp.trim() ? Number(productMrp) : null,
            mrpSource: productMrpSource || null,
          }),
        },
        token,
      )
      setProductName('')
      setProductCategory('')
      setProductSubcategory('')
      setProductBrand('')
      setProductBarcode('')
      setProductPackSize('')
      setProductDescription('')
      setProductMrp('')
      setProductMrpSource('')
      setNotice(created.name + ' was added to the shared catalogue.')
    } catch (createError) {
      setError(messageFor(createError))
    } finally {
      setBusyKey('')
    }
  }

  const statCards = [
    { label: 'Orders in period', value: overview?.totalOrders ?? '—', foot: 'All recorded order statuses' },
    { label: 'Delivered', value: overview?.deliveredOrders ?? '—', foot: 'Successfully completed' },
    { label: 'Open orders', value: overview?.openOrders ?? '—', foot: 'Still moving through the workflow' },
    { label: 'Active shops covered', value: overview?.shopsCovered ?? '—', foot: 'Shops with activity in the period' },
    { label: 'Delivered sales', value: overview ? formatRupees(overview.deliveredOrderValue) : '—', foot: 'Value of completed orders' },
    { label: 'Customers served', value: overview?.customersWithOrders ?? '—', foot: 'Customers with an order in the period' },
  ]

  return (
    <div className="operations-app">
      <header className="operations-header">
        <div className="operations-shell operations-header__inner">
          <Wordmark />
          <div className="operations-user">
            <span>{session?.name}</span>
            <NotificationCenter />
            <span className="operations-role">ADMIN</span>
            <Button tone="quiet" onClick={signOut}>Sign out</Button>
          </div>
        </div>
      </header>

      <main className="operations-shell operations-main">
        <section className="operations-intro">
          <div>
            <p className="operations-eyebrow">APNIKO OPERATIONS</p>
            <h1>Keep the local<br /><em>loop moving.</em></h1>
            <p>Manage staff access, shop ownership and the shared product catalogue.</p>
          </div>
          <button className="operations-button operations-button--secondary" type="button" disabled={loading} onClick={() => void loadData()}>
            {loading ? 'Refreshing…' : 'Refresh data ↻'}
          </button>
        </section>

        <nav className="operations-tabs" aria-label="Administration sections">
          {([
            ['overview', 'Overview'],
            ['people', 'People'],
            ['shops', 'Shops'],
            ['catalogue', 'Product catalogue'],
          ] as const).map(([key, label]) => (
            <button type="button" key={key} className={tab === key ? 'is-active' : ''} aria-current={tab === key ? 'page' : undefined} onClick={() => { setTab(key); setError(''); setNotice('') }}>
              {label}
            </button>
          ))}
        </nav>

        <nav className="operations-bottom-nav" aria-label="Administration navigation">
          {([
            ['overview', 'Overview', '⌂'],
            ['people', 'People', '♙'],
            ['shops', 'Shops', '⌖'],
            ['catalogue', 'Catalogue', '▦'],
          ] as const).map(([key, label, symbol]) => (
            <button
              key={key}
              type="button"
              className={tab === key ? 'is-active' : ''}
              aria-current={tab === key ? 'page' : undefined}
              onClick={() => { setTab(key); setError(''); setNotice('') }}
            >
              <span className="operations-bottom-nav__symbol" aria-hidden="true">{symbol}</span>
              <span>{label}</span>
            </button>
          ))}
        </nav>

        {error && <div className="operations-alert operations-alert--error" role="alert">{error}</div>}
        {notice && <div className="operations-alert operations-alert--success" role="status">{notice}</div>}

        {loading ? (
          <div className="operations-state" role="status">Loading live administration data…</div>
        ) : (
          <>
            {tab === 'overview' && (
              <section>
                <div className="operations-section-heading">
                  <div><p className="operations-eyebrow">LIVE PLATFORM SNAPSHOT</p><h2>Overview</h2></div>
                  {overview?.from && overview?.to && <span>{overview.from} — {overview.to}</span>}
                </div>
                <div className="operations-stat-grid">
                  {statCards.map((card) => <article className="operations-stat" key={card.label}><span>{card.label}</span><strong>{card.value}</strong><small>{card.foot}</small></article>)}
                </div>
                <div className="operations-overview-columns">
                  <article className="operations-panel">
                    <p className="operations-eyebrow">TEAM</p><h3>People with access</h3>
                    <strong className="operations-panel__metric">{users.length}</strong>
                    <p>Accounts across customers, shopkeepers, delivery partners and administrators.</p>
                    <button className="operations-text-button" type="button" onClick={() => setTab('people')}>Manage people →</button>
                  </article>
                  <article className="operations-panel">
                    <p className="operations-eyebrow">LOCAL NETWORK</p><h3>Registered shops</h3>
                    <strong className="operations-panel__metric">{shops.length}</strong>
                    <p>{shops.filter((shop) => shop.active).length} active · {shops.filter((shop) => !shop.active).length} inactive</p>
                    <button className="operations-text-button" type="button" onClick={() => setTab('shops')}>Manage shops →</button>
                  </article>
                </div>
              </section>
            )}

            {tab === 'people' && (
              <section>
                <div className="operations-section-heading">
                  <div><p className="operations-eyebrow">ACCOUNT ACCESS</p><h2>People</h2><p>Create shopkeeper and delivery accounts; public sign-up remains customer-only.</p></div>
                </div>
                <form className="operations-panel operations-form" onSubmit={(event) => void createStaff(event)}>
                  <div><p className="operations-eyebrow">NEW TEAM ACCOUNT</p><h3>Create an account</h3></div>
                  <div className="operations-form-grid">
                    <label>Account type<select value={staffRole} onChange={(event) => setStaffRole(event.target.value as StaffRole)}><option value="SHOPKEEPER">Shopkeeper</option><option value="DELIVERY_PARTNER">Delivery partner</option></select></label>
                    <label>Full name<input value={staffName} onChange={(event) => setStaffName(event.target.value)} maxLength={100} required autoComplete="name" /></label>
                    <label>Email address<input type="email" value={staffEmail} onChange={(event) => setStaffEmail(event.target.value)} maxLength={150} required autoComplete="email" /></label>
                    <label>Password<input type="password" value={staffPassword} onChange={(event) => setStaffPassword(event.target.value)} minLength={12} maxLength={72} pattern="(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{12,72}" required autoComplete="new-password" /></label>
                  </div>
                  <p className="operations-help">Use 12–72 characters with upper- and lower-case letters, a number and a symbol. Share the password securely.</p>
                  <button className="operations-button" type="submit" disabled={busyKey === 'create-staff' || !staffName.trim() || !staffEmail.trim() || !staffPassword}>{busyKey === 'create-staff' ? 'Creating…' : 'Create ' + (staffRole === 'SHOPKEEPER' ? 'shopkeeper' : 'delivery') + ' account'}</button>
                </form>

                <div className="operations-toolbar">
                  <h3>Account directory <span>{visibleUsers.length}</span></h3>
                  <label>Filter<select value={roleFilter} onChange={(event) => setRoleFilter(event.target.value)}><option value="ALL">All roles</option><option value="CUSTOMER">Customers</option><option value="SHOPKEEPER">Shopkeepers</option><option value="DELIVERY_PARTNER">Delivery partners</option><option value="ADMIN">Admins</option></select></label>
                </div>
                <div className="operations-table-wrap">
                  <table className="operations-table">
                    <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Access</th><th>Action</th></tr></thead>
                    <tbody>{visibleUsers.map((user) => (
                      <tr key={user.id}>
                        <td data-label="Name"><strong>{user.name}</strong></td>
                        <td data-label="Email">{user.email}</td>
                        <td data-label="Role">{formatRole(user.role)}</td>
                        <td data-label="Access"><span className={user.active ? 'operations-status operations-status--active' : 'operations-status'}>{user.active ? 'Active' : 'Inactive'}</span></td>
                        <td data-label="Action"><button type="button" className="operations-text-button" disabled={busyKey === 'user-' + user.id || user.id === session?.userId} onClick={() => void toggleUser(user)}>{user.id === session?.userId ? 'Current account' : user.active ? 'Deactivate' : 'Activate'}</button></td>
                      </tr>
                    ))}</tbody>
                  </table>
                  {visibleUsers.length === 0 && <div className="operations-state">No accounts match this filter.</div>}
                </div>
              </section>
            )}

            {tab === 'shops' && (
              <section>
                <div className="operations-section-heading"><div><p className="operations-eyebrow">SHOP OWNERSHIP</p><h2>Shops</h2><p>Assign each store to an active shopkeeper account.</p></div></div>
                {shops.length ? <div className="operations-shop-grid">{shops.map((shop) => (
                  <article className="operations-panel operations-shop-card" key={shop.id}>
                    <div className="operations-shop-card__top"><div><p className="operations-eyebrow">SHOP / {shop.id}</p><h3>{shop.name}</h3></div><span className={shop.active ? 'operations-status operations-status--active' : 'operations-status'}>{shop.active ? 'Active' : 'Inactive'}</span></div>
                    <p>{shop.address || 'No address recorded'}{shop.phone ? ' · ' + shop.phone : ''}</p>
                    <div className="operations-owner"><span>Current owner</span><strong>{shop.ownerName || 'Not assigned'}</strong><small>{shop.ownerEmail || 'Choose an active shopkeeper below'}</small></div>
                    <div className="operations-form-grid operations-form-grid--two operations-shop-location">
                      <label>Shop latitude
                        <input type="number" min="-90" max="90" step="0.000001"
                          value={shopLocationDrafts[shop.id]?.latitude ?? ''}
                          onChange={(event) => updateShopLocationDraft(shop.id, 'latitude', event.target.value)}
                          placeholder="12.971600" />
                      </label>
                      <label>Shop longitude
                        <input type="number" min="-180" max="180" step="0.000001"
                          value={shopLocationDrafts[shop.id]?.longitude ?? ''}
                          onChange={(event) => updateShopLocationDraft(shop.id, 'longitude', event.target.value)}
                          placeholder="77.594600" />
                      </label>
                    </div>
                    <p className="operations-help">Save a pin only after verifying it belongs to this store. Use device location while physically at the shop, or enter mapped coordinates.</p>
                    <div className="operations-shop-location__actions">
                      <button className="operations-text-button" type="button"
                        disabled={busyKey === 'shop-geolocate-' + shop.id}
                        onClick={() => useCurrentLocationForShop(shop)}>
                        {busyKey === 'shop-geolocate-' + shop.id ? 'Locating…' : 'Use this device location'}
                      </button>
                      <button className="operations-button operations-button--secondary" type="button"
                        disabled={busyKey === 'shop-location-' + shop.id}
                        onClick={() => void saveShopLocation(shop)}>
                        {busyKey === 'shop-location-' + shop.id ? 'Saving…' : 'Save shop pin'}
                      </button>
                    </div>
                    <label>Assign shopkeeper<select value={shop.ownerId ?? ''} disabled={!shopkeepers.length || busyKey === 'shop-' + shop.id} onChange={(event) => void assignOwner(shop, event.target.value)}><option value="" disabled>{shopkeepers.length ? 'Select a shopkeeper' : 'No active shopkeepers'}</option>{shopkeepers.map((owner) => <option key={owner.id} value={owner.id}>{owner.name} · {owner.email}</option>)}</select></label>
                    <button className="operations-text-button" type="button" disabled={busyKey === 'shop-' + shop.id} onClick={() => void toggleShop(shop)}>{shop.active ? 'Deactivate shop' : 'Activate shop'}</button>
                  </article>
                ))}</div> : <div className="operations-state">No shops have been registered yet.</div>}
              </section>
            )}

            {tab === 'catalogue' && (
              <section>
                <div className="operations-section-heading"><div><p className="operations-eyebrow">SHARED PRODUCTS</p><h2>Product catalogue</h2><p>Shopkeepers add these products to their own inventory and set local prices.</p></div></div>
                <form className="operations-panel operations-form" onSubmit={(event) => void createProduct(event)}>
                  <div><p className="operations-eyebrow">NEW CATALOGUE ITEM</p><h3>Add a product</h3><p>Master product details are shared; each shop still controls its own selling price and stock.</p></div>
                  <div className="operations-form-grid operations-form-grid--two">
                    <label>Product name<input value={productName} onChange={(event) => setProductName(event.target.value)} maxLength={120} required /></label>
                    <label>Category<select value={productCategory} onChange={(event) => { setProductCategory(event.target.value); setProductSubcategory('') }} required><option value="">Choose a category</option>{categories.map((category) => <option key={category.id} value={category.name}>{category.name}</option>)}</select></label>
                    <label>Subcategory<select value={productSubcategory} onChange={(event) => setProductSubcategory(event.target.value)} disabled={!productCategory}><option value="">No subcategory</option>{categories.find((category) => category.name === productCategory)?.subcategories.map((category) => <option key={category.id} value={category.name}>{category.name}</option>)}</select></label>
                    <label>Brand<input value={productBrand} onChange={(event) => setProductBrand(event.target.value)} maxLength={100} placeholder="Brand name" /></label>
                    <label>Barcode (GTIN)<input value={productBarcode} onChange={(event) => setProductBarcode(event.target.value)} inputMode="numeric" maxLength={14} pattern="([0-9]{8}|[0-9]{12}|[0-9]{13}|[0-9]{14})" placeholder="EAN / GTIN barcode" /></label>
                    <label>Pack size<input value={productPackSize} onChange={(event) => setProductPackSize(event.target.value)} maxLength={80} placeholder="e.g. 500 g, 1 L, 10 pcs" /></label>
                    <label>MRP (₹)<input type="number" min="0.01" max="1000000" step="0.01" value={productMrp} onChange={(event) => setProductMrp(event.target.value)} placeholder="Printed pack MRP" /></label>
                    <label>MRP source<select value={productMrpSource} onChange={(event) => setProductMrpSource(event.target.value)}><option value="">Not recorded</option><option value="pack">Physical pack</option><option value="brand">Brand supplied</option><option value="gov">Government source</option></select></label>
                  </div>
                  <label>Product description<textarea value={productDescription} onChange={(event) => setProductDescription(event.target.value)} maxLength={1000} rows={3} placeholder="Product details, pack information or usage notes" /></label>
                  <p className="operations-help">MRP is a sourced reference value, not the shop's selling price. Enter an MRP source only when an MRP is provided.</p>
                  <button className="operations-button" type="submit" disabled={busyKey === 'create-product' || !productName.trim() || !productCategory}>{busyKey === 'create-product' ? 'Adding…' : 'Add to catalogue'}</button>
                </form>
                <CatalogueReviewQueue token={token} />
              </section>
            )}
          </>
        )}
      </main>

      <footer className="operations-footer operations-shell">
        <Wordmark /><span>Administration / Apniko</span><Link to="/">Back to home ↗</Link>
      </footer>
    </div>
  )
}
