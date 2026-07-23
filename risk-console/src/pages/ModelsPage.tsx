import { Box, Check, Gauge, Plus, RotateCcw } from 'lucide-react'
import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { Button, ConfirmDialog, DataTable, Field, Input, LoadingButton, Modal, PageHeader, PageState, RiskBadge, useToast, type TableColumn } from '../components/ui'
import { useResource } from '../hooks/useResource'
import { api } from '../services/api'
import { errorMessage, type DataRecord } from './pageUtils'

const INITIAL_MODEL = { modelCode: 'fraud-rf', version: 1, artifactUri: 'http://localhost:9000/models/fraud-rf/model.pmml', checksum: '', trainingDataVersion: 'facts-v1', metrics: { auc: .9, ks: .5, recallAtFixedFpr: .65, precision: .75, recall: .7 } }

export default function ModelsPage() {
  const auth = useAuth(); const toast = useToast()
  const resource = useResource(['models'], () => api<DataRecord[]>('/api/v1/models'))
  const [open, setOpen] = useState(false)
  const [activation, setActivation] = useState<DataRecord | null>(null)
  const [rollbackModel, setRollbackModel] = useState<DataRecord | null>(null)
  const [rollout, setRollout] = useState(100)
  const [form, setForm] = useState(INITIAL_MODEL)
  const [saving, setSaving] = useState(false)
  async function run(task: () => Promise<unknown>, message: string) { setSaving(true); try { await task(); toast(message); setOpen(false); setActivation(null); setRollbackModel(null); void resource.reload() } catch (error) { toast(errorMessage(error), 'error') } finally { setSaving(false) } }
  function register() { void run(() => api('/api/v1/models', { method: 'POST', body: JSON.stringify(form) }), '模型制品已登记') }
  function action(row: DataRecord, name: string) {
    void run(() => api(`/api/v1/models/${row.model_id}/${name}`, { method: 'POST' }), name === 'approve' ? '模型版本已批准' : '模型运行版本已回滚')
  }
  function activate() { if (activation) void run(() => api(`/api/v1/models/${activation.model_id}/activate`, { method: 'POST', body: JSON.stringify({ rolloutPercentage: rollout }) }), '模型制品校验通过，流量配置已更新') }
  const columns: TableColumn<DataRecord>[] = [
    { key: 'model', label: '模型', render: (row) => <span className="model-name"><Box size={15} />{row.model_code}</span> },
    { key: 'version', label: '版本', render: (row) => `v${row.version_no}` },
    { key: 'status', label: '状态', render: (row) => <RiskBadge level={row.status} /> },
    { key: 'rollout', label: '流量', render: (row) => <span className="percentage"><i style={{ width: `${row.rollout_percentage}%` }} />{row.rollout_percentage}%</span> },
    { key: 'data', label: '训练数据', render: (row) => row.training_data_version },
    { key: 'metric', label: '评估指标', render: (row) => <span className="mono metrics-text">{row.metrics_json}</span> },
    { key: 'action', label: '操作', render: (row) => <div className="table-actions">{row.status === 'REGISTERED' && auth.can('model.approve') && <Button variant="ghost" icon={Check} onClick={() => action(row, 'approve')}>批准</Button>}{['APPROVED', 'CANARY'].includes(row.status) && auth.can('model.activate') && <Button variant="warning" icon={Gauge} onClick={() => { setActivation(row); setRollout(row.rollout_percentage ?? 100) }}>{row.status === 'CANARY' ? '调整灰度' : '激活'}</Button>}{['RETIRED', 'STABLE'].includes(row.status) && auth.can('model.activate') && <Button variant="warning" icon={RotateCcw} onClick={() => setRollbackModel(row)}>回滚</Button>}</div> },
  ]

  return <>
    <PageHeader eyebrow="MODEL LIFECYCLE" title="模型中心" description="集中评估、审批和激活模型制品，用确定性流量保持决策稳定。" actions={auth.can('model.write') && <Button variant="primary" icon={Plus} onClick={() => setOpen(true)}>登记模型版本</Button>} />
    <article className="panel"><PageState loading={resource.loading} error={resource.error} empty={resource.data?.length === 0} onRetry={() => void resource.reload()}><DataTable rows={resource.data ?? []} columns={columns} rowKey={(row) => row.model_id} /></PageState></article>
    <Modal open={open} onOpenChange={setOpen} title="登记模型制品" description="制品将在激活前完成完整性与兼容性校验。" wide footer={<><Button onClick={() => setOpen(false)}>取消</Button><LoadingButton loading={saving} onClick={register}>登记制品</LoadingButton></>}><div className="form-grid"><Field label="模型编码"><Input value={form.modelCode} onChange={(e) => setForm({ ...form, modelCode: e.target.value })} /></Field><Field label="版本"><Input type="number" min="1" value={form.version} onChange={(e) => setForm({ ...form, version: Number(e.target.value) })} /></Field><Field className="field--full" label="制品 URI"><Input value={form.artifactUri} onChange={(e) => setForm({ ...form, artifactUri: e.target.value })} /></Field><Field className="field--full" label="SHA-256"><Input className="input mono" value={form.checksum} onChange={(e) => setForm({ ...form, checksum: e.target.value })} placeholder="模型制品校验和" /></Field><Field label="训练数据版本"><Input value={form.trainingDataVersion} onChange={(e) => setForm({ ...form, trainingDataVersion: e.target.value })} /></Field></div></Modal>
    <Modal open={Boolean(activation)} onOpenChange={(value) => !value && setActivation(null)} title="模型灰度激活" description="相同 sourceId + txnId 将稳定命中同一模型版本。" footer={<><Button onClick={() => setActivation(null)}>取消</Button><LoadingButton loading={saving} onClick={activate}>校验制品并激活</LoadingButton></>}><div className="rollout-card"><span><Gauge size={20} /></span><div><small>新版本流量比例</small><strong>{rollout}%</strong></div></div><input className="range" type="range" min="0" max="100" value={rollout} onChange={(e) => setRollout(Number(e.target.value))} /><div className="range-labels"><span>仅验证</span><span>全量流量</span></div></Modal>
    <ConfirmDialog open={Boolean(rollbackModel)} onOpenChange={(value) => !value && setRollbackModel(null)} title="确认回滚模型" description={`将 ${rollbackModel?.model_code ?? '该模型'} v${rollbackModel?.version_no ?? ''} 重新切换为活动版本，线上流量将立即使用该制品。`} confirmLabel="确认回滚" onConfirm={() => rollbackModel && action(rollbackModel, 'rollback')} busy={saving} tone="warning" />
  </>
}
