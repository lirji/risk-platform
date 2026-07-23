import { Plus, RefreshCw } from 'lucide-react'
import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { Button, DataTable, Field, Input, LoadingButton, Modal, PageHeader, PageState, RiskBadge, useToast, type TableColumn } from '../components/ui'
import { useResource } from '../hooks/useResource'
import { api } from '../services/api'
import { errorMessage, formatDate, shortId, type DataRecord } from './pageUtils'

export default function RatingsPage() {
  const auth = useAuth(); const toast = useToast()
  const resource = useResource(['rating-jobs'], () => api<DataRecord[]>('/api/v1/ratings/jobs'))
  const [open, setOpen] = useState(false); const [saving, setSaving] = useState(false)
  const [form, setForm] = useState({ modelCode: 'RISK_M1', sourceIndex: 'cust-tags', targetIndex: 'es-risk-store' })
  async function create() { setSaving(true); try { await api('/api/v1/ratings/jobs', { method: 'POST', body: JSON.stringify(form) }); toast('评级任务已进入队列'); setOpen(false); void resource.reload() } catch (error) { toast(errorMessage(error), 'error') } finally { setSaving(false) } }
  async function retry(id: string) { try { await api(`/api/v1/ratings/jobs/${id}/retry`, { method: 'POST' }); toast('任务已重新排队'); void resource.reload() } catch (error) { toast(errorMessage(error), 'error') } }
  const columns: TableColumn<DataRecord>[] = [
    { key: 'id', label: '任务编号', render: (row) => <span className="mono text-strong" title={row.job_id}>{shortId(row.job_id, 18)}</span> },
    { key: 'model', label: '模型', render: (row) => row.model_code },
    { key: 'status', label: '状态', render: (row) => <RiskBadge level={row.status} /> },
    { key: 'attempts', label: '尝试', align: 'right', render: (row) => row.attempts },
    { key: 'worker', label: '执行器', render: (row) => row.lease_owner || '等待领取' },
    { key: 'time', label: '更新时间', render: (row) => formatDate(row.updated_at ?? row.created_at) },
    { key: 'error', label: '失败原因', render: (row) => <span className="truncate-cell" title={row.last_error}>{row.last_error || '—'}</span> },
    { key: 'action', label: '操作', render: (row) => row.status === 'FAILED' && auth.can('rating.write') ? <Button variant="warning" icon={RefreshCw} onClick={() => void retry(row.job_id)}>重试</Button> : '—' },
  ]
  return <>
    <PageHeader eyebrow="BATCH RISK RATING" title="评级任务" description="跟踪任务领取、租约恢复与批量写入，在失败时精准恢复执行。" actions={auth.can('rating.write') && <Button variant="primary" icon={Plus} onClick={() => setOpen(true)}>创建评级任务</Button>} />
    <article className="panel"><PageState loading={resource.loading} error={resource.error} empty={resource.data?.length === 0} onRetry={() => void resource.reload()}><DataTable rows={resource.data ?? []} columns={columns} rowKey={(row) => row.job_id} /></PageState></article>
    <Modal open={open} onOpenChange={setOpen} title="创建评级任务" description="任务会被可用执行器原子领取，并在租约过期后安全恢复。" footer={<><Button onClick={() => setOpen(false)}>取消</Button><LoadingButton loading={saving} onClick={() => void create()}>创建任务</LoadingButton></>}><div className="form-stack"><Field label="评级模型"><Input value={form.modelCode} onChange={(e) => setForm({ ...form, modelCode: e.target.value })} /></Field><Field label="来源索引"><Input value={form.sourceIndex} onChange={(e) => setForm({ ...form, sourceIndex: e.target.value })} /></Field><Field label="目标索引"><Input value={form.targetIndex} onChange={(e) => setForm({ ...form, targetIndex: e.target.value })} /></Field></div></Modal>
  </>
}
