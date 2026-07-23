import { expect, test, type Page } from '@playwright/test'

const SESSION = {
  id: 'analyst-1',
  displayName: 'Risk Analyst',
  roles: ['analyst'],
  permissions: ['decision.read', 'case.read', 'profile.read', 'rule.read', 'model.read', 'rating.read', 'ops.read', 'audit.read'],
  authenticated: true,
  mode: 'dev',
}

const DECISION = {
  decisionId: 'decision-1',
  sourceId: 'MOBILE_TRANSFER',
  txnId: 'txn-2026-0001',
  eventTime: '2026-07-22T12:00:00Z',
  riskLevel: 'HIGH',
  action: 'REVIEW',
  fraudScore: 0.913,
  hitRulesJson: '[]',
  ruleVersion: 'rule-v8',
  modelVersion: 'fraud-rf-v3',
  costMs: 18,
  createdAt: '2026-07-22T12:00:00Z',
}

async function mockConsoleApi(page: Page, decisions: typeof DECISION[] = [], decisionDelay = 0) {
  await page.route('**/api/v1/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname
    let body: unknown = []
    if (pathname === '/api/v1/auth/me') body = SESSION
    else if (pathname === '/api/v1/decisions') {
      if (decisionDelay) await new Promise((resolve) => setTimeout(resolve, decisionDelay))
      body = { content: decisions, total: decisions.length, page: 0, size: 50 }
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  })
}

test('restores the dev session and navigates the management console', async ({ page }) => {
  const pageErrors: string[] = []
  page.on('pageerror', (error) => pageErrors.push(error.message))
  await mockConsoleApi(page)

  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { name: '实时风险态势' })).toBeVisible()
  const viewportWidth = page.viewportSize()?.width ?? 1280
  if (viewportWidth > 1180) await expect(page.getByText('生产镜像', { exact: true })).toBeVisible()
  await expect(page.getByText('决策样本')).toBeVisible()

  if (viewportWidth >= 1100) {
    await page.keyboard.press('/')
    await expect(page.getByLabel('搜索交易流水')).toBeFocused()
    await page.getByLabel('搜索交易流水').fill('txn-does-not-exist')
    await page.getByLabel('搜索交易流水').press('Enter')
  } else {
    await page.goto('/decisions?q=txn-does-not-exist')
  }
  await expect(page).toHaveURL(/\/decisions\?q=txn-does-not-exist/)
  await expect(page.getByLabel('交易流水筛选')).toHaveValue('txn-does-not-exist')
  await expect(page.getByText('暂无数据')).toBeVisible()

  if (viewportWidth < 768) {
    await page.getByRole('button', { name: '打开菜单' }).click()
  }
  await page.getByRole('link', { name: /规则治理/ }).click()
  await expect(page.getByRole('heading', { name: '规则治理', level: 2 })).toBeVisible()
  expect(pageErrors).toEqual([])
})

test('keeps loading, hover, and primary action surfaces readable in the dark theme', async ({ page }) => {
  await mockConsoleApi(page, [DECISION], 350)
  await page.goto('/dashboard')

  const skeleton = page.locator('.state--loading .skeleton').first()
  await expect(skeleton).toBeVisible()
  const skeletonBackground = await skeleton.evaluate((element) => getComputedStyle(element).backgroundImage)
  expect(skeletonBackground).toContain('rgb(16, 37, 48)')
  expect(skeletonBackground).not.toContain('rgb(242, 242, 242)')

  await page.goto('/decisions')
  const row = page.locator('.data-table--interactive tbody tr').first()
  await expect(row).toContainText(DECISION.txnId)
  expect(await row.locator('td').first().evaluate((element) => getComputedStyle(element).fontSize)).toBe('12px')
  if ((page.viewportSize()?.width ?? 0) < 620) {
    const tableMetrics = await page.locator('.table-wrap').evaluate((element) => ({ client: element.clientWidth, scroll: element.scrollWidth }))
    expect(tableMetrics.scroll).toBeGreaterThan(tableMetrics.client)
  }
  await row.hover()
  await expect.poll(() => row.evaluate((element) => getComputedStyle(element).backgroundColor)).not.toBe('rgba(0, 0, 0, 0)')

  const primaryButton = page.getByRole('button', { name: '搜索' })
  expect(await primaryButton.evaluate((element) => getComputedStyle(element).backgroundImage)).toContain('linear-gradient')
})

test('keeps sidebar icons centered after collapsing', async ({ page }) => {
  test.skip((page.viewportSize()?.width ?? 0) < 820, 'desktop collapse control is hidden on mobile')
  await mockConsoleApi(page)
  await page.goto('/dashboard')

  const sidebar = page.locator('.sidebar')
  await page.getByRole('button', { name: '收起侧边栏' }).click()
  await expect(page.locator('.app-shell')).toHaveClass(/app-shell--collapsed/)

  const targets = [
    page.locator('.brand__mark'),
    page.locator('.sidebar__status .pulse-dot'),
    page.locator('.nav-item').first().locator('svg'),
    page.getByRole('button', { name: '展开侧边栏' }),
  ]
  for (const target of targets) {
    await expect.poll(async () => {
      const sidebarBox = await sidebar.boundingBox()
      const targetBox = await target.boundingBox()
      if (!sidebarBox || !targetBox) return 99
      return Math.abs(targetBox.x + targetBox.width / 2 - (sidebarBox.x + sidebarBox.width / 2))
    }).toBeLessThan(1)
  }
  await expect(page.locator('.nav-item').first().locator('span')).toBeHidden()
  await page.reload()
  await expect(page.locator('.app-shell')).toHaveClass(/app-shell--collapsed/)
  await expect(page.getByRole('button', { name: '展开侧边栏' })).toBeVisible()
})
