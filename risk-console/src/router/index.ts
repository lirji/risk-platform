import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AppShell from '../components/AppShell.vue'

const pages = {
  dashboard: () => import('../views/DashboardView.vue'), decisions: () => import('../views/DecisionsView.vue'),
  cases: () => import('../views/CasesView.vue'), profiles: () => import('../views/ProfilesView.vue'),
  rules: () => import('../views/RulesView.vue'), models: () => import('../views/ModelsView.vue'),
  ratings: () => import('../views/RatingsView.vue'), operations: () => import('../views/OperationsView.vue'),
  audit: () => import('../views/AuditView.vue'),
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
    { path: '/auth/callback', component: () => import('../views/AuthCallbackView.vue'), meta: { public: true } },
    { path: '/403', component: () => import('../views/ForbiddenView.vue'), meta: { public: true } },
    { path: '/', component: AppShell, redirect: '/dashboard', children: [
      { path: 'dashboard', component: pages.dashboard },
      { path: 'decisions', component: pages.decisions, meta: { permission: 'decision.read' } },
      { path: 'cases', component: pages.cases, meta: { permission: 'case.read' } },
      { path: 'profiles', component: pages.profiles, meta: { permission: 'profile.read' } },
      { path: 'rules', component: pages.rules, meta: { permission: 'rule.read' } },
      { path: 'models', component: pages.models, meta: { permission: 'model.read' } },
      { path: 'ratings', component: pages.ratings, meta: { permission: 'rating.read' } },
      { path: 'operations', component: pages.operations, meta: { permission: 'ops.read' } },
      { path: 'audit', component: pages.audit, meta: { permission: 'audit.read' } },
    ] },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

router.beforeEach(async (to) => {
  if (to.meta.public) return true
  const auth = useAuthStore()
  await auth.load()
  if (!auth.authenticated) return { path: '/login', query: { returnTo: to.fullPath } }
  const permission = to.meta.permission as string | undefined
  if (!auth.can(permission)) return '/403'
  return true
})

export default router
