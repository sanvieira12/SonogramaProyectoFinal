import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import DiscosCatalogo from './DiscosCatalogo'
import { discoService } from '../services/discoService'
import { api } from '../api/sonograma'

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

  it('filters the catalogue by a logical Discogs source without exposing job details', async () => {
    const reused = catalogDisco({ idDisco: 806, artista: 'Producto reutilizado' })
    const newlyCreated = catalogDisco({ idDisco: 1450, artista: 'Producto nuevo' })
    discoService.listarFuentesImportacionDiscogs.mockResolvedValue([
      { key: 'pin', label: 'Discos PIN', productos: 238 },
    ])
    discoService.getPorFuenteImportacionDiscogs.mockResolvedValue([reused, newlyCreated])

    render(<MemoryRouter><DiscosCatalogo /></MemoryRouter>)
    await screen.findByRole('option', { name: /Discos PIN.*238 productos/i })
    expect(screen.queryByRole('option', { name: /Job\s*\d+/i })).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Importación Discogs'), { target: { value: 'pin' } })

    await waitFor(() => expect(discoService.getPorFuenteImportacionDiscogs).toHaveBeenCalledWith('pin'))
    expect(await screen.findByText('Producto reutilizado')).toBeInTheDocument()
    expect(screen.getByText('Producto nuevo')).toBeInTheDocument()
  })
})
