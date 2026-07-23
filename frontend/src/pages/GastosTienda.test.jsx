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

const expenses = [
  { idGasto: 1, fecha: '2026-07-10', categoria: 'FIXED_EXPENSES', descripcion: 'Luz', monto: 100 },
  { idGasto: 2, fecha: '2026-07-11', categoria: 'STORE_EXPENSES', descripcion: 'Bolsas', monto: 200 },
  { idGasto: 3, fecha: '2026-07-12', categoria: 'USED_ORDERS', descripcion: 'Compra usados', monto: 300 },
  { idGasto: 4, fecha: '2026-07-13', categoria: 'NEW_ORDERS', descripcion: 'Compra nuevos', monto: 400 },
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

  it('carga la categoría de históricos como Sin categoría y crea con la categoría seleccionada', async () => {
    renderPage()

    await screen.findByText('Histórico')
    expect(screen.getByText('Sin categoría')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('CATEGORÍA'), { target: { value: 'STORE_EXPENSES' } })
    fireEvent.change(screen.getByLabelText('Motivo'), { target: { value: 'Cinta' } })
    fireEvent.change(screen.getByLabelText('Monto'), { target: { value: '50' } })
    fireEvent.click(screen.getByRole('button', { name: 'Agregar gasto' }))

    await waitFor(() => expect(api.gastosTienda.crear).toHaveBeenCalledWith(expect.objectContaining({
      categoria: 'STORE_EXPENSES',
      descripcion: 'Cinta',
      monto: 50,
    })))
    expect(screen.getByLabelText('CATEGORÍA')).toHaveValue('')
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
      ['STORE_EXPENSES', 'Gastos de tienda', 'UYU $200,00'],
      ['USED_ORDERS', 'Pedidos usados', 'UYU $300,00'],
      ['NEW_ORDERS', 'Pedidos nuevos', 'UYU $400,00'],
    ]) {
      fireEvent.change(filter, { target: { value } })
      expect(screen.getByText(description, { selector: 'td' })).toBeInTheDocument()
      expect(screen.queryByText('Histórico')).not.toBeInTheDocument()
      expect(screen.getByText(`TOTAL · ${description.toUpperCase()}`)).toBeInTheDocument()
      expect(screen.getAllByText(total).length).toBeGreaterThanOrEqual(1)
    }

    fireEvent.change(filter, { target: { value: '' } })
    expect(screen.getByText('TOTAL DEL MES')).toBeInTheDocument()
    expect(screen.getByText('Histórico')).toBeInTheDocument()
  })

  it('precarga y actualiza la categoría conservando el filtro activo, también después de eliminar', async () => {
    renderPage()
    await screen.findByText('Luz')
    const filter = screen.getByLabelText('Filtrar por categoría')
    fireEvent.change(filter, { target: { value: 'STORE_EXPENSES' } })
    fireEvent.click(screen.getByRole('button', { name: 'Editar' }))

    expect(screen.getByLabelText('CATEGORÍA')).toHaveValue('STORE_EXPENSES')
    fireEvent.change(screen.getByLabelText('CATEGORÍA'), { target: { value: 'NEW_ORDERS' } })
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
