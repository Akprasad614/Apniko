import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { BrandMark } from '../../components/BrandMark'
import { Button } from '../../components/Button'
import { Field } from '../../components/Field'
import { useAuth } from '../../auth/AuthProvider'
import { ApiError, apiRequest } from '../../lib/api'
import { getCurrentCoordinates } from '../../lib/geolocation'
import { filterOrdersForShop, formatRupees, nextShopkeeperStatus, statusActionLabel, statusLabel } from './shopkeeper'
import type { InventoryItem, Product, Shop, ShopkeeperOrder } from './shopkeeper'
import './shopkeeper-workspace.css'

type WorkspaceSection = 'overview' | 'orders' | 'inventory' | 'shop'

const SECTIONS: Array<{ id: WorkspaceSection; label: string; icon: string }> = [
  { id: 'overview', label: 'Overview', icon: '⌂' },
  { id: 'orders', label: 'Orders', icon: '◫' },
  { id: 'inventory', label: 'Inventory', icon: '▤' },
  { id: 'shop', label: 'Shop', icon: '●' },
]

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Your session has expired. Sign in again to continue.'
    if (error.status === 403) return 'This account cannot access that shopkeeper action.'
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

export function ShopkeeperWorkspace() {
  const { session, signOut } = useAuth()
  const token = session?.token ?? ''
  const [section, setSection] = useState<WorkspaceSection>('overview')
  const [shops, setShops] = useState<Shop[]>([])
  const [selectedShopId, setSelectedShopId] = useState<number | null>(null)
  const [inventory, setInventory] = useState<InventoryItem[]>([])
  const [lowStock, setLowStock] = useState<InventoryItem[]>([])
  const [orders, setOrders] = useState<ShopkeeperOrder[]>([])
  const [products, setProducts] = useState<Product[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [showAddItem, setShowAddItem] = useState(false)
  const [shopForm, setShopForm] = useState({ name: '', phone: '', address: '', latitude: '', longitude: '' })
  const [shopLocationMessage, setShopLocationMessage] = useState('')
  const [itemForm, setItemForm] = useState({ productId: '', price: '', stockQuantity: '' })
  const [stockAction, setStockAction] = useState<{ kind: 'restock' | 'threshold'; item: InventoryItem } | null>(null)
  const [stockActionQuantity, setStockActionQuantity] = useState('1')
  const [stockActionNote, setStockActionNote] = useState('')
  const [stockActionThreshold, setStockActionThreshold] = useState('5')
  const [cancelOrder, setCancelOrder] = useState<ShopkeeperOrder | null>(null)

  const selectedShop = useMemo(
    () => shops.find((shop) => shop.id === selectedShopId) ?? null,
    [shops, selectedShopId],
  )
  const selectedShopOrders = useMemo(
    () => filterOrdersForShop(orders, selectedShopId),
    [orders, selectedShopId],
  )

  const loadShops = useCallback(async () => {
    const result = await apiRequest<Shop[]>('/api/shopkeeper/shops', {}, token)
    setShops(result)
    setSelectedShopId((current) => current && result.some((shop) => shop.id === current) ? current : result[0]?.id ?? null)
  }, [token])

  const loadOrders = useCallback(async () => {
    const result = await apiRequest<ShopkeeperOrder[]>('/api/shopkeeper/orders', {}, token)
    setOrders(result)
  }, [token])

  const loadInventory = useCallback(async (shopId: number) => {
    const [items, lowItems] = await Promise.all([
      apiRequest<InventoryItem[]>(`/api/shopkeeper/shops/${shopId}/inventory`, {}, token),
      apiRequest<InventoryItem[]>(`/api/shopkeeper/shops/${shopId}/inventory/low-stock`, {}, token),
    ])
    setInventory(items)
    setLowStock(lowItems)
  }, [token])

  const refreshWorkspace = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      await Promise.all([loadShops(), loadOrders(), apiRequest<Product[]>('/api/products', {}, token).then(setProducts)])
    } catch (caught) {
      setError(messageFor(caught))
    } finally {
      setLoading(false)
    }
  }, [loadOrders, loadShops, token])

  useEffect(() => { void refreshWorkspace() }, [refreshWorkspace])

  useEffect(() => {
    if (selectedShopId === null) {
      setInventory([])
      setLowStock([])
      return
    }
    void loadInventory(selectedShopId).catch((caught: unknown) => setError(messageFor(caught)))
  }, [loadInventory, selectedShopId])

  async function captureNewShopLocation() {
    setShopLocationMessage('Requesting the device location…')
    try {
      const coordinates = await getCurrentCoordinates()
      setShopForm((current) => ({
        ...current,
        latitude: coordinates.latitude.toFixed(6),
        longitude: coordinates.longitude.toFixed(6),
      }))
      setShopLocationMessage('Shop coordinates captured. They will help customers find this shop nearby.')
    } catch (caught) {
      setShopLocationMessage(messageFor(caught))
    }
  }

  async function createShop(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const hasLatitude = shopForm.latitude.trim() !== ''
    const hasLongitude = shopForm.longitude.trim() !== ''
    if (hasLatitude !== hasLongitude) {
      setError('Capture both shop coordinates, or leave the location blank.')
      return
    }
    setBusy(true); setError(''); setNotice('')
    try {
      const created = await apiRequest<Shop>('/api/shopkeeper/shops', {
        method: 'POST',
        body: JSON.stringify({
          name: shopForm.name,
          phone: shopForm.phone,
          address: shopForm.address,
          latitude: hasLatitude ? Number(shopForm.latitude) : null,
          longitude: hasLongitude ? Number(shopForm.longitude) : null,
        }),
      }, token)
      setShops((current) => [...current, created])
      setSelectedShopId(created.id)
      setShopForm({ name: '', phone: '', address: '', latitude: '', longitude: '' })
      setShopLocationMessage('')
      setNotice(`${created.name} is ready for your catalogue.`)
    } catch (caught) { setError(messageFor(caught)) } finally { setBusy(false) }
  }

  async function addInventory(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (selectedShopId === null) return
    setBusy(true); setError(''); setNotice('')
    try {
      await apiRequest<InventoryItem>(`/api/shopkeeper/shops/${selectedShopId}/inventory`, {
        method: 'POST',
        body: JSON.stringify({ productId: Number(itemForm.productId), price: Number(itemForm.price), stockQuantity: Number(itemForm.stockQuantity) }),
      }, token)
      await loadInventory(selectedShopId)
      setItemForm({ productId: '', price: '', stockQuantity: '' })
      setShowAddItem(false)
      setNotice('Item added to your inventory.')
    } catch (caught) { setError(messageFor(caught)) } finally { setBusy(false) }
  }

  async function uploadInventoryImage(item: InventoryItem, file: File) {
    if (selectedShopId === null) return
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      setError('Choose a JPEG or PNG image.')
      return
    }
    if (file.size > 5 * 1024 * 1024) {
      setError('Product photos must be 5 MB or smaller.')
      return
    }

    const payload = new FormData()
    payload.append('file', file)
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await apiRequest<InventoryItem>(
        `/api/shopkeeper/shops/${selectedShopId}/inventory/${item.id}/image`,
        { method: 'POST', body: payload },
        token,
      )
      await loadInventory(selectedShopId)
      setNotice(`${item.productName} photo uploaded.`)
    } catch (caught) {
      setError(messageFor(caught))
    } finally {
      setBusy(false)
    }
  }

  async function updateItem(item: InventoryItem, values: Partial<Pick<InventoryItem, 'price' | 'stockQuantity' | 'available'>>) {
    setBusy(true); setError(''); setNotice('')
    try {
      await apiRequest<InventoryItem>(`/api/shopkeeper/shops/inventory/${item.id}`, {
        method: 'PUT', body: JSON.stringify({ price: values.price ?? item.price, stockQuantity: values.stockQuantity ?? item.stockQuantity, available: values.available ?? item.available }),
      }, token)
      if (selectedShopId !== null) await loadInventory(selectedShopId)
      setNotice(`${item.productName} updated.`)
    } catch (caught) { setError(messageFor(caught)) } finally { setBusy(false) }
  }

  function openRestock(item: InventoryItem) {
    setStockAction({ kind: 'restock', item })
    setStockActionQuantity('1')
    setStockActionNote('')
  }

  function openThreshold(item: InventoryItem) {
    setStockAction({ kind: 'threshold', item })
    setStockActionThreshold(String(item.lowStockThreshold))
  }

  async function submitStockAction(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!stockAction || selectedShopId === null) return
    const { kind, item } = stockAction
    const quantity = Number(stockActionQuantity)
    const threshold = Number(stockActionThreshold)
    if (kind === 'restock' && (!Number.isSafeInteger(quantity) || quantity < 1)) {
      setError('Enter a whole restock quantity of at least 1.')
      return
    }
    if (kind === 'threshold' && (!Number.isSafeInteger(threshold) || threshold < 0)) {
      setError('Enter a whole stock-alert quantity of 0 or more.')
      return
    }

    setBusy(true); setError(''); setNotice('')
    try {
      if (kind === 'restock') {
        await apiRequest<InventoryItem>(`/api/shopkeeper/shops/${selectedShopId}/inventory/${item.id}/restock`, {
          method: 'POST',
          body: JSON.stringify({ quantity, note: stockActionNote.trim() }),
        }, token)
        setNotice(`${item.productName} restocked.`)
      } else {
        await apiRequest<InventoryItem>(`/api/shopkeeper/shops/${selectedShopId}/inventory/${item.id}/low-stock-threshold`, {
          method: 'PUT',
          body: JSON.stringify({ lowStockThreshold: threshold }),
        }, token)
        setNotice('Low-stock level updated.')
      }
      await loadInventory(selectedShopId)
      setStockAction(null)
    } catch (caught) { setError(messageFor(caught)) } finally { setBusy(false) }
  }

  async function moveOrder(order: ShopkeeperOrder, cancel = false) {
    const nextStatus = nextShopkeeperStatus[order.status]
    if (cancel) {
      setCancelOrder(order)
      return
    }
    if (!nextStatus) return
    setBusy(true); setError(''); setNotice('')
    try {
      await apiRequest<ShopkeeperOrder>(`/api/shopkeeper/orders/${order.id}/status?status=${nextStatus}`, { method: 'PUT' }, token)
      await loadOrders()
      setNotice(`Order #${order.id} updated.`)
    } catch (caught) { setError(messageFor(caught)) } finally { setBusy(false) }
  }

  async function confirmCancelOrder() {
    if (!cancelOrder) return
    const order = cancelOrder
    setBusy(true); setError(''); setNotice('')
    try {
      await apiRequest<ShopkeeperOrder>(`/api/shopkeeper/orders/${order.id}/cancel`, { method: 'PUT' }, token)
      await loadOrders()
      setCancelOrder(null)
      setNotice(`Order #${order.id} cancelled and stock restoration recorded.`)
    } catch (caught) { setError(messageFor(caught)) } finally { setBusy(false) }
  }

  const availableProducts = products.filter((product) => !inventory.some((item) => item.productId === product.id))

  return (
    <div className="shopkeeper-app">
      <a className="skip-link" href="#shopkeeper-main">Skip to workspace</a>
      <header className="shopkeeper-header">
        <div className="shopkeeper-wrap shopkeeper-header__inner">
          <BrandMark />
          <div className="shopkeeper-header__user"><span>{session?.name}</span><Button tone="quiet" onClick={signOut}>Sign out</Button></div>
        </div>
      </header>
      <div className="shopkeeper-shell shopkeeper-wrap">
        <nav className="shopkeeper-nav" aria-label="Shopkeeper workspace">
          {SECTIONS.map((entry) => <button key={entry.id} type="button" className={section === entry.id ? 'is-active' : ''} onClick={() => setSection(entry.id)}><span aria-hidden="true">{entry.icon}</span>{entry.label}</button>)}
        </nav>
        <main id="shopkeeper-main" className="shopkeeper-main">
          <div className="shopkeeper-title"><div><p className="shopkeeper-kicker">YOUR SHOPKEEPER WORKSPACE</p><h1>{selectedShop?.name ?? 'Your shop, at hand.'}</h1></div>{selectedShop && <label className="shop-select"><span>Current shop</span><select value={selectedShopId ?? ''} onChange={(event) => setSelectedShopId(Number(event.target.value))}>{shops.map((shop) => <option key={shop.id} value={shop.id}>{shop.name}</option>)}</select></label>}</div>
          {error && <p className="workspace-message workspace-message--error" role="alert">{error}</p>}
          {notice && <p className="workspace-message" role="status">{notice}</p>}
          {loading ? <p className="workspace-state" role="status">Loading your shop workspace…</p> : shops.length === 0 ? (
            <section className="workspace-panel onboarding">
              <p className="shopkeeper-kicker">START HERE</p>
              <h2>Set up your first shop.</h2>
              <p>Once it is added, you can list the products you already sell and manage incoming orders.</p>
              <form className="workspace-form" onSubmit={createShop}>
                <Field label="Shop name" name="name" required value={shopForm.name} onChange={(event) => setShopForm({ ...shopForm, name: event.target.value })} />
                <Field label="Phone" name="phone" type="tel" required value={shopForm.phone} onChange={(event) => setShopForm({ ...shopForm, phone: event.target.value })} />
                <Field label="Address" name="address" required value={shopForm.address} onChange={(event) => setShopForm({ ...shopForm, address: event.target.value })} />
                <div className="shop-location-capture">
                  <div>
                    <strong>Shop map location</strong>
                    <span>{shopForm.latitude && shopForm.longitude ? `${shopForm.latitude}, ${shopForm.longitude}` : 'Optional, but helps customers discover nearby shops.'}</span>
                  </div>
                  <Button tone="quiet" type="button" disabled={busy} onClick={() => void captureNewShopLocation()}>Use this device location</Button>
                  {shopLocationMessage && <p role="status">{shopLocationMessage}</p>}
                </div>
                <Button type="submit" disabled={busy}>{busy ? 'Creating shop…' : 'Create shop'}</Button>
              </form>
            </section>
          ) : section === 'overview' ? <Overview shop={selectedShop} lowStock={lowStock} orders={selectedShopOrders} onOpen={(target) => setSection(target)} />
            : section === 'orders' ? <Orders orders={selectedShopOrders} busy={busy} onMove={moveOrder} />
              : section === 'inventory' ? <Inventory inventory={inventory} lowStock={lowStock} products={availableProducts} showAdd={showAddItem} busy={busy} itemForm={itemForm} onItemForm={setItemForm} onToggleAdd={() => setShowAddItem((current) => !current)} onAdd={addInventory} onUpdate={updateItem} onUploadImage={uploadInventoryImage} onRestock={openRestock} onThreshold={openThreshold} />
                : <ShopDetails key={selectedShop?.id ?? 'shop'} shop={selectedShop} token={token} onUpdated={(updated) => setShops((current) => current.map((entry) => entry.id === updated.id ? updated : entry))} />}
        </main>
      </div>
      <nav className="shopkeeper-bottom-nav" aria-label="Shopkeeper workspace mobile navigation">{SECTIONS.map((entry) => <button key={entry.id} type="button" className={section === entry.id ? 'is-active' : ''} onClick={() => setSection(entry.id)}><span aria-hidden="true">{entry.icon}</span>{entry.label}</button>)}</nav>
      {stockAction && (
        <div className="shopkeeper-sheet-backdrop">
          <section className="shopkeeper-sheet" role="dialog" aria-modal="true" aria-labelledby="stock-action-title">
            <div className="shopkeeper-sheet__heading">
              <div>
                <p className="shopkeeper-kicker">{stockAction.kind === 'restock' ? 'INVENTORY UPDATE' : 'STOCK ALERT'}</p>
                <h2 id="stock-action-title">{stockAction.kind === 'restock' ? 'Restock item' : 'Set low-stock alert'}</h2>
                <p>{stockAction.item.productName}</p>
              </div>
              <button type="button" className="shopkeeper-sheet__close" aria-label="Close" onClick={() => setStockAction(null)}>×</button>
            </div>
            <form className="shopkeeper-sheet__form" onSubmit={(event) => void submitStockAction(event)}>
              {stockAction.kind === 'restock' ? (
                <>
                  <Field label="Quantity to add" name="restockQuantity" type="number" min="1" step="1" required autoFocus value={stockActionQuantity} onChange={(event) => setStockActionQuantity(event.target.value)} />
                  <Field label="Note (optional)" name="restockNote" type="text" maxLength={500} value={stockActionNote} onChange={(event) => setStockActionNote(event.target.value)} />
                </>
              ) : (
                <Field label="Alert when stock reaches" name="stockThreshold" type="number" min="0" step="1" required autoFocus value={stockActionThreshold} onChange={(event) => setStockActionThreshold(event.target.value)} hint="Set 0 to disable the low-stock alert." />
              )}
              <div className="shopkeeper-sheet__actions">
                <Button tone="quiet" type="button" disabled={busy} onClick={() => setStockAction(null)}>Go back</Button>
                <Button type="submit" disabled={busy}>{busy ? 'Saving…' : stockAction.kind === 'restock' ? 'Confirm restock' : 'Save alert'}</Button>
              </div>
            </form>
          </section>
        </div>
      )}
      {cancelOrder && (
        <div className="shopkeeper-sheet-backdrop">
          <section className="shopkeeper-sheet" role="dialog" aria-modal="true" aria-labelledby="cancel-order-title">
            <div className="shopkeeper-sheet__heading">
              <div>
                <p className="shopkeeper-kicker">ORDER CANCELLATION</p>
                <h2 id="cancel-order-title">Cancel order #{cancelOrder.id}?</h2>
                <p>Apniko will restore the reserved inventory and update the order status.</p>
              </div>
              <button type="button" className="shopkeeper-sheet__close" aria-label="Close" onClick={() => setCancelOrder(null)}>×</button>
            </div>
            <div className="shopkeeper-sheet__actions">
              <Button tone="quiet" disabled={busy} onClick={() => setCancelOrder(null)}>Keep order</Button>
              <Button disabled={busy} onClick={() => void confirmCancelOrder()}>{busy ? 'Cancelling…' : 'Confirm cancellation'}</Button>
            </div>
          </section>
        </div>
      )}
    </div>
  )
}

