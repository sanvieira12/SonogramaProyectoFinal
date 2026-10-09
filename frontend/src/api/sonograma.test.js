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

  it('uses the bounded Nueva Venta endpoint and forwards its abort signal', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      text: () => Promise.resolve('[]'),
    })
    const controller = new AbortController()

    await api.discos.buscarVenta('LO / rare', 20, controller.signal)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/discos/buscar-venta?q=LO%20%2F%20rare&limit=20',
      expect.objectContaining({ method: 'GET', signal: controller.signal }),
    )
  })

  it('preserves AbortError so Nueva Venta can silently discard cancellation', async () => {
    const abortError = new DOMException('Aborted', 'AbortError')
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(abortError)

    await expect(api.discos.buscarVenta('radio', 20, new AbortController().signal))
      .rejects.toBe(abortError)
  })

  it('retires one exact physical copy with the backend reason and optional note contract', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      text: () => Promise.resolve('{"idDisco":42,"cantidadCopias":1}'),
    })

    await api.discos.retirarCopia(42, 77, {
      reason: 'DAMAGED',
      note: 'Rayón profundo',
    })

    expect(fetchMock).toHaveBeenCalledWith('/api/discos/42/copias/77/retiro', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ reason: 'DAMAGED', note: 'Rayón profundo' }),
    }))
  })

  it('loads the authoritative Stock valuation independently from pricing preview settings', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      text: () => Promise.resolve('{"projectedNewUyu":6000}'),
    })

    await api.pricing.stockValuation()

    expect(fetchMock).toHaveBeenCalledWith('/api/pricing/stock-valuation', expect.objectContaining({
      method: 'GET',
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

  it('actualiza un pago usando su id estable y campos propios del movimiento', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      text: () => Promise.resolve('{}'),
    })

    await api.deudas.actualizarPago(66, {
      monto: 200,
      fechaPago: '2026-07-19',
      numeroRecibo: 'B-2',
      notas: 'Corrección',
    })

    expect(fetchMock).toHaveBeenCalledWith('/api/deudas/pagos/66', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        monto: 200,
        fechaPago: '2026-07-19',
        numeroRecibo: 'B-2',
        notas: 'Corrección',
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

  it('valida y confirma una factura VinylFuture antes de importarla', async () => {
    vi.spyOn(window.localStorage.__proto__, 'getItem').mockReturnValue('token-1')
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        text: () => Promise.resolve('{"validationId":"validation-1","consistent":true}'),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 202,
        text: () => Promise.resolve('{"jobId":"job-1"}'),
      })

    await expect(api.importar.vinylfutureValidar(new File(['pdf'], 'factura.pdf')))
      .resolves.toMatchObject({ validationId: 'validation-1', consistent: true })
    await expect(api.importar.vinylfutureConfirmar('validation-1', true))
      .resolves.toEqual({ jobId: 'job-1' })

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/importar/vinylfuture/validar', expect.objectContaining({
      method: 'POST',
      headers: { Authorization: 'Bearer token-1' },
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/importar/vinylfuture/validaciones/validation-1/confirmar?continuarParcial=true',
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('busca y confirma un producto Vinyl Future manual con cantidad', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        text: () => Promise.resolve('{"previewId":"preview-1","catalogueCode":"TEST-1"}'),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        text: () => Promise.resolve('{"productId":42,"addedCopies":3}'),
      })

    await api.importar.vinylfutureManualBuscar('https://www.vinylfuture.com/product__1', 9)
    await api.importar.vinylfutureManualConfirmar('preview-1', 3)

    expect(fetchMock).toHaveBeenNthCalledWith(1,
      '/api/importar/vinylfuture/manual/buscar',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ url: 'https://www.vinylfuture.com/product__1', pendingItemId: 9 }),
      }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(2,
      '/api/importar/vinylfuture/manual/preview-1/confirmar',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ quantity: 3 }) }),
    )
  })

  it('descarga la portada y el ZIP individuales del producto Vinyl Future', async () => {
    vi.spyOn(window.localStorage.__proto__, 'getItem').mockReturnValue('token-1')
    const cover = new Blob(['cover'], { type: 'image/jpeg' })
    const zip = new Blob(['zip'], { type: 'application/zip' })
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ 'Content-Type': 'image/jpeg' }),
        blob: () => Promise.resolve(cover),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({
          'Content-Type': 'application/zip',
          'Content-Disposition': 'attachment; filename="producto.zip"',
        }),
        blob: () => Promise.resolve(zip),
      })

    await expect(api.importar.vinylfuturePortada('preview-1')).resolves.toMatchObject({ blob: cover })
    await expect(api.importar.vinylfutureProductoZip('preview-1')).resolves.toMatchObject({
      blob: zip, filename: 'producto.zip',
    })
    expect(fetchMock).toHaveBeenNthCalledWith(1,
      '/api/importar/vinylfuture/manual/preview-1/portada',
      { headers: { Authorization: 'Bearer token-1' } },
    )
    expect(fetchMock).toHaveBeenNthCalledWith(2,
      '/api/importar/vinylfuture/manual/preview-1/zip',
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

  it('descarga el Excel de un batch manual Discogs con su nombre y MIME', async () => {
    vi.spyOn(window.localStorage.__proto__, 'getItem').mockReturnValue('token-1')
    const blob = new Blob(['xlsx'], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({
        'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'Content-Disposition': 'attachment; filename="JPH_2026-09-04_batch-15.xlsx"',
      }),
      blob: () => Promise.resolve(blob),
    })

    await expect(api.importaciones.discogsManualBatchExcel(15)).resolves.toEqual({
      blob,
      filename: 'JPH_2026-09-04_batch-15.xlsx',
      contentDisposition: 'attachment; filename="JPH_2026-09-04_batch-15.xlsx"',
    })
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/importaciones/discogs/manual-batches/15/excel',
      { headers: { Authorization: 'Bearer token-1' } },
    )
  })

  it('descarga el Excel lógico usando la fuente normalizada y el nombre del servidor', async () => {
    vi.spyOn(window.localStorage.__proto__, 'getItem').mockReturnValue('token-1')
    const blob = new Blob(['xlsx'], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({
        'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'Content-Disposition': 'attachment; filename="SV3_2026-09-30.xlsx"',
      }),
      blob: () => Promise.resolve(blob),
    })

    await expect(api.importaciones.discogsManualSourceExcel(' sv3 ')).resolves.toEqual({
      blob,
      filename: 'SV3_2026-09-30.xlsx',
      contentDisposition: 'attachment; filename="SV3_2026-09-30.xlsx"',
    })
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/importaciones/discogs/manual-sources/SV3/excel',
      { headers: { Authorization: 'Bearer token-1' } },
    )
  })

  it('descarga el ZIP de un batch manual Discogs con su nombre y MIME', async () => {
    vi.spyOn(window.localStorage.__proto__, 'getItem').mockReturnValue('token-1')
    const blob = new Blob(['zip'], { type: 'application/zip' })
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({
        'Content-Type': 'application/zip',
        'Content-Disposition': 'attachment; filename="JPH_2026-09-04_batch-15.zip"',
      }),
      blob: () => Promise.resolve(blob),
    })

    await expect(api.importaciones.discogsManualBatchZip(15)).resolves.toEqual({
      blob,
      filename: 'JPH_2026-09-04_batch-15.zip',
      contentDisposition: 'attachment; filename="JPH_2026-09-04_batch-15.zip"',
    })
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/importaciones/discogs/manual-batches/15/zip',
      { headers: { Authorization: 'Bearer token-1' } },
    )
  })

  it('finaliza un batch manual Discogs con POST y devuelve su estado', async () => {
    vi.spyOn(window.localStorage.__proto__, 'getItem').mockReturnValue('token-1')
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      text: () => Promise.resolve(JSON.stringify({
        batchId: 15,
        status: 'FINALIZED',
        finalizedAt: '2026-09-04T12:00:00',
        porcentajeSonograma: 30,
      })),
    })

    await expect(api.importaciones.discogsManualBatchFinalize(15, 30)).resolves.toEqual({
      batchId: 15,
      status: 'FINALIZED',
      finalizedAt: '2026-09-04T12:00:00',
      porcentajeSonograma: 30,
    })
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/importaciones/discogs/manual-batches/15/finalize',
      expect.objectContaining({
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer token-1' },
        body: JSON.stringify({
          porcentajeSonograma: 30,
          confirmPendingOperations: false,
          confirmReconciliationWarnings: false,
        }),
      }),
    )
  })

  it('reads source reconciliation and updates only user-supplied expected-count fields', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      text: () => Promise.resolve('{}'),
    })
    const payload = {
      expectedCopyCount: 99,
      version: 2,
      note: 'Conteo confirmado',
      changeReason: 'Proveedor confirmó dos unidades adicionales',
    }

    await api.importaciones.discogsManualSourceReconciliation(' LO ')
    await api.importaciones.discogsManualExpectedCountUpdate(' LO ', payload)
    await api.importaciones.discogsManualReconciliationSnapshots(' LO ')

    const source = '%20LO%20'
    expect(fetchMock).toHaveBeenNthCalledWith(1,
      `/api/importaciones/discogs/manual-sources/${source}/reconciliation`,
      expect.objectContaining({ method: 'GET' }))
    expect(fetchMock).toHaveBeenNthCalledWith(2,
      `/api/importaciones/discogs/manual-sources/${source}/reconciliation/expected-count`,
      expect.objectContaining({ method: 'PUT', body: JSON.stringify(payload) }))
    expect(fetchMock).toHaveBeenNthCalledWith(3,
      `/api/importaciones/discogs/manual-sources/${source}/reconciliation/snapshots`,
      expect.objectContaining({ method: 'GET' }))
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
