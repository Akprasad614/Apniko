import { test, expect, request } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { URL } from 'node:url'

/* global document */

const API_BASE = process.env.PHASE7_API_URL || 'http://127.0.0.1:8080'
const ADMIN_EMAIL = process.env.ADMIN_BOOTSTRAP_EMAIL || 'phase7-admin@local.test'
const PASSWORD = process.env.PHASE7_TEST_PASSWORD || process.env.ADMIN_BOOTSTRAP_PASSWORD
const CUSTOMER_EMAIL = 'phase7-customer@local.test'
const SHOPKEEPER_EMAIL = 'phase7-shopkeeper@local.test'
const DELIVERY_EMAIL = 'phase7-delivery@local.test'
const PRODUCT_NAME = 'Apniko Phase 7 Test Atta'
const SHOP_NAME = 'Phase 7 Test Corner'

let api
let shopkeeperToken
let customerToken
let shopId
let orderId

test.describe.configure({ mode: 'serial' })

async function call(method, endpoint, data, token) {
  const options = {}
  if (data !== undefined) options.data = data
  if (token) options.headers = { Authorization: 'Bearer ' + token }
  const response = await api[method](endpoint, options)
  if (!response.ok()) {
    throw new Error(method.toUpperCase() + ' ' + endpoint + ' returned ' + response.status()
      + ': ' + (await response.text()).slice(0, 1200))
  }
  return response.status() === 204 ? null : response.json()
}

async function signIn(page, email) {
  await page.goto('/login')
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: /sign in/i }).click()
  await expect(page).not.toHaveURL(/\/login(?:$|\?)/)
}

async function checkA11y(page, label) {
  // Run axe after entrance/interaction transitions settle. Sampling opacity
  // animations mid-frame can create false contrast failures on otherwise
  // readable controls and product text.
  await page.evaluate(async () => {
    const animations = document.getAnimations({ subtree: true })
    await Promise.all(
      animations.map((animation) => animation.finished.catch(() => undefined)),
    )
  })

  const result = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  const serious = result.violations.filter(
    (violation) => violation.impact === 'critical' || violation.impact === 'serious',
  )
  expect(
    serious.map((item) => ({
      id: item.id,
      impact: item.impact,
      help: item.help,
      nodes: item.nodes.slice(0, 5).map((node) => ({
        target: node.target,
        failure: node.failureSummary,
      })),
    })),
    'Critical or serious accessibility violations on ' + label,
  ).toEqual([])
}

async function checkMobile(page, label) {
  const width = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    content: document.documentElement.scrollWidth,
  }))
  expect(width.content, label + ' horizontal overflow').toBeLessThanOrEqual(width.viewport + 2)
  await checkA11y(page, label)
}

async function screenshot(page, testInfo, name) {
  const folder = path.join(testInfo.outputDir, 'phase7-screenshots')
  await fs.mkdir(folder, { recursive: true })
  const file = path.join(folder, name + '.png')
  await page.screenshot({ path: file, fullPage: true, animations: 'disabled' })
  await testInfo.attach(name, { path: file, contentType: 'image/png' })
}

