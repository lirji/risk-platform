export type DataRecord = Record<string, any>

export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '操作未完成，请稍后重试。'
}

export function formatDate(value: unknown) {
  if (!value) return '—'
  const date = new Date(String(value))
  return Number.isNaN(date.getTime()) ? String(value) : new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(date)
}

export function shortId(value: unknown, length = 14) {
  const text = String(value ?? '—')
  return text.length > length ? `${text.slice(0, length)}…` : text
}
