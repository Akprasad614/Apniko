import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '../../auth/AuthProvider'
import { ApiError, apiRequest } from '../../lib/api'
import { roleHomePath } from '../../lib/roles'
import './notification-center.css'

interface NotificationItem {
  id: number
  title: string
  message: string
  type: string
  relatedOrderId?: number | null
  createdAt: string
  readAt?: string | null
  read: boolean
}

interface NotificationPage {
  content: NotificationItem[]
  totalPages: number
}

interface UnreadCountResponse {
  unreadCount: number
}

function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Your session has expired. Sign in again.'
    if (error.status === 403) return 'Notifications are not available for this account.'
    return error.message
  }
  return error instanceof Error ? error.message : 'Notifications could not be loaded.'
}

function formatNotificationDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('en-IN', {
    day: 'numeric',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date)
}

export function NotificationCenter() {
  const { session } = useAuth()
  const navigate = useNavigate()
  const token = session?.token ?? ''
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<NotificationItem[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)
  const [markingAll, setMarkingAll] = useState(false)

  const refreshUnreadCount = useCallback(async () => {
    if (!token) return
    try {
      const result = await apiRequest<UnreadCountResponse>('/api/notifications/unread-count', {}, token)
      setUnreadCount(Math.max(0, result.unreadCount))
    } catch {
      // Keep the last known count visible if a short poll fails.
    }
  }, [token])

  const loadNotifications = useCallback(async () => {
    if (!token) return
    setLoading(true)
    setError('')
    try {
      const [page] = await Promise.all([
        apiRequest<NotificationPage>('/api/notifications?page=0&size=20', {}, token),
        refreshUnreadCount(),
      ])
      setItems(page.content ?? [])
    } catch (loadError) {
      setError(describeError(loadError))
    } finally {
      setLoading(false)
    }
  }, [refreshUnreadCount, token])

  useEffect(() => {
    void refreshUnreadCount()
    const intervalId = window.setInterval(() => void refreshUnreadCount(), 30000)
    return () => window.clearInterval(intervalId)
  }, [refreshUnreadCount])

  useEffect(() => {
    if (!open) return
    void loadNotifications()
  }, [loadNotifications, open])

  useEffect(() => {
    if (!open) return
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [open])

  async function markRead(item: NotificationItem) {
    if (item.read || busyId === item.id) return
    setBusyId(item.id)
    try {
      const updated = await apiRequest<NotificationItem>(
        '/api/notifications/' + item.id + '/read',
        { method: 'PATCH' },
        token,
      )
      setItems((current) => current.map((entry) => entry.id === item.id ? updated : entry))
      setUnreadCount((current) => Math.max(0, current - 1))
    } catch (actionError) {
      setError(describeError(actionError))
    } finally {
      setBusyId(null)
    }
  }

  async function markAllRead() {
    if (markingAll || unreadCount === 0) return
    setMarkingAll(true)
    setError('')
    try {
      await apiRequest<{ updatedCount: number }>(
        '/api/notifications/read-all',
        { method: 'PATCH' },
        token,
      )
      setItems((current) => current.map((item) => ({ ...item, read: true })))
      setUnreadCount(0)
    } catch (actionError) {
      setError(describeError(actionError))
    } finally {
      setMarkingAll(false)
    }
  }

  async function dismiss(item: NotificationItem) {
    if (busyId === item.id) return
    setBusyId(item.id)
    try {
      await apiRequest<void>(
        '/api/notifications/' + item.id,
        { method: 'DELETE' },
        token,
      )
      setItems((current) => current.filter((entry) => entry.id !== item.id))
      if (!item.read) setUnreadCount((current) => Math.max(0, current - 1))
    } catch (actionError) {
      setError(describeError(actionError))
    } finally {
      setBusyId(null)
    }
  }

  async function openNotification(item: NotificationItem) {
    await markRead(item)
    setOpen(false)
    if (item.relatedOrderId && session?.role === 'CUSTOMER') {
      navigate('/customer/orders?placed=' + item.relatedOrderId)
      return
    }
    if (session) navigate(roleHomePath(session.role))
  }

  if (!session) return null

  return (
    <div className="notification-center">
      <button
        type="button"
        className="notification-trigger"
        aria-expanded={open}
        aria-haspopup="dialog"
        aria-label={unreadCount ? 'Notifications, ' + unreadCount + ' unread' : 'Notifications'}
        onClick={() => setOpen((current) => !current)}
      >
        <span className="notification-trigger__icon" aria-hidden="true">♢</span>
        <span className="notification-trigger__label">Updates</span>
        {unreadCount > 0 && <span className="notification-trigger__count">{unreadCount > 99 ? '99+' : unreadCount}</span>}
      </button>

      {open && (
        <section className="notification-panel" role="dialog" aria-label="Notifications">
          <header className="notification-panel__header">
            <div>
              <p className="notification-panel__eyebrow">APNIKO / UPDATES</p>
              <h2>Your notifications</h2>
            </div>
            <button type="button" className="notification-panel__close" onClick={() => setOpen(false)} aria-label="Close notifications">×</button>
          </header>
          <div className="notification-panel__actions">
            <span>{unreadCount} unread</span>
            <button type="button" onClick={() => void markAllRead()} disabled={markingAll || unreadCount === 0}>
              {markingAll ? 'Marking…' : 'Mark all as read'}
            </button>
          </div>
          {error && <p className="notification-panel__error" role="alert">{error}</p>}
          {loading ? (
            <p className="notification-panel__state" role="status">Loading updates…</p>
          ) : items.length ? (
            <div className="notification-panel__list">
              {items.map((item) => (
                <article className={'notification-item' + (item.read ? ' is-read' : '')} key={item.id}>
                  <button type="button" className="notification-item__open" onClick={() => void openNotification(item)} disabled={busyId === item.id}>
                    <span className="notification-item__dot" aria-hidden="true" />
                    <span className="notification-item__copy">
                      <strong>{item.title}</strong>
                      <span>{item.message}</span>
                      {item.relatedOrderId && <small>Order #{item.relatedOrderId}</small>}
                      <time dateTime={item.createdAt}>{formatNotificationDate(item.createdAt)}</time>
                    </span>
                  </button>
                  <button type="button" className="notification-item__dismiss" onClick={() => void dismiss(item)} disabled={busyId === item.id} aria-label={'Dismiss ' + item.title}>×</button>
                </article>
              ))}
            </div>
          ) : (
            <div className="notification-panel__state">
              <span aria-hidden="true">✳</span>
              <strong>You're all caught up.</strong>
              <p>New order and account updates will appear here.</p>
            </div>
          )}
          <footer className="notification-panel__footer">
            Recent updates · refreshed while you work
          </footer>
        </section>
      )}
    </div>
  )
}