function Overview({ shop, lowStock, orders, onOpen }: { shop: Shop | null; lowStock: InventoryItem[]; orders: ShopkeeperOrder[]; onOpen: (section: WorkspaceSection) => void }) {
  const activeOrders = orders.filter((order) => order.status !== 'DELIVERED' && order.status !== 'CANCELLED')
  return <><section className="workspace-panel overview-intro"><p>Keep the day moving from one calm, practical place. Your inventory and orders below are live records from Apniko.</p><dl><div><dt>Shop</dt><dd>{shop?.address || 'Address not listed'}</dd></div><div><dt>Orders needing attention</dt><dd>{activeOrders.length}</dd></div><div><dt>Low-stock items</dt><dd>{lowStock.length}</dd></div></dl></section><section className="workspace-split"><article className="workspace-panel"><div className="panel-heading"><div><p className="shopkeeper-kicker">ORDERS</p><h2>Next actions</h2></div><button type="button" className="inline-action" onClick={() => onOpen('orders')}>See orders</button></div>{activeOrders.length ? <ul className="compact-list">{activeOrders.slice(0, 3).map((order) => <li key={order.id}><div><strong>Order #{order.id}</strong><span>{statusLabel(order.status)} · {order.items.length} item{order.items.length === 1 ? '' : 's'}</span></div><b>{formatRupees(order.totalAmount)}</b></li>)}</ul> : <p className="empty-copy">No open orders right now.</p>}</article><article className="workspace-panel"><div className="panel-heading"><div><p className="shopkeeper-kicker">INVENTORY</p><h2>Low stock</h2></div><button type="button" className="inline-action" onClick={() => onOpen('inventory')}>Manage stock</button></div>{lowStock.length ? <ul className="compact-list">{lowStock.slice(0, 3).map((item) => <li key={item.id}><div><strong>{item.productName}</strong><span>{item.stockQuantity} left · alert at {item.lowStockThreshold}</span></div></li>)}</ul> : <p className="empty-copy">Nothing is currently below its stock level.</p>}</article></section></>
}

function Orders({ orders, busy, onMove }: { orders: ShopkeeperOrder[]; busy: boolean; onMove: (order: ShopkeeperOrder, cancel?: boolean) => Promise<void> }) {
  return <section><div className="section-title"><p className="shopkeeper-kicker">ORDERS</p><h2>Incoming and in progress</h2></div>{orders.length ? <div className="order-list">{orders.map((order) => <article className="order-card" key={order.id}><div className="order-card__top"><div><strong>Order #{order.id}</strong><span className={`status-tag status-tag--${order.status.toLowerCase()}`}>{statusLabel(order.status)}</span></div><b>{formatRupees(order.totalAmount)}</b></div><p className="order-card__meta">{order.deliveryMode.replaceAll('_', ' ').toLowerCase()} · {order.items.length} item{order.items.length === 1 ? '' : 's'}</p><ul>{order.items.map((item) => <li key={item.id}><span>{item.quantity} × {item.product.name}</span><span>{formatRupees(item.price * item.quantity)}</span></li>)}</ul><div className="order-card__actions">{nextShopkeeperStatus[order.status] && <Button disabled={busy} onClick={() => void onMove(order)}>{statusActionLabel[order.status]}</Button>}{['PLACED', 'ACCEPTED', 'PREPARING', 'READY'].includes(order.status) && <button className="danger-action" disabled={busy} type="button" onClick={() => void onMove(order, true)}>Cancel order</button>}</div></article>)}</div> : <p className="workspace-state">There are no orders for your shops yet.</p>}</section>
}

function Inventory({
  inventory,
  lowStock,
  products,
  showAdd,
  busy,
  itemForm,
  onItemForm,
  onToggleAdd,
  onAdd,
  onUpdate,
  onUploadImage,
  onRestock,
  onThreshold,
}: {
  inventory: InventoryItem[]
  lowStock: InventoryItem[]
  products: Product[]
  showAdd: boolean
  busy: boolean
  itemForm: { productId: string; price: string; stockQuantity: string }
  onItemForm: (value: { productId: string; price: string; stockQuantity: string }) => void
  onToggleAdd: () => void
  onAdd: (event: FormEvent<HTMLFormElement>) => Promise<void>
  onUpdate: (item: InventoryItem, values: Partial<Pick<InventoryItem, 'price' | 'stockQuantity' | 'available'>>) => Promise<void>
  onUploadImage: (item: InventoryItem, file: File) => Promise<void>
  onRestock: (item: InventoryItem) => void
  onThreshold: (item: InventoryItem) => void
}) {
  return (
    <section>
      <div className="section-title section-title--row">
        <div>
          <p className="shopkeeper-kicker">INVENTORY</p>
          <h2>Your shelf</h2>
          <p>{lowStock.length
            ? `${lowStock.length} item${lowStock.length === 1 ? '' : 's'} need attention.`
            : 'All stock levels are currently above their alerts.'}</p>
        </div>
        <Button disabled={busy || products.length === 0} onClick={onToggleAdd}>
          {showAdd ? 'Close' : 'Add product'}
        </Button>
      </div>

      {showAdd && (
        <form className="workspace-panel workspace-form inventory-add" onSubmit={(event) => void onAdd(event)}>
          <label className="field">
            <span className="field__label">Existing product</span>
            <select required value={itemForm.productId} onChange={(event) => onItemForm({ ...itemForm, productId: event.target.value })}>
              <option value="">Choose a product</option>
              {products.map((product) => (
                <option key={product.id} value={product.id}>
                  {product.name}{product.category ? ` · ${product.category}` : ''}
                </option>
              ))}
            </select>
          </label>
          <Field label="Price (₹)" name="price" type="number" min="0" step="0.01" required value={itemForm.price} onChange={(event) => onItemForm({ ...itemForm, price: event.target.value })} />
          <Field label="Opening stock" name="stockQuantity" type="number" min="0" required value={itemForm.stockQuantity} onChange={(event) => onItemForm({ ...itemForm, stockQuantity: event.target.value })} />
          <Button type="submit" disabled={busy}>Add to shelf</Button>
        </form>
      )}

      {inventory.length ? (
        <div className="inventory-list">
          {inventory.map((item) => (
            <article className="inventory-card" key={item.id}>
              <div className="inventory-card__main">
                <div className="inventory-card__title">
                  <h3>{item.productName}</h3>
                  {item.lowStock && <span className="stock-alert">Low stock</span>}
                </div>
                <p>{item.category || 'Uncategorised'} · {formatRupees(item.price)}</p>
              </div>

              <div className="inventory-card__stock">
                <strong>{item.stockQuantity}</strong>
                <span>in stock</span>
              </div>

              <div className="inventory-card__photo-row">
                {item.imageUrl ? (
                  <img className="inventory-card__photo" src={item.imageUrl} alt={item.productName} loading="lazy" />
                ) : (
                  <div className="inventory-card__photo inventory-card__photo--empty">
                    <span aria-hidden="true">＋</span>
                    <small>No shop photo</small>
                  </div>
                )}
                <label className="inventory-image-upload">
                  <span>{item.imageUrl ? 'Replace photo' : 'Add product photo'}</span>
                  <input
                    type="file"
                    accept="image/jpeg,image/png"
                    aria-label={`Upload a photo for ${item.productName}`}
                    disabled={busy}
                    onChange={(event) => {
                      const file = event.target.files?.[0]
                      if (file) void onUploadImage(item, file)
                      event.currentTarget.value = ''
                    }}
                  />
                  <small>JPEG or PNG · up to 5 MB</small>
                </label>
              </div>

              <div className="inventory-card__controls">
                <label>
                  <span>Price</span>
                  <input
                    aria-label={`${item.productName} price`}
                    type="number"
                    min="0"
                    step="0.01"
                    defaultValue={item.price}
                    onBlur={(event) => {
                      const price = Number(event.target.value)
                      if (Number.isFinite(price) && price !== item.price) void onUpdate(item, { price })
                    }}
                  />
                </label>
                <label>
                  <span>Stock</span>
                  <input
                    aria-label={`${item.productName} stock`}
                    type="number"
                    min="0"
                    defaultValue={item.stockQuantity}
                    onBlur={(event) => {
                      const stockQuantity = Number(event.target.value)
                      if (Number.isInteger(stockQuantity) && stockQuantity !== item.stockQuantity) {
                        void onUpdate(item, { stockQuantity })
                      }
                    }}
                  />
                </label>
                <label className="availability">
                  <input type="checkbox" checked={item.available} onChange={(event) => void onUpdate(item, { available: event.target.checked })} />
                  Available
                </label>
                <button type="button" disabled={busy} onClick={() => void onRestock(item)}>Restock</button>
                <button type="button" disabled={busy} onClick={() => void onThreshold(item)}>Stock alert: {item.lowStockThreshold}</button>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <p className="workspace-state">No products are on this shop’s shelf yet.</p>
      )}
    </section>
  )
}

function ShopDetails({
  shop,
  token,
  onUpdated,
}: {
  shop: Shop | null
  token: string
  onUpdated: (shop: Shop) => void
}) {
  const [form, setForm] = useState({
    name: shop?.name ?? '',
    phone: shop?.phone ?? '',
    address: shop?.address ?? '',
    latitude: shop?.latitude == null ? '' : String(shop.latitude),
    longitude: shop?.longitude == null ? '' : String(shop.longitude),
  })
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')

  async function captureLocation() {
    setMessage('Requesting the device location…')
    try {
      const coordinates = await getCurrentCoordinates()
      setForm((current) => ({
        ...current,
        latitude: coordinates.latitude.toFixed(6),
        longitude: coordinates.longitude.toFixed(6),
      }))
      setMessage('Coordinates captured. Save the shop to publish its nearby location.')
    } catch (caught) {
      setMessage(messageFor(caught))
    }
  }

  async function saveShop(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!shop) return
    const hasLatitude = form.latitude.trim() !== ''
    const hasLongitude = form.longitude.trim() !== ''
    if (hasLatitude !== hasLongitude) {
      setMessage('Both latitude and longitude are required. Capture the location again or clear both fields.')
      return
    }

    setSaving(true)
    setMessage('')
    try {
      const updated = await apiRequest<Shop>(`/api/shops/${shop.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          name: form.name,
          phone: form.phone,
          address: form.address,
          latitude: hasLatitude ? Number(form.latitude) : null,
          longitude: hasLongitude ? Number(form.longitude) : null,
        }),
      }, token)
      onUpdated(updated)
      setMessage('Shop details saved.')
    } catch (caught) {
      setMessage(messageFor(caught))
    } finally {
      setSaving(false)
    }
  }

  if (!shop) return <p className="workspace-state">Choose a shop to manage its details.</p>

  return (
    <section className="workspace-panel shop-details">
      <p className="shopkeeper-kicker">SHOP PROFILE</p>
      <h2>{shop.name}</h2>
      <form className="workspace-form" onSubmit={(event) => void saveShop(event)}>
        <Field label="Shop name" name="shopName" required value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
        <Field label="Phone" name="shopPhone" type="tel" required value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} />
        <Field label="Address" name="shopAddress" required value={form.address} onChange={(event) => setForm({ ...form, address: event.target.value })} />
        <div className="shop-location-capture">
          <div>
            <strong>Shop map location</strong>
            <span>{form.latitude && form.longitude ? `${form.latitude}, ${form.longitude}` : 'No coordinates saved yet.'}</span>
          </div>
          <Button tone="quiet" type="button" disabled={saving} onClick={() => void captureLocation()}>Use this device location</Button>
          <p>Allow location access while standing at the shop. Customers can then sort shops by distance.</p>
        </div>
        {message && <p className="workspace-message" role="status">{message}</p>}
        <Button type="submit" disabled={saving}>{saving ? 'Saving…' : 'Save shop details'}</Button>
      </form>
      <dl className="shop-details__summary">
        <div><dt>Owner</dt><dd>{shop.ownerName || 'Not listed'}</dd></div>
        <div><dt>Status</dt><dd>{shop.active ? 'Active on Apniko' : 'Inactive'}</dd></div>
      </dl>
    </section>
  )
}
