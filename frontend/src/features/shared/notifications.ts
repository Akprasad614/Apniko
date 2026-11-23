import { roleHomePath } from '../../lib/roles'
import type { AppRole } from '../../lib/roles'

export function notificationDestination(role: AppRole, relatedOrderId?: number | null): string {
  if (role === 'CUSTOMER' && Number.isSafeInteger(relatedOrderId) && (relatedOrderId ?? 0) > 0) {
    return '/customer/orders?placed=' + relatedOrderId
  }
  return roleHomePath(role)
}

export function formatNotificationDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('en-IN', {
    day: 'numeric',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date)
}
