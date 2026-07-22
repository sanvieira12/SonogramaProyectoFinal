import { fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LibroVentas from './LibroVentas'
import { api } from '../api/sonograma'

vi.mock('../api/sonograma', () => ({
  api: {
    libro: {
      listar: vi.fn(),
      exportarUrl: vi.fn(),
    },
    ventas: {
      resumenMensual: vi.fn(),
      resumenMensualExportarUrl: vi.fn(),
    },
    discos: {
      porId: vi.fn(),
    },
  },
  FINANCIAL_DATA_CHANGED_EVENT: 'sonograma:financial-data-changed',
  resolveApiUrl: vi.fn(value => value || ''),
}))

const movements = [
  {
    idVenta: 1,
    tipoMovimiento: 'VENTA',
    descripcionMovimiento: 'Venta',
    clienteNombreSnapshot: 'Ana Pérez',
    fechaVenta: '2026-07-22T10:00:00',
    medioPago: 'TRANSFERENCIA',
    numeroRecibo: 'R-1',
    totalFinal: 1370,
    montoMovimiento: 1000,
    montoPagado: 1000,
    montoDeuda: 370,
    estadoPago: 'PARCIAL',
    gananciaNeta: 320,
    estadoGanancia: 'POSITIVE',
    detalles: [{
      idDetalle: 11,
      idDisco: 8,
      artista: 'Artista',
      album: 'Álbum',
      codigoInterno: 'OUT008',
      cantidad: 1,
      precioUnitario: 1370,
      importeVentaReal: 1370,
      gananciaNeta: 320,
      estadoGanancia: 'POSITIVE',
      manualItem: false,
    }],
  },
  {
    idVenta: 2,
    tipoMovimiento: 'VENTA',
    descripcionMovimiento: 'Venta',
    clienteNombreSnapshot: 'Bruno Díaz',
    fechaVenta: '2026-07-21T10:00:00',
    totalFinal: 900,
    montoMovimiento: 900,
    estadoPago: 'PAGADO',
    gananciaNeta: -150,
    estadoGanancia: 'NEGATIVE',
    detalles: [],
  },
  {
    idVenta: 3,
    tipoMovimiento: 'VENTA',
    descripcionMovimiento: 'Venta',
    clienteNombreSnapshot: 'Carla Ruiz',
    fechaVenta: '2026-07-20T10:00:00',
    totalFinal: 500,
    montoMovimiento: 500,
    estadoPago: 'PAGADO',
    gananciaNeta: 0,
    estadoGanancia: 'ZERO',
    detalles: [],
  },
  {
    idVenta: 4,
    tipoMovimiento: 'VENTA',
    descripcionMovimiento: 'Venta',
    clienteNombreSnapshot: 'Diego Soto',
    fechaVenta: '2026-07-19T10:00:00',
    totalFinal: 700,
    montoMovimiento: 700,
    estadoPago: 'PENDIENTE',
    gananciaNeta: 0,
    estadoGanancia: 'UNAVAILABLE',
    detalles: [{
      idDetalle: 44,
      artista: 'Sin costo',
      album: 'Registro',
      codigoInterno: 'MISS-1',
      cantidad: 1,
      precioUnitario: 700,
      importeVentaReal: 700,
      gananciaNeta: null,
      estadoGanancia: 'UNAVAILABLE',
      manualItem: false,
    }],
  },
  {
    idPagoDeuda: 5,
    tipoMovimiento: 'PAGO_DEUDA',
    descripcionMovimiento: 'Pago de deuda',
    clienteNombreSnapshot: 'Eva López',
    fechaVenta: '2026-07-18T10:00:00',
    totalFinal: 250,
    montoMovimiento: 250,
    estadoPago: 'PAGADO',
  },
]

function rowContaining(table, text) {
  return within(table).getAllByRole('row').find(row => row.textContent.includes(text))
}

describe('LibroVentas profit display', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.libro.listar.mockResolvedValue(movements)
    api.libro.exportarUrl.mockReturnValue('/api/ventas/libro/exportar')
    api.ventas.resumenMensual.mockResolvedValue({
      cantidadVentas: 4,
      cantidadItems: 4,
      totalVentas: 3470,
      ingresosRegistrados: 2850,
      gananciaItems: 170,
      gastos: 100,
      balanceFinal: 2750,
    })
    api.ventas.resumenMensualExportarUrl.mockReturnValue('/api/ventas/resumen-mensual/exportar?periodo=2026-07')
  })

  it('replaces the payment-method column and renders profit statuses without changing row clicks', async () => {
    render(<LibroVentas />)

    const table = await screen.findByRole('table')
    expect(screen.getByRole('columnheader', { name: 'Ganancia neta' })).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: 'Medio Pago' })).not.toBeInTheDocument()

    const positiveRow = rowContaining(table, 'Ana Pérez')
    const negativeRow = rowContaining(table, 'Bruno Díaz')
    const zeroRow = rowContaining(table, 'Carla Ruiz')
    const paymentRow = rowContaining(table, 'Pago de deuda')

    expect(within(positiveRow).getByText('+ UYU $320,00').parentElement).toHaveClass('text-emerald-600')
    expect(within(negativeRow).getByText('- UYU $150,00').parentElement).toHaveClass('text-red-600')
    expect(within(zeroRow).getByText('UYU $0,00').parentElement).toHaveClass('text-slate-500')
    expect(paymentRow.textContent).toContain('—')

    fireEvent.click(positiveRow)
    expect(await screen.findByText('Discos vendidos')).toBeInTheDocument()
  })

  it('keeps payment method in details and shows matching sale/item profit including missing cost state', async () => {
    render(<LibroVentas />)

    const table = await screen.findByRole('table')
    fireEvent.click(rowContaining(table, 'Ana Pérez'))

    expect(screen.getByText('Método de pago')).toBeInTheDocument()
    expect(screen.getByText('TRANSFERENCIA')).toBeInTheDocument()
    expect(screen.getAllByText('+ UYU $320,00').length).toBeGreaterThanOrEqual(2)
    expect(screen.getByText('OUT008 · Cant. 1 · UYU $1.370,00')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /✕/ }))
    fireEvent.click(rowContaining(table, 'Diego Soto'))
    expect(screen.getByText('Ganancia no disponible')).toBeInTheDocument()
    expect(screen.getAllByText('—').length).toBeGreaterThan(0)
  })

  it('preserves the compact fixed table layout', async () => {
    render(<LibroVentas />)

    const table = await screen.findByRole('table')
    expect(table).toHaveClass('table-fixed')
    expect(table.parentElement.parentElement).toHaveClass('overflow-hidden')
  })
})