test.beforeAll(async () => {
  if (!PASSWORD || PASSWORD.length < 12) {
    throw new Error('A one-run test password of at least 12 characters is required.')
  }
  api = await request.newContext({ baseURL: API_BASE })

  const adminResponse = await api.post('/api/auth/login', {
    data: { email: ADMIN_EMAIL, password: PASSWORD },
  })
  if (!adminResponse.ok()) {
    throw new Error('Could not sign in to the one-run CI administrator: ' + await adminResponse.text())
  }
  const admin = await adminResponse.json()
  const adminToken = admin.token

  await call('post', '/api/admin/users/shopkeepers', {
    name: 'Phase 7 Shopkeeper',
    email: SHOPKEEPER_EMAIL,
    password: PASSWORD,
  }, adminToken)
  await call('post', '/api/admin/users/delivery-partners', {
    name: 'Phase 7 Delivery Partner',
    email: DELIVERY_EMAIL,
    password: PASSWORD,
  }, adminToken)

  const shopkeeper = await call('post', '/api/auth/login', {
    email: SHOPKEEPER_EMAIL,
    password: PASSWORD,
  })
  shopkeeperToken = shopkeeper.token

  const shop = await call('post', '/api/shops', {
    name: SHOP_NAME,
    phone: '+919999000701',
    address: 'Phase 7 disposable CI fixture, Bengaluru',
  }, shopkeeperToken)
  shopId = shop.id

  const product = await call('post', '/api/products', {
    name: PRODUCT_NAME,
    category: 'Staples',
    subcategory: 'Flour',
    brand: 'Apniko Test Fixture',
    packSize: '1 kg',
    description: 'Disposable Phase 7 browser-test fixture; not a live catalogue entry.',
  }, adminToken)
  await call('post', '/api/shopkeeper/shops/' + shopId + '/inventory', {
    productId: product.id,
    price: 58.5,
    stockQuantity: 20,
  }, shopkeeperToken)

  const customer = await call('post', '/api/auth/register', {
    name: 'Phase 7 Customer',
    email: CUSTOMER_EMAIL,
    password: PASSWORD,
    role: 'CUSTOMER',
  })
  customerToken = customer.token
  await call('post', '/api/customer/addresses', {
    label: 'Phase 7 test address',
    recipientName: 'Phase 7 Customer',
    phone: '+919999000702',
    addressLine1: 'Phase 7 CI Test Lane',
    addressLine2: 'Indiranagar',
    landmark: 'CI-only fixture',
    city: 'Bengaluru',
    state: 'Karnataka',
    postalCode: '560038',
    defaultAddress: true,
  }, customerToken)

  const forbidden = await api.get('/api/admin/users', {
    headers: { Authorization: 'Bearer ' + shopkeeperToken },
  })
  expect(forbidden.status()).toBe(403)
  expect(shopId).toBeTruthy()
})

test.afterAll(async () => {
  await api?.dispose()
})

