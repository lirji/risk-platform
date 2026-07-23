import { lazy, Suspense, type ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import AppShell from './components/AppShell'
import { PageState } from './components/ui'

const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const DecisionsPage = lazy(() => import('./pages/DecisionsPage'))
const CasesPage = lazy(() => import('./pages/CasesPage'))
const ProfilesPage = lazy(() => import('./pages/ProfilesPage'))
const RulesPage = lazy(() => import('./pages/RulesPage'))
const ModelsPage = lazy(() => import('./pages/ModelsPage'))
const RatingsPage = lazy(() => import('./pages/RatingsPage'))
const OperationsPage = lazy(() => import('./pages/OperationsPage'))
const AuditPage = lazy(() => import('./pages/AuditPage'))
const LoginPage = lazy(() => import('./pages/LoginPage'))
const AuthCallbackPage = lazy(() => import('./pages/AuthCallbackPage'))
const ForbiddenPage = lazy(() => import('./pages/ForbiddenPage'))

function ScreenLoader() {
  return <main className="route-loader"><PageState loading /></main>
}

function Protected({ permission, children }: { permission?: string; children: ReactNode }) {
  const auth = useAuth()
  const location = useLocation()
  if (!auth.loaded) return <ScreenLoader />
  if (!auth.authenticated) return <Navigate replace to={`/login?returnTo=${encodeURIComponent(location.pathname + location.search)}`} />
  if (!auth.can(permission)) return <Navigate replace to="/forbidden" />
  return children
}

function GuestOnly({ children }: { children: ReactNode }) {
  const auth = useAuth()
  if (!auth.loaded) return <ScreenLoader />
  return auth.authenticated ? <Navigate replace to="/dashboard" /> : children
}

export default function App() {
  return <BrowserRouter><Suspense fallback={<ScreenLoader />}><Routes>
    <Route path="/login" element={<GuestOnly><LoginPage /></GuestOnly>} />
    <Route path="/auth/callback" element={<AuthCallbackPage />} />
    <Route path="/forbidden" element={<ForbiddenPage />} />
    <Route element={<Protected><AppShell /></Protected>}>
      <Route index element={<Navigate replace to="/dashboard" />} />
      <Route path="/dashboard" element={<DashboardPage />} />
      <Route path="/decisions" element={<Protected permission="decision.read"><DecisionsPage /></Protected>} />
      <Route path="/cases" element={<Protected permission="case.read"><CasesPage /></Protected>} />
      <Route path="/profiles" element={<Protected permission="profile.read"><ProfilesPage /></Protected>} />
      <Route path="/rules" element={<Protected permission="rule.read"><RulesPage /></Protected>} />
      <Route path="/models" element={<Protected permission="model.read"><ModelsPage /></Protected>} />
      <Route path="/ratings" element={<Protected permission="rating.read"><RatingsPage /></Protected>} />
      <Route path="/operations" element={<Protected permission="ops.read"><OperationsPage /></Protected>} />
      <Route path="/audit" element={<Protected permission="audit.read"><AuditPage /></Protected>} />
    </Route>
    <Route path="*" element={<Navigate replace to="/dashboard" />} />
  </Routes></Suspense></BrowserRouter>
}
