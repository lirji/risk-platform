import { validateTenantSelection } from './tenantSelection'

describe('tenant selection', () => {
  const organization = 'risk-platform'
  const clientId = 'ragshared0client00000001-org-risk-platform'

  it('accepts only the configured organization and matching derived client', () => {
    expect(validateTenantSelection(' risk-platform ', organization, clientId)).toEqual({
      ok: true,
      organization,
    })
  })

  it('rejects an empty or unknown tenant', () => {
    expect(validateTenantSelection('', organization, clientId)).toEqual({ ok: false, message: '请输入所属租户' })
    expect(validateTenantSelection('other', organization, clientId)).toEqual({
      ok: false,
      message: '租户 other 未开放。当前可用租户：risk-platform',
    })
  })

  it('fails closed when the configured client does not belong to the tenant', () => {
    expect(validateTenantSelection(organization, organization, 'unexpected-client')).toEqual({
      ok: false,
      message: '统一登录配置与租户不匹配，请联系管理员',
    })
  })
})