test('customer to shopkeeper to delivery order lifecycle; role accessibility and responsive layouts', async ({ page }, testInfo) => {
  const browserErrors = []
  page.on('pageerror', (error) => browserErrors.push(error.message))

  // Customer: browse the API-backed shop, add an item and complete COD checkout.
  await page.setViewportSize({ width: 1440, height: 1000 })
  await signIn(page, CUSTOMER_EMAIL)
  await expect(page.getByRole('heading', { name: /Good food/i })).toBeVisible()
  await expect(page.getByText(SHOP_NAME, { exact: true })).toBeVisible()
  await expect(page.getByText(PRODUCT_NAME, { exact: true })).toBeVisible()
  await checkA11y(page, 'customer desktop storefront')
  await screenshot(page, testInfo, 'customer-desktop')

  const productCard = page.locator('.catalogue-product-card').filter({ hasText: PRODUCT_NAME })
  await productCard.getByRole('button', { name: /^Add \\+$/i }).click()
  await page.locator('.customer-primary-nav')
    .getByRole('link', { name: 'Cart', exact: true }).click()
  await expect(page.getByText(PRODUCT_NAME, { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: /Place order/i })).toBeEnabled()
  await page.getByRole('button', { name: /Place order/i }).click()
  await expect(page).toHaveURL(/\/customer\/orders\?placed=/)
  orderId = Number(new URL(page.url()).searchParams.get('placed'))
  expect(Number.isSafeInteger(orderId)).toBeTruthy()

  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/customer/home')
  await expect(page.getByRole('heading', { name: /Good food/i })).toBeVisible()
  await checkMobile(page, 'customer mobile storefront')
  await screenshot(page, testInfo, 'customer-mobile')

  // Shopkeeper: choose platform delivery, accept, prepare and mark the order ready.
  await page.setViewportSize({ width: 1440, height: 1000 })
  await signIn(page, SHOPKEEPER_EMAIL)
  await expect(page.getByRole('heading', { name: /Good to see you/i })).toBeVisible()
  await page.getByRole('navigation', { name: 'Shopkeeper sections' })
    .getByRole('button', { name: 'Orders', exact: true }).click()
  const orderCard = page.locator('.shopkeeper-order').filter({ hasText: 'ORDER #' + orderId })
  await expect(orderCard).toBeVisible()
  await checkA11y(page, 'shopkeeper desktop orders')
  await orderCard.getByLabel('Fulfilment method for order #' + orderId).selectOption('PLATFORM_DELIVERY')
  await orderCard.getByRole('button', { name: /Save fulfilment/i }).click()
  await expect(orderCard.getByText(/Saved: Platform Delivery/i)).toBeVisible()
  await orderCard.getByRole('button', { name: /Accept order/i }).click()
  await orderCard.getByRole('button', { name: /Start preparing/i }).click()
  await orderCard.getByRole('button', { name: /Mark ready/i }).click()
  await expect(orderCard.locator('.shopkeeper-order__status')).toHaveText(/Ready/i)
  await screenshot(page, testInfo, 'shopkeeper-desktop')

  await page.setViewportSize({ width: 390, height: 844 })
  await page.locator('.shopkeeper-bottom-nav')
    .getByRole('button', { name: 'Overview', exact: true }).click()
  await checkMobile(page, 'shopkeeper mobile workspace')
  await screenshot(page, testInfo, 'shopkeeper-mobile')

  // Delivery partner: accept, pick up and complete the ready order.
  await page.setViewportSize({ width: 1440, height: 1000 })
  await signIn(page, DELIVERY_EMAIL)
  await expect(page.getByRole('heading', { name: /Every hand-off/i })).toBeVisible()
  await checkA11y(page, 'delivery desktop workspace')
  const available = page.locator('.delivery-order').filter({ hasText: 'ORDER / ' + orderId })
  await expect(available).toBeVisible()
  await available.getByRole('button', { name: /Accept delivery/i }).click()
  await page.getByRole('navigation', { name: 'Delivery sections' })
    .getByRole('button', { name: /My deliveries/i }).click()
  const assigned = page.locator('.delivery-order').filter({ hasText: 'ORDER / ' + orderId })
  await expect(assigned).toBeVisible()
  await assigned.getByRole('button', { name: /Confirm pickup/i }).click()
  await assigned.getByRole('button', { name: /Mark delivered/i }).click()
  await page.getByRole('navigation', { name: 'Delivery sections' })
    .getByRole('button', { name: /Completed/i }).click()
  await expect(page.locator('.delivery-order').filter({ hasText: 'ORDER / ' + orderId }))
    .toContainText('Delivered')
  await screenshot(page, testInfo, 'delivery-desktop')

  await page.setViewportSize({ width: 390, height: 844 })
  await checkMobile(page, 'delivery mobile workspace')
  await screenshot(page, testInfo, 'delivery-mobile')

  // Administrator: verify the protected operations workspace is usable.
  await page.setViewportSize({ width: 1440, height: 1000 })
  await signIn(page, ADMIN_EMAIL)
  await expect(page.getByRole('heading', { name: /Keep the local/i })).toBeVisible()
  await expect(page.getByRole('navigation', { name: 'Administration sections' })).toBeVisible()
  await checkA11y(page, 'administrator desktop workspace')
  await screenshot(page, testInfo, 'administrator-desktop')

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole('navigation', { name: 'Administration navigation' })).toBeVisible()
  await checkMobile(page, 'administrator mobile workspace')
  await screenshot(page, testInfo, 'administrator-mobile')

  // Verify the order lifecycle is visible to the customer after delivery.
  await page.setViewportSize({ width: 1440, height: 1000 })
  await signIn(page, CUSTOMER_EMAIL)
  await page.goto('/customer/orders')
  const deliveredOrder = page.locator('.customer-order').filter({ hasText: 'ORDER #' + orderId })
  await expect(deliveredOrder).toContainText(/Delivered/i)
  await deliveredOrder.getByRole('button', { name: 'Items and status history' }).click()
  await expect(deliveredOrder.getByText(PRODUCT_NAME, { exact: false })).toBeVisible()
  await checkA11y(page, 'customer delivered order history')
  await screenshot(page, testInfo, 'customer-order-delivered')

  expect(browserErrors, 'No uncaught browser exceptions during the role journey').toEqual([])
})
