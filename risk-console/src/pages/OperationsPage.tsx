import { Activity, DatabaseBackup, HeartPulse, RefreshCw, RotateCcw, Waypoints } from 'lucide-react'
import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { Button, ConfirmDialog, DataTable, MetricCard, PageHeader, PageState, SectionHeader, useToast, type TableColumn } from '../components/ui'
import { useResource } from '../hooks/useResource'
import { api } from '../services/api'
import { errorMessage, formatDate, shortId, type DataRecord } from './pageUtils'

export default function OperationsPage() {
  const auth = useAuth(); const toast = useToast()
  const resource = useResource(['operations'], async () => ({ health: await api<DataRecord>('/actuator/health'), dead: await api<DataRecord[]>('/api/v1/operations/dead-events') }))
  const [replayEvent, setReplayEvent] = useState<DataRecord | null>(null)
  const [replaying, setReplaying] = useState(false)
  async function replay() {
    if (!replayEvent) return
    setReplaying(true)
    try { await api(`/api/v1/operations/dead-events/${replayEvent.event_id}/replay`, { method: 'POST' }); toast(replayEvent.event_kind === 'KAFKA_DLT' ? '事件已重新发送到原 Topic' : '事件已重新进入 Outbox 队列'); setReplayEvent(null); void resource.reload() }
    catch (error) { toast(errorMessage(error), 'error') }
    finally { setReplaying(false) }
  }
  const columns: TableColumn<DataRecord>[] = [
    { key: 'id', label: '事件', render: (row) => <span className="mono text-strong" title={row.event_id}>{shortId(row.event_id, 20)}</span> },
    { key: 'kind', label: '来源', render: (row) => <span className="source-label">{row.event_kind}</span> },
    { key: 'topic', label: '原 Topic', render: (row) => <span className="mono">{row.topic || '—'}</span> },
    { key: 'attempts', label: '尝试', align: 'right', render: (row) => row.attempts },
    { key: 'time', label: '最近失败', render: (row) => formatDate(row.updated_at ?? row.created_at) },
    { key: 'error', label: '最后错误', render: (row) => <span className="truncate-cell" title={row.last_error}>{row.last_error}</span> },
    { key: 'action', label: '操作', render: (row) => auth.can('ops.replay') ? <Button variant="warning" icon={RotateCcw} onClick={() => setReplayEvent(row)}>授权重放</Button> : '—' },
  ]
  return <>
    <PageHeader eyebrow="SYSTEM RESILIENCE" title="运维中心" description="监控服务健康与消息死信，用受控重放恢复数据链路。" actions={<Button className={resource.refreshing ? 'is-loading' : undefined} icon={RefreshCw} aria-busy={resource.refreshing} disabled={resource.refreshing} onClick={() => void resource.reload()}>{resource.refreshing ? '正在刷新' : '刷新状态'}</Button>} />
    <PageState loading={resource.loading} error={resource.error} onRetry={() => void resource.reload()}>{resource.data && <><section className="metric-grid"><MetricCard icon={HeartPulse} label="管理服务" value={resource.data.health.status} hint="Spring health" tone={resource.data.health.status === 'UP' ? 'green' : 'red'} /><MetricCard icon={DatabaseBackup} label="统一死信" value={resource.data.dead.length} hint="Outbox 与 Kafka DLT" tone={resource.data.dead.length ? 'red' : 'blue'} /><MetricCard icon={Waypoints} label="消费者策略" value="3 + DLT" hint="有限退避重试" tone="blue" /><MetricCard icon={Activity} label="状态恢复" value="启用" hint="Checkpoint / lease" tone="green" /></section><article className="panel section-gap"><SectionHeader title="死信事件" meta="重放操作会写入审计日志" /><PageState empty={resource.data.dead.length === 0} emptyText="当前没有死信事件"><DataTable rows={resource.data.dead} columns={columns} rowKey={(row) => row.event_id} /></PageState></article></>}</PageState>
    <ConfirmDialog open={Boolean(replayEvent)} onOpenChange={(open) => !open && setReplayEvent(null)} title="确认重放死信事件" description={`事件 ${replayEvent?.event_id ?? ''} 将重新进入消息链路。请先确认下游故障已经恢复。`} confirmLabel="授权并重放" onConfirm={() => void replay()} busy={replaying} tone="warning" />
  </>
}
