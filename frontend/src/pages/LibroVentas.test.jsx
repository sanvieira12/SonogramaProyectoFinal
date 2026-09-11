import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
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
      actualizar: vi.fn(),
    },
    preVentas: {
      actualizarPago: vi.fn(),
      eliminarPago: vi.fn(),
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

const preVentaMovement = {
  idVenta: 10,
  idPreVentaOrigen: 20,
  tipoMovimiento: 'PRE_VENTA',
  descripcionMovimiento: 'Cobro de pre-venta',
  clienteNombreSnapshot: 'Lucía Silva',
  fechaVenta: '2026-07-17T10:00:00',
  medioPago: 'OTRO',
  totalFinal: 1200,
  montoMovimiento: 1200,
  montoPagado: 1200,
  montoDeuda: 0,
  estadoPago: 'PAGADO',
  observaciones: 'Cobro de pre-venta #20',
  detalles: [{
    idDetalle: 20,
    artista: 'Artista PV',
    album: 'Álbum PV',
    cantidad: 2,
    precioUnitario: 600,
    manualItem: true,
    estadoGanancia: 'UNAVAILABLE',
  }],
}

const multiItemMovement = {
  idVenta: 30,
  tipoMovimiento: 'VENTA',
  descripcionMovimiento: 'Venta',
  idCliente: 7,
  clienteNombreSnapshot: 'María Silva',
  fechaVenta: '2026-07-16T10:00:00',
  canalVenta: 'LOCAL',
  tipoEntrega: 'RETIRO',
  medioPago: 'EFECTIVO',
  numeroRecibo: 'R-30',
  subtotal: 2500,
  descuentoPorcentaje: 0,
  totalFinal: 2500,
  montoMovimiento: 2500,
  montoPagado: 2500,
  montoDeuda: 0,
  estadoPago: 'PAGADO',
  grossProfit: 700,
  gananciaNeta: 700,
  estadoGanancia: 'POSITIVE',
  detalles: [
    {
      idDetalle: 31,
      idDisco: 81,
      artista: 'Artista A',
      album: 'Álbum A',
      codigoInterno: 'A-1',
      cantidad: 2,
      precioUnitario: 1000,
      importeVentaReal: 2000,
      grossProfit: 600,
      gananciaNeta: 600,
      estadoGanancia: 'POSITIVE',
      manualItem: false,
    },
    {
      idDetalle: 32,
      idDisco: 82,
      artista: 'Artista B',
      album: 'Álbum B',
      codigoInterno: 'B-1',
      cantidad: 1,
      precioUnitario: 500,
      importeVentaReal: 500,
      grossProfit: 100,
      gananciaNeta: 100,
      estadoGanancia: 'POSITIVE',
      manualItem: false,
    },
  ],
}

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
      balanceFinal: 2850,
      advertenciaGanancia: '20 ítem(s) no tienen un costo de adquisición histórico válido; su ganancia no fue inventada ni incluida.',
    })
  })

  it('replaces the payment-method column and renders profit statuses without changing row clicks', async () => {
    render(<LibroVentas />)

    const table = await screen.findByRole('table')
    expect(screen.getByRole('columnheader', { name: 'Ganancia bruta' })).toBeInTheDocument()
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

  it('exposes dedicated edit and permanent delete actions for pre-sale payments', async () => {
    api.libro.listar.mockResolvedValue([...movements, preVentaMovement])
    api.preVentas.actualizarPago.mockResolvedValue({})
    api.preVentas.eliminarPago.mockResolvedValue(undefined)
    render(<LibroVentas />)

    const table = await screen.findByRole('table')
    fireEvent.click(rowContaining(table, 'Lucía Silva'))
    expect(screen.getByRole('button', { name: 'Editar' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Eliminar' })).toBeInTheDocument()
    expect(screen.queryByText(/Movimiento protegido/)).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Editar' }))
    fireEvent.change(screen.getByDisplayValue('1200'), { target: { value: '1500' } })
    fireEvent.change(screen.getByDisplayValue('2026-07-17T10:00'), { target: { value: '2026-07-18T11:30' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    await waitFor(() => expect(api.preVentas.actualizarPago).toHaveBeenCalledWith(20, expect.objectContaining({
      precio: 1500,
      cantidad: 2,
      fechaPago: '2026-07-18T11:30',
    })))

    fireEvent.click(rowContaining(table, 'Lucía Silva'))
    fireEvent.click(screen.getByRole('button', { name: 'Eliminar' }))
    expect(screen.getByText(/eliminar permanentemente este cobro/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Eliminar permanentemente' }))

    await waitFor(() => expect(api.preVentas.eliminarPago).toHaveBeenCalledWith(20))
  })

  it('keeps normal sales on the existing edit/cancel actions', async () => {
    render(<LibroVentas />)
    const table = await screen.findByRole('table')
    fireEvent.click(rowContaining(table, 'Ana Pérez'))
    expect(screen.getByRole('button', { name: 'Editar' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancelar venta' })).toBeInTheDocument()
  })

  it('switches a normal sale in the existing panel without opening a second modal', async () => {
    render(<LibroVentas />)
    const table = await screen.findByRole('table')
    fireEvent.click(rowContaining(table, 'Ana Pérez'))

    fireEvent.click(screen.getByRole('button', { name: 'Editar' }))

    expect(screen.getByText('Editar venta')).toBeInTheDocument()
    expect(within(screen.getByRole('dialog')).getByText('Ana Pérez')).toBeInTheDocument()
    expect(within(screen.getByRole('dialog')).getByText('22/07/2026')).toBeInTheDocument()
    expect(screen.getAllByRole('dialog')).toHaveLength(1)
    expect(screen.queryByText('Cancelar venta')).not.toBeInTheDocument()
  })

  it('cancels edit without requesting an update or cancelling the sale', async () => {
    render(<LibroVentas />)
    const table = await screen.findByRole('table')
    fireEvent.click(rowContaining(table, 'Ana Pérez'))
    fireEvent.click(screen.getByRole('button', { name: 'Editar' }))

    fireEvent.change(screen.getByLabelText('Precio de venta Artista — Álbum'), { target: { value: '999' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar edición' }))

    expect(api.ventas.actualizar).not.toHaveBeenCalled()
    expect(screen.getByText('Discos vendidos')).toBeInTheDocument()
    expect(screen.getByText('Cancelar venta')).toBeInTheDocument()
    expect(screen.getByText('OUT008 · Cant. 1 · UYU $1.370,00')).toBeInTheDocument()
  })

  it('saves through the existing update API, keeps the panel open, and refreshes financial data', async () => {
    const updated = {
      ...movements[0],
      totalFinal: 1350,
      montoMovimiento: 1350,
      montoPagado: 1350,
      montoDeuda: 0,
      estadoPago: 'PAGADO',
      detalles: [{ ...movements[0].detalles[0], precioUnitario: 1500, importeVentaReal: 1350 }],
    }
    api.ventas.actualizar.mockResolvedValue(updated)
    const dispatchSpy = vi.spyOn(window, 'dispatchEvent')

    render(<LibroVentas />)
    const table = await screen.findByRole('table')
    fireEvent.click(rowContaining(table, 'Ana Pérez'))
    fireEvent.click(screen.getByRole('button', { name: 'Editar' }))
    fireEvent.change(screen.getByLabelText('Precio de venta Artista — Álbum'), { target: { value: '1500' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    await waitFor(() => expect(api.ventas.actualizar).toHaveBeenCalledWith(1, expect.objectContaining({
      total: 1500,
      detalles: [expect.objectContaining({ cantidad: 1, precioUnitario: 1500 })],
    })))
    await waitFor(() => expect(api.ventas.resumenMensual).toHaveBeenCalledTimes(2))

    expect(screen.getByText('Discos vendidos')).toBeInTheDocument()
    expect(screen.queryByText('Cancelar edición')).not.toBeInTheDocument()
    expect(screen.getByText('Venta actualizada correctamente.')).toBeInTheDocument()
    expect(dispatchSpy).toHaveBeenCalledWith(expect.objectContaining({ type: 'sonograma:financial-data-changed' }))
    dispatchSpy.mockRestore()
  })

  it('renders and submits every multi-item price while preserving quantities in the preview and payload', async () => {
    api.libro.listar.mockResolvedValue([multiItemMovement])
    api.ventas.actualizar.mockResolvedValue(multiItemMovement)
    render(<LibroVentas />)
    const table = await screen.findByRole('table')
    fireEvent.click(rowContaining(table, 'María Silva'))
    fireEvent.click(screen.getByRole('button', { name: 'Editar' }))
    const panel = screen.getByRole('dialog')

    expect(within(panel).getByText('Artista A — Álbum A')).toBeInTheDocument()
    expect(within(panel).getByText('Artista B — Álbum B')).toBeInTheDocument()
    expect(within(panel).getAllByText('UYU $2.500,00').length).toBeGreaterThan(0)

    fireEvent.change(screen.getByLabelText('Precio de venta Artista A — Álbum A'), { target: { value: '950' } })
    fireEvent.change(screen.getByLabelText('Descuento %'), { target: { value: '10' } })
    fireEvent.change(screen.getByLabelText('Monto pagado'), { target: { value: '2000' } })

    expect(within(panel).getByText('UYU $2.400,00')).toBeInTheDocument()
    expect(within(panel).getByText('UYU $2.160,00')).toBeInTheDocument()
    expect(within(panel).getByText('UYU $160,00')).toBeInTheDocument()
    expect(within(panel).getByText('PARCIAL')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    await waitFor(() => expect(api.ventas.actualizar).toHaveBeenCalledWith(30, expect.objectContaining({
      total: 2160,
      detalles: [
        expect.objectContaining({ cantidad: 2, precioUnitario: 950 }),
        expect.objectContaining({ cantidad: 1, precioUnitario: 500 }),
      ],
    })))
  })

  it('blocks invalid prices, discounts, and amounts before calling the API', async () => {
    render(<LibroVentas />)
    const table = await screen.findByRole('table')
    fireEvent.click(rowContaining(table, 'Ana Pérez'))
    fireEvent.click(screen.getByRole('button', { name: 'Editar' }))

    const price = screen.getByLabelText('Precio de venta Artista — Álbum')
    fireEvent.change(price, { target: { value: '0' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('precio de venta válido')

    fireEvent.change(price, { target: { value: '1370' } })
    fireEvent.change(screen.getByLabelText('Descuento %'), { target: { value: '101' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('entre 0% y 100%')

    fireEvent.change(screen.getByLabelText('Descuento %'), { target: { value: '0' } })
    fireEvent.change(screen.getByLabelText('Monto pagado'), { target: { value: '1371' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('no puede superar el total')
    expect(api.ventas.actualizar).not.toHaveBeenCalled()
  })

  it('keeps edit mode and entered values when the backend rejects the update', async () => {
    api.ventas.actualizar.mockRejectedValue(new Error('El servidor rechazó la venta'))
    render(<LibroVentas />)
    const table = await screen.findByRole('table')
    fireEvent.click(rowContaining(table, 'Ana Pérez'))
    fireEvent.click(screen.getByRole('button', { name: 'Editar' }))
    fireEvent.change(screen.getByLabelText('Precio de venta Artista — Álbum'), { target: { value: '1500' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('El servidor rechazó la venta')
    expect(screen.getByText('Editar venta')).toBeInTheDocument()
    expect(screen.getByLabelText('Precio de venta Artista — Álbum')).toHaveValue(1500)
  })

  it('preserves the compact fixed table layout', async () => {
    render(<LibroVentas />)

    const table = await screen.findByRole('table')
    expect(table).toHaveClass('table-fixed')
    expect(table.parentElement.parentElement).toHaveClass('overflow-hidden')
  })

  it('uses recorded income for final balance and removes the warning and PDF action', async () => {
    render(<LibroVentas />)

    expect(await screen.findByText('Balance final')).toBeInTheDocument()
    expect(screen.getAllByText('UYU $2.850,00')).toHaveLength(2)
    expect(screen.getByText('UYU $100,00')).toBeInTheDocument()
    expect(screen.queryByText(/no tienen un costo de adquisición histórico válido/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Descargar resumen PDF' })).not.toBeInTheDocument()
  })

  it('keeps the Excel export action available', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      status: 200,
      ok: true,
      blob: async () => new Blob(['xlsx']),
    })
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:excel') })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    render(<LibroVentas />)
    const button = await screen.findByRole('button', { name: 'Exportar Excel' })
    fireEvent.click(button)

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/ventas/libro/exportar',
      expect.objectContaining({ headers: { Authorization: 'Bearer null' } }),
    ))
  })
})
