import { AUTH_MODE } from '../auth/config'
import { getAccessToken, renewAccessToken } from '../auth/oidc'

export class ApiError extends Error {
  constructor(public status: number, message: string, public code = 'HTTP_ERROR') { super(message) }
}

async function request<T>(path: string, init: RequestInit, retry: boolean): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const token = await getAccessToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(path, { ...init, headers, credentials: 'same-origin' })
  if (response.status === 401 && retry && AUTH_MODE === 'oidc') {
    const renewed = await renewAccessToken()
    if (renewed) return request<T>(path, init, false)
  }
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string; code?: string }
    throw new ApiError(response.status, body.message ?? `请求失败 (${response.status})`, body.code)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  return request<T>(path, init, true)
}
