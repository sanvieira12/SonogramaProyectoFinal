import { describe, expect, it } from 'vitest'
import { salesChannelStats } from './salesChannelStats'

const sale = (idVenta, canalVenta, extra = {}) => ({
  idVenta, canalVenta, estado: 'COMPLETADA', tipoMovimiento: 'VENTA', ...extra,
})

describe('salesChannelStats', () => {
  it('counts sale IDs once regardless of items or payment status and excludes payment movements', () => {
    expect(salesChannelStats([
      sale(1, 'LOCAL', { detalles: [{ cantidad: 5 }, { cantidad: 2 }], estadoPago: 'PARCIAL' }),
      sale(1, 'LOCAL'), sale(2, 'INSTAGRAM'),
      sale(3, 'LOCAL', { tipoMovimiento: 'PAGO_DEUDA' }),
      sale(4, 'LOCAL', { tipoMovimiento: 'PRE_VENTA' }),
      sale(5, 'LOCAL', { estado: 'CANCELADA' }),
      sale(6, 'LOCAL', { estado: 'PENDIENTE' }),
      sale(7, 'WEB'), sale(8, 'ENCARGUE'), sale(9, 'OTROS'),
    ])).toEqual({ LOCAL: 1, INSTAGRAM: 1, WEB: 1, ENCARGUE: 1, OTROS: 1, unknown: 0 })
  })

  it('reports missing and unknown channels without assigning a channel', () => {
    expect(salesChannelStats([sale(1, null), sale(2, undefined), sale(3, 'OTHER')]))
      .toEqual({ LOCAL: 0, INSTAGRAM: 0, WEB: 0, ENCARGUE: 0, OTROS: 0, unknown: 3 })
  })
})
