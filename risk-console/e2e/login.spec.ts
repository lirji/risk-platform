import { expect, test } from '@playwright/test'

test('login page explains the identity boundary', async ({ page }) => {
  await page.route('**/api/v1/auth/me', (route) => route.fulfill({ status: 401, contentType: 'application/json', body: '{}' }))
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: /统一身份登录|本地开发模式/ })).toBeVisible()
  await expect(page.getByRole('button', { name: /统一身份登录|本地开发模式/ })).toBeVisible()
})
