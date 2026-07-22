import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from './LoginView.vue'

vi.mock('../auth/config', () => ({
  AUTH_MODE: 'oidc',
  AUTH_CONFIG: {
    issuer: 'http://localhost:8000',
    clientId: 'ragshared0client00000001-org-risk-platform',
    organization: 'risk-platform',
    scope: 'openid profile offline_access',
  },
}))

async function mountLogin(path = '/login?returnTo=%2Fcases') {
  const pinia = createPinia()
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/login', component: LoginView }],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(LoginView, { global: { plugins: [pinia, router, ElementPlus] } })
  const login = vi.spyOn(useAuthStore(pinia), 'login').mockResolvedValue()
  return { wrapper, login }
}

describe('Risk login tenant page', () => {
  it('renders the shared brand layout and configured tenant hint', async () => {
    const { wrapper } = await mountLogin()

    expect(wrapper.text()).toContain('实时风险识别')
    expect(wrapper.text()).toContain('风险案件处置')
    expect(wrapper.text()).toContain('统一身份登录')
    expect(wrapper.text()).toContain('当前可用租户：risk-platform')
  })

  it('blocks an unknown tenant without starting OIDC', async () => {
    const { wrapper, login } = await mountLogin()

    await wrapper.get('#login-tenant').setValue('unknown')
    await wrapper.get('form').trigger('submit')

    expect(login).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('租户 unknown 未开放。当前可用租户：risk-platform')
  })

  it('starts OIDC once with the sanitized in-app return path', async () => {
    const { wrapper, login } = await mountLogin()

    await wrapper.get('form').trigger('submit')

    expect(login).toHaveBeenCalledTimes(1)
    expect(login).toHaveBeenCalledWith('/cases')
  })
})
