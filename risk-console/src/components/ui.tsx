import * as Dialog from '@radix-ui/react-dialog'
import * as Tooltip from '@radix-ui/react-tooltip'
import clsx from 'clsx'
import { AlertTriangle, Check, ChevronRight, LoaderCircle, ShieldAlert, X, type LucideIcon } from 'lucide-react'
import { createContext, useCallback, useContext, useMemo, useState, type ButtonHTMLAttributes, type ReactNode } from 'react'

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'warning'
type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant; icon?: LucideIcon }

export function Button({ variant = 'secondary', icon: Icon, children, className, ...props }: ButtonProps) {
  return <button className={clsx('button', `button--${variant}`, className)} {...props}>{Icon && <Icon size={15} aria-hidden="true" />}<span>{children}</span></button>
}

export function IconButton({ label, icon: Icon, className, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string; icon: LucideIcon }) {
  return <Tooltip.Root><Tooltip.Trigger asChild><button className={clsx('icon-button', className)} aria-label={label} {...props}><Icon size={18} /></button></Tooltip.Trigger><Tooltip.Portal><Tooltip.Content className="tooltip" sideOffset={8}>{label}<Tooltip.Arrow className="tooltip__arrow" /></Tooltip.Content></Tooltip.Portal></Tooltip.Root>
}

export function Field({ label, hint, children, className }: { label: string; hint?: string; children: ReactNode; className?: string }) {
  return <label className={clsx('field', className)}><span className="field__label">{label}</span>{children}{hint && <small>{hint}</small>}</label>
}

export function Input(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return <input className="input" {...props} />
}

export function Select(props: React.SelectHTMLAttributes<HTMLSelectElement>) {
  return <select className="select" {...props} />
}

export function Textarea(props: React.TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className="textarea" {...props} />
}

export function Modal({ open, onOpenChange, title, description, children, footer, wide = false }: { open: boolean; onOpenChange: (open: boolean) => void; title: string; description?: string; children: ReactNode; footer?: ReactNode; wide?: boolean }) {
  return <Dialog.Root open={open} onOpenChange={onOpenChange}><Dialog.Portal><Dialog.Overlay className="dialog-overlay" /><Dialog.Content className={clsx('dialog', wide && 'dialog--wide')}><div className="dialog__head"><div><Dialog.Title>{title}</Dialog.Title>{description && <Dialog.Description>{description}</Dialog.Description>}</div><Dialog.Close asChild><IconButton label="关闭" icon={X} /></Dialog.Close></div><div className="dialog__body">{children}</div>{footer && <div className="dialog__footer">{footer}</div>}</Dialog.Content></Dialog.Portal></Dialog.Root>
}

export function Drawer({ open, onOpenChange, title, children, footer }: { open: boolean; onOpenChange: (open: boolean) => void; title: string; children: ReactNode; footer?: ReactNode }) {
  return <Dialog.Root open={open} onOpenChange={onOpenChange}><Dialog.Portal><Dialog.Overlay className="dialog-overlay" /><Dialog.Content className="drawer"><div className="dialog__head"><Dialog.Title>{title}</Dialog.Title><Dialog.Close asChild><IconButton label="关闭" icon={X} /></Dialog.Close></div><div className="drawer__body">{children}</div>{footer && <div className="dialog__footer">{footer}</div>}</Dialog.Content></Dialog.Portal></Dialog.Root>
}

export function PageState({ loading, error, empty, emptyText = '暂无数据', onRetry, children }: { loading?: boolean; error?: string; empty?: boolean; emptyText?: string; onRetry?: () => void; children?: ReactNode }) {
  if (loading) return <div className="state state--loading" role="status" aria-live="polite"><span className="sr-only">正在加载页面数据</span><div className="skeleton skeleton--title" />{[1, 2, 3, 4, 5].map((item) => <div className="skeleton" key={item} />)}</div>
  if (error) return <div className="state state--error" role="alert"><span className="state__icon"><AlertTriangle size={20} /></span><h3>数据暂时不可用</h3><p>{error}</p>{onRetry && <Button variant="secondary" onClick={onRetry}>重新加载</Button>}</div>
  if (empty) return <div className="state"><span className="state__icon state__icon--ok"><Check size={20} /></span><h3>{emptyText}</h3><p>调整筛选条件，或等待新的数据进入。</p></div>
  return children
}

export function RiskBadge({ level }: { level: string }) {
  const tone = ['REJECT', 'CRITICAL', 'HIGH', 'FAILED', 'DEAD'].includes(level) ? 'red' : ['MEDIUM', 'CANARY', 'IN_REVIEW'].includes(level) ? 'amber' : 'green'
  return <span className={clsx('badge', `badge--${tone}`)}><span />{level}</span>
}

