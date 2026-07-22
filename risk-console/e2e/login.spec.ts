import { expect, test } from '@playwright/test'

test('login page explains the identity boundary', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
  await expect(page.getByRole('button', { name: /统一身份平台|本地开发模式/ })).toBeVisible()
})
