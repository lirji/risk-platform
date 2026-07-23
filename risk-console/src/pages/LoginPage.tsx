import { ArrowRight, Building2, Fingerprint, ScanSearch, ShieldCheck, Workflow } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { AUTH_CONFIG, AUTH_MODE } from '../auth/config'
import { useAuth } from '../auth/AuthContext'
import { validateTenantSelection } from '../auth/tenantSelection'
import { Field, Input, LoadingButton } from '../components/ui'

const FEATURES = [
  { icon: ScanSearch, title: '实时风险识别', description: '融合规则、模型与画像，快速识别可疑交易' },
  { icon: Workflow, title: '风险案件处置', description: '串联告警、认领、研判与审计，形成闭环' },
  { icon: Fingerprint, title: '统一身份与权限', description: '通过 Casdoor 单点登录，严格落实最小权限' },
]

export default function LoginPage() {
  const auth = useAuth(); const [params] = useSearchParams()
  const [tenant, setTenant] = useState(AUTH_CONFIG.organization)
  const [tenantError, setTenantError] = useState('')
  async function submit(event: FormEvent) {
    event.preventDefault(); setTenantError('')
    if (AUTH_MODE === 'oidc') {
      const result = validateTenantSelection(tenant, AUTH_CONFIG.organization, AUTH_CONFIG.clientId)
      if (!result.ok) { setTenantError(result.message); return }
    }
    await auth.login(params.get('returnTo') ?? '/dashboard')
  }
  return <main className="login-page">
    <section className="login-story" aria-label="风控平台能力"><div className="login-grid-art" /><div className="login-story__content"><span className="env-pill"><span /> ZERO TRUST CONTROL PLANE</span><div className="login-brand"><span><ShieldCheck size={22} /></span><strong>RISK COMMAND</strong></div><h1>把风险<br /><em>看清楚。</em></h1><p>融合实时决策、客户画像、策略模型与案件运营，让每次判断都有依据，每次处置都有记录。</p><div className="login-features">{FEATURES.map(({ icon: Icon, title, description }) => <div className="login-feature" key={title}><span><Icon size={19} /></span><div><strong>{title}</strong><small>{description}</small></div></div>)}</div><small className="login-story__foot">实时决策 · 全程可溯 · 最小权限</small></div></section>
    <section className="login-card-wrap"><article className="login-card"><div className="login-mobile-brand"><span><ShieldCheck size={20} /></span><strong>RISK COMMAND</strong></div><span className="login-card__eyebrow">SECURE ACCESS</span><h2>{AUTH_MODE === 'oidc' ? '统一身份登录' : '本地开发模式'}</h2><p>{AUTH_MODE === 'oidc' ? '确认所属租户，使用统一企业身份安全登录。' : '使用预置开发身份进入风险控制台。'}</p><form className="login-form" onSubmit={submit}><Field label="所属租户"><div className="tenant-input"><Building2 size={17} /><Input value={tenant} onChange={(event) => { setTenant(event.target.value); setTenantError('') }} disabled={auth.redirecting || AUTH_MODE !== 'oidc'} autoComplete="organization" spellCheck={false} placeholder="请输入租户名称" /></div></Field><p className={tenantError ? 'tenant-help tenant-help--error' : 'tenant-help'} role={tenantError ? 'alert' : undefined}>{tenantError || `当前可用租户：${AUTH_CONFIG.organization}`}</p>{auth.error && <div className="notice notice--danger" role="alert">{auth.error}</div>}<LoadingButton type="submit" loading={auth.redirecting}>{auth.redirecting ? '正在跳转 Casdoor…' : AUTH_MODE === 'oidc' ? '使用统一身份登录' : '进入本地开发模式'}<ArrowRight size={16} /></LoadingButton></form><div className="security-note"><Fingerprint size={17} /><div><strong>统一身份认证 · PKCE 安全登录</strong><span>本系统不存储密码，会话仅在当前标签页有效。</span></div></div></article></section>
  </main>
}
