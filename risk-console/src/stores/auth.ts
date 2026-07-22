import { defineStore } from 'pinia'
import { api, ApiError } from '../services/api'
import type { UserSession } from '../types'
import { AUTH_MODE } from '../auth/config'
import { currentOidcUser, sanitizeReturnTo, userManager, type LoginState } from '../auth/oidc'

export const useAuthStore = defineStore('auth', {
  state: () => ({ user: null as UserSession | null, loaded: false, redirecting: false, error: '' }),
  getters: {
    authenticated: (state) => Boolean(state.user?.authenticated),
    can: (state) => (permission?: string) => !permission || Boolean(state.user?.permissions.includes(permission)),
  },
  actions: {
    async load() {
      if (this.loaded) return
      try {
        if (AUTH_MODE === 'oidc' && !(await currentOidcUser())) {
          this.user = null
          return
        }
        this.user = await api<UserSession>('/api/v1/auth/me')
      }
      catch (error) {
        if (!(error instanceof ApiError) || error.status !== 401) throw error
        this.user = null
      } finally { this.loaded = true }
    },
    async login(returnTo?: string) {
      this.error = ''
      if (AUTH_MODE === 'dev') {
        this.loaded = false
        await this.load()
        window.location.assign(sanitizeReturnTo(returnTo))
        return
      }
      this.redirecting = true
      try {
        await userManager.signinRedirect({ state: { returnTo: sanitizeReturnTo(returnTo) } satisfies LoginState })
      } catch (error) {
        this.redirecting = false
        this.error = error instanceof Error ? error.message : '无法连接统一身份平台'
      }
    },
    async completeLogin(): Promise<string> {
      const user = await userManager.signinRedirectCallback()
      const state = user.state as LoginState | undefined
      this.loaded = false
      await this.load()
      if (!this.user) throw new Error('统一身份登录成功，但后端未接受该账号')
      return sanitizeReturnTo(state?.returnTo)
    },
    async logout() {
      this.user = null
      this.loaded = true
      if (AUTH_MODE === 'oidc') {
        try {
          await userManager.signoutRedirect()
          return
        } catch {
          await userManager.removeUser()
        }
      }
      window.location.assign('/login')
    },
  },
})
