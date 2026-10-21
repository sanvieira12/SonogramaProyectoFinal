import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PreVentas from './PreVentas'
import { mergePreVentaItem, transactionTotal } from './preVentaTransactionUtils'
import { api } from '../api/sonograma'

vi.mock('../api/sonograma', () => ({
  api: {
    preVentas: { marcarPagada: vi.fn() },
    preVentaTransacciones: {
      listar: vi.fn(), obtener: vi.fn(), crear: vi.fn(), agregarItem: vi.fn(),
      actualizarItem: vi.fn(), eliminarItem: vi.fn(), pagarPendientes: vi.fn(),
    },
    clientes: { todos: vi.fn(), crear: vi.fn() },
    discos: { todos: vi.fn() },
  },
  FINANCIAL_DATA_CHANGED_EVENT: 'sonograma:financial-data-changed',
  resolveApiUrl: vi.fn(value => value || ''),
}))

const pendingItem = {
  idPreVenta: 11, idCliente: 7, clienteNombre: 'Frank', idDisco: null,
  descripcion: 'Sepher', codigoDisco: 'FUT-1', cantidad: 1, precio: 1470,
  fecha: '2026-09-11', estado: 'PENDIENTE', notas: null,
}

const transaction = {
  idPreVentaTransaccion: 105, idCliente: 7, clienteNombre: 'Frank', fecha: '2026-09-11',
  notas: null, items: [pendingItem], cantidadItems: 1, cantidadUnidades: 1,
  total: 1470, pagado: 0, pendiente: 1470, estadoPago: 'PENDIENTE',
}

const clients = [{ idCliente: 7, nombre: 'Frank', apellido: 'Ocean', cedula: '123' }]
const discs = [
  { idDisco: 21, artista: 'Radiohead', album: 'OK Computer', codigoInterno: 'RH-1', precioVenta: 1800 },
  { idDisco: 22, artista: 'Daft Punk', album: 'Discovery', codigoInterno: 'DP-1', precioVenta: 3000 },
]

describe('PreVentas multi-item helpers', () => {
  it('merges the same catalog item only when price and notes match', () => {
    const first = { uid: 'a', idDisco: 21, cantidad: '1', precioUnitario: '1800', notas: '' }
    const matching = { uid: 'b', idDisco: 21, cantidad: '2', precioUnitario: '1800', notas: '' }
    const differentPrice = { uid: 'c', idDisco: 21, cantidad: '1', precioUnitario: '1900', notas: '' }

    const merged = mergePreVentaItem([first], matching)
    expect(merged).toHaveLength(1)
    expect(merged[0].cantidad).toBe('3')
    expect(mergePreVentaItem(merged, differentPrice)).toHaveLength(2)
  })

  it('calculates the authoritative transaction total from line quantities and unit prices', () => {
    expect(transactionTotal([
      { cantidad: '2', precioUnitario: '1800' },
      { cantidad: '1', precioUnitario: '3000' },
    ])).toBe(6600)
  })
})

describe('PreVentas transaction UI', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.preVentaTransacciones.listar.mockResolvedValue([transaction])
    api.clientes.todos.mockResolvedValue(clients)
    api.discos.todos.mockResolvedValue(discs)
  })

  it('renders persisted transactions rather than grouping by customer', async () => {
    api.preVentaTransacciones.listar.mockResolvedValue([
      transaction,
      { ...transaction, idPreVentaTransaccion: 106, fecha: '2026-09-12' },
    ])
    render(<PreVentas />)

    const table = await screen.findByTestId('preventa-transaction-table')
    expect(within(table).getAllByRole('row')).toHaveLength(3)
    expect(within(table).getByText('#105')).toBeInTheDocument()
    expect(within(table).getByText('#106')).toBeInTheDocument()
  })

  it('creates two records through one atomic transaction request', async () => {
    const created = { ...transaction, idPreVentaTransaccion: 107, items: [], cantidadItems: 2, cantidadUnidades: 2, total: 4800, pendiente: 4800 }
    api.preVentaTransacciones.crear.mockResolvedValue(created)
    render(<PreVentas />)

    fireEvent.click(await screen.findByRole('button', { name: /Nueva pre-venta/i }))
    fireEvent.click(screen.getByRole('button', { name: /Frank Ocean/i }))
    fireEvent.click(screen.getByRole('button', { name: /Radiohead — OK Computer/i }))
    fireEvent.click(screen.getByRole('button', { name: /Daft Punk — Discovery/i }))
    fireEvent.click(screen.getByRole('button', { name: 'Guardar pre-venta' }))

    await waitFor(() => expect(api.preVentaTransacciones.crear).toHaveBeenCalledTimes(1))
    const payload = api.preVentaTransacciones.crear.mock.calls[0][0]
    expect(payload.idCliente).toBe(7)
    expect(payload.items).toEqual(expect.arrayContaining([
      expect.objectContaining({ idDisco: 21, cantidad: 1, precio: 1800 }),
      expect.objectContaining({ idDisco: 22, cantidad: 1, precio: 3000 }),
    ]))
    expect(payload.idempotencyKey).toBeTruthy()
  })

  it('confirms and pays the exact remaining balance once', async () => {
    const paid = { ...transaction, pagado: 1470, pendiente: 0, estadoPago: 'PAGADA', items: [{ ...pendingItem, estado: 'PAGADA' }] }
    api.preVentaTransacciones.pagarPendientes.mockResolvedValue(paid)
    render(<PreVentas />)

    const table = await screen.findByTestId('preventa-transaction-table')
    fireEvent.click(within(table).getByRole('button', { name: 'Ver detalle' }))
    fireEvent.click(await screen.findByRole('button', { name: /Pagar saldo pendiente.*1\.470/i }))
    expect(screen.getByText(/cobros individuales por UYU \$1\.470/i)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }))

    await waitFor(() => expect(api.preVentaTransacciones.pagarPendientes).toHaveBeenCalledWith(105))
    expect(api.preVentaTransacciones.pagarPendientes).toHaveBeenCalledTimes(1)
  })

  it('prevents overlapping item additions and reuses the idempotency key after a failed response', async () => {
    let rejectFirst
    api.preVentaTransacciones.agregarItem
      .mockImplementationOnce(() => new Promise((resolve, reject) => { rejectFirst = reject }))
      .mockResolvedValueOnce({ ...transaction, items: [...transaction.items, { ...pendingItem, idPreVenta: 12 }] })
    render(<PreVentas />)

    const table = await screen.findByTestId('preventa-transaction-table')
    fireEvent.click(within(table).getByRole('button', { name: 'Ver detalle' }))
    const catalogButton = await screen.findByRole('button', { name: /Radiohead — OK Computer/i })
    fireEvent.click(catalogButton)
    fireEvent.click(catalogButton)

    expect(api.preVentaTransacciones.agregarItem).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('status')).toHaveTextContent('Agregando ítem')
    const firstKey = api.preVentaTransacciones.agregarItem.mock.calls[0][1].idempotencyKey
    expect(firstKey).toBeTruthy()

    rejectFirst(new Error('Respuesta perdida'))
    expect(await screen.findByText('Respuesta perdida')).toBeInTheDocument()
    expect(screen.getAllByText(/Ítem #/)).toHaveLength(1)
    fireEvent.click(screen.getByRole('button', { name: /Radiohead — OK Computer/i }))

    await waitFor(() => expect(api.preVentaTransacciones.agregarItem).toHaveBeenCalledTimes(2))
    expect(api.preVentaTransacciones.agregarItem.mock.calls[1][1].idempotencyKey).toBe(firstKey)
  })
})
