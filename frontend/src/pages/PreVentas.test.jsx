import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PreVentas from './PreVentas'
import { calculatePreVentaGroupSummary, groupPreVentasByClient } from './preVentaGrouping'
import { api } from '../api/sonograma'

vi.mock('../api/sonograma', () => ({
  api: {
    preVentas: {
      listar: vi.fn(),
      crear: vi.fn(),
      marcarPagada: vi.fn(),
      eliminar: vi.fn(),
    },
    clientes: {
      todos: vi.fn(),
      crear: vi.fn(),
    },
    discos: {
      todos: vi.fn(),
    },
  },
  FINANCIAL_DATA_CHANGED_EVENT: 'sonograma:financial-data-changed',
  resolveApiUrl: vi.fn(value => value || ''),
}))

const frankPending = {
  idPreVenta: 11,
  idCliente: 7,
  clienteNombre: 'Frank',
  idDisco: null,
  descripcion: 'sepher',
  codigoDisco: 'FUT-1',
  cantidad: 1,
  precio: 1470,
  fecha: '2026-09-11',
  estado: 'PENDIENTE',
  notas: null,
}

const frankPaid = {
  idPreVenta: 12,
  idCliente: 7,
  clienteNombre: 'Frank',
  idDisco: null,
  descripcion: 'Eversines',
  codigoDisco: null,
  cantidad: 1,
  precio: 1390,
  fecha: '2026-09-10',
  estado: 'PAGADA',
  fechaPago: '2026-09-10T12:30:00',
  notas: null,
}

function setupApi(records = [frankPending, frankPaid]) {
  api.preVentas.listar.mockResolvedValue(records)
  api.clientes.todos.mockResolvedValue([])
  api.discos.todos.mockResolvedValue([])
}

describe('PreVentas client grouping helpers', () => {
  it('groups by idCliente and keeps same-name clients separate', () => {
    const records = [
      frankPending,
      { ...frankPaid, idPreVenta: 20, idCliente: 19 },
      { ...frankPaid, idPreVenta: 21, idCliente: 7, clienteNombre: 'Frank' },
    ]

    const groups = groupPreVentasByClient(records)

    expect(groups).toHaveLength(2)
    expect(groups.find(group => group.idCliente === 7).preVentas.map(item => item.idPreVenta)).toEqual([11, 21])
    expect(groups.find(group => group.idCliente === 19).preVentas).toHaveLength(1)
  })

  it('sums record totals directly and quantity separately', () => {
    expect(calculatePreVentaGroupSummary([
      { ...frankPending, cantidad: 2, precio: 1470 },
      { ...frankPaid, cantidad: 3, precio: 1390 },
    ])).toEqual({
      preVentaCount: 2,
      totalQuantity: 5,
      totalAmount: 2860,
      pendingCount: 1,
      paidCount: 1,
      pendingAmount: 1470,
      paidAmount: 1390,
    })
  })
})

describe('PreVentas client grouping UI', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setupApi()
  })

  it('renders one top-level group, opens the detail panel, and keeps both records', async () => {
    render(<PreVentas />)

    const table = await screen.findByTestId('preventa-client-table')
    expect(within(table).getAllByRole('row')).toHaveLength(2)
    expect(within(table).getByText('Frank')).toBeInTheDocument()
    expect(within(table).getAllByText('2', { exact: true }).length).toBeGreaterThan(0)
    expect(within(table).getByText('1 pendiente · 1 pagada')).toBeInTheDocument()

    fireEvent.click(within(table).getByRole('button', { name: 'Ver detalle de Frank' }))

    const panel = await screen.findByRole('dialog', { name: 'Frank' })
    expect(within(panel).getByText('sepher')).toBeInTheDocument()
    expect(within(panel).getByText('Eversines')).toBeInTheDocument()
    expect(within(panel).getByText('Pre-ventas individuales')).toBeInTheDocument()
    expect(within(panel).getByText('UYU $2.860')).toBeInTheDocument()
  })

  it('keeps the creation flow available and clarifies the price label', async () => {
    render(<PreVentas />)

    expect(await screen.findByText('Nueva preventa')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Nueva preventa/i }))
    fireEvent.click(screen.getByRole('button', { name: 'Agregar disco fuera de catálogo' }))

    expect(screen.getAllByText('Precio por unidad').length).toBeGreaterThan(0)
    expect(screen.getByText('Total preventa')).toBeInTheDocument()
  })

  it('marks only one record paid and dispatches the financial refresh event', async () => {
    const updated = { ...frankPending, estado: 'PAGADA', idVentaPago: 101, fechaPago: '2026-09-11T13:00:00' }
    api.preVentas.marcarPagada.mockResolvedValue(updated)
    const dispatchSpy = vi.spyOn(window, 'dispatchEvent')
    render(<PreVentas />)

    const table = await screen.findByTestId('preventa-client-table')
    fireEvent.click(within(table).getByRole('button', { name: 'Ver detalle de Frank' }))
    const panel = await screen.findByRole('dialog', { name: 'Frank' })
    fireEvent.click(within(panel).getByRole('button', { name: 'Marcar pago' }))

    await waitFor(() => expect(api.preVentas.marcarPagada).toHaveBeenCalledWith(11))
    await waitFor(() => expect(within(panel).getByText('2 pagadas')).toBeInTheDocument())
    expect(within(panel).getAllByText('Pagada')).toHaveLength(2)
    expect(dispatchSpy).toHaveBeenCalledWith(expect.objectContaining({ type: 'sonograma:financial-data-changed' }))
    dispatchSpy.mockRestore()
  })

  it('deletes one record, keeps the panel open with siblings, and closes after the last record', async () => {
    api.preVentas.eliminar.mockResolvedValue(undefined)
    setupApi([frankPending, { ...frankPaid, estado: 'PENDIENTE', fechaPago: null }])
    render(<PreVentas />)

    const table = await screen.findByTestId('preventa-client-table')
    fireEvent.click(within(table).getByRole('button', { name: 'Ver detalle de Frank' }))
    let panel = await screen.findByRole('dialog', { name: 'Frank' })
    fireEvent.click(within(panel).getAllByRole('button', { name: 'Eliminar' })[0])
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }))

    await waitFor(() => expect(api.preVentas.eliminar).toHaveBeenCalledWith(11))
    panel = await screen.findByRole('dialog', { name: 'Frank' })
    expect(within(panel).queryByText('sepher')).not.toBeInTheDocument()
    expect(within(panel).getByText('Eversines')).toBeInTheDocument()

    fireEvent.click(within(panel).getByRole('button', { name: 'Eliminar' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }))

    await waitFor(() => expect(api.preVentas.eliminar).toHaveBeenCalledWith(12))
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Frank' })).not.toBeInTheDocument())
  })
})
