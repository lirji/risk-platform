import { Activity, AlertOctagon, BriefcaseBusiness, Radar, RefreshCw, ShieldCheck } from 'lucide-react'
import { Link } from 'react-router-dom'
import RiskChart from '../components/RiskChart'
import { Button, DataTable, MetricCard, PageHeader, PageState, RiskBadge, SectionHeader, type TableColumn } from '../components/ui'
import { useResource } from '../hooks/useResource'
import { api } from '../services/api'
import type { Decision, PageResponse } from '../types'
import { formatDate, type DataRecord } from './pageUtils'

export default function DashboardPage() {
  const resource = useResource(['dashboard'], async () => {
    const [decisions, cases, dead] = await Promise.all([
      api<PageResponse<Decision>>('/api/v1/decisions?size=100'),
      api<DataRecord[]>('/api/v1/cases'),
      api<DataRecord[]>('/api/v1/operations/dead-events'),
    ])
    return { decisions, cases, dead }
  })
  const decisions = resource.data?.decisions.content ?? []
  const high = decisions.filter((item) => ['HIGH', 'REJECT'].includes(item.riskLevel)).length
  const openCases = resource.data?.cases.filter((item) => item.status !== 'RESOLVED').length ?? 0
  const buckets = ['LOW', 'MEDIUM', 'HIGH', 'REJECT'].map((level) => decisions.filter((item) => item.riskLevel === level).length)
  const columns: TableColumn<Decision>[] = [
    { key: 'txn', label: '交易流水', render: (row) => <span className="mono text-strong">{row.txnId}</span> },
    { key: 'source', label: '来源', render: (row) => row.sourceId },
    { key: 'risk', label: '风险', render: (row) => <RiskBadge level={row.riskLevel} /> },
    { key: 'action', label: '决策动作', render: (row) => <span className="action-label">{row.action}</span> },
    { key: 'time', label: '时间', align: 'right', render: (row) => formatDate(row.createdAt) },
  ]

  return <>
    <PageHeader eyebrow="COMMAND OVERVIEW" title="实时风险态势" description="把决策、案件与数据链路收拢到一张可操作的风险地图。" actions={<Button className={resource.refreshing ? 'is-loading' : undefined} icon={RefreshCw} aria-busy={resource.refreshing} onClick={() => void resource.reload()} disabled={resource.refreshing}>{resource.refreshing ? '正在刷新' : '刷新数据'}</Button>} />
    <PageState loading={resource.loading} error={resource.error} onRetry={() => void resource.reload()}>{resource.data && <>
      <section className="metric-grid">
        <MetricCard icon={AlertOctagon} label="近期待审决策" value={high} hint="高风险与拒绝" tone="red" trend={`${Math.round(high / Math.max(decisions.length, 1) * 100)}%`} />
        <MetricCard icon={BriefcaseBusiness} label="未结案件" value={openCases} hint="等待分析师处置" tone="amber" />
        <MetricCard icon={Radar} label="决策样本" value={resource.data.decisions.total} hint="当前检索窗口" tone="green" />
        <MetricCard icon={Activity} label="死信事件" value={resource.data.dead.length} hint="需要授权重放" tone={resource.data.dead.length ? 'red' : 'blue'} />
      </section>
      <section className="dashboard-grid section-gap">
        <article className="panel panel--chart"><SectionHeader title="风险等级分布" meta="最近 100 条决策" /><RiskChart labels={['低风险', '中风险', '高风险', '拒绝']} values={buckets} summary="最近决策按风险等级分布" /></article>
        <article className="panel health-panel"><SectionHeader title="链路健康" meta={<span className="live-label"><span /> LIVE</span>} /><div className="health-score"><div className="health-score__ring"><strong>{resource.data.dead.length ? '92' : '99'}</strong><small>/ 100</small></div><div><strong>{resource.data.dead.length ? '链路轻度降级' : '全部系统运行正常'}</strong><p>数据新鲜度实时，决策持久化正常。</p></div></div><div className="health-list"><div><span><ShieldCheck size={16} />决策持久化</span><b className="status-ok">正常</b></div><div><span><Activity size={16} />Outbox 队列</span><b className={resource.data.dead.length ? 'status-warn' : 'status-ok'}>{resource.data.dead.length ? '存在死信' : '无积压'}</b></div><div><span><BriefcaseBusiness size={16} />案件闭环</span><b>{openCases} 待处理</b></div></div><Link className="panel-link" to="/operations">进入运维中心 <span>→</span></Link></article>
      </section>
      <article className="panel section-gap"><SectionHeader title="最新风险决策" meta={`${decisions.length} 条样本`} action={<Link className="text-link" to="/decisions">查看全部</Link>} /><DataTable rows={decisions.slice(0, 7)} columns={columns} rowKey={(row) => row.decisionId} /></article>
    </>}</PageState>
  </>
}
