import { render, screen } from '@testing-library/react'
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

describe('DiscogsTab Excel import', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.setItem('sonograma:discogs-excel-job:v1', '42')
    api.importaciones.discogsJob.mockResolvedValue(completedJob)
  })

  it('shows sold as source metadata and keeps the identifiable row importable as USADO', async () => {
    render(<DiscogsTab />)

    expect(await screen.findByText('Filas detectadas')).toBeInTheDocument()
    expect(screen.getByText('Estado Excel: vendidos')).toBeInTheDocument()
    expect(screen.getByText('Catálogo: USADO')).toBeInTheDocument()
    expect(screen.getByText('Copias importadas en esta carga')).toBeInTheDocument()
    expect(screen.getByText('Productos de catálogo afectados')).toBeInTheDocument()
    expect(screen.getByText('Filas asociadas al catálogo')).toBeInTheDocument()
    expect(screen.queryByText(/Vendida — omitida/i)).not.toBeInTheDocument()
    expect(screen.getByRole('button', {
      name: 'Importar todas las filas identificables (1)',
    })).toBeEnabled()
  })
})
