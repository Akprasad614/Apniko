import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Button } from '../../components/Button'
import { Field } from '../../components/Field'
import { ApiError, apiRequest } from '../../lib/api'
import {
  createEmptyAddressDraft,
  formatAddressLocation,
  toAddressDraft,
} from './customer-addresses'
import type { CustomerAddress, CustomerAddressDraft } from './customer-addresses'
import './customer-address-book.css'

interface CustomerAddressBookProps {
  token: string
  onClose: () => void
}

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Your session has expired. Sign in again to continue.'
    if (error.status === 403) return 'Only customer accounts can manage delivery addresses.'
    return error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

export function CustomerAddressBook({ token, onClose }: CustomerAddressBookProps) {
  const [addresses, setAddresses] = useState<CustomerAddress[]>([])
  const [draft, setDraft] = useState<CustomerAddressDraft>(createEmptyAddressDraft)
  const [editingAddressId, setEditingAddressId] = useState<number | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [confirmRemoveId, setConfirmRemoveId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const loadAddresses = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const result = await apiRequest<CustomerAddress[]>('/api/customer/addresses', {}, token)
      setAddresses(result)
    } catch (caught) {
      setError(messageFor(caught))
    } finally {
      setLoading(false)
    }
  }, [token])

  useEffect(() => {
    void loadAddresses()
  }, [loadAddresses])

  useEffect(() => {
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape' && !busy) onClose()
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [busy, onClose])

  function startNewAddress() {
    setEditingAddressId(null)
    setDraft(createEmptyAddressDraft())
    setConfirmRemoveId(null)
    setShowForm(true)
    setError('')
    setNotice('')
  }

  function startEditing(address: CustomerAddress) {
    setEditingAddressId(address.id)
    setDraft(toAddressDraft(address))
    setConfirmRemoveId(null)
    setShowForm(true)
    setError('')
    setNotice('')
  }

  async function saveAddress(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError('')
    setNotice('')

    const isEditing = editingAddressId !== null
    const path = isEditing
      ? `/api/customer/addresses/${editingAddressId}`
      : '/api/customer/addresses'

    const payload = {
      ...draft,
      label: draft.label.trim(),
      recipientName: draft.recipientName.trim(),
      phone: draft.phone.trim(),
      addressLine1: draft.addressLine1.trim(),
      addressLine2: draft.addressLine2.trim(),
      landmark: draft.landmark.trim(),
      locality: draft.locality.trim(),
      city: draft.city.trim(),
      state: draft.state.trim(),
      postalCode: draft.postalCode.trim(),
    }

    try {
      await apiRequest<CustomerAddress>(
        path,
        { method: isEditing ? 'PUT' : 'POST', body: JSON.stringify(payload) },
        token,
      )
      await loadAddresses()
      setShowForm(false)
      setEditingAddressId(null)
      setDraft(createEmptyAddressDraft())
      setNotice(isEditing ? 'Delivery address updated.' : 'Delivery address saved.')
    } catch (caught) {
      setError(messageFor(caught))
    } finally {
      setBusy(false)
    }
  }

  async function makeDefault(addressId: number) {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const updated = await apiRequest<CustomerAddress>(
        `/api/customer/addresses/${addressId}/default`,
        { method: 'PUT' },
        token,
      )
      setAddresses((current) => current.map((address) => ({
        ...address,
        defaultAddress: address.id === updated.id,
      })))
      setNotice('Default delivery address updated.')
    } catch (caught) {
      setError(messageFor(caught))
    } finally {
      setBusy(false)
    }
  }

  async function removeAddress(addressId: number) {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await apiRequest<void>(
        `/api/customer/addresses/${addressId}`,
        { method: 'DELETE' },
        token,
      )
      setConfirmRemoveId(null)
      await loadAddresses()
      setNotice('Address removed. Your remaining default address is unchanged.')
    } catch (caught) {
      setError(messageFor(caught))
    } finally {
      setBusy(false)
    }
  }

  function updateDraft<K extends keyof CustomerAddressDraft>(
    key: K,
    value: CustomerAddressDraft[K],
  ) {
    setDraft((current) => ({ ...current, [key]: value }))
  }

  return (
    <div className="address-sheet-backdrop">
      <section className="address-sheet" role="dialog" aria-modal="true" aria-labelledby="address-sheet-title">
        <header className="address-sheet__header">
          <div>
            <p className="customer-eyebrow">APNIKO DELIVERY DETAILS</p>
            <h2 id="address-sheet-title">Delivery addresses</h2>
            <p>Save the places where your local shops can bring an order.</p>
          </div>
          <button type="button" className="address-sheet__close" aria-label="Close address book" onClick={onClose}>×</button>
        </header>

        {error && <p className="address-message address-message--error" role="alert">{error}</p>}
        {notice && <p className="address-message" role="status">{notice}</p>}

        {!showForm && (
          <div className="address-sheet__toolbar">
            <span>{addresses.length} saved {addresses.length === 1 ? 'address' : 'addresses'}</span>
            <Button onClick={startNewAddress} disabled={busy}>Add address</Button>
          </div>
        )}

        {loading ? (
          <p className="address-sheet__state" role="status">Loading your saved addresses…</p>
        ) : !showForm ? (
          addresses.length ? (
            <div className="address-list">
              {addresses.map((address) => (
                <article className="address-card" key={address.id}>
                  <div className="address-card__top">
                    <div className="address-card__label">
                      <span>{address.label}</span>
                      {address.defaultAddress && <strong>Default</strong>}
                    </div>
                    <span className="address-card__recipient">{address.recipientName} · {address.phone}</span>
                  </div>
                  <p>{formatAddressLocation(address)}</p>
                  {confirmRemoveId === address.id ? (
                    <div className="address-card__confirm">
                      <span>Remove this saved address?</span>
                      <button type="button" disabled={busy} onClick={() => setConfirmRemoveId(null)}>Keep it</button>
                      <button type="button" disabled={busy} onClick={() => void removeAddress(address.id)}>Remove</button>
                    </div>
                  ) : (
                    <div className="address-card__actions">
                      {!address.defaultAddress && (
                        <button type="button" disabled={busy} onClick={() => void makeDefault(address.id)}>Make default</button>
                      )}
                      <button type="button" disabled={busy} onClick={() => startEditing(address)}>Edit</button>
                      <button type="button" disabled={busy} onClick={() => setConfirmRemoveId(address.id)}>Remove</button>
                    </div>
                  )}
                </article>
              ))}
            </div>
          ) : (
            <div className="address-sheet__empty">
              <span aria-hidden="true">⌖</span>
              <strong>No delivery address saved yet.</strong>
              <p>Add your home or work address now. Checkout will use one of your saved addresses.</p>
              <Button onClick={startNewAddress}>Add your first address</Button>
            </div>
          )
        ) : (
          <form className="address-form" onSubmit={(event) => void saveAddress(event)}>
            <div className="address-form__grid">
              <Field label="Address label" name="addressLabel" required maxLength={30} value={draft.label} onChange={(event) => updateDraft('label', event.target.value)} hint="For example, Home, Work or Parents." />
              <Field label="Recipient name" name="recipientName" required maxLength={100} autoComplete="name" value={draft.recipientName} onChange={(event) => updateDraft('recipientName', event.target.value)} />
              <Field label="Phone number" name="addressPhone" type="tel" required maxLength={20} pattern="[+]?[0-9][0-9 -]{7,19}" autoComplete="tel" value={draft.phone} onChange={(event) => updateDraft('phone', event.target.value)} />
              <Field label="House / building / street" name="addressLine1" required maxLength={255} autoComplete="address-line1" value={draft.addressLine1} onChange={(event) => updateDraft('addressLine1', event.target.value)} />
              <Field label="Apartment / floor (optional)" name="addressLine2" maxLength={255} autoComplete="address-line2" value={draft.addressLine2} onChange={(event) => updateDraft('addressLine2', event.target.value)} />
              <Field label="Landmark (optional)" name="addressLandmark" maxLength={120} value={draft.landmark} onChange={(event) => updateDraft('landmark', event.target.value)} />
              <Field label="Area / locality" name="addressLocality" required maxLength={120} autoComplete="address-level3" value={draft.locality} onChange={(event) => updateDraft('locality', event.target.value)} />
              <Field label="City" name="addressCity" required maxLength={80} autoComplete="address-level2" value={draft.city} onChange={(event) => updateDraft('city', event.target.value)} />
              <Field label="State" name="addressState" required maxLength={80} autoComplete="address-level1" value={draft.state} onChange={(event) => updateDraft('state', event.target.value)} />
              <Field label="6-digit PIN code" name="addressPostalCode" required maxLength={6} minLength={6} pattern="[1-9][0-9]{5}" inputMode="numeric" autoComplete="postal-code" value={draft.postalCode} onChange={(event) => updateDraft('postalCode', event.target.value.replace(/\\D/g, '').slice(0, 6))} />
            </div>
            <label className="address-form__default">
              <input type="checkbox" checked={draft.defaultAddress} onChange={(event) => updateDraft('defaultAddress', event.target.checked)} />
              <span>Make this my default delivery address</span>
            </label>
            <div className="address-form__actions">
              <Button tone="quiet" type="button" disabled={busy} onClick={() => setShowForm(false)}>Back to addresses</Button>
              <Button type="submit" disabled={busy}>{busy ? 'Saving…' : editingAddressId ? 'Save changes' : 'Save address'}</Button>
            </div>
          </form>
        )}
      </section>
    </div>
  )
}
