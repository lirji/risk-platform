import { expect, test, type Page } from '@playwright/test'

const username = process.env.E2E_USERNAME
const password = process.env.E2E_PASSWORD
const caseId = process.env.E2E_CASE_ID

async function loginThroughCasdoor(page: Page) {
  if (!username || !password) throw new Error('E2E_USERNAME and E2E_PASSWORD are required')
  await page.goto('/login')
  await page.getByRole('button', { name: /统一身份平台/ }).click()
  await expect(page).toHaveURL(/localhost:8000/)

  // A shared Casdoor client can either derive the organization from its client ID or show
  // a short organization-selection step first.
  if (await page.locator('input[type="password"]:visible').count() === 0) {
    const organization = page.locator('input:visible').first()
    await organization.fill('risk-platform')
    await organization.press('Enter')
  }
  const userInput = page.locator('input:not([type="password"]):visible').first()
  const passwordInput = page.locator('input[type="password"]:visible').first()
  await expect(passwordInput).toBeVisible()
  await userInput.fill(username)
  await passwordInput.fill(password)
  await passwordInput.press('Enter')
  await expect(page).toHaveURL(/localhost:15173\/dashboard/)
}

test('auth-platform PKCE login, rule draft and assigned-case resolution', async ({ page }) => {
  if (!caseId) throw new Error('E2E_CASE_ID is required')
  const pageErrors: string[] = []
  const authenticatedApiRequests: string[] = []
  let tokenExchange = ''
  page.on('pageerror', error => pageErrors.push(error.message))
  page.on('request', request => {
    if (request.url().includes('/api/login/oauth/access_token')) tokenExchange = request.postData() ?? ''
    if (request.url().includes('/api/v1/') && request.headers().authorization?.startsWith('Bearer ')) {
      authenticatedApiRequests.push(request.url())
    }
  })

  await loginThroughCasdoor(page)
  await expect(page.getByRole('heading', { name: '实时风险态势' })).toBeVisible()
  await expect(page.getByText('AUTH PLATFORM', { exact: true })).toBeVisible()
  expect(tokenExchange).toContain('code_verifier=')
  expect(tokenExchange).not.toContain('client_secret=')

  await page.getByRole('link', { name: /规则治理/ }).click()
  await expect(page.getByRole('heading', { name: '规则治理', level: 2 })).toBeVisible()
  const ruleCode = `AUTH_E2E_${Date.now()}`
  await page.getByRole('button', { name: '新建规则版本' }).click()
  await page.getByLabel('规则编码').fill(ruleCode)
  await page.getByLabel('名称').fill('auth-platform E2E draft')
  await page.getByRole('button', { name: '保存草稿' }).click()
  await expect(page.getByText('草稿已创建')).toBeVisible()
  await expect(page.getByText(ruleCode, { exact: true })).toBeVisible()

  await page.getByRole('link', { name: /案件中心/ }).click()
  const row = page.getByRole('row').filter({ hasText: caseId })
  await expect(row).toBeVisible()
  const claimResponse = page.waitForResponse(response =>
    response.url().includes(`/api/v1/cases/${caseId}/claim`) && response.request().method() === 'POST')
  await row.getByRole('button', { name: '认领' }).click()
  expect((await claimResponse).status()).toBe(200)

  await page.getByText('处理中', { exact: true }).click()
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: '结案' }).click()
  await page.getByRole('dialog', { name: '结案' }).getByRole('textbox').fill('browser auth-platform E2E passed')
  await page.getByRole('dialog', { name: '结案' }).getByRole('button', { name: 'OK' }).click()
  const resolveResponse = page.waitForResponse(response =>
    response.url().includes(`/api/v1/cases/${caseId}/resolve`) && response.request().method() === 'POST')
  await page.getByRole('dialog', { name: '标签回流' }).getByRole('button', { name: '欺诈' }).click()
  expect((await resolveResponse).status()).toBe(200)
  await page.getByText('已结案', { exact: true }).click()
  await expect(row).toContainText('RESOLVED')

  expect(authenticatedApiRequests.some(url => url.includes('/api/v1/auth/me'))).toBe(true)
  expect(authenticatedApiRequests.some(url => url.includes('/api/v1/rules/releases'))).toBe(true)
  expect(authenticatedApiRequests.some(url => url.includes(`/api/v1/cases/${caseId}/claim`))).toBe(true)
  expect(authenticatedApiRequests.some(url => url.includes(`/api/v1/cases/${caseId}/resolve`))).toBe(true)
  expect(pageErrors).toEqual([])
})

test('restores the OIDC session after reload and completes SSO logout', async ({ page }) => {
  await loginThroughCasdoor(page)
  await page.reload()
  await expect(page.getByRole('heading', { name: '实时风险态势' })).toBeVisible()
  await expect(page.getByText('AUTH PLATFORM', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: /risk-e2e-admin/ }).click()
  await page.getByRole('menuitem', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/localhost:15173\/login/)
  await expect(page.getByRole('button', { name: /统一身份平台/ })).toBeVisible()
})
