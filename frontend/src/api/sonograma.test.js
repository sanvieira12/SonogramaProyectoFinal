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

  it('envía el número de boleta opcional y la clave de idempotencia del pago', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      text: () => Promise.resolve('{}'),
    })

    await api.deudas.registrarPago(42, 1000, null, '1258', 'payment-1')

    expect(fetchMock).toHaveBeenCalledWith('/api/deudas/42/registrar-pago', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        monto: 1000,
        notas: null,
        numeroRecibo: '1258',
        idempotencyKey: 'payment-1',
      }),
    }))
  })

  it('envía null cuando el número de boleta se deja vacío', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      text: () => Promise.resolve('{}'),
    })

    await api.deudas.registrarPago(42, 500, null, null, 'payment-2')

    expect(fetchMock).toHaveBeenCalledWith('/api/deudas/42/registrar-pago', expect.objectContaining({
      body: JSON.stringify({
        monto: 500,
        notas: null,
        numeroRecibo: null,
        idempotencyKey: 'payment-2',
      }),
    }))
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

  it('downloads a copy QR as a PNG blob', async () => {
    vi.spyOn(window.localStorage.__proto__, 'getItem').mockReturnValue('token-1')
    const blob = new Blob(['png'], { type: 'image/png' })
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({
        'Content-Type': 'image/png',
        'Content-Disposition': 'inline; filename="qr-42-2.png"',
      }),
      blob: () => Promise.resolve(blob),
    })

    await expect(api.qr.descargarCopia(42, 2)).resolves.toEqual({
      blob,
      contentDisposition: 'inline; filename="qr-42-2.png"',
    })
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/qr/descargar/42/2',
      { headers: { Authorization: 'Bearer token-1' } },
    )
  })
})
