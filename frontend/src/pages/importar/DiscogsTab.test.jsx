import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../api/sonograma'
import DiscogsTab from './DiscogsTab'

vi.mock('../../api/sonograma', () => ({
  api: {
    importaciones: {
      discogsJob: vi.fn(),
      discogsImportarJob: vi.fn(),
      discogsDesdeExcel: vi.fn(),
      discogsRetryRow: vi.fn(),
      discogsRetryPending: vi.fn(),
      discogsPrepareCoversZip: vi.fn(),
      discogsCoversZip: vi.fn(),
      discogsDesdeLink: vi.fn(),
      discogsGuardar: vi.fn(),
      discogsManualPending: vi.fn(),
      discogsManualOperationContext: vi.fn(),
      discogsManualOperationAbandon: vi.fn(),
      discogsManualSourceReconciliation: vi.fn(),
      discogsManualExpectedCountUpdate: vi.fn(),
      discogsManualCover: vi.fn(),
      discogsManualZip: vi.fn(),
    },
  },
}))

const completedJob = {
  id: 42,
  nombreArchivo: 'discogs.xlsx',
  nombreHoja: 'Discos',
  status: 'completed_with_warnings',
  stage: 'completed',
  rowsDetected: 1,
  rowsImported: 1,
  catalogProductsAffected: 1,
  rowsRequiringReview: 1,
  rowsWithFullMetadata: 0,
  rowsWithWarnings: 1,
  rowsTechnicallyImpossible: 0,
  readyToImport: 1,
  realRowsRead: 1,
  totalRowsRead: 1,
  linksDetected: 1,
  validReleaseUrls: 1,
  validMasterUrls: 0,
  soldRows: 1,
  reservedRows: 0,
  metadataFetched: 1,
  metadataPending: 0,
  metadataFailed: 0,
  coversDownloaded: 0,
  coversMissing: 1,
  youtubeLinksFound: 0,
  youtubeTracksMissing: 1,
  imported: 1,
  alreadyImported: 0,
  meaningfulRows: 1,
  identityBearingRows: 1,
  resolvedConcreteReleases: 1,
  newCopiesToReceive: 1,
  alreadyReceivedRows: 0,
  availableCopiesToReceive: 1,
  soldCopiesToReceive: 0,
  noPriceRows: 1,
  noPriceReceivableRows: 1,
  manualReviewRows: 0,
  canConfirm: true,
  warnings: 1,
  rows: [{
    id: 7,
    sourceExcelRowNumber: 2,
    sourceStatus: 'VENDIDO',
    manualCondition: 'NUEVO',
    rawPrice: 'SP',
    artist: 'Example Artist',
    title: 'Example Album',
    discogsType: 'release',
    discogsId: 12345,
    normalizedDiscogsUrl: 'https://www.discogs.com/release/12345',
    metadataStatus: 'success',
    coverStatus: 'unavailable',
    youtubeStatus: 'not_found',
    catalogImportStatus: 'ready',
    warningMessage: 'PRICE_REQUIRES_REVIEW — COVER_UNAVAILABLE — YOUTUBE_UNAVAILABLE',
  }],
}

const existingPreview = {
  operationId: '0f8fad5b-d9cb-469f-a165-70867728950e',
  discogsReleaseId: 456,
  discogsUrl: 'https://www.discogs.com/release/456',
  artista: 'Example Artist',
  album: 'Example Album',
  formato: 'VINILO',
  condicion: 'USADO',
  customerCode: 'JPH',
  copySalePrice: 1500,
  precioVenta: 1500,
  physicalCondition: 'VG+',
  cantidadCopias: 1,
  productoExistente: true,
  copiasDisponibles: 2,
  errores: [],
}

function reconciliation(overrides = {}) {
  return {
    sourceCustomerCode: 'TESTSOURCE',
    normalizedSourceCustomerCode: 'TESTSOURCE',
    expectedCopyCount: null,
    provablePhysicalCopyCount: 3,
    availableCopyCount: 2,
    soldCopyCount: 1,
    removedCopyCount: 0,
    distinctReleaseCount: 2,
    duplicateReleaseGroupCount: 1,
    extraDuplicateCopyCount: 1,
    pendingOperationCount: 0,
    completedOperationCount: 3,
    abandonedOperationCount: 0,
    difference: null,
    reconciliationStatus: 'EXPECTED_COUNT_UNKNOWN',
    reconciliationNote: null,
    version: null,
    ...overrides,
  }
}

