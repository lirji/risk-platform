import { ArrowLeft, LockKeyhole } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Button } from '../components/ui'

export default function ForbiddenPage() {
  const navigate = useNavigate()
  return <main className="route-loader"><article className="access-card"><span><LockKeyhole size={28} /></span><small>ACCESS DENIED · 403</small><h1>没有访问权限</h1><p>当前角色不包含此功能所需权限。若职责已经变化，请联系平台管理员。</p><Button variant="primary" icon={ArrowLeft} onClick={() => navigate('/dashboard')}>返回风险总览</Button></article></main>
}
