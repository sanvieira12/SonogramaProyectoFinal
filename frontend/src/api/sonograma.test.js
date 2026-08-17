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

  it('sends login through the same-origin API path', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      text: () => Promise.resolve('{"token":"test-token"}'),
    })

    await api.login('admin', 'test-password')

    expect(fetchMock).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({
      method: 'POST',
    }))
  })

  it('exchanges the Google handoff code through a POST body', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      text: () => Promise.resolve('{"token":"test-token"}'),
    })

    await api.exchangeGoogleLogin('single-use-code')

    expect(fetchMock).toHaveBeenCalledWith('/api/auth/google/exchange', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ code: 'single-use-code' }),
    }))
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

  it('elimina un pago usando únicamente el id estable del pago', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 204,
      text: () => Promise.resolve(''),
    })

    await api.deudas.eliminarPago(66)

    expect(fetchMock).toHaveBeenCalledWith('/api/deudas/pagos/66', expect.objectContaining({
      method: 'DELETE',
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
      '/api/importaciones/discogs/jobs/42/covers-zip/download',
      { headers: { Authorization: 'Bearer token-1' } },
    )
  })

  it('starts and reads persisted Discogs ZIP preparation progress', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      text: () => Promise.resolve('{"zipStatus":"preparing","zipProcessedCovers":3}'),
    })

    await expect(api.importaciones.discogsPrepareCoversZip(42)).resolves.toMatchObject({
      zipStatus: 'preparing',
      zipProcessedCovers: 3,
    })
    await api.importaciones.discogsCoversZipStatus(42)

    expect(fetchMock).toHaveBeenNthCalledWith(1,
      '/api/importaciones/discogs/jobs/42/covers-zip',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(2,
      '/api/importaciones/discogs/jobs/42/covers-zip/status',
      expect.objectContaining({ method: 'GET' }),
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

  it('uses the CRM namespace for customer profiles, interests and reverse recommendations', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      text: () => Promise.resolve('[]'),
    })

    await api.crm.perfil(7)
    await api.crm.crearInteres(7, { tipo: 'ARTISTA', texto: 'Drexciya' })
    await api.crm.cambiarEstadoInteres(7, 3, false)
    await api.crm.clientesRecomendados(42)

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/crm/clientes/7/perfil', expect.objectContaining({ method: 'GET' }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/crm/clientes/7/intereses', expect.objectContaining({ method: 'POST' }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/crm/clientes/7/intereses/3', expect.objectContaining({
      method: 'PATCH', body: JSON.stringify({ activo: false }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/crm/discos/42/clientes-recomendados?limit=20', expect.objectContaining({ method: 'GET' }))
  })
})
