import { describe, expect, it } from 'vitest'
import { cantidadPagosLabel, cantidadVentasLabel } from './dashboardIncome'

describe('etiquetas de ingresos del dashboard', () => {
  it.each([
    [0, '0 ventas'],
    [1, '1 venta'],
    [2, '2 ventas'],
  ])('pluraliza ventas para %s', (cantidad, etiqueta) => {
    expect(cantidadVentasLabel(cantidad)).toBe(etiqueta)
  })

  it.each([
    [0, '0 pagos de deudas'],
    [1, '1 pago de deuda'],
    [2, '2 pagos de deudas'],
  ])('pluraliza pagos para %s', (cantidad, etiqueta) => {
    expect(cantidadPagosLabel(cantidad)).toBe(etiqueta)
  })
})
