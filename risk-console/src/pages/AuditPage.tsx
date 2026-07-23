import { RefreshCw, ScrollText } from 'lucide-react'
import { Button, DataTable, PageHeader, PageState, RiskBadge, type TableColumn } from '../components/ui'
import { useResource } from '../hooks/useResource'
import { api } from '../services/api'
import { formatDate, shortId, type DataRecord } from './pageUtils'

export default function AuditPage() {
  const resource = useResource(['audit'], () => api<DataRecord[]>('/api/v1/audit'))
  const columns: TableColumn<DataRecord>[] = [
    { key: 'time', label: '时间', render: (row) => formatDate(row.created_at) },
    { key: 'actor', label: '操作者', render: (row) => <span className="actor"><span>{String(row.actor_id ?? 'S').slice(0, 1).toUpperCase()}</span>{row.actor_id}</span> },
    { key: 'action', label: '动作', render: (row) => <RiskBadge level={row.action} /> },
    { key: 'resource', label: '资源', render: (row) => row.resource_type },
    { key: 'id', label: '资源 ID', render: (row) => <span className="mono" title={row.resource_id}>{shortId(row.resource_id, 20)}</span> },
    { key: 'detail', label: '详情', render: (row) => <span className="mono truncate-cell" title={row.details_json}>{row.details_json}</span> },
  ]
  return <>
    <PageHeader eyebrow="IMMUTABLE EVIDENCE" title="审计日志" description="保留规则、模型、案件和恢复操作的完整责任证据。" actions={<Button className={resource.refreshing ? 'is-loading' : undefined} icon={RefreshCw} aria-busy={resource.refreshing} disabled={resource.refreshing} onClick={() => void resource.reload()}>{resource.refreshing ? '正在刷新' : '刷新日志'}</Button>} />
    <article className="panel"><div className="audit-note"><ScrollText size={17} /><span>审计日志仅追加，不允许在线修改或删除。</span></div><PageState loading={resource.loading} error={resource.error} empty={resource.data?.length === 0} onRetry={() => void resource.reload()}><DataTable rows={resource.data ?? []} columns={columns} rowKey={(row) => String(row.id ?? `${row.created_at}-${row.resource_id}`)} /></PageState></article>
  </>
}
