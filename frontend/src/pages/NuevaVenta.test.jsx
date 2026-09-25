import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import NuevaVenta from './NuevaVenta'
import { api } from '../api/sonograma'

const navigate = vi.fn()

vi.mock('react-router-dom', () => ({
  useNavigate: () => navigate,
  useSearchParams: () => [new URLSearchParams()],
}))

vi.mock('../components/QRScanner', () => ({ default: () => null }))
vi.mock('../components/DacBranchSelect', () => ({ default: () => null }))
vi.mock('../api/sonograma', () => ({
  api: {
    envios: { departamentosDac: vi.fn(), sucursalesDac: vi.fn(), cotizar: vi.fn() },
    discos: { disponibles: vi.fn(), buscar: vi.fn(), porId: vi.fn() },
    clientes: { buscar: vi.fn(), direcciones: vi.fn(), crear: vi.fn() },
    ventas: { registrar: vi.fn() },
  },
  resolveApiUrl: vi.fn(value => value || ''),
}))

describe('NuevaVenta manual items', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.envios.departamentosDac.mockResolvedValue([])
    api.envios.sucursalesDac.mockResolvedValue([])
    api.envios.cotizar.mockResolvedValue({ costo: 0 })
    api.discos.disponibles.mockResolvedValue([])
    api.clientes.direcciones.mockResolvedValue([])
    api.clientes.buscar.mockResolvedValue([{ idCliente: 7, nombre: 'Ana', apellido: 'Pérez', activo: true }])
    api.ventas.registrar.mockResolvedValue({ idVenta: 1 })
  })

  it('requires and submits the explicit Nuevo/Usado classification', async () => {
    render(<NuevaVenta />)
    fireEvent.click(screen.getByRole('button', { name: 'Agregar disco fuera de catálogo' }))
    fireEvent.change(screen.getByPlaceholderText('Descripción'), { target: { value: 'Vinilo manual' } })
    fireEvent.change(screen.getByPlaceholderText('Precio unitario'), { target: { value: '900' } })
    fireEvent.click(screen.getByRole('button', { name: 'Agregar ítem' }))
    expect(await screen.findByText('Seleccioná si el ítem manual es nuevo o usado')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Tipo de ítem manual'), { target: { value: 'USADO' } })
    fireEvent.click(screen.getByRole('button', { name: 'Agregar ítem' }))
    expect(screen.getByText('Usado')).toBeInTheDocument()

    const clientInput = screen.getByPlaceholderText('Buscar por nombre, cédula, Instagram, teléfono o dirección…')
    fireEvent.change(clientInput, { target: { value: 'Ana' } })
    await waitFor(() => expect(screen.getByRole('button', { name: /Ana Pérez/ })).toBeInTheDocument())
    fireEvent.click(screen.getByRole('button', { name: /Ana Pérez/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar venta' }))

    await waitFor(() => expect(api.ventas.registrar).toHaveBeenCalledWith(expect.objectContaining({
      detalles: [expect.objectContaining({ manualItem: true, clasificacionItem: 'USADO', descripcion: 'Vinilo manual' })],
    })))
  })
})
