import { CheckCircle2, Hand, ShieldAlert } from 'lucide-react'
import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { Button, DataTable, Field, LoadingButton, Modal, PageHeader, PageState, RiskBadge, Textarea, useToast, type TableColumn } from '../components/ui'
import { useResource } from '../hooks/useResource'
import { api } from '../services/api'
import { errorMessage, formatDate, type DataRecord } from './pageUtils'

const FILTERS = [{ value: 'OPEN', label: '待认领' }, { value: 'CLAIMED', label: '处理中' }, { value: 'RESOLVED', label: '已结案' }]

export default function CasesPage() {
  const auth = useAuth(); const toast = useToast()
  const [status, setStatus] = useState('OPEN')
  const [selected, setSelected] = useState<DataRecord | null>(null)
  const [reason, setReason] = useState('')
  const [label, setLabel] = useState<'FRAUD' | 'NORMAL'>('FRAUD')
  const [saving, setSaving] = useState(false)
  const resource = useResource(['cases', status], () => api<DataRecord[]>(`/api/v1/cases?status=${status}`))
  async function claim(row: DataRecord) {
    try { await api(`/api/v1/cases/${row.case_id}/claim`, { method: 'POST' }); toast('案件已认领'); void resource.reload() }
    catch (error) { toast(errorMessage(error), 'error') }
  }
  async function resolve() {
    if (!selected || reason.trim().length < 3) return
    setSaving(true)
    try { await api(`/api/v1/cases/${selected.case_id}/resolve`, { method: 'POST', body: JSON.stringify({ label, reason: reason.trim() }) }); toast('案件已结案，训练标签已回流'); setSelected(null); void resource.reload() }
    catch (error) { toast(errorMessage(error), 'error') }
    finally { setSaving(false) }
  }
  const columns: TableColumn<DataRecord>[] = [
    { key: 'id', label: '案件编号', render: (row) => <span className="mono text-strong">{row.case_id}</span> },
    { key: 'txn', label: '交易流水', render: (row) => <span className="mono">{row.txn_id}</span> },
    { key: 'risk', label: '风险', render: (row) => <RiskBadge level={row.risk_level} /> },
    { key: 'status', label: '状态', render: (row) => <span className="status-text">{row.status}</span> },
    { key: 'assignee', label: '处理人', render: (row) => row.assignee || '—' },
    { key: 'created', label: '创建时间', render: (row) => formatDate(row.created_at) },
    { key: 'actions', label: '操作', render: (row) => auth.can('case.write') && (row.status === 'OPEN' ? <Button variant="ghost" icon={Hand} onClick={(event) => { event.stopPropagation(); void claim(row) }}>认领</Button> : row.status === 'CLAIMED' ? <Button variant="warning" icon={CheckCircle2} onClick={(event) => { event.stopPropagation(); setReason(''); setSelected(row) }}>结案</Button> : '—') },
  ]

  return <>
    <PageHeader eyebrow="CASE OPERATIONS" title="案件中心" description="从认领、研判到结案标签回流，让高风险处置始终有迹可循。" />
    <article className="panel"><div className="segmented" role="tablist" aria-label="案件状态">{FILTERS.map((item) => <button role="tab" aria-selected={status === item.value} className={status === item.value ? 'is-active' : ''} key={item.value} onClick={() => setStatus(item.value)}>{item.label}</button>)}</div><PageState loading={resource.loading} error={resource.error} empty={resource.data?.length === 0} emptyText="当前队列已清空" onRetry={() => void resource.reload()}><DataTable rows={resource.data ?? []} columns={columns} rowKey={(row) => row.case_id} /></PageState></article>
    <Modal open={Boolean(selected)} onOpenChange={(open) => !open && setSelected(null)} title="案件结论与标签回流" description="结案结果会成为模型训练的权威标签，请确认依据完整。" footer={<><Button onClick={() => setSelected(null)}>取消</Button><LoadingButton loading={saving} disabled={reason.trim().length < 3} onClick={() => void resolve()}>确认结案</LoadingButton></>}><div className="form-stack"><div className="label-choice"><button className={label === 'FRAUD' ? 'is-active is-danger' : ''} onClick={() => setLabel('FRAUD')}><ShieldAlert />确认欺诈<small>回流 FRAUD 标签</small></button><button className={label === 'NORMAL' ? 'is-active' : ''} onClick={() => setLabel('NORMAL')}><CheckCircle2 />确认正常<small>回流 NORMAL 标签</small></button></div><Field label="结论依据" hint="至少 3 个字符"><Textarea rows={5} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="记录证据、研判过程与处置结论" /></Field></div></Modal>
  </>
}
