import { GitCompareArrows, Plus, Rocket, RotateCcw } from 'lucide-react'
import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { Button, ConfirmDialog, DataTable, Field, Input, LoadingButton, Modal, PageHeader, PageState, RiskBadge, SectionHeader, Textarea, useToast, type TableColumn } from '../components/ui'
import { useResource } from '../hooks/useResource'
import { api } from '../services/api'
import { errorMessage, type DataRecord } from './pageUtils'

type Binding = { sourceId: string; activeReleaseId: string; previousReleaseId?: string; ruleSets: string[]; rolloutPercentage: number; shadowReleaseId?: string }
const INITIAL_DRL = `package rules.fraud;

rule "new-rule"
  agenda-group "threshold"
when
then
end`

export default function RulesPage() {
  const auth = useAuth(); const toast = useToast()
  const resource = useResource(['rules'], async () => ({ releases: await api<DataRecord[]>('/api/v1/rules/releases'), bindings: await api<Binding[]>('/api/v1/rules/bindings') }))
  const [createOpen, setCreateOpen] = useState(false)
  const [publishRow, setPublishRow] = useState<DataRecord | null>(null)
  const [rollbackRow, setRollbackRow] = useState<DataRecord | null>(null)
  const [form, setForm] = useState({ ruleCode: '', ruleName: '', drl: INITIAL_DRL })
  const [deployment, setDeployment] = useState({ sourceId: 'MOBILE_TRANSFER', ruleSets: 'blacklist,threshold,model', rolloutPercentage: 100, shadowReleaseId: '' })
  const [saving, setSaving] = useState(false)

  async function run(task: () => Promise<unknown>, message: string) {
    setSaving(true)
    try { await task(); toast(message); setCreateOpen(false); setPublishRow(null); setRollbackRow(null); void resource.reload() }
    catch (error) { toast(errorMessage(error), 'error') }
    finally { setSaving(false) }
  }
  function create() { void run(() => api('/api/v1/rules/releases', { method: 'POST', body: JSON.stringify(form) }), '规则草稿已创建') }
  function action(row: DataRecord, name: string) { void run(() => api(`/api/v1/rules/releases/${row.releaseId}/${name}`, { method: 'POST' }), name === 'submit' ? '规则已提交复核' : '规则版本已批准') }
  function publish() {
    if (!publishRow) return
    const ruleSets = deployment.ruleSets.split(',').map((item) => item.trim()).filter(Boolean)
    if (!ruleSets.length) { toast('至少配置一个规则集', 'error'); return }
    void run(() => api(`/api/v1/rules/releases/${publishRow.releaseId}/publish`, { method: 'POST', body: JSON.stringify({ ...deployment, ruleSets, shadowReleaseId: deployment.shadowReleaseId || null }) }), '运行时编译通过，规则版本已原子切换')
  }
  function rollback() {
    if (!rollbackRow) return
    void run(() => api(`/api/v1/rules/releases/${rollbackRow.releaseId}/rollback`, { method: 'POST', body: JSON.stringify({ sourceId: deployment.sourceId }) }), '规则运行时已回滚')
  }
  const bindingColumns: TableColumn<Binding>[] = [
    { key: 'source', label: '业务来源', render: (row) => <span className="source-label">{row.sourceId}</span> },
    { key: 'active', label: '当前版本', render: (row) => <span className="mono">{row.activeReleaseId}</span> },
    { key: 'flow', label: '规则集 / 决策流', render: (row) => <div className="flow-tags">{row.ruleSets.map((item, index) => <span key={item}>{item}{index < row.ruleSets.length - 1 && <b>→</b>}</span>)}</div> },
    { key: 'rollout', label: '灰度比例', render: (row) => <span className="percentage"><i style={{ width: `${row.rolloutPercentage}%` }} />{row.rolloutPercentage}%</span> },
    { key: 'shadow', label: 'Shadow 版本', render: (row) => row.shadowReleaseId || '—' },
  ]
  const releaseColumns: TableColumn<DataRecord>[] = [
    { key: 'code', label: '规则编码', render: (row) => <span className="mono text-strong">{row.ruleCode}</span> },
    { key: 'name', label: '规则名称', render: (row) => row.ruleName },
    { key: 'version', label: '版本', render: (row) => `v${row.version}` },
    { key: 'status', label: '状态', render: (row) => <RiskBadge level={row.status} /> },
    { key: 'author', label: '作者', render: (row) => row.authorId },
    { key: 'action', label: '操作', render: (row) => <div className="table-actions">{row.status === 'DRAFT' && auth.can('rule.write') && <Button variant="ghost" onClick={() => action(row, 'submit')}>提审</Button>}{row.status === 'IN_REVIEW' && auth.can('rule.approve') && <Button variant="warning" onClick={() => action(row, 'approve')}>批准</Button>}{row.status === 'APPROVED' && auth.can('rule.publish') && <Button variant="danger" icon={Rocket} onClick={() => setPublishRow(row)}>发布</Button>}{row.status === 'PUBLISHED' && auth.can('rule.publish') && <Button variant="warning" icon={RotateCcw} onClick={() => setRollbackRow(row)}>回滚</Button>}</div> },
  ]

  return <>
    <PageHeader eyebrow="POLICY GOVERNANCE" title="规则治理" description="用可审计的版本流管理草稿、复核、灰度发布与快速回滚。" actions={auth.can('rule.write') && <Button variant="primary" icon={Plus} onClick={() => setCreateOpen(true)}>新建规则版本</Button>} />
    <PageState loading={resource.loading} error={resource.error} onRetry={() => void resource.reload()}>{resource.data && <><article className="panel"><SectionHeader title="来源与决策流绑定" meta={`${resource.data.bindings.length} 个来源`} action={<span className="legend"><GitCompareArrows size={14} /> 稳定流量绑定</span>} /><DataTable rows={resource.data.bindings} columns={bindingColumns} rowKey={(row) => row.sourceId} emptyText="尚无已发布绑定" /></article><article className="panel section-gap"><SectionHeader title="规则版本" meta={`${resource.data.releases.length} 个版本`} /><DataTable rows={resource.data.releases} columns={releaseColumns} rowKey={(row) => row.releaseId} emptyText="尚无规则版本" /></article></>}</PageState>
    <Modal open={createOpen} onOpenChange={setCreateOpen} title="新建规则版本" description="新版本先以草稿保存，通过双人复核后才能发布。" wide footer={<><Button onClick={() => setCreateOpen(false)}>取消</Button><LoadingButton loading={saving} disabled={!form.ruleCode.trim() || !form.ruleName.trim()} onClick={create}>保存草稿</LoadingButton></>}><div className="form-grid"><Field label="规则编码"><Input value={form.ruleCode} onChange={(e) => setForm({ ...form, ruleCode: e.target.value })} placeholder="TRANSFER_THRESHOLD" /></Field><Field label="规则名称"><Input value={form.ruleName} onChange={(e) => setForm({ ...form, ruleName: e.target.value })} placeholder="大额转账阈值" /></Field><Field className="field--full" label="DRL 规则内容"><Textarea className="mono code-editor" rows={15} value={form.drl} onChange={(e) => setForm({ ...form, drl: e.target.value })} /></Field></div></Modal>
    <Modal open={Boolean(publishRow)} onOpenChange={(open) => !open && setPublishRow(null)} title="配置发布流量" description="编译验证通过后将原子切换运行时版本。" footer={<><Button onClick={() => setPublishRow(null)}>取消</Button><LoadingButton loading={saving} onClick={publish}>编译并发布</LoadingButton></>}><div className="form-stack"><Field label="来源 ID"><Input value={deployment.sourceId} onChange={(e) => setDeployment({ ...deployment, sourceId: e.target.value })} /></Field><Field label="规则集 / 决策流" hint="使用逗号分隔，按顺序执行"><Input value={deployment.ruleSets} onChange={(e) => setDeployment({ ...deployment, ruleSets: e.target.value })} /></Field><Field label={`新版本流量比例 · ${deployment.rolloutPercentage}%`}><input className="range" type="range" min="0" max="100" value={deployment.rolloutPercentage} onChange={(e) => setDeployment({ ...deployment, rolloutPercentage: Number(e.target.value) })} /></Field><Field label="Shadow 版本 ID" hint="可选，仅用于双跑比对"><Input value={deployment.shadowReleaseId} onChange={(e) => setDeployment({ ...deployment, shadowReleaseId: e.target.value })} /></Field></div></Modal>
    <ConfirmDialog open={Boolean(rollbackRow)} onOpenChange={(open) => !open && setRollbackRow(null)} title="确认回滚规则版本" description={`将 ${deployment.sourceId} 切回上一已发布版本。运行时流量会立即受到影响。`} confirmLabel="确认回滚" onConfirm={rollback} busy={saving} tone="warning" />
  </>
}
