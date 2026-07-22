export type TenantSelectionResult =
  | { ok: true; organization: string }
  | { ok: false; message: string }

/**
 * 风控平台目前只开放一个经过后端 owner/audience 与数据边界验证的 Casdoor organization。
 * 登录页允许用户确认租户，但绝不根据任意输入动态拼接 clientId。
 */
export function validateTenantSelection(
  rawTenant: string,
  expectedOrganization: string,
  configuredClientId: string,
): TenantSelectionResult {
  const tenant = rawTenant.trim()
  const organization = expectedOrganization.trim()

  if (!tenant) return { ok: false, message: '请输入所属租户' }
  if (!organization) return { ok: false, message: '未配置可用租户，请联系管理员' }
  if (tenant !== organization) {
    return { ok: false, message: `租户 ${tenant} 未开放。当前可用租户：${organization}` }
  }
  if (!configuredClientId.endsWith(`-org-${organization}`)) {
    return { ok: false, message: '统一登录配置与租户不匹配，请联系管理员' }
  }

  return { ok: true, organization }
}