export function MetricCard({ label, value, hint, tone = 'green', icon: Icon, trend }: { label: string; value: ReactNode; hint: string; tone?: 'green' | 'amber' | 'red' | 'blue'; icon: LucideIcon; trend?: string }) {
  return <article className={clsx('metric-card', `metric-card--${tone}`)}><div className="metric-card__top"><span className="metric-card__icon"><Icon size={17} /></span>{trend && <span className="metric-card__trend">{trend}</span>}</div><strong>{value}</strong><div className="metric-card__label">{label}</div><p>{hint}</p></article>
}

export type TableColumn<T> = { key: string; label: string; width?: number | string; render: (row: T) => ReactNode; align?: 'left' | 'right' | 'center' }

export function DataTable<T>({ rows, columns, rowKey, onRowClick, emptyText = '暂无记录' }: { rows: T[]; columns: TableColumn<T>[]; rowKey: (row: T) => string; onRowClick?: (row: T) => void; emptyText?: string }) {
  return <div className="table-wrap"><table className={clsx('data-table', onRowClick && 'data-table--interactive')}><thead><tr>{columns.map((column) => <th key={column.key} style={{ width: column.width, textAlign: column.align }}>{column.label}</th>)}</tr></thead><tbody>{rows.length ? rows.map((row) => <tr key={rowKey(row)} onClick={() => onRowClick?.(row)} tabIndex={onRowClick ? 0 : undefined} onKeyDown={(event) => { if (onRowClick && (event.key === 'Enter' || event.key === ' ')) { event.preventDefault(); onRowClick(row) } }}>{columns.map((column) => <td key={column.key} style={{ textAlign: column.align }}>{column.render(row)}</td>)}</tr>) : <tr><td className="table-empty" colSpan={columns.length}>{emptyText}</td></tr>}</tbody></table></div>
}

export function SectionHeader({ title, meta, action }: { title: string; meta?: ReactNode; action?: ReactNode }) {
  return <div className="section-header"><div><h3>{title}</h3>{meta && <span>{meta}</span>}</div>{action}</div>
}

export function PageHeader({ eyebrow, title, description, actions }: { eyebrow?: string; title: string; description: string; actions?: ReactNode }) {
  return <header className="page-header"><div>{eyebrow && <span className="eyebrow">{eyebrow}</span>}<h2>{title}</h2><p>{description}</p></div>{actions && <div className="page-header__actions">{actions}</div>}</header>
}

export function KeyValueGrid({ values }: { values: Record<string, unknown> }) {
  return <div className="key-grid">{Object.entries(values).map(([key, value]) => <div key={key}><label>{key}</label><span className="mono">{typeof value === 'object' ? JSON.stringify(value) : String(value ?? '—')}</span></div>)}</div>
}

type ToastItem = { id: number; message: string; tone: 'success' | 'error' }
const ToastContext = createContext<(message: string, tone?: ToastItem['tone']) => void>(() => undefined)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])
  const push = useCallback((message: string, tone: ToastItem['tone'] = 'success') => {
    const id = Date.now()
    setItems((current) => [...current, { id, message, tone }])
    window.setTimeout(() => setItems((current) => current.filter((item) => item.id !== id)), 3200)
  }, [])
  const value = useMemo(() => push, [push])
  return <ToastContext.Provider value={value}>{children}<div className="toast-stack" aria-live="polite">{items.map((item) => <div className={clsx('toast', `toast--${item.tone}`)} key={item.id}>{item.tone === 'success' ? <Check size={16} /> : <AlertTriangle size={16} />}<span>{item.message}</span></div>)}</div></ToastContext.Provider>
}

export function useToast() { return useContext(ToastContext) }

export function LoadingButton({ loading, children, variant = 'primary', ...props }: ButtonProps & { loading?: boolean }) {
  return <Button variant={variant} disabled={loading || props.disabled} {...props} icon={loading ? LoaderCircle : props.icon} className={clsx(loading && 'is-loading', props.className)}>{children}</Button>
}

export function ConfirmDialog({ open, onOpenChange, title, description, confirmLabel, onConfirm, busy = false, tone = 'warning' }: { open: boolean; onOpenChange: (open: boolean) => void; title: string; description: string; confirmLabel: string; onConfirm: () => void; busy?: boolean; tone?: 'warning' | 'danger' }) {
  return <Modal open={open} onOpenChange={onOpenChange} title={title} description="此操作会记录到审计日志，请确认业务影响。" footer={<><Button disabled={busy} onClick={() => onOpenChange(false)}>取消</Button><LoadingButton loading={busy} variant={tone} onClick={onConfirm}>{confirmLabel}</LoadingButton></>}><div className={clsx('confirm-message', tone === 'danger' && 'confirm-message--danger')}><span><ShieldAlert size={20} /></span><p>{description}</p></div></Modal>
}

export function InlineAction({ children, onClick, tone = 'default' }: { children: ReactNode; onClick: () => void; tone?: 'default' | 'danger' | 'warning' }) {
  return <button type="button" className={clsx('inline-action', `inline-action--${tone}`)} onClick={(event) => { event.stopPropagation(); onClick() }}>{children}<ChevronRight size={13} /></button>
}

export { Tooltip }