describe('DiscogsTab Excel import', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.setItem('sonograma:discogs-excel-job:v1', '42')
    api.importaciones.discogsJob.mockResolvedValue(completedJob)
  })

  it('shows sold as source metadata and keeps the identifiable row importable as USADO', async () => {
    render(<DiscogsTab />)

    expect(await screen.findByText('Filas detectadas')).toBeInTheDocument()
    expect(screen.getByText('Importación completada con elementos pendientes')).toBeInTheDocument()
    expect(screen.getByText('Estado Excel: vendidos')).toBeInTheDocument()
    expect(screen.getByText('Catálogo: USADO')).toBeInTheDocument()
    expect(screen.getByText('Copias importadas en esta carga')).toBeInTheDocument()
    expect(screen.getByText('Productos de catálogo afectados')).toBeInTheDocument()
    expect(screen.getByText('Filas asociadas al catálogo')).toBeInTheDocument()
    expect(screen.getByText('✓ Filas significativas: 1')).toBeInTheDocument()
    expect(screen.getByText('✓ Filas con identidad Discogs: 1')).toBeInTheDocument()
    expect(screen.getByText('✓ Releases concretos únicos: 1')).toBeInTheDocument()
    expect(screen.getByText('✓ Revisión manual: 0')).toBeInTheDocument()
    expect(screen.queryByText(/✓ Requieren revisión:/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Vendida — omitida/i)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirmar recepción (1)' })).toBeEnabled()
  })

  it('shows exact-source replay rows as already received', async () => {
    api.importaciones.discogsJob.mockResolvedValue({
      ...completedJob,
      imported: 0,
      alreadyImported: 1,
      readyToImport: 0,
      newCopiesToReceive: 0,
      alreadyReceivedRows: 1,
      availableCopiesToReceive: 0,
      soldCopiesToReceive: 0,
      canConfirm: false,
      rows: [{
        ...completedJob.rows[0],
        catalogImportStatus: 'already_imported',
        status: 'already_imported',
        catalogImportErrorCode: 'ALREADY_RECEIVED',
        warningMessage: 'ALREADY_RECEIVED — Esta fila del mismo archivo ya recibió una copia física.',
      }],
    })

    render(<DiscogsTab />)

    expect(await screen.findByText('Ya importada')).toBeInTheDocument()
    expect(screen.getAllByText(/ALREADY_RECEIVED/).length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: 'Confirmar recepción (0)' })).toBeDisabled()
  })

  it('renders reconciliation counts and requires final confirmation', async () => {
    const preview = {
      ...completedJob,
      imported: 0,
      alreadyImported: 4,
      meaningfulRows: 8,
      identityBearingRows: 7,
      newCopiesToReceive: 3,
      alreadyReceivedRows: 4,
      availableCopiesToReceive: 2,
      soldCopiesToReceive: 1,
      noPriceRows: 2,
      noPriceReceivableRows: 2,
      manualReviewRows: 1,
      canConfirm: true,
    }
    api.importaciones.discogsJob.mockResolvedValue(preview)
    api.importaciones.discogsImportarJob.mockResolvedValue({
      ...preview,
      imported: 3,
      alreadyImported: 4,
      newCopiesToReceive: 0,
      alreadyReceivedRows: 7,
      canConfirm: false,
    })

    render(<DiscogsTab />)

    expect(await screen.findByText('Nuevas copias')).toBeInTheDocument()
    expect(screen.getByText('Nuevas copias').parentElement).toHaveTextContent('3')
    expect(screen.getByText('Revisión manual')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar recepción (3)' }))
    expect(await screen.findByRole('dialog')).toHaveTextContent('2 disponibles y 1 vendidas')
    expect(screen.getByRole('dialog')).toHaveTextContent('1 fila(s) requieren revisión manual')
    expect(screen.getByRole('dialog')).toHaveTextContent('4 fila(s) ya recibidas')
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar recepción' }))

    await waitFor(() => expect(api.importaciones.discogsImportarJob).toHaveBeenCalledWith(42))
    expect(await screen.findByText(/Recepción completada: 3 copias nuevas/)).toBeInTheDocument()
  })

  it('disables confirmation when an exact replay has no new copies', async () => {
    api.importaciones.discogsJob.mockResolvedValue({
      ...completedJob,
      imported: 0,
      alreadyImported: 113,
      newCopiesToReceive: 0,
      alreadyReceivedRows: 113,
      availableCopiesToReceive: 0,
      soldCopiesToReceive: 0,
      manualReviewRows: 1,
      canConfirm: false,
    })

    render(<DiscogsTab />)

    expect(await screen.findByText(/Este archivo exacto ya recibió sus copias físicas/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirmar recepción (0)' })).toBeDisabled()
    expect(api.importaciones.discogsImportarJob).not.toHaveBeenCalled()
  })
})

describe('DiscogsTab manual import', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.removeItem('sonograma:discogs-excel-job:v1')
    window.localStorage.removeItem('sonograma:discogs-manual-source:v1')
    api.importaciones.discogsManualPending.mockResolvedValue([])
    api.importaciones.discogsManualOperationContext.mockResolvedValue({})
    api.importaciones.discogsManualOperationAbandon.mockResolvedValue({ status: 'ABANDONED' })
    api.importaciones.discogsManualSourceReconciliation.mockResolvedValue(reconciliation())
  })

  it('shows source reconciliation and records explicit expected-count changes with version and reason', async () => {
    window.localStorage.setItem('sonograma:discogs-manual-source:v1', 'TESTSOURCE')
    api.importaciones.discogsManualExpectedCountUpdate
      .mockResolvedValueOnce(reconciliation({
        expectedCopyCount: 5,
        difference: -2,
        reconciliationStatus: 'DIFFERENCE',
        reconciliationNote: 'Conteo del proveedor',
        version: 0,
      }))
      .mockResolvedValueOnce(reconciliation({
        expectedCopyCount: 3,
        difference: 0,
        reconciliationStatus: 'MATCHED',
        reconciliationNote: 'Conteo corregido',
        version: 1,
      }))

    render(<DiscogsTab />)

    const section = await screen.findByRole('region', { name: 'Conciliación de TESTSOURCE' })
    expect(await within(section).findAllByText('Cantidad esperada no definida')).toHaveLength(2)
    expect(section).toHaveTextContent('Copias físicas3')
    expect(section).toHaveTextContent('Releases con más de una copia 1')

    fireEvent.click(within(section).getByRole('button', { name: 'Definir cantidad esperada' }))
    fireEvent.change(within(section).getByLabelText('Cantidad esperada'), { target: { value: '5' } })
    fireEvent.change(within(section).getByLabelText('Nota de conciliación'), { target: { value: 'Conteo del proveedor' } })
    fireEvent.click(within(section).getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(api.importaciones.discogsManualExpectedCountUpdate).toHaveBeenNthCalledWith(
      1,
      'TESTSOURCE',
      {
        expectedCopyCount: 5,
        version: null,
        note: 'Conteo del proveedor',
        changeReason: null,
      },
    ))
    expect(section).toHaveTextContent('Faltan demostrar 2 copias')

    fireEvent.click(within(section).getByRole('button', { name: 'Editar cantidad esperada' }))
    fireEvent.change(within(section).getByLabelText('Cantidad esperada'), { target: { value: '3' } })
    expect(within(section).getByLabelText('Motivo del cambio')).toBeRequired()
    fireEvent.change(within(section).getByLabelText('Nota de conciliación'), { target: { value: 'Conteo corregido' } })
    fireEvent.change(within(section).getByLabelText('Motivo del cambio'), { target: { value: 'Corrección confirmada' } })
    fireEvent.click(within(section).getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(api.importaciones.discogsManualExpectedCountUpdate).toHaveBeenNthCalledWith(
      2,
      'TESTSOURCE',
      {
        expectedCopyCount: 3,
        version: 0,
        note: 'Conteo corregido',
        changeReason: 'Corrección confirmada',
      },
    ))
    expect(section).toHaveTextContent('Las cantidades coinciden')
  })

  it('shows existing stock and disables confirmation while one operation is saving', async () => {
    let resolveSave
    api.importaciones.discogsDesdeLink
      .mockResolvedValueOnce(existingPreview)
      .mockResolvedValueOnce({ ...existingPreview, customerCode: '' })
    api.importaciones.discogsGuardar.mockImplementation(() => new Promise(resolve => { resolveSave = resolve }))

    render(<DiscogsTab />)
    fireEvent.change(screen.getByPlaceholderText('https://www.discogs.com/release/12345'), {
      target: { value: 'https://www.discogs.com/release/456' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))

    expect(await screen.findByText('Producto ya existente')).toBeInTheDocument()
    await waitFor(() => expect(api.importaciones.discogsManualSourceReconciliation)
      .toHaveBeenCalledWith('JPH'))
    expect(api.importaciones.discogsDesdeLink).toHaveBeenCalledWith(
      'https://www.discogs.com/release/456', '')
    expect(screen.getByText('Actualmente tiene 2 copias disponibles. Se agregará 1 copia nueva.')).toBeInTheDocument()
    expect(screen.getByDisplayValue('JPH')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('VG+, NM, M, G...')).toHaveValue('VG+')
    const save = screen.getByRole('button', { name: 'Agregar copia' })
    fireEvent.click(save)
    expect(save).toBeDisabled()
    await waitFor(() => expect(api.importaciones.discogsGuardar).toHaveBeenCalledWith({
        ...existingPreview,
        duplicateOverride: false,
        duplicateOverrideReason: null,
      }))

    resolveSave({ resultType: 'EXISTING_PRODUCT', copiesAdded: 1, availableCopies: 3, alreadyProcessed: false })
    expect(await screen.findByText(/Producto ya existente: se agregó 1 copia al stock\./)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Buscar otro' }))
    fireEvent.change(screen.getByPlaceholderText('https://www.discogs.com/release/12345'), {
      target: { value: 'https://www.discogs.com/release/789' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
    expect(await screen.findByDisplayValue('JPH')).toBeInTheDocument()
  })

  it('shows a structured duplicate warning and sends an explicit override with reason', async () => {
    api.importaciones.discogsDesdeLink.mockResolvedValue(existingPreview)
    const duplicate = new Error('Este release ya fue ingresado para JPH.')
    duplicate.code = 'MANUAL_DISCOGS_DUPLICATE'
    duplicate.data = {
      code: duplicate.code,
      sourceCustomerCode: 'JPH',
      discogsReleaseId: 456,
      existingCopies: [{ copyId: 22, copyNumber: 2, estado: 'VENDIDO', condicionFisica: 'NM', precioVenta: 1100 }],
    }
    api.importaciones.discogsGuardar
      .mockRejectedValueOnce(duplicate)
      .mockRejectedValueOnce(duplicate)
      .mockResolvedValueOnce({ resultType: 'EXISTING_PRODUCT', copiesAdded: 1, availableCopies: 3 })

    render(<DiscogsTab />)
    fireEvent.change(screen.getByPlaceholderText('https://www.discogs.com/release/12345'), {
      target: { value: 'https://www.discogs.com/release/456' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Agregar copia' }))

    const dialog = await screen.findByRole('dialog', { name: 'Confirmar copia física duplicada' })
    expect(dialog).toHaveTextContent('JPH')
    expect(dialog).toHaveTextContent('Copia 2 · Vendida')
    expect(dialog).toHaveTextContent('NM · $1100')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByRole('dialog', { name: 'Confirmar copia física duplicada' })).not.toBeInTheDocument()
    expect(api.importaciones.discogsGuardar).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'Agregar copia' }))
    const reopenedDialog = await screen.findByRole('dialog', { name: 'Confirmar copia física duplicada' })
    const reason = within(reopenedDialog).getByRole('textbox', { name: 'Motivo' })
    fireEvent.change(reason, { target: { value: '' } })
    expect(within(reopenedDialog).getByRole('button', { name: 'Confirmar otra copia' })).toBeDisabled()
    fireEvent.change(reason, { target: { value: 'Proveedor entregó otra copia física' } })
    fireEvent.click(within(reopenedDialog).getByRole('button', { name: 'Confirmar otra copia' }))

    await waitFor(() => expect(api.importaciones.discogsGuardar).toHaveBeenCalledTimes(3))
    expect(api.importaciones.discogsGuardar.mock.calls[2][0]).toEqual(expect.objectContaining({
      operationId: existingPreview.operationId,
      duplicateOverride: true,
      duplicateOverrideReason: 'Proveedor entregó otra copia física',
    }))
    expect(await screen.findByText(/Producto ya existente: se agregó 1 copia al stock/)).toBeInTheDocument()
  })

  it('refetches durable source pending operations after remount and abandons without deleting', async () => {
    const pending = [{
      operationId: 'pending-operation-1',
      discogsReleaseId: 777,
      sourceCustomerCode: 'TESTSOURCE',
      status: 'PENDING',
      submittedPrice: 990,
      submittedCondition: 'VG+',
    }]
    window.localStorage.setItem('sonograma:discogs-manual-source:v1', 'TESTSOURCE')
    api.importaciones.discogsManualPending.mockResolvedValue(pending)

    const first = render(<DiscogsTab />)
    expect(await screen.findByText('Release 777')).toBeInTheDocument()
    first.unmount()
    render(<DiscogsTab />)
    expect(await screen.findByText('Release 777')).toBeInTheDocument()
    expect(api.importaciones.discogsManualPending).toHaveBeenCalledWith('TESTSOURCE')

    fireEvent.click(screen.getByRole('button', { name: 'Descartar' }))
    await waitFor(() => expect(api.importaciones.discogsManualOperationAbandon)
      .toHaveBeenCalledWith('pending-operation-1'))
    expect(screen.queryByText('Release 777')).not.toBeInTheDocument()
  })
})
