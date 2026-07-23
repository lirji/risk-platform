import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { PageState } from '../components/ui'

export default function AuthCallbackPage() {
  const { completeLogin } = useAuth(); const navigate = useNavigate(); const [error, setError] = useState('')
  useEffect(() => { let active = true; void completeLogin().then((path) => active && navigate(path, { replace: true })).catch((cause) => active && setError(cause instanceof Error ? cause.message : '登录回调处理失败')); return () => { active = false } }, [completeLogin, navigate])
  return <main className="route-loader"><PageState loading={!error} error={error} onRetry={() => navigate('/login', { replace: true })} /></main>
}
