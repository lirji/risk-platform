export interface UserSession {
  id: string
  displayName: string
  roles: string[]
  permissions: string[]
  authenticated: boolean
  tenant?: string
  mode?: 'dev' | 'auth-platform'
}

export interface PageResponse<T> { content: T[]; total: number; page: number; size: number }

export interface Decision {
  decisionId: string
  sourceId: string
  txnId: string
  eventTime: string
  riskLevel: string
  action: string
  fraudScore: number
  hitRulesJson: string
  ruleVersion: string
  modelVersion: string
  costMs: number
  createdAt: string
}
