import { vi } from 'vitest'

class ResizeObserverMock {
  observe() { /* no-op in jsdom */ }
  unobserve() { /* no-op in jsdom */ }
  disconnect() { /* no-op in jsdom */ }
}
vi.stubGlobal('ResizeObserver', ResizeObserverMock)
