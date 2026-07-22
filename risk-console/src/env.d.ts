/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_AUTH_MODE?: 'dev' | 'oidc'
  readonly VITE_CASDOOR_ISSUER?: string
  readonly VITE_CASDOOR_CLIENT_ID?: string
  readonly VITE_CASDOOR_ORGANIZATION?: string
  readonly VITE_CASDOOR_SCOPE?: string
}
