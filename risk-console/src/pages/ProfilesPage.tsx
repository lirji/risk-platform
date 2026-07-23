import { Search, UserRoundSearch } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Button, Input, PageHeader, PageState, SectionHeader } from '../components/ui'
import { useResource } from '../hooks/useResource'
import { api } from '../services/api'
import { type DataRecord } from './pageUtils'

export default function ProfilesPage() {
  const [input, setInput] = useState('')
  const [account, setAccount] = useState('')
  const resource = useResource(['profile', account], () => api<DataRecord>(`/api/v1/profiles/${encodeURIComponent(account)}`), Boolean(account))
  function submit(event: FormEvent) { event.preventDefault(); if (input.trim()) setAccount(input.trim()) }
  const profile = resource.data
  return <>
    <PageHeader eyebrow="CUSTOMER INTELLIGENCE" title="客户画像" description="融合实时特征、离线标签与口径版本，快速建立客户风险上下文。" />
    <article className="panel"><form className="toolbar profile-search" onSubmit={submit}><div className="search-input search-input--wide"><Search size={16} /><Input value={input} onChange={(event) => setInput(event.target.value)} placeholder="输入客户账号" aria-label="客户账号" /></div><Button variant="primary" icon={UserRoundSearch} type="submit" disabled={!input.trim()}>查询画像</Button></form>
      {!account ? <div className="state profile-empty"><span className="profile-empty__art"><UserRoundSearch size={34} /></span><h3>查询一个客户</h3><p>账号仅用于在线查询，页面与审计默认展示掩码数据。</p><div className="empty-chips"><span>实时特征</span><span>离线标签</span><span>口径版本</span></div></div> : <PageState loading={resource.loading} error={resource.error} onRetry={() => void resource.reload()}>{profile && <>{!profile.available && <div className="notice notice--warning">实时特征存储不可用，当前展示降级画像。</div>}<section className="profile-grid section-gap"><article className="panel panel--nested"><SectionHeader title="实时特征" meta={profile.accountNo} /><div className="feature-grid">{Object.entries(profile.onlineFeatures ?? {}).map(([key, value]) => <div key={key}><label>{key}</label><strong>{String(value ?? '—')}</strong></div>)}</div></article><article className="panel panel--nested"><SectionHeader title="标签口径" meta={`${profile.definitions?.length ?? 0} 项`} /><div className="tag-list">{(profile.definitions ?? []).map((tag: DataRecord) => <div className="tag-card" key={tag.tag_code}><span>{tag.tag_code}</span><strong>{tag.tag_name}</strong><p>{tag.definition_text}</p><small>口径版本 v{tag.version_no}</small></div>)}</div></article></section></>}</PageState>}
    </article>
  </>
}
