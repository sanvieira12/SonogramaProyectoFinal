import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import GastosTienda from './GastosTienda'
import { api } from '../api/sonograma'

vi.mock('../api/sonograma', () => ({
  api: {
    gastosTienda: {
      listar: vi.fn(),
      crear: vi.fn(),
      actualizar: vi.fn(),
      eliminar: vi.fn(),
    },
  },
}))

const currentPeriod = new Date().toLocaleDateString('en-CA', { year: 'numeric', month: '2-digit' })

const expenses = [
  { idGasto: 1, fecha: `${currentPeriod}-10`, categoria: 'FIXED_EXPENSES', descripcion: 'Luz', monto: 100 },
  { idGasto: 2, fecha: `${currentPeriod}-11`, categoria: 'STORE_EXPENSES', descripcion: 'Bolsas', monto: 200 },
  { idGasto: 3, fecha: `${currentPeriod}-12`, categoria: 'USED_ORDERS', descripcion: 'Compra usados', monto: 300 },
  { idGasto: 4, fecha: `${currentPeriod}-13`, categoria: 'NEW_ORDERS', descripcion: 'Compra nuevos', monto: 400 },
  { idGasto: 5, fecha: '2025-06-01', categoria: null, descripcion: 'Histórico', monto: 500 },
]

function renderPage() {
  return render(<GastosTienda />)
}

describe('GastosTienda', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.gastosTienda.listar.mockResolvedValue(expenses)
    api.gastosTienda.crear.mockImplementation(async payload => ({ idGasto: 6, ...payload }))
    api.gastosTienda.actualizar.mockImplementation(async (id, payload) => ({ idGasto: id, ...payload }))
    api.gastosTienda.eliminar.mockResolvedValue(null)
  })

  it('muestra solo el mes seleccionado y crea con la categoría seleccionada', async () => {
    renderPage()

    await screen.findByText('Luz')
    expect(screen.queryByText('Histórico')).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Categoría'), { target: { value: 'STORE_EXPENSES' } })
    fireEvent.change(screen.getByLabelText('Motivo'), { target: { value: 'Cinta' } })
    fireEvent.change(screen.getByLabelText('Monto'), { target: { value: '50' } })
    fireEvent.click(screen.getByRole('button', { name: 'Agregar gasto' }))

    await waitFor(() => expect(api.gastosTienda.crear).toHaveBeenCalledWith(expect.objectContaining({
      categoria: 'STORE_EXPENSES',
      descripcion: 'Cinta',
      monto: 50,
    })))
    expect(screen.getByLabelText('Categoría')).toHaveValue('')
  })

  it('valida la categoría antes de enviar', async () => {
    renderPage()
    await screen.findByText('Luz')

    fireEvent.click(screen.getByRole('button', { name: 'Agregar gasto' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Seleccioná una categoría')
    expect(api.gastosTienda.crear).not.toHaveBeenCalled()
  })

  it('filtra cada categoría, actualiza el total mensual y vuelve a mostrar todas', async () => {
    renderPage()
    await screen.findByText('Luz')
    const filter = screen.getByLabelText('Filtrar por categoría')

    for (const [value, description, total] of [
      ['FIXED_EXPENSES', 'Gastos fijos', 'UYU $100,00'],
      ['STORE_EXPENSES', 'Gastos secundarios', 'UYU $200,00'],
      ['USED_ORDERS', 'Pedidos usados', 'UYU $300,00'],
      ['NEW_ORDERS', 'Pedidos nuevos', 'UYU $400,00'],
    ]) {
      fireEvent.change(filter, { target: { value } })
      expect(screen.getByTitle(description)).toBeInTheDocument()
      expect(screen.queryByText('Histórico')).not.toBeInTheDocument()
      expect(screen.getByText('Total gastos')).toBeInTheDocument()
      expect(screen.getAllByText(total).length).toBeGreaterThanOrEqual(1)
    }

    fireEvent.change(filter, { target: { value: '' } })
    expect(screen.getByText('Total gastos')).toBeInTheDocument()
    expect(screen.queryByText('Histórico')).not.toBeInTheDocument()
  })

  it('mantiene cuatro tarjetas de categoría y el total mensual independiente de los filtros', async () => {
    renderPage()
    await screen.findByText('Luz')

    expect(screen.getByText('Gastos')).toBeInTheDocument()
    expect(screen.getAllByText('Gastos fijos').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Gastos secundarios').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Pedidos usados').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Pedidos nuevos').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('UYU $1.000,00').length).toBeGreaterThanOrEqual(1)

    fireEvent.change(screen.getByLabelText('Filtrar por categoría'), { target: { value: 'NEW_ORDERS' } })
    expect(screen.getByText('Compra nuevos')).toBeInTheDocument()
    expect(screen.getByText('UYU $1.000,00')).toBeInTheDocument()
  })

  it('normaliza una categoría legacy en la tabla, el filtro y la edición', async () => {
    api.gastosTienda.listar.mockResolvedValue([
      ...expenses,
      { idGasto: 6, fecha: `${currentPeriod}-14`, categoria: 'Gastos del local', descripcion: 'Legacy', monto: 50 },
    ])
    renderPage()
    await screen.findByText('Legacy')

    expect(screen.getAllByText('Gastos secundarios').length).toBeGreaterThanOrEqual(1)
    fireEvent.change(screen.getByLabelText('Filtrar por categoría'), { target: { value: 'STORE_EXPENSES' } })
    expect(screen.getByText('Legacy')).toBeInTheDocument()
    fireEvent.click(within(screen.getByText('Legacy').closest('tr')).getByRole('button', { name: 'Editar' }))
    expect(screen.getByLabelText('Categoría')).toHaveValue('STORE_EXPENSES')

    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    await waitFor(() => expect(api.gastosTienda.actualizar).toHaveBeenCalledWith(6, expect.objectContaining({ categoria: 'STORE_EXPENSES' })))
  })

  it('precarga y actualiza la categoría conservando el filtro activo, también después de eliminar', async () => {
    renderPage()
    await screen.findByText('Luz')
    const filter = screen.getByLabelText('Filtrar por categoría')
    fireEvent.change(filter, { target: { value: 'STORE_EXPENSES' } })
    fireEvent.click(screen.getByRole('button', { name: 'Editar' }))

    expect(screen.getByLabelText('Categoría')).toHaveValue('STORE_EXPENSES')
    fireEvent.change(screen.getByLabelText('Categoría'), { target: { value: 'NEW_ORDERS' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    await waitFor(() => expect(api.gastosTienda.actualizar).toHaveBeenCalledWith(2, expect.objectContaining({ categoria: 'NEW_ORDERS' })))
    expect(filter).toHaveValue('STORE_EXPENSES')
    expect(screen.queryByText('Bolsas')).not.toBeInTheDocument()

    fireEvent.change(filter, { target: { value: 'NEW_ORDERS' } })
    expect(screen.getByText('Bolsas')).toBeInTheDocument()
    fireEvent.click(within(screen.getByText('Bolsas').closest('tr')).getByRole('button', { name: 'Eliminar' }))
    await waitFor(() => expect(api.gastosTienda.eliminar).toHaveBeenCalledWith(2))
    expect(filter).toHaveValue('NEW_ORDERS')
  })
})
