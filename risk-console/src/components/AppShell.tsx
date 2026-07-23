import * as DropdownMenu from '@radix-ui/react-dropdown-menu'
import clsx from 'clsx'
import { Activity, BookOpenCheck, Boxes, BriefcaseBusiness, ChevronDown, CircleGauge, DatabaseZap, LogOut, Menu, PanelLeftClose, PanelLeftOpen, Search, Settings2, ShieldCheck, Sparkles, UserRoundSearch, X } from 'lucide-react'
import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { IconButton } from './ui'

const NAV = [
  { to: '/dashboard', label: '风险总览', hint: '实时态势', icon: CircleGauge },
  { to: '/decisions', label: '风险决策', hint: '证据与重放', icon: ShieldCheck, permission: 'decision.read' },
  { to: '/cases', label: '案件中心', hint: '复核闭环', icon: BriefcaseBusiness, permission: 'case.read' },
  { to: '/profiles', label: '客户画像', hint: '特征与标签', icon: UserRoundSearch, permission: 'profile.read' },
  { to: '/rules', label: '规则治理', hint: '版本与发布', icon: BookOpenCheck, permission: 'rule.read' },
  { to: '/models', label: '模型中心', hint: '评估与灰度', icon: Boxes, permission: 'model.read' },
  { to: '/ratings', label: '评级任务', hint: '批处理运行', icon: DatabaseZap, permission: 'rating.read' },
  { to: '/operations', label: '运维中心', hint: '健康与恢复', icon: Activity, permission: 'ops.read' },
  { to: '/audit', label: '审计日志', hint: '操作证据', icon: Settings2, permission: 'audit.read' },
]

export default function AppShell() {
  const auth = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [collapsed, setCollapsed] = useState(() => window.localStorage.getItem('risk-sidebar-collapsed') === '1')
  const [mobileOpen, setMobileOpen] = useState(false)
  const [search, setSearch] = useState('')
  const searchRef = useRef<HTMLInputElement>(null)
  const items = useMemo(() => NAV.filter((item) => auth.can(item.permission)), [auth])
  const current = NAV.find((item) => location.pathname.startsWith(item.to))

  useEffect(() => { window.localStorage.setItem('risk-sidebar-collapsed', collapsed ? '1' : '0') }, [collapsed])
  useEffect(() => {
    function handleShortcut(event: KeyboardEvent) {
      const target = event.target
      const editing = target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement || (target instanceof HTMLElement && target.isContentEditable)
      if (event.key === '/' && !editing && !event.metaKey && !event.ctrlKey && !event.altKey) {
        event.preventDefault()
        searchRef.current?.focus()
      }
      if (event.key === 'Escape' && document.activeElement === searchRef.current) {
        setSearch('')
        searchRef.current?.blur()
      }
    }
    window.addEventListener('keydown', handleShortcut)
    return () => window.removeEventListener('keydown', handleShortcut)
  }, [])

  function submitSearch(event: FormEvent) {
    event.preventDefault()
    if (!search.trim()) return
    setMobileOpen(false)
    navigate(`/decisions?q=${encodeURIComponent(search.trim())}`)
  }

  return <div className={clsx('app-shell', collapsed && 'app-shell--collapsed', mobileOpen && 'app-shell--mobile-open')}>
    <button className="mobile-scrim" aria-label="关闭菜单" onClick={() => setMobileOpen(false)} />
    <aside className="sidebar" aria-label="主导航">
      <div className="brand"><span className="brand__mark"><ShieldCheck size={20} /></span><div><strong>RISK COMMAND</strong><small>CONTROL PLANE</small></div><IconButton className="sidebar__mobile-close" label="关闭菜单" icon={X} onClick={() => setMobileOpen(false)} /></div>
      <div className="sidebar__status"><span className="pulse-dot" /><div><strong>风险引擎在线</strong><small>决策链路运行正常</small></div></div>
      <nav className="nav-list">{items.map(({ to, label, hint, icon: Icon }) => <NavLink key={to} to={to} onClick={() => setMobileOpen(false)} className={({ isActive }) => clsx('nav-item', isActive && 'nav-item--active')} title={collapsed ? label : undefined}><Icon size={18} /><span><strong>{label}</strong><small>{hint}</small></span></NavLink>)}</nav>
      <div className="sidebar__foot"><div className="version-card"><Sparkles size={15} /><span><strong>Risk Platform</strong><small>v1.0 · production ready</small></span></div><IconButton label={collapsed ? '展开侧边栏' : '收起侧边栏'} icon={collapsed ? PanelLeftOpen : PanelLeftClose} onClick={() => setCollapsed((value) => !value)} /></div>
    </aside>
    <div className="workspace">
      <header className="topbar">
        <IconButton className="mobile-menu" label="打开菜单" icon={Menu} onClick={() => setMobileOpen(true)} />
        <div className="topbar__context"><span>{current?.hint ?? '安全运营'}</span><strong>{current?.label ?? '风险平台'}</strong></div>
        <form className="global-search" onSubmit={submitSearch}><Search size={16} /><input ref={searchRef} value={search} onChange={(event) => setSearch(event.target.value)} placeholder="搜索交易流水…" aria-label="搜索交易流水" /><kbd aria-label="快捷键斜杠">/</kbd></form>
        <span className="env-pill"><span />生产镜像</span>
        <DropdownMenu.Root><DropdownMenu.Trigger asChild><button className="user-menu"><span className="user-avatar">{auth.user?.displayName?.slice(0, 1).toUpperCase() || 'R'}</span><span><strong>{auth.user?.displayName ?? '风险分析师'}</strong><small>{auth.user?.roles?.[0] ?? 'AUTHORIZED'}</small></span><ChevronDown size={14} /></button></DropdownMenu.Trigger><DropdownMenu.Portal><DropdownMenu.Content className="dropdown" sideOffset={8} align="end"><div className="dropdown__meta"><strong>{auth.user?.displayName}</strong><span>{auth.user?.tenant ?? 'risk-platform'}</span></div><DropdownMenu.Separator /><DropdownMenu.Item className="dropdown__item" onSelect={() => void auth.logout()}><LogOut size={15} />退出登录</DropdownMenu.Item></DropdownMenu.Content></DropdownMenu.Portal></DropdownMenu.Root>
      </header>
      <main className="content"><div className="page-motion" key={location.pathname}><Outlet /></div></main>
    </div>
  </div>
}
