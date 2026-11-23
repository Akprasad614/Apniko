import { defineConfig } from '@playwright/test'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const frontendRoot = path.dirname(fileURLToPath(import.meta.url))
const repositoryRoot = path.resolve(frontendRoot, '..')

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.mjs',
  fullyParallel: false,
  workers: 1,
  timeout: 360_000,
  expect: { timeout: 20_000 },
  outputDir: './test-results',
  reporter: [['list'], ['html', { outputFolder: './playwright-report', open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:5173',
    browserName: 'chromium',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    actionTimeout: 20_000,
    navigationTimeout: 30_000,
  },
  webServer: [
    {
      command: './mvnw -B -ntp spring-boot:run',
      cwd: repositoryRoot,
      url: 'http://127.0.0.1:8080/api/test',
      timeout: 180_000,
      reuseExistingServer: !process.env.CI,
      env: {
        ...process.env,
        SPRING_DATASOURCE_URL: process.env.SPRING_DATASOURCE_URL
          || 'jdbc:mysql://127.0.0.1:3306/localcart_phase7?allowPublicKeyRetrieval=true&useSSL=false',
      },
    },
    {
      command: 'npm run dev -- --host 127.0.0.1',
      cwd: frontendRoot,
      url: 'http://127.0.0.1:5173',
      timeout: 60_000,
      reuseExistingServer: !process.env.CI,
    },
  ],
})
