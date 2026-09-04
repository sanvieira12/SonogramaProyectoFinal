import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import DiscosCatalogo from './DiscosCatalogo'
import { discoService } from '../services/discoService'
import { api } from '../api/sonograma'
import { downloadBlob } from '../utils/downloadBlob'

vi.mock('../utils/downloadBlob', () => ({
  downloadBlob: vi.fn(),
}))

vi.mock('../services/discoService', () => ({
  discoService: {
    getAll: vi.fn(),
    getPorFuenteImportacionDiscogs: vi.fn(),
    listarFuentesImportacionDiscogs: vi.fn(),
    buscar: vi.fn(),
    eliminar: vi.fn(),
    actualizar: vi.fn(),
    crear: vi.fn(),
    cambiarEstado: vi.fn(),
    actualizarCopias: vi.fn(),
  },
}))

  vi.mock('../api/sonograma', () => ({
  api: {
    discos: {
      porId: vi.fn(),
      eliminarCopia: vi.fn(),
      previews: { listar: vi.fn().mockResolvedValue([]) },
    },
    qr: {
      urlDescargaCopia: vi.fn(),
      descargarCopia: vi.fn(),
    },
    crm: { clientesRecomendados: vi.fn() },
    importaciones: {
      discogsManualBatchExcel: vi.fn(),
      discogsManualBatchZip: vi.fn(),
      discogsManualBatchFinalize: vi.fn(),
    },
  },
  FINANCIAL_DATA_CHANGED_EVENT: 'sonograma:financial-data-changed',
  resolveApiUrl: vi.fn(value => value || ''),
}))

vi.mock('../components/CompactPlayer', () => ({ default: () => null }))

const disco = {
  idDisco: 42,
  codigoInterno: 'CAT-42',
  artista: 'Deletion Artist',
  album: 'Deletion Album',
  estado: 'DISPONIBLE',
  condicion: 'USADO',
  condicionFisica: 'NM',
  cantidadCopias: 1,
  totalCopias: 1,
  qrCopies: [],
  audioPreviews: [],
  fechaIngreso: '2026-08-03T10:00:00',
}

function catalogDisco(overrides = {}) {
  const id = overrides.idDisco ?? 999999
  return {
    ...disco,
    idDisco: id,
    codigoInterno: `CAT-${id}`,
    artista: `Artist ${id}`,
    album: `Album ${id}`,
    condicion: 'NUEVO',
    ...overrides,
  }
}

