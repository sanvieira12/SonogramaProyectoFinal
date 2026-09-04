import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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
  cantidadCopias: 1,
  productoExistente: true,
  copiasDisponibles: 2,
  errores: [],
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
  })

  it('shows existing stock and disables confirmation while one operation is saving', async () => {
    let resolveSave
    api.importaciones.discogsDesdeLink.mockResolvedValue(existingPreview)
    api.importaciones.discogsGuardar.mockImplementation(() => new Promise(resolve => { resolveSave = resolve }))

    render(<DiscogsTab />)
    fireEvent.change(screen.getByPlaceholderText('https://www.discogs.com/release/12345'), {
      target: { value: 'https://www.discogs.com/release/456' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))

    expect(await screen.findByText('Producto ya existente')).toBeInTheDocument()
    expect(screen.getByText('Actualmente tiene 2 copias disponibles. Se agregará 1 copia nueva.')).toBeInTheDocument()
    const save = screen.getByRole('button', { name: 'Agregar copia' })
    fireEvent.click(save)
    expect(save).toBeDisabled()
    expect(api.importaciones.discogsGuardar).toHaveBeenCalledWith(existingPreview)

    resolveSave({ resultType: 'EXISTING_PRODUCT', copiesAdded: 1, availableCopies: 3, alreadyProcessed: false })
    expect(await screen.findByText(/Producto ya existente: se agregó 1 copia al stock\./)).toBeInTheDocument()
  })
})
