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
      headers: new Headers({
        'Content-Type': 'application/zip',
        'Content-Disposition': 'attachment; filename="vinylfuture-import.zip"',
      }),
      blob: () => Promise.resolve(blob),
    })

    await expect(api.importar.vinylfutureZip('import-123')).resolves.toEqual({
      blob,
      filename: 'vinylfuture-import.zip',
      contentDisposition: 'attachment; filename="vinylfuture-import.zip"',
    })
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/importar/vinylfuture/import-123/zip',
      { headers: { Authorization: 'Bearer token-1' } },
    )
  })

  it('exports VinylFuture CSV ZIP with a filename from Content-Disposition', async () => {
    vi.spyOn(window.localStorage.__proto__, 'getItem').mockReturnValue('token-1')
    const blob = new Blob(['zip'])
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({
        'Content-Type': 'application/zip',
        'Content-Disposition': "attachment; filename*=UTF-8''vinylfuture-csv.zip",
      }),
      blob: () => Promise.resolve(blob),
    })

    await expect(api.importar.vinylfutureCsv(new File(['pdf'], 'factura.pdf'))).resolves.toMatchObject({
      blob,
      filename: 'vinylfuture-csv.zip',
    })
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/importar/vinylfuture-csv',
      expect.objectContaining({
        method: 'POST',
        headers: { Authorization: 'Bearer token-1' },
      }),
    )
  })

  it('exports Discogs covers ZIP with a filename from Content-Disposition', async () => {
    vi.spyOn(window.localStorage.__proto__, 'getItem').mockReturnValue('token-1')
    const blob = new Blob(['zip'])
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({
        'Content-Type': 'application/zip',
        'Content-Disposition': 'attachment; filename="discogs-covers.zip"',
      }),
      blob: () => Promise.resolve(blob),
    })

    await expect(api.importaciones.discogsCoversZip(42)).resolves.toEqual({
      blob,
      filename: 'discogs-covers.zip',
      contentDisposition: 'attachment; filename="discogs-covers.zip"',
    })
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/importaciones/discogs/jobs/42/covers.zip',
      { headers: { Authorization: 'Bearer token-1' } },
    )
  })
})
