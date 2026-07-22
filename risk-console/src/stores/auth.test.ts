import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'

describe('auth permissions', () => {
  it('uses exact permissions and never infers admin access', () => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.user = { id: 'u', displayName: 'Analyst', authenticated: true, roles: ['analyst'], permissions: ['decision.read'] }
    expect(auth.can('decision.read')).toBe(true)
    expect(auth.can('rule.publish')).toBe(false)
  })
})
