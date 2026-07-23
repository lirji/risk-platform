import * as Tooltip from '@radix-ui/react-tooltip'
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ConfirmDialog, PageState } from './ui'

describe('PageState', () => {
  it('renders a dark loading skeleton with an accessible status', () => {
    const { container } = render(<PageState loading />)
    expect(screen.getByRole('status').textContent).toContain('正在加载页面数据')
    expect(container.querySelectorAll('.skeleton')).toHaveLength(6)
  })

  it('offers retry when loading fails', () => {
    const retry = vi.fn()
    render(<PageState error="网络中断" onRetry={retry} />)
    expect(screen.getByRole('alert').textContent).toContain('网络中断')
    fireEvent.click(screen.getByRole('button', { name: '重新加载' }))
    expect(retry).toHaveBeenCalledOnce()
  })

  it('renders children in the ready state', () => {
    render(<PageState><span>业务数据</span></PageState>)
    expect(screen.getByText('业务数据')).toBeTruthy()
  })

  it('uses an accessible in-product confirmation dialog for sensitive actions', () => {
    const confirm = vi.fn()
    render(<Tooltip.Provider><ConfirmDialog open onOpenChange={vi.fn()} title="确认重放" description="事件将重新进入消息链路。" confirmLabel="授权并重放" onConfirm={confirm} /></Tooltip.Provider>)
    expect(screen.getByRole('dialog', { name: '确认重放' }).textContent).toContain('事件将重新进入消息链路')
    fireEvent.click(screen.getByRole('button', { name: '授权并重放' }))
    expect(confirm).toHaveBeenCalledOnce()
  })
})
