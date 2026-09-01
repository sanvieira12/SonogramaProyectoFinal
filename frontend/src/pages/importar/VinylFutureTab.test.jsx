import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import VinylFutureTab from './VinylFutureTab'
import { api } from '../../api/sonograma'

vi.mock('../../api/sonograma', () => ({
  resolveApiUrl: value => value,
  api: {
    importar: {
      vinylfutureValidar: vi.fn(),
      vinylfutureConfirmar: vi.fn(),
      vinylfutureCancelar: vi.fn(),
      vinylfutureJob: vi.fn(),
      vinylfutureZip: vi.fn(),
      vinylfuturePendientes: vi.fn(),
      vinylfutureManualBuscar: vi.fn(),
      vinylfutureManualConfirmar: vi.fn(),
      vinylfuturePortada: vi.fn(),
      vinylfutureProductoZip: vi.fn(),
    },
  },
}))

const discrepantValidation = {
  validationId: 'validation-1',
  invoiceNumber: '0036-188471',
  declaredQuantity: 32,
  detectedSourceRows: 31,
  parsedRows: 30,
  unparsedRows: 1,
  parsedPhysicalQuantity: 30,
  pendingPhysicalQuantity: 2,
  consistent: false,
  warnings: [],
  errors: ['La cantidad declarada (32) no coincide con las copias interpretadas (30).'],
  sourceRows: [{
    sourceRowNumber: 31,
    pageNumber: 2,
    sourceText: 'BAD02 - Artista- Título 10,00 X 10,00',
    status: 'REVIEW_REQUIRED',
    estimatedQuantity: null,
    reason: 'No se pudo determinar la cantidad.',
  }],
}

function uploadPdf() {
  const input = document.querySelector('input[type="file"]')
  fireEvent.change(input, { target: { files: [new File(['pdf'], 'factura.pdf', { type: 'application/pdf' })] } })
  fireEvent.click(screen.getByRole('button', { name: 'Subir factura PDF' }))
}

describe('VinylFutureTab: validación previa', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.importar.vinylfutureValidar.mockResolvedValue(discrepantValidation)
    api.importar.vinylfutureCancelar.mockResolvedValue(null)
    api.importar.vinylfuturePendientes.mockResolvedValue([])
  })

  it('muestra la discrepancia y la línea pendiente antes de importar', async () => {
    render(<VinylFutureTab />)
    uploadPdf()

    expect(await screen.findByText('Se detectaron elementos que requieren revisión')).toBeInTheDocument()
    expect(screen.getByText('32')).toBeInTheDocument()
    expect(screen.getByText('30')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
    expect(screen.getByText(/BAD02 - Artista/)).toBeInTheDocument()
    expect(api.importar.vinylfutureConfirmar).not.toHaveBeenCalled()
  })

  it('cancela sin confirmar ningún cambio', async () => {
    render(<VinylFutureTab />)
    uploadPdf()
    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar importación' }))

    await waitFor(() => expect(api.importar.vinylfutureCancelar).toHaveBeenCalledWith('validation-1'))
    expect(api.importar.vinylfutureConfirmar).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Subir factura PDF' })).toBeInTheDocument()
  })

  it('solo continúa parcialmente después de una confirmación explícita', async () => {
    api.importar.vinylfutureConfirmar.mockResolvedValue({ jobId: 'job-1' })
    api.importar.vinylfutureJob.mockResolvedValue({
      jobId: 'job-1', status: 'RUNNING', progressPercent: 5, currentStep: 'Factura recibida',
    })
    render(<VinylFutureTab />)
    uploadPdf()
    fireEvent.click(await screen.findByRole('button', { name: 'Continuar con 30 copias válidas' }))

    await waitFor(() => expect(api.importar.vinylfutureConfirmar).toHaveBeenCalledWith('validation-1', true))
  })
})

describe('VinylFutureTab: importación manual y recuperación', () => {
  const preview = {
    previewId: 'preview-1',
    pendingItemId: null,
    suggestedQuantity: 1,
    sourceUrl: 'https://www.vinylfuture.com/product__123',
    catalogueCode: 'TEST-1',
    artist: 'Artista',
    title: 'Título',
    format: '12"',
    label: 'Sello',
    year: 2026,
    genre: 'House',
    country: 'Alemania',
    metadataStatus: 'Información disponible',
    existingProduct: true,
    existingProductId: 10,
    coverAvailable: false,
    tracks: [],
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.importar.vinylfuturePendientes.mockResolvedValue([])
    api.importar.vinylfutureManualBuscar.mockResolvedValue(preview)
    api.importar.vinylfutureManualConfirmar.mockResolvedValue({
      previewId: 'preview-1', productId: 10, catalogueStatus: 'EXISTENTE',
      addedCopies: 2, resultingStock: 5, pendingItemResolved: false, alreadyProcessed: false,
    })
  })

  it('busca un enlace, avisa que reutiliza el producto y agrega la cantidad elegida', async () => {
    render(<VinylFutureTab />)

    fireEvent.change(screen.getByLabelText('Enlace de Vinyl Future'), {
      target: { value: preview.sourceUrl },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))

    expect(await screen.findByText('Producto encontrado')).toBeInTheDocument()
    expect(screen.getByText(/Producto ya existente/)).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Copias físicas a agregar'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: 'Agregar copia al stock' }))

    await waitFor(() => expect(api.importar.vinylfutureManualConfirmar).toHaveBeenCalledWith('preview-1', 2))
    expect(await screen.findByText(/se agregaron 2 copia/)).toBeInTheDocument()
  })

  it('abre la resolución manual conservando la referencia y cantidad estimada', async () => {
    const pending = {
      pendingItemId: 51,
      orderId: 50,
      invoiceNumber: 'INV-PENDING',
      pageNumber: 2,
      sourceText: 'línea ambigua',
      reviewReason: 'cantidad ambigua',
      estimatedQuantity: 3,
    }
    api.importar.vinylfuturePendientes.mockResolvedValue([pending])
    api.importar.vinylfutureManualBuscar.mockResolvedValue({
      ...preview, pendingItemId: 51, suggestedQuantity: 3,
    })
    render(<VinylFutureTab />)

    fireEvent.click(await screen.findByRole('button', { name: 'Resolver manualmente' }))

    expect(screen.getByText(/Resolviendo un elemento de la factura INV-PENDING/)).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Enlace de Vinyl Future'), {
      target: { value: preview.sourceUrl },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
    await waitFor(() => expect(api.importar.vinylfutureManualBuscar)
      .toHaveBeenCalledWith(preview.sourceUrl, 51))
    expect(await screen.findByLabelText('Copias físicas a agregar')).toHaveValue(3)
  })
})
