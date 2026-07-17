import { describe, expect, it } from 'vitest'
import { cantidadPagosLabel, cantidadVentasLabel, ingresosPeriodoLabel } from './dashboardIncome'

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

  it.each([
    ['dia', 'Ingresos del día'],
    ['semana', 'Ingresos de la semana'],
    ['mes', 'Ingresos del mes'],
    ['trimestre', 'Ingresos del trimestre'],
    ['semestre', 'Ingresos del semestre'],
    ['anio', 'Ingresos del año'],
  ])('etiqueta el período %s', (periodo, etiqueta) => {
    expect(ingresosPeriodoLabel(periodo)).toBe(etiqueta)
  })
})
