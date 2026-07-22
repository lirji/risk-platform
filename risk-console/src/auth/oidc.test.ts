import { sanitizeReturnTo } from './oidc'

describe('OIDC return path', () => {
  it('accepts only local application paths', () => {
    expect(sanitizeReturnTo('/cases?status=OPEN')).toBe('/cases?status=OPEN')
    expect(sanitizeReturnTo('https://evil.invalid')).toBe('/dashboard')
    expect(sanitizeReturnTo('//evil.invalid')).toBe('/dashboard')
    expect(sanitizeReturnTo('/auth/callback?code=x')).toBe('/dashboard')
    expect(sanitizeReturnTo('/login')).toBe('/dashboard')
  })
})