describe('Catalog permanent deletion flow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.matchMedia = vi.fn().mockReturnValue({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })
    discoService.getAll.mockResolvedValue([disco])
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([])
  })

  async function openDeleteDialog() {
    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByText('Deletion Artist')
    fireEvent.click(screen.getAllByText('Deletion Artist')[0])
    fireEvent.click(await screen.findByRole('button', { name: 'Eliminar definitivamente' }))
    return screen.findByRole('dialog')
  }

  it('waits for backend success, then removes, closes, and refetches the Catalog', async () => {
    let resolveDelete
    discoService.eliminar.mockImplementation(() => new Promise(resolve => { resolveDelete = resolve }))
    const dialog = await openDeleteDialog()

    expect(within(dialog).getByText(/Esta acción no se puede deshacer/i)).toBeInTheDocument()
    expect(within(dialog).getByText(/historial de ventas y contabilidad se conservará/i)).toBeInTheDocument()
    const confirm = within(dialog).getByRole('button', { name: 'Eliminar definitivamente' })
    fireEvent.click(confirm)
    fireEvent.click(confirm)

    expect(discoService.eliminar).toHaveBeenCalledTimes(1)
    expect(discoService.eliminar).toHaveBeenCalledWith(42)
    expect(confirm).toBeDisabled()
    expect(screen.getAllByText('Deletion Artist').length).toBeGreaterThan(0)

    discoService.getAll.mockResolvedValue([])
    resolveDelete()

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await waitFor(() => expect(discoService.getAll).toHaveBeenCalledTimes(2))
    expect(screen.queryByText('Deletion Artist')).not.toBeInTheDocument()
  })

  it('shows backend errors in the confirmation and keeps the record visible', async () => {
    discoService.eliminar.mockRejectedValue(new Error('No se puede eliminar mientras tenga una reserva activa'))
    const dialog = await openDeleteDialog()

    fireEvent.click(within(dialog).getByRole('button', { name: 'Eliminar definitivamente' }))

    await waitFor(() => expect(within(dialog).getByRole('alert'))
      .toHaveTextContent('No se puede eliminar mientras tenga una reserva activa'))
    expect(screen.getAllByText('Deletion Artist').length).toBeGreaterThan(0)
    expect(discoService.getAll).toHaveBeenCalledTimes(1)
  })

  it('loads reverse recommendations only when Clientes afines is opened', async () => {
    api.crm.clientesRecomendados.mockResolvedValue([{
      cliente: { idCliente: 9, nombre: 'Ada', apellido: 'Lovelace', instagramUsuario: '@ada' },
      nivelAfinidad: 'ALTA', puntaje: 72,
      razones: ['Coincide con sus géneros habituales: Techno'],
    }])
    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByText('Deletion Artist')
    expect(api.crm.clientesRecomendados).not.toHaveBeenCalled()

    fireEvent.click(screen.getAllByText('Deletion Artist')[0])
    fireEvent.click(await screen.findByRole('button', { name: 'Clientes afines' }))

    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument()
    expect(api.crm.clientesRecomendados).toHaveBeenCalledWith(42)
    expect(screen.getByText('• Coincide con sus géneros habituales: Techno')).toBeInTheDocument()
  })

  it('shows physical condition separately from the used category', async () => {
    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)

    await screen.findByText('Deletion Artist')
    expect(screen.getAllByText('NM').length).toBeGreaterThan(0)

    fireEvent.click(screen.getAllByText('Deletion Artist')[0])
    expect(await screen.findByText('Categoría')).toBeInTheDocument()
    expect(screen.getAllByText('USADO').length).toBeGreaterThan(0)
  })

  it('labels an undefined catalogue price as Sin precio', async () => {
    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)

    await screen.findByText('Deletion Artist')
    expect(screen.getAllByText('Sin precio').length).toBeGreaterThan(0)
    expect(screen.queryByText('UYU $0')).not.toBeInTheDocument()
  })

  it('deletes one selected physical copy from the QR management dialog', async () => {
    const withCopy = {
      ...disco,
      qrCopies: [{ id: 77, copyNumber: 1, codigoQr: 'copy-77', estado: 'DISPONIBLE' }],
      totalCopias: 1,
    }
    api.discos.porId.mockResolvedValue(withCopy)
    api.discos.eliminarCopia.mockResolvedValue({ ...withCopy, qrCopies: [], totalCopias: 0, cantidadCopias: 0, estado: 'SIN_STOCK' })

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByText('Deletion Artist')
    fireEvent.click(screen.getAllByText('Deletion Artist')[0])
    fireEvent.click((await screen.findAllByRole('button', { name: 'Ver QR' }))[0])
    await screen.findByText('Código QR')

    fireEvent.click(screen.getByRole('button', { name: 'Eliminar copia' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/El producto y las demás copias se conservarán/i)).toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Eliminar copia' }))

    await waitFor(() => expect(api.discos.eliminarCopia).toHaveBeenCalledWith(42, 77))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByText('Este disco no tiene copias físicas con QR.')).toBeInTheDocument()
  })

  it('keeps a sold new product visible and orders by update date with entry-date fallback', async () => {
    discoService.getAll.mockResolvedValue([
      catalogDisco({
        idDisco: 1210,
        artista: 'Various',
        album: 'PACHA IBIZA CLASSICS LP 3x12"',
        estado: 'VENDIDO',
        cantidadCopias: 0,
        totalCopias: 1,
        fechaIngreso: '2026-08-31T14:53:34',
        fechaActualizacion: '2026-09-02T04:30:27',
      }),
      catalogDisco({
        idDisco: 1449,
        artista: 'Señor Coconut',
        fechaIngreso: '2026-09-01T09:00:00',
      }),
      catalogDisco({
        idDisco: 1437,
        artista: 'Invisible',
        fechaIngreso: '2026-09-02T03:00:00',
        fechaActualizacion: null,
      }),
      catalogDisco({
        idDisco: 999,
        artista: 'Used Artist',
        condicion: 'USADO',
        fechaIngreso: '2026-09-03T00:00:00',
      }),
    ])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByText('Various')

    fireEvent.click(screen.getByRole('button', { name: /Nuevos/i }))
    expect(screen.getByText('PACHA IBIZA CLASSICS LP 3x12"')).toBeInTheDocument()
    expect(screen.queryByText('Used Artist')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /Fecha importación/i }))
    const rows = within(screen.getByRole('table')).getAllByRole('row').slice(1)
    expect(rows[0]).toHaveTextContent('Various')
    expect(rows[0]).toHaveTextContent('Vendido')
    expect(rows[1]).toHaveTextContent('Invisible')
    expect(rows[2]).toHaveTextContent('Señor Coconut')
  })

  it('preserves state filters, search, and pagination behavior', async () => {
    const pageRecords = Array.from({ length: 21 }, (_, index) => catalogDisco({
      idDisco: index + 1,
      artista: `Paged Artist ${String(index + 1).padStart(2, '0')}`,
      estado: index === 20 ? 'VENDIDO' : 'DISPONIBLE',
    }))
    discoService.getAll.mockResolvedValue(pageRecords)

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByText('Paged Artist 01')
    expect(screen.getByText('Mostrando 1–20 de 21 registros')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    expect(screen.getByText('Paged Artist 21')).toBeInTheDocument()
    expect(screen.queryByText('Paged Artist 01')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /^Vendido/ }))
    expect(screen.getByText('Paged Artist 21')).toBeInTheDocument()
    expect(screen.queryByText('Paged Artist 20')).not.toBeInTheDocument()

    const searchResult = catalogDisco({ idDisco: 88, artista: 'Search Result Artist' })
    discoService.buscar.mockResolvedValue([searchResult])
    fireEvent.click(screen.getAllByRole('button', { name: /^Todos/ })[0])
    fireEvent.change(screen.getByPlaceholderText('Buscar disco, artista o código...'), {
      target: { value: 'Search Result' },
    })

    await waitFor(() => expect(discoService.buscar).toHaveBeenCalledWith('Search Result'), { timeout: 1000 })
    expect(await screen.findByText('Search Result Artist')).toBeInTheDocument()
  })

  it('filters the catalogue by a persisted Discogs source without exposing job details', async () => {
    const reused = catalogDisco({ idDisco: 806, artista: 'Producto reutilizado' })
    const newlyCreated = catalogDisco({ idDisco: 1450, artista: 'Producto nuevo' })
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([
      { key: 'discos pin.xlsx', label: 'Discos PIN.xlsx', productos: 238 },
    ])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([reused, newlyCreated])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByRole('option', { name: /Discos PIN.xlsx.*238 productos/i })
    expect(screen.queryByRole('option', { name: /Job\s*\d+/i })).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Importación Discogs'), { target: { value: 'discos pin.xlsx' } })

    await waitFor(() => expect(discoService.getPorFuenteImportacionDiscogs)
      .toHaveBeenCalledWith('discos pin.xlsx'))
    expect(await screen.findByText('Producto reutilizado')).toBeInTheDocument()
    expect(screen.getByText('Producto nuevo')).toBeInTheDocument()
  })

  it('renders persisted Discogs sources without duplicate names', async () => {
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([
      { key: 'jph para catalogo y web.xlsx', label: 'JPH PARA CATALOGO Y WEB.xlsx', productos: 2 },
      { key: 'discos pin.xlsx', label: 'Discos PIN.xlsx', productos: 238 },
    ])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)

    await screen.findByRole('option', { name: /JPH PARA CATALOGO Y WEB\.xlsx.*2 productos/i })
    expect(screen.getAllByRole('option', { name: /Discos PIN\.xlsx/i })).toHaveLength(1)
    expect(screen.getByRole('option', { name: 'Todas las importaciones' })).toBeInTheDocument()
  })

  it('shows independent manual batches with physical-copy labels and scoped summary', async () => {
    const firstProduct = catalogDisco({
      idDisco: 501,
      artista: 'Producto del batch 1',
      manualBatchPrecioVenta: 1750,
      manualBatchCondicionFisica: 'VG+',
    })
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([
      {
        type: 'MANUAL', key: 'manual:11', label: 'JPH · 2 discos · En curso',
        customerCode: 'JPH', status: 'OPEN', batchId: 11, copyCount: 2,
      },
      {
        type: 'MANUAL', key: 'manual:12', label: 'JPH · 1 discos · Finalizada',
        customerCode: 'JPH', status: 'FINALIZED', batchId: 12, copyCount: 1,
      },
    ])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([firstProduct])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)

    expect(await screen.findByRole('option', { name: 'JPH · 2 discos · En curso' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'JPH · 1 discos · Finalizada' })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Importación Discogs'), { target: { value: 'manual:11' } })

    await waitFor(() => expect(discoService.getPorFuenteImportacionDiscogs)
      .toHaveBeenCalledWith('manual:11'))
    expect(await screen.findByTestId('manual-batch-summary'))
      .toHaveTextContent('JPH · 2 discos · En curso')
    expect(screen.getByText('Producto del batch 1')).toBeInTheDocument()
    expect(screen.getByText('VG+')).toBeInTheDocument()
    expect(screen.getByText('UYU $1.750')).toBeInTheDocument()
    expect(screen.queryByText('Producto del batch 2')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Descargar ZIP' })).toBeInTheDocument()
  })

  it('shows the selected manual batch customer code without replacing the internal code', async () => {
    const product = catalogDisco({
      idDisco: 508,
      codigoInterno: 'Z-2007-1019255',
      manualBatchCustomerCode: 'SV3',
      artista: 'Z@P',
      album: 'Palvince EP',
      genero: 'Tech House',
    })
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([
      { type: 'MANUAL', key: 'manual:51', label: 'SV3 · 1 discos · En curso', customerCode: 'SV3', status: 'OPEN', batchId: 51, copyCount: 1 },
    ])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([product])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByRole('option', { name: 'SV3 · 1 discos · En curso' })
    fireEvent.change(screen.getByLabelText('Importación Discogs'), { target: { value: 'manual:51' } })
    const artist = await screen.findByText('Z@P')
    fireEvent.mouseEnter(artist.closest('tr'))

    expect(await screen.findByText('Tech House')).toBeInTheDocument()
    expect(screen.getByText('SV3', { exact: true })).toBeInTheDocument()
    expect(screen.queryByText('Código: Z-2007-1019255')).not.toBeInTheDocument()
  })

  it('exports an OPEN manual batch ZIP and triggers a browser download', async () => {
    const blob = new Blob(['zip'], { type: 'application/zip' })
    api.importaciones.discogsManualBatchZip.mockResolvedValue({
      blob, filename: 'JPH_2026-09-04_batch-31.zip',
    })
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([{
      type: 'MANUAL', key: 'manual:31', label: 'JPH · 1 discos · En curso',
      customerCode: 'JPH', status: 'OPEN', batchId: 31, copyCount: 1,
    }])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([catalogDisco({ idDisco: 504 })])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByRole('option', { name: 'JPH · 1 discos · En curso' })
    fireEvent.change(screen.getByLabelText('Importación Discogs'), { target: { value: 'manual:31' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Descargar ZIP' }))

    await waitFor(() => expect(api.importaciones.discogsManualBatchZip).toHaveBeenCalledWith(31))
    expect(downloadBlob).toHaveBeenCalledWith(blob, 'JPH_2026-09-04_batch-31.zip', undefined)
    expect(screen.getByRole('button', { name: 'Descargar ZIP' })).toBeEnabled()
  })

  it('prevents duplicate ZIP requests and restores the action after a Spanish error', async () => {
    let rejectExport
    api.importaciones.discogsManualBatchZip.mockImplementation(() => new Promise((resolve, reject) => {
      rejectExport = reject
    }))
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([{
      type: 'MANUAL', key: 'manual:32', label: 'JPH · 1 discos · Finalizada',
      customerCode: 'JPH', status: 'FINALIZED', batchId: 32, copyCount: 1,
    }])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([catalogDisco({ idDisco: 505 })])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByRole('option', { name: 'JPH · 1 discos · Finalizada' })
    fireEvent.change(screen.getByLabelText('Importación Discogs'), { target: { value: 'manual:32' } })

    const zipButton = await screen.findByRole('button', { name: 'Descargar ZIP' })
    fireEvent.click(zipButton)
    fireEvent.click(zipButton)
    expect(api.importaciones.discogsManualBatchZip).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Generando ZIP…' })).toBeDisabled()
    expect(screen.getByTestId('manual-batch-zip-progress')).toHaveTextContent('Generando archivo')

    rejectExport(new Error('Batch sin copias'))
    expect(await screen.findByRole('alert')).toHaveTextContent('Batch sin copias')
    expect(screen.getByRole('button', { name: 'Descargar ZIP' })).toBeEnabled()
  })

  it('requires confirmation and finalizes an OPEN batch while preserving downloads', async () => {
    let resolveFinalize
    api.importaciones.discogsManualBatchFinalize.mockImplementation(() => new Promise(resolve => {
      resolveFinalize = resolve
    }))
    discoService.listarFuentesImportacionDiscogs
      .mockResolvedValueOnce([{
        type: 'MANUAL', key: 'manual:41', label: 'JPH · 2 discos · En curso',
        customerCode: 'JPH', status: 'OPEN', batchId: 41, copyCount: 2,
      }])
      .mockResolvedValueOnce([{
        type: 'MANUAL', key: 'manual:41', label: 'JPH · 2 discos · Finalizada',
        customerCode: 'JPH', status: 'FINALIZED', batchId: 41, copyCount: 2,
      }])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([catalogDisco({ idDisco: 506 })])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByRole('option', { name: 'JPH · 2 discos · En curso' })
    fireEvent.change(screen.getByLabelText('Importación Discogs'), { target: { value: 'manual:41' } })

    fireEvent.click(await screen.findByRole('button', { name: 'Finalizar importación' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/Ya no se podrán agregar nuevas copias/i)).toBeInTheDocument()
    expect(api.importaciones.discogsManualBatchFinalize).not.toHaveBeenCalled()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(api.importaciones.discogsManualBatchFinalize).not.toHaveBeenCalled()

    fireEvent.click(await screen.findByRole('button', { name: 'Finalizar importación' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Finalizar importación' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Finalizando…' }))
    expect(api.importaciones.discogsManualBatchFinalize).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Finalizando…' })).toBeDisabled()

    resolveFinalize({ batchId: 41, status: 'FINALIZED', finalizedAt: '2026-09-04T12:00:00' })
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Finalizar importación' })).not.toBeInTheDocument())
    expect(screen.getByRole('option', { name: 'JPH · 2 discos · Finalizada' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Exportar Excel' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Descargar ZIP' })).toBeInTheDocument()
  })

  it('restores finalization action and shows a Spanish error when finalization fails', async () => {
    api.importaciones.discogsManualBatchFinalize.mockRejectedValue(new Error('El batch ya está finalizado'))
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([{
      type: 'MANUAL', key: 'manual:42', label: 'JPH · 1 discos · En curso',
      customerCode: 'JPH', status: 'OPEN', batchId: 42, copyCount: 1,
    }])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([catalogDisco({ idDisco: 507 })])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByRole('option', { name: 'JPH · 1 discos · En curso' })
    fireEvent.change(screen.getByLabelText('Importación Discogs'), { target: { value: 'manual:42' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Finalizar importación' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Finalizar importación' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('El batch ya está finalizado')
    expect(within(screen.getByTestId('manual-batch-summary'))
      .getByRole('button', { name: 'Finalizar importación' })).toBeEnabled()
  })

  it('shows truthful export progress, prevents duplicate clicks, and keeps re-download available', async () => {
    let resolveExport
    api.importaciones.discogsManualBatchExcel.mockImplementation(() => new Promise(resolve => {
      resolveExport = resolve
    }))
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([{
      type: 'MANUAL', key: 'manual:21', label: 'PIN · 1 discos · Finalizada',
      customerCode: 'PIN', status: 'FINALIZED', batchId: 21, copyCount: 1,
    }])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([catalogDisco({ idDisco: 503 })])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByRole('option', { name: 'PIN · 1 discos · Finalizada' })
    fireEvent.change(screen.getByLabelText('Importación Discogs'), { target: { value: 'manual:21' } })

    const exportButton = await screen.findByRole('button', { name: 'Exportar Excel' })
    fireEvent.click(exportButton)
    fireEvent.click(exportButton)
    expect(api.importaciones.discogsManualBatchExcel).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Generando Excel…' })).toBeDisabled()
    expect(screen.getByTestId('manual-batch-export-progress')).toHaveTextContent('Generando archivo')

    resolveExport({ blob: new Blob(['xlsx']), filename: 'PIN_2026-09-04_batch-21.xlsx' })
    await waitFor(() => expect(screen.getByRole('button', { name: 'Descargar Excel' })).toBeEnabled())
  })

  it('restores the manual export button and shows a Spanish error when export fails', async () => {
    api.importaciones.discogsManualBatchExcel.mockRejectedValue(new Error('Batch sin copias'))
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([{
      type: 'MANUAL', key: 'manual:22', label: 'PIN · 0 discos · En curso',
      customerCode: 'PIN', status: 'OPEN', batchId: 22, copyCount: 0,
    }])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByRole('option', { name: 'PIN · 0 discos · En curso' })
    fireEvent.change(screen.getByLabelText('Importación Discogs'), { target: { value: 'manual:22' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Exportar Excel' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Batch sin copias')
    expect(screen.getByRole('button', { name: 'Exportar Excel' })).toBeEnabled()
  })

  it('does not show a manual batch summary when all imports are selected', async () => {
    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)

    await screen.findByText('Deletion Artist')
    expect(screen.queryByTestId('manual-batch-summary')).not.toBeInTheDocument()
  })
})
