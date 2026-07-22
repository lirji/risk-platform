import { fireEvent, render } from '@testing-library/vue'
import ElementPlus from 'element-plus'
import PageState from './PageState.vue'

function renderState(props: Record<string, unknown>) {
  return render(PageState, { props, global: { plugins: [ElementPlus] }, slots: { default: '<div>业务数据</div>' } })
}

describe('PageState', () => {
  it('exposes retry for failures', async () => {
    const view = renderState({ error: 'network timeout' })
    await fireEvent.click(view.getByText('重新加载'))
    expect(view.emitted().retry).toHaveLength(1)
    expect(view.getByRole('alert').textContent).toContain('network timeout')
  })

  it('renders content only in ready state', () => {
    expect(renderState({}).getByText('业务数据')).toBeTruthy()
  })
})
