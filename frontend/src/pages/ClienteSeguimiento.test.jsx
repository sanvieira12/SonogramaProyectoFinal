import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ClienteSeguimiento from './ClienteSeguimiento'
import { api } from '../api/sonograma'

vi.mock('../api/sonograma', () => ({
  api: {
    crm: {
      perfil: vi.fn(),
      intereses: vi.fn(),
      recomendaciones: vi.fn(),
      crearInteres: vi.fn(),
      cambiarEstadoInteres: vi.fn(),
    },
  },
  resolveApiUrl: vi.fn(value => value || ''),
}))

const profile = {
  cliente: { idCliente: 7, nombre: 'Lucía', apellido: 'Pérez', instagramUsuario: '@lucia', telefono: '099', email: 'lucia@example.com' },
  metricas: {
    cantidadCompras: 2, cantidadDiscos: 3, totalGastado: 12000, promedioPorCompra: 6000,
    precioPromedioPorDisco: 4000, precioMedianoPorDisco: 3800, precioMaximoPorDisco: 5200,
    rangoTipicoMinimo: 3500, rangoTipicoMaximo: 4500, primeraCompra: '2024-01-10T12:00:00',
    ultimaCompra: '2026-07-10T12:00:00', frecuenciaPromedioDias: 45, comprasUltimos12Meses: 1,
  },
  perfilHistorico: {
    artistas: [{ valor: 'Aphex Twin', cantidad: 2, porcentaje: 66.67 }],
    generos: [{ valor: 'IDM', cantidad: 2, porcentaje: 66.67 }], estilos: [], sellos: [],
    anios: [], decadas: [], formatos: [], condiciones: [],
  },
  perfilReciente: {
    artistas: [{ valor: 'Autechre', cantidad: 1, porcentaje: 100 }],
    generos: [{ valor: 'Ambient', cantidad: 1, porcentaje: 100 }], estilos: [], sellos: [],
    anios: [], decadas: [], formatos: [], condiciones: [],
  },
  historialCompras: [{ idVenta: 1, idDetalle: 2, idDisco: 3, artista: 'Aphex Twin', album: 'Amber', fechaCompra: '2026-07-10T12:00:00', cantidad: 1, precioUnitarioPagado: 4000 }],
}

const recommendations = [{
  idDisco: 12, artista: 'Autechre', album: 'Tri Repetae', selloDiscografico: 'Warp', anio: 1995,
  formato: 'LP', precio: 4800, cantidadDisponible: 1, nivelAfinidad: 'ALTA',
  razones: ['Warp está entre sus sellos más comprados', 'El precio está dentro de su rango habitual'],
}]

describe('ClienteSeguimiento', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.crm.perfil.mockResolvedValue(profile)
    api.crm.intereses.mockResolvedValue([{ idInteres: 5, tipo: 'SELLO', texto: 'Warp', activo: true }])
    api.crm.recomendaciones.mockResolvedValue(recommendations)
    api.crm.crearInteres.mockResolvedValue({})
    api.crm.cambiarEstadoInteres.mockResolvedValue({})
  })

  function renderPage() {
    return render(
      <MemoryRouter initialEntries={['/clientes/7/seguimiento']}>
        <Routes><Route path="/clientes/:id/seguimiento" element={<ClienteSeguimiento />} /></Routes>
      </MemoryRouter>,
    )
  }

  it('renders metrics, historical/recent taste, purchase history and explained recommendations', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Lucía Pérez' })).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getAllByText('Aphex Twin').length).toBeGreaterThan(0)
    expect(screen.getByText('Afinidad alta')).toBeInTheDocument()
    expect(screen.getByText('• Warp está entre sus sellos más comprados')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Últimos 12 meses' }))
    expect(screen.getAllByText('Autechre').length).toBeGreaterThan(0)
    expect(screen.queryByText('IDM')).not.toBeInTheDocument()
  })

  it('adds and deactivates interests, then refreshes interest-driven recommendations', async () => {
    renderPage()
    await screen.findByText('Warp')

    fireEvent.change(screen.getByLabelText('Tipo de interés'), { target: { value: 'ARTISTA' } })
    fireEvent.change(screen.getByLabelText('Interés del cliente'), { target: { value: 'Drexciya' } })
    fireEvent.click(screen.getByRole('button', { name: 'Agregar' }))

    await waitFor(() => expect(api.crm.crearInteres).toHaveBeenCalledWith('7', { tipo: 'ARTISTA', texto: 'Drexciya' }))
    await waitFor(() => expect(api.crm.recomendaciones).toHaveBeenCalledTimes(2))

    const interestSection = screen.getByRole('heading', { name: 'Intereses expresados' }).closest('section')
    fireEvent.click(within(interestSection).getByRole('button', { name: 'Desactivar' }))
    await waitFor(() => expect(api.crm.cambiarEstadoInteres).toHaveBeenCalledWith('7', 5, false))
  })
})
