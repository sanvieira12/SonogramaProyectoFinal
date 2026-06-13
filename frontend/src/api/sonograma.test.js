import { describe, expect, it } from 'vitest'
import { normalizeApiBase } from './sonograma'

describe('normalizeApiBase', () => {
  it('removes trailing slashes to avoid malformed auth URLs', () => {
    expect(normalizeApiBase('https://sonograma.example/api/')).toBe(
      'https://sonograma.example/api',
    )
  })

  it('falls back to the local reverse-proxy path', () => {
    expect(normalizeApiBase('')).toBe('/api')
  })
})
