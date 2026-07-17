export function cantidadVentasLabel(cantidad = 0) {
  const total = Number(cantidad || 0)
  return `${total} ${total === 1 ? 'venta' : 'ventas'}`
}

export function cantidadPagosLabel(cantidad = 0) {
  const total = Number(cantidad || 0)
  return `${total} ${total === 1 ? 'pago de deuda' : 'pagos de deudas'}`
}

export function ingresosPeriodoLabel(periodo) {
  const etiquetas = {
    dia: 'Ingresos del día',
    semana: 'Ingresos de la semana',
    mes: 'Ingresos del mes',
    trimestre: 'Ingresos del trimestre',
    semestre: 'Ingresos del semestre',
    anio: 'Ingresos del año',
  }
  return etiquetas[periodo] || 'Ingresos del período'
}
