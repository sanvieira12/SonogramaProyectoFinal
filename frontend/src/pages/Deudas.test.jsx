import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Deudas from './Deudas'
import { api } from '../api/sonograma'

vi.mock('../api/sonograma', () => ({
  api: {
    deudas: {
      listar: vi.fn(),
      resumen: vi.fn(),
      actualizar: vi.fn(),
      crear: vi.fn(),
      registrarPago: vi.fn(),
      eliminar: vi.fn(),
    },
    clientes: { todos: vi.fn() },
  },
  FINANCIAL_DATA_CHANGED_EVENT: 'sonograma:financial-data-changed',
  resolveApiUrl: vi.fn(value => value || ''),
}))

const debt = {
  idDeuda: 7,
  idCliente: 3,
  nombreCliente: 'Ana Pérez',
  montoTotal: 2000,
  montoPagado: 500,
  montoPendiente: 1500,
  estadoPago: 'PARCIAL',
  fechaDeuda: '2026-07-28',
  cantidadMovimientos: 1,
  movimientos: [{
    idDeuda: 7,
    idCliente: 3,
    nombreCliente: 'Ana Pérez',
    montoTotal: 2000,
    montoPagado: 500,
    montoPendiente: 1500,
    estadoPago: 'PARCIAL',
    fechaDeuda: '2026-07-28',
    detalles: [{ idDetalle: 10, idDisco: 4, artista: 'Artista', album: 'Álbum', cantidad: 1 }],
    pagos: [],
  }],
}

describe('Deudas deletion flow', () => {
  let currentDebts

  beforeEach(() => {
    vi.clearAllMocks()
    currentDebts = [debt]
    api.deudas.listar.mockImplementation(() => Promise.resolve(currentDebts))
    api.deudas.resumen.mockResolvedValue({ totalPendiente: 1500, cantDeudores: 1, cantDeudas: 1, mayorDeuda: 1500 })
    api.clientes.todos.mockResolvedValue([{ idCliente: 3, nombre: 'Ana', apellido: 'Pérez' }])
    api.deudas.eliminar.mockImplementation(async () => { currentDebts = [] })
  })

  it('confirma, elimina la deuda, refresca la lista y muestra la notificación', async () => {
    render(<Deudas />)

    fireEvent.click(await screen.findByRole('button', { name: 'Ver' }))
    fireEvent.click(screen.getByRole('button', { name: 'Eliminar' }))

    expect(screen.getByRole('heading', { name: 'Eliminar deuda' })).toBeInTheDocument()
    expect(screen.getByText(/los discos asociados volverán a estar disponibles en stock/i)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(api.deudas.eliminar).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: 'Eliminar' }))
    fireEvent.click(screen.getByRole('button', { name: 'Eliminar deuda' }))

    await waitFor(() => expect(api.deudas.eliminar).toHaveBeenCalledWith(7))
    await waitFor(() => expect(screen.queryByText('Ana Pérez')).not.toBeInTheDocument())
    expect(screen.getByRole('status')).toHaveTextContent('Deuda eliminada. Los discos asociados volvieron al stock.')
  })

  it('mantiene visible la deuda si el backend rechaza la operación', async () => {
    api.deudas.eliminar.mockRejectedValue(new Error('No se pudo eliminar la deuda'))
    render(<Deudas />)

    fireEvent.click(await screen.findByRole('button', { name: 'Ver' }))
    fireEvent.click(screen.getByRole('button', { name: 'Eliminar' }))
    fireEvent.click(screen.getByRole('button', { name: 'Eliminar deuda' }))

    await waitFor(() => expect(screen.getAllByRole('alert')[0]).toHaveTextContent('No se pudo eliminar la deuda'))
    expect(screen.getAllByText('Ana Pérez').length).toBeGreaterThan(0)
  })
})
