export const APP_ROLES = [
  'CUSTOMER',
  'SHOPKEEPER',
  'DELIVERY_PARTNER',
  'ADMIN',
] as const

export type AppRole = (typeof APP_ROLES)[number]

const ROLE_HOME_PATH: Record<AppRole, string> = {
  CUSTOMER: '/customer',
  SHOPKEEPER: '/shopkeeper',
  DELIVERY_PARTNER: '/delivery',
  ADMIN: '/admin',
}

export function isAppRole(value: unknown): value is AppRole {
  return typeof value === 'string' && APP_ROLES.includes(value as AppRole)
}

export function roleHomePath(role: AppRole): string {
  return ROLE_HOME_PATH[role]
}

export function publicRegistrationRole(): AppRole {
  return 'CUSTOMER'
}
