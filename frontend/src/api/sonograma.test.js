import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, normalizeApiBase } from './sonograma'

describe('normalizeApiBase', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('removes trailing slashes to avoid malformed auth URLs', () => {
    expect(normalizeApiBase('https://sonograma.example/api/')).toBe(
      'https://sonograma.example/api',
    )
  })

  it('falls back to the local reverse-proxy path', () => {
    expect(normalizeApiBase('')).toBe('/api')
  })

  it('exports VinylFuture ZIP from an import id', async () => {
    vi.spyOn(window.localStorage.__proto__, 'getItem').mockReturnValue('token-1')
    const blob = new Blob(['zip'])
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      blob: () => Promise.resolve(blob),
    })

    await expect(api.importar.vinylfutureZip('import-123')).resolves.toBe(blob)
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/importar/vinylfuture/import-123/zip',
      { headers: { Authorization: 'Bearer token-1' } },
    )
  })
})
