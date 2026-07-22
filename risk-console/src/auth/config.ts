export type AuthMode = 'dev' | 'oidc'

export const AUTH_MODE: AuthMode = import.meta.env.VITE_AUTH_MODE === 'oidc' ? 'oidc' : 'dev'

export const AUTH_CONFIG = {
  issuer: import.meta.env.VITE_CASDOOR_ISSUER ?? 'http://localhost:8000',
  clientId: import.meta.env.VITE_CASDOOR_CLIENT_ID ?? 'ragshared0client00000001-org-risk-platform',
  organization: import.meta.env.VITE_CASDOOR_ORGANIZATION ?? 'risk-platform',
  scope: import.meta.env.VITE_CASDOOR_SCOPE ?? 'openid profile offline_access',
}
