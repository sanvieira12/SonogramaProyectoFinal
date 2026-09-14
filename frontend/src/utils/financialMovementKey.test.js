import { describe, expect, it } from 'vitest'
import { financialMovementKey } from './financialMovementKey'

describe('financialMovementKey', () => {
  it('separa ventas, pagos vinculados y pagos manuales aunque compartan id de venta', () => {
    const sale = { tipoMovimiento: 'VENTA', idVenta: 7 }
    const linkedPayment = { tipoMovimiento: 'PAGO_DEUDA', idVenta: 7, idPagoDeuda: 9 }
    const manualPayment = { tipoMovimiento: 'PAGO_DEUDA', idVenta: null, idPagoDeuda: 10 }

    expect(new Set([sale, linkedPayment, manualPayment].map(financialMovementKey)).size).toBe(3)
  })
})
