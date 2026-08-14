import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import DiscosCatalogo from './DiscosCatalogo'
import { discoService } from '../services/discoService'
import { api } from '../api/sonograma'

vi.mock('../services/discoService', () => ({
  discoService: {
    getAll: vi.fn(),
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
  condicion: 'NUEVO',
  cantidadCopias: 1,
  totalCopias: 1,
  qrCopies: [],
  audioPreviews: [],
  fechaIngreso: '2026-08-03T10:00:00',
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
})
