import { RefreshCw, RotateCcw, Search, SlidersHorizontal } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Button, DataTable, Drawer, Field, Input, KeyValueGrid, LoadingButton, PageHeader, PageState, RiskBadge, Select, Textarea, useToast, type TableColumn } from '../components/ui'
import { useResource } from '../hooks/useResource'
import { api } from '../services/api'
import type { Decision, PageResponse } from '../types'
import { errorMessage, formatDate, type DataRecord } from './pageUtils'

export default function DecisionsPage() {
  const [params] = useSearchParams()
  const query = params.get('q') ?? ''
  return <DecisionsContent key={query} query={query} />
}

function DecisionsContent({ query }: { query: string }) {
  const auth = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const [search, setSearch] = useState(query)
  const [level, setLevel] = useState('')
  const [selected, setSelected] = useState<DataRecord | null>(null)
  const [drawer, setDrawer] = useState(false)
  const [reason, setReason] = useState('')
  const [replaying, setReplaying] = useState(false)
  const resource = useResource(['decisions', query, level], () => {
    const values = new URLSearchParams({ size: '50' })
    if (level) values.set('riskLevel', level)
    if (query) values.set('q', query)
    return api<PageResponse<Decision>>(`/api/v1/decisions?${values}`)
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    navigate(search.trim() ? `/decisions?q=${encodeURIComponent(search.trim())}` : '/decisions')
  }
  async function openDetail(row: Decision) {
    try { setSelected(await api<DataRecord>(`/api/v1/decisions/${row.decisionId}`)); setReason(''); setDrawer(true) }
    catch (error) { toast(errorMessage(error), 'error') }
  }
  async function replay() {
    if (!selected || reason.trim().length < 3) return
    setReplaying(true)
    try {
      await api(`/api/v1/decisions/${selected.decision_id ?? selected.decisionId}/replay`, { method: 'POST', body: JSON.stringify({ reason: reason.trim() }) })
      toast('已创建新的审计重放事件'); setReason('')
    } catch (error) { toast(errorMessage(error), 'error') }
    finally { setReplaying(false) }
  }
  const columns: TableColumn<Decision>[] = [
    { key: 'txn', label: '交易流水', render: (row) => <span className="mono text-strong">{row.txnId}</span> },
    { key: 'source', label: '来源', render: (row) => row.sourceId },
    { key: 'risk', label: '等级', render: (row) => <RiskBadge level={row.riskLevel} /> },
    { key: 'action', label: '动作', render: (row) => <span className="action-label">{row.action}</span> },
    { key: 'score', label: '模型分', align: 'right', render: (row) => Number(row.fraudScore).toFixed(3) },
    { key: 'cost', label: '耗时', align: 'right', render: (row) => `${row.costMs} ms` },
    { key: 'time', label: '决策时间', align: 'right', render: (row) => formatDate(row.createdAt) },
  ]

  return <>
    <PageHeader eyebrow="DECISION EVIDENCE" title="风险决策" description="检索不可变决策快照，核对命中的规则、模型版本与响应耗时。" />
    <article className="panel"><form className="toolbar" onSubmit={submit}><div className="search-input"><Search size={16} /><Input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="输入交易流水" aria-label="交易流水筛选" /></div><div className="select-wrap"><SlidersHorizontal size={15} /><Select value={level} onChange={(event) => setLevel(event.target.value)} aria-label="风险等级"><option value="">全部风险等级</option>{['LOW', 'MEDIUM', 'HIGH', 'REJECT'].map((item) => <option value={item} key={item}>{item}</option>)}</Select></div><Button variant="primary" icon={Search} type="submit">搜索</Button><Button className={resource.refreshing ? 'is-loading' : undefined} icon={RefreshCw} type="button" aria-busy={resource.refreshing} disabled={resource.refreshing} onClick={() => void resource.reload()}>{resource.refreshing ? '刷新中' : '刷新'}</Button><span className="toolbar__meta">TOTAL <b>{resource.data?.total ?? 0}</b></span></form><PageState loading={resource.loading} error={resource.error} empty={resource.data?.content.length === 0} onRetry={() => void resource.reload()}><DataTable rows={resource.data?.content ?? []} columns={columns} rowKey={(row) => row.decisionId} onRowClick={openDetail} /></PageState></article>
    <Drawer open={drawer} onOpenChange={setDrawer} title="决策证据快照" footer={auth.can('decision.replay') && <div className="drawer-actions"><Field label="重放原因" hint="至少 3 个字符；原始事件不会修改。"><Textarea value={reason} onChange={(event) => setReason(event.target.value)} rows={3} placeholder="说明授权重放原因" /></Field><LoadingButton loading={replaying} disabled={reason.trim().length < 3} onClick={() => void replay()}><RotateCcw size={15} />授权重放</LoadingButton></div>}><>{selected && <KeyValueGrid values={selected} />}</></Drawer>
  </>
}
