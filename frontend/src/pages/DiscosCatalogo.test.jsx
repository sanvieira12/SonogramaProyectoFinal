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
      copias: vi.fn(),
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
      discogsManualSourceExcel: vi.fn(),
      discogsManualBatchZip: vi.fn(),
      discogsManualBatchFinalize: vi.fn(),
      discogsManualSourceReconciliation: vi.fn(),
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

function copyDetail(overrides = {}) {
  const id = overrides.id ?? 1
  const copyNumber = overrides.copyNumber ?? id
  return {
    id,
    productId: overrides.productId ?? 42,
    copyNumber,
    codigoQr: `qr-${id}`,
    estado: 'DISPONIBLE',
    precioVenta: 1100,
    condicionFisica: 'NM',
    createdAt: '2026-09-01T10:00:00',
    manualBatchId: 11,
    sourceCustomerCode: 'LO',
    normalizedSourceCustomerCode: 'LO',
    dispositionReason: null,
    dispositionNote: null,
    disposedAt: null,
    disposedBy: null,
    updatedAt: '2026-09-01T10:00:00',
    ...overrides,
  }
}

function matchedReconciliation(overrides = {}) {
  return {
    sourceCustomerCode: 'JPH',
    normalizedSourceCustomerCode: 'JPH',
    expectedCopyCount: 2,
    provablePhysicalCopyCount: 2,
    availableCopyCount: 2,
    soldCopyCount: 0,
    removedCopyCount: 0,
    distinctReleaseCount: 2,
    duplicateReleaseGroupCount: 0,
    extraDuplicateCopyCount: 0,
    pendingOperationCount: 0,
    completedOperationCount: 2,
    abandonedOperationCount: 0,
    difference: 0,
    reconciliationStatus: 'MATCHED',
    version: 0,
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
    api.discos.copias.mockResolvedValue([])
    api.importaciones.discogsManualSourceReconciliation.mockResolvedValue(matchedReconciliation())
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

  it('collapses legacy technical batch selectors by customer while rendering the grouped summary', async () => {
    const firstProduct = catalogDisco({
      idDisco: 501,
      artista: 'Producto del batch 1',
      manualBatchPrecioVenta: 1750,
      manualBatchCondicionFisica: 'VG+',
    })
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([
      {
        type: 'MANUAL', key: 'manual:11', label: 'JPH · 2 copias físicas · En curso',
        customerCode: 'JPH', status: 'OPEN', batchId: 11, copyCount: 2,
      },
      {
        type: 'MANUAL', key: 'manual:12', label: 'JPH · 1 copias físicas · Finalizada',
        customerCode: 'JPH', status: 'FINALIZED', batchId: 12, copyCount: 1,
      },
    ])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([firstProduct])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)

    expect(await screen.findByRole('option', { name: 'JPH · 3 copias físicas · En curso' })).toBeInTheDocument()
    expect(screen.getAllByRole('option', { name: /JPH · 3 copias físicas/ })).toHaveLength(1)
    fireEvent.change(screen.getByLabelText('Importación Discogs'), {
      target: { value: 'manual:customer:JPH' },
    })

    await waitFor(() => expect(discoService.getPorFuenteImportacionDiscogs)
      .toHaveBeenCalledWith('manual:customer:JPH'))
    expect(await screen.findByTestId('manual-batch-summary'))
      .toHaveTextContent('JPH · 3 copias físicas · En curso')
    expect(screen.getByText('Producto del batch 1')).toBeInTheDocument()
    expect(screen.getByText('VG+')).toBeInTheDocument()
    expect(screen.getByText('UYU $1.750')).toBeInTheDocument()
    expect(screen.queryByText('Producto del batch 2')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Descargar ZIP' })).toBeInTheDocument()
  })

  it('renders one logical customer selector and loads copies from all technical batches', async () => {
    const releaseFromFirstBatch = catalogDisco({ idDisco: 601, artista: 'Release from first batch' })
    const releaseFromSecondBatch = catalogDisco({ idDisco: 602, artista: 'Release from second batch' })
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([{
      type: 'MANUAL',
      key: 'manual:customer:JPH',
      label: 'JPH · 3 copias físicas · En curso',
      customerCode: 'JPH',
      status: 'OPEN',
      batchId: 12,
      copyCount: 3,
    }])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([
      releaseFromFirstBatch,
      releaseFromSecondBatch,
    ])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)

    expect(await screen.findByRole('option', { name: 'JPH · 3 copias físicas · En curso' })).toBeInTheDocument()
    expect(screen.getAllByRole('option', { name: /JPH · 3 copias físicas/ })).toHaveLength(1)
    fireEvent.change(screen.getByLabelText('Importación Discogs'), {
      target: { value: 'manual:customer:JPH' },
    })

    await waitFor(() => expect(discoService.getPorFuenteImportacionDiscogs)
      .toHaveBeenCalledWith('manual:customer:JPH'))
    expect(await screen.findByText('Release from first batch')).toBeInTheDocument()
    expect(screen.getByText('Release from second batch')).toBeInTheDocument()
  })

  it('keeps logical-source physical copy count distinct from deduplicated product rows', async () => {
    const products = [
      catalogDisco({ idDisco: 611, artista: 'Shared release with two copies' }),
      catalogDisco({ idDisco: 612, artista: 'Second release' }),
      catalogDisco({ idDisco: 613, artista: 'Third release' }),
    ]
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([{
      type: 'MANUAL',
      key: 'manual:customer:TESTSOURCE',
      label: 'TESTSOURCE · 4 copias físicas · En curso',
      customerCode: 'TESTSOURCE',
      status: 'OPEN',
      batchId: 72,
      copyCount: 4,
    }])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue(products)

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)

    expect(await screen.findByRole('option', { name: 'TESTSOURCE · 4 copias físicas · En curso' })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Importación Discogs'), {
      target: { value: 'manual:customer:TESTSOURCE' },
    })
    await waitFor(() => expect(discoService.getPorFuenteImportacionDiscogs)
      .toHaveBeenCalledWith('manual:customer:TESTSOURCE'))
    expect(await screen.findByText('Shared release with two copies')).toBeInTheDocument()
    expect(screen.getByText('Second release')).toBeInTheDocument()
    expect(screen.getByText('Third release')).toBeInTheDocument()
    expect(screen.getAllByRole('option', { name: /TESTSOURCE · 4 copias físicas/ })).toHaveLength(1)
  })

  it('keeps manual customer results visible when searching within the logical source', async () => {
    const product = catalogDisco({
      idDisco: 603,
      artista: 'Manual customer release',
      manualBatchCustomerCode: 'JS',
    })
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([{
      type: 'MANUAL',
      key: 'manual:customer:JS',
      label: 'JS · 1 copias físicas · Finalizada',
      customerCode: 'JS',
      status: 'FINALIZED',
      batchId: 13,
      copyCount: 1,
    }])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([product])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)

    await screen.findByRole('option', { name: 'JS · 1 copias físicas · Finalizada' })
    fireEvent.change(screen.getByLabelText('Importación Discogs'), {
      target: { value: 'manual:customer:JS' },
    })
    await screen.findByText('Manual customer release')
    fireEvent.change(screen.getByPlaceholderText('Buscar disco, artista o código...'), {
      target: { value: ' js ' },
    })

    expect(screen.getByText('Manual customer release')).toBeInTheDocument()
    expect(discoService.buscar).not.toHaveBeenCalled()
  })

  it('collapses four legacy JS technical entries into one logical selector', async () => {
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([
      { type: 'MANUAL', key: 'manual:101', customerCode: 'JS', status: 'FINALIZED', batchId: 101, copyCount: 1 },
      { type: 'MANUAL', key: 'manual:102', customerCode: 'js', status: 'FINALIZED', batchId: 102, copyCount: 1 },
      { type: 'MANUAL', key: 'manual:103', customerCode: ' JS ', status: 'FINALIZED', batchId: 103, copyCount: 1 },
      { type: 'MANUAL', key: 'manual:104', customerCode: 'Js', status: 'FINALIZED', batchId: 104, copyCount: 51 },
    ])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([
      catalogDisco({ idDisco: 701, artista: 'Historical JS release' }),
    ])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)

    expect(await screen.findByRole('option', { name: 'JS · 54 copias físicas · Finalizada' })).toBeInTheDocument()
    expect(screen.getAllByRole('option', { name: /JS · 54 copias físicas/ })).toHaveLength(1)
    expect(screen.queryByRole('option', { name: /manual:10/ })).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Importación Discogs'), {
      target: { value: 'manual:customer:JS' },
    })

    await waitFor(() => expect(discoService.getPorFuenteImportacionDiscogs)
      .toHaveBeenCalledWith('manual:customer:JS'))
    expect(await screen.findByText('Historical JS release')).toBeInTheDocument()
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
      { type: 'MANUAL', key: 'manual:51', label: 'SV3 · 1 copias físicas · En curso', customerCode: 'SV3', status: 'OPEN', batchId: 51, copyCount: 1 },
    ])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([product])
    api.discos.copias.mockResolvedValue([
      copyDetail({ id: 5081, productId: 508, sourceCustomerCode: 'SV3', normalizedSourceCustomerCode: 'SV3' }),
    ])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByRole('option', { name: 'SV3 · 1 copias físicas · En curso' })
    fireEvent.change(screen.getByLabelText('Importación Discogs'), { target: { value: 'manual:51' } })
    const artist = await screen.findByText('Z@P')
    fireEvent.mouseEnter(artist.closest('tr'))

    expect(await screen.findByText('Tech House')).toBeInTheDocument()
    const section = await screen.findByTestId('physical-copy-section')
    expect(within(section).getByText('SV3', { exact: true })).toBeInTheDocument()
    expect(screen.getByText('Z-2007-1019255')).toBeInTheDocument()
    expect(screen.getByText('SKU del producto')).toBeInTheDocument()
  })

  it('exports an OPEN manual batch ZIP and triggers a browser download', async () => {
    const blob = new Blob(['zip'], { type: 'application/zip' })
    api.importaciones.discogsManualBatchZip.mockResolvedValue({
      blob, filename: 'JPH_2026-09-04_batch-31.zip',
    })
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([{
      type: 'MANUAL', key: 'manual:31', label: 'JPH · 1 copias físicas · En curso',
      customerCode: 'JPH', status: 'OPEN', batchId: 31, copyCount: 1,
    }])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([catalogDisco({ idDisco: 504 })])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByRole('option', { name: 'JPH · 1 copias físicas · En curso' })
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
      type: 'MANUAL', key: 'manual:32', label: 'JPH · 1 copias físicas · Finalizada',
      customerCode: 'JPH', status: 'FINALIZED', batchId: 32, copyCount: 1,
    }])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([catalogDisco({ idDisco: 505 })])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByRole('option', { name: 'JPH · 1 copias físicas · Finalizada' })
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
        type: 'MANUAL', key: 'manual:41', label: 'JPH · 2 copias físicas · En curso',
        customerCode: 'JPH', status: 'OPEN', batchId: 41, copyCount: 2,
      }])
      .mockResolvedValueOnce([{
        type: 'MANUAL', key: 'manual:41', label: 'JPH · 2 copias físicas · Finalizada',
        customerCode: 'JPH', status: 'FINALIZED', batchId: 41, copyCount: 2,
      }])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([catalogDisco({ idDisco: 506 })])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByRole('option', { name: 'JPH · 2 copias físicas · En curso' })
    fireEvent.change(screen.getByLabelText('Importación Discogs'), { target: { value: 'manual:41' } })

    fireEvent.click(await screen.findByRole('button', { name: 'Finalizar importación' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/Ya no se podrán agregar nuevas copias/i)).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Porcentaje Sonograma')).toBeInTheDocument()
    expect(within(dialog).getAllByRole('option').map(option => option.value)).toEqual(['', '10', '15', '20', '25', '30', '35', '40', '45'])
    expect(within(dialog).queryByRole('spinbutton')).not.toBeInTheDocument()
    expect(api.importaciones.discogsManualBatchFinalize).not.toHaveBeenCalled()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(api.importaciones.discogsManualBatchFinalize).not.toHaveBeenCalled()

    fireEvent.click(await screen.findByRole('button', { name: 'Finalizar importación' }))
    const reopenedDialog = await screen.findByRole('dialog')
    fireEvent.change(within(reopenedDialog).getByLabelText('Porcentaje Sonograma'), { target: { value: '30' } })
    fireEvent.click(within(reopenedDialog).getByRole('button', { name: 'Finalizar importación' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Finalizando…' }))
    expect(api.importaciones.discogsManualBatchFinalize).toHaveBeenCalledTimes(1)
    expect(api.importaciones.discogsManualBatchFinalize).toHaveBeenCalledWith(41, 30, false)
    expect(screen.getByRole('button', { name: 'Finalizando…' })).toBeDisabled()

    resolveFinalize({ batchId: 41, status: 'FINALIZED', finalizedAt: '2026-09-04T12:00:00', porcentajeSonograma: 30 })
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Finalizar importación' })).not.toBeInTheDocument())
    expect(screen.getByRole('option', { name: 'JPH · 2 copias físicas · Finalizada' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Exportar Excel' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Descargar ZIP' })).toBeInTheDocument()
  })

  it('restores finalization action and shows a Spanish error when finalization fails', async () => {
    api.importaciones.discogsManualBatchFinalize.mockRejectedValue(new Error('El batch ya está finalizado'))
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([{
      type: 'MANUAL', key: 'manual:42', label: 'JPH · 1 copias físicas · En curso',
      customerCode: 'JPH', status: 'OPEN', batchId: 42, copyCount: 1,
    }])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([catalogDisco({ idDisco: 507 })])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByRole('option', { name: 'JPH · 1 copias físicas · En curso' })
    fireEvent.change(screen.getByLabelText('Importación Discogs'), { target: { value: 'manual:42' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Finalizar importación' }))
    const dialog = await screen.findByRole('dialog')
    fireEvent.change(within(dialog).getByLabelText('Porcentaje Sonograma'), { target: { value: '30' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Finalizar importación' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('El batch ya está finalizado')
    expect(within(screen.getByTestId('manual-batch-summary'))
      .getByRole('button', { name: 'Finalizar importación' })).toBeEnabled()
  })

  it('warns when the expected count is unknown and sends an explicit finalization confirmation', async () => {
    api.importaciones.discogsManualSourceReconciliation.mockResolvedValue(matchedReconciliation({
      expectedCopyCount: null,
      difference: null,
      reconciliationStatus: 'EXPECTED_COUNT_UNKNOWN',
    }))
    api.importaciones.discogsManualBatchFinalize.mockResolvedValue({
      batchId: 44,
      status: 'FINALIZED',
      porcentajeSonograma: 25,
    })
    discoService.listarFuentesImportacionDiscogs
      .mockResolvedValueOnce([{
        type: 'MANUAL', key: 'manual:44', label: 'UNKNOWN · 2 copias físicas · En curso',
        customerCode: 'UNKNOWN', status: 'OPEN', batchId: 44, copyCount: 2,
      }])
      .mockResolvedValueOnce([{
        type: 'MANUAL', key: 'manual:44', label: 'UNKNOWN · 2 copias físicas · Finalizada',
        customerCode: 'UNKNOWN', status: 'FINALIZED', batchId: 44, copyCount: 2,
      }])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([catalogDisco({ idDisco: 509 })])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByRole('option', { name: 'UNKNOWN · 2 copias físicas · En curso' })
    fireEvent.change(screen.getByLabelText('Importación Discogs'), { target: { value: 'manual:44' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Finalizar importación' }))

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByLabelText('Resumen de conciliación')).toHaveTextContent('Cantidad esperada: No definida')
    fireEvent.change(within(dialog).getByLabelText('Porcentaje Sonograma'), { target: { value: '25' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Finalizar de todos modos' }))

    await waitFor(() => expect(api.importaciones.discogsManualBatchFinalize)
      .toHaveBeenCalledWith(44, 25, true))
  })

  it('warns about pending operations and finalizes only after explicit confirmation', async () => {
    const pendingError = new Error('La cantidad registrada no coincide con la esperada. Hay 2 importaciones pendientes.')
    pendingError.code = 'MANUAL_DISCOGS_RECONCILIATION_CONFIRMATION_REQUIRED'
    pendingError.data = {
      code: pendingError.code,
      warnings: ['Hay 2 importaciones pendientes.'],
      reconciliation: matchedReconciliation({
        normalizedSourceCustomerCode: 'TESTSOURCE',
        expectedCopyCount: 3,
        provablePhysicalCopyCount: 1,
        availableCopyCount: 1,
        distinctReleaseCount: 1,
        difference: -2,
        pendingOperationCount: 2,
        reconciliationStatus: 'IN_PROGRESS',
      }),
    }
    api.importaciones.discogsManualBatchFinalize
      .mockRejectedValueOnce(pendingError)
      .mockResolvedValueOnce({ batchId: 43, status: 'FINALIZED', porcentajeSonograma: 30 })
    discoService.listarFuentesImportacionDiscogs
      .mockResolvedValueOnce([{
        type: 'MANUAL', key: 'manual:43', label: 'TESTSOURCE · 1 copias físicas · En curso',
        customerCode: 'TESTSOURCE', status: 'OPEN', batchId: 43, copyCount: 1,
      }])
      .mockResolvedValueOnce([{
        type: 'MANUAL', key: 'manual:43', label: 'TESTSOURCE · 1 copias físicas · Finalizada',
        customerCode: 'TESTSOURCE', status: 'FINALIZED', batchId: 43, copyCount: 1,
      }])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([catalogDisco({ idDisco: 508 })])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByRole('option', { name: 'TESTSOURCE · 1 copias físicas · En curso' })
    fireEvent.change(screen.getByLabelText('Importación Discogs'), { target: { value: 'manual:43' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Finalizar importación' }))
    const dialog = await screen.findByRole('dialog')
    fireEvent.change(within(dialog).getByLabelText('Porcentaje Sonograma'), { target: { value: '30' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Finalizar importación' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Hay 2 importaciones pendientes')
    expect(within(dialog).getByLabelText('Resumen de conciliación')).toHaveTextContent('Faltan demostrar 2 copias')
    expect(within(dialog).getByRole('button', { name: 'Finalizar de todos modos' })).toBeEnabled()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Finalizar de todos modos' }))

    await waitFor(() => expect(api.importaciones.discogsManualBatchFinalize).toHaveBeenNthCalledWith(2, 43, 30, true))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('shows truthful export progress, prevents duplicate clicks, and keeps re-download available', async () => {
    let resolveExport
    api.importaciones.discogsManualSourceExcel.mockImplementation(() => new Promise(resolve => {
      resolveExport = resolve
    }))
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([{
      type: 'MANUAL', key: 'manual:customer:SV3', label: 'SV3 · 4 copias físicas · Finalizada',
      customerCode: 'SV3', status: 'FINALIZED', batchId: 21, copyCount: 4,
    }])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([catalogDisco({ idDisco: 503 })])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByRole('option', { name: 'SV3 · 4 copias físicas · Finalizada' })
    fireEvent.change(screen.getByLabelText('Importación Discogs'), { target: { value: 'manual:customer:SV3' } })

    const exportButton = await screen.findByRole('button', { name: 'Exportar Excel' })
    fireEvent.click(exportButton)
    fireEvent.click(exportButton)
    expect(api.importaciones.discogsManualSourceExcel).toHaveBeenCalledTimes(1)
    expect(api.importaciones.discogsManualSourceExcel).toHaveBeenCalledWith('SV3')
    expect(api.importaciones.discogsManualBatchExcel).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Generando Excel…' })).toBeDisabled()
    expect(screen.getByTestId('manual-batch-export-progress')).toHaveTextContent('Generando archivo')

    resolveExport({ blob: new Blob(['xlsx']), filename: 'SV3_2026-09-30.xlsx' })
    await waitFor(() => expect(screen.getByRole('button', { name: 'Descargar Excel' })).toBeEnabled())
    expect(downloadBlob).toHaveBeenCalledWith(expect.any(Blob), 'SV3_2026-09-30.xlsx', undefined)
  })

  it('restores the manual export button and shows a Spanish error when export fails', async () => {
    api.importaciones.discogsManualSourceExcel.mockRejectedValue(new Error('Fuente sin copias'))
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([{
      type: 'MANUAL', key: 'manual:22', label: 'PIN · 0 copias físicas · En curso',
      customerCode: 'PIN', status: 'OPEN', batchId: 22, copyCount: 0,
    }])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByRole('option', { name: 'PIN · 0 copias físicas · En curso' })
    fireEvent.change(screen.getByLabelText('Importación Discogs'), { target: { value: 'manual:22' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Exportar Excel' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Fuente sin copias')
    expect(screen.getByRole('button', { name: 'Exportar Excel' })).toBeEnabled()
  })

  it('updates logical export scope when the selected source changes', async () => {
    api.importaciones.discogsManualSourceExcel.mockResolvedValue({
      blob: new Blob(['xlsx']), filename: 'source.xlsx',
    })
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([
      { type: 'MANUAL', key: 'manual:customer:SV3', customerCode: 'sv3', status: 'FINALIZED', batchId: 8, copyCount: 4 },
      { type: 'MANUAL', key: 'manual:customer:LO', customerCode: ' LO ', status: 'OPEN', batchId: 9, copyCount: 2 },
    ])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([catalogDisco({ idDisco: 510 })])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    const selector = screen.getByLabelText('Importación Discogs')
    await screen.findByRole('option', { name: 'SV3 · 4 copias físicas · Finalizada' })

    fireEvent.change(selector, { target: { value: 'manual:customer:SV3' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Exportar Excel' }))
    await waitFor(() => expect(api.importaciones.discogsManualSourceExcel).toHaveBeenCalledWith('SV3'))

    fireEvent.change(selector, { target: { value: 'manual:customer:LO' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Exportar Excel' }))
    await waitFor(() => expect(api.importaciones.discogsManualSourceExcel).toHaveBeenCalledWith('LO'))
    expect(api.importaciones.discogsManualBatchExcel).not.toHaveBeenCalled()
  })

  it('does not show a manual batch summary when all imports are selected', async () => {
    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)

    await screen.findByText('Deletion Artist')
    expect(screen.queryByTestId('manual-batch-summary')).not.toBeInTheDocument()
  })

  it('keeps one product row and switches independent source, price, condition, and state by copy', async () => {
    api.discos.copias.mockResolvedValue([
      copyDetail({ id: 101, copyNumber: 1, sourceCustomerCode: 'LO', normalizedSourceCustomerCode: 'LO', precioVenta: 1100, condicionFisica: 'NM' }),
      copyDetail({ id: 102, copyNumber: 2, sourceCustomerCode: 'SV3', normalizedSourceCustomerCode: 'SV3', precioVenta: 925, condicionFisica: 'VG', estado: 'VENDIDO' }),
    ])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    const artist = await screen.findByText('Deletion Artist')
    expect(within(screen.getByRole('table')).getAllByText('Deletion Artist')).toHaveLength(1)
    fireEvent.click(artist.closest('tr'))

    const section = await screen.findByTestId('physical-copy-section')
    await waitFor(() => expect(within(section).getByText('1 disponible · 2 copias físicas')).toBeInTheDocument())
    expect(within(section).getByText('LO')).toBeInTheDocument()
    expect(within(section).getByText('UYU $1.100')).toBeInTheDocument()
    expect(within(section).getByText('NM')).toBeInTheDocument()

    fireEvent.click(within(section).getByRole('button', { name: 'Copia 2' }))
    expect(within(section).getByText('SV3')).toBeInTheDocument()
    expect(within(section).getByText('UYU $925')).toBeInTheDocument()
    expect(within(section).getByText('VG')).toBeInTheDocument()
    expect(within(section).getAllByText('Vendida').length).toBeGreaterThan(0)
    expect(api.discos.copias).toHaveBeenCalledWith(42)
  })

  it('defaults to the active LO copy, labels the match, and shows retained removal details', async () => {
    const filtered = catalogDisco({ idDisco: 801, artista: 'Shared LO and SV3 release' })
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([{
      type: 'MANUAL', key: 'manual:customer:LO', customerCode: 'LO', status: 'OPEN', batchId: 15, copyCount: 1,
    }])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([filtered])
    api.discos.copias.mockResolvedValue([
      copyDetail({ id: 201, productId: 801, copyNumber: 1, sourceCustomerCode: 'SV3', normalizedSourceCustomerCode: 'SV3' }),
      copyDetail({
        id: 202, productId: 801, copyNumber: 2, sourceCustomerCode: 'LO', normalizedSourceCustomerCode: 'LO',
        estado: 'REMOVED', dispositionReason: 'DAMAGED', dispositionNote: 'Rayón profundo', disposedAt: '2026-09-20T14:30:00',
      }),
    ])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByRole('option', { name: 'LO · 1 copias físicas · En curso' })
    fireEvent.change(screen.getByLabelText('Importación Discogs'), { target: { value: 'manual:customer:LO' } })
    const artist = await screen.findByText('Shared LO and SV3 release')
    fireEvent.click(artist.closest('tr'))

    const section = await screen.findByTestId('physical-copy-section')
    expect(await within(section).findByText('Coincide con filtro LO')).toBeInTheDocument()
    expect(within(section).getByText('Se muestran todas las copias; ✓ indica LO.')).toBeInTheDocument()
    expect(within(section).getByRole('button', { name: 'Copia 1' })).toBeInTheDocument()
    expect(within(section).getByRole('button', { name: 'Copia 2' })).toBeInTheDocument()
    expect(within(section).getByRole('heading', { name: 'Copia 2' })).toBeInTheDocument()
    expect(within(section).getAllByText('Retirada').length).toBeGreaterThan(0)
    expect(within(section).getByText('Dañada')).toBeInTheDocument()
    expect(within(section).getByText('Rayón profundo')).toBeInTheDocument()
  })

  it('prefers the first available copy and uses honest null fallbacks without product substitutions', async () => {
    discoService.getAll.mockResolvedValue([catalogDisco({
      idDisco: 802,
      artista: 'Fallback release',
      precioVenta: 9999,
      condicionFisica: 'MINT PRODUCT',
    })])
    api.discos.copias.mockResolvedValue([
      copyDetail({ id: 301, productId: 802, copyNumber: 1, estado: 'VENDIDO', sourceCustomerCode: 'SV3', normalizedSourceCustomerCode: 'SV3' }),
      copyDetail({
        id: 302, productId: 802, copyNumber: 2, precioVenta: null, condicionFisica: null,
        sourceCustomerCode: null, normalizedSourceCustomerCode: null, manualBatchId: null,
      }),
    ])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    const artist = await screen.findByText('Fallback release')
    fireEvent.click(artist.closest('tr'))

    const section = await screen.findByTestId('physical-copy-section')
    expect(await within(section).findByRole('heading', { name: 'Copia 2' })).toBeInTheDocument()
    expect(within(section).getByText('Sin procedencia')).toBeInTheDocument()
    expect(within(section).getByText('Sin precio específico')).toBeInTheDocument()
    expect(within(section).getByText('Sin condición registrada')).toBeInTheDocument()
    expect(within(section).queryByText('UYU $9.999')).not.toBeInTheDocument()
    expect(within(section).queryByText('MINT PRODUCT')).not.toBeInTheDocument()
  })

  it('resolves the verified DiSKOP and Italoboyz copies independently and opens the selected exact QR', async () => {
    const diskop = catalogDisco({
      idDisco: 1880,
      artista: 'DiSKOP',
      album: 'High Hill / The Spirit',
      codigoInterno: 'D-2017-10020965',
      cantidadCopias: 2,
      totalCopias: 2,
      condicion: 'USADO',
      condicionFisica: 'NM',
      precioVenta: 790,
    })
    const italoboyz = catalogDisco({
      idDisco: 1911,
      artista: 'Italoboyz',
      album: 'Episode #11',
      codigoInterno: 'I-2018-12490535',
      cantidadCopias: 2,
      totalCopias: 2,
      condicion: 'USADO',
      condicionFisica: 'VG+',
      precioVenta: 750,
    })
    const copiesByProduct = {
      1880: [
        copyDetail({ id: 28332, productId: 1880, copyNumber: 1, codigoQr: '323c2615-428f-43e1-a33b-9b99b041cfc1', precioVenta: 800, condicionFisica: 'NM' }),
        copyDetail({ id: 28333, productId: 1880, copyNumber: 2, codigoQr: '45f4b046-676b-4af6-9ed9-ab06011dded0', precioVenta: 790, condicionFisica: 'NM' }),
      ],
      1911: [
        copyDetail({ id: 28390, productId: 1911, copyNumber: 1, codigoQr: 'd0e63626-4277-4b08-bf70-3f6186804826', precioVenta: 800, condicionFisica: 'VG+' }),
        copyDetail({ id: 28413, productId: 1911, copyNumber: 2, codigoQr: 'ade8eac5-fea5-4149-980c-f2a46ed89552', precioVenta: 750, condicionFisica: 'VG+' }),
      ],
    }
    discoService.getAll.mockResolvedValue([diskop, italoboyz])
    api.discos.copias.mockImplementation(id => Promise.resolve(copiesByProduct[id]))
    api.discos.porId.mockImplementation(id => {
      const product = id === 1880 ? diskop : italoboyz
      return Promise.resolve({ ...product, qrCopies: copiesByProduct[id] })
    })
    api.qr.urlDescargaCopia.mockImplementation((id, copyNumber) => `/api/qr/${id}/${copyNumber}`)

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)

    const diskopRow = (await screen.findByText('DiSKOP')).closest('tr')
    fireEvent.click(diskopRow)
    let section = await screen.findByTestId('physical-copy-section')
    expect(await within(section).findByRole('heading', { name: 'Copia 1' })).toBeInTheDocument()
    expect(within(section).getAllByText('323c2615-428f-43e1-a33b-9b99b041cfc1').length).toBeGreaterThan(0)
    expect(within(section).getByText('UYU $800')).toBeInTheDocument()
    expect(within(section).getByText('Procedencia')).toBeInTheDocument()
    expect(within(section).getByText('LO')).toBeInTheDocument()

    fireEvent.click(within(section).getByRole('button', { name: 'Copia 2' }))
    expect(within(section).getByRole('heading', { name: 'Copia 2' })).toBeInTheDocument()
    expect(within(section).getAllByText('45f4b046-676b-4af6-9ed9-ab06011dded0').length).toBeGreaterThan(0)
    expect(within(section).getByText('UYU $790')).toBeInTheDocument()
    expect(within(diskopRow).getByText('UYU $790–$800')).toBeInTheDocument()

    fireEvent.click(within(section).getByRole('button', { name: 'Ver QR de Copia 2' }))
    expect(await screen.findByText('Mostrando copia 2 de 2')).toBeInTheDocument()
    expect(screen.getAllByText('45f4b046-676b-4af6-9ed9-ab06011dded0').length).toBeGreaterThan(0)
    expect(api.discos.porId).toHaveBeenCalledWith(1880)
    expect(api.qr.urlDescargaCopia).toHaveBeenCalledWith(1880, 2)
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar' }))

    const italoboyzRow = screen.getByText('Italoboyz').closest('tr')
    fireEvent.click(italoboyzRow)
    section = await screen.findByTestId('physical-copy-section')
    expect((await within(section).findAllByText('d0e63626-4277-4b08-bf70-3f6186804826')).length).toBeGreaterThan(0)
    expect(within(section).getByText('UYU $800')).toBeInTheDocument()
    fireEvent.click(within(section).getByRole('button', { name: 'Copia 2' }))
    expect(within(section).getAllByText('ade8eac5-fea5-4149-980c-f2a46ed89552').length).toBeGreaterThan(0)
    expect(within(section).getByText('UYU $750')).toBeInTheDocument()
    expect(within(italoboyzRow).getByText('UYU $750–$800')).toBeInTheDocument()

    expect(screen.getAllByText('2 copias').length).toBeGreaterThan(0)
    expect(discoService.actualizarCopias).not.toHaveBeenCalled()
    expect(api.discos.eliminarCopia).not.toHaveBeenCalled()
  })

  it('uses a compact select for five or more retained copies', async () => {
    api.discos.copias.mockResolvedValue(Array.from({ length: 6 }, (_, index) => copyDetail({
      id: 400 + index,
      copyNumber: index + 1,
      sourceCustomerCode: index % 2 ? 'SV3' : 'LO',
      normalizedSourceCustomerCode: index % 2 ? 'SV3' : 'LO',
    })))

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    const artist = await screen.findByText('Deletion Artist')
    fireEvent.click(artist.closest('tr'))

    const selector = await screen.findByLabelText('Copia física')
    expect(selector).toBeInTheDocument()
    expect(within(selector).getAllByRole('option')).toHaveLength(6)
    expect(screen.queryByRole('button', { name: /^Copia 5/ })).not.toBeInTheDocument()
  })

  it('resets selection when the product changes and reuses cached details when returning', async () => {
    const first = catalogDisco({ idDisco: 901, artista: 'First product' })
    const second = catalogDisco({ idDisco: 902, artista: 'Second product' })
    discoService.getAll.mockResolvedValue([first, second])
    api.discos.copias.mockImplementation(id => Promise.resolve(id === 901
      ? [copyDetail({ id: 501, productId: 901, copyNumber: 1 }), copyDetail({ id: 502, productId: 901, copyNumber: 2, sourceCustomerCode: 'SV3', normalizedSourceCustomerCode: 'SV3' })]
      : [copyDetail({ id: 601, productId: 902, copyNumber: 1, sourceCustomerCode: 'P', normalizedSourceCustomerCode: 'P' })]))

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    fireEvent.click((await screen.findByText('First product')).closest('tr'))
    let section = await screen.findByTestId('physical-copy-section')
    fireEvent.click(await within(section).findByRole('button', { name: 'Copia 2' }))
    expect(within(section).getByRole('heading', { name: 'Copia 2' })).toBeInTheDocument()

    fireEvent.click(screen.getByText('Second product').closest('tr'))
    section = await screen.findByTestId('physical-copy-section')
    expect(await within(section).findByRole('heading', { name: 'Copia 1' })).toBeInTheDocument()
    expect(within(section).getByText('P')).toBeInTheDocument()

    fireEvent.click(screen.getByText('First product').closest('tr'))
    await waitFor(() => expect(within(screen.getByTestId('physical-copy-section')).getByRole('heading', { name: 'Copia 1' })).toBeInTheDocument())
    expect(api.discos.copias.mock.calls.filter(([id]) => id === 901)).toHaveLength(1)
  })

  it('uses the same physical-copy semantics in the mobile slide-over', async () => {
    window.matchMedia = vi.fn().mockReturnValue({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })
    api.discos.copias.mockResolvedValue([
      copyDetail({ id: 701, copyNumber: 1, sourceCustomerCode: 'LO', normalizedSourceCustomerCode: 'LO' }),
      copyDetail({ id: 702, copyNumber: 2, sourceCustomerCode: 'SV3', normalizedSourceCustomerCode: 'SV3', estado: 'VENDIDO' }),
    ])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    fireEvent.click((await screen.findByText('Deletion Artist')).closest('tr'))

    const section = await screen.findByTestId('physical-copy-section')
    expect(await within(section).findByText('LO')).toBeInTheDocument()
    fireEvent.click(within(section).getByRole('button', { name: 'Copia 2' }))
    expect(within(section).getByText('SV3')).toBeInTheDocument()
    expect(within(section).getAllByText('Vendida').length).toBeGreaterThan(0)
  })

  it('keeps shared product data visible through local loading and retryable copy errors', async () => {
    let rejectCopies
    api.discos.copias.mockImplementationOnce(() => new Promise((resolve, reject) => { rejectCopies = reject }))
      .mockResolvedValueOnce([copyDetail({ id: 801 })])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    fireEvent.click((await screen.findByText('Deletion Artist')).closest('tr'))

    expect(await screen.findByText('Cargando copias físicas…')).toBeInTheDocument()
    expect(screen.getAllByText('Deletion Album').length).toBeGreaterThan(0)
    rejectCopies(new Error('No se pudo consultar el inventario'))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('No se pudo consultar el inventario')
    expect(screen.getAllByText('Deletion Album').length).toBeGreaterThan(0)

    fireEvent.click(within(alert).getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByText('1 disponible · 1 copia física')).toBeInTheDocument()
    expect(api.discos.copias).toHaveBeenCalledTimes(2)
  })
})
