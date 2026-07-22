import { expect, test } from '@playwright/test'

test('restores the dev session and navigates the management console', async ({ page }) => {
  const pageErrors: string[] = []
  page.on('pageerror', (error) => pageErrors.push(error.message))

  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { name: '实时风险态势' })).toBeVisible()
  const viewportWidth = page.viewportSize()?.width ?? 1280
  if (viewportWidth >= 768) {
    await expect(page.getByText('LOCAL', { exact: true })).toBeVisible()
  }
  await expect(page.getByText('决策样本')).toBeVisible()

  if (viewportWidth >= 1100) {
    await page.getByLabel('搜索决策流水').fill('txn-does-not-exist')
    await page.getByLabel('搜索决策流水').press('Enter')
  } else {
    await page.goto('/decisions?q=txn-does-not-exist')
  }
  await expect(page).toHaveURL(/\/decisions\?q=txn-does-not-exist/)
  await expect(page.getByLabel('交易流水筛选')).toHaveValue('txn-does-not-exist')
  await expect(page.getByText('暂无数据')).toBeVisible()

  if (viewportWidth < 768) {
    await page.getByRole('button', { name: '打开导航' }).click()
  }
  await page.getByRole('link', { name: /规则治理/ }).click()
  await expect(page.getByRole('heading', { name: '规则治理', level: 2 })).toBeVisible()
  expect(pageErrors).toEqual([])
})
